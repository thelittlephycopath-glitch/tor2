package org.torproject.vpn.autonomy

enum class CoverTrafficThreatLevel { LOW, MEDIUM, HIGH, CRITICAL }
enum class CoverTrafficTransport { WIFI, CELLULAR, ETHERNET, UNKNOWN }
enum class CoverTrafficRate { OFF, LOW, STANDARD }

enum class CoverTrafficDecisionReason {
    USER_DISABLED,
    TARGET_NOT_USER_CONTROLLED,
    NETWORK_UNSUPPORTED,
    WIFI_ONLY_POLICY,
    BATTERY_UNKNOWN,
    LOW_BATTERY,
    RISK_TOO_LOW,
    CELLULAR_REQUIRES_CRITICAL_RISK,
    BUDGET_EXHAUSTED,
    STANDARD_WIFI_BUDGET,
    LOW_WIFI_BUDGET,
    CRITICAL_CELLULAR_BUDGET
}

data class CoverTrafficPolicyInputs(
    val enabled: Boolean,
    val targetIsUserControlled: Boolean,
    val threatLevel: CoverTrafficThreatLevel,
    val transport: CoverTrafficTransport,
    val batteryPercent: Int?,
    val charging: Boolean,
    val wifiOnly: Boolean,
    val coverTrafficUsedTodayMb: Long,
    val dailyCoverTrafficBudgetMb: Long
) {
    init {
        require(batteryPercent == null || batteryPercent in 0..100) {
            "Battery percentage must be 0..100 or unknown"
        }
        require(coverTrafficUsedTodayMb >= 0L) { "Used cover-traffic budget cannot be negative" }
        require(dailyCoverTrafficBudgetMb >= 0L) { "Daily cover-traffic budget cannot be negative" }
    }
}

data class CoverTrafficPolicyDecision(
    val rate: CoverTrafficRate,
    val reason: CoverTrafficDecisionReason,
    val remainingBudgetMb: Long,
    val maximumSessionBudgetMb: Long,
    val onlyUserControlledTargets: Boolean = true
) {
    val enabled: Boolean get() = rate != CoverTrafficRate.OFF
}

/**
 * Conservative cover-traffic budget decision. It never emits packets, selects destinations, or
 * enables itself. Traffic can only be recommended when an upstream caller supplies explicit opt-in
 * and confirms a user-controlled target. Actual generation and routing remain a separate, reviewed
 * integration and must not send unsolicited noise to third parties.
 */
