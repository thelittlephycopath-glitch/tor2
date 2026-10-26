package org.torproject.vpn.autonomy

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class PhysicalNetworkTransport { WIFI, CELLULAR, ETHERNET, UNKNOWN }

/**
 * Ephemeral caller-supplied identity material. Do not persist or log this object.
 * SSID is preserved exactly as supplied (including case and whitespace); BSSID and security type are normalized.
 */
data class NetworkFingerprintInput(
    val transport: PhysicalNetworkTransport,
    val ssid: String? = null,
    val bssid: String? = null,
    val securityType: String? = null
) {
    init {
        require(ssid == null || ssid.length <= MAX_SSID_CHARS) {
            "SSID input is too long"
        }
        require(bssid == null || bssid.length <= MAX_BSSID_CHARS) {
            "BSSID input is too long"
        }
        require(securityType == null || securityType.length <= MAX_SECURITY_TYPE_CHARS) {
            "Security type is too long"
        }
    }

    companion object {
        const val MAX_SSID_CHARS = 256
        const val MAX_BSSID_CHARS = 64
        const val MAX_SECURITY_TYPE_CHARS = 128
    }
}

/**
 * Generates a stable, per-install HMAC-SHA-256 fingerprint without returning raw network identifiers.
 *
 * The key must be a cryptographically random per-install secret (ideally backed by Android Keystore).
 * Do not store the key beside the fingerprint, use a shared key across devices, or log the input.
 * The digest alone cannot be used as an ordinary unsalted SSID dictionary hash.
 *
 * This is hashing only: it does not collect SSID/BSSID, persist history, infer network trust, or
 * enable automation. Callers must separately implement explicit opt-in, retention, and secure key
 * management before saving fingerprints.
 */
class NetworkFingerprintHasher(secretKey: ByteArray) {
    private val key = createKey(secretKey)

    fun fingerprint(input: NetworkFingerprintInput): String {
        val ssid = input.ssid?.takeIf { it.isNotEmpty() }
        val bssid = input.bssid?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        val securityType = input.securityType?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }

        require(ssid != null || bssid != null) {
            "At least one non-empty network identity field is required"
        }

        val mac = Mac.getInstance(HMAC_SHA_256)
        mac.init(key)
        mac.update(DOMAIN_SEPARATOR)
        updateField(mac, input.transport.name)
        updateField(mac, ssid)
        updateField(mac, bssid)
        updateField(mac, securityType)
        return mac.doFinal().toLowerHex()
    }

    private fun updateField(mac: Mac, value: String?) {
        if (value == null) {
            mac.update(NULL_FIELD_LENGTH)
            return
        }
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        mac.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        mac.update(bytes)
    }

    private fun createKey(secretKey: ByteArray): SecretKeySpec {
        require(secretKey.size >= MIN_KEY_BYTES) {
            "Fingerprint key must be at least 32 bytes long"
        }
        return SecretKeySpec(secretKey.copyOf(), HMAC_SHA_256)
    }

    private fun ByteArray.toLowerHex(): String = buildString(size * 2) {
        for (byte in this@toLowerHex) {
            val unsigned = byte.toInt() and 0xff
            append(HEX_DIGITS[unsigned ushr 4])
            append(HEX_DIGITS[unsigned and 0x0f])
        }
    }

    companion object {
        private const val HMAC_SHA_256 = "HmacSHA256"
        private const val MIN_KEY_BYTES = 32
        private val DOMAIN_SEPARATOR =
            "tor-vpn-network-fingerprint-v1\u0000".toByteArray(StandardCharsets.UTF_8)
        private val NULL_FIELD_LENGTH = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(-1).array()
        private const val HEX_DIGITS = "0123456789abcdef"
    }
}
