package org.torproject.vpn.autonomy

/**
 * Additive, platform-independent classification of network trust.
 *
 * Evidence timestamps use Android's elapsed-realtime clock, not wall-clock time, so changing the
 * device clock cannot make old network observations appear fresh.
 */
data class NetworkTrustEvidence(
    val observedAtElapsedRealtimeMillis: Long,
    val internetValidated: Boolean,
    val explicitlyTrustedByUser: Boolean = false,
    val knownPublicNetwork: Boolean = false,
    val captivePortalDetected: Boolean = false,
    val dnsHijackSuspected: Boolean = false,
    val tlsInterceptionSuspected: Boolean = false
) {
    init {
        require(observedAtElapsedRealtimeMillis >= 0L) {
            "Observation time must not be negative"
        }
    }
}

/**
 * Classifies evidence conservatively. A network is TRUSTED only when the evidence is fresh,
 * internet validation succeeded, and the user explicitly trusted it.
 *
 * HOSTILE means a fresh security-interference indicator was supplied; it is a warning
 * classification, not proof of malicious intent. Stale or future-dated evidence is UNKNOWN.
 *
 * This consumes caller-supplied evidence only. It does not inspect networks, probe endpoints,
 * change routes, or authorize traffic release.
 */
class NetworkTrustClassifier(
    val maxEvidenceAgeMillis: Long = DEFAULT_MAX_EVIDENCE_AGE_MILLIS
) {
    init {
        require(maxEvidenceAgeMillis > 0L) { "Maximum evidence age must be positive" }
    }

    fun classify(
        evidence: NetworkTrustEvidence,
        nowElapsedRealtimeMillis: Long
    ): NetworkTrust {
        require(nowElapsedRealtimeMillis >= 0L) { "Current elapsed realtime must not be negative" }

        val observedAt = evidence.observedAtElapsedRealtimeMillis
        if (observedAt > nowElapsedRealtimeMillis ||
            nowElapsedRealtimeMillis - observedAt > maxEvidenceAgeMillis
        ) {
            return NetworkTrust.UNKNOWN
        }

        return when {
            evidence.dnsHijackSuspected || evidence.tlsInterceptionSuspected ->
                NetworkTrust.HOSTILE
            evidence.captivePortalDetected -> NetworkTrust.UNKNOWN
            !evidence.internetValidated -> NetworkTrust.UNKNOWN
            evidence.knownPublicNetwork -> NetworkTrust.PUBLIC
            evidence.explicitlyTrustedByUser -> NetworkTrust.TRUSTED
            else -> NetworkTrust.UNKNOWN
        }
    }

    companion object {
        const val DEFAULT_MAX_EVIDENCE_AGE_MILLIS = 30_000L
    }
}
