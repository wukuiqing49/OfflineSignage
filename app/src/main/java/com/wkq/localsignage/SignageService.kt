package com.wkq.localsignage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import com.wkq.localsignage.feature.app.discovery.LocalDeviceDiscovery
import com.wkq.localsignage.feature.app.player.SignagePlaybackController
import com.wkq.localsignage.feature.app.server.KtorSignageServer

class SignageService : Service() {
    private val networkHandler = Handler(Looper.getMainLooper())
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
        startForeground(NOTIFICATION_ID, notification())
        SignageRuntime.initialize(this)
        val state = SignageRuntime.state()
        LocalDeviceDiscovery.start(this, state.deviceId, state.deviceName, SignageRuntime.SERVER_PORT)
        registerNetworkCallback()
        SignagePlaybackController.initialize(this)
        KtorSignageServerHolder.start(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 从最近任务移除 Activity 时，继续保持广告机服务；部分厂商会同时回收任务关联服务，
        // 因此主动请求一次前台服务恢复。用户在系统设置中“强行停止”应用时，Android 仍会阻止自动重启。
        runCatching { ContextCompat.startForegroundService(this, Intent(this, SignageService::class.java)) }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
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
        }
    }

    private fun scheduleDiscoveryRestart() {
        networkHandler.removeCallbacks(restartDiscovery)
        networkHandler.removeCallbacks(startDiscovery)
        networkHandler.postDelayed(restartDiscovery, NETWORK_RESTART_DEBOUNCE_MS)
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.service_running))
        .setOngoing(true)
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.service_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    private companion object {
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
