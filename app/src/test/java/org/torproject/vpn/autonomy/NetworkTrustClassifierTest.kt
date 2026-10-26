package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkTrustClassifierTest {
    private val classifier = NetworkTrustClassifier()

    @Test
    fun requiresExplicitTrustAndValidatedInternetForTrustedClassification() {
        assertEquals(
            NetworkTrust.TRUSTED,
            classifier.classify(
                NetworkTrustEvidence(
                    internetValidated = true,
                    explicitlyTrustedByUser = true
                )
            )
        )
        assertEquals(
            NetworkTrust.UNKNOWN,
            classifier.classify(
                NetworkTrustEvidence(
                    internetValidated = true,
                    explicitlyTrustedByUser = false
                )
            )
        )
        assertEquals(
            NetworkTrust.UNKNOWN,
            classifier.classify(
                NetworkTrustEvidence(
                    internetValidated = false,
                    explicitlyTrustedByUser = true
                )
            )
        )
    }

    @Test
    fun knownPublicNetworkUsesStricterClassificationEvenWhenUserTrustsIt() {
        assertEquals(
            NetworkTrust.PUBLIC,
            classifier.classify(
                NetworkTrustEvidence(
                    internetValidated = true,
                    explicitlyTrustedByUser = true,
                    knownPublicNetwork = true
                )
            )
        )
    }

    @Test
    fun captivePortalDoesNotQualifyAsTrustedOrPublicEvidence() {
        assertEquals(
            NetworkTrust.UNKNOWN,
            classifier.classify(
                NetworkTrustEvidence(
                    internetValidated = true,
                    explicitlyTrustedByUser = true,
                    captivePortalDetected = true
                )
            )
        )
    }

    @Test
    fun securityInterferenceIndicatorsTakePriority() {
        assertEquals(
            NetworkTrust.HOSTILE,
            classifier.classify(
                NetworkTrustEvidence(
                    internetValidated = true,
                    explicitlyTrustedByUser = true,
                    dnsHijackSuspected = true
                )
            )
        )
        assertEquals(
            NetworkTrust.HOSTILE,
            classifier.classify(
                NetworkTrustEvidence(
                    internetValidated = true,
                    tlsInterceptionSuspected = true,
                    captivePortalDetected = true
                )
            )
        )
    }
}
