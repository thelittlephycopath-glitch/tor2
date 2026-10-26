package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkTrustClassifierTest {
    private val classifier = NetworkTrustClassifier()
    private val now = 1_000L

    @Test
    fun requiresExplicitTrustAndValidatedInternetForTrustedClassification() {
        assertEquals(
            NetworkTrust.TRUSTED,
            classify(evidence(internetValidated = true, explicitlyTrustedByUser = true))
        )
        assertEquals(
            NetworkTrust.UNKNOWN,
            classify(evidence(internetValidated = true, explicitlyTrustedByUser = false))
        )
        assertEquals(
            NetworkTrust.UNKNOWN,
            classify(evidence(internetValidated = false, explicitlyTrustedByUser = true))
        )
    }

    @Test
    fun knownPublicNetworkUsesStricterClassificationEvenWhenUserTrustsIt() {
        assertEquals(
            NetworkTrust.PUBLIC,
            classify(
                evidence(
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
            classify(
                evidence(
                    internetValidated = true,
                    explicitlyTrustedByUser = true,
                    captivePortalDetected = true
                )
            )
        )
    }

    @Test
    fun freshSecurityInterferenceIndicatorsTakePriority() {
        assertEquals(
            NetworkTrust.HOSTILE,
            classify(
                evidence(
                    internetValidated = true,
                    explicitlyTrustedByUser = true,
                    dnsHijackSuspected = true
                )
            )
        )
        assertEquals(
            NetworkTrust.HOSTILE,
            classify(
                evidence(
                    internetValidated = true,
                    tlsInterceptionSuspected = true,
                    captivePortalDetected = true
                )
            )
        )
    }

    @Test
    fun staleEvidenceIsUnknownRatherThanTrustedOrHostile() {
        val evidence = evidence(
            internetValidated = true,
            explicitlyTrustedByUser = true,
            dnsHijackSuspected = true
        )

        assertEquals(
            NetworkTrust.UNKNOWN,
            classifier.classify(
                evidence,
                now + NetworkTrustClassifier.DEFAULT_MAX_EVIDENCE_AGE_MILLIS + 1L
            )
        )
    }

    @Test
    fun futureDatedEvidenceIsUnknown() {
        assertEquals(
            NetworkTrust.UNKNOWN,
            classifier.classify(
                evidence(internetValidated = true, explicitlyTrustedByUser = true, observedAt = now + 1L),
                now
            )
        )
    }

    @Test
    fun evidenceAtMaximumAgeBoundaryIsAccepted() {
        val maxAge = 100L
        val boundaryClassifier = NetworkTrustClassifier(maxEvidenceAgeMillis = maxAge)
        val evidence = evidence(
            internetValidated = true,
            explicitlyTrustedByUser = true,
            observedAt = now - maxAge
        )

        assertEquals(NetworkTrust.TRUSTED, boundaryClassifier.classify(evidence, now))
    }

    @Test
    fun rejectsInvalidClockValuesAndAgeConfiguration() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkTrustClassifier(maxEvidenceAgeMillis = 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            classifier.classify(evidence(internetValidated = true), -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NetworkTrustEvidence(observedAtElapsedRealtimeMillis = -1L, internetValidated = true)
        }
    }

    private fun classify(evidence: NetworkTrustEvidence): NetworkTrust =
        classifier.classify(evidence, now)

    private fun evidence(
        internetValidated: Boolean,
        explicitlyTrustedByUser: Boolean = false,
        knownPublicNetwork: Boolean = false,
        captivePortalDetected: Boolean = false,
        dnsHijackSuspected: Boolean = false,
        tlsInterceptionSuspected: Boolean = false,
        observedAt: Long = now
    ) = NetworkTrustEvidence(
        observedAtElapsedRealtimeMillis = observedAt,
        internetValidated = internetValidated,
        explicitlyTrustedByUser = explicitlyTrustedByUser,
        knownPublicNetwork = knownPublicNetwork,
        captivePortalDetected = captivePortalDetected,
        dnsHijackSuspected = dnsHijackSuspected,
        tlsInterceptionSuspected = tlsInterceptionSuspected
    )
}
