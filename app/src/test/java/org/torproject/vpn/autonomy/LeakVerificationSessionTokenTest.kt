package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeakVerificationSessionTokenTest {
    @Test
    fun beginVerificationInvalidatesOlderTokenAndReturnsCurrentGeneration() {
        val coordinator = AutonomyCoordinator()
        val previous = coordinator.currentVerificationGeneration()

        val token = coordinator.beginLeakVerification()

        assertTrue(token > previous)
        assertEquals(token, coordinator.currentVerificationGeneration())
    }

    @Test
    fun networkChangeDuringVerificationMakesCapturedTokenStale() {
        val coordinator = AutonomyCoordinator()
        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = false
        )
        val token = coordinator.beginLeakVerification()

        coordinator.updateNetworkEnvironment(
            networkAvailable = true,
            captivePortalDetected = false,
            mobileData = true
        )

        assertTrue(coordinator.currentVerificationGeneration() > token)
    }
}
