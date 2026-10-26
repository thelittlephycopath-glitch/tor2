package org.torproject.vpn.autonomy

/**
 * Additive, platform-independent classification of network trust.
 *
 * This classifier consumes only explicit evidence supplied by a caller. It does not inspect
 * network identifiers, perform probes, change routes, or authorize traffic release.
 */
data class NetworkTrustEvidence(
    val internetValidated: Boolean,
    val explicitlyTrustedByUser: Boolean = false,
    val knownPublicNetwork: Boolean = false,
    val captivePortalDetected: Boolean = false,
    val dnsHijackSuspected: Boolean = false,
    val tlsInterceptionSuspected: Boolean = false
)

/**
 * Classifies evidence conservatively. A network is TRUSTED only when the user explicitly trusted
 * it, internet validation succeeded, and no portal or interference warning is present.
 *
 * HOSTILE means a security-interference indicator was supplied; it is a warning classification,
 * not a claim that malicious intent has been proven.
 */
class NetworkTrustClassifier {
    fun classify(evidence: NetworkTrustEvidence): NetworkTrust = when {
        evidence.dnsHijackSuspected || evidence.tlsInterceptionSuspected -> NetworkTrust.HOSTILE
        evidence.captivePortalDetected -> NetworkTrust.UNKNOWN
        !evidence.internetValidated -> NetworkTrust.UNKNOWN
        evidence.knownPublicNetwork -> NetworkTrust.PUBLIC
        evidence.explicitlyTrustedByUser -> NetworkTrust.TRUSTED
        else -> NetworkTrust.UNKNOWN
    }
}
