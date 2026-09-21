package com.wkq.localsignage.monetization

import com.wkq.google.billing.GooglePurchase
import com.wkq.google.billing.GooglePurchaseState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

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

    @Test
    fun releaseVerificationAcceptsOnlyTheOriginalSignedPurchase() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val originalJson = "{\"packageName\":\"$PACKAGE_NAME\"}"
        val signature = Signature.getInstance("SHA1withRSA").run {
            initSign(keyPair.private)
            update(originalJson.toByteArray(Charsets.UTF_8))
            sign()
        }
        val licensePublicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val signedPurchase = purchase().copy(
            originalJson = originalJson,
            signature = Base64.getEncoder().encodeToString(signature)
        )

        assertTrue(
            PurchaseVerifier.verify(
                purchase = signedPurchase,
                expectedPackageName = PACKAGE_NAME,
                allowedProductIds = setOf(MonetizationRepository.PRO_SUBSCRIPTION_ID),
                licensePublicKey = licensePublicKey,
                allowMissingPublicKey = false
            )
        )
        assertFalse(
            PurchaseVerifier.verify(
                purchase = signedPurchase.copy(
                    originalJson = "{\"packageName\":\"$PACKAGE_NAME\",\"tampered\":true}"
                ),
                expectedPackageName = PACKAGE_NAME,
                allowedProductIds = setOf(MonetizationRepository.PRO_SUBSCRIPTION_ID),
                licensePublicKey = licensePublicKey,
                allowMissingPublicKey = false
            )
        )
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
