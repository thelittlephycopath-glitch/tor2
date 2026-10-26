package org.torproject.vpn.autonomy

/**
 * Additive assessment pipeline joining explicit network evidence to the existing pure policy engine.
 *
 * The caller supplies the current elapsed-realtime timestamp so stale observations cannot be
 * accidentally reused as current. This is deliberately not wired into the Android service yet:
 * it remains a testable integration seam with no lifecycle, routing, or UI behavior changes.
 */
class AutonomyAssessmentPipeline(
    private val trustClassifier: NetworkTrustClassifier = NetworkTrustClassifier(),
    private val policyEngine: AutonomousPolicyEngine = AutonomousPolicyEngine()
) {
    fun assess(
        signals: NetworkSignals,
        evidence: NetworkTrustEvidence,
        nowElapsedRealtimeMillis: Long
    ): AutonomyDecision {
        val classifiedTrust = trustClassifier.classify(evidence, nowElapsedRealtimeMillis)
        return policyEngine.evaluate(signals.copy(trust = classifiedTrust))
    }
}
