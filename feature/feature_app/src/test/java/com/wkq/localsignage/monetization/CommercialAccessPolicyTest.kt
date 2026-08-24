package com.wkq.localsignage.monetization

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialAccessPolicyTest {
    @Test
    fun `active trial has unrestricted access`() {
        val access = CommercialAccessPolicy.evaluate(entitlement(EntitlementType.TRIAL_ACTIVE), 20, 4)

        assertTrue(access.unrestricted)
        assertTrue(access.canUseAdvancedContent)
        assertNull(access.resourceLimit)
    }

    @Test
    fun `paid access remains unrestricted`() {
        listOf(
            EntitlementType.SUBSCRIPTION,
            EntitlementType.SUBSCRIPTION_GRACE,
            EntitlementType.LIFETIME
        ).forEach { type ->
            assertTrue(CommercialAccessPolicy.evaluate(entitlement(type), 20, 4).unrestricted)
        }
    }

    @Test
    fun `expired trial safely downgrades to limited free mode`() {
        val access = CommercialAccessPolicy.evaluate(entitlement(EntitlementType.TRIAL_EXPIRED), 4, 0)

        assertFalse(access.unrestricted)
        assertFalse(access.canUseAdvancedContent)
        assertFalse(access.canUseMultiDevice)
        assertTrue(access.canAddBasicMedia)
        assertTrue(access.canCreatePlaylist)
    }

    @Test
    fun `free limits close exactly at configured capacity`() {
        val access = CommercialAccessPolicy.evaluate(
            entitlement(EntitlementType.TRIAL_EXPIRED),
            CommercialAccessPolicy.FREE_RESOURCE_LIMIT,
            CommercialAccessPolicy.FREE_PLAYLIST_LIMIT
        )

        assertFalse(access.canAddBasicMedia)
        assertFalse(access.canCreatePlaylist)
    }

    private fun entitlement(type: EntitlementType) = EntitlementState(
        type = type,
        trialStartedAtEpochMillis = 1L,
        trialEndsAtEpochMillis = 2L
    )
}
