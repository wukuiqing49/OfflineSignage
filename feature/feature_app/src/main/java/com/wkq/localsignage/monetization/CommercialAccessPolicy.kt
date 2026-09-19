package com.wkq.localsignage.monetization

enum class CommercialAccessMode {
    TRIAL,
    PRO,
    FREE_LIMITED
}

data class CommercialAccessState(
    val mode: CommercialAccessMode,
    val resourceCount: Int,
    val playlistCount: Int,
    val resourceLimit: Int?,
    val playlistLimit: Int?
) {
    val unrestricted: Boolean get() = mode != CommercialAccessMode.FREE_LIMITED
    val canUseAdvancedContent: Boolean get() = unrestricted
    val canUseMultiDevice: Boolean get() = unrestricted
    val canAddBasicMedia: Boolean get() = resourceLimit == null || resourceCount < resourceLimit
    val canCreatePlaylist: Boolean get() = playlistLimit == null || playlistCount < playlistLimit
}

/**
 * Pure commercial policy shared by Android and the local Web API.
 * Existing playback and destructive cleanup are intentionally outside this policy so an expired
 * trial never interrupts a running display or traps user-owned content.
 */
object CommercialAccessPolicy {
    const val FREE_RESOURCE_LIMIT = 10
    const val FREE_PLAYLIST_LIMIT = 1

    fun evaluate(
        entitlement: EntitlementState,
        resourceCount: Int,
        playlistCount: Int
    ): CommercialAccessState {
        val mode = when {
            entitlement.isPaid -> CommercialAccessMode.PRO
            entitlement.type == EntitlementType.TRIAL_ACTIVE -> CommercialAccessMode.TRIAL
            else -> CommercialAccessMode.FREE_LIMITED
        }
        return CommercialAccessState(
            mode = mode,
            resourceCount = resourceCount,
            playlistCount = playlistCount,
            resourceLimit = FREE_RESOURCE_LIMIT.takeIf { mode == CommercialAccessMode.FREE_LIMITED },
            playlistLimit = FREE_PLAYLIST_LIMIT.takeIf { mode == CommercialAccessMode.FREE_LIMITED }
        )
    }
}
