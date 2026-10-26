package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomyBridgeLearningTest {
    @Test
    fun coordinatorLearnsTransportOutcomesAndReturnsRankedCandidates() {
        val coordinator = AutonomyCoordinator(
            bridgeHealthLedger = BridgeHealthLedger(blockedFailureStreak = 3),
            elapsedRealtimeClock = { 100L }
        )

        coordinator.recordBridgeOutcome(Transport.WEBTUNNEL, false, 0, 1)
        coordinator.recordBridgeOutcome(Transport.WEBTUNNEL, false, 0, 2)
        coordinator.recordBridgeOutcome(Transport.WEBTUNNEL, false, 0, 3)
        coordinator.recordBridgeOutcome(Transport.SNOWFLAKE, true, 250, 4)
        coordinator.recordBridgeOutcome(Transport.SNOWFLAKE, true, 300, 5)

        val ranked = coordinator.rankedBridgeCandidates()
        assertFalse(ranked.any { it.transport == Transport.WEBTUNNEL })
        assertEquals(Transport.SNOWFLAKE, ranked.first().transport)
        assertTrue(
            coordinator.journal.snapshot().any {
                it.type == AutonomyEventType.BRIDGE_ATTEMPT_SUCCEEDED
            }
        )
        assertTrue(
            coordinator.journal.snapshot().any {
                it.type == AutonomyEventType.BRIDGE_ATTEMPT_FAILED
            }
        )
    }

    @Test
    fun bridgeLearningIsSessionScopedAndContainsNoNetworkIdentity() {
        val coordinator = AutonomyCoordinator()
        coordinator.recordBridgeOutcome(Transport.OBFS4, true, 500, 10)

        val events = coordinator.journal.snapshot()
        assertEquals(1, events.size)
        assertEquals(AutonomyEventType.BRIDGE_ATTEMPT_SUCCEEDED, events.single().type)
        assertEquals(AutonomyEventSource.VPN_SERVICE, events.single().source)
        assertEquals(Transport.entries.size, coordinator.rankedBridgeCandidates().size)
    }
}
