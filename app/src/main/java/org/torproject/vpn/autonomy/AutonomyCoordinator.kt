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
    private val bridgeHealthLedger: BridgeHealthLedger = BridgeHealthLedger(),
    private val elapsedRealtimeClock: () -> Long = { System.nanoTime() / 1_000_000L },
    private val recoveryPlanner: AutonomyRecoveryPlanner = AutonomyRecoveryPlanner(),
    private val leakVerificationGate: LeakVerificationGate = LeakVerificationGate(),
    private val circuitRefreshPlanner: TorCircuitRefreshPlanner = TorCircuitRefreshPlanner(),
    private val coverTrafficPolicyPlanner: CoverTrafficPolicyPlanner = CoverTrafficPolicyPlanner(),
    private val trustClassifier: NetworkTrustClassifier = NetworkTrustClassifier()
) {
    private var environmentalSignals = NetworkSignals()
    private var tunnelReady = false
    private var leakChecksPassed = false
    private var leakCheckFailed = false
    private var validatedLeakReportReady = false
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
                // Transport outcomes from the previous network are not reliable evidence here.
                bridgeHealthLedger.clear()
                recoveryPlanner.onNetworkChanged()
            }
            AutonomyEventType.NETWORK_CONNECTIVITY_RESTORED -> {
                environmentalSignals = environmentalSignals.copy(networkAvailable = true)
                // A network return invalidates both the old tunnel verification and old transport
                // learning. Failure streaks from a previous network must not trigger false failover.
                tunnelReady = false
                leakChecksPassed = false
                leakCheckFailed = false
                consecutiveFailures = 0
                bridgeHealthLedger.clear()
                recoveryPlanner.onNetworkChanged()
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
                // Starting a new check invalidates any previous pass, but does not erase a recorded
                // failure. Only a fresh, complete, safe report may clear that failure.
                if (source == AutonomyEventSource.LEAK_CHECKER) {
                    leakChecksPassed = false
                    validatedLeakReportReady = false
                }
            }
            AutonomyEventType.LEAK_CHECK_PASSED -> {
                // A pass is accepted only from the checker and only against the current safe state.
                // Invalid or spoofed events must not clear an existing failure.
                if (source == AutonomyEventSource.LEAK_CHECKER && validatedLeakReportReady) {
                    validatedLeakReportReady = false
                    val passIsSafe = tunnelReady &&
                        environmentalSignals.networkAvailable &&
                        !environmentalSignals.dnsConfigurationConflict &&
                        !environmentalSignals.dnsHijackSuspected &&
                        !environmentalSignals.tlsInterceptionSuspected &&
                        !environmentalSignals.captivePortalDetected
                    leakChecksPassed = passIsSafe
                    if (passIsSafe) {
                        leakCheckFailed = false
                        recoveryPlanner.resetAfterVerifiedTunnel()
                    }
                }
            }
            AutonomyEventType.LEAK_CHECK_FAILED -> {
                // A failed verification is authoritative only when emitted by the leak checker.
                // Other adapters may record observations but cannot forge a check result.
                if (source == AutonomyEventSource.LEAK_CHECKER) {
                    validatedLeakReportReady = false
                    leakChecksPassed = false
                    leakCheckFailed = true
                }
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
                environmentalSignals = environmentalSignals.copy(
                    batteryReadingAvailable = false,
                    charging = false,
                    chargingReadingAvailable = false
                )
                lowBatteryActive = false
            }
            AutonomyEventType.SERVICE_CREATED,
            AutonomyEventType.LOW_BATTERY,
            AutonomyEventType.LOW_DATA_BUDGET,
            AutonomyEventType.BRIDGE_ATTEMPT_SUCCEEDED,
            AutonomyEventType.BRIDGE_ATTEMPT_FAILED -> Unit
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
        val networkChanged =
            environmentalSignals.networkAvailable != networkAvailable ||
                environmentalSignals.captivePortalDetected != captivePortalDetected ||
                environmentalSignals.mobileData != mobileData
        if (networkChanged) {
            // Network or transport changes invalidate verification and session-scoped learning.
            tunnelReady = false
            leakChecksPassed = false
            leakCheckFailed = false
            consecutiveFailures = 0
            bridgeHealthLedger.clear()
            recoveryPlanner.onNetworkChanged()
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

    /**
     * Supplies charging telemetry separately from battery percentage. Unknown charging state is
     * represented explicitly so policy callers cannot mistake missing data for "not charging".
     */
    @Synchronized
    fun updateChargingState(isCharging: Boolean, readingAvailable: Boolean = true): AutonomyDecision {
        environmentalSignals = environmentalSignals.copy(
            charging = isCharging,
            chargingReadingAvailable = readingAvailable
        )
        return calculateDecision()
    }

    /**
     * Records a measured transport outcome without storing network identifiers or destination data.
     * This updates advisory learning only; it never changes the active Tor route.
     */
    @Synchronized
    fun recordBridgeOutcome(
        transport: Transport,
        succeeded: Boolean,
        latencyMillis: Long,
        elapsedRealtimeMillis: Long = elapsedRealtimeClock()
    ): List<BridgeCandidate> {
        bridgeHealthLedger.record(
            transport = transport,
            succeeded = succeeded,
            latencyMillis = latencyMillis,
            elapsedRealtimeMillis = elapsedRealtimeMillis
        )
        journal.record(
            AutonomyEvent(
                type = if (succeeded) {
                    AutonomyEventType.BRIDGE_ATTEMPT_SUCCEEDED
                } else {
                    AutonomyEventType.BRIDGE_ATTEMPT_FAILED
                },
                source = AutonomyEventSource.VPN_SERVICE,
                elapsedRealtimeMillis = elapsedRealtimeMillis
            )
        )
        return policyEngine.rankBridges(bridgeHealthLedger.candidates())
    }

    /** Returns the best currently learned candidates; selection remains advisory. */
    @Synchronized
    fun rankedBridgeCandidates(): List<BridgeCandidate> =
        policyEngine.rankBridges(bridgeHealthLedger.candidates())

    /**
     * Accepts a report only when every required control is fresh and explicitly verified and the
     * current coordinator state is safe. Unknown platform checks are failures, never implicit passes.
     *
     * The caller is responsible for independently measuring the evidence fields. This method does
     * not perform external leak probes or change VPN routing.
     */
    @Synchronized
    fun submitLeakCheckEvidence(
        evidence: LeakVerificationEvidence,
        nowElapsedRealtimeMillis: Long = elapsedRealtimeClock()
    ): LeakVerificationResult {
        val failures = leakVerificationGate.evaluate(evidence, nowElapsedRealtimeMillis)
            .failures.toMutableList()
        if (!tunnelReady) failures += LeakVerificationFailure.TUNNEL_NOT_READY
        if (!environmentalSignals.networkAvailable) failures += LeakVerificationFailure.NETWORK_NOT_AVAILABLE
        if (environmentalSignals.dnsConfigurationConflict) {
            failures += LeakVerificationFailure.DNS_CONFIGURATION_CONFLICT
        }
        if (environmentalSignals.dnsHijackSuspected || environmentalSignals.tlsInterceptionSuspected) {
            failures += LeakVerificationFailure.HOSTILE_NETWORK_INDICATOR
        }
        if (environmentalSignals.captivePortalDetected) {
            failures += LeakVerificationFailure.CAPTIVE_PORTAL_ACTIVE
        }

        val result = LeakVerificationResult(failures.distinct())
        if (result.passed) {
            validatedLeakReportReady = true
            observe(
                AutonomyEventType.LEAK_CHECK_PASSED,
                source = AutonomyEventSource.LEAK_CHECKER,
                elapsedRealtimeMillis = nowElapsedRealtimeMillis
            )
        } else {
            validatedLeakReportReady = false
            observe(
                AutonomyEventType.LEAK_CHECK_FAILED,
                source = AutonomyEventSource.LEAK_CHECKER,
                elapsedRealtimeMillis = nowElapsedRealtimeMillis
            )
        }
        return result
    }

    /**
     * Applies fresh caller-supplied trust evidence to the policy snapshot.
     * Classification is advisory and never changes routes or service lifecycle.
     */
    @Synchronized
    fun updateNetworkTrust(
        evidence: NetworkTrustEvidence,
        nowElapsedRealtimeMillis: Long = elapsedRealtimeClock()
    ): AutonomyDecision {
        val classifiedTrust = trustClassifier.classify(evidence, nowElapsedRealtimeMillis)
        if (classifiedTrust == NetworkTrust.HOSTILE) {
            // Previously successful tunnel/leak checks do not remain authoritative after a hostile
            // network indicator. Record the classification without pretending a leak probe ran.
            tunnelReady = false
            leakChecksPassed = false
            validatedLeakReportReady = false
        }
        environmentalSignals = environmentalSignals.copy(trust = classifiedTrust)
        return calculateDecision()
    }

    /**
     * Evaluates caller-supplied circuit telemetry without opening a Tor control connection.
     * The result is advisory until a supported executor and no-direct-fallback tests exist.
     */
    @Synchronized
    fun assessCircuitRefresh(
        telemetry: CircuitTelemetry,
        nowElapsedRealtimeMillis: Long = elapsedRealtimeClock()
    ): CircuitRefreshDecision = circuitRefreshPlanner.assess(
        telemetry = telemetry,
        nowElapsedRealtimeMillis = nowElapsedRealtimeMillis
    )

    /**
     * Applies explicit cover-traffic opt-in, target ownership, battery, transport, and data-budget
     * limits. This method never generates packets or contacts destinations.
     */
    @Synchronized
    fun assessCoverTrafficPolicy(inputs: CoverTrafficPolicyInputs): CoverTrafficPolicyDecision =
        coverTrafficPolicyPlanner.assess(inputs)

    /**
     * Returns a bounded reconnect recommendation for a future service executor.
     * This method only plans; it never schedules Android work or modifies routing.
     */
    @Synchronized
    fun planRecovery(): RecoveryPlan = recoveryPlanner.planReconnect(
        networkAvailable = environmentalSignals.networkAvailable,
        tunnelReady = tunnelReady,
        leakChecksPassed = leakChecksPassed,
        recoveryRequired = consecutiveFailures > 0 || leakCheckFailed
    )

    /** Confirms that a previously planned attempt actually began, preventing duplicate work. */
    @Synchronized
    fun markRecoveryAttemptStarted(attempt: Int): Boolean =
        recoveryPlanner.markAttemptStarted(attempt)

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
