package com.wkq.localsignage.feature.app.discovery

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal class UdpDeviceDiscovery(
    private val localDeviceId: String,
    private val localDeviceName: String,
    private val serverPort: Int,
    private val onDeviceFound: (DiscoveredDevice) -> Unit
) {
    @Volatile private var running = false
    private var socket: DatagramSocket? = null
    private var receiver: Thread? = null
    private var announcer: ScheduledExecutorService? = null

    @Synchronized
    fun start() {
        if (running) return
        val opened = runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                soTimeout = RECEIVE_TIMEOUT_MS
                bind(InetSocketAddress(DISCOVERY_PORT))
            }
        }.getOrNull() ?: return
        socket = opened
        running = true
        receiver = Thread({ receiveLoop(opened) }, "signage-udp-discovery").apply {
            isDaemon = true
            start()
        }
        announcer = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "signage-udp-announcer").apply { isDaemon = true }
        }.also { executor ->
            executor.scheduleWithFixedDelay(::sendProbeAndAnnouncement, 0L, ANNOUNCE_INTERVAL_SECONDS, TimeUnit.SECONDS)
        }
    }

    @Synchronized
    fun stop() {
        running = false
        announcer?.shutdownNow()
        announcer = null
        socket?.close()
        socket = null
        receiver?.interrupt()
        receiver = null
    }

    private fun receiveLoop(activeSocket: DatagramSocket) {
        val buffer = ByteArray(MAX_PACKET_BYTES)
        while (running && !activeSocket.isClosed) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                activeSocket.receive(packet)
                handlePacket(activeSocket, packet)
            } catch (_: SocketTimeoutException) {
                // Timeout keeps the loop responsive to stop().
            } catch (_: SocketException) {
                if (running) continue else break
            } catch (_: Exception) {
                // A malformed or transient packet must not stop discovery.
            }
        }
    }

    private fun handlePacket(activeSocket: DatagramSocket, packet: DatagramPacket) {
        val source = packet.address
        if (!isAllowedAddress(source)) return
        val message = runCatching {
            JSONObject(String(packet.data, packet.offset, packet.length, Charsets.UTF_8))
        }.getOrNull() ?: return
        if (message.optInt("version") != PROTOCOL_VERSION) return
        when (message.optString("type")) {
            TYPE_PROBE -> send(activeSocket, source, packet.port, announcement())
            TYPE_ANNOUNCEMENT -> {
                val deviceId = message.optString("deviceId").takeIf { it.isNotBlank() } ?: return
                if (deviceId == localDeviceId) return
                val name = message.optString("deviceName").take(MAX_DEVICE_NAME_LENGTH).ifBlank { "Local Signage" }
                val port = message.optInt("port")
                if (port !in 1..65535) return
                onDeviceFound(
                    DiscoveredDevice(
                        deviceId = deviceId,
                        deviceName = name,
                        host = source.hostAddress.orEmpty(),
                        port = port,
                        serviceName = UDP_SERVICE_NAME,
                        lastSeenAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun sendProbeAndAnnouncement() {
        val activeSocket = socket ?: return
        broadcastAddresses().forEach { address ->
            send(activeSocket, address, DISCOVERY_PORT, probe())
            send(activeSocket, address, DISCOVERY_PORT, announcement())
        }
    }

    private fun send(activeSocket: DatagramSocket, address: InetAddress, port: Int, payload: ByteArray) {
        if (!running || activeSocket.isClosed) return
        runCatching { activeSocket.send(DatagramPacket(payload, payload.size, address, port)) }
    }

    private fun broadcastAddresses(): Set<InetAddress> {
        val addresses = linkedSetOf<InetAddress>()
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.forEach { network ->
                network.interfaceAddresses.mapNotNullTo(addresses) { it.broadcast?.takeIf(::isAllowedAddress) }
            }
        }
        runCatching { InetAddress.getByName("255.255.255.255") }.getOrNull()?.let(addresses::add)
        return addresses
    }

    private fun probe(): ByteArray = JSONObject()
        .put("version", PROTOCOL_VERSION)
        .put("type", TYPE_PROBE)
        .put("deviceId", localDeviceId)
        .toString().toByteArray(Charsets.UTF_8)

    private fun announcement(): ByteArray = JSONObject()
        .put("version", PROTOCOL_VERSION)
        .put("type", TYPE_ANNOUNCEMENT)
        .put("deviceId", localDeviceId)
        .put("deviceName", localDeviceName.take(MAX_DEVICE_NAME_LENGTH))
        .put("port", serverPort)
        .toString().toByteArray(Charsets.UTF_8)

    private fun isAllowedAddress(address: InetAddress): Boolean =
        address is Inet4Address && (address.isSiteLocalAddress || address.hostAddress == "255.255.255.255")

    private companion object {
        const val DISCOVERY_PORT = 18080
        const val PROTOCOL_VERSION = 1
        const val TYPE_PROBE = "WHO_IS_SIGNAGE"
        const val TYPE_ANNOUNCEMENT = "SIGNAGE"
        const val UDP_SERVICE_NAME = "udp-fallback"
        const val MAX_PACKET_BYTES = 2_048
        const val MAX_DEVICE_NAME_LENGTH = 63
        const val RECEIVE_TIMEOUT_MS = 2_000
        const val ANNOUNCE_INTERVAL_SECONDS = 15L
    }
}
