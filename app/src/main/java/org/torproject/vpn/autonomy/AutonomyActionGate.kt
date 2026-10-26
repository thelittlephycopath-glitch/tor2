package org.torproject.vpn.autonomy

enum class ActionSuppressionReason {
    AUTOMATION_DISABLED,
    OBSERVE_ONLY_MODE,
    FEATURE_NOT_ENABLED,
    SAFETY_VERIFICATION_REQUIRED,
    ACTION_NOT_IMPLEMENTED
}

data class AutonomyExecutionPlan(
    val approvedActions: List<AutonomyAction>,
    val suppressedActions: Map<AutonomyAction, ActionSuppressionReason>
)

/**
 * Pure allow-list for future autonomous command execution.
 *
 * This gate does not execute commands, modify VPN routing, or schedule work. A real dispatcher must
 * still serialize service operations and verify that its platform adapters actually enforce each
 * approved action. Unimplemented actions are deliberately rejected even if a config flag is set.
 */
class AutonomyActionGate {
    fun plan(
        config: AutonomyConfig,
        decision: AutonomyDecision,
        leakVerificationResult: LeakVerificationResult? = null
    ): AutonomyExecutionPlan {
        val approved = linkedSetOf<AutonomyAction>()
        val suppressed = linkedMapOf<AutonomyAction, ActionSuppressionReason>()

        decision.recommendedActions.distinct().forEach { action ->
            when (action) {
                // Fail-closed actions are always approved by policy. Their executor must only reduce
                // exposure; this plan does not itself enforce a traffic block.
                AutonomyAction.KEEP_TRAFFIC_BLOCKED,
                AutonomyAction.SUPPRESS_COVER_TRAFFIC -> approved += action

                AutonomyAction.ALLOW_CAPTIVE_PORTAL_ONLY,
                AutonomyAction.NOTIFY_USER -> {
                    suppressed[action] = ActionSuppressionReason.ACTION_NOT_IMPLEMENTED
                }

                AutonomyAction.RESTORE_VERIFIED_TUNNELED_TRAFFIC -> {
                    when {
                        !config.enabled ->
                            suppressed[action] = ActionSuppressionReason.AUTOMATION_DISABLED
                        config.mode == AutonomyMode.OBSERVE_ONLY ->
                            suppressed[action] = ActionSuppressionReason.OBSERVE_ONLY_MODE
                        !decision.trafficReleaseEligible ||
                            leakVerificationResult?.passed != true ->
                            suppressed[action] = ActionSuppressionReason.SAFETY_VERIFICATION_REQUIRED
                        else -> approved += action
                    }
                }

                else -> {
                    when {
                        !config.enabled ->
                            suppressed[action] = ActionSuppressionReason.AUTOMATION_DISABLED
                        config.mode == AutonomyMode.OBSERVE_ONLY ->
                            suppressed[action] = ActionSuppressionReason.OBSERVE_ONLY_MODE
                        !isFeatureEnabled(config, action) ->
                            suppressed[action] = ActionSuppressionReason.FEATURE_NOT_ENABLED
                        else -> approved += action
                    }
                }
            }
        }

        return AutonomyExecutionPlan(
            approvedActions = approved.toList(),
            suppressedActions = suppressed.toMap()
        )
    }

    private fun isFeatureEnabled(config: AutonomyConfig, action: AutonomyAction): Boolean =
        when (action) {
            AutonomyAction.RUN_LEAK_CHECKS -> true
            AutonomyAction.REQUEST_TUNNEL_RECONNECT -> config.automaticReconnect
            AutonomyAction.REQUEST_BRIDGE_FAILOVER -> config.automaticBridgeFailover
            AutonomyAction.REQUEST_CIRCUIT_REFRESH -> config.automaticCircuitRefresh
            AutonomyAction.KEEP_TRAFFIC_BLOCKED,
            AutonomyAction.SUPPRESS_COVER_TRAFFIC,
            AutonomyAction.NOTIFY_USER,
            AutonomyAction.ALLOW_CAPTIVE_PORTAL_ONLY,
            AutonomyAction.RESTORE_VERIFIED_TUNNELED_TRAFFIC -> false
        }
}
