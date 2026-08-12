package com.wkq.localsignage.feature.app.discovery

data class DiscoveredDevice(
    val deviceId: String,
    val deviceName: String,
    val host: String,
    val port: Int,
    val serviceName: String,
    val lastSeenAt: Long
)
