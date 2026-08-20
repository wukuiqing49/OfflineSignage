package com.wkq.localsignage.feature.app.server

import org.json.JSONObject

internal fun JSONObject.stringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return normalizedString(optString(key))
}

internal fun normalizedString(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

internal fun normalizedPairingCode(value: String?): String? = value
    ?.filterNot(Char::isWhitespace)
    ?.takeIf { code -> code.length == 6 && code.all { it in '0'..'9' } }
