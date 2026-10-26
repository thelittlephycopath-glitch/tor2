package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverTrafficPolicyPlannerTest {
    private val planner = CoverTrafficPolicyPlanner()

    private fun input(
        enabled: Boolean = true,
        ownedTarget: Boolean = true,
        threat: CoverTrafficThreatLevel = CoverTrafficThreatLevel.HIGH,
        transport: CoverTrafficTransport = CoverTrafficTransport.WIFI,
        battery: Int? = 80,
        charging: Boolean = false,
        wifiOnly: Boolean = true,
        used: Long = 0L,
        budget: Long = 300L
    ) = CoverTrafficPolicyInputs(
        enabled, ownedTarget, threat, transport, battery, charging, wifiOnly, used, budget
    )

    @Test
    fun requiresExplicitEnableAndUserControlledTarget() {
        assertEquals(CoverTrafficDecisionReason.USER_DISABLED,
            planner.assess(input(enabled = false)).reason)
        val external = planner.assess(input(ownedTarget = false))
        assertFalse(external.enabled)
        assertEquals(CoverTrafficDecisionReason.TARGET_NOT_USER_CONTROLLED, external.reason)
        assertTrue(external.onlyUserControlledTargets)
    }

    @Test
    fun onlyHighRiskOrCriticalScenariosMayUseCoverTraffic() {
        assertEquals(CoverTrafficDecisionReason.RISK_TOO_LOW,
            planner.assess(input(threat = CoverTrafficThreatLevel.LOW)).reason)
        assertEquals(CoverTrafficDecisionReason.RISK_TOO_LOW,
            planner.assess(input(threat = CoverTrafficThreatLevel.MEDIUM)).reason)
    }

    @Test
    fun healthyWifiUsesBoundedStandardBudget() {
        val decision = planner.assess(input(used = 295L, budget = 300L))
        assertTrue(decision.enabled)
        assertEquals(CoverTrafficRate.STANDARD, decision.rate)
        assertEquals(5L, decision.remainingBudgetMb)
        assertEquals(5L, decision.maximumSessionBudgetMb)
    }

    @Test
    fun intermediateBatteryUsesSmallWifiBudget() {
        val decision = planner.assess(input(battery = 25))
        assertEquals(CoverTrafficRate.LOW, decision.rate)
        assertEquals(CoverTrafficDecisionReason.LOW_WIFI_BUDGET, decision.reason)
        assertEquals(3L, decision.maximumSessionBudgetMb)
    }

    @Test
    fun lowOrUnknownBatteryFailsClosedUnlessCharging() {
        assertEquals(CoverTrafficDecisionReason.LOW_BATTERY,
            planner.assess(input(battery = 10)).reason)
        assertEquals(CoverTrafficDecisionReason.BATTERY_UNKNOWN,
            planner.assess(input(battery = null)).reason)
        assertEquals(CoverTrafficRate.STANDARD,
            planner.assess(input(battery = 10, charging = true)).rate)
    }

    @Test
    fun cellularUseRequiresExplicitPolicyCriticalRiskAndPowerBudget() {
        assertEquals(CoverTrafficDecisionReason.WIFI_ONLY_POLICY,
            planner.assess(input(transport = CoverTrafficTransport.CELLULAR)).reason)
        val decision = planner.assess(input(
            threat = CoverTrafficThreatLevel.CRITICAL,
            transport = CoverTrafficTransport.CELLULAR,
            wifiOnly = false,
            battery = 75
        ))
        assertEquals(CoverTrafficRate.LOW, decision.rate)
        assertEquals(CoverTrafficDecisionReason.CRITICAL_CELLULAR_BUDGET, decision.reason)
        assertEquals(2L, decision.maximumSessionBudgetMb)
    }

    @Test
    fun unsupportedNetworkAndExhaustedBudgetStayOff() {
        assertEquals(CoverTrafficDecisionReason.NETWORK_UNSUPPORTED,
            planner.assess(input(transport = CoverTrafficTransport.UNKNOWN)).reason)
        assertEquals(CoverTrafficDecisionReason.BUDGET_EXHAUSTED,
            planner.assess(input(used = 300L, budget = 300L)).reason)
    }

    @Test
    fun invalidInputsAndThresholdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CoverTrafficPolicyInputs(true, true, CoverTrafficThreatLevel.HIGH,
                CoverTrafficTransport.WIFI, 101, false, true, 0L, 10L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CoverTrafficPolicyInputs(true, true, CoverTrafficThreatLevel.HIGH,
                CoverTrafficTransport.WIFI, 50, false, true, -1L, 10L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CoverTrafficPolicyPlanner(lowWifiSessionCapMb = 0L)
        }
    }
}
