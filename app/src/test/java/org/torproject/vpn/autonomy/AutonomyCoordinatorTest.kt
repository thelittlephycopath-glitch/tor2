package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomyCoordinatorTest {

    private fun submitPassingLeakCheck(
        coordinator: AutonomyCoordinator,
        timestamp: Long = 2L
    ): LeakVerificationResult = coordinator.submitLeakCheckEvidence(
        evidence = LeakVerificationEvidence(
            checkedAtElapsedRealtimeMillis = timestamp,
            ipv4DefaultRouteProtected = true,
            ipv6ContainedOrBlocked = true,
            dnsProtectedByTunnel = true,
            systemLockdownVerified = true,
            appRoutingVerified = true
        ),
        nowElapsedRealtimeMillis = timestamp
    )

    private fun connectedCoordinator(): AutonomyCoordinator {
        // Most tests below use small synthetic timestamps. A fixed injected monotonic clock keeps
        // their leak-verification evidence fresh unless a test deliberately advances that clock.
        return AutonomyCoordinator(elapsedRealtimeClock = { TEST_CLOCK_MILLIS }).also {
            it.updateNetworkEnvironment(
                networkAvailable = true,
                captivePortalDetected = false,
                mobileData = false
            )
        }
    }

    private companion object {
        const val TEST_CLOCK_MILLIS = 100L
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

        val verified = submitPassingLeakCheck(coordinator).let {
            assertTrue(it.passed)
            coordinator.currentDecision()
        }
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
        submitPassingLeakCheck(coordinator).let {
            assertTrue(it.passed)
            coordinator.currentDecision()
        }
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
        submitPassingLeakCheck(coordinator).let {
            assertTrue(it.passed)
            coordinator.currentDecision()
        }
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
        submitPassingLeakCheck(coordinator).let {
            assertTrue(it.passed)
            coordinator.currentDecision()
        }
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
        submitPassingLeakCheck(coordinator).let {
            assertTrue(it.passed)
            coordinator.currentDecision()
        }
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
        submitPassingLeakCheck(coordinator).let {
            assertTrue(it.passed)
            coordinator.currentDecision()
        }
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
    fun networkRestorationRequestsRecoveryOnlyForAnInvalidatedSession() {
        // A normal first-time network observation must not create a reconnect loop.
        val fresh = connectedCoordinator()
        assertFalse(fresh.planRecovery().shouldScheduleReconnect)

        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        assertTrue(submitPassingLeakCheck(coordinator).passed)
        assertTrue(coordinator.currentDecision().trafficReleaseEligible)

        coordinator.updateNetworkEnvironment(
            networkAvailable = false,
            captivePortalDetected = false,
            mobileData = false
        )
        assertFalse(coordinator.planRecovery().shouldScheduleReconnect)

        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = false
        )
        val recovery = coordinator.planRecovery()
        assertTrue(recovery.shouldScheduleReconnect)
        assertTrue(recovery.keepTrafficBlocked)
        assertEquals(1, recovery.attempt)
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
        submitPassingLeakCheck(coordinator).let {
            assertTrue(it.passed)
            coordinator.currentDecision()
        }
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
        submitPassingLeakCheck(coordinator).let {
            assertTrue(it.passed)
            coordinator.currentDecision()
        }
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
        val portalCoordinatorResult = submitPassingLeakCheck(portalCoordinator, timestamp = 3L)
        val portalDecision = portalCoordinator.currentDecision()
        assertFalse(portalCoordinator.currentSignals().leakChecksPassed)
        assertFalse(portalDecision.trafficReleaseEligible)
        assertFalse(portalCoordinatorResult.passed)
        assertTrue(portalCoordinatorResult.failures.contains(LeakVerificationFailure.CAPTIVE_PORTAL_ACTIVE))

        val tlsCoordinator = connectedCoordinator()
        tlsCoordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        tlsCoordinator.observe(
            AutonomyEventType.TLS_INTERCEPTION_SUSPECTED,
            source = AutonomyEventSource.NETWORK_MONITOR,
            elapsedRealtimeMillis = 2
        )
        val tlsCoordinatorResult = submitPassingLeakCheck(tlsCoordinator, timestamp = 3L)
        val tlsDecision = tlsCoordinator.currentDecision()
        assertFalse(tlsCoordinator.currentSignals().leakChecksPassed)
        assertFalse(tlsDecision.trafficReleaseEligible)
        assertFalse(tlsCoordinatorResult.passed)
        assertTrue(tlsCoordinatorResult.failures.contains(LeakVerificationFailure.HOSTILE_NETWORK_INDICATOR))
    }

    @Test
    fun tunnelStoppedInvalidatesPreviouslyVerifiedState() {
        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        submitPassingLeakCheck(coordinator).let {
            assertTrue(it.passed)
            coordinator.currentDecision()
        }
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
    fun recoveryWaitsForAnInFlightTunnelStartupToFinish() {
        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_FAILED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 2
        )

        val first = coordinator.planRecovery()
        assertTrue(first.shouldScheduleReconnect)
        assertTrue(coordinator.markRecoveryAttemptStarted(first.attempt!!))

        coordinator.observe(AutonomyEventType.TUNNEL_START_REQUESTED, elapsedRealtimeMillis = 3)
        coordinator.observe(AutonomyEventType.TUNNEL_BOOTSTRAPPING, elapsedRealtimeMillis = 4)
        val whileStarting = coordinator.planRecovery()
        assertFalse(whileStarting.shouldScheduleReconnect)
        assertTrue(whileStarting.keepTrafficBlocked)
        assertTrue(whileStarting.reason.contains("startup is in progress"))

        coordinator.observe(AutonomyEventType.TUNNEL_FAILED, elapsedRealtimeMillis = 5)
        val afterFailure = coordinator.planRecovery()
        assertTrue(afterFailure.shouldScheduleReconnect)
        assertEquals(2, afterFailure.attempt)
    }

    @Test
    fun failedLeakCheckRequiresReconnectAndFreshVerificationBeforeClearingFailure() {
        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_FAILED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 2
        )

        assertTrue(coordinator.currentSignals().leakCheckFailed)
        assertFalse(coordinator.currentSignals().tunnelReady)
        val recovery = coordinator.planRecovery()
        assertTrue(recovery.shouldScheduleReconnect)
        assertTrue(coordinator.markRecoveryAttemptStarted(recovery.attempt!!))

        // A new tunnel-ready event does not erase the failure; it enables a fresh check.
        val ready = coordinator.observe(
            AutonomyEventType.TUNNEL_READY,
            elapsedRealtimeMillis = 3
        )
        assertTrue(coordinator.currentSignals().leakCheckFailed)
        assertFalse(coordinator.currentSignals().leakChecksPassed)
        assertTrue(AutonomyAction.RUN_LEAK_CHECKS in ready.recommendedActions)
        assertFalse(coordinator.planRecovery().shouldScheduleReconnect)

        coordinator.observe(
            AutonomyEventType.LEAK_CHECK_STARTED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 4
        )
        val verified = submitPassingLeakCheck(coordinator, timestamp = 5L)
        assertTrue(verified.passed)
        assertFalse(coordinator.currentSignals().leakCheckFailed)
        assertTrue(coordinator.currentDecision().trafficReleaseEligible)
    }

    @Test
    fun leakCheckFailureInvalidatesPreviousPass() {
        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        submitPassingLeakCheck(coordinator).let {
            assertTrue(it.passed)
            coordinator.currentDecision()
        }
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
        submitPassingLeakCheck(coordinator).let {
            assertTrue(it.passed)
            coordinator.currentDecision()
        }
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
        submitPassingLeakCheck(coordinator).let {
            assertTrue(it.passed)
            coordinator.currentDecision()
        }
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
        submitPassingLeakCheck(coordinator).let {
            assertTrue(it.passed)
            coordinator.currentDecision()
        }
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
        coordinator.observe(AutonomyEventType.CONNECTION_FAILED, elapsedRealtimeMillis = 1)
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
        coordinator.observe(AutonomyEventType.CONNECTION_FAILED, elapsedRealtimeMillis = 1)
        val first = coordinator.planRecovery()
        assertTrue(coordinator.markRecoveryAttemptStarted(first.attempt!!))
        val second = coordinator.planRecovery()
        assertEquals(2, second.attempt)

        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = true
        )
        coordinator.observe(AutonomyEventType.CONNECTION_FAILED, elapsedRealtimeMillis = 2)
        val afterChange = coordinator.planRecovery()
        assertEquals(1, afterChange.attempt)
        assertEquals(1_000L, afterChange.delayMillis)
    }

    @Test
    fun recoveryPlannerDoesNotTreatStartupOrPendingLeakCheckAsFailure() {
        val coordinator = connectedCoordinator()

        assertFalse(coordinator.planRecovery().shouldScheduleReconnect)

        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        val awaitingLeakCheck = coordinator.planRecovery()
        assertFalse(awaitingLeakCheck.shouldScheduleReconnect)
        assertTrue(awaitingLeakCheck.keepTrafficBlocked)
    }

    @Test
    fun rawCheckerEventCannotAuthorizeTrafficWithoutValidatedEvidence() {
        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)

        val decision = coordinator.observe(
            AutonomyEventType.LEAK_CHECK_PASSED,
            source = AutonomyEventSource.LEAK_CHECKER,
            elapsedRealtimeMillis = 2
        )

        assertFalse(coordinator.currentSignals().leakChecksPassed)
        assertFalse(decision.trafficReleaseEligible)
        assertTrue(decision.blockTrafficUntilTunnelVerified)
    }

    @Test
    fun coordinatorExposesCircuitRefreshRecommendationWithoutExecutingIt() {
        val coordinator = connectedCoordinator()
        val decision = coordinator.assessCircuitRefresh(
            telemetry = CircuitTelemetry(
                observedAtElapsedRealtimeMillis = 100L,
                circuitAgeMillis = 600_000L,
                latencyMillis = 100L,
                recentStreamFailures = 0
            ),
            nowElapsedRealtimeMillis = 100L
        )

        assertTrue(decision.shouldRefresh)
        assertEquals(CircuitRefreshReason.CIRCUIT_TOO_OLD, decision.reason)
    }

    @Test
    fun coordinatorCoverTrafficGateRejectsUncontrolledTargets() {
        val coordinator = connectedCoordinator()
        val decision = coordinator.assessCoverTrafficPolicy(
            CoverTrafficPolicyInputs(
                enabled = true,
                targetIsUserControlled = false,
                threatLevel = CoverTrafficThreatLevel.CRITICAL,
                transport = CoverTrafficTransport.WIFI,
                batteryPercent = 90,
                charging = false,
                wifiOnly = true,
                coverTrafficUsedTodayMb = 0L,
                dailyCoverTrafficBudgetMb = 300L
            )
        )

        assertFalse(decision.enabled)
        assertEquals(CoverTrafficDecisionReason.TARGET_NOT_USER_CONTROLLED, decision.reason)
        assertTrue(decision.onlyUserControlledTargets)
    }

    @Test
    fun coordinatorClassifiesFreshPublicNetworkEvidenceConservatively() {
        val coordinator = connectedCoordinator()
        val decision = coordinator.updateNetworkTrust(
            evidence = NetworkTrustEvidence(
                observedAtElapsedRealtimeMillis = 100L,
                internetValidated = true,
                knownPublicNetwork = true
            ),
            nowElapsedRealtimeMillis = 100L
        )

        assertEquals(NetworkTrust.PUBLIC, coordinator.currentSignals().trust)
        assertEquals(ProtectionProfile.MAXIMUM, decision.profile)
        assertFalse(decision.trafficReleaseEligible)
    }

    @Test
    fun coordinatorDoesNotTrustStaleNetworkEvidence() {
        val staleNow =
            NetworkTrustClassifier.DEFAULT_MAX_EVIDENCE_AGE_MILLIS + 2L
        val coordinator = AutonomyCoordinator(elapsedRealtimeClock = { staleNow })
        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = false
        )
        coordinator.updateNetworkTrust(
            evidence = NetworkTrustEvidence(
                observedAtElapsedRealtimeMillis = 1L,
                internetValidated = true,
                explicitlyTrustedByUser = true
            ),
            nowElapsedRealtimeMillis = staleNow
        )

        assertEquals(NetworkTrust.UNKNOWN, coordinator.currentSignals().trust)
    }

    @Test
    fun hostileNetworkEvidenceInvalidatesPreviouslyEligibleDecision() {
        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)
        assertTrue(submitPassingLeakCheck(coordinator).passed)
        assertTrue(coordinator.currentDecision().trafficReleaseEligible)

        val hostile = coordinator.updateNetworkTrust(
            evidence = NetworkTrustEvidence(
                observedAtElapsedRealtimeMillis = 3L,
                internetValidated = true,
                dnsHijackSuspected = true
            ),
            nowElapsedRealtimeMillis = 3L
        )

        assertEquals(NetworkTrust.HOSTILE, coordinator.currentSignals().trust)
        assertEquals(ProtectionProfile.MAXIMUM, hostile.profile)
        assertFalse(coordinator.currentSignals().tunnelReady)
        assertFalse(coordinator.currentSignals().leakChecksPassed)
        assertFalse(hostile.trafficReleaseEligible)
        assertTrue(hostile.blockTrafficUntilTunnelVerified)
    }
    @Test
    fun connectivityEventsClearCachedNetworkTrustEvidence() {
        val coordinator = connectedCoordinator()
        coordinator.updateNetworkTrust(
            evidence = NetworkTrustEvidence(
                observedAtElapsedRealtimeMillis = TEST_CLOCK_MILLIS,
                internetValidated = true,
                explicitlyTrustedByUser = true
            ),
            nowElapsedRealtimeMillis = TEST_CLOCK_MILLIS
        )
        assertEquals(NetworkTrust.TRUSTED, coordinator.currentSignals().trust)

        coordinator.observe(
            AutonomyEventType.NETWORK_CONNECTIVITY_LOST,
            source = AutonomyEventSource.NETWORK_MONITOR,
            elapsedRealtimeMillis = TEST_CLOCK_MILLIS
        )
        assertEquals(NetworkTrust.UNKNOWN, coordinator.currentSignals().trust)

        coordinator.observe(
            AutonomyEventType.NETWORK_CONNECTIVITY_RESTORED,
            source = AutonomyEventSource.NETWORK_MONITOR,
            elapsedRealtimeMillis = TEST_CLOCK_MILLIS
        )
        assertEquals(NetworkTrust.UNKNOWN, coordinator.currentSignals().trust)
    }

    @Test
    fun networkChangeDoesNotCarryTrustedClassificationToTheNewPath() {
        val coordinator = connectedCoordinator()
        coordinator.updateNetworkTrust(
            evidence = NetworkTrustEvidence(
                observedAtElapsedRealtimeMillis = TEST_CLOCK_MILLIS,
                internetValidated = true,
                explicitlyTrustedByUser = true
            ),
            nowElapsedRealtimeMillis = TEST_CLOCK_MILLIS
        )
        assertEquals(NetworkTrust.TRUSTED, coordinator.currentSignals().trust)

        val changed = coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = true
        )
        assertEquals(NetworkTrust.UNKNOWN, coordinator.currentSignals().trust)
        assertEquals(ProtectionProfile.HIGH, changed.profile)
    }

    @Test
    fun futureDatedTrustEvidenceCannotBecomeTrustedWhenClockCatchesUp() {
        var now = 100L
        val coordinator = AutonomyCoordinator(elapsedRealtimeClock = { now })
        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = false
        )

        coordinator.updateNetworkTrust(
            evidence = NetworkTrustEvidence(
                observedAtElapsedRealtimeMillis = 101L,
                internetValidated = true,
                explicitlyTrustedByUser = true
            ),
            nowElapsedRealtimeMillis = now
        )
        assertEquals(NetworkTrust.UNKNOWN, coordinator.currentSignals().trust)

        // The original observation remains rejected; time passing is not a new observation.
        now = 101L
        assertEquals(NetworkTrust.UNKNOWN, coordinator.currentSignals().trust)
    }

    @Test
    fun trustedNetworkEvidenceExpiresToUnknownWithoutARefresh() {
        var now = 100L
        val coordinator = AutonomyCoordinator(elapsedRealtimeClock = { now })
        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = false
        )

        val initial = coordinator.updateNetworkTrust(
            evidence = NetworkTrustEvidence(
                observedAtElapsedRealtimeMillis = now,
                internetValidated = true,
                explicitlyTrustedByUser = true
            ),
            nowElapsedRealtimeMillis = now
        )
        assertEquals(NetworkTrust.TRUSTED, coordinator.currentSignals().trust)
        assertEquals(ProtectionProfile.BALANCED, initial.profile)

        now += NetworkTrustClassifier.DEFAULT_MAX_EVIDENCE_AGE_MILLIS + 1L
        val expired = coordinator.currentDecision()
        assertEquals(NetworkTrust.UNKNOWN, coordinator.currentSignals().trust)
        assertEquals(ProtectionProfile.HIGH, expired.profile)
        assertFalse(expired.trafficReleaseEligible)
    }

    @Test
    fun coordinatorUsesTheConfiguredLeakVerificationAgeLimit() {
        var now = 100L
        val coordinator = AutonomyCoordinator(
            elapsedRealtimeClock = { now },
            leakVerificationGate = LeakVerificationGate(maxReportAgeMillis = 100L)
        )
        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = false
        )
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = now)

        val report = coordinator.submitLeakCheckEvidence(
            evidence = LeakVerificationEvidence(
                checkedAtElapsedRealtimeMillis = now,
                ipv4DefaultRouteProtected = true,
                ipv6ContainedOrBlocked = true,
                dnsProtectedByTunnel = true,
                systemLockdownVerified = true,
                appRoutingVerified = true
            ),
            nowElapsedRealtimeMillis = now
        )
        assertTrue(report.passed)
        assertTrue(coordinator.currentDecision().trafficReleaseEligible)

        now += 101L
        assertFalse(coordinator.currentDecision().trafficReleaseEligible)
        assertFalse(coordinator.currentSignals().leakChecksPassed)
    }

    @Test
    fun staleLeakVerificationExpiresAndRequiresANewCheck() {
        var now = 100L
        val coordinator = AutonomyCoordinator(elapsedRealtimeClock = { now })
        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = false
        )
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = now)

        val accepted = coordinator.submitLeakCheckEvidence(
            evidence = LeakVerificationEvidence(
                checkedAtElapsedRealtimeMillis = now,
                ipv4DefaultRouteProtected = true,
                ipv6ContainedOrBlocked = true,
                dnsProtectedByTunnel = true,
                systemLockdownVerified = true,
                appRoutingVerified = true
            ),
            nowElapsedRealtimeMillis = now
        )
        assertTrue(accepted.passed)
        assertTrue(coordinator.currentDecision().trafficReleaseEligible)

        now += LeakVerificationGate.DEFAULT_MAX_REPORT_AGE_MILLIS + 1L
        val expired = coordinator.currentDecision()
        assertFalse(coordinator.currentSignals().leakChecksPassed)
        assertFalse(expired.trafficReleaseEligible)
        assertTrue(expired.blockTrafficUntilTunnelVerified)
        assertTrue(AutonomyAction.RUN_LEAK_CHECKS in expired.recommendedActions)
        assertFalse(coordinator.planRecovery().shouldScheduleReconnect)
    }

    @Test
    fun hostileTrustEvidenceCannotBeOverriddenByACompleteLeakReport() {
        val coordinator = connectedCoordinator()
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 1)

        val hostile = coordinator.updateNetworkTrust(
            evidence = NetworkTrustEvidence(
                observedAtElapsedRealtimeMillis = 2L,
                internetValidated = true,
                dnsHijackSuspected = true
            ),
            nowElapsedRealtimeMillis = 2L
        )
        assertTrue(hostile.blockTrafficUntilTunnelVerified)

        // A new tunnel-ready event is not itself proof that the hostile condition cleared.
        coordinator.observe(AutonomyEventType.TUNNEL_READY, elapsedRealtimeMillis = 3)
        val result = submitPassingLeakCheck(coordinator, timestamp = 4L)

        assertFalse(result.passed)
        assertTrue(result.failures.contains(LeakVerificationFailure.HOSTILE_NETWORK_INDICATOR))
        assertFalse(coordinator.currentSignals().leakChecksPassed)
        assertFalse(coordinator.currentDecision().trafficReleaseEligible)
    }

    @Test
    fun chargingTelemetryPreservesUnknownStateAndCanBeUpdated() {
        val coordinator = AutonomyCoordinator()
        assertFalse(coordinator.currentSignals().chargingReadingAvailable)

        coordinator.updateChargingState(isCharging = true)
        assertTrue(coordinator.currentSignals().charging)
        assertTrue(coordinator.currentSignals().chargingReadingAvailable)

        coordinator.updateChargingState(isCharging = false, readingAvailable = false)
        assertFalse(coordinator.currentSignals().charging)
        assertFalse(coordinator.currentSignals().chargingReadingAvailable)
    }
}
