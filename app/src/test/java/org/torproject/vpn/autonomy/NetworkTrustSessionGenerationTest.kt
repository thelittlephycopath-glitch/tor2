package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkTrustSessionGenerationTest {
    @Test
    fun freshTrustEvidenceFromPreviousNetworkGenerationIsRejected() {
        val coordinator = AutonomyCoordinator(elapsedRealtimeClock = { 10L })
        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = false
        )
        val previousGeneration = coordinator.currentVerificationGeneration()
        val previousEvidence = NetworkTrustEvidence(
            observedAtElapsedRealtimeMillis = 10L,
            internetValidated = true,
            explicitlyTrustedByUser = true,
            sessionGeneration = previousGeneration
        )

        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = true
        )
        coordinator.updateNetworkTrust(
            evidence = previousEvidence,
            nowElapsedRealtimeMillis = 11L
        )

        assertEquals(NetworkTrust.UNKNOWN, coordinator.currentSignals().trust)
    }

    @Test
    fun trustEvidenceWithoutSessionTokenRemainsCompatibleForNow() {
        val coordinator = AutonomyCoordinator(elapsedRealtimeClock = { 10L })
        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = false
        )
        coordinator.updateNetworkTrust(
            evidence = NetworkTrustEvidence(
                observedAtElapsedRealtimeMillis = 10L,
                internetValidated = true,
                explicitlyTrustedByUser = true
            ),
            nowElapsedRealtimeMillis = 10L
        )

        assertEquals(NetworkTrust.TRUSTED, coordinator.currentSignals().trust)
    }
}
