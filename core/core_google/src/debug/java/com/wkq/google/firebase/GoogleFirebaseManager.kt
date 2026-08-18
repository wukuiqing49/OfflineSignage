package com.wkq.google.firebase

import android.content.Context
import com.wkq.google.GoogleKit
import com.wkq.google.GoogleKitConfig

/** Debug 变体不打包 Firebase SDK，所有 Analytics 操作均为空实现。 */
object GoogleFirebaseManager {

    fun initialize(
        context: Context,
        config: GoogleKitConfig = GoogleKit.currentConfig()
    ): Boolean = false

    fun isAnalyticsAvailable(): Boolean = false

    fun getLastInitializeErrorMessage(): String = "Firebase Analytics is disabled in debug builds."

    fun logEvent(name: String, params: Map<String, Any?> = emptyMap()): Boolean = false

    fun setUserId(userId: String?): Boolean = false

    fun setUserProperty(name: String, value: String?): Boolean = false

    fun setAnalyticsCollectionEnabled(enabled: Boolean): Boolean = false

    fun resetAnalyticsData(): Boolean = false
}
