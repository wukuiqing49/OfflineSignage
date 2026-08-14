package com.wkq.localsignage.feature.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimingPolicyTest {
    @Test
    fun normalizesTextTiming() {
        assertEquals(90, PlaybackTimingPolicy.normalizeTextSpeed(null))
        assertEquals(20, PlaybackTimingPolicy.normalizeTextSpeed(1))
        assertEquals(300, PlaybackTimingPolicy.normalizeTextSpeed(999))
        assertEquals(0, PlaybackTimingPolicy.normalizeTextRepeatCount(-1))
        assertEquals(100, PlaybackTimingPolicy.normalizeTextRepeatCount(999))
    }

    @Test
    fun normalizesMediaTiming() {
        assertEquals(1.0f, PlaybackTimingPolicy.normalizeVideoPlaybackSpeed(Float.NaN), 0f)
        assertEquals(0.5f, PlaybackTimingPolicy.normalizeVideoPlaybackSpeed(0.1f), 0f)
        assertEquals(2.0f, PlaybackTimingPolicy.normalizeVideoPlaybackSpeed(3f), 0f)
        assertEquals(1_000L, PlaybackTimingPolicy.normalizeImageDuration(100L))
        assertEquals(3_600_000L, PlaybackTimingPolicy.normalizeImageDuration(Long.MAX_VALUE))
    }

    @Test
    fun calculatesTextTickerDurationWithoutViewMeasurements() {
        assertEquals(
            20_000L,
            PlaybackTimingPolicy.textTickerDurationMs(900, 900f, 1f, 90, 1)
        )
        assertEquals(
            40_000L,
            PlaybackTimingPolicy.textTickerDurationMs(900, 900f, 1f, 90, 2)
        )
    }
}
