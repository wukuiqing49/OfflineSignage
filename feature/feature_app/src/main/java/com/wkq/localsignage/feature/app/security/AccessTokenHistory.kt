package com.wkq.localsignage.feature.app.security

import java.security.MessageDigest

data class ExpiringAccessToken(val value: String, val expiresAt: Long)

object AccessTokenHistory {
    fun parse(raw: String?): List<ExpiringAccessToken> =
        raw.orEmpty().lineSequence().mapNotNull { entry ->
            val separator = entry.lastIndexOf('|')
            if (separator <= 0) return@mapNotNull null
            val token = entry.substring(0, separator)
            val expiresAt = entry.substring(separator + 1).toLongOrNull() ?: return@mapNotNull null
            ExpiringAccessToken(token, expiresAt)
        }.toList()

    fun encode(tokens: List<ExpiringAccessToken>, now: Long, limit: Int): String? =
        tokens.filter { it.expiresAt > now }
            .fold(linkedMapOf<String, ExpiringAccessToken>()) { active, token ->
                val previous = active.remove(token.value)
                active[token.value] = if (previous != null && previous.expiresAt > token.expiresAt) {
                    previous
                } else {
                    token
                }
                active
            }
            .values
            .toList()
            .takeLast(limit.coerceAtLeast(0))
            .joinToString("\n") { "${it.value}|${it.expiresAt}" }
            .ifBlank { null }

    fun contains(tokens: List<ExpiringAccessToken>, candidate: String, now: Long): Boolean =
        candidate.isNotEmpty() && tokens.any { token ->
            token.expiresAt > now && MessageDigest.isEqual(
                candidate.toByteArray(),
                token.value.toByteArray()
            )
        }
}
