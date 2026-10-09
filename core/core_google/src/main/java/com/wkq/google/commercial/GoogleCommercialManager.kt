package com.wkq.google.commercial

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.devhub.sdk.AnalyticsMode
import com.devhub.sdk.DevHub
import com.devhub.sdk.DevHubConfig

/** Optional install/daily-active statistics; no media, device or account data. */
object GoogleCommercialManager {
    private const val TAG = "SignageDevHub"
    private const val CONSENT_KEY = "collection_consent_v1"
    private var preferences: SharedPreferences? = null
    @Volatile private var client: DevHub? = null
    private var initialized = false

    @Synchronized
    fun initialize(context: Context, config: GoogleCommercialConfig) {
        if (initialized) return
        initialized = true
        preferences = context.applicationContext.getSharedPreferences(
            "local_signage_analytics", Context.MODE_PRIVATE
        )
        if (!config.enabled || config.apiUrl.isBlank() || config.appKey.isBlank()) return
        try {
            val sdk = DevHub.initialize(
                context.applicationContext,
                DevHubConfig(
                    apiUrl = config.apiUrl,
                    appKey = config.appKey,
                    storageNamespace = "local_signage",
                    collectionEnabled = preferences?.getBoolean(CONSENT_KEY, false) == true,
                    analyticsMode = AnalyticsMode.CORE_ONLY,
                    collectDeviceInfo = false,
                    collectLocale = false,
                    allowedEventProperties = emptySet(),
                    playIntegrityCloudProjectNumber = config.playIntegrityCloudProjectNumber,
                    onError = { Log.w(TAG, it) }
                )
            )
            if (sdk.initializationError == null) client = sdk
            else Log.w(TAG, "INITIALIZATION_FAILED")
        } catch (_: Exception) {
            // SDK failures must not interrupt local playback or server initialization.
            Log.w(TAG, "INITIALIZATION_FAILED")
        }
    }

    fun isConfigured(): Boolean = client != null

    fun isCollectionEnabled(): Boolean = client?.collectionEnabled == true

    fun setCollectionEnabled(enabled: Boolean, onComplete: (Boolean) -> Unit) {
        val sdk = client ?: run { onComplete(false); return }
        // Persist withdrawal before clearing the SDK queue, including offline withdrawals.
        preferences?.edit()?.putBoolean(CONSENT_KEY, enabled)?.apply()
        try {
            sdk.setCollectionEnabled(enabled) { result ->
                if (enabled && !result.successful) {
                    preferences?.edit()?.putBoolean(CONSENT_KEY, false)?.apply()
                    sdk.setCollectionEnabled(false)
                }
                onComplete(result.successful)
            }
        } catch (_: Exception) {
            preferences?.edit()?.putBoolean(CONSENT_KEY, false)?.apply()
            Log.w(TAG, "COLLECTION_UPDATE_FAILED")
            onComplete(false)
        }
    }

    fun reportActive() {
        val sdk = client?.takeIf { it.collectionEnabled } ?: return
        try {
            sdk.start()
        } catch (_: Exception) {
            Log.w(TAG, "ACTIVE_REPORT_FAILED")
        }
    }
}
