package com.wkq.google.billing.gate

class GoogleFeatureAccessException(
    val accessResult: GoogleFeatureAccessResult
) : IllegalStateException(
    accessResult.upgradeMessage.ifBlank { "Feature requires a paid entitlement." }
)
