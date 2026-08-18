package com.wkq.google.billing

import com.android.billingclient.api.Purchase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleBillingPolicyTest {

    @Test
    fun obfuscatedAccountId_isStableAndDoesNotExposeRawId() {
        val rawAccountId = "user@example.com"

        val first = obfuscateBillingAccountId(rawAccountId)
        val second = obfuscateBillingAccountId("  $rawAccountId  ")

        assertEquals(first, second)
        assertEquals(64, first.length)
        assertFalse(first.contains(rawAccountId))
    }

    @Test
    fun currentAccountFilter_acceptsMatchingAndLegacyPurchasesOnly() {
        val accountId = obfuscateBillingAccountId("account-a")
        val purchases = listOf(
            purchase("matching", accountId),
            purchase("legacy", ""),
            purchase("other", obfuscateBillingAccountId("account-b"))
        )

        val filtered = purchases.filterCurrentAccountPurchases(
            expectedAccountId = accountId,
            requireAccountId = true
        )

        assertEquals(listOf("matching", "legacy"), filtered.map { it.purchaseToken })
    }

    @Test
    fun currentAccountFilter_rejectsAllPurchasesWhenAccountIsRequired() {
        val purchases = listOf(purchase("legacy", ""))

        val filtered = purchases.filterCurrentAccountPurchases(
            expectedAccountId = "",
            requireAccountId = true
        )

        assertTrue(filtered.isEmpty())
    }

    @Test
    fun currentAccountFilter_acceptsPlayPurchasesWithoutAppAccount() {
        val purchases = listOf(purchase("play-account", ""))

        val filtered = purchases.filterCurrentAccountPurchases(
            expectedAccountId = "",
            requireAccountId = false
        )

        assertEquals(purchases, filtered)
    }

    private fun purchase(token: String, accountId: String): GooglePurchase {
        return GooglePurchase(
            products = listOf("pro_monthly"),
            purchaseToken = token,
            purchaseTimeMillis = 1L,
            isAcknowledged = true,
            purchaseState = Purchase.PurchaseState.PURCHASED,
            obfuscatedAccountId = accountId
        )
    }
}
