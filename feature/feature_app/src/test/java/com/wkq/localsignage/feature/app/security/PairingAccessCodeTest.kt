package com.wkq.localsignage.feature.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingAccessCodeTest {
    @Test
    fun createReturnsStableSixDigitCode() {
        val code = PairingAccessCode.create("pairing-token")

        assertEquals(6, code.length)
        assertTrue(code.all(Char::isDigit))
        assertEquals(code, PairingAccessCode.create("pairing-token"))
    }

    @Test
    fun matchesAcceptsSurroundingWhitespace() {
        val code = PairingAccessCode.create("pairing-token")

        assertTrue(PairingAccessCode.matches("pairing-token", code))
        assertTrue(PairingAccessCode.matches("pairing-token", "  $code  "))
    }

    @Test
    fun matchesRejectsWrongOrMalformedCodes() {
        val code = PairingAccessCode.create("pairing-token")

        assertFalse(PairingAccessCode.matches("another-token", code))
        assertFalse(PairingAccessCode.matches("pairing-token", "12345"))
        assertFalse(PairingAccessCode.matches("pairing-token", "12345A"))
        assertFalse(PairingAccessCode.matches(null, code))
    }
}
