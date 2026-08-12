package com.wkq.localsignage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import com.wkq.localsignage.feature.app.discovery.LocalDeviceDiscovery
import com.wkq.localsignage.feature.app.player.SignagePlaybackController
import com.wkq.localsignage.feature.app.server.KtorSignageServer

class SignageService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification())
        SignageRuntime.initialize(this)
        val state = SignageRuntime.state()
        LocalDeviceDiscovery.start(this, state.deviceId, state.deviceName, SignageRuntime.SERVER_PORT)
        SignagePlaybackController.initialize(this)
        KtorSignageServerHolder.start(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        KtorSignageServerHolder.stop()
        LocalDeviceDiscovery.stop()
        SignagePlaybackController.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
    }
}

private object KtorSignageServerHolder {
    private var server: KtorSignageServer? = null

    fun start(context: android.content.Context) {
        if (server == null) server = KtorSignageServer(SignageRuntime.SERVER_PORT)
        server?.start()
    }

    fun stop() {
        server?.stop()
        server = null
    }
}
