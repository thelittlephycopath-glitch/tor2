package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryBackoffPolicyTest {
    @Test
    fun delaysGrowExponentiallyAndSaturateAtMaximum() {
        val policy = RecoveryBackoffPolicy(
            baseDelayMillis = 1_000L,
            maximumDelayMillis = 5_000L,
            maximumAttempts = 6
        )

        assertEquals(1_000L, policy.delayBeforeAttempt(1))
        assertEquals(2_000L, policy.delayBeforeAttempt(2))
        assertEquals(4_000L, policy.delayBeforeAttempt(3))
        assertEquals(5_000L, policy.delayBeforeAttempt(4))
        assertEquals(5_000L, policy.delayBeforeAttempt(5))
        assertEquals(5_000L, policy.delayBeforeAttempt(6))
    }

    @Test
    fun retryBudgetRejectsInvalidAttemptNumbers() {
        val policy = RecoveryBackoffPolicy(maximumAttempts = 3)

        assertNull(policy.delayBeforeAttempt(0))
        assertNull(policy.delayBeforeAttempt(-1))
        assertNull(policy.delayBeforeAttempt(4))
    }

    @Test
    fun configurationRejectsInvalidBounds() {
        assertTrue(
            runCatching { RecoveryBackoffPolicy(baseDelayMillis = 0L) }
                .exceptionOrNull() is IllegalArgumentException
        )
        assertTrue(
            runCatching {
                RecoveryBackoffPolicy(baseDelayMillis = 2_000L, maximumDelayMillis = 1_000L)
            }.exceptionOrNull() is IllegalArgumentException
        )
        assertTrue(
            runCatching { RecoveryBackoffPolicy(maximumAttempts = 0) }
                .exceptionOrNull() is IllegalArgumentException
        )
    }

    @Test
    fun largeDelaysSaturateWithoutOverflow() {
        val policy = RecoveryBackoffPolicy(
            baseDelayMillis = Long.MAX_VALUE / 3L,
            maximumDelayMillis = Long.MAX_VALUE,
            maximumAttempts = 5
        )

        assertEquals(Long.MAX_VALUE / 3L, policy.delayBeforeAttempt(1))
        assertEquals(Long.MAX_VALUE * 2L / 3L, policy.delayBeforeAttempt(2))
        assertEquals(Long.MAX_VALUE, policy.delayBeforeAttempt(3))
        assertEquals(Long.MAX_VALUE, policy.delayBeforeAttempt(5))
    }
}
