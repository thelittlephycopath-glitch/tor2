package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomousPolicyEngineTest {
    private val engine = AutonomousPolicyEngine()

    @Test
    fun unknownNetworkUsesHighProtectionAndFailsClosed() {
        val decision = engine.evaluate(NetworkSignals(trust = NetworkTrust.UNKNOWN))
        assertEquals(ProtectionProfile.HIGH, decision.profile)
        assertTrue(decision.blockTrafficUntilTunnelVerified)
        assertFalse(decision.allowCaptivePortalOnly)
        assertFalse(decision.trafficReleaseEligible)
        assertTrue(AutonomyAction.KEEP_TRAFFIC_BLOCKED in decision.recommendedActions)
    }

    @Test
    fun suspectedDnsHijackSelectsMaximumProtectionAndRecommendsRecovery() {
        val decision = engine.evaluate(
            NetworkSignals(
                trust = NetworkTrust.TRUSTED,
                networkAvailable = true,
                dnsHijackSuspected = true
            )
        )
        assertEquals(ProtectionProfile.MAXIMUM, decision.profile)
        assertTrue(decision.recommendBridgeFailover)
        assertTrue(decision.recommendCircuitRefresh)
        assertTrue(decision.reasons.isNotEmpty())
    }

    @Test
    fun captivePortalDoesNotPermitGeneralTraffic() {
        val decision = engine.evaluate(
            NetworkSignals(trust = NetworkTrust.PUBLIC, captivePortalDetected = true)
        )
        assertTrue(decision.allowCaptivePortalOnly)
        assertTrue(decision.blockTrafficUntilTunnelVerified)
        assertTrue(AutonomyAction.ALLOW_CAPTIVE_PORTAL_ONLY in decision.recommendedActions)
        assertFalse(decision.trafficReleaseEligible)
    }

    @Test
    fun coverTrafficBudgetGateDisablesOnMobileLowBatteryOrLowData() {
        assertFalse(
            engine.evaluate(NetworkSignals(batteryPercent = 10)).coverTrafficAllowedByBudget
        )
        assertFalse(engine.evaluate(NetworkSignals(mobileData = true)).coverTrafficAllowedByBudget)
        assertFalse(
            engine.evaluate(NetworkSignals(remainingDataBudgetMb = 10)).coverTrafficAllowedByBudget
        )
        assertTrue(
            engine.evaluate(NetworkSignals(batteryReadingAvailable = true)).coverTrafficAllowedByBudget
        )
    }

    @Test
    fun bridgeRankingExcludesBlockedCandidatesAndUsesDeterministicOrder() {
        val blocked = BridgeCandidate(Transport.WEBTUNNEL, 1.0, 10, 0, knownBlocked = true)
        val reliable = BridgeCandidate(Transport.OBFS4, 0.95, 200, 0)
        val lessReliable = BridgeCandidate(Transport.SNOWFLAKE, 0.70, 50, 1)
        assertEquals(
            listOf(reliable, lessReliable),
            engine.rankBridges(listOf(lessReliable, blocked, reliable))
        )
    }

    @Test
    fun invalidSignalRangesAreRejected() {
        val exception = runCatching { NetworkSignals(batteryPercent = 101) }.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
        assertTrue(
            runCatching { BridgeCandidate(Transport.OBFS4, Double.NaN, 1, 0) }
                .exceptionOrNull() is IllegalArgumentException
        )
    }

    @Test
    fun tunnelReadyWithoutLeakChecksStillBlocksTraffic() {
        val decision = engine.evaluate(
            NetworkSignals(
                trust = NetworkTrust.TRUSTED,
                networkAvailable = true,
                tunnelReady = true,
                leakChecksPassed = false
            )
        )
        assertFalse(decision.trafficReleaseEligible)
        assertTrue(decision.blockTrafficUntilTunnelVerified)
        assertTrue(AutonomyAction.RUN_LEAK_CHECKS in decision.recommendedActions)
    }

    @Test
    fun trafficReleaseRequiresEveryIndependentPredicate() {
        val fullyVerified = NetworkSignals(
            trust = NetworkTrust.TRUSTED,
            networkAvailable = true,
            tunnelReady = true,
            leakChecksPassed = true
        )
        assertTrue(engine.evaluate(fullyVerified).trafficReleaseEligible)
        assertFalse(
            engine.evaluate(fullyVerified.copy(leakCheckFailed = true)).trafficReleaseEligible
        )
        assertFalse(
            engine.evaluate(fullyVerified.copy(dnsConfigurationConflict = true))
                .trafficReleaseEligible
        )
        assertFalse(
            engine.evaluate(fullyVerified.copy(networkAvailable = false)).trafficReleaseEligible
        )
        assertFalse(
            engine.evaluate(fullyVerified.copy(captivePortalDetected = true)).trafficReleaseEligible
        )
        assertFalse(
            engine.evaluate(fullyVerified.copy(trust = NetworkTrust.HOSTILE)).trafficReleaseEligible
        )
        assertFalse(
            engine.evaluate(fullyVerified.copy(dnsHijackSuspected = true)).trafficReleaseEligible
        )
        assertFalse(
            engine.evaluate(fullyVerified.copy(tlsInterceptionSuspected = true))
                .trafficReleaseEligible
        )
    }

    @Test
    fun bridgeFailoverIsNotRecommendedWhenNetworkIsDown() {
        val decision = engine.evaluate(
            NetworkSignals(networkAvailable = false, repeatedConnectionFailures = 10)
        )
        assertFalse(decision.recommendBridgeFailover)
        assertFalse(decision.recommendCircuitRefresh)
    }

    @Test
    fun unavailableBatteryTelemetrySuppressesOptionalTrafficWithoutClaimingLowBattery() {
        val decision = engine.evaluate(
            NetworkSignals(batteryReadingAvailable = false, batteryPercent = 100)
        )
        assertFalse(decision.coverTrafficAllowedByBudget)
        assertFalse(decision.reasons.contains("Battery budget is low"))
    }

    @Test
    fun coverTrafficRequestIsSuppressedWhenBudgetIsInsufficient() {
        val decision = engine.evaluate(
            NetworkSignals(mobileData = true, coverTrafficRequested = true)
        )
        assertFalse(decision.coverTrafficAllowedByBudget)
        assertTrue(AutonomyAction.SUPPRESS_COVER_TRAFFIC in decision.recommendedActions)
    }
}
