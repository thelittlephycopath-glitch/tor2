package org.torproject.vpn.autonomy

/**
 * Pure, bounded reconnect planner for a future reviewed Android recovery executor.
 *
 * This class never schedules work or starts/stops the VPN. A caller must enforce the returned
 * delay, single active service operation, cancellation, and fail-closed traffic policy.
 */
data class RecoveryPlan(
    val shouldScheduleReconnect: Boolean,
    val attempt: Int? = null,
    val delayMillis: Long? = null,
    val keepTrafficBlocked: Boolean = true,
    val reason: String
) {
    init {
        require(attempt == null || attempt > 0) { "Attempt must be positive" }
        require(delayMillis == null || delayMillis > 0L) { "Delay must be positive" }
        require(shouldScheduleReconnect == (attempt != null && delayMillis != null)) {
            "A scheduled reconnect requires both an attempt and a delay"
        }
    }
}

/**
 * Coordinates bounded recovery recommendations without owning service lifecycle or traffic routing.
 *
 * A retry is recommended only after the caller reports an actual recovery trigger. A tunnel that is
 * bootstrapping, or ready but awaiting independent leak verification, must not cause reconnect loops.
 * Attempt count advances when the caller confirms the scheduled attempt actually started.
 */
class AutonomyRecoveryPlanner(
    private val backoffPolicy: RecoveryBackoffPolicy = RecoveryBackoffPolicy()
) {
    private var attemptsStarted = 0
    private var pendingAttempt: Int? = null

    @Synchronized
    fun planReconnect(
        networkAvailable: Boolean,
        tunnelReady: Boolean,
        leakChecksPassed: Boolean,
        recoveryRequired: Boolean
    ): RecoveryPlan {
        if (tunnelReady && leakChecksPassed) {
            reset()
            return RecoveryPlan(
                shouldScheduleReconnect = false,
                keepTrafficBlocked = false,
                reason = "Tunnel and independent leak checks are verified"
            )
        }

        if (!networkAvailable) {
            // A scheduled attempt that did not start is discarded without consuming a retry.
            pendingAttempt = null
            return RecoveryPlan(
                shouldScheduleReconnect = false,
                keepTrafficBlocked = true,
                reason = "Network unavailable; remain blocked"
            )
        }

        if (!recoveryRequired) {
            return RecoveryPlan(
                shouldScheduleReconnect = false,
                keepTrafficBlocked = true,
                reason = if (tunnelReady) {
                    "Tunnel is ready; await independent leak verification"
                } else {
                    "No recovery trigger; do not reconnect during normal startup"
                }
            )
        }

        pendingAttempt?.let { attempt ->
            return RecoveryPlan(
                shouldScheduleReconnect = false,
                attempt = attempt,
                keepTrafficBlocked = true,
                reason = "Reconnect attempt is already pending"
            )
        }

        val attempt = attemptsStarted + 1
        val delay = backoffPolicy.delayBeforeAttempt(attempt) ?: return RecoveryPlan(
            shouldScheduleReconnect = false,
            keepTrafficBlocked = true,
            reason = "Reconnect retry budget exhausted"
        )

        pendingAttempt = attempt
        return RecoveryPlan(
            shouldScheduleReconnect = true,
            attempt = attempt,
            delayMillis = delay,
            keepTrafficBlocked = true,
            reason = "Recovery trigger observed; schedule bounded reconnect"
        )
    }

    /** Returns true exactly once for the currently pending attempt. */
    @Synchronized
    fun markAttemptStarted(attempt: Int): Boolean {
        if (pendingAttempt != attempt) return false
        pendingAttempt = null
        attemptsStarted = attempt
        return true
    }

    /** Invalidates pending work and forgets old-network failures. */
    @Synchronized
    fun onNetworkChanged() {
        reset()
    }

    @Synchronized
    fun resetAfterVerifiedTunnel() {
        reset()
    }

    @Synchronized
    fun attemptsStarted(): Int = attemptsStarted

    @Synchronized
    private fun reset() {
        attemptsStarted = 0
        pendingAttempt = null
    }
}
