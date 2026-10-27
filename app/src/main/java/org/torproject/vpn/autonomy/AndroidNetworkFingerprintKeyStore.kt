package org.torproject.vpn.autonomy

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Supplies a stable, non-exportable per-install HMAC key from Android Keystore.
 *
 * The key is not written to preferences, files, logs, backups, or the fingerprint records. If the
 * OS removes the key (for example, after an app reinstall), old fingerprints cannot be reproduced;
 * that loss of cross-install continuity is intentional. The caller must still request explicit
 * opt-in before collecting any network identifiers or persisting their keyed fingerprints.
 */
class AndroidNetworkFingerprintKeyStore {
    fun getOrCreateKey(): SecretKey = synchronized(lock) {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { existing ->
            require(existing.algorithm.equals(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ignoreCase = true)) {
                "Stored network fingerprint key has an unexpected algorithm"
            }
            return@synchronized existing
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            ANDROID_KEY_STORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setKeySize(KEY_SIZE_BITS)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        generator.generateKey()
    }

    fun createHasher(): NetworkFingerprintHasher =
        NetworkFingerprintHasher(getOrCreateKey())

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "org.torproject.vpn.autonomy.network-fingerprint.hmac.v1"
        const val KEY_SIZE_BITS = 256
        val lock = Any()
    }
}
