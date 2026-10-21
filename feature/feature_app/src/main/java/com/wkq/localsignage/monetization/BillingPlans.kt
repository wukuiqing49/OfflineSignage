package com.wkq.localsignage.monetization

import com.wkq.google.billing.GoogleBillingCatalog
import com.wkq.google.billing.GoogleProduct
import com.wkq.google.billing.GoogleProductType

internal data class BillingPlans(
    val monthly: GoogleProduct?,
    val yearly: GoogleProduct?,
    val lifetime: GoogleProduct?
) {
    val hasAny: Boolean get() = monthly != null || yearly != null || lifetime != null
    val hasAll: Boolean get() = monthly != null && yearly != null && lifetime != null
}

/** 只允许购买 Play 返回有效价格和 offer 的商品，缺少某项不影响其他套餐。 */
internal fun GoogleBillingCatalog.availablePlans(): BillingPlans {
    val recurring = subscriptions.filter {
        it.productType == GoogleProductType.SUBS &&
            it.baseProductId == MonetizationRepository.PRO_SUBSCRIPTION_ID &&
            it.formattedPrice.isNotBlank() && it.offerToken.isNotBlank()
    }
    return BillingPlans(
        monthly = recurring.firstOrNull { it.basePlanId in MonetizationRepository.monthlyBasePlanIds },
        yearly = recurring.firstOrNull { it.basePlanId == MonetizationRepository.YEARLY_BASE_PLAN_ID },
        lifetime = oneTimeProducts.firstOrNull {
            it.productType == GoogleProductType.IN_APP &&
                it.productId == MonetizationRepository.LIFETIME_PRODUCT_ID &&
                it.formattedPrice.isNotBlank() && it.offerToken.isNotBlank()
        }
    )
}

/** SUBS 与 INAPP 独立加载，单侧失败不丢弃另一侧成功返回的商品。 */
internal fun combineBillingCatalog(
    subscriptions: Result<List<GoogleProduct>>,
    oneTime: Result<List<GoogleProduct>>
): Result<GoogleBillingCatalog> {
    if (subscriptions.isFailure && oneTime.isFailure) {
        return Result.failure(requireNotNull(subscriptions.exceptionOrNull()))
    }
    return Result.success(GoogleBillingCatalog(
        subscriptions = subscriptions.getOrDefault(emptyList()),
        oneTimeProducts = oneTime.getOrDefault(emptyList())
    ))
}
