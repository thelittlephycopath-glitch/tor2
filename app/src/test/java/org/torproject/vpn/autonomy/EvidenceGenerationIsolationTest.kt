package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceGenerationIsolationTest {
    @Test
    fun startingLeakCheckDoesNotChangeNetworkGeneration() {
        val coordinator = AutonomyCoordinator()
        val networkGeneration = coordinator.currentNetworkGeneration()

        coordinator.beginLeakVerification()

        assertEquals(networkGeneration, coordinator.currentNetworkGeneration())
    }

    @Test
    fun networkPathChangeAdvancesBothEvidenceGenerations() {
        val coordinator = AutonomyCoordinator()
        val verificationBefore = coordinator.currentVerificationGeneration()
        val networkBefore = coordinator.currentNetworkGeneration()

        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = false
        )

        assertTrue(coordinator.currentVerificationGeneration() > verificationBefore)
        assertTrue(coordinator.currentNetworkGeneration() > networkBefore)
    }
}
