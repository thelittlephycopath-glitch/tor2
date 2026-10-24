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
    private var lowBatteryActive = false
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
                consecutiveFailures = 0
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
            AutonomyEventType.DNS_HIJACK_SUSPECTED -> {
                environmentalSignals = environmentalSignals.copy(dnsHijackSuspected = true)
                tunnelReady = false
                leakChecksPassed = false
                leakCheckFailed = false
            }
            AutonomyEventType.DNS_HIJACK_CLEARED -> {
                environmentalSignals = environmentalSignals.copy(dnsHijackSuspected = false)
                tunnelReady = false
                leakChecksPassed = false
                leakCheckFailed = false
            }
            AutonomyEventType.TLS_INTERCEPTION_SUSPECTED -> {
                environmentalSignals = environmentalSignals.copy(tlsInterceptionSuspected = true)
                tunnelReady = false
                leakChecksPassed = false
                leakCheckFailed = false
            }
            AutonomyEventType.TLS_INTERCEPTION_CLEARED -> {
                environmentalSignals = environmentalSignals.copy(tlsInterceptionSuspected = false)
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
            AutonomyEventType.NETWORK_MONITOR_UNAVAILABLE -> {
                environmentalSignals = environmentalSignals.copy(networkAvailable = false)
                tunnelReady = false
                leakChecksPassed = false
                leakCheckFailed = false
            }
            AutonomyEventType.RESOURCE_MONITOR_UNAVAILABLE -> {
                environmentalSignals = environmentalSignals.copy(batteryReadingAvailable = false)
                lowBatteryActive = false
            }
            AutonomyEventType.SERVICE_CREATED,
            AutonomyEventType.LOW_BATTERY,
            AutonomyEventType.LOW_DATA_BUDGET -> Unit
        }
        return calculateDecision()
    }

    /**
     * Updates the fields owned by the Android network callback without overwriting trust, anomaly,
     * resource-budget, or service-owned DNS-conflict signals from other components.
     */
    @Synchronized
    fun updateNetworkEnvironment(
        networkAvailable: Boolean,
        captivePortalDetected: Boolean,
        mobileData: Boolean
    ): AutonomyDecision {
        if (environmentalSignals.networkAvailable != networkAvailable) {
            // A default-network change invalidates readiness regardless of callback ordering.
            tunnelReady = false
            leakChecksPassed = false
            leakCheckFailed = false
        }
        environmentalSignals = environmentalSignals.copy(
            networkAvailable = networkAvailable,
            captivePortalDetected = captivePortalDetected,
            mobileData = mobileData,
            repeatedConnectionFailures = consecutiveFailures,
            tunnelReady = tunnelReady,
            leakChecksPassed = leakChecksPassed,
            leakCheckFailed = leakCheckFailed,
            dnsConfigurationConflict = environmentalSignals.dnsConfigurationConflict
        )
        return calculateDecision()
    }

    /**
     * Supplies a validated battery reading from Android's sticky battery broadcast.
     * Unknown readings use a separate unavailable state so they are not mislabeled as 0% battery.
     */
    @Synchronized
    fun updateBatteryPercent(percent: Int): AutonomyDecision {
        require(percent in 0..100) { "Battery percentage must be between 0 and 100" }
        environmentalSignals = environmentalSignals.copy(
            batteryPercent = percent,
            batteryReadingAvailable = true
        )
        val wasLow = lowBatteryActive
        lowBatteryActive = percent <= LOW_BATTERY_THRESHOLD_PERCENT
        if (lowBatteryActive && !wasLow) {
            journal.record(
                AutonomyEvent(
                    type = AutonomyEventType.LOW_BATTERY,
                    source = AutonomyEventSource.RESOURCE_MONITOR,
                    elapsedRealtimeMillis = elapsedRealtimeClock()
                )
            )
        }
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
    companion object {
        private const val LOW_BATTERY_THRESHOLD_PERCENT = 20
    }
}