class CoverTrafficPolicyPlanner(
    private val standardWifiSessionCapMb: Long = DEFAULT_STANDARD_WIFI_SESSION_CAP_MB,
    private val lowWifiSessionCapMb: Long = DEFAULT_LOW_WIFI_SESSION_CAP_MB,
    private val criticalCellularSessionCapMb: Long = DEFAULT_CRITICAL_CELLULAR_SESSION_CAP_MB,
    private val standardBatteryThresholdPercent: Int = DEFAULT_STANDARD_BATTERY_THRESHOLD_PERCENT,
    private val lowBatteryThresholdPercent: Int = DEFAULT_LOW_BATTERY_THRESHOLD_PERCENT,
    private val criticalCellularBatteryThresholdPercent: Int =
        DEFAULT_CRITICAL_CELLULAR_BATTERY_THRESHOLD_PERCENT
) {
    init {
        require(standardWifiSessionCapMb > 0L) { "Standard Wi-Fi cap must be positive" }
        require(lowWifiSessionCapMb > 0L) { "Low Wi-Fi cap must be positive" }
        require(criticalCellularSessionCapMb > 0L) { "Cellular cap must be positive" }
        require(lowBatteryThresholdPercent in 0..100)
        require(standardBatteryThresholdPercent in lowBatteryThresholdPercent..100)
        require(criticalCellularBatteryThresholdPercent in lowBatteryThresholdPercent..100)
    }

    fun assess(inputs: CoverTrafficPolicyInputs): CoverTrafficPolicyDecision {
        val remaining = (inputs.dailyCoverTrafficBudgetMb - inputs.coverTrafficUsedTodayMb)
            .coerceAtLeast(0L)
        fun off(reason: CoverTrafficDecisionReason) = CoverTrafficPolicyDecision(
            rate = CoverTrafficRate.OFF,
            reason = reason,
            remainingBudgetMb = remaining,
            maximumSessionBudgetMb = 0L
        )

        if (!inputs.enabled) return off(CoverTrafficDecisionReason.USER_DISABLED)
        if (!inputs.targetIsUserControlled) {
            return off(CoverTrafficDecisionReason.TARGET_NOT_USER_CONTROLLED)
        }
        if (inputs.transport == CoverTrafficTransport.UNKNOWN ||
            inputs.transport == CoverTrafficTransport.ETHERNET
        ) {
            return off(CoverTrafficDecisionReason.NETWORK_UNSUPPORTED)
        }
        if (inputs.wifiOnly && inputs.transport != CoverTrafficTransport.WIFI) {
            return off(CoverTrafficDecisionReason.WIFI_ONLY_POLICY)
        }
        if (remaining == 0L) return off(CoverTrafficDecisionReason.BUDGET_EXHAUSTED)
        val battery = inputs.batteryPercent
            ?: return off(CoverTrafficDecisionReason.BATTERY_UNKNOWN)
        if (!inputs.charging && battery < lowBatteryThresholdPercent) {
            return off(CoverTrafficDecisionReason.LOW_BATTERY)
        }
        if (inputs.threatLevel == CoverTrafficThreatLevel.LOW ||
            inputs.threatLevel == CoverTrafficThreatLevel.MEDIUM
        ) {
            return off(CoverTrafficDecisionReason.RISK_TOO_LOW)
        }

        if (inputs.transport == CoverTrafficTransport.WIFI) {
            val standard = inputs.charging || battery >= standardBatteryThresholdPercent
            val cap = if (standard) standardWifiSessionCapMb else lowWifiSessionCapMb
            return CoverTrafficPolicyDecision(
                rate = if (standard) CoverTrafficRate.STANDARD else CoverTrafficRate.LOW,
                reason = if (standard) {
                    CoverTrafficDecisionReason.STANDARD_WIFI_BUDGET
                } else {
                    CoverTrafficDecisionReason.LOW_WIFI_BUDGET
                },
                remainingBudgetMb = remaining,
                maximumSessionBudgetMb = minOf(cap, remaining)
            )
        }

        // Cellular use is only eligible when the caller explicitly disables Wi-Fi-only mode,
        // threat is critical, and the power budget is healthy. It remains very tightly capped.
        if (inputs.threatLevel != CoverTrafficThreatLevel.CRITICAL) {
            return off(CoverTrafficDecisionReason.CELLULAR_REQUIRES_CRITICAL_RISK)
        }
        if (!inputs.charging && battery < criticalCellularBatteryThresholdPercent) {
            return off(CoverTrafficDecisionReason.LOW_BATTERY)
        }
        return CoverTrafficPolicyDecision(
            rate = CoverTrafficRate.LOW,
            reason = CoverTrafficDecisionReason.CRITICAL_CELLULAR_BUDGET,
            remainingBudgetMb = remaining,
            maximumSessionBudgetMb = minOf(criticalCellularSessionCapMb, remaining)
        )
    }

    companion object {
        const val DEFAULT_STANDARD_WIFI_SESSION_CAP_MB = 20L
        const val DEFAULT_LOW_WIFI_SESSION_CAP_MB = 3L
        const val DEFAULT_CRITICAL_CELLULAR_SESSION_CAP_MB = 2L
        const val DEFAULT_LOW_BATTERY_THRESHOLD_PERCENT = 20
        const val DEFAULT_STANDARD_BATTERY_THRESHOLD_PERCENT = 40
        const val DEFAULT_CRITICAL_CELLULAR_BATTERY_THRESHOLD_PERCENT = 50
    }
}
