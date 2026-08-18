package com.wkq.google.billing

data class GoogleBillingCatalog(
    val subscriptions: List<GoogleProduct> = emptyList(),
    val oneTimeProducts: List<GoogleProduct> = emptyList()
) {
    val isEmpty: Boolean
        get() = subscriptions.isEmpty() && oneTimeProducts.isEmpty()
}

enum class GoogleBillingEntitlementState {
    FREE,
    PRO,
    UNKNOWN
}

data class GoogleBillingEntitlement(
    val state: GoogleBillingEntitlementState = GoogleBillingEntitlementState.UNKNOWN,
    val hasSubscription: Boolean = false,
    val hasLifetimeUnlock: Boolean = false,
    val activeSubscriptionIds: List<String> = emptyList(),
    val ownedOneTimeProductIds: List<String> = emptyList(),
    val source: String = "google_play",
    val errorMessage: String = ""
) {
    val isPro: Boolean
        get() = state == GoogleBillingEntitlementState.PRO

    val isKnown: Boolean
        get() = state != GoogleBillingEntitlementState.UNKNOWN

    val activePlanId: String
        get() = activeSubscriptionIds.firstOrNull()
            ?: ownedOneTimeProductIds.firstOrNull()
            .orEmpty()
}
