package com.wkq.localsignage.monetization

import java.util.concurrent.TimeUnit

internal class EntitlementPolicy(
    private val trialDurationMillis: Long = TimeUnit.DAYS.toMillis(7),
    private val subscriptionGraceMillis: Long = TimeUnit.DAYS.toMillis(30)
) {
    fun evaluateLocal(
        snapshot: EntitlementSnapshot,
        billingAvailable: Boolean = false,
        pendingProductIds: Set<String> = emptySet()
    ): EntitlementState {
        val type = when {
            snapshot.lifetimeVerified -> EntitlementType.LIFETIME
            subscriptionInGrace(snapshot) -> EntitlementType.SUBSCRIPTION_GRACE
            snapshot.clockRollbackDetected -> EntitlementType.TRIAL_EXPIRED
            snapshot.effectiveNowEpochMillis < snapshot.trialStartedAtEpochMillis + trialDurationMillis ->
                EntitlementType.TRIAL_ACTIVE
            else -> EntitlementType.TRIAL_EXPIRED
        }
        return state(snapshot, type, billingAvailable, pendingProductIds)
    }

    fun evaluateVerified(
        snapshot: EntitlementSnapshot,
        hasLifetime: Boolean,
        hasSubscription: Boolean,
        billingAvailable: Boolean,
        pendingProductIds: Set<String>
    ): EntitlementState {
        val type = when {
            hasLifetime -> EntitlementType.LIFETIME
            hasSubscription -> EntitlementType.SUBSCRIPTION
            snapshot.clockRollbackDetected -> EntitlementType.TRIAL_EXPIRED
            snapshot.effectiveNowEpochMillis < snapshot.trialStartedAtEpochMillis + trialDurationMillis ->
                EntitlementType.TRIAL_ACTIVE
            else -> EntitlementType.TRIAL_EXPIRED
        }
        return state(snapshot, type, billingAvailable, pendingProductIds)
    }

    private fun subscriptionInGrace(snapshot: EntitlementSnapshot): Boolean {
        val verifiedAt = snapshot.lastSubscriptionVerifiedAtEpochMillis ?: return false
        if (snapshot.clockRollbackDetected) return false
        return snapshot.effectiveNowEpochMillis - verifiedAt <= subscriptionGraceMillis
    }

    private fun state(
        snapshot: EntitlementSnapshot,
        type: EntitlementType,
        billingAvailable: Boolean,
        pendingProductIds: Set<String>
    ) = EntitlementState(
        type = type,
        trialStartedAtEpochMillis = snapshot.trialStartedAtEpochMillis,
        trialEndsAtEpochMillis = snapshot.trialStartedAtEpochMillis + trialDurationMillis,
        lastVerifiedAtEpochMillis = snapshot.lastSubscriptionVerifiedAtEpochMillis,
        billingAvailable = billingAvailable,
        pendingProductIds = pendingProductIds,
        clockRollbackDetected = snapshot.clockRollbackDetected
    )
}
