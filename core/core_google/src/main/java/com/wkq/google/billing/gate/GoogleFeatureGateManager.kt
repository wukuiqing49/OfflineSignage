package com.wkq.google.billing.gate

import android.content.Context
import android.content.pm.ApplicationInfo
import com.wkq.google.GoogleKit
import com.wkq.google.billing.GoogleBillingEntitlement
import com.wkq.google.billing.GoogleBillingEntitlementState

object GoogleFeatureGateManager {

    @Volatile
    private var config: GoogleFeatureGateConfig = GoogleFeatureGateConfig()

    @Volatile
    private var modeOverrideAllowed: Boolean = false

    fun initialize(context: Context, config: GoogleFeatureGateConfig) {
        this.config = config
        GoogleFeatureGateStore.initialize(context)
        modeOverrideAllowed =
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!modeOverrideAllowed) {
            GoogleFeatureGateStore.setModeOverride(null)
        }
    }

    fun currentMode(): GoogleFeatureGateMode {
        val override = if (modeOverrideAllowed) {
            GoogleFeatureGateStore.getModeOverride()
        } else {
            null
        }
        return override ?: config.mode
    }

    fun setModeOverride(mode: GoogleFeatureGateMode?) {
        GoogleFeatureGateStore.setModeOverride(if (modeOverrideAllowed) mode else null)
    }

    fun clearModeOverride() {
        GoogleFeatureGateStore.setModeOverride(null)
    }

    suspend fun isAllowed(featureId: String, usageCount: Int? = null): Boolean {
        return check(featureId, usageCount).allowed
    }

    suspend fun requireAllowed(featureId: String, usageCount: Int? = null): GoogleFeatureAccessResult {
        val result = check(featureId, usageCount)
        if (!result.allowed) {
            throw GoogleFeatureAccessException(result)
        }
        return result
    }

    suspend fun check(featureId: String, usageCount: Int? = null): GoogleFeatureAccessResult {
        val mode = currentMode()
        val rule = config.ruleFor(featureId)
        val requirement = rule?.requirement ?: GoogleFeatureRequirement.Free
        val entitlement = if (requirement == GoogleFeatureRequirement.Free || !config.enabled || mode == GoogleFeatureGateMode.OPEN_ALL) {
            GoogleBillingEntitlement(
                state = GoogleBillingEntitlementState.FREE,
                source = "feature_gate"
            )
        } else {
            runCatching { GoogleKit.billing.queryEntitlement() }.getOrElse { error ->
                GoogleBillingEntitlement(
                    state = GoogleBillingEntitlementState.UNKNOWN,
                    source = "billing_unavailable",
                    errorMessage = error.message.orEmpty()
                )
            }
        }

        if (!config.enabled) {
            return result(
                featureId = featureId,
                allowed = true,
                policyAllowed = true,
                requirement = requirement,
                reason = GoogleFeatureAccessReason.GATE_DISABLED,
                entitlement = entitlement,
                mode = mode,
                upgradeMessage = rule?.upgradeMessage.orEmpty()
            )
        }

        if (mode == GoogleFeatureGateMode.OPEN_ALL) {
            return result(
                featureId = featureId,
                allowed = true,
                policyAllowed = true,
                requirement = requirement,
                reason = GoogleFeatureAccessReason.GLOBAL_OPEN_ALL,
                entitlement = entitlement,
                mode = mode,
                upgradeMessage = rule?.upgradeMessage.orEmpty()
            )
        }

        val policy = evaluate(featureId, requirement, entitlement, usageCount, rule?.upgradeMessage.orEmpty(), mode)
        return if (mode == GoogleFeatureGateMode.LOG_ONLY && !policy.policyAllowed) {
            policy.copy(allowed = true, reason = GoogleFeatureAccessReason.LOG_ONLY)
        } else {
            policy
        }
    }

    private fun evaluate(
        featureId: String,
        requirement: GoogleFeatureRequirement,
        entitlement: GoogleBillingEntitlement,
        usageCount: Int?,
        upgradeMessage: String,
        mode: GoogleFeatureGateMode
    ): GoogleFeatureAccessResult {
        val ownedProducts = entitlement.activeSubscriptionIds + entitlement.ownedOneTimeProductIds
        val allowed: Boolean
        val reason: GoogleFeatureAccessReason

        if (entitlement.state == GoogleBillingEntitlementState.UNKNOWN &&
            requirement != GoogleFeatureRequirement.Free &&
            requirement !is GoogleFeatureRequirement.LimitedFree
        ) {
            return result(
                featureId = featureId,
                allowed = false,
                policyAllowed = false,
                requirement = requirement,
                reason = GoogleFeatureAccessReason.ENTITLEMENT_UNKNOWN,
                entitlement = entitlement,
                mode = mode,
                upgradeMessage = upgradeMessage
            )
        }

        when (requirement) {
            GoogleFeatureRequirement.Free -> {
                allowed = true
                reason = GoogleFeatureAccessReason.FREE_FEATURE
            }
            GoogleFeatureRequirement.Pro -> {
                allowed = entitlement.isPro
                reason = if (allowed) GoogleFeatureAccessReason.PRO_ACTIVE else GoogleFeatureAccessReason.PRO_REQUIRED
            }
            GoogleFeatureRequirement.Subscription -> {
                allowed = entitlement.hasSubscription
                reason = if (allowed) GoogleFeatureAccessReason.SUBSCRIPTION_ACTIVE else GoogleFeatureAccessReason.SUBSCRIPTION_REQUIRED
            }
            GoogleFeatureRequirement.Lifetime -> {
                allowed = entitlement.hasLifetimeUnlock
                reason = if (allowed) GoogleFeatureAccessReason.LIFETIME_ACTIVE else GoogleFeatureAccessReason.LIFETIME_REQUIRED
            }
            is GoogleFeatureRequirement.AnyProduct -> {
                allowed = ownedProducts.any { it in requirement.productIds }
                reason = if (allowed) GoogleFeatureAccessReason.PRODUCT_OWNED else GoogleFeatureAccessReason.PRODUCT_REQUIRED
            }
            is GoogleFeatureRequirement.LimitedFree -> {
                val usage = usageCount
                allowed = when {
                    requirement.freeLimit <= 0 -> true
                    entitlement.isPro -> true
                    usage == null -> false
                    else -> usage < requirement.freeLimit
                }
                reason = when {
                    requirement.freeLimit <= 0 -> GoogleFeatureAccessReason.FREE_FEATURE
                    entitlement.isPro -> GoogleFeatureAccessReason.PRO_ACTIVE
                    entitlement.state == GoogleBillingEntitlementState.UNKNOWN && allowed ->
                        GoogleFeatureAccessReason.LIMIT_NOT_REACHED
                    entitlement.state == GoogleBillingEntitlementState.UNKNOWN ->
                        GoogleFeatureAccessReason.ENTITLEMENT_UNKNOWN
                    usage == null -> GoogleFeatureAccessReason.USAGE_REQUIRED
                    allowed -> GoogleFeatureAccessReason.LIMIT_NOT_REACHED
                    else -> GoogleFeatureAccessReason.LIMIT_EXCEEDED
                }
            }
        }

        return result(
            featureId = featureId,
            allowed = allowed,
            policyAllowed = allowed,
            requirement = requirement,
            reason = reason,
            entitlement = entitlement,
            mode = mode,
            upgradeMessage = upgradeMessage
        )
    }

    private fun result(
        featureId: String,
        allowed: Boolean,
        policyAllowed: Boolean,
        requirement: GoogleFeatureRequirement,
        reason: GoogleFeatureAccessReason,
        entitlement: GoogleBillingEntitlement,
        mode: GoogleFeatureGateMode,
        upgradeMessage: String
    ): GoogleFeatureAccessResult {
        return GoogleFeatureAccessResult(
            featureId = featureId,
            allowed = allowed,
            policyAllowed = policyAllowed,
            requirement = requirement,
            reason = reason,
            entitlement = entitlement,
            mode = mode,
            upgradeMessage = upgradeMessage
        )
    }
}
