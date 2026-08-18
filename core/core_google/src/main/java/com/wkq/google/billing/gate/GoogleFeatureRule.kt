package com.wkq.google.billing.gate

data class GoogleFeatureRule(
    val featureId: String,
    val requirement: GoogleFeatureRequirement,
    val upgradeMessage: String = ""
)
