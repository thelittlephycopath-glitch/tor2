package org.torproject.vpn.autonomy

/**
 * Deterministic, explainable policy decisions for the autonomous backend.
 *
 * This is a planner, not an enforcement boundary. It has no network, filesystem, device-identifier,
 * Android routing, or Onionmasq side effects. The service must not interpret a recommendation as
 * proof that a kill switch or leak protection is actually enforced.
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
        val actions = linkedSetOf<AutonomyAction>()
        val hostile = signals.trust == NetworkTrust.HOSTILE ||
            signals.dnsHijackSuspected ||
            signals.tlsInterceptionSuspected

        val profile = when {
            hostile || signals.trust == NetworkTrust.PUBLIC -> ProtectionProfile.MAXIMUM
            signals.trust == NetworkTrust.UNKNOWN || !signals.networkAvailable ->
                ProtectionProfile.HIGH
            else -> ProtectionProfile.BALANCED
        }

        if (hostile) reasons += "Potential network interference detected"
        if (signals.trust == NetworkTrust.PUBLIC) {
            reasons += "Public network requires stricter policy"
        }
        if (signals.trust == NetworkTrust.UNKNOWN) reasons += "Network has no trusted history"
        if (!signals.networkAvailable) reasons += "Network connectivity is unavailable"
        if (signals.captivePortalDetected) reasons += "Captive portal detected"
        if (signals.dnsConfigurationConflict) {
            reasons += "Device DNS policy conflicts with tunnel setup"
        }
        if (signals.repeatedConnectionFailures >= repeatedFailureThreshold) {
            reasons += "Repeated connection failures"
        }

        val lowBattery = signals.batteryPercent <= lowBatteryThresholdPercent
        val lowData = signals.remainingDataBudgetMb?.let { it <= lowDataBudgetThresholdMb } == true
        if (lowBattery) reasons += "Battery budget is low"
        if (lowData) reasons += "Data budget is low"

        // An Onionmasq bootstrap event is not a leak test. All predicates must be independently
        // observed before a future executor may consider restoring traffic.
        val trafficReleaseEligible = signals.networkAvailable &&
            signals.tunnelReady &&
            signals.leakChecksPassed &&
            !signals.leakCheckFailed &&
            !hostile &&
            !signals.dnsConfigurationConflict &&
            !signals.captivePortalDetected

        if (!trafficReleaseEligible) {
            actions += AutonomyAction.KEEP_TRAFFIC_BLOCKED
            reasons +=
                "Tunnel readiness and independent leak checks must both pass before traffic release"
        }
        if (signals.tunnelReady && !signals.leakChecksPassed && !signals.leakCheckFailed) {
            actions += AutonomyAction.RUN_LEAK_CHECKS
        }
        if (signals.leakCheckFailed || signals.dnsConfigurationConflict) {
            actions += AutonomyAction.NOTIFY_USER
        }
        if (signals.captivePortalDetected) {
            // This is a plan for a future narrowly scoped portal policy, not permission to bypass Tor.
            actions += AutonomyAction.ALLOW_CAPTIVE_PORTAL_ONLY
        }

        val failuresExceeded = signals.repeatedConnectionFailures >= repeatedFailureThreshold
        val recommendBridgeFailover = signals.networkAvailable && (failuresExceeded || hostile)
        val recommendCircuitRefresh = signals.networkAvailable && (hostile || failuresExceeded)
        if (recommendBridgeFailover) actions += AutonomyAction.REQUEST_BRIDGE_FAILOVER
        if (recommendCircuitRefresh) actions += AutonomyAction.REQUEST_CIRCUIT_REFRESH
        if (signals.networkAvailable && !signals.tunnelReady && failuresExceeded) {
            actions += AutonomyAction.REQUEST_TUNNEL_RECONNECT
        }

        val coverTrafficAllowedByBudget = !lowBattery && !lowData && !signals.mobileData
        if (signals.coverTrafficRequested && !coverTrafficAllowedByBudget) {
            actions += AutonomyAction.SUPPRESS_COVER_TRAFFIC
        }
        if (trafficReleaseEligible) actions += AutonomyAction.RESTORE_VERIFIED_TUNNELED_TRAFFIC

        return AutonomyDecision(
            profile = profile,
            blockTrafficUntilTunnelVerified = !trafficReleaseEligible,
            allowCaptivePortalOnly = signals.captivePortalDetected,
            recommendBridgeFailover = recommendBridgeFailover,
            recommendCircuitRefresh = recommendCircuitRefresh,
            coverTrafficAllowedByBudget = coverTrafficAllowedByBudget,
            reasons = reasons.distinct(),
            trafficReleaseEligible = trafficReleaseEligible,
            recommendedActions = actions.toList()
        )
    }

    /**
     * Ranks observed candidates only. No bridge is probed and no fallback is executed here.
     * Reliability is the primary signal; failures and latency break ties deterministically.
     */
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
