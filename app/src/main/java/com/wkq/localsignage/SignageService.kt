package com.wkq.localsignage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import com.wkq.localsignage.feature.app.R as FeatureAppR
import com.wkq.localsignage.feature.app.discovery.LocalDeviceDiscovery
import com.wkq.localsignage.feature.app.player.SignagePlaybackController
import com.wkq.localsignage.feature.app.server.KtorSignageServer
import com.wkq.google.GoogleKit

class SignageService : Service() {
    private var foregroundStarted = false
    private val networkHandler = Handler(Looper.getMainLooper())
    private val reportActive = object : Runnable {
        override fun run() {
            GoogleKit.commercial.reportActive()
            networkHandler.postDelayed(this, 60 * 60 * 1000L)
        }
    }
    private var connectivityManager: ConnectivityManager? = null
    private val startDiscovery = Runnable {
        val state = SignageRuntime.state()
        LocalDeviceDiscovery.start(this, state.deviceId, state.deviceName, SignageRuntime.SERVER_PORT)
    }
    private val restartDiscovery = Runnable {
        LocalDeviceDiscovery.stop()
        networkHandler.removeCallbacks(startDiscovery)
        networkHandler.postDelayed(startDiscovery, DISCOVERY_REREGISTRATION_DELAY_MS)
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleDiscoveryRestart()
        override fun onLost(network: Network) = scheduleDiscoveryRestart()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            scheduleDiscoveryRestart()
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
            scheduleDiscoveryRestart()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        try {
            startForeground(NOTIFICATION_ID, notification())
        } catch (error: Exception) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                error !is ForegroundServiceStartNotAllowedException
            ) throw error
            // 调用方获准发起请求，不代表服务创建时仍获准提升前台。
            // 停止本次启动；用户回到可见页面时重新请求，不在后台循环重试。
            Log.w(TAG, "FOREGROUND_START_REJECTED_WAIT_FOR_USER", error)
            stopSelf()
            return
        }
        foregroundStarted = true
        if (!SignageRuntime.tryInitialize(this)) {
            foregroundStarted = false
            stopSelf()
            return
        }
        networkHandler.post(reportActive)
        val state = SignageRuntime.state()
        LocalDeviceDiscovery.start(this, state.deviceId, state.deviceName, SignageRuntime.SERVER_PORT)
        registerNetworkCallback()
        SignagePlaybackController.initialize(this)
        try {
            KtorSignageServerHolder.start(this)
        } catch (error: Exception) {
            Log.e(TAG, "LOCAL_SERVER_START_FAILED", error)
            SignageRuntime.setError("LOCAL_SERVER_START_FAILED")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        if (foregroundStarted) START_STICKY else {
            stopSelf(startId)
            START_NOT_STICKY
        }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // stopWithTask=false 保留现有服务；进程回收由 START_STICKY 交给系统恢复。
        // 此时应用已经不可见，主动再发起前台服务请求可能被系统拒绝。
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (!foregroundStarted) {
            // 提升前台失败时没有取得运行时资源，不释放页面持有的播放器。
            super.onDestroy()
            return
        }
        networkHandler.removeCallbacks(reportActive)
        networkHandler.removeCallbacks(restartDiscovery)
        networkHandler.removeCallbacks(startDiscovery)
        connectivityManager?.let { manager -> runCatching { manager.unregisterNetworkCallback(networkCallback) } }
        connectivityManager = null
        KtorSignageServerHolder.stop()
        LocalDeviceDiscovery.stop()
        SignagePlaybackController.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerNetworkCallback() {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        connectivityManager = manager
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                manager.registerDefaultNetworkCallback(networkCallback)
            } else {
                manager.registerNetworkCallback(
                    NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                        .build(),
                    networkCallback
                )
            }
        }.onFailure { Log.w(TAG, "NETWORK_MONITOR_UNAVAILABLE", it) }
    }

    private fun scheduleDiscoveryRestart() {
        networkHandler.removeCallbacks(restartDiscovery)
        networkHandler.removeCallbacks(startDiscovery)
        networkHandler.postDelayed(restartDiscovery, NETWORK_RESTART_DEBOUNCE_MS)
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(getString(FeatureAppR.string.app_name))
        .setContentText(getString(FeatureAppR.string.service_running))
        .setOngoing(true)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(FeatureAppR.string.service_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    private companion object {
        const val TAG = "SignageService"
        const val CHANNEL_ID = "local_signage_service"
        const val NOTIFICATION_ID = 1001
        const val NETWORK_RESTART_DEBOUNCE_MS = 1_500L
        const val DISCOVERY_REREGISTRATION_DELAY_MS = 500L
    }
}

private object KtorSignageServerHolder {
    private var server: KtorSignageServer? = null

    fun start(context: android.content.Context) {
        if (server == null) server = KtorSignageServer(context.applicationContext, SignageRuntime.SERVER_PORT)
        server?.start()
    }

    fun stop() {
        server?.stop()
        server = null
    }
}
