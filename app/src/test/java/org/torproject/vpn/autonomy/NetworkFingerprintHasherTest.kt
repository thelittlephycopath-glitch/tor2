package org.torproject.vpn.autonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkFingerprintHasherTest {
    private val key = ByteArray(32) { index -> (index * 7 + 3).toByte() }
    private val hasher = NetworkFingerprintHasher(key)

    private fun input(
        ssid: String? = "Library-WiFi",
        bssid: String? = "AA:BB:CC:DD:EE:FF",
        securityType: String? = "WPA2"
    ) = NetworkFingerprintInput(
        transport = PhysicalNetworkTransport.WIFI,
        ssid = ssid,
        bssid = bssid,
        securityType = securityType
    )

    @Test
    fun sameIdentityAndKeyProduceSameFingerprint() {
        assertEquals(hasher.fingerprint(input()), hasher.fingerprint(input()))
    }

    @Test
    fun differentInstallKeysProduceUnlinkableFingerprints() {
        val differentKey = ByteArray(32) { index -> (index * 11 + 5).toByte() }
        val otherHasher = NetworkFingerprintHasher(differentKey)
        assertNotEquals(hasher.fingerprint(input()), otherHasher.fingerprint(input()))
    }

    @Test
    fun identityAndSecurityChangesProduceDifferentFingerprints() {
        val baseline = hasher.fingerprint(input())
        assertNotEquals(baseline, hasher.fingerprint(input(ssid = "Library-WiFi-Guest")))
        assertNotEquals(baseline, hasher.fingerprint(input(bssid = "11:22:33:44:55:66")))
        assertNotEquals(baseline, hasher.fingerprint(input(securityType = "Open")))
    }

    @Test
    fun outputIsFixedLengthLowercaseHexWithoutRawIdentity() {
        val raw = input()
        val fingerprint = hasher.fingerprint(raw)
        assertEquals(64, fingerprint.length)
        assertTrue(fingerprint.all { it in "0123456789abcdef" })
        assertTrue(!fingerprint.contains(raw.ssid!!))
        assertTrue(!fingerprint.contains(raw.bssid!!))
    }

    @Test
    fun ssidIsPreservedExactlyAndRemainsCaseSensitive() {
        assertNotEquals(
            hasher.fingerprint(input(ssid = "\u00e9-Cafe", bssid = null)),
            hasher.fingerprint(input(ssid = "e\u0301-Cafe", bssid = null))
        )
        assertNotEquals(
            hasher.fingerprint(input(ssid = "Library-WiFi", bssid = null)),
            hasher.fingerprint(input(ssid = "library-wifi", bssid = null))
        )
        assertNotEquals(
            hasher.fingerprint(input(ssid = " Library-WiFi", bssid = null)),
            hasher.fingerprint(input(ssid = "Library-WiFi", bssid = null))
        )
    }

    @Test
    fun rejectsWeakKeysEmptyIdentityAndOversizedInput() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkFingerprintHasher(ByteArray(16))
        }
        assertThrows(IllegalArgumentException::class.java) {
            hasher.fingerprint(input(ssid = "", bssid = " "))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NetworkFingerprintInput(
                transport = PhysicalNetworkTransport.WIFI,
                ssid = "x".repeat(NetworkFingerprintInput.MAX_SSID_CHARS + 1)
            )
        }
    }

    @Test
    fun callerKeyArrayIsCopied() {
        val mutableKey = ByteArray(32) { 7 }
        val hasher = NetworkFingerprintHasher(mutableKey)
        val before = hasher.fingerprint(input())
        mutableKey.fill(1)
        assertEquals(before, hasher.fingerprint(input()))
    }
}
