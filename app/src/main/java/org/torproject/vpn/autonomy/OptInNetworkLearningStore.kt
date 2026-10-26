package org.torproject.vpn.autonomy

/**
 * Storage boundary for privacy-minimised network learning. Implementations must keep the value
 * private to this app and must not log it.
 */
interface NetworkLearningStorage {
    fun read(): String?
    fun write(value: String?)
}

/** A keyed fingerprint digest and its wall-clock observation time; never contains raw identifiers. */
data class NetworkLearningRecord(
    val digest: String,
    val observedAtEpochMillis: Long
)

/**
 * Explicit-consent, bounded, retention-limited persistence for keyed network fingerprints.
 *
 * The caller must supply the current explicit opt-in state on every operation. When opt-in is false,
 * records are erased rather than merely hidden. This class does not collect network identifiers,
 * derive fingerprints, infer trust, or enable any VPN automation.
 */
class OptInNetworkLearningStore(
    private val storage: NetworkLearningStorage,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val retentionMillis: Long = DEFAULT_RETENTION_MILLIS
) {
    init {
        require(maxEntries in 1..HARD_MAX_ENTRIES) { "Entry capacity must be between 1 and 512" }
        require(retentionMillis in 1L..MAX_RETENTION_MILLIS) {
            "Retention must be between 1 ms and 180 days"
        }
    }

    @Synchronized
    fun recordFingerprint(
        digest: String,
        observedAtEpochMillis: Long,
        optedIn: Boolean,
        nowEpochMillis: Long
    ): Boolean {
        require(nowEpochMillis >= 0L) { "Current time cannot be negative" }
        require(observedAtEpochMillis >= 0L) { "Observation time cannot be negative" }
        if (!optedIn) {
            clear()
            return false
        }
        require(isValidDigest(digest)) { "Only lowercase 64-character SHA-256/HMAC digests may be stored" }
        if (observedAtEpochMillis > nowEpochMillis ||
            nowEpochMillis - observedAtEpochMillis > retentionMillis
        ) {
            return false
        }

        val records = readValidRecords(nowEpochMillis)
            .filterNot { it.digest == digest }
            .toMutableList()
        records += NetworkLearningRecord(digest, observedAtEpochMillis)
        persist(records.takeLast(maxEntries))
        return true
    }

    @Synchronized
    fun readFingerprints(
        nowEpochMillis: Long,
        optedIn: Boolean
    ): List<NetworkLearningRecord> {
        require(nowEpochMillis >= 0L) { "Current time cannot be negative" }
        if (!optedIn) {
            clear()
            return emptyList()
        }
        val valid = readValidRecords(nowEpochMillis)
        persist(valid)
        return valid.toList()
    }

    /** Permanently removes all saved learning records from this store. */
    @Synchronized
    fun clear() {
        storage.write(null)
    }

    private fun readValidRecords(nowEpochMillis: Long): List<NetworkLearningRecord> {
        val raw = storage.read() ?: return emptyList()
        return raw.lineSequence()
            .mapNotNull(::parseRecord)
            .filter { record ->
                record.observedAtEpochMillis <= nowEpochMillis &&
                    nowEpochMillis - record.observedAtEpochMillis <= retentionMillis
            }
            .groupBy { it.digest }
            .mapNotNull { (_, duplicates) -> duplicates.maxByOrNull { it.observedAtEpochMillis } }
            .sortedBy { it.observedAtEpochMillis }
            .takeLast(maxEntries)
            .toList()
    }

    private fun parseRecord(line: String): NetworkLearningRecord? {
        val separator = line.indexOf('|')
        if (separator <= 0 || separator != line.lastIndexOf('|')) return null
        val digest = line.substring(0, separator)
        if (!isValidDigest(digest)) return null
        val timestamp = line.substring(separator + 1).toLongOrNull() ?: return null
        if (timestamp < 0L) return null
        return NetworkLearningRecord(digest, timestamp)
    }

    private fun persist(records: List<NetworkLearningRecord>) {
        if (records.isEmpty()) {
            storage.write(null)
            return
        }
        storage.write(records.joinToString(separator = "\n") {
            "${it.digest}|${it.observedAtEpochMillis}"
        })
    }

    private fun isValidDigest(digest: String): Boolean =
        digest.length == DIGEST_HEX_LENGTH && digest.all { it in '0'..'9' || it in 'a'..'f' }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 128
        const val HARD_MAX_ENTRIES = 512
        const val DEFAULT_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1000L
        const val MAX_RETENTION_MILLIS = 180L * 24L * 60L * 60L * 1000L
        private const val DIGEST_HEX_LENGTH = 64
    }
}
