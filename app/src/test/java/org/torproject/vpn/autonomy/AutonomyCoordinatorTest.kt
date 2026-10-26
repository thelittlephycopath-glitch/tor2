package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomyCoordinatorTest {
    private fun connectedCoordinator(): AutonomyCoordinator {
        return AutonomyCoordinator().also {
            it.updateNetworkEnvironment(
                networkAvailable = true,
                captivePortalDetected = false,
                mobileData = false
            )
        }
    }

    @Test
    fun tunnelReadyDoesNotReleaseTrafficUntilLeakCheckPasses() {
        val coordinator = connectedCoordinator()
        val bootstrapped = coordinator.observe(
            AutonomyEventType.TUNNEL_READY,
            elapsedRealtimeMillis = 1
        )
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
        val coordinator = connectedCoordinator()
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
        val coordinator = connectedCoordinator()
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

        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 6)
        assertEquals(0, coordinator.currentSignals().repeatedConnectionFailures)
    }

    @Test
    fun dnsConfigurationConflictPreventsTrafficRelease() {
        val coordinator = connectedCoordinator()
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
        journal.record(
            AutonomyEvent(AutonomyEventType.TUNNEL_START_REQUESTED, AutonomyEventSource.TEST, 2)
        )
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
        val decision = coordinator.observe(
            AutonomyEventType.SERVICE_CREATED,
            elapsedRealtimeMillis = 1
        )
        assertFalse(decision.automationConfigured)
    }

    @Test
    fun suspectedDnsHijackInvalidatesVerificationAndRequestsRecovery() {
        val coordinator = connectedCoordinator()
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
    fun directNetworkUpdateInvalidatesOldTunnelVerification() {
        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 2
        )
        assertTrue(coordinator.currentDecision().trafficReleaseEligible)

        val afterNetworkChange = coordinator.updateNetworkEnvironment(
            networkAvailable = false,
            captivePortalDetected = false,
            mobileData = false
        )
        assertTrue(afterNetworkChange.blockTrafficUntilTunnelVerified)
        assertFalse(afterNetworkChange.trafficReleaseEligible)

        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = false
        )
        assertFalse(coordinator.currentDecision().trafficReleaseEligible)
    }

    @Test
    fun networkUpdateDoesNotClearSuspectedDnsHijack() {
        val coordinator = AutonomyCoordinator()
        coordinator.observe(
            AutonomyEventType.DNS_HIJACK_SUSPECTED,
            source = AutonomyEventSource.NETWORK_MONITOR,
            elapsedRealtimeMillis = 1
        )
        val decision = coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = false
        )
        assertEquals(ProtectionProfile.MAXIMUM, decision.profile)
        assertFalse(decision.trafficReleaseEligible)
    }

    @Test
    fun unavailableNetworkMonitorInvalidatesVerification() {
        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 2
        )
        assertTrue(coordinator.currentDecision().trafficReleaseEligible)

        val decision = coordinator.observe(
            AutonomyEventType.NETWORK_MONITOR_UNAVAILABLE,
            source = AutonomyEventSource.NETWORK_MONITOR,
            elapsedRealtimeMillis = 3
        )
        assertTrue(decision.blockTrafficUntilTunnelVerified)
        assertFalse(decision.trafficReleaseEligible)
        assertFalse(coordinator.currentSignals().networkAvailable)
    }

    @Test
    fun batteryBudgetSuppressesOptionalTrafficAndLogsOnlyThresholdTransitions() {
        val coordinator = connectedCoordinator()
        val lowBattery = coordinator.updateBatteryPercent(15)
        assertEquals(15, coordinator.currentSignals().batteryPercent)
        assertFalse(lowBattery.coverTrafficAllowedByBudget)
        assertEquals(
            1,
            coordinator.journal.snapshot().count { it.type == AutonomyEventType.LOW_BATTERY }
        )

        coordinator.updateBatteryPercent(10)
        assertEquals(
            1,
            coordinator.journal.snapshot().count { it.type == AutonomyEventType.LOW_BATTERY }
        )

        val recovered = coordinator.updateBatteryPercent(50)
        assertTrue(recovered.coverTrafficAllowedByBudget)
        coordinator.updateBatteryPercent(20)
        assertEquals(
            2,
            coordinator.journal.snapshot().count { it.type == AutonomyEventType.LOW_BATTERY }
        )
    }

    @Test
    fun invalidBatteryReadingsAreRejectedByCoordinator() {
        val coordinator = AutonomyCoordinator()
        assertTrue(
            runCatching { coordinator.updateBatteryPercent(101) }
                .exceptionOrNull() is IllegalArgumentException
        )
    }

    @Test
    fun unavailableResourceMonitorSuppressesOptionalTrafficWithoutFalseLowBatteryEvent() {
        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 2
        )
        assertTrue(coordinator.currentDecision().trafficReleaseEligible)

        val decision = coordinator.observe(
            AutonomyEventType.RESOURCE_MONITOR_UNAVAILABLE,
            source = AutonomyEventSource.RESOURCE_MONITOR,
            elapsedRealtimeMillis = 3
        )

        assertFalse(decision.coverTrafficAllowedByBudget)
        assertFalse(coordinator.currentSignals().batteryReadingAvailable)
        assertEquals(
            0,
            coordinator.journal.snapshot().count { it.type == AutonomyEventType.LOW_BATTERY }
        )
        assertEquals(
            1,
            coordinator.journal.snapshot().count {
                it.type == AutonomyEventType.RESOURCE_MONITOR_UNAVAILABLE
            }
        )
    }

    @Test
    fun invalidJournalCapacityIsRejected() {
        val exception = runCatching { AutonomyEventJournal(0) }.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
    }

    @Test
    fun leakCheckPassFromNonCheckerCannotAuthorizeTraffic() {
        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)

        val decision = coordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.VPN_SERVICE,
            elapsedRealtimeMillis = 2
        )

        assertFalse(coordinator.currentSignals().leakChecksPassed)
        assertFalse(decision.trafficReleaseEligible)
        assertTrue(decision.blockTrafficUntilTunnelVerified)
    }

    @Test
    fun leakCheckPassDuringCaptivePortalOrSuspectedInterceptionIsRejected() {
        val portalCoordinator = connectedCoordinator()
        portalCoordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        portalCoordinator.observe(
            AutonomyEventType.CAPTIVE_PORTAL_DETECTED,
            source = AutonomyEventSource.NETWORK_MONITOR,
            elapsedRealtimeMillis = 2
        )
        val portalDecision = portalCoordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 3
        )
        assertFalse(portalCoordinator.currentSignals().leakChecksPassed)
        assertFalse(portalDecision.trafficReleaseEligible)

        val tlsCoordinator = connectedCoordinator()
        tlsCoordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        tlsCoordinator.observe(
            AutonomyEventType.TLS_INTERCEPTION_SUSPECTED,
            source = AutonomyEventSource.NETWORK_MONITOR,
            elapsedRealtimeMillis = 2
        )
        val tlsDecision = tlsCoordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 3
        )
        assertFalse(tlsCoordinator.currentSignals().leakChecksPassed)
        assertFalse(tlsDecision.trafficReleaseEligible)
    }

    @Test
    fun tunnelStoppedInvalidatesPreviouslyVerifiedState() {
        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 2
        )
        assertTrue(coordinator.currentDecision().trafficReleaseEligible)

        val stopped = coordinator.observe(
            AutonomyEventType.TUNNEL_STOPPED,
            elapsedRealtimeMillis = 3
        )

        assertFalse(coordinator.currentSignals().tunnelReady)
        assertFalse(coordinator.currentSignals().leakChecksPassed)
        assertFalse(stopped.trafficReleaseEligible)
        assertTrue(stopped.blockTrafficUntilTunnelVerified)
    }

    @Test
    fun spoofedLeakEventsCannotClearAnExistingFailure() {
        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_FAILED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 2
        )
        assertTrue(coordinator.currentSignals().leakCheckFailed)

        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_STARTED,
            source = AutonomyEventSource.VPN_SERVICE,
            elapsedRealtimeMillis = 3
        )
        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.VPN_SERVICE,
            elapsedRealtimeMillis = 4
        )

        assertTrue(coordinator.currentSignals().leakCheckFailed)
        assertFalse(coordinator.currentSignals().leakChecksPassed)
        assertFalse(coordinator.currentDecision().trafficReleaseEligible)
    }

    @Test
    fun leakCheckFailureInvalidatesPreviousPass() {
        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 2
        )
        assertTrue(coordinator.currentDecision().trafficReleaseEligible)

        val failed = coordinator.observe(
            AutonomyEventType.LEAK_CHECK_FAILED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 3
        )

        assertFalse(failed.trafficReleaseEligible)
        assertTrue(failed.blockTrafficUntilTunnelVerified)
        assertTrue(coordinator.currentSignals().leakCheckFailed)
    }
    @Test
    fun spoofedLeakFailureFromNonCheckerCannotInvalidateVerifiedState() {
        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 2
        )
        assertTrue(coordinator.currentDecision().trafficReleaseEligible)

        val decision = coordinator.observe(
            AutonomyEventType.LEAK_CHECK_FAILED,
            source = AutonomyEventSource.POLICY_ENGINE,
            elapsedRealtimeMillis = 3
        )

        assertTrue(coordinator.currentSignals().leakChecksPassed)
        assertFalse(coordinator.currentSignals().leakCheckFailed)
        assertTrue(decision.trafficReleaseEligible)
    }

    @Test
    fun captivePortalTransitionInvalidatesPreviouslyVerifiedTunnel() {
        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 2
        )
        assertTrue(coordinator.currentDecision().trafficReleaseEligible)

        val decision = coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = true,
            mobileData = false
        )

        assertFalse(coordinator.currentSignals().tunnelReady)
        assertFalse(coordinator.currentSignals().leakChecksPassed)
        assertFalse(decision.trafficReleaseEligible)
        assertTrue(decision.blockTrafficUntilTunnelVerified)
    }


    @Test
    fun mobileTransportChangeInvalidatesPreviouslyVerifiedTunnel() {
        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 2
        )
        assertTrue(coordinator.currentDecision().trafficReleaseEligible)

        val afterTransportChange = coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = true
        )
        assertTrue(afterTransportChange.blockTrafficUntilTunnelVerified)
        assertFalse(afterTransportChange.trafficReleaseEligible)
    }

    @Test
    fun bridgeFailureLearningIsResetWhenTransportChanges() {
        val coordinator = connectedCoordinator()
        repeat(4) { index ->
            coordinator.recordBridgeOutcome(
                transport = Transport.SNOWFLAKE,
                succeeded = false,
                latencyMillis = 0,
                elapsedRealtimeMillis = index + 1L
            )
        }
        assertFalse(coordinator.rankedBridgeCandidates().any { it.transport == Transport.SNOWFLAKE })

        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = true
        )

        assertTrue(coordinator.rankedBridgeCandidates().any {
            it.transport == Transport.SNOWFLAKE && !it.knownBlocked
        })
    }
    @Test
    fun recoveryPlanningIsBoundedAndRemainsAdvisory() {
        val coordinator = connectedCoordinator()
        val first = coordinator.planRecovery()

        assertTrue(first.shouldScheduleReconnect)
        assertTrue(first.keepTrafficBlocked)
        assertEquals(1, first.attempt)
        assertEquals(1_000L, first.delayMillis)

        val duplicate = coordinator.planRecovery()
        assertFalse(duplicate.shouldScheduleReconnect)
        assertEquals(first.attempt, duplicate.attempt)

        assertTrue(coordinator.markRecoveryAttemptStarted(1))
        assertFalse(coordinator.markRecoveryAttemptStarted(1))
        val second = coordinator.planRecovery()
        assertTrue(second.shouldScheduleReconnect)
        assertEquals(2, second.attempt)
        assertEquals(2_000L, second.delayMillis)
    }

    @Test
    fun networkChangeResetsRecoveryBudget() {
        val coordinator = connectedCoordinator()
        val first = coordinator.planRecovery()
        assertTrue(coordinator.markRecoveryAttemptStarted(first.attempt!!))
        val second = coordinator.planRecovery()
        assertEquals(2, second.attempt)

        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = true
        )
        val afterChange = coordinator.planRecovery()
        assertEquals(1, afterChange.attempt)
        assertEquals(1_000L, afterChange.delayMillis)
    }

}
