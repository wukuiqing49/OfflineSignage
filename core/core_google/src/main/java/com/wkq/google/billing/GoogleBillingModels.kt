package com.wkq.google.billing

object GooglePurchaseState {
    const val UNSPECIFIED = 0
    const val PURCHASED = 1
    const val PENDING = 2
}

object GoogleBillingResponseCode {
    const val USER_CANCELED = 1
}

data class GooglePricingPhase(
    val formattedPrice: String,
    val priceCurrencyCode: String = "",
    val billingPeriod: String,
    val recurrenceMode: Int,
    val billingCycleCount: Int
)

data class GoogleProduct(
    val productId: String,
    val productType: String,
    val title: String,
    val description: String,
    val formattedPrice: String,
    val priceCurrencyCode: String = "",
    val offerToken: String = "",
    val baseProductId: String = productId.substringBefore(":"),
    val basePlanId: String = "",
    val billingPeriod: String = "",
    val pricingPhases: List<GooglePricingPhase> = emptyList()
)

data class GooglePurchase(
    val products: List<String>,
    val purchaseToken: String,
    val purchaseTimeMillis: Long,
    val isAcknowledged: Boolean,
    val purchaseState: Int,
    val obfuscatedAccountId: String = "",
    val originalJson: String = "",
    val signature: String = ""
)

data class GoogleBillingResponse(
    val isSuccess: Boolean,
    val responseCode: Int,
    val message: String = ""
)
