package com.wkq.localsignage.monetization

import com.wkq.google.billing.GoogleBillingCatalog

enum class EntitlementType {
    TRIAL_ACTIVE,
    TRIAL_EXPIRED,
    SUBSCRIPTION,
    SUBSCRIPTION_GRACE,
    LIFETIME
}

data class EntitlementState(
    val type: EntitlementType,
    val trialStartedAtEpochMillis: Long,
    val trialEndsAtEpochMillis: Long,
    val lastVerifiedAtEpochMillis: Long? = null,
    val billingAvailable: Boolean = false,
    val pendingProductIds: Set<String> = emptySet(),
    val clockRollbackDetected: Boolean = false
) {
    val isPaid: Boolean
        get() = type == EntitlementType.SUBSCRIPTION ||
            type == EntitlementType.SUBSCRIPTION_GRACE ||
            type == EntitlementType.LIFETIME
}

data class MonetizationUiState(
    val entitlement: EntitlementState,
    val catalog: GoogleBillingCatalog = GoogleBillingCatalog(),
    val loading: Boolean = false,
    val errorMessage: String = ""
)

internal data class EntitlementSnapshot(
    val trialStartedAtEpochMillis: Long,
    val effectiveNowEpochMillis: Long,
    val lastSubscriptionVerifiedAtEpochMillis: Long?,
    val lifetimeVerified: Boolean,
    val clockRollbackDetected: Boolean
)

fun interface MonetizationClock {
    fun nowEpochMillis(): Long
}

object SystemMonetizationClock : MonetizationClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
