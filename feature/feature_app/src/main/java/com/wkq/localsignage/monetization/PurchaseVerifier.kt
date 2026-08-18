package com.wkq.localsignage.monetization

import android.util.Base64
import com.google.gson.JsonParser
import com.wkq.google.billing.GooglePurchase
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

internal object PurchaseVerifier {
    fun verify(
        purchase: GooglePurchase,
        expectedPackageName: String,
        allowedProductIds: Set<String>,
        licensePublicKey: String,
        allowMissingPublicKey: Boolean
    ): Boolean {
        if (purchase.products.isEmpty() || purchase.products.any { it !in allowedProductIds }) return false
        val packageName = runCatching {
            JsonParser.parseString(purchase.originalJson)
                .asJsonObject
                .get("packageName")
                ?.asString
        }.getOrNull()
        if (packageName != expectedPackageName) return false
        if (licensePublicKey.isBlank()) return allowMissingPublicKey
        if (purchase.signature.isBlank()) return false
        return runCatching {
            val keyBytes = Base64.decode(licensePublicKey, Base64.DEFAULT)
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(keyBytes))
            Signature.getInstance("SHA1withRSA").run {
                initVerify(publicKey)
                update(purchase.originalJson.toByteArray(Charsets.UTF_8))
                verify(Base64.decode(purchase.signature, Base64.DEFAULT))
            }
        }.getOrDefault(false)
    }
}
