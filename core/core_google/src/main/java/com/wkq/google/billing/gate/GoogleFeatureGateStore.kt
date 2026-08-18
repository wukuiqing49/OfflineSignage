package com.wkq.google.billing.gate

import android.content.Context

internal object GoogleFeatureGateStore {
    private const val PREF_NAME = "google_feature_gate_state"
    private const val KEY_MODE_OVERRIDE = "mode_override"

    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun getModeOverride(): GoogleFeatureGateMode? {
        val raw = preferences().getString(KEY_MODE_OVERRIDE, "").orEmpty()
        return raw.takeIf { it.isNotBlank() }?.let {
            runCatching { GoogleFeatureGateMode.valueOf(it) }.getOrNull()
        }
    }

    fun setModeOverride(mode: GoogleFeatureGateMode?) {
        preferences().edit().apply {
            if (mode == null) {
                remove(KEY_MODE_OVERRIDE)
            } else {
                putString(KEY_MODE_OVERRIDE, mode.name)
            }
        }.apply()
    }

    private fun preferences() = requireNotNull(appContext) {
        "GoogleFeatureGateStore has not been initialized."
    }.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
}
