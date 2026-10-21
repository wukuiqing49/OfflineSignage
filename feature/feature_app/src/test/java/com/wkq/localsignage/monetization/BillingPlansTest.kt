package com.wkq.localsignage.monetization

import com.wkq.google.billing.GoogleBillingCatalog
import com.wkq.google.billing.GoogleProduct
import org.junit.Assert.*
import org.junit.Test

class BillingPlansTest {
    private fun subscription(plan: String) = GoogleProduct(
        productId = "pro_subscription:$plan", productType = "subs", title = "Pro",
        description = "", formattedPrice = "$9.99", offerToken = "test-offer-$plan", basePlanId = plan
    )
    private val lifetime = GoogleProduct(
        productId = "pro_lifetime", productType = "inapp", title = "Lifetime",
        description = "", formattedPrice = "$99.99", offerToken = "test-onetime"
    )

    @Test fun `all three plans retain exact purchase identifiers and offers`() {
        val month = subscription("pro-month")
        val year = subscription("pro-yearly")
        val plans = GoogleBillingCatalog(listOf(month, year), listOf(lifetime)).availablePlans()
        assertTrue(plans.hasAll)
        assertSame(month, plans.monthly)
        assertSame(year, plans.yearly)
        assertSame(lifetime, plans.lifetime)
    }
    @Test fun `legacy monthly plan remains purchasable`() {
        assertEquals("pro-mouth", GoogleBillingCatalog(listOf(subscription("pro-mouth"))).availablePlans().monthly?.basePlanId)
    }
    @Test fun `lifetime alone is a usable catalog without yearly`() {
        val plans = GoogleBillingCatalog(oneTimeProducts = listOf(lifetime)).availablePlans()
        assertTrue(plans.hasAny)
        assertFalse(plans.hasAll)
        assertNull(plans.yearly)
    }
    @Test fun `invalid offers and unrelated products cannot enable purchases`() {
        val plans = GoogleBillingCatalog(
            listOf(subscription("pro-month").copy(formattedPrice = " "), subscription("pro-yearly").copy(offerToken = "")),
            listOf(lifetime.copy(productId = "unrelated"))
        ).availablePlans()
        assertFalse(plans.hasAny)
    }
    @Test fun `subscription query failure preserves lifetime`() {
        val catalog = combineBillingCatalog(Result.failure(Exception("subs unavailable")), Result.success(listOf(lifetime))).getOrThrow()
        assertSame(lifetime, catalog.availablePlans().lifetime)
    }
    @Test fun `one time query failure preserves subscriptions`() {
        val year = subscription("pro-yearly")
        val catalog = combineBillingCatalog(Result.success(listOf(year)), Result.failure(Exception("inapp unavailable"))).getOrThrow()
        assertSame(year, catalog.availablePlans().yearly)
    }
    @Test fun `both failed queries report failure`() {
        assertTrue(combineBillingCatalog(Result.failure(Exception("subs")), Result.failure(Exception("inapp"))).isFailure)
    }
}
