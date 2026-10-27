package org.torproject.vpn.autonomy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for asynchronous leak-check callbacks arriving after the VPN lifecycle
 * has moved to a different session. A callback must never make an old session eligible.
 */
class AutonomyLifecycleOrderingTest {
    private fun passingEvidence(
        checkedAt: Long,
        generation: Long
    ) = LeakVerificationEvidence(
        checkedAtElapsedRealtimeMillis = checkedAt,
        ipv4DefaultRouteProtected = true,
        ipv6ContainedOrBlocked = true,
        dnsProtectedByTunnel = true,
        systemLockdownVerified = true,
        appRoutingVerified = true,
        sessionGeneration = generation
    )

    private fun connectAndStart(coordinator: AutonomyCoordinator, timestamp: Long) {
        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = false
        )
        coordinator.observe(
            AutonomyEventType.TUNNEL_READY,
            elapsedRealtimeMillis = timestamp
        )
    }

    @Test
    fun delayedLeakCheckCallbackFromStoppedSessionIsRejected() {
        val coordinator = AutonomyCoordinator()
        connectAndStart(coordinator, timestamp = 1L)
        val oldGeneration = coordinator.currentVerificationGeneration()
        val delayedReport = passingEvidence(
            checkedAt = 2L,
            generation = oldGeneration
        )

        coordinator.observe(
            AutonomyEventType.TUNNEL_STOPPED,
            elapsedRealtimeMillis = 3L
        )
        connectAndStart(coordinator, timestamp = 4L)

        val result = coordinator.submitLeakCheckEvidence(
            evidence = delayedReport,
            nowElapsedRealtimeMillis = 5L
        )

        assertFalse(result.passed)
        assertFalse(coordinator.currentSignals().leakChecksPassed)
        assertFalse(coordinator.currentDecision().trafficReleaseEligible)
        assertTrue(coordinator.currentDecision().blockTrafficUntilTunnelVerified)
    }

    @Test
    fun delayedLeakCheckCallbackCannotReplaceNewerVerificationAttempt() {
        val coordinator = AutonomyCoordinator()
        connectAndStart(coordinator, timestamp = 1L)

        val oldGeneration = coordinator.beginLeakVerification()
        val delayedReport = passingEvidence(
            coordinator = coordinator,
            checkedAt = 2L,
            generation = oldGeneration
        )

        val newGeneration = coordinator.beginLeakVerification()
        assertTrue(newGeneration > oldGeneration)

        val result = coordinator.submitLeakCheckEvidence(
            evidence = delayedReport,
            nowElapsedRealtimeMillis = 3L
        )

        assertFalse(result.passed)
        assertFalse(coordinator.currentSignals().leakChecksPassed)
        assertFalse(coordinator.currentDecision().trafficReleaseEligible)
    }

    @Test
    fun delayedCallbackAfterNetworkTransitionFailsClosedEvenIfTunnelRestarts() {
        val coordinator = AutonomyCoordinator()
        connectAndStart(coordinator, timestamp = 1L)
        val oldGeneration = coordinator.currentVerificationGeneration()
        val delayedReport = passingEvidence(
            coordinator = coordinator,
            checkedAt = 2L,
            generation = oldGeneration
        )

        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = true
        )
        connectAndStart(coordinator, timestamp = 4L)

        val result = coordinator.submitLeakCheckEvidence(
            evidence = delayedReport,
            nowElapsedRealtimeMillis = 5L
        )

        assertFalse(result.passed)
        assertFalse(coordinator.currentSignals().leakChecksPassed)
        assertFalse(coordinator.currentDecision().trafficReleaseEligible)
        assertTrue(coordinator.currentDecision().blockTrafficUntilTunnelVerified)
    }
}
