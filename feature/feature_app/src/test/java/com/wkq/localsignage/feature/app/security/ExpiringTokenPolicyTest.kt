package com.wkq.localsignage.feature.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpiringTokenPolicyTest {
    @Test
    fun acceptsOnlyExactUnexpiredToken() {
        assertTrue(ExpiringTokenPolicy.matches("secret", 101, "secret", 100))
        assertFalse(ExpiringTokenPolicy.matches("secret", 100, "secret", 100))
        assertFalse(ExpiringTokenPolicy.matches("secret", 101, "SECRET", 100))
        assertFalse(ExpiringTokenPolicy.matches("secret", 101, null, 100))
        assertFalse(ExpiringTokenPolicy.matches(null, 101, "secret", 100))
    }

    @Test
    fun trimsTransportWhitespaceFromCandidate() {
        assertTrue(ExpiringTokenPolicy.matches("secret", 101, "  secret  ", 100))
    }
}
