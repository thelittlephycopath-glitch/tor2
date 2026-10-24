package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomyCoordinatorTest {
    @Test
    fun tunnelReadyDoesNotReleaseTrafficUntilLeakCheckPasses() {
        val coordinator = AutonomyCoordinator()
        val bootstrapped = coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        assertTrue(bootstrapped.blockTrafficUntilTunnelVerified)
        assertFalse(bootstrapped.trafficReleaseEligible)

        val verified = coordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 2
        )
        assertTrue(verified.trafficReleaseEligible)
        assertFalse(verified.blockTrafficUntilTunnelVerified)
    }

    @Test
    fun leakCheckPassWithoutReadyTunnelIsIgnored() {
        val coordinator = AutonomyCoordinator()
        val decision = coordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 1
        )
        assertFalse(decision.trafficReleaseEligible)
        assertTrue(coordinator.currentSignals().leakChecksPassed.not())
    }

    @Test
    fun networkLossInvalidatesPreviouslyVerifiedTunnel() {
        val coordinator = AutonomyCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 2
        )
        assertTrue(coordinator.currentDecision().trafficReleaseEligible)

        val afterLoss = coordinator.observe(
            AutonomyEventType.NETWORK_CONNECTIVITY_LOST,
            source = AutonomyEventSource.NETWORK_MONITOR,
            elapsedRealtimeMillis = 3
        )
        assertTrue(afterLoss.blockTrafficUntilTunnelVerified)
        assertFalse(afterLoss.trafficReleaseEligible)
    }

    @Test
    fun failureRequiresRecoveryAndNeverKeepsOldVerification() {
        val coordinator = AutonomyCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 2
        )
        coordinator.observe(AutonomyEventType.CONNECTION_FAILED, elapsedRealtimeMillis = 3)
        coordinator.observe(AutonomyEventType.CONNECTION_FAILED, elapsedRealtimeMillis = 4)
        val decision = coordinator.observe(
            AutonomyEventType.CONNECTION_FAILED,
            elapsedRealtimeMillis = 5
        )

        assertTrue(decision.blockTrafficUntilTunnelVerified)
        assertFalse(decision.trafficReleaseEligible)
        assertTrue(decision.recommendBridgeFailover)
    }

    @Test
    fun dnsConfigurationConflictPreventsTrafficRelease() {
        val coordinator = AutonomyCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 2
        )
        val conflict = coordinator.observe(
            AutonomyEventType.DNS_CONFIGURATION_CONFLICT,
            elapsedRealtimeMillis = 3
        )
        assertFalse(conflict.trafficReleaseEligible)
        assertTrue(conflict.blockTrafficUntilTunnelVerified)
        assertTrue(AutonomyAction.NOTIFY_USER in conflict.recommendedActions)
    }

    @Test
    fun journalIsBoundedAndSequencesStayMonotonicAfterClear() {
        val journal = AutonomyEventJournal(capacity = 2)
        val first = journal.record(
            AutonomyEvent(AutonomyEventType.SERVICE_CREATED, AutonomyEventSource.TEST, 1)
        )
        journal.record(AutonomyEvent(AutonomyEventType.TUNNEL_START_REQUESTED, AutonomyEventSource.TEST, 2))
        journal.record(AutonomyEvent(AutonomyEventType.TUNNEL_FAILED, AutonomyEventSource.TEST, 3))

        assertEquals(1L, first.sequence)
        assertEquals(2, journal.size())
        assertEquals(listOf(2L, 3L), journal.snapshot().map { it.sequence })
        journal.clear()
        val fourth = journal.record(
            AutonomyEvent(AutonomyEventType.SERVICE_STOPPED, AutonomyEventSource.TEST, 4)
        )
        assertEquals(4L, fourth.sequence)
    }

    @Test
    fun automationDefaultsToDisabled() {
        val coordinator = AutonomyCoordinator()
        val decision = coordinator.observe(AutonomyEventType.SERVICE_CREATED, elapsedRealtimeMillis = 1)
        assertFalse(decision.automationConfigured)
    }

    @Test
    fun suspectedDnsHijackInvalidatesVerificationAndRequestsRecovery() {
        val coordinator = AutonomyCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 2
        )
        val decision = coordinator.observe(
            AutonomyEventType.DNS_HIJACK_SUSPECTED,
            source = AutonomyEventSource.NETWORK_MONITOR,
            elapsedRealtimeMillis = 3
        )

        assertFalse(decision.trafficReleaseEligible)
        assertTrue(decision.blockTrafficUntilTunnelVerified)
        assertTrue(decision.recommendBridgeFailover)
        assertTrue(decision.recommendCircuitRefresh)
        assertEquals(ProtectionProfile.MAXIMUM, decision.profile)
    }

    @Test
    fun invalidJournalCapacityIsRejected() {
        assertTrue(runCatching { AutonomyEventJournal(0) }.exceptionOrNull() is IllegalArgumentException)
    }
}
