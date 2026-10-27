package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomyAssessmentPipelineTest {
    private val pipeline = AutonomyAssessmentPipeline()
    private val now = 100L

    @Test
    fun publicNetworkEvidenceProducesMaximumProtectionRecommendation() {
        val decision = pipeline.assess(
            signals = NetworkSignals(networkAvailable = true),
            evidence = NetworkTrustEvidence(
                observedAtElapsedRealtimeMillis = now,
                internetValidated = true,
                knownPublicNetwork = true
            ),
            nowElapsedRealtimeMillis = now
        )

        assertEquals(ProtectionProfile.MAXIMUM, decision.profile)
        assertFalse(decision.trafficReleaseEligible)
        assertTrue(decision.blockTrafficUntilTunnelVerified)
    }

    @Test
    fun interferenceEvidencePreventsTrafficReleaseEvenWhenBaseSignalsLookReady() {
        val decision = pipeline.assess(
            signals = NetworkSignals(
                networkAvailable = true,
                tunnelReady = true,
                leakChecksPassed = true
            ),
            evidence = NetworkTrustEvidence(
                observedAtElapsedRealtimeMillis = now,
                internetValidated = true,
                explicitlyTrustedByUser = true,
                dnsHijackSuspected = true
            ),
            nowElapsedRealtimeMillis = now
        )

        assertEquals(ProtectionProfile.MAXIMUM, decision.profile)
        assertFalse(decision.trafficReleaseEligible)
        assertTrue(decision.blockTrafficUntilTunnelVerified)
    }

    @Test
    fun trustedEvidenceDoesNotReplaceIndependentTunnelAndLeakCheckRequirements() {
        val decision = pipeline.assess(
            signals = NetworkSignals(networkAvailable = true),
            evidence = NetworkTrustEvidence(
                observedAtElapsedRealtimeMillis = now,
                internetValidated = true,
                explicitlyTrustedByUser = true
            ),
            nowElapsedRealtimeMillis = now
        )

        assertEquals(ProtectionProfile.BALANCED, decision.profile)
        assertFalse(decision.trafficReleaseEligible)
        assertTrue(decision.recommendedActions.contains(AutonomyAction.KEEP_TRAFFIC_BLOCKED))
    }

    @Test
    fun unavailableInternetCannotBeClassifiedAsTrustedByThisPipeline() {
        val decision = pipeline.assess(
            signals = NetworkSignals(networkAvailable = true),
            evidence = NetworkTrustEvidence(
                observedAtElapsedRealtimeMillis = now,
                internetValidated = false,
                explicitlyTrustedByUser = true
            ),
            nowElapsedRealtimeMillis = now
        )

        assertEquals(ProtectionProfile.HIGH, decision.profile)
        assertFalse(decision.trafficReleaseEligible)
    }

    @Test
    fun staleTrustedEvidenceFallsBackToUnknownTrust() {
        val decision = pipeline.assess(
            signals = NetworkSignals(networkAvailable = true),
            evidence = NetworkTrustEvidence(
                observedAtElapsedRealtimeMillis = 1L,
                internetValidated = true,
                explicitlyTrustedByUser = true
            ),
            nowElapsedRealtimeMillis =
                NetworkTrustClassifier.DEFAULT_MAX_EVIDENCE_AGE_MILLIS + 2L
        )

        assertEquals(ProtectionProfile.HIGH, decision.profile)
        assertFalse(decision.trafficReleaseEligible)
    }
}
