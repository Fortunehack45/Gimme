package org.airshare.app

import org.airshare.app.engine.crypto.CryptoEngine
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class CryptoEngineTest {

    @Test
    fun testKeyPairGeneration() {
        val keyPair = CryptoEngine.generateEphemeralKeyPair()
        assertNotNull(keyPair)
        assertNotNull(keyPair.public)
        assertNotNull(keyPair.private)
        assertEquals("EC", keyPair.public.algorithm)
    }

    @Test
    fun testEcdhKeyAgreement() {
        // Alice generates keypair
        val aliceKeyPair = CryptoEngine.generateEphemeralKeyPair()

        // Bob generates keypair
        val bobKeyPair = CryptoEngine.generateEphemeralKeyPair()

        // Alice derives shared secret using Bob's public key
        val aliceSessionKey = CryptoEngine.deriveSharedSessionKey(
            aliceKeyPair.private,
            bobKeyPair.public.encoded
        )

        // Bob derives shared secret using Alice's public key
        val bobSessionKey = CryptoEngine.deriveSharedSessionKey(
            bobKeyPair.private,
            aliceKeyPair.public.encoded
        )

        // Both derived 256-bit AES keys must match identically
        assertArrayEquals(aliceSessionKey.encoded, bobSessionKey.encoded)
    }

    @Test
    fun testAesGcmEncryptionDecryptionRoundtrip() {
        val aliceKeyPair = CryptoEngine.generateEphemeralKeyPair()
        val bobKeyPair = CryptoEngine.generateEphemeralKeyPair()
        val sessionKey = CryptoEngine.deriveSharedSessionKey(aliceKeyPair.private, bobKeyPair.public.encoded)

        val sampleData = "AirShare: High-Speed Encrypted Local P2P Transfer Payload 1234567890".toByteArray(Charsets.UTF_8)

        // Encrypt with AES-256-GCM
        val encryptedPayload = CryptoEngine.encryptChunk(sampleData, sessionKey)
        assertNotNull(encryptedPayload)
        assertTrue(encryptedPayload.size > sampleData.size) // Includes 12-byte IV + 16-byte Auth Tag

        // Decrypt with Bob's session key
        val decryptedData = CryptoEngine.decryptChunk(encryptedPayload, sessionKey)
        assertArrayEquals(sampleData, decryptedData)
    }

    @Test
    fun testSha256Checksum() {
        val sample = "AirShare".toByteArray(Charsets.UTF_8)
        val hash = CryptoEngine.calculateSha256(sample)
        assertNotNull(hash)
        assertEquals(64, hash.length)

        val stream = ByteArrayInputStream(sample)
        val streamHash = CryptoEngine.calculateSha256(stream)
        assertEquals(hash, streamHash)
    }
}
