package com.example.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val cipherTextBase64: String,
    val ivBase64: String,
    val authTagHash: String
)

/**
 * End-to-End Encryption (E2EE) Manager using AES-256-GCM with PBKDF2-HMAC-SHA256 key derivation.
 * Plaintext is encrypted client-side before leaving the device so cloud Firestore only stores
 * authenticated ciphertext, random 96-bit IVs, and SHA-256 integrity digests.
 */
object E2eeCryptoManager {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_DERIVATION_ALGO = "PBKDF2WithHmacSHA256"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val PBKDF2_ITERATIONS = 100_000

    private val secureRandom = SecureRandom()

    fun generateSaltBase64(): String {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun deriveKey(passphrase: String, saltBase64: String): SecretKey {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGO)
        val encoded = factory.generateSecret(spec).encoded
        return SecretKeySpec(encoded, "AES")
    }

    fun deriveDefaultSessionKey(userId: String, saltBase64: String): SecretKey {
        // Binds device-local zero-friction key to UID + per-user cryptographic salt
        // while allowing users to upgrade to a custom vault passphrase at any time.
        return deriveKey("auraglass-e2ee-$userId", saltBase64)
    }

    fun fingerprint(key: SecretKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.encoded)
        return digest.take(8).joinToString(":") { "%02X".format(it) }
    }

    fun sha256Hex(input: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun encrypt(plaintext: String, key: SecretKey): EncryptedPayload {
        val iv = ByteArray(GCM_IV_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val cipherB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val integrity = sha256Hex(cipherBytes + iv)
        return EncryptedPayload(
            cipherTextBase64 = cipherB64,
            ivBase64 = ivB64,
            authTagHash = integrity
        )
    }

    fun decrypt(cipherTextBase64: String, ivBase64: String, key: SecretKey): Result<String> {
        return runCatching {
            val cipherBytes = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        }
    }
}
