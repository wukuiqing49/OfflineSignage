package com.wkq.localsignage.monetization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class EntitlementPolicyTest {
    private val policy = EntitlementPolicy()
    private val start = 1_700_000_000_000L

    @Test
    fun trialIsActiveBeforeSevenDays() {
        val state = policy.evaluateLocal(snapshot(now = start + days(6)))

        assertEquals(EntitlementType.TRIAL_ACTIVE, state.type)
    }

    @Test
    fun trialExpiresAtSevenDays() {
        val state = policy.evaluateLocal(snapshot(now = start + days(7)))

        assertEquals(EntitlementType.TRIAL_EXPIRED, state.type)
    }

    @Test
    fun clockRollbackEndsTrial() {
        val state = policy.evaluateLocal(
            snapshot(now = start + days(1), clockRollbackDetected = true)
        )

        assertEquals(EntitlementType.TRIAL_EXPIRED, state.type)
        assertTrue(state.clockRollbackDetected)
    }

    @Test
    fun verifiedSubscriptionUsesOfflineGraceForThirtyDays() {
        val verifiedAt = start + days(8)
        val state = policy.evaluateLocal(
            snapshot(
                now = verifiedAt + days(29),
                subscriptionVerifiedAt = verifiedAt
            )
        )

        assertEquals(EntitlementType.SUBSCRIPTION_GRACE, state.type)
    }

    @Test
    fun subscriptionGraceExpiresAfterThirtyDays() {
        val verifiedAt = start + days(8)
        val state = policy.evaluateLocal(
            snapshot(
                now = verifiedAt + days(31),
                subscriptionVerifiedAt = verifiedAt
            )
        )

        assertEquals(EntitlementType.TRIAL_EXPIRED, state.type)
    }

    @Test
    fun lifetimePurchaseHasHighestPriority() {
        val state = policy.evaluateVerified(
            snapshot = snapshot(now = start + days(100), lifetime = true),
            hasLifetime = true,
            hasSubscription = true,
            billingAvailable = true,
            pendingProductIds = emptySet()
        )

        assertEquals(EntitlementType.LIFETIME, state.type)
    }

    @Test
    fun onlineSubscriptionIsActive() {
        val state = policy.evaluateVerified(
            snapshot = snapshot(now = start + days(100)),
            hasLifetime = false,
            hasSubscription = true,
            billingAvailable = true,
            pendingProductIds = emptySet()
        )

        assertEquals(EntitlementType.SUBSCRIPTION, state.type)
    }

    @Test
    fun pendingPurchaseDoesNotGrantPaidAccess() {
        val productId = MonetizationRepository.PRO_SUBSCRIPTION_ID
        val state = policy.evaluateVerified(
            snapshot = snapshot(now = start + days(8)),
            hasLifetime = false,
            hasSubscription = false,
            billingAvailable = true,
            pendingProductIds = setOf(productId)
        )

        assertEquals(EntitlementType.TRIAL_EXPIRED, state.type)
        assertEquals(setOf(productId), state.pendingProductIds)
    }

    private fun snapshot(
        now: Long,
        subscriptionVerifiedAt: Long? = null,
        lifetime: Boolean = false,
        clockRollbackDetected: Boolean = false
    ) = EntitlementSnapshot(
        trialStartedAtEpochMillis = start,
        effectiveNowEpochMillis = now,
        lastSubscriptionVerifiedAtEpochMillis = subscriptionVerifiedAt,
        lifetimeVerified = lifetime,
        clockRollbackDetected = clockRollbackDetected
    )

    private fun days(value: Long): Long = TimeUnit.DAYS.toMillis(value)
}
