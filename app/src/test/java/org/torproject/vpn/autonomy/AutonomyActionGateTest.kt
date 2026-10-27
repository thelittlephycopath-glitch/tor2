package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomyActionGateTest {
    private val gate = AutonomyActionGate()

    private fun decision(vararg actions: AutonomyAction, trafficEligible: Boolean = false) =
        AutonomyDecision(
            profile = ProtectionProfile.HIGH,
            blockTrafficUntilTunnelVerified = !trafficEligible,
            allowCaptivePortalOnly = false,
            recommendBridgeFailover = false,
            recommendCircuitRefresh = false,
            coverTrafficAllowedByBudget = false,
            reasons = emptyList(),
            trafficReleaseEligible = trafficEligible,
            recommendedActions = actions.toList()
        )

    @Test
    fun defaultConfigApprovesOnlyFailClosedSafetyActions() {
        val result = gate.plan(
            config = AutonomyConfig(),
            decision = decision(
                AutonomyAction.KEEP_TRAFFIC_BLOCKED,
                AutonomyAction.REQUEST_TUNNEL_RECONNECT,
                AutonomyAction.REQUEST_BRIDGE_FAILOVER,
                AutonomyAction.REQUEST_CIRCUIT_REFRESH,
                AutonomyAction.NOTIFY_USER,
                AutonomyAction.ALLOW_CAPTIVE_PORTAL_ONLY
            )
        )

        assertEquals(listOf(AutonomyAction.KEEP_TRAFFIC_BLOCKED), result.approvedActions)
        assertEquals(
            ActionSuppressionReason.AUTOMATION_DISABLED,
            result.suppressedActions[AutonomyAction.REQUEST_TUNNEL_RECONNECT]
        )
        assertEquals(
            ActionSuppressionReason.ACTION_NOT_IMPLEMENTED,
            result.suppressedActions[AutonomyAction.NOTIFY_USER]
        )
        assertEquals(
            ActionSuppressionReason.ACTION_NOT_IMPLEMENTED,
            result.suppressedActions[AutonomyAction.ALLOW_CAPTIVE_PORTAL_ONLY]
        )
    }

    @Test
    fun observeOnlyModeSuppressesAutomationEvenWhenAFeatureFlagIsSet() {
        val result = gate.plan(
            config = AutonomyConfig(
                enabled = true,
                mode = AutonomyMode.OBSERVE_ONLY,
                automaticReconnect = true
            ),
            decision = decision(AutonomyAction.REQUEST_TUNNEL_RECONNECT)
        )

        assertTrue(result.approvedActions.isEmpty())
        assertEquals(
            ActionSuppressionReason.OBSERVE_ONLY_MODE,
            result.suppressedActions[AutonomyAction.REQUEST_TUNNEL_RECONNECT]
        )
    }

    @Test
    fun guardedModeAllowsOnlyExplicitlyEnabledFeatures() {
        val result = gate.plan(
            config = AutonomyConfig(
                enabled = true,
                mode = AutonomyMode.GUARDED_AUTOMATION,
                automaticReconnect = true,
                automaticBridgeFailover = false,
                automaticCircuitRefresh = true
            ),
            decision = decision(
                AutonomyAction.REQUEST_TUNNEL_RECONNECT,
                AutonomyAction.REQUEST_BRIDGE_FAILOVER,
                AutonomyAction.REQUEST_CIRCUIT_REFRESH,
                AutonomyAction.RUN_LEAK_CHECKS
            )
        )

        assertTrue(result.approvedActions.isEmpty())
        assertEquals(
            ActionSuppressionReason.ACTION_NOT_IMPLEMENTED,
            result.suppressedActions[AutonomyAction.REQUEST_TUNNEL_RECONNECT]
        )
        assertEquals(
            ActionSuppressionReason.ACTION_NOT_IMPLEMENTED,
            result.suppressedActions[AutonomyAction.REQUEST_CIRCUIT_REFRESH]
        )
        assertEquals(
            ActionSuppressionReason.ACTION_NOT_IMPLEMENTED,
            result.suppressedActions[AutonomyAction.RUN_LEAK_CHECKS]
        )
        assertFalse(result.approvedActions.contains(AutonomyAction.REQUEST_BRIDGE_FAILOVER))
        assertEquals(
            ActionSuppressionReason.FEATURE_NOT_ENABLED,
            result.suppressedActions[AutonomyAction.REQUEST_BRIDGE_FAILOVER]
        )
    }

    @Test
    fun trafficRestorationRequiresBothDecisionAndPassingLeakReport() {
        val config = AutonomyConfig(
            enabled = true,
            mode = AutonomyMode.GUARDED_AUTOMATION
        )
        val actionDecision = decision(
            AutonomyAction.RESTORE_VERIFIED_TUNNELED_TRAFFIC,
            trafficEligible = true
        )

        val noEvidence = gate.plan(config, actionDecision)
        assertFalse(noEvidence.approvedActions.contains(AutonomyAction.RESTORE_VERIFIED_TUNNELED_TRAFFIC))
        assertEquals(
            ActionSuppressionReason.SAFETY_VERIFICATION_REQUIRED,
            noEvidence.suppressedActions[AutonomyAction.RESTORE_VERIFIED_TUNNELED_TRAFFIC]
        )

        val failedEvidence = gate.plan(
            config,
            actionDecision,
            LeakVerificationResult(listOf(LeakVerificationFailure.IPV6_NOT_CONTAINED))
        )
        assertFalse(failedEvidence.approvedActions.contains(AutonomyAction.RESTORE_VERIFIED_TUNNELED_TRAFFIC))

        val passedEvidence = gate.plan(
            config,
            actionDecision,
            LeakVerificationResult(emptyList())
        )
        assertFalse(passedEvidence.approvedActions.contains(AutonomyAction.RESTORE_VERIFIED_TUNNELED_TRAFFIC))
        assertEquals(
            ActionSuppressionReason.ACTION_NOT_IMPLEMENTED,
            passedEvidence.suppressedActions[AutonomyAction.RESTORE_VERIFIED_TUNNELED_TRAFFIC]
        )
    }

    @Test
    fun failClosedActionsRemainApprovedEvenWhenAutomationIsDisabled() {
        val result = gate.plan(
            config = AutonomyConfig(),
            decision = decision(
                AutonomyAction.KEEP_TRAFFIC_BLOCKED,
                AutonomyAction.SUPPRESS_COVER_TRAFFIC
            )
        )

        assertTrue(result.approvedActions.contains(AutonomyAction.KEEP_TRAFFIC_BLOCKED))
        assertTrue(result.approvedActions.contains(AutonomyAction.SUPPRESS_COVER_TRAFFIC))
        assertTrue(result.suppressedActions.isEmpty())
    }

    @Test
    fun everySideEffectingActionRemainsSuppressedUntilAnExecutorIsReviewed() {
        val config = AutonomyConfig(
            enabled = true,
            mode = AutonomyMode.EXPERIMENTAL,
            automaticReconnect = true,
            automaticBridgeFailover = true,
            automaticCircuitRefresh = true,
            coverTraffic = true,
            decoyData = true,
            honeyvault = true,
            persistNetworkLearning = true
        )
        val allActions = AutonomyAction.values().toList()
        val result = gate.plan(
            config = config,
            decision = decision(*allActions.toTypedArray(), trafficEligible = true),
            leakVerificationResult = LeakVerificationResult(emptyList())
        )

        assertEquals(
            setOf(
                AutonomyAction.KEEP_TRAFFIC_BLOCKED,
                AutonomyAction.SUPPRESS_COVER_TRAFFIC
            ),
            result.approvedActions.toSet()
        )
        val sideEffectingActions = allActions.toSet() - setOf(
            AutonomyAction.KEEP_TRAFFIC_BLOCKED,
            AutonomyAction.SUPPRESS_COVER_TRAFFIC
        )
        assertEquals(sideEffectingActions, result.suppressedActions.keys)
        assertTrue(
            result.suppressedActions.values.all {
                it == ActionSuppressionReason.ACTION_NOT_IMPLEMENTED
            }
        )
    }

}
