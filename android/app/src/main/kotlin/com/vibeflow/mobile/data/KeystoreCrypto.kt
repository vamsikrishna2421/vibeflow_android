package com.vibeflow.mobile.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small secrets at rest — currently the user's LLM API key — using a key
 * that lives in the **Android Keystore**: generated in, and never extractable from,
 * the device's secure hardware (TEE / StrongBox where available). We only ever
 * persist ciphertext; the plaintext key exists in memory just long enough to make
 * a request.
 *
 * Stored form:  `enc:v1:` + Base64( iv(12 bytes) ‖ AES-256-GCM ciphertext+tag ).
 * Anything without that prefix is treated as **legacy plaintext** and returned
 * unchanged, so existing installs keep working and get encrypted on the next save
 * (or via [SettingsRepository.migrateSecretsIfNeeded]).
 *
 * Failure handling is deliberately asymmetric so we never silently corrupt or leak
 * the user's (paid) API key:
 *  - [encrypt] returns **null** if encryption isn't possible — callers must then
 *    SKIP the write rather than persist plaintext (we never write cleartext).
 *  - [decrypt] returns the plaintext on success, **""** only for a confirmed-bad
 *    tag (wrong/rotated key or tampered blob — genuinely unusable), and **null**
 *    for a *transient* Keystore failure so callers keep the stored blob intact
 *    instead of mistaking a hiccup for "no key" and overwriting it.
 */
object KeystoreCrypto {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "vibeflow_secret_v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val PREFIX = "enc:v1:"
    private const val IV_BYTES = 12      // GCM standard nonce length
    private const val TAG_BITS = 128     // GCM auth tag length

    /** True if [stored] is one of our encrypted blobs (vs legacy plaintext). */
    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    /**
     * Encrypt [plain] for storage. Returns the `enc:v1:` blob on success, `""` for empty
     * input (a valid "no key" value), or **null** if encryption failed — in which case the
     * caller must NOT write anything (never persist plaintext).
     */
    fun encrypt(plain: String): String? {
        if (plain.isEmpty()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv                                       // random 12-byte IV from the Keystore
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val packed = ByteArray(iv.size + ct.size)
            System.arraycopy(iv, 0, packed, 0, iv.size)
            System.arraycopy(ct, 0, packed, iv.size, ct.size)
            PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP)
        }.getOrNull()                                               // failure → null: caller skips the write
    }

    /**
     * Decrypt a value produced by [encrypt]. Legacy plaintext passes straight through.
     * Returns `""` only for a confirmed-bad tag (key rotated / blob tampered — genuinely
     * unusable), and **null** for a transient Keystore failure so the caller keeps the
     * stored blob instead of treating a hiccup as "no key".
     */
    fun decrypt(stored: String): String? {
        if (!isEncrypted(stored)) return stored                      // legacy plaintext or empty
        return try {
            val packed = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val iv = packed.copyOfRange(0, IV_BYTES)
            val ct = packed.copyOfRange(IV_BYTES, packed.size)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            ""                                                       // permanent: wrong key / tampered → drop it
        } catch (e: Throwable) {
            null                                                     // transient: keep the blob, signal unavailable
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }
}
