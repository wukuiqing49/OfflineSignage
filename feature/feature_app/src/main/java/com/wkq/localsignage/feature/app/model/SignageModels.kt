package com.wkq.localsignage.feature.app.model

data class SignageResource(
    val id: String,
    val name: String,
    val mimeType: String,
    val path: String,
    val hash: String,
    val sizeBytes: Long,
    val createdAt: Long
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

data class SignageState(
    val deviceId: String,
    val deviceName: String,
    val currentResourceId: String?,
    val playing: Boolean,
    val volume: Int,
    val muted: Boolean,
    val positionMs: Long,
    val serverPort: Int
)

interface PlaybackListener {
    fun onStateChanged(state: SignageState)
}
