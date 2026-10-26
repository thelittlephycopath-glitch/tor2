package org.torproject.vpn.autonomy

import java.util.ArrayDeque

/**
 * Session-scoped bridge/transport outcome learning.
 *
 * Stores only transport-level outcomes and elapsed durations: no SSIDs, IP addresses, hostnames,
 * app identifiers, or destination metadata. The ledger is intentionally in-memory and bounded.
 * It informs selection; it never probes a transport or changes the active Tor route.
 */
class BridgeHealthLedger(
    private val maxSamplesPerTransport: Int = 48,
    private val blockedFailureStreak: Int = 4,
    private val defaultLatencyMillis: Long = 2_500L
) {
    private data class Sample(
        val succeeded: Boolean,
        val latencyMillis: Long,
        val elapsedRealtimeMillis: Long
    )

    data class Snapshot(
        val transport: Transport,
        val sampleCount: Int,
        val successRate: Double,
        val meanLatencyMillis: Long,
        val consecutiveFailures: Int,
        val knownBlocked: Boolean,
        val lastObservedElapsedRealtimeMillis: Long?
    )

    private val samples = Transport.entries.associateWith { ArrayDeque<Sample>() }

    init {
        require(maxSamplesPerTransport in 1..512)
        require(blockedFailureStreak >= 1)
        require(defaultLatencyMillis > 0)
    }

    @Synchronized
    fun record(
        transport: Transport,
        succeeded: Boolean,
        latencyMillis: Long,
        elapsedRealtimeMillis: Long
    ) {
        require(latencyMillis >= 0L) { "Latency cannot be negative" }
        require(elapsedRealtimeMillis >= 0L) { "Elapsed realtime cannot be negative" }
        val queue = samples.getValue(transport)
        if (queue.isNotEmpty()) {
            require(elapsedRealtimeMillis >= queue.last().elapsedRealtimeMillis) {
                "Samples must be recorded in monotonic time order"
            }
        }
        queue.addLast(Sample(succeeded, latencyMillis, elapsedRealtimeMillis))
        while (queue.size > maxSamplesPerTransport) queue.removeFirst()
    }

    @Synchronized
    fun snapshot(transport: Transport): Snapshot {
        val queue = samples.getValue(transport)
        if (queue.isEmpty()) {
            return Snapshot(
                transport = transport,
                sampleCount = 0,
                successRate = 0.5,
                meanLatencyMillis = defaultLatencyMillis,
                consecutiveFailures = 0,
                knownBlocked = false,
                lastObservedElapsedRealtimeMillis = null
            )
        }

        // Beta(1,1) smoothing avoids treating one success/failure as absolute certainty.
        val successes = queue.count { it.succeeded }
        val rate = (successes + 1.0) / (queue.size + 2.0)
        val measuredLatency = queue.filter { it.succeeded }.map { it.latencyMillis }
        val meanLatency = if (measuredLatency.isEmpty()) {
            defaultLatencyMillis
        } else {
            measuredLatency.average().toLong().coerceAtLeast(1L)
        }
        var failureStreak = 0
        for (sample in queue.reversed()) {
            if (sample.succeeded) break
            failureStreak++
        }
        return Snapshot(
            transport = transport,
            sampleCount = queue.size,
            successRate = rate,
            meanLatencyMillis = meanLatency,
            consecutiveFailures = failureStreak,
            knownBlocked = failureStreak >= blockedFailureStreak,
            lastObservedElapsedRealtimeMillis = queue.last().elapsedRealtimeMillis
        )
    }

    @Synchronized
    fun snapshots(): List<Snapshot> = Transport.entries.map(::snapshot)

    /**
     * Converts learned health into policy candidates. Unobserved transports receive a neutral prior
     * and default latency, not a fabricated success. Known-blocked transports remain excluded by the
     * policy engine. Callers must still validate that the selected transport is actually available.
     */
    @Synchronized
    fun candidates(): List<BridgeCandidate> = snapshots().map { health ->
        BridgeCandidate(
            transport = health.transport,
            recentSuccessRate = health.successRate,
            latencyMillis = health.meanLatencyMillis,
            recentFailures = health.consecutiveFailures,
            knownBlocked = health.knownBlocked
        )
    }

    @Synchronized
    fun clear() {
        samples.values.forEach { it.clear() }
    }
}
