package com.wkq.localsignage.feature.app

import android.content.Context
import com.wkq.google.GoogleKit
import com.wkq.google.GoogleKitConfig
import com.wkq.google.billing.gate.GoogleFeatureGateConfig
import com.wkq.localsignage.monetization.MonetizationRepository
import com.wkq.localsignage.feature.app.runtime.SignageRuntime
import com.wkq.util.CoreUtils

object FeatureAppEntry {

    fun initialize(
        context: Context,
        debug: Boolean,
        playLicensePublicKey: String,
        googleServerClientId: String
    ) {
        val appContext = context.applicationContext
        GoogleKit.initialize(
            context = appContext,
            config = GoogleKitConfig(
                serverClientId = googleServerClientId,
                enableAds = false,
                billingInAppProductIds = listOf(MonetizationRepository.LIFETIME_PRODUCT_ID),
                billingSubscriptionIds = listOf(MonetizationRepository.PRO_SUBSCRIPTION_ID),
                billingRequireAppAccount = false,
                featureGateConfig = GoogleFeatureGateConfig(enabled = false),
                enableFirebaseAnalytics = !debug,
                appName = appContext.getString(R.string.app_name),
                feedbackEmail = "wukuiqing@gmail.com"
            )
        )
        MonetizationRepository.initialize(
            context = appContext,
            playLicensePublicKey = playLicensePublicKey,
            allowMissingPublicKey = debug
        )
        SignageRuntime.initialize(appContext)
    }

    fun isAvailable(): Boolean = CoreUtils.isInitialized()
}
