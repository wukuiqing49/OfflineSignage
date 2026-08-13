package com.wkq.localsignage.feature.app.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStartupPolicyTest {
    @Test
    fun resumesOnlyWhenPlaybackAndAutoResumeAreEnabled() {
        assertTrue(PlaybackStartupPolicy.shouldResume(persistedPlaying = true, autoResume = true))
        assertFalse(PlaybackStartupPolicy.shouldResume(persistedPlaying = true, autoResume = false))
        assertFalse(PlaybackStartupPolicy.shouldResume(persistedPlaying = false, autoResume = true))
        assertFalse(PlaybackStartupPolicy.shouldResume(persistedPlaying = false, autoResume = false))
    }
}
