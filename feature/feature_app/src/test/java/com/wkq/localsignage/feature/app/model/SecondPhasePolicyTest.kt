package com.wkq.localsignage.feature.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SecondPhasePolicyTest {
    @Test
    fun acceptsSupportedWebAndStreamResources() {
        SecondPhasePolicy.validateVirtualResource(ResourceKind.WEB, "https://example.com/signage", null)
        SecondPhasePolicy.validateVirtualResource(ResourceKind.WEB, null, "<h1>Local</h1>")
        SecondPhasePolicy.validateVirtualResource(ResourceKind.STREAM, "https://example.com/live.m3u8", null)
        SecondPhasePolicy.validateVirtualResource(ResourceKind.STREAM, "https://example.com/live.mpd", null)
        SecondPhasePolicy.validateVirtualResource(ResourceKind.STREAM, "rtsp://192.168.1.20/live", null)
        SecondPhasePolicy.validateVirtualResource(ResourceKind.TEXT, null, "Store closes at 9 PM")
        SecondPhasePolicy.validateVirtualResource(ResourceKind.REMOTE_FILE, "https://example.com/media/photo.jpg", null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHttpWebResource() {
        SecondPhasePolicy.validateVirtualResource(ResourceKind.WEB, "http://example.com", null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedStreamFormat() {
        SecondPhasePolicy.validateVirtualResource(ResourceKind.STREAM, "https://example.com/video.mp4", null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCredentialsEmbeddedInStreamUrl() {
        SecondPhasePolicy.validateVirtualResource(ResourceKind.STREAM, "rtsp://user:secret@192.168.1.20/live", null)
    }

    @Test
    fun clampsWebRefreshInterval() {
        assertEquals(30_000L, SecondPhasePolicy.normalizedRefreshInterval(1L))
        assertEquals(86_400_000L, SecondPhasePolicy.normalizedRefreshInterval(Long.MAX_VALUE))
    }
}
