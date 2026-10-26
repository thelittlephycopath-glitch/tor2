package org.torproject.vpn.autonomy

/**
 * Snapshot of already-measured circuit health. This planner does not query Tor or collect telemetry.
 * Timestamps use monotonic elapsed-realtime milliseconds, not wall-clock time.
 */
data class CircuitTelemetry(
    val observedAtElapsedRealtimeMillis: Long,
    val circuitAgeMillis: Long,
    val latencyMillis: Long?,
    val recentStreamFailures: Int,
    val lastRefreshAtElapsedRealtimeMillis: Long? = null,
    val networkChanged: Boolean = false,
    val circuitFailed: Boolean = false,
    val highRiskSessionStarted: Boolean = false
) {
    init {
        require(observedAtElapsedRealtimeMillis >= 0L) { "Observation timestamp must not be negative" }
        require(circuitAgeMillis >= 0L) { "Circuit age cannot be negative" }
        require(latencyMillis == null || latencyMillis >= 0L) { "Latency cannot be negative" }
        require(recentStreamFailures >= 0) { "Stream failure count cannot be negative" }
        require(lastRefreshAtElapsedRealtimeMillis == null || lastRefreshAtElapsedRealtimeMillis >= 0L) {
            "Last-refresh timestamp must not be negative"
        }
    }
}

enum class CircuitRefreshReason {
    HEALTHY,
    STALE_OBSERVATION,
    FUTURE_OBSERVATION,
    FUTURE_REFRESH_TIMESTAMP,
    NETWORK_CHANGED,
    CIRCUIT_FAILED,
    HIGH_RISK_SESSION_STARTED,
    CIRCUIT_TOO_OLD,
    LATENCY_HIGH,
    REPEATED_STREAM_FAILURES,
    COOLDOWN
}

data class CircuitRefreshDecision(
    val shouldRefresh: Boolean,
    val reason: CircuitRefreshReason,
    val estimatedCircuitAgeMillis: Long? = null
)

/**
 * Makes an advisory circuit-refresh recommendation from caller-supplied telemetry.
 *
 * It never opens a Tor control connection or refreshes the active circuit. A real executor must use
 * supported Tor/Onionmasq APIs, serialize with VPN lifecycle operations, and test that refresh does
 * not create direct-network fallback. Stale/future evidence produces no refresh recommendation.
 */
class TorCircuitRefreshPlanner(
    private val maximumCircuitAgeMillis: Long = DEFAULT_MAXIMUM_CIRCUIT_AGE_MILLIS,
    private val maximumLatencyMillis: Long = DEFAULT_MAXIMUM_LATENCY_MILLIS,
    private val repeatedFailureThreshold: Int = DEFAULT_REPEATED_FAILURE_THRESHOLD,
    private val refreshCooldownMillis: Long = DEFAULT_REFRESH_COOLDOWN_MILLIS,
    private val maximumObservationAgeMillis: Long = DEFAULT_MAXIMUM_OBSERVATION_AGE_MILLIS
) {
    init {
        require(maximumCircuitAgeMillis > 0L) { "Maximum circuit age must be positive" }
        require(maximumLatencyMillis > 0L) { "Maximum latency must be positive" }
        require(repeatedFailureThreshold > 0) { "Failure threshold must be positive" }
        require(refreshCooldownMillis >= 0L) { "Refresh cooldown cannot be negative" }
        require(maximumObservationAgeMillis > 0L) { "Maximum observation age must be positive" }
    }

    fun assess(
        telemetry: CircuitTelemetry,
        nowElapsedRealtimeMillis: Long
    ): CircuitRefreshDecision {
        require(nowElapsedRealtimeMillis >= 0L) { "Current elapsed realtime must not be negative" }

        val observedAt = telemetry.observedAtElapsedRealtimeMillis
        if (observedAt > nowElapsedRealtimeMillis) {
            return CircuitRefreshDecision(false, CircuitRefreshReason.FUTURE_OBSERVATION)
        }
        val observationAge = nowElapsedRealtimeMillis - observedAt
        if (observationAge > maximumObservationAgeMillis) {
            return CircuitRefreshDecision(false, CircuitRefreshReason.STALE_OBSERVATION)
        }

        val lastRefresh = telemetry.lastRefreshAtElapsedRealtimeMillis
        if (lastRefresh != null && lastRefresh > nowElapsedRealtimeMillis) {
            return CircuitRefreshDecision(false, CircuitRefreshReason.FUTURE_REFRESH_TIMESTAMP)
        }

        val estimatedAge = saturatingAdd(telemetry.circuitAgeMillis, observationAge)

        // These indicate a broken/changed path and are allowed to bypass cooldown.
        if (telemetry.networkChanged) {
            return CircuitRefreshDecision(true, CircuitRefreshReason.NETWORK_CHANGED, estimatedAge)
        }
        if (telemetry.circuitFailed) {
            return CircuitRefreshDecision(true, CircuitRefreshReason.CIRCUIT_FAILED, estimatedAge)
        }

        val trigger = when {
            telemetry.highRiskSessionStarted ->
                CircuitRefreshReason.HIGH_RISK_SESSION_STARTED
            estimatedAge >= maximumCircuitAgeMillis ->
                CircuitRefreshReason.CIRCUIT_TOO_OLD
            telemetry.latencyMillis != null && telemetry.latencyMillis >= maximumLatencyMillis ->
                CircuitRefreshReason.LATENCY_HIGH
            telemetry.recentStreamFailures >= repeatedFailureThreshold ->
                CircuitRefreshReason.REPEATED_STREAM_FAILURES
            else -> CircuitRefreshReason.HEALTHY
        }
        if (trigger == CircuitRefreshReason.HEALTHY) {
            return CircuitRefreshDecision(false, trigger, estimatedAge)
        }

        if (lastRefresh != null && nowElapsedRealtimeMillis - lastRefresh < refreshCooldownMillis) {
            return CircuitRefreshDecision(false, CircuitRefreshReason.COOLDOWN, estimatedAge)
        }
        return CircuitRefreshDecision(true, trigger, estimatedAge)
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

    companion object {
        const val DEFAULT_MAXIMUM_CIRCUIT_AGE_MILLIS = 10 * 60 * 1_000L
        const val DEFAULT_MAXIMUM_LATENCY_MILLIS = 5_000L
        const val DEFAULT_REPEATED_FAILURE_THRESHOLD = 3
        const val DEFAULT_REFRESH_COOLDOWN_MILLIS = 60_000L
        const val DEFAULT_MAXIMUM_OBSERVATION_AGE_MILLIS = 10_000L
    }
}
