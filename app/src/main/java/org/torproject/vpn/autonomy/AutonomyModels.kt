package org.torproject.vpn.autonomy

/**
 * Platform-independent inputs and outputs for the additive autonomous privacy layer.
 *
 * These values are policy descriptions only. They do not establish a tunnel, alter Android routing,
 * probe relays, write files, or release traffic. Enforcement must remain in verified platform code.
 */
enum class NetworkTrust { TRUSTED, UNKNOWN, PUBLIC, HOSTILE }
enum class ProtectionProfile { BALANCED, HIGH, MAXIMUM }
enum class Transport { WEBTUNNEL, SNOWFLAKE, OBFS4, DIRECT_BRIDGE }

enum class AutonomyMode {
    /** Collect decisions and diagnostics only. This is the default for this branch. */
    OBSERVE_ONLY,

    /** May permit only the limited, reviewed automatic actions explicitly configured. */
    GUARDED_AUTOMATION,

    /** Experimental policy evaluation. This is never enabled by default. */
    EXPERIMENTAL
}

enum class AutonomyEventSource {
    VPN_SERVICE,
    NETWORK_MONITOR,
    LEAK_CHECKER,
    RESOURCE_MONITOR,
    POLICY_ENGINE,
    TEST
}

enum class AutonomyEventType {
    SERVICE_CREATED,
    TUNNEL_START_REQUESTED,
    TUNNEL_BOOTSTRAPPING,
    TUNNEL_READY,
    TUNNEL_FAILED,
    TUNNEL_STOPPED,
    CONNECTION_FAILED,
    DNS_CONFIGURATION_CONFLICT,
    DNS_CONFIGURATION_CLEARED,
    NETWORK_CONNECTIVITY_LOST,
    NETWORK_CONNECTIVITY_RESTORED,
    NETWORK_MONITOR_UNAVAILABLE,
    DNS_HIJACK_SUSPECTED,
    DNS_HIJACK_CLEARED,
    TLS_INTERCEPTION_SUSPECTED,
    TLS_INTERCEPTION_CLEARED,
    CAPTIVE_PORTAL_DETECTED,
    CAPTIVE_PORTAL_CLEARED,
    LEAK_CHECK_STARTED,
    LEAK_CHECK_PASSED,
    LEAK_CHECK_FAILED,
    LOW_BATTERY,
    LOW_DATA_BUDGET,
    SERVICE_STOPPED
}

enum class AutonomyAction {
    KEEP_TRAFFIC_BLOCKED,
    RUN_LEAK_CHECKS,
    REQUEST_TUNNEL_RECONNECT,
    REQUEST_BRIDGE_FAILOVER,
    REQUEST_CIRCUIT_REFRESH,
    ALLOW_CAPTIVE_PORTAL_ONLY,
    SUPPRESS_COVER_TRAFFIC,
    NOTIFY_USER,
    RESTORE_VERIFIED_TUNNELED_TRAFFIC
}

/**
 * All flags default off. This object is not yet persisted or wired to an automation executor.
 * Enabling a flag alone must never be interpreted as proof that its action is implemented.
 */
data class AutonomyConfig(
    val enabled: Boolean = false,
    val mode: AutonomyMode = AutonomyMode.OBSERVE_ONLY,
    val automaticReconnect: Boolean = false,
    val automaticBridgeFailover: Boolean = false,
    val automaticCircuitRefresh: Boolean = false,
    val coverTraffic: Boolean = false,
    val decoyData: Boolean = false,
    val honeyvault: Boolean = false,
    val persistNetworkLearning: Boolean = false
) {
    val hasAutomationFeatureEnabled: Boolean
        get() = automaticReconnect ||
            automaticBridgeFailover ||
            automaticCircuitRefresh ||
            coverTraffic ||
            decoyData ||
            honeyvault ||
            persistNetworkLearning
}

/**
 * Privacy-minimised event record. It intentionally has no hostname, IP, SSID, package name,
 * destination, query, app-content, account, or free-form detail field.
 */
data class AutonomyEvent(
    val type: AutonomyEventType,
    val source: AutonomyEventSource,
    val elapsedRealtimeMillis: Long,
    val sequence: Long = 0L
) {
    init {
        require(elapsedRealtimeMillis >= 0L) { "Elapsed realtime must not be negative" }
        require(sequence >= 0L) { "Event sequence must not be negative" }
    }
}

data class NetworkSignals(
    val trust: NetworkTrust = NetworkTrust.UNKNOWN,
    val dnsHijackSuspected: Boolean = false,
    val tlsInterceptionSuspected: Boolean = false,
    val repeatedConnectionFailures: Int = 0,
    val captivePortalDetected: Boolean = false,
    val batteryPercent: Int = 100,
    val mobileData: Boolean = false,
    val remainingDataBudgetMb: Int? = null,
    val networkAvailable: Boolean = false,
    val tunnelReady: Boolean = false,
    val leakChecksPassed: Boolean = false,
    val leakCheckFailed: Boolean = false,
    val dnsConfigurationConflict: Boolean = false,
    val coverTrafficRequested: Boolean = false
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
    val reasons: List<String>,
    /** True only when network, tunnel, leak-check, and DNS-policy predicates all pass. */
    val trafficReleaseEligible: Boolean = false,
    /** Advisory only. No item is executed unless a separately reviewed executor supports it. */
    val recommendedActions: List<AutonomyAction> = emptyList(),
    /** Configuration gate only; not proof that an action executor exists or is active. */
    val automationConfigured: Boolean = false
)
