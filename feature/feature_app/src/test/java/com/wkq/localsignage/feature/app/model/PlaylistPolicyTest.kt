package com.wkq.localsignage.feature.app.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistPolicyTest {
    @Test
    fun acceptsPlaylistWhenEverySceneExists() {
        val playlist = SignagePlaylist("playlist", "Morning", listOf(
            SignagePlaylistItem("scene-1"),
            SignagePlaylistItem("scene-2", enabled = false)
        ))

        assertTrue(PlaylistPolicy.hasKnownScenes(playlist, setOf("scene-1", "scene-2")))
    }

    @Test
    fun rejectsUnknownSceneEvenWhenItemIsDisabled() {
        val playlist = SignagePlaylist("playlist", "Morning", listOf(
            SignagePlaylistItem("missing", enabled = false)
        ))

        assertFalse(PlaylistPolicy.hasKnownScenes(playlist, emptySet()))
    }

    @Test
    fun acceptsEmptyPlaylistModel() {
        assertTrue(PlaylistPolicy.hasKnownScenes(SignagePlaylist("playlist", "Empty", emptyList()), emptySet()))
    }
}
