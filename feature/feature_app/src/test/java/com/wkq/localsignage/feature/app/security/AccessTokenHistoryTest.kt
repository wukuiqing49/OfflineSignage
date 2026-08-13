package com.wkq.localsignage.feature.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessTokenHistoryTest {
    @Test
    fun parseIgnoresMalformedEntries() {
        val parsed = AccessTokenHistory.parse("first|200\ninvalid\nsecond|not-a-time\nthird|300")

        assertEquals(
            listOf(ExpiringAccessToken("first", 200), ExpiringAccessToken("third", 300)),
            parsed
        )
    }

    @Test
    fun encodeDropsExpiredEntriesAndKeepsNewestLimit() {
        val encoded = AccessTokenHistory.encode(
            listOf(
                ExpiringAccessToken("expired", 99),
                ExpiringAccessToken("first", 200),
                ExpiringAccessToken("second", 300),
                ExpiringAccessToken("third", 400)
            ),
            now = 100,
            limit = 2
        )

        assertEquals("second|300\nthird|400", encoded)
    }

    @Test
    fun encodeReturnsNullWhenNothingIsActive() {
        assertNull(AccessTokenHistory.encode(listOf(ExpiringAccessToken("expired", 100)), 100, 5))
    }

    @Test
    fun containsAcceptsOnlyActiveExactToken() {
        val tokens = listOf(ExpiringAccessToken("active", 200), ExpiringAccessToken("expired", 100))

        assertTrue(AccessTokenHistory.contains(tokens, "active", 100))
        assertFalse(AccessTokenHistory.contains(tokens, "expired", 100))
        assertFalse(AccessTokenHistory.contains(tokens, "ACTIVE", 100))
        assertFalse(AccessTokenHistory.contains(tokens, "", 100))
    }
}
