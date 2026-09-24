package com.wkq.localsignage.monetization

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonetizationUiStateTest {
    private val entitlement = EntitlementState(
        type = EntitlementType.TRIAL_ACTIVE,
        trialStartedAtEpochMillis = 1L,
        trialEndsAtEpochMillis = 2L
    )

    @Test
    fun catalogWithoutResultOrErrorRemainsPending() {
        assertTrue(MonetizationUiState(entitlement = entitlement).isCatalogPending)
    }

    @Test
    fun catalogErrorSettlesLoadingAndAllowsUnavailableState() {
        val state = MonetizationUiState(
            entitlement = entitlement,
            errorMessage = "Billing service unavailable on device."
        )

        assertFalse(state.isCatalogPending)
    }
}
