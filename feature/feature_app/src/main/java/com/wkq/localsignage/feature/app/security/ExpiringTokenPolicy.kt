package com.wkq.localsignage.feature.app.security

import java.security.MessageDigest

object ExpiringTokenPolicy {
    fun matches(current: String?, expiresAt: Long, candidate: String?, now: Long): Boolean {
        val expected = current?.toByteArray() ?: return false
        val supplied = candidate?.trim()?.takeIf { it.isNotEmpty() }?.toByteArray() ?: return false
        return expiresAt > now && MessageDigest.isEqual(supplied, expected)
    }
}
