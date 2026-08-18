package com.wkq.google.billing.gate

sealed class GoogleFeatureRequirement {
    data object Free : GoogleFeatureRequirement()
    data object Pro : GoogleFeatureRequirement()
    data object Subscription : GoogleFeatureRequirement()
    data object Lifetime : GoogleFeatureRequirement()
    data class AnyProduct(val productIds: Set<String>) : GoogleFeatureRequirement()
    data class LimitedFree(val freeLimit: Int) : GoogleFeatureRequirement()
}
