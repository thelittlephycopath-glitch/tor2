package org.torproject.vpn.autonomy

/**
 * Computes bounded retry delays for a future reviewed recovery executor.
 *
 * This policy does not schedule work or reconnect the VPN. Callers must still enforce a single
 * active recovery, cancellation, a cooldown across service recreation, and a fail-closed outcome.
 */
class RecoveryBackoffPolicy(
    private val baseDelayMillis: Long = 1_000L,
    private val maximumDelayMillis: Long = 60_000L,
    private val maximumAttempts: Int = 5
) {
    init {
        require(baseDelayMillis > 0L) { "Base delay must be positive" }
        require(maximumDelayMillis >= baseDelayMillis) {
            "Maximum delay must be at least the base delay"
        }
        require(maximumAttempts > 0) { "Maximum attempts must be positive" }
    }

    /**
     * Returns the delay before a 1-based attempt, or null after the retry budget is exhausted.
     * Delay doubles per attempt and saturates at the configured maximum without overflow.
     */
    fun delayBeforeAttempt(attempt: Int): Long? {
        if (attempt !in 1..maximumAttempts) return null

        var delay = baseDelayMillis
        repeat(attempt - 1) {
            delay = if (delay > maximumDelayMillis / 2L) {
                maximumDelayMillis
            } else {
                (delay * 2L).coerceAtMost(maximumDelayMillis)
            }
        }
        return delay.coerceAtMost(maximumDelayMillis)
    }
}
