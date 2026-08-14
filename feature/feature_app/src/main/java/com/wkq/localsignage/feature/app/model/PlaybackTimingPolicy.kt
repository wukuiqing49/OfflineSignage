package com.wkq.localsignage.feature.app.model

object PlaybackTimingPolicy {
    const val DEFAULT_TEXT_SPEED_DP_PER_SECOND = 90
    const val MIN_TEXT_SPEED_DP_PER_SECOND = 20
    const val MAX_TEXT_SPEED_DP_PER_SECOND = 300
    const val INFINITE_TEXT_REPEAT_COUNT = 0
    const val MAX_TEXT_REPEAT_COUNT = 100

    const val DEFAULT_VIDEO_PLAYBACK_SPEED = 1.0f
    const val MIN_VIDEO_PLAYBACK_SPEED = 0.5f
    const val MAX_VIDEO_PLAYBACK_SPEED = 2.0f

    const val DEFAULT_IMAGE_DURATION_MS = 8_000L
    const val MIN_IMAGE_DURATION_MS = 1_000L
    const val MAX_IMAGE_DURATION_MS = 3_600_000L

    fun normalizeTextSpeed(value: Int?): Int =
        (value ?: DEFAULT_TEXT_SPEED_DP_PER_SECOND)
            .coerceIn(MIN_TEXT_SPEED_DP_PER_SECOND, MAX_TEXT_SPEED_DP_PER_SECOND)

    fun normalizeTextRepeatCount(value: Int?): Int =
        (value ?: INFINITE_TEXT_REPEAT_COUNT).coerceIn(INFINITE_TEXT_REPEAT_COUNT, MAX_TEXT_REPEAT_COUNT)

    fun normalizeVideoPlaybackSpeed(value: Float?): Float {
        val finiteValue = value?.takeIf { it.isFinite() } ?: DEFAULT_VIDEO_PLAYBACK_SPEED
        return finiteValue.coerceIn(MIN_VIDEO_PLAYBACK_SPEED, MAX_VIDEO_PLAYBACK_SPEED)
    }

    fun normalizeImageDuration(value: Long?): Long =
        (value ?: DEFAULT_IMAGE_DURATION_MS).coerceIn(MIN_IMAGE_DURATION_MS, MAX_IMAGE_DURATION_MS)

    fun textTickerDurationMs(
        viewportWidthPx: Int,
        textWidthPx: Float,
        density: Float,
        speedDpPerSecond: Int?,
        repeatCount: Int?
    ): Long {
        val viewport = viewportWidthPx.coerceAtLeast(1)
        val width = textWidthPx.takeIf { it.isFinite() }?.coerceAtLeast(viewport.toFloat()) ?: viewport.toFloat()
        val normalizedDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val pixelsPerSecond = normalizeTextSpeed(speedDpPerSecond) * normalizedDensity
        val onePass = ((viewport + width) / pixelsPerSecond * 1_000f).toLong().coerceAtLeast(100L)
        return onePass * normalizeTextRepeatCount(repeatCount).coerceAtLeast(1)
    }
}
