package com.wkq.localsignage

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** 管理应用提供的本地热点。热点只提供本地连接，不共享公网。 */
object LocalHotspotController {
    data class HotspotInfo(val ssid: String, val passphrase: String)

    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var info: HotspotInfo? = null

    fun isActive(): Boolean = reservation != null && info != null

    fun currentInfo(): HotspotInfo? = info

    fun start(
        context: Context,
        onStarted: (HotspotInfo) -> Unit,
        onFailed: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onFailed("HOTSPOT_API_UNSUPPORTED")
            return
        }
        if (isActive()) {
            info?.let(onStarted)
            return
        }
        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
        if (wifiManager == null) {
            onFailed("WIFI_SERVICE_UNAVAILABLE")
            return
        }
        runCatching {
            wifiManager.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                        reservation = value
                        val hotspotInfo = value.toHotspotInfo()
                        info = hotspotInfo
                        onStarted(hotspotInfo)
                    }

                    override fun onStopped() {
                        reservation = null
                        info = null
                    }

                    override fun onFailed(reason: Int) {
                        reservation = null
                        info = null
                        onFailed("HOTSPOT_START_FAILED_$reason")
                    }
                },
                Handler(Looper.getMainLooper())
            )
        }.onFailure { error ->
            onFailed(error.message ?: "HOTSPOT_START_FAILED")
        }
    }

    fun stop() {
        reservation?.close()
        reservation = null
        info = null
    }

    private fun WifiManager.LocalOnlyHotspotReservation.toHotspotInfo(): HotspotInfo {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val configuration = softApConfiguration
                HotspotInfo(
                    ssid = configuration.wifiSsid?.toString().orEmpty(),
                    passphrase = configuration.passphrase.orEmpty()
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> toAndroidRHotspotInfo()
            else -> toLegacyHotspotInfo()
        }
    }

    @Suppress("DEPRECATION")
    private fun WifiManager.LocalOnlyHotspotReservation.toAndroidRHotspotInfo(): HotspotInfo {
        val configuration = softApConfiguration
        return HotspotInfo(
            ssid = configuration.ssid.orEmpty(),
            passphrase = configuration.passphrase.orEmpty()
        )
    }

    @Suppress("DEPRECATION")
    private fun WifiManager.LocalOnlyHotspotReservation.toLegacyHotspotInfo(): HotspotInfo {
        val configuration = wifiConfiguration
        return HotspotInfo(
            ssid = configuration?.SSID.orEmpty(),
            passphrase = configuration?.preSharedKey.orEmpty()
        )
    }
}
