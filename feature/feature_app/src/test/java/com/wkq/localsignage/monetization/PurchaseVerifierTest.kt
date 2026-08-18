package com.wkq.localsignage.monetization

import com.wkq.google.billing.GooglePurchase
import com.wkq.google.billing.GooglePurchaseState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseVerifierTest {
    @Test
    fun debugVerificationAcceptsValidPayloadWithoutPublicKey() {
        assertTrue(verify(purchase()))
    }

    @Test
    fun verificationRejectsWrongPackage() {
        assertFalse(verify(purchase(packageName = "example.invalid")))
    }

    @Test
    fun verificationRejectsUnknownProduct() {
        assertFalse(verify(purchase(productId = "unknown_product")))
    }

    @Test
    fun verificationRejectsMalformedPayload() {
        assertFalse(verify(purchase().copy(originalJson = "not-json")))
    }

    private fun verify(purchase: GooglePurchase): Boolean = PurchaseVerifier.verify(
        purchase = purchase,
        expectedPackageName = PACKAGE_NAME,
        allowedProductIds = setOf(MonetizationRepository.PRO_SUBSCRIPTION_ID),
        licensePublicKey = "",
        allowMissingPublicKey = true
    )

    private fun purchase(
        packageName: String = PACKAGE_NAME,
        productId: String = MonetizationRepository.PRO_SUBSCRIPTION_ID
    ) = GooglePurchase(
        products = listOf(productId),
        purchaseToken = "token-not-persisted",
        purchaseTimeMillis = 1L,
        isAcknowledged = false,
        purchaseState = GooglePurchaseState.PURCHASED,
        originalJson = "{\"packageName\":\"$packageName\"}"
    )

    private companion object {
        const val PACKAGE_NAME = "com.wkq.localsignage"
    }
}
