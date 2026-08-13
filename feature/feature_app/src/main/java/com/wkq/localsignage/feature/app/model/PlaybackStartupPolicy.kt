package com.wkq.localsignage.feature.app.model

internal object PlaybackStartupPolicy {
    fun shouldResume(persistedPlaying: Boolean, autoResume: Boolean): Boolean =
        persistedPlaying && autoResume
}
