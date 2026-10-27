package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomyRecoveryPlannerTest {
    private fun planner() = AutonomyRecoveryPlanner(
        RecoveryBackoffPolicy(
            baseDelayMillis = 1_000L,
            maximumDelayMillis = 4_000L,
            maximumAttempts = 3
        )
    )

    private fun plan(planner: AutonomyRecoveryPlanner) = planner.planReconnect(
        networkAvailable = true,
        tunnelReady = false,
        leakChecksPassed = false,
        recoveryRequired = true
    )

    @Test
    fun schedulesBoundedBackoffAndKeepsTrafficBlocked() {
        val planner = planner()
        val first = plan(planner)

        assertTrue(first.shouldScheduleReconnect)
        assertEquals(1, first.attempt)
        assertEquals(1_000L, first.delayMillis)
        assertTrue(first.keepTrafficBlocked)

        assertTrue(planner.markAttemptStarted(1))
        val second = plan(planner)
        assertTrue(second.shouldScheduleReconnect)
        assertEquals(2, second.attempt)
        assertEquals(2_000L, second.delayMillis)
        assertTrue(second.keepTrafficBlocked)
    }

    @Test
    fun duplicatePlansDoNotScheduleParallelReconnects() {
        val planner = planner()
        val first = plan(planner)
        val duplicate = plan(planner)

        assertTrue(first.shouldScheduleReconnect)
        assertFalse(duplicate.shouldScheduleReconnect)
        assertEquals(first.attempt, duplicate.attempt)
        assertNull(duplicate.delayMillis)
        assertFalse(planner.markAttemptStarted(2))
        assertTrue(planner.markAttemptStarted(1))
        assertFalse(planner.markAttemptStarted(1))
    }

    @Test
    fun noNetworkCancelsPendingPlanWithoutConsumingAttempt() {
        val planner = planner()
        plan(planner)

        val offline = planner.planReconnect(
            networkAvailable = false,
            tunnelReady = false,
            leakChecksPassed = false,
            recoveryRequired = true
        )
        assertFalse(offline.shouldScheduleReconnect)
        assertTrue(offline.keepTrafficBlocked)
        assertEquals(0, planner.attemptsStarted())

        val online = plan(planner)
        assertEquals(1, online.attempt)
        assertEquals(1_000L, online.delayMillis)
    }

    @Test
    fun retryBudgetExhaustionNeverPermitsTraffic() {
        val planner = planner()
        repeat(3) { index ->
            val scheduled = plan(planner)
            assertTrue(scheduled.shouldScheduleReconnect)
            assertTrue(scheduled.keepTrafficBlocked)
            assertTrue(planner.markAttemptStarted(index + 1))
        }

        val exhausted = plan(planner)
        assertFalse(exhausted.shouldScheduleReconnect)
        assertTrue(exhausted.keepTrafficBlocked)
        assertEquals("Reconnect retry budget exhausted", exhausted.reason)
    }

    @Test
    fun startupOrPendingLeakCheckDoesNotCauseReconnectLoop() {
        val planner = planner()
        val starting = planner.planReconnect(
            networkAvailable = true,
            tunnelReady = false,
            leakChecksPassed = false,
            recoveryRequired = false
        )
        assertFalse(starting.shouldScheduleReconnect)
        assertTrue(starting.keepTrafficBlocked)

        val awaitingLeakCheck = planner.planReconnect(
            networkAvailable = true,
            tunnelReady = true,
            leakChecksPassed = false,
            recoveryRequired = false
        )
        assertFalse(awaitingLeakCheck.shouldScheduleReconnect)
        assertTrue(awaitingLeakCheck.keepTrafficBlocked)
        assertEquals("Tunnel is ready; await independent leak verification", awaitingLeakCheck.reason)
    }

    @Test
    fun verifiedTunnelAndNetworkChangeResetRetryBudget() {
        val planner = planner()
        val first = plan(planner)
        assertTrue(planner.markAttemptStarted(first.attempt!!))

        val verified = planner.planReconnect(
            networkAvailable = true,
            tunnelReady = true,
            leakChecksPassed = true,
            recoveryRequired = false
        )
        assertFalse(verified.shouldScheduleReconnect)
        assertFalse(verified.keepTrafficBlocked)
        assertEquals(0, planner.attemptsStarted())

        val pending = plan(planner)
        assertTrue(planner.markAttemptStarted(pending.attempt!!))
        planner.onNetworkChanged()
        assertEquals(0, planner.attemptsStarted())
        val afterChange = plan(planner)
        assertEquals(1, afterChange.attempt)
        assertEquals(1_000L, afterChange.delayMillis)
    }

    @Test
    fun serviceStopClearsPendingAttemptAndRetryBudget() {
        val planner = planner()
        val first = plan(planner)
        assertTrue(first.shouldScheduleReconnect)
        assertTrue(planner.markAttemptStarted(first.attempt!!))

        val second = plan(planner)
        assertTrue(second.shouldScheduleReconnect)
        assertEquals(2, second.attempt)

        planner.resetAfterServiceStopped()

        val afterStop = plan(planner)
        assertTrue(afterStop.shouldScheduleReconnect)
        assertEquals(1, afterStop.attempt)
        assertEquals(1_000L, afterStop.delayMillis)
        assertTrue(afterStop.keepTrafficBlocked)
    }
}
