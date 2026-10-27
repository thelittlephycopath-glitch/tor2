package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LeakVerificationGateTest {
    private val gate = LeakVerificationGate()
    private val now = 10_000L

    @Test
    fun onlyCompleteFreshEvidencePasses() {
        val result = gate.evaluate(validEvidence(now), now)
        assertTrue(result.passed)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun everyRequiredControlFailsClosedWhenMissing() {
        val evidence = validEvidence(now).copy(
            ipv4DefaultRouteProtected = false,
            ipv6ContainedOrBlocked = false,
            dnsProtectedByTunnel = false,
            systemLockdownVerified = false,
            appRoutingVerified = false
        )

        val result = gate.evaluate(evidence, now)
        assertFalse(result.passed)
        assertEquals(
            setOf(
                LeakVerificationFailure.IPV4_ROUTE_NOT_VERIFIED,
                LeakVerificationFailure.IPV6_NOT_CONTAINED,
                LeakVerificationFailure.DNS_NOT_PROTECTED,
                LeakVerificationFailure.SYSTEM_LOCKDOWN_NOT_VERIFIED,
                LeakVerificationFailure.APP_ROUTING_NOT_VERIFIED
            ),
            result.failures.toSet()
        )
    }

    @Test
    fun staleAndFutureReportsCannotPassEvenIfEveryCheckIsTrue() {
        val stale = gate.evaluate(
            validEvidence(now - LeakVerificationGate.DEFAULT_MAX_REPORT_AGE_MILLIS - 1L),
            now
        )
        assertFalse(stale.passed)
        assertTrue(stale.failures.contains(LeakVerificationFailure.STALE_REPORT))

        val future = gate.evaluate(validEvidence(now + 1L), now)
        assertFalse(future.passed)
        assertTrue(future.failures.contains(LeakVerificationFailure.FUTURE_REPORT))
    }

    @Test
    fun exactFreshnessBoundaryIsAccepted() {
        val boundary = gate.evaluate(
            validEvidence(now - LeakVerificationGate.DEFAULT_MAX_REPORT_AGE_MILLIS),
            now
        )
        assertTrue(boundary.passed)
    }

    @Test
    fun rejectsInvalidClockAndAgeConfiguration() {
        assertThrows(IllegalArgumentException::class.java) {
            LeakVerificationGate(maxReportAgeMillis = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            gate.evaluate(validEvidence(now), -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LeakVerificationEvidence(-1L, true, true, true, true, true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validEvidence(now).copy(sessionGeneration = -1L)
        }
    }

    private fun validEvidence(timestamp: Long) = LeakVerificationEvidence(
        checkedAtElapsedRealtimeMillis = timestamp,
        ipv4DefaultRouteProtected = true,
        ipv6ContainedOrBlocked = true,
        dnsProtectedByTunnel = true,
        systemLockdownVerified = true,
        appRoutingVerified = true
    )
}
