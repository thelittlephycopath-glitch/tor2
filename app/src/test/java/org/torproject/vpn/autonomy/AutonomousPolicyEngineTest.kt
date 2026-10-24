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
    }

    @Test
    fun suspectedDnsHijackSelectsMaximumProtectionAndRecommendsRecovery() {
        val decision = engine.evaluate(
            NetworkSignals(trust = NetworkTrust.TRUSTED, dnsHijackSuspected = true)
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
    }

    @Test
    fun coverTrafficBudgetGateDisablesOnMobileLowBatteryOrLowData() {
        assertFalse(engine.evaluate(NetworkSignals(batteryPercent = 10)).coverTrafficAllowedByBudget)
        assertFalse(engine.evaluate(NetworkSignals(mobileData = true)).coverTrafficAllowedByBudget)
        assertFalse(
            engine.evaluate(NetworkSignals(remainingDataBudgetMb = 10)).coverTrafficAllowedByBudget
        )
        assertTrue(engine.evaluate(NetworkSignals()).coverTrafficAllowedByBudget)
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
    }
}
