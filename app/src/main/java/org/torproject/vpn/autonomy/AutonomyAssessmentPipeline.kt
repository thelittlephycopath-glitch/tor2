package org.torproject.vpn.autonomy

/**
 * Additive assessment pipeline joining explicit network evidence to the existing pure policy engine.
 *
 * This is deliberately not wired into the Android service yet: it provides a testable integration
 * seam without changing current VPN lifecycle, routing, or UI behavior.
 */
class AutonomyAssessmentPipeline(
    private val trustClassifier: NetworkTrustClassifier = NetworkTrustClassifier(),
    private val policyEngine: AutonomousPolicyEngine = AutonomousPolicyEngine()
) {
    fun assess(
        signals: NetworkSignals,
        evidence: NetworkTrustEvidence
    ): AutonomyDecision {
        val classifiedTrust = trustClassifier.classify(evidence)
        return policyEngine.evaluate(signals.copy(trust = classifiedTrust))
    }
}
