package com.wkq.google.billing.gate

data class GoogleFeatureGateConfig(
    val enabled: Boolean = true,
    val mode: GoogleFeatureGateMode = GoogleFeatureGateMode.ENFORCE,
    val rules: List<GoogleFeatureRule> = emptyList()
) {
    internal fun ruleFor(featureId: String): GoogleFeatureRule? {
        return rules.firstOrNull { it.featureId == featureId }
    }
}
