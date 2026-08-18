package com.wkq.localsignage.feature.app.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/** Registers this player and discovers peer Local Signage players on the LAN. */
object LocalDeviceDiscovery {
    private const val SERVICE_TYPE = "_localsignage._tcp."
    private const val DEVICE_ID_ATTRIBUTE = "deviceId"
    private const val DEVICE_NAME_ATTRIBUTE = "deviceName"

    private val nsdDevices = ConcurrentHashMap<String, DiscoveredDevice>()
    private val udpDevices = ConcurrentHashMap<String, DiscoveredDevice>()
    private val resolvingServices = ConcurrentHashMap.newKeySet<String>()
    private val serviceInfoCallbacks = ConcurrentHashMap<String, NsdManager.ServiceInfoCallback>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbackExecutor = Executor { command -> mainHandler.post(command) }
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var localDeviceId: String? = null
    private var localServiceName: String? = null
    private var udpDiscovery: UdpDeviceDiscovery? = null

    @Synchronized
    fun start(context: Context, deviceId: String, deviceName: String, port: Int) {
        if (nsdManager != null || udpDiscovery != null) return
        localDeviceId = deviceId
        udpDiscovery = UdpDeviceDiscovery(deviceId, deviceName, port) { device ->
            udpDevices[device.deviceId] = device
        }.also { it.start() }
        context.applicationContext.getSystemService(NsdManager::class.java)?.let { manager ->
            nsdManager = manager
            registerLocalService(manager, deviceId, deviceName, port)
            startDiscovery(manager)
        }
    }

    @Synchronized
    fun stop() {
        nsdManager?.let { manager ->
            discoveryListener?.let { listener -> runCatching { manager.stopServiceDiscovery(listener) } }
            registrationListener?.let { listener -> runCatching { manager.unregisterService(listener) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                serviceInfoCallbacks.values.forEach { callback ->
                    runCatching { manager.unregisterServiceInfoCallback(callback) }
                }
            }
        }
        discoveryListener = null
        registrationListener = null
        nsdManager = null
        localDeviceId = null
        localServiceName = null
        udpDiscovery?.stop()
        udpDiscovery = null
        nsdDevices.clear()
        udpDevices.clear()
        resolvingServices.clear()
        serviceInfoCallbacks.clear()
    }

    fun snapshot(): List<DiscoveredDevice> {
        val staleBefore = System.currentTimeMillis() - UDP_STALE_AFTER_MS
        udpDevices.entries.removeIf { it.value.lastSeenAt < staleBefore }
        return (udpDevices + nsdDevices).values.sortedBy { it.deviceName.lowercase() }
    }

    fun find(deviceId: String): DiscoveredDevice? = nsdDevices[deviceId] ?: udpDevices[deviceId]

    private fun registerLocalService(manager: NsdManager, deviceId: String, deviceName: String, port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = deviceName.take(63).ifBlank { "Local Signage" }
            serviceType = SERVICE_TYPE
            this.port = port
            setAttribute(DEVICE_ID_ATTRIBUTE, deviceId)
            setAttribute(DEVICE_NAME_ATTRIBUTE, deviceName.take(63))
        }
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                localServiceName = info.serviceName
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) { registrationListener = null }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        runCatching { manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener) }
    }

    private fun startDiscovery(manager: NsdManager) {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType == SERVICE_TYPE && resolvingServices.add(serviceInfo.serviceName)) {
                    runCatching { resolveService(manager, serviceInfo) }
                        .onFailure { resolvingServices.remove(serviceInfo.serviceName) }
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                resolvingServices.remove(serviceInfo.serviceName)
                unregisterServiceInfoCallback(manager, serviceInfo.serviceName)
                nsdDevices.entries.removeIf { it.value.serviceName == serviceInfo.serviceName }
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { discoveryListener = null }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener) }
    }

    private fun resolveService(manager: NsdManager, serviceInfo: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val serviceName = serviceInfo.serviceName
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    resolvingServices.remove(serviceName)
                    serviceInfoCallbacks.remove(serviceName, this)
                }

                override fun onServiceInfoCallbackUnregistered() {
                    resolvingServices.remove(serviceName)
                    serviceInfoCallbacks.remove(serviceName, this)
                }

                override fun onServiceLost() {
                    resolvingServices.remove(serviceName)
                    serviceInfoCallbacks.remove(serviceName, this)
                    nsdDevices.entries.removeIf { it.value.serviceName == serviceName }
                }

                override fun onServiceUpdated(updatedServiceInfo: NsdServiceInfo) {
                    if (serviceInfoCallbacks[serviceName] !== this) return
                    resolvingServices.remove(serviceName)
                    updateResolvedService(updatedServiceInfo)
                }
            }
            if (serviceInfoCallbacks.putIfAbsent(serviceName, callback) != null) {
                resolvingServices.remove(serviceName)
                return
            }
            runCatching {
                manager.registerServiceInfoCallback(serviceInfo, callbackExecutor, callback)
            }.onFailure {
                serviceInfoCallbacks.remove(serviceName, callback)
            }.getOrThrow()
        } else {
            @Suppress("DEPRECATION")
            manager.resolveService(serviceInfo, resolveListener)
        }
    }

    private fun unregisterServiceInfoCallback(manager: NsdManager, serviceName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val callback = serviceInfoCallbacks.remove(serviceName) ?: return
        runCatching { manager.unregisterServiceInfoCallback(callback) }
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            resolvingServices.remove(serviceInfo.serviceName)
            updateResolvedService(serviceInfo)
        }

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            resolvingServices.remove(serviceInfo.serviceName)
        }
    }

    private fun updateResolvedService(serviceInfo: NsdServiceInfo) {
        if (serviceInfo.serviceName == localServiceName) return
        val attributes = serviceInfo.attributes
        val deviceId = attributes[DEVICE_ID_ATTRIBUTE]?.toString(Charsets.UTF_8)
            ?.takeIf { it.isNotBlank() } ?: serviceInfo.serviceName
        if (deviceId == localDeviceId) return
        val host = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            serviceInfo.hostAddresses.firstOrNull()?.hostAddress
        } else {
            @Suppress("DEPRECATION")
            serviceInfo.host?.hostAddress
        } ?: return
        val deviceName = attributes[DEVICE_NAME_ATTRIBUTE]?.toString(Charsets.UTF_8)
            ?.takeIf { it.isNotBlank() } ?: serviceInfo.serviceName
        nsdDevices[deviceId] = DiscoveredDevice(
            deviceId = deviceId,
            deviceName = deviceName,
            host = host,
            port = serviceInfo.port,
            serviceName = serviceInfo.serviceName,
            lastSeenAt = System.currentTimeMillis()
        )
    }

    private const val UDP_STALE_AFTER_MS = 60_000L
}
