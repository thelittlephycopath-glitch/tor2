package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OptInNetworkLearningStoreTest {
    private class MemoryStorage(var value: String? = null) : NetworkLearningStorage {
        override fun read(): String? = value
        override fun write(value: String?) {
            this.value = value
        }
    }

    private val storage = MemoryStorage()
    private val store = OptInNetworkLearningStore(storage, maxEntries = 2, retentionMillis = 100L)
    private val digestA = "a".repeat(64)
    private val digestB = "b".repeat(64)
    private val digestC = "c".repeat(64)

    @Test
    fun doesNotWriteWithoutOptInAndErasesPriorRecords() {
        assertTrue(store.recordFingerprint(digestA, 90L, true, 100L))
        assertFalse(store.recordFingerprint(digestB, 95L, false, 100L))
        assertEquals(null, storage.value)
        assertTrue(store.readFingerprints(100L, false).isEmpty())
    }

    @Test
    fun optingOutClearsRecordsBeforeValidatingIrrelevantObservationData() {
        store.recordFingerprint(digestA, 99L, true, 100L)
        assertFalse(store.recordFingerprint("not-a-digest", -1L, false, -1L))
        assertEquals(null, storage.value)
    }

    @Test
    fun retainsOnlyBoundedMostRecentEntries() {
        store.recordFingerprint(digestA, 80L, true, 100L)
        store.recordFingerprint(digestB, 90L, true, 100L)
        store.recordFingerprint(digestC, 95L, true, 100L)
        assertEquals(listOf(digestB, digestC), store.readFingerprints(100L, true).map { it.digest })
    }

    @Test
    fun removesExpiredFutureDatedMalformedAndDuplicateRecords() {
        storage.value = listOf(
            "$digestA|1",
            "$digestB|99",
            "$digestC|101",
            "not-a-digest|99",
            "$digestB|99",
            "$digestA|malformed"
        ).joinToString("\n")
        assertEquals(listOf(digestB), store.readFingerprints(100L, true).map { it.digest })
        assertEquals("$digestB|99", storage.value)
    }

    @Test
    fun newestTimestampWinsWhenPersistedRecordsContainDuplicateDigests() {
        storage.value = listOf("$digestA|80", "$digestB|90", "$digestA|95").joinToString("\n")
        assertEquals(
            listOf(NetworkLearningRecord(digestB, 90L), NetworkLearningRecord(digestA, 95L)),
            store.readFingerprints(100L, true)
        )
    }

    @Test
    fun outOfOrderObservationsCannotDisplaceNewerRecordsOrRegressDuplicateTimestamp() {
        store.recordFingerprint(digestA, 99L, true, 100L)
        store.recordFingerprint(digestB, 98L, true, 100L)
        store.recordFingerprint(digestC, 50L, true, 100L)
        assertEquals(
            listOf(NetworkLearningRecord(digestB, 98L), NetworkLearningRecord(digestA, 99L)),
            store.readFingerprints(100L, true)
        )
        store.recordFingerprint(digestA, 60L, true, 100L)
        assertEquals(
            listOf(NetworkLearningRecord(digestB, 98L), NetworkLearningRecord(digestA, 99L)),
            store.readFingerprints(100L, true)
        )
    }

    @Test
    fun rejectsInvalidDigestAndInvalidTime() {
        try {
            store.recordFingerprint("ssid-name", 99L, true, 100L)
            throw AssertionError("Expected invalid digest to be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        try {
            store.recordFingerprint(digestA, 99L, true, -1L)
            throw AssertionError("Expected negative current time to be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun expiredOrFutureObservationsAreNotSaved() {
        assertFalse(store.recordFingerprint(digestA, 0L, true, 101L))
        assertFalse(store.recordFingerprint(digestB, 101L, true, 100L))
        assertEquals(null, storage.value)
    }

    @Test
    fun duplicateDigestUpdatesTimestampAndKeepsSingleEntry() {
        store.recordFingerprint(digestA, 80L, true, 100L)
        store.recordFingerprint(digestA, 95L, true, 100L)
        assertEquals(listOf(NetworkLearningRecord(digestA, 95L)), store.readFingerprints(100L, true))
    }

    @Test
    fun clearRemovesPersistedLearning() {
        store.recordFingerprint(digestA, 99L, true, 100L)
        store.clear()
        assertEquals(null, storage.value)
    }

    @Test
    fun rejectsUnreasonablyLargeCapacityAndRetention() {
        try {
            OptInNetworkLearningStore(MemoryStorage(), maxEntries = 513)
            throw AssertionError("Expected excessive capacity to be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        try {
            OptInNetworkLearningStore(
                MemoryStorage(),
                retentionMillis = OptInNetworkLearningStore.MAX_RETENTION_MILLIS + 1L
            )
            throw AssertionError("Expected excessive retention to be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
