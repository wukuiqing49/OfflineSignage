package com.wkq.localsignage.feature.app.security

import java.security.MessageDigest

object CommandRequestFingerprint {
    fun create(values: Map<String, String?>): String {
        val canonical = FIELDS.joinToString("|") { key -> "$key=${values[key].orEmpty()}" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private val FIELDS = listOf("action", "resourceId", "sceneId", "playlistId", "value", "revision")
}
