package org.torproject.vpn.autonomy

/**
 * The verification report contains facts established by a separate verifier; this gate does not
 * perform network probes or infer safety from tunnel bootstrap alone.
 *
 * This gate checks required controls and monotonic timestamp freshness. The coordinator also
 * requires the report's session generation to match the current tunnel/network generation.
 * Unknown or unmeasured controls must be reported as false by the caller.
 */
data class LeakVerificationEvidence(
    val checkedAtElapsedRealtimeMillis: Long,
    val ipv4DefaultRouteProtected: Boolean,
    val ipv6ContainedOrBlocked: Boolean,
    val dnsProtectedByTunnel: Boolean,
    val systemLockdownVerified: Boolean,
    val appRoutingVerified: Boolean,
    /** Generation of the tunnel/network session this evidence belongs to; null is untrusted. */
    val sessionGeneration: Long? = null
) {
    init {
        require(checkedAtElapsedRealtimeMillis >= 0L) {
            "Verification timestamp must not be negative"
        }
        require(sessionGeneration == null || sessionGeneration >= 0L) {
            "Session generation must not be negative"
        }
    }
}

enum class LeakVerificationFailure {
    STALE_REPORT,
    FUTURE_REPORT,
    IPV4_ROUTE_NOT_VERIFIED,
    IPV6_NOT_CONTAINED,
    DNS_NOT_PROTECTED,
    SYSTEM_LOCKDOWN_NOT_VERIFIED,
    APP_ROUTING_NOT_VERIFIED,
    TUNNEL_NOT_READY,
    NETWORK_NOT_AVAILABLE,
    DNS_CONFIGURATION_CONFLICT,
    HOSTILE_NETWORK_INDICATOR,
    CAPTIVE_PORTAL_ACTIVE,
    SESSION_GENERATION_MISMATCH
}

data class LeakVerificationResult(val failures: List<LeakVerificationFailure>) {
    val passed: Boolean
        get() = failures.isEmpty()
}

/**
 * Strict, side-effect-free gate for caller-supplied leak-check evidence.
 *
 * This class cannot itself prove that a report is truthful: the platform verifier must measure
 * each field independently. Missing/unsupported measurements must fail closed, not be guessed.
 */
class LeakVerificationGate(
    val maxReportAgeMillis: Long = DEFAULT_MAX_REPORT_AGE_MILLIS
) {
    init {
        require(maxReportAgeMillis > 0L) { "Maximum report age must be positive" }
    }

    fun evaluate(
        evidence: LeakVerificationEvidence,
        nowElapsedRealtimeMillis: Long
    ): LeakVerificationResult {
        require(nowElapsedRealtimeMillis >= 0L) {
            "Current elapsed realtime must not be negative"
        }

        val failures = linkedSetOf<LeakVerificationFailure>()
        when {
            evidence.checkedAtElapsedRealtimeMillis > nowElapsedRealtimeMillis ->
                failures += LeakVerificationFailure.FUTURE_REPORT
            nowElapsedRealtimeMillis - evidence.checkedAtElapsedRealtimeMillis > maxReportAgeMillis ->
                failures += LeakVerificationFailure.STALE_REPORT
        }

        if (!evidence.ipv4DefaultRouteProtected) failures += LeakVerificationFailure.IPV4_ROUTE_NOT_VERIFIED
        if (!evidence.ipv6ContainedOrBlocked) failures += LeakVerificationFailure.IPV6_NOT_CONTAINED
        if (!evidence.dnsProtectedByTunnel) failures += LeakVerificationFailure.DNS_NOT_PROTECTED
        if (!evidence.systemLockdownVerified) failures += LeakVerificationFailure.SYSTEM_LOCKDOWN_NOT_VERIFIED
        if (!evidence.appRoutingVerified) failures += LeakVerificationFailure.APP_ROUTING_NOT_VERIFIED

        return LeakVerificationResult(failures.toList())
    }

    companion object {
        const val DEFAULT_MAX_REPORT_AGE_MILLIS = 15_000L
    }
}
