package com.wkq.google.billing.gate

import com.wkq.google.billing.GoogleBillingEntitlement

data class GoogleFeatureAccessResult(
    val featureId: String,
    val allowed: Boolean,
    val policyAllowed: Boolean,
    val requirement: GoogleFeatureRequirement,
    val reason: GoogleFeatureAccessReason,
    val entitlement: GoogleBillingEntitlement,
    val mode: GoogleFeatureGateMode,
    val upgradeMessage: String = ""
) {
    val requiresUpgrade: Boolean
        get() = !policyAllowed && reason in upgradeReasons

    companion object {
        private val upgradeReasons = setOf(
            GoogleFeatureAccessReason.PRO_REQUIRED,
            GoogleFeatureAccessReason.SUBSCRIPTION_REQUIRED,
            GoogleFeatureAccessReason.LIFETIME_REQUIRED,
            GoogleFeatureAccessReason.PRODUCT_REQUIRED,
            GoogleFeatureAccessReason.LIMIT_EXCEEDED
        )
    }
}
