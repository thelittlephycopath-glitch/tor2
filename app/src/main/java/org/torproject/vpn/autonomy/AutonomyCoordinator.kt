package org.torproject.vpn.autonomy

/**
 * Stateful adapter between existing VPN lifecycle observations and the pure policy engine.
 *
 * The coordinator never starts/stops the VPN, changes app routing, probes a bridge, or releases
 * traffic. It produces advisory decisions for reviewed integration in later phases.
 */
class AutonomyCoordinator(
    private val policyEngine: AutonomousPolicyEngine = AutonomousPolicyEngine(),
    val journal: AutonomyEventJournal = AutonomyEventJournal(),
    private val config: AutonomyConfig = AutonomyConfig(),
    private val elapsedRealtimeClock: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private var environmentalSignals = NetworkSignals()
    private var tunnelReady = false
    private var leakChecksPassed = false
    private var leakCheckFailed = false
    private var consecutiveFailures = 0
    private var lastDecision = policyEngine.evaluate(environmentalSignals)

    @Synchronized
    fun observe(
        type: AutonomyEventType,
        source: AutonomyEventSource = AutonomyEventSource.VPN_SERVICE,
        elapsedRealtimeMillis: Long = elapsedRealtimeClock()
    ): AutonomyDecision {
        journal.record(AutonomyEvent(type, source, elapsedRealtimeMillis))
        when (type) {
            AutonomyEventType.TUNNEL_START_REQUESTED,
            AutonomyEventType.TUNNEL_BOOTSTRAPPING -> {
                tunnelReady = false
                leakChecksPassed = false
                leakCheckFailed = false
            }
            AutonomyEventType.TUNNEL_READY -> {
                tunnelReady = true
                leakChecksPassed = false
                leakCheckFailed = false
            }
            AutonomyEventType.TUNNEL_FAILED,
            AutonomyEventType.CONNECTION_FAILED -> {
                consecutiveFailures = (consecutiveFailures + 1).coerceAtMost(10_000)
                tunnelReady = false
                leakChecksPassed = false
                leakCheckFailed = false
            }
            AutonomyEventType.NETWORK_CONNECTIVITY_LOST -> {
                environmentalSignals = environmentalSignals.copy(networkAvailable = false)
                tunnelReady = false
                leakChecksPassed = false
                leakCheckFailed = false
            }
            AutonomyEventType.NETWORK_CONNECTIVITY_RESTORED -> {
                environmentalSignals = environmentalSignals.copy(networkAvailable = true)
                // A network return invalidates the previous tunnel verification.
                tunnelReady = false
                leakChecksPassed = false
                leakCheckFailed = false
            }
            AutonomyEventType.DNS_CONFIGURATION_CONFLICT -> {
                environmentalSignals = environmentalSignals.copy(dnsConfigurationConflict = true)
                tunnelReady = false
                leakChecksPassed = false
                leakCheckFailed = false
            }
            AutonomyEventType.DNS_CONFIGURATION_CLEARED -> {
                environmentalSignals = environmentalSignals.copy(dnsConfigurationConflict = false)
                tunnelReady = false
                leakChecksPassed = false
                leakCheckFailed = false
            }
            AutonomyEventType.CAPTIVE_PORTAL_DETECTED -> {
                environmentalSignals = environmentalSignals.copy(captivePortalDetected = true)
                tunnelReady = false
                leakChecksPassed = false
                leakCheckFailed = false
            }
            AutonomyEventType.CAPTIVE_PORTAL_CLEARED -> {
                environmentalSignals = environmentalSignals.copy(captivePortalDetected = false)
                tunnelReady = false
                leakChecksPassed = false
                leakCheckFailed = false
            }
            AutonomyEventType.LEAK_CHECK_STARTED -> {
                leakChecksPassed = false
                leakCheckFailed = false
            }
            AutonomyEventType.LEAK_CHECK_PASSED -> {
                // A "pass" without a ready tunnel is deliberately ignored.
                leakChecksPassed = tunnelReady &&
                    environmentalSignals.networkAvailable &&
                    !environmentalSignals.dnsConfigurationConflict
                leakCheckFailed = false
            }
            AutonomyEventType.LEAK_CHECK_FAILED -> {
                leakChecksPassed = false
                leakCheckFailed = true
            }
            AutonomyEventType.TUNNEL_STOPPED,
            AutonomyEventType.SERVICE_STOPPED -> {
                tunnelReady = false
                leakChecksPassed = false
                leakCheckFailed = false
            }
            AutonomyEventType.SERVICE_CREATED,
            AutonomyEventType.LOW_BATTERY,
            AutonomyEventType.LOW_DATA_BUDGET -> Unit
        }
        return calculateDecision()
    }

    /**
     * Updates environment facts supplied by a future Android monitor. Tunnel and leak-check state
     * are coordinator-owned and cannot be set by this method's caller.
     */
    @Synchronized
    fun updateEnvironment(signals: NetworkSignals): AutonomyDecision {
        environmentalSignals = signals.copy(
            repeatedConnectionFailures = consecutiveFailures,
            tunnelReady = tunnelReady,
            leakChecksPassed = leakChecksPassed,
            leakCheckFailed = leakCheckFailed
        )
        return calculateDecision()
    }

    @Synchronized
    fun currentDecision(): AutonomyDecision = lastDecision

    @Synchronized
    fun currentSignals(): NetworkSignals = environmentalSignals.copy(
        repeatedConnectionFailures = consecutiveFailures,
        tunnelReady = tunnelReady,
        leakChecksPassed = leakChecksPassed,
        leakCheckFailed = leakCheckFailed
    )

    private fun calculateDecision(): AutonomyDecision {
        val observed = currentSignals()
        val decision = policyEngine.evaluate(observed)
        val automationConfigured = config.enabled &&
            config.mode != AutonomyMode.OBSERVE_ONLY &&
            config.hasAutomationFeatureEnabled
        lastDecision = decision.copy(automationConfigured = automationConfigured)
        return lastDecision
    }
}
