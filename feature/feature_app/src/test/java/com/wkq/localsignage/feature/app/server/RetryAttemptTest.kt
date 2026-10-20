package com.wkq.localsignage.feature.app.server

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RetryAttemptTest {
    @Test fun failedAttemptDoesNotPreventNextAttempt() = runBlocking {
        var errors = 0
        assertFalse(retryAttempt({ errors++ }) { throw java.io.IOException("storage unavailable") })
        var completed = false
        assertTrue(retryAttempt({ errors++ }) { completed = true })
        assertTrue(completed)
        assertEquals(1, errors)
    }

    @Test fun cancellationIsPropagated() = runBlocking {
        val expected = CancellationException("service stopped")
        try {
            retryAttempt({ fail("Cancellation is not a retryable error") }) { throw expected }
            fail("Must cancel")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
    }
}
