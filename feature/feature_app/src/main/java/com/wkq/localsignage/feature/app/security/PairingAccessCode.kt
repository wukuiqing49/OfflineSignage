package com.wkq.localsignage.feature.app.security

import java.nio.charset.StandardCharsets
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Locale

object PairingAccessCode {
    private const val CODE_LENGTH = 6
    private const val CODE_SPACE = 1_000_000L

    fun create(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
        val value = ByteBuffer.wrap(digest, 0, Long.SIZE_BYTES).long.toULong() % CODE_SPACE.toULong()
        return String.format(Locale.US, "%0${CODE_LENGTH}d", value.toLong())
    }

    fun matches(token: String?, candidate: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val normalized = candidate.orEmpty()
            .filterNot { it.isWhitespace() }
        if (normalized.length != CODE_LENGTH || normalized.any { !it.isDigit() }) return false
        val expected = create(token)
        return MessageDigest.isEqual(
            normalized.toByteArray(StandardCharsets.US_ASCII),
            expected.toByteArray(StandardCharsets.US_ASCII)
        )
    }
}
