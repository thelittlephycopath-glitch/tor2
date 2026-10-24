package org.torproject.vpn.autonomy

/**
 * Deterministic, explainable policy decisions for the future autonomous backend.
 * Advisory only: it has no network, filesystem, device-identifier, or Android side effects.
 */
class AutonomousPolicyEngine(
    private val lowBatteryThresholdPercent: Int = 20,
    private val lowDataBudgetThresholdMb: Int = 50,
    private val repeatedFailureThreshold: Int = 3
) {
    init {
        require(lowBatteryThresholdPercent in 0..100)
        require(lowDataBudgetThresholdMb >= 0)
        require(repeatedFailureThreshold > 0)
    }

    fun evaluate(signals: NetworkSignals): AutonomyDecision {
        val reasons = mutableListOf<String>()
        val hostile = signals.trust == NetworkTrust.HOSTILE ||
            signals.dnsHijackSuspected ||
            signals.tlsInterceptionSuspected

        val profile = when {
            hostile || signals.trust == NetworkTrust.PUBLIC -> ProtectionProfile.MAXIMUM
            signals.trust == NetworkTrust.UNKNOWN -> ProtectionProfile.HIGH
            else -> ProtectionProfile.BALANCED
        }

        if (hostile) reasons += "Potential network interference detected"
        if (signals.trust == NetworkTrust.PUBLIC) reasons += "Public network requires stricter policy"
        if (signals.trust == NetworkTrust.UNKNOWN) reasons += "Network has no trusted history"
        if (signals.captivePortalDetected) reasons += "Captive portal detected"
        if (signals.repeatedConnectionFailures >= repeatedFailureThreshold) {
            reasons += "Repeated connection failures"
        }

        val lowBattery = signals.batteryPercent <= lowBatteryThresholdPercent
        val lowData = signals.remainingDataBudgetMb?.let { it <= lowDataBudgetThresholdMb } == true
        if (lowBattery) reasons += "Battery budget is low"
        if (lowData) reasons += "Data budget is low"

        return AutonomyDecision(
            profile = profile,
            blockTrafficUntilTunnelVerified = true,
            allowCaptivePortalOnly = signals.captivePortalDetected,
            recommendBridgeFailover = signals.repeatedConnectionFailures >= repeatedFailureThreshold ||
                signals.trust == NetworkTrust.HOSTILE,
            recommendCircuitRefresh = hostile ||
                signals.repeatedConnectionFailures >= repeatedFailureThreshold,
            coverTrafficAllowedByBudget = !lowBattery && !lowData && !signals.mobileData,
            reasons = reasons.toList()
        )
    }

    /** Ranks observed bridge candidates without probing them; blocked candidates are excluded. */
    fun rankBridges(candidates: List<BridgeCandidate>): List<BridgeCandidate> =
        candidates.asSequence()
            .filterNot { it.knownBlocked }
            .sortedWith(
                compareByDescending<BridgeCandidate> { it.recentSuccessRate }
                    .thenBy { it.recentFailures }
                    .thenBy { it.latencyMillis }
                    .thenBy { it.transport.ordinal }
            )
            .toList()
}
