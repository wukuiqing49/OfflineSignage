package com.wkq.google.firebase

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.wkq.google.GoogleKit
import com.wkq.google.GoogleKitConfig

/** Release 变体中的 Firebase Analytics 门面。 */
object GoogleFirebaseManager {

    @Volatile
    private var analytics: FirebaseAnalytics? = null

    @Volatile
    private var analyticsEnabled: Boolean = false

    @Volatile
    private var lastInitializeError: Throwable? = null

    fun initialize(
        context: Context,
        config: GoogleKitConfig = GoogleKit.currentConfig()
    ): Boolean {
        analyticsEnabled = config.enableFirebaseAnalytics
        if (!analyticsEnabled) {
            analytics = null
            return false
        }
        return runCatching {
            analytics = FirebaseAnalytics.getInstance(context.applicationContext).also {
                it.setAnalyticsCollectionEnabled(true)
            }
            lastInitializeError = null
            true
        }.getOrElse { throwable ->
            analytics = null
            lastInitializeError = throwable
            false
        }
    }

    fun isAnalyticsAvailable(): Boolean = analyticsEnabled && analytics != null

    fun getLastInitializeErrorMessage(): String = lastInitializeError?.message.orEmpty()

    fun logEvent(name: String, params: Map<String, Any?> = emptyMap()): Boolean {
        val firebaseAnalytics = analytics ?: return false
        if (!analyticsEnabled || name.isBlank()) return false
        firebaseAnalytics.logEvent(name, params.toFirebaseBundle())
        return true
    }

    fun setUserId(userId: String?): Boolean {
        val firebaseAnalytics = analytics ?: return false
        if (!analyticsEnabled) return false
        firebaseAnalytics.setUserId(userId)
        return true
    }

    fun setUserProperty(name: String, value: String?): Boolean {
        val firebaseAnalytics = analytics ?: return false
        if (!analyticsEnabled || name.isBlank()) return false
        firebaseAnalytics.setUserProperty(name, value)
        return true
    }

    fun setAnalyticsCollectionEnabled(enabled: Boolean): Boolean {
        analyticsEnabled = enabled
        val firebaseAnalytics = analytics ?: return false
        firebaseAnalytics.setAnalyticsCollectionEnabled(enabled)
        return true
    }

    fun resetAnalyticsData(): Boolean {
        val firebaseAnalytics = analytics ?: return false
        firebaseAnalytics.resetAnalyticsData()
        return true
    }
}

private fun Map<String, Any?>.toFirebaseBundle(): Bundle {
    val bundle = Bundle()
    forEach { (key, value) ->
        if (key.isBlank() || value == null) return@forEach
        when (value) {
            is String -> bundle.putString(key, value)
            is Int -> bundle.putLong(key, value.toLong())
            is Long -> bundle.putLong(key, value)
            is Short -> bundle.putLong(key, value.toLong())
            is Byte -> bundle.putLong(key, value.toLong())
            is Double -> bundle.putDouble(key, value)
            is Float -> bundle.putDouble(key, value.toDouble())
            is Boolean -> bundle.putString(key, value.toString())
        }
    }
    return bundle
}
