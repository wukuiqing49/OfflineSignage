package com.wkq.localsignage.feature.app.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PairingAttemptLimiterTest {
    @Test
    fun blocksAfterConfiguredFailuresAndRecoversAfterLockout() {
        var now = 1_000L
        val limiter = PairingAttemptLimiter(maxFailures = 3, windowMs = 1_000L, lockoutMs = 2_000L) { now }

        assertEquals(0L, limiter.recordFailure())
        assertEquals(0L, limiter.recordFailure())
        assertEquals(2_000L, limiter.recordFailure())
        assertEquals(2_000L, limiter.retryAfterMs())

        now += 2_000L
        assertEquals(0L, limiter.retryAfterMs())
        assertEquals(0L, limiter.recordFailure())
    }

    @Test
    fun resetClearsFailuresAndLockout() {
        val limiter = PairingAttemptLimiter(maxFailures = 1, lockoutMs = 2_000L)

        limiter.recordFailure()
        limiter.reset()

        assertEquals(0L, limiter.retryAfterMs())
    }
}
