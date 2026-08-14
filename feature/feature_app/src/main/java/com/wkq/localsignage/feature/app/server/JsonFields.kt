package com.wkq.localsignage.feature.app.server

import org.json.JSONObject

internal fun JSONObject.stringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return normalizedString(optString(key))
}

internal fun normalizedString(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
