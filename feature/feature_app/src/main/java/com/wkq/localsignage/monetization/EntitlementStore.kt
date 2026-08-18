package com.wkq.localsignage.monetization

import android.content.Context
import kotlin.math.max

internal class EntitlementStore(
    context: Context,
    private val clock: MonetizationClock = SystemMonetizationClock
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun snapshot(): EntitlementSnapshot {
        val wallClockNow = clock.nowEpochMillis()
        val firstLaunchAt = preferences.getLong(KEY_TRIAL_STARTED_AT, 0L).takeIf { it > 0L }
            ?: wallClockNow.also {
                preferences.edit().putLong(KEY_TRIAL_STARTED_AT, it).apply()
            }
        val lastObservedAt = preferences.getLong(KEY_LAST_OBSERVED_AT, firstLaunchAt)
        val rollbackDetected = wallClockNow + CLOCK_ROLLBACK_TOLERANCE_MS < lastObservedAt
        val effectiveNow = max(wallClockNow, lastObservedAt)
        preferences.edit().putLong(KEY_LAST_OBSERVED_AT, effectiveNow).apply()
        return EntitlementSnapshot(
            trialStartedAtEpochMillis = firstLaunchAt,
            effectiveNowEpochMillis = effectiveNow,
            lastSubscriptionVerifiedAtEpochMillis = preferences
                .getLong(KEY_SUBSCRIPTION_VERIFIED_AT, 0L)
                .takeIf { it > 0L },
            lifetimeVerified = preferences.getBoolean(KEY_LIFETIME_VERIFIED, false),
            clockRollbackDetected = rollbackDetected
        )
    }

    @Synchronized
    fun saveVerifiedPurchases(hasLifetime: Boolean, hasSubscription: Boolean) {
        val now = clock.nowEpochMillis()
        preferences.edit()
            .putBoolean(KEY_LIFETIME_VERIFIED, hasLifetime)
            .putLong(KEY_SUBSCRIPTION_VERIFIED_AT, if (hasSubscription) now else 0L)
            .putLong(KEY_LAST_OBSERVED_AT, max(now, preferences.getLong(KEY_LAST_OBSERVED_AT, now)))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "local_signage_entitlement"
        const val KEY_TRIAL_STARTED_AT = "trial_started_at"
        const val KEY_LAST_OBSERVED_AT = "last_observed_at"
        const val KEY_SUBSCRIPTION_VERIFIED_AT = "subscription_verified_at"
        const val KEY_LIFETIME_VERIFIED = "lifetime_verified"
        const val CLOCK_ROLLBACK_TOLERANCE_MS = 5 * 60 * 1000L
    }
}
