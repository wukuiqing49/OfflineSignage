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

data class SignageScene(
    val id: String,
    val name: String,
    val resourceId: String,
    val fitMode: String = "FIT",
    val cropGravity: String = "CENTER",
    val backgroundType: String = "BLACK",
    val backgroundColor: String? = null,
    val volume: Int? = null,
    val muted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class SignagePlaylistItem(
    val sceneId: String,
    val durationMs: Long? = null,
    val enabled: Boolean = true
)

data class SignagePlaylist(
    val id: String,
    val name: String,
    val items: List<SignagePlaylistItem>,
    val loop: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

data class SignageState(
    val deviceId: String,
    val deviceName: String,
    val currentResourceId: String?,
    val currentSceneId: String?,
    val currentPlaylistId: String?,
    val playing: Boolean,
    val volume: Int,
    val muted: Boolean,
    val positionMs: Long,
    val error: String? = null,
    val serverPort: Int,
    val commandRevision: Long = 0L
)

data class SignageSettings(
    val fallbackSceneId: String? = null,
    val keepScreenAwake: Boolean = true,
    val autoResume: Boolean = true,
    val fullscreen: Boolean = true
)

data class PlaybackErrorRecord(
    val id: Long,
    val mediaId: String?,
    val sceneId: String?,
    val errorCode: String,
    val action: String,
    val attempt: Int,
    val createdAt: Long
)

data class ControlSession(
    val sessionId: String,
    val clientName: String,
    val expiresAt: Long
)

data class PairedDevice(
    val deviceId: String,
    val deviceName: String,
    val host: String,
    val port: Int,
    val token: String,
    val pairedAt: Long
)

interface PlaybackListener {
    fun onStateChanged(state: SignageState)
}
