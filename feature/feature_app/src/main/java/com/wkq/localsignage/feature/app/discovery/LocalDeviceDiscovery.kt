package com.wkq.localsignage.feature.app.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.ConcurrentHashMap

/** Registers this player and discovers peer Local Signage players on the LAN. */
object LocalDeviceDiscovery {
    private const val SERVICE_TYPE = "_localsignage._tcp."
    private const val DEVICE_ID_ATTRIBUTE = "deviceId"
    private const val DEVICE_NAME_ATTRIBUTE = "deviceName"

    private val devices = ConcurrentHashMap<String, DiscoveredDevice>()
    private val resolvingServices = ConcurrentHashMap.newKeySet<String>()
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var localDeviceId: String? = null
    private var localServiceName: String? = null

    @Synchronized
    fun start(context: Context, deviceId: String, deviceName: String, port: Int) {
        if (nsdManager != null) return
        val manager = context.applicationContext.getSystemService(NsdManager::class.java) ?: return
        nsdManager = manager
        localDeviceId = deviceId
        registerLocalService(manager, deviceId, deviceName, port)
        startDiscovery(manager)
    }

    @Synchronized
    fun stop() {
        val manager = nsdManager ?: return
        discoveryListener?.let { listener -> runCatching { manager.stopServiceDiscovery(listener) } }
        registrationListener?.let { listener -> runCatching { manager.unregisterService(listener) } }
        discoveryListener = null
        registrationListener = null
        nsdManager = null
        localDeviceId = null
        localServiceName = null
        devices.clear()
        resolvingServices.clear()
    }

    fun snapshot(): List<DiscoveredDevice> = devices.values.sortedBy { it.deviceName.lowercase() }

    fun find(deviceId: String): DiscoveredDevice? = devices[deviceId]

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
                    runCatching { manager.resolveService(serviceInfo, resolveListener) }
                        .onFailure { resolvingServices.remove(serviceInfo.serviceName) }
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                resolvingServices.remove(serviceInfo.serviceName)
                devices.entries.removeIf { it.value.serviceName == serviceInfo.serviceName }
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { discoveryListener = null }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener) }
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            resolvingServices.remove(serviceInfo.serviceName)
            if (serviceInfo.serviceName == localServiceName) return
            val attributes = serviceInfo.attributes
            val deviceId = attributes[DEVICE_ID_ATTRIBUTE]?.toString(Charsets.UTF_8)
                ?.takeIf { it.isNotBlank() } ?: serviceInfo.serviceName
            if (deviceId == localDeviceId) return
            val host = serviceInfo.host?.hostAddress ?: return
            val deviceName = attributes[DEVICE_NAME_ATTRIBUTE]?.toString(Charsets.UTF_8)
                ?.takeIf { it.isNotBlank() } ?: serviceInfo.serviceName
            devices[deviceId] = DiscoveredDevice(
                deviceId = deviceId,
                deviceName = deviceName,
                host = host,
                port = serviceInfo.port,
                serviceName = serviceInfo.serviceName,
                lastSeenAt = System.currentTimeMillis()
            )
        }

        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            resolvingServices.remove(serviceInfo.serviceName)
        }
    }
}
