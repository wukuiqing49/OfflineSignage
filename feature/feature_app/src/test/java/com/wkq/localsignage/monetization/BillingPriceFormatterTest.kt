package com.wkq.localsignage.monetization

import com.wkq.google.billing.GoogleProduct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BillingPriceFormatterTest {
    @Test
    fun `annual price preserves Google Play localized formatting`() {
        val product = product(formattedPrice = "1.299,00 €", billingPeriod = "P1Y")

        val displayed = playDisplayPrice(product) { price, period -> "$price / $period" }

        assertEquals("1.299,00 € / P1Y", displayed)
    }

    @Test
    fun `blank Play price is not replaced with a locally reconstructed amount`() {
        val product = product(formattedPrice = " ", billingPeriod = "P1Y")

        assertNull(playDisplayPrice(product) { price, period -> "$price / $period" })
    }

    private fun product(formattedPrice: String, billingPeriod: String) = GoogleProduct(
        productId = "pro_subscription:pro-yearly",
        productType = "subs",
        title = "Local Signage Pro",
        description = "Annual subscription",
        formattedPrice = formattedPrice,
        billingPeriod = billingPeriod
    )
}
