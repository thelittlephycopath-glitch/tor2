package org.torproject.vpn.autonomy

/**
 * Inputs and outputs for the additive autonomous policy layer.
 * This module is platform-independent and does not touch the active VPN tunnel.
 */
enum class NetworkTrust { TRUSTED, UNKNOWN, PUBLIC, HOSTILE }
enum class ProtectionProfile { BALANCED, HIGH, MAXIMUM }
enum class Transport { WEBTUNNEL, SNOWFLAKE, OBFS4, DIRECT_BRIDGE }

data class NetworkSignals(
    val trust: NetworkTrust = NetworkTrust.UNKNOWN,
    val dnsHijackSuspected: Boolean = false,
    val tlsInterceptionSuspected: Boolean = false,
    val repeatedConnectionFailures: Int = 0,
    val captivePortalDetected: Boolean = false,
    val batteryPercent: Int = 100,
    val mobileData: Boolean = false,
    val remainingDataBudgetMb: Int? = null
) {
    init {
        require(repeatedConnectionFailures >= 0) { "Failure count cannot be negative" }
        require(batteryPercent in 0..100) { "Battery percentage must be between 0 and 100" }
        require(remainingDataBudgetMb == null || remainingDataBudgetMb >= 0) {
            "Remaining data budget cannot be negative"
        }
    }
}

data class BridgeCandidate(
    val transport: Transport,
    val recentSuccessRate: Double,
    val latencyMillis: Long,
    val recentFailures: Int,
    val knownBlocked: Boolean = false
) {
    init {
        require(recentSuccessRate.isFinite() && recentSuccessRate in 0.0..1.0) {
            "Success rate must be finite and between 0 and 1"
        }
        require(latencyMillis >= 0) { "Latency cannot be negative" }
        require(recentFailures >= 0) { "Failure count cannot be negative" }
    }
}

data class AutonomyDecision(
    val profile: ProtectionProfile,
    val blockTrafficUntilTunnelVerified: Boolean,
    val allowCaptivePortalOnly: Boolean,
    val recommendBridgeFailover: Boolean,
    val recommendCircuitRefresh: Boolean,
    val coverTrafficAllowedByBudget: Boolean,
    val reasons: List<String>
)
