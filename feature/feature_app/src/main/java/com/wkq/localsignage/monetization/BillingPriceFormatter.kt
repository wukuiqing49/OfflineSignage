package com.wkq.localsignage.monetization

import com.wkq.google.billing.GoogleProduct

internal fun playDisplayPrice(
    product: GoogleProduct,
    formatRecurringPrice: (price: String, billingPeriod: String) -> String
): String? {
    val localizedPrice = product.formattedPrice.trim().takeIf { it.isNotEmpty() } ?: return null
    return when (product.billingPeriod) {
        "P1M", "P1Y" -> formatRecurringPrice(localizedPrice, product.billingPeriod)
        else -> localizedPrice
    }
}
