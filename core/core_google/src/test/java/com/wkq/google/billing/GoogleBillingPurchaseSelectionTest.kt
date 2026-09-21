package com.wkq.google.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleBillingPurchaseSelectionTest {
    @Test
    fun acceptsOnlyTheExactFreshOffer() {
        val product = GoogleProduct(
            productId = "pro_subscription:pro-yearly",
            productType = GoogleProductType.SUBS,
            title = "Pro",
            description = "",
            formattedPrice = "\$10",
            offerToken = "fresh-token"
        )

        assertEquals(product, listOf(product).matchingPurchaseProduct(product.productId, "fresh-token"))
        assertNull(listOf(product).matchingPurchaseProduct(product.productId, "stale-token"))
        assertNull(listOf(product).matchingPurchaseProduct(product.productId, ""))
    }
}
