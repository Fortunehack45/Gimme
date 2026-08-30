package org.airshare.app.engine.crypto

import android.util.Base64
import java.io.InputStream
import java.io.OutputStream
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {

    private const val EC_ALGORITHM = "EC"
    private const val EC_KEY_SIZE = 256
    private const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val HKDF_ALGORITHM = "HmacSHA256"

    private val secureRandom = SecureRandom()

    /**
     * Generates an ephemeral EC key pair for ECDH handshake.
     */
    fun generateEphemeralKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(EC_ALGORITHM)
        keyPairGenerator.initialize(EC_KEY_SIZE, secureRandom)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Derives a 256-bit AES SecretKey using ECDH shared secret + HKDF-SHA256.
     */
    fun deriveSharedSessionKey(myPrivateKey: PrivateKey, peerPublicKeyBytes: ByteArray, salt: ByteArray? = null): SecretKey {
        val keyFactory = KeyFactory.getInstance(EC_ALGORITHM)
        val peerPublicKeySpec = X509EncodedKeySpec(peerPublicKeyBytes)
        val peerPublicKey = keyFactory.generatePublic(peerPublicKeySpec)

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myPrivateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        val rawSharedSecret = keyAgreement.generateSecret()

        // Apply HKDF-Extract and Expand to derive a 256-bit AES key
        val derivedKeyBytes = hkdf(
            ikm = rawSharedSecret,
            salt = salt ?: "AirShareSessionSalt_v1".toByteArray(Charsets.UTF_8),
            info = "AirShare_AES256_GCM_Transfer".toByteArray(Charsets.UTF_8),
            outputLength = 32
        )
        return SecretKeySpec(derivedKeyBytes, "AES")
    }

    /**
     * Encrypts a chunk of plaintext bytes with AES-256-GCM.
     * Returns IV (12 bytes) + Ciphertext + Auth Tag (16 bytes).
     */
    fun encryptChunk(plainData: ByteArray, sessionKey: SecretKey): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey, gcmSpec)

        val cipherBytes = cipher.doFinal(plainData)
        val output = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, output, 0, iv.size)
        System.arraycopy(cipherBytes, 0, output, iv.size, cipherBytes.size)
        return output
    }

    /**
     * Decrypts an AES-256-GCM encrypted chunk.
     * Expects input formatted as: IV (12 bytes) + Ciphertext with Auth Tag.
     */
    fun decryptChunk(encryptedPayload: ByteArray, sessionKey: SecretKey): ByteArray {
        require(encryptedPayload.size > GCM_IV_LENGTH) { "Payload too small for IV" }
        val iv = ByteArray(GCM_IV_LENGTH)
        System.arraycopy(encryptedPayload, 0, iv, 0, GCM_IV_LENGTH)

        val cipherBytesLength = encryptedPayload.size - GCM_IV_LENGTH
        val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, sessionKey, gcmSpec)

        return cipher.doFinal(encryptedPayload, GCM_IV_LENGTH, cipherBytesLength)
    }

    /**
     * Calculates SHA-256 hex string for a byte array.
     */
    fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Calculates SHA-256 hex string for an InputStream.
     */
    fun calculateSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Standard RFC 5869 HMAC-based Extract-and-Expand Key Derivation Function (HKDF).
     */
    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, outputLength: Int): ByteArray {
        // Step 1: Extract
        val mac = Mac.getInstance(HKDF_ALGORITHM)
        val saltKey = SecretKeySpec(if (salt.isNotEmpty()) salt else ByteArray(32), HKDF_ALGORITHM)
        mac.init(saltKey)
        val prk = mac.doFinal(ikm)

        // Step 2: Expand
        val prkKey = SecretKeySpec(prk, HKDF_ALGORITHM)
        mac.init(prkKey)

        var t = ByteArray(0)
        val okm = ByteArray(outputLength)
        var bytesGenerated = 0
        var round = 1.toByte()

        while (bytesGenerated < outputLength) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(round)
            t = mac.doFinal()

            val toCopy = minOf(t.size, outputLength - bytesGenerated)
            System.arraycopy(t, 0, okm, bytesGenerated, toCopy)
            bytesGenerated += toCopy
            round++
        }
        return okm
    }

    fun encodePublicKey(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun decodePublicKey(base64Str: String): ByteArray {
        return Base64.decode(base64Str, Base64.NO_WRAP)
    }
}
