package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TorCircuitRefreshPlannerTest {
    private val planner = TorCircuitRefreshPlanner()
    private val now = 100_000L

    private fun telemetry(
        observedAt: Long = now,
        circuitAge: Long = 60_000L,
        latency: Long? = 100L,
        failures: Int = 0,
        lastRefresh: Long? = null,
        networkChanged: Boolean = false,
        circuitFailed: Boolean = false,
        highRisk: Boolean = false
    ) = CircuitTelemetry(
        observedAtElapsedRealtimeMillis = observedAt,
        circuitAgeMillis = circuitAge,
        latencyMillis = latency,
        recentStreamFailures = failures,
        lastRefreshAtElapsedRealtimeMillis = lastRefresh,
        networkChanged = networkChanged,
        circuitFailed = circuitFailed,
        highRiskSessionStarted = highRisk
    )

    @Test
    fun healthyCircuitDoesNotRefresh() {
        val decision = planner.assess(telemetry(), now)
        assertFalse(decision.shouldRefresh)
        assertEquals(CircuitRefreshReason.HEALTHY, decision.reason)
    }

    @Test
    fun agedCircuitUsesObservationElapsedTime() {
        val decision = planner.assess(
            telemetry(observedAt = now - 2_000L, circuitAge = 599_000L),
            now
        )
        assertTrue(decision.shouldRefresh)
        assertEquals(CircuitRefreshReason.CIRCUIT_TOO_OLD, decision.reason)
        assertEquals(601_000L, decision.estimatedCircuitAgeMillis)
    }

    @Test
    fun latencyAndRepeatedFailuresCanTriggerRefresh() {
        val slow = planner.assess(telemetry(latency = 5_000L), now)
        assertEquals(CircuitRefreshReason.LATENCY_HIGH, slow.reason)
        assertTrue(slow.shouldRefresh)

        val failures = planner.assess(telemetry(latency = 100L, failures = 3), now)
        assertEquals(CircuitRefreshReason.REPEATED_STREAM_FAILURES, failures.reason)
        assertTrue(failures.shouldRefresh)
    }

    @Test
    fun networkChangeAndCircuitFailureBypassCooldown() {
        val network = planner.assess(
            telemetry(lastRefresh = now - 1_000L, networkChanged = true),
            now
        )
        assertTrue(network.shouldRefresh)
        assertEquals(CircuitRefreshReason.NETWORK_CHANGED, network.reason)

        val failed = planner.assess(
            telemetry(lastRefresh = now - 1_000L, circuitFailed = true),
            now
        )
        assertTrue(failed.shouldRefresh)
        assertEquals(CircuitRefreshReason.CIRCUIT_FAILED, failed.reason)
    }

    @Test
    fun ordinaryTriggersRespectCooldown() {
        val decision = planner.assess(
            telemetry(circuitAge = 600_000L, lastRefresh = now - 1_000L),
            now
        )
        assertFalse(decision.shouldRefresh)
        assertEquals(CircuitRefreshReason.COOLDOWN, decision.reason)
    }

    @Test
    fun riskSessionRequestsRefreshButCannotCauseRefreshStorm() {
        val decision = planner.assess(
            telemetry(lastRefresh = now - 1_000L, highRisk = true),
            now
        )
        assertFalse(decision.shouldRefresh)
        assertEquals(CircuitRefreshReason.COOLDOWN, decision.reason)
    }

    @Test
    fun staleOrFutureObservationsAreNotUsed() {
        val stale = planner.assess(
            telemetry(observedAt = now - TorCircuitRefreshPlanner.DEFAULT_MAXIMUM_OBSERVATION_AGE_MILLIS - 1L),
            now
        )
        assertFalse(stale.shouldRefresh)
        assertEquals(CircuitRefreshReason.STALE_OBSERVATION, stale.reason)

        val future = planner.assess(telemetry(observedAt = now + 1L), now)
        assertFalse(future.shouldRefresh)
        assertEquals(CircuitRefreshReason.FUTURE_OBSERVATION, future.reason)

        val futureRefresh = planner.assess(telemetry(lastRefresh = now + 1L), now)
        assertFalse(futureRefresh.shouldRefresh)
        assertEquals(CircuitRefreshReason.FUTURE_REFRESH_TIMESTAMP, futureRefresh.reason)
    }

    @Test
    fun rejectsInvalidConfigurationAndTelemetry() {
        assertThrows(IllegalArgumentException::class.java) {
            TorCircuitRefreshPlanner(maximumCircuitAgeMillis = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TorCircuitRefreshPlanner(refreshCooldownMillis = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            telemetry(circuitAge = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            telemetry(latency = -1L)
        }
    }
}
