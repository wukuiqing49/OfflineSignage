package com.wkq.localsignage.feature.app.model

data class SignageResource(
    val id: String,
    val name: String,
    val mimeType: String,
    val path: String,
    val hash: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val kind: String = ResourceKind.LOCAL_FILE.name,
    val sourceUri: String? = null,
    val content: String? = null,
    val refreshIntervalMs: Long? = null,
    val textSizeSp: Int = TextStylePolicy.DEFAULT_TEXT_SIZE_SP,
    val textColor: String = TextStylePolicy.DEFAULT_TEXT_COLOR,
    val textBackgroundColor: String = TextStylePolicy.DEFAULT_BACKGROUND_COLOR,
    val fontFamily: String = TextStylePolicy.DEFAULT_FONT_FAMILY,
    val textSpeedDpPerSecond: Int = PlaybackTimingPolicy.DEFAULT_TEXT_SPEED_DP_PER_SECOND,
    val textRepeatCount: Int = PlaybackTimingPolicy.INFINITE_TEXT_REPEAT_COUNT
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isLocalFile: Boolean get() = kind == ResourceKind.LOCAL_FILE.name
    val isRemoteFile: Boolean get() = kind == ResourceKind.REMOTE_FILE.name
    val isWeb: Boolean get() = kind == ResourceKind.WEB.name
    val isStream: Boolean get() = kind == ResourceKind.STREAM.name
    val isText: Boolean get() = kind == ResourceKind.TEXT.name
}

enum class ResourceKind { LOCAL_FILE, REMOTE_FILE, WEB, STREAM, TEXT }

data class SignageOverlay(
    val id: String,
    val type: String,
    val content: String,
    val horizontalPosition: String = "CENTER",
    val verticalPosition: String = "BOTTOM",
    val textSizeSp: Int = 28,
    val textColor: String = "#FFFFFFFF",
    val backgroundColor: String = "#99000000",
    val paddingDp: Int = 12,
    val cornerRadiusDp: Int = 8,
    val fontFamily: String = TextStylePolicy.DEFAULT_FONT_FAMILY,
    val speedDpPerSecond: Int = 80,
    val enabled: Boolean = true,
    val zIndex: Int = 0,
    val widthPercent: Int = 42,
    val heightPercent: Int = 24
)

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
    val createdAt: Long = System.currentTimeMillis(),
    val overlays: List<SignageOverlay> = emptyList(),
    val playbackSpeed: Float = PlaybackTimingPolicy.DEFAULT_VIDEO_PLAYBACK_SPEED
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

data class OperationRecord(
    val id: Long,
    val createdAt: Long,
    val clientName: String,
    val deviceId: String,
    val action: String,
    val result: String,
    val statusCode: Int
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

data class DeviceAssignment(
    val deviceId: String,
    val playlistId: String,
    val desiredRevision: Long,
    val desiredPlaying: Boolean,
    val state: String,
    val appliedRevision: Long? = null,
    val lastSyncAt: Long? = null,
    val lastError: String? = null
)

interface PlaybackListener {
    fun onStateChanged(state: SignageState)
}
