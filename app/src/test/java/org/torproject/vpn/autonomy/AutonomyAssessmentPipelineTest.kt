package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomyAssessmentPipelineTest {
    private val pipeline = AutonomyAssessmentPipeline()

    @Test
    fun publicNetworkEvidenceProducesMaximumProtectionRecommendation() {
        val decision = pipeline.assess(
            signals = NetworkSignals(networkAvailable = true),
            evidence = NetworkTrustEvidence(
                internetValidated = true,
                knownPublicNetwork = true
            )
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
                internetValidated = true,
                explicitlyTrustedByUser = true,
                dnsHijackSuspected = true
            )
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
                internetValidated = true,
                explicitlyTrustedByUser = true
            )
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
                internetValidated = false,
                explicitlyTrustedByUser = true
            )
        )

        assertEquals(ProtectionProfile.HIGH, decision.profile)
        assertFalse(decision.trafficReleaseEligible)
    }
}
