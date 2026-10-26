package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeHealthLedgerTest {
    @Test
    fun emptyTransportUsesNeutralPriorWithoutClaimingObservedSuccess() {
        val health = BridgeHealthLedger().snapshot(Transport.SNOWFLAKE)

        assertEquals(0, health.sampleCount)
        assertEquals(0.5, health.successRate, 0.0)
        assertFalse(health.knownBlocked)
        assertEquals(null, health.lastObservedElapsedRealtimeMillis)
    }

    @Test
    fun recentOutcomesAreBoundedAndSmoothed() {
        val ledger = BridgeHealthLedger(maxSamplesPerTransport = 3)
        ledger.record(Transport.OBFS4, succeeded = true, latencyMillis = 100, elapsedRealtimeMillis = 1)
        ledger.record(Transport.OBFS4, succeeded = false, latencyMillis = 900, elapsedRealtimeMillis = 2)
        ledger.record(Transport.OBFS4, succeeded = true, latencyMillis = 200, elapsedRealtimeMillis = 3)
        ledger.record(Transport.OBFS4, succeeded = true, latencyMillis = 300, elapsedRealtimeMillis = 4)

        val health = ledger.snapshot(Transport.OBFS4)
        assertEquals(3, health.sampleCount)
        // The oldest success was evicted; Beta(1,1) smoothing yields (2 + 1) / (3 + 2).
        assertEquals(0.6, health.successRate, 0.0001)
        assertEquals(250L, health.meanLatencyMillis)
        assertEquals(4L, health.lastObservedElapsedRealtimeMillis)
    }

    @Test
    fun repeatedFailuresMarkTransportBlockedUntilSuccessResetsStreak() {
        val ledger = BridgeHealthLedger(blockedFailureStreak = 3)
        repeat(3) { index ->
            ledger.record(
                Transport.WEBTUNNEL,
                succeeded = false,
                latencyMillis = 0,
                elapsedRealtimeMillis = index.toLong()
            )
        }
        assertTrue(ledger.snapshot(Transport.WEBTUNNEL).knownBlocked)
        ledger.record(Transport.WEBTUNNEL, true, 400, 3)
        val recovered = ledger.snapshot(Transport.WEBTUNNEL)
        assertFalse(recovered.knownBlocked)
        assertEquals(0, recovered.consecutiveFailures)
    }

    @Test
    fun samplesMustUseMonotonicTime() {
        val ledger = BridgeHealthLedger()
        ledger.record(Transport.DIRECT_BRIDGE, true, 500, 20)
        assertThrows(IllegalArgumentException::class.java) {
            ledger.record(Transport.DIRECT_BRIDGE, false, 500, 19)
        }
    }

    @Test
    fun clearRemovesAllSessionLearning() {
        val ledger = BridgeHealthLedger()
        ledger.record(Transport.SNOWFLAKE, true, 100, 1)
        ledger.clear()

        assertEquals(0, ledger.snapshot(Transport.SNOWFLAKE).sampleCount)
    }

    @Test
    fun policyRankingBalancesReliabilityAgainstLatencyAndExcludesBlockedCandidates() {
        val engine = AutonomousPolicyEngine()
        val candidates = listOf(
            BridgeCandidate(Transport.WEBTUNNEL, 0.95, 8_000, 2),
            BridgeCandidate(Transport.SNOWFLAKE, 0.80, 100, 0),
            BridgeCandidate(Transport.OBFS4, 1.0, 10, 0, knownBlocked = true)
        )

        val ranked = engine.rankBridges(candidates)
        assertEquals(listOf(Transport.SNOWFLAKE, Transport.WEBTUNNEL), ranked.map { it.transport })
        assertFalse(ranked.any { it.knownBlocked })
    }
    @Test
    fun randomizedOutcomeStreamPreservesBoundsAndValidStatistics() {
        val ledger = BridgeHealthLedger(maxSamplesPerTransport = 32)
        Transport.entries.forEach { transport ->
            repeat(200) { index ->
                ledger.record(
                    transport = transport,
                    succeeded = index % 5 != 0,
                    latencyMillis = (index * 7L) % 4_000L,
                    elapsedRealtimeMillis = index.toLong()
                )
            }

            val health = ledger.snapshot(transport)
            assertEquals(32, health.sampleCount)
            assertTrue(health.successRate in 0.0..1.0)
            assertTrue(health.meanLatencyMillis > 0L)
            assertTrue(health.consecutiveFailures in 0..32)
            assertEquals(199L, health.lastObservedElapsedRealtimeMillis)
        }
    }

}
