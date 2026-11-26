package com.wkq.localsignage

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import java.util.concurrent.CopyOnWriteArraySet

/** 管理本地热点及其状态；只提供局域网连接，不共享公网。 */
object LocalHotspotController {
    data class HotspotInfo(val ssid: String, val passphrase: String)
    enum class Phase { IDLE, STARTING, ACTIVE, FAILED }
    enum class Failure { PERMISSION, UNSUPPORTED, LOCATION, NO_CHANNEL, INCOMPATIBLE, DISALLOWED, GENERIC }

    private val handler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private var reservation: WifiManager.LocalOnlyHotspotReservation? = null
    private var info: HotspotInfo? = null
    private var generation = 0L
    var phase = Phase.IDLE
        private set
    var failure: Failure? = null
        private set

    fun isActive(): Boolean = phase == Phase.ACTIVE && reservation != null && info != null
    fun currentInfo(): HotspotInfo? = info
    fun addListener(listener: () -> Unit) { listeners += listener }
    fun removeListener(listener: () -> Unit) { listeners -= listener }
    private fun publish() { listeners.forEach { it() } }

    private fun fail(reason: Failure) {
        reservation = null
        info = null
        failure = reason
        phase = Phase.FAILED
        publish()
    }

    /** 一次只保留一个请求，回调不持有Activity；页面通过生命周期订阅状态。 */
    fun start(context: Context) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (phase == Phase.STARTING || isActive()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) { fail(Failure.UNSUPPORTED); return }
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            fail(Failure.PERMISSION)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val location = context.applicationContext.getSystemService(LocationManager::class.java)
            if (location == null || !LocationManagerCompat.isLocationEnabled(location)) {
                fail(Failure.LOCATION)
                return
            }
        }
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        if (wifi == null) { fail(Failure.UNSUPPORTED); return }
        val request = ++generation
        failure = null
        phase = Phase.STARTING
        publish()
        startApi26(wifi, request)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission") // start已按系统版本检查运行时权限和定位状态。
    private fun startApi26(wifi: WifiManager, request: Long) {
        try {
            wifi.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                override fun onStarted(value: WifiManager.LocalOnlyHotspotReservation) {
                    if (request != generation || phase != Phase.STARTING) { value.close(); return }
                    val details = try { value.toHotspotInfo() } catch (_: RuntimeException) {
                        value.close()
                        fail(Failure.GENERIC)
                        return
                    }
                    if (details.ssid.isBlank() || details.passphrase.isBlank()) {
                        value.close()
                        fail(Failure.GENERIC)
                        return
                    }
                    reservation = value
                    info = details.copy(ssid = details.ssid.removeSurrounding("\""))
                    phase = Phase.ACTIVE
                    publish()
                }
                override fun onStopped() {
                    if (request != generation) return
                    reservation = null
                    info = null
                    failure = null
                    phase = Phase.IDLE
                    publish()
                }
                override fun onFailed(reason: Int) {
                    if (request != generation) return
                    fail(when (reason) {
                        ERROR_NO_CHANNEL -> Failure.NO_CHANNEL
                        ERROR_INCOMPATIBLE_MODE -> Failure.INCOMPATIBLE
                        ERROR_TETHERING_DISALLOWED -> Failure.DISALLOWED
                        else -> Failure.GENERIC
                    })
                }
            }, handler)
        } catch (_: SecurityException) {
            fail(Failure.PERMISSION)
        } catch (_: RuntimeException) {
            fail(Failure.GENERIC)
        }
    }

    /** 先使旧回调失效，防止关闭后迟到的onStarted重新保留热点。 */
    fun stop() {
        check(Looper.myLooper() == Looper.getMainLooper())
        generation++
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) reservation?.close()
        reservation = null
        info = null
        failure = null
        phase = Phase.IDLE
        publish()
    }

    @RequiresApi(Build.VERSION_CODES.O)
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

    @RequiresApi(Build.VERSION_CODES.R)
    @Suppress("DEPRECATION")
    private fun WifiManager.LocalOnlyHotspotReservation.toAndroidRHotspotInfo(): HotspotInfo {
        val configuration = softApConfiguration
        return HotspotInfo(
            ssid = configuration.ssid.orEmpty(),
            passphrase = configuration.passphrase.orEmpty()
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @Suppress("DEPRECATION")
    private fun WifiManager.LocalOnlyHotspotReservation.toLegacyHotspotInfo(): HotspotInfo {
        val configuration = wifiConfiguration
        return HotspotInfo(
            ssid = configuration?.SSID.orEmpty(),
            passphrase = configuration?.preSharedKey.orEmpty()
        )
    }
}
