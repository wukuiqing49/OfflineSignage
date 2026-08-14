package com.wkq.localsignage.feature.app.security

class PairingAttemptLimiter(
    private val maxFailures: Int = 5,
    private val windowMs: Long = 60_000L,
    private val lockoutMs: Long = 60_000L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val failures = ArrayDeque<Long>()
    private var blockedUntil = 0L

    @Synchronized
    fun retryAfterMs(): Long = (blockedUntil - clock()).coerceAtLeast(0L)

    @Synchronized
    fun recordFailure(): Long {
        val now = clock()
        if (blockedUntil > now) return blockedUntil - now
        while (failures.firstOrNull()?.let { now - it >= windowMs } == true) failures.removeFirst()
        failures.addLast(now)
        if (failures.size >= maxFailures) {
            failures.clear()
            blockedUntil = now + lockoutMs
        }
        return (blockedUntil - now).coerceAtLeast(0L)
    }

    @Synchronized
    fun reset() {
        failures.clear()
        blockedUntil = 0L
    }
}
