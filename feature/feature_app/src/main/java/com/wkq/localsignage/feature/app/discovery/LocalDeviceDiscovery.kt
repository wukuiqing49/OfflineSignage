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

    private val nsdDevices = ConcurrentHashMap<String, DiscoveredDevice>()
    private val udpDevices = ConcurrentHashMap<String, DiscoveredDevice>()
    private val resolvingServices = ConcurrentHashMap.newKeySet<String>()
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
                    runCatching { manager.resolveService(serviceInfo, resolveListener) }
                        .onFailure { resolvingServices.remove(serviceInfo.serviceName) }
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                resolvingServices.remove(serviceInfo.serviceName)
                nsdDevices.entries.removeIf { it.value.serviceName == serviceInfo.serviceName }
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
            nsdDevices[deviceId] = DiscoveredDevice(
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

    private const val UDP_STALE_AFTER_MS = 60_000L
}
