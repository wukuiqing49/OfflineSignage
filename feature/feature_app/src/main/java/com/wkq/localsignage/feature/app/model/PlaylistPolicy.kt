package com.wkq.localsignage.feature.app.model

object PlaylistPolicy {
    fun hasKnownScenes(playlist: SignagePlaylist, knownSceneIds: Set<String>): Boolean =
        playlist.items.all { it.sceneId in knownSceneIds }
}
