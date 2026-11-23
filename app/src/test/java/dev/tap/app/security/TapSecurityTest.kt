package dev.tap.app.security

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class TapSecurityTest {

    @Test
    fun testKeyPairGenerationAndKeyExchange() {
        val aliceKeyPair = TapSecurity.generateKeyPair()
        val bobKeyPair = TapSecurity.generateKeyPair()

        assertNotNull(aliceKeyPair.public)
        assertNotNull(aliceKeyPair.private)
        assertNotNull(bobKeyPair.public)
        assertNotNull(bobKeyPair.private)

        val aliceSharedSecret = TapSecurity.deriveSharedSecret(aliceKeyPair.private, bobKeyPair.public)
        val bobSharedSecret = TapSecurity.deriveSharedSecret(bobKeyPair.private, aliceKeyPair.public)

        assertArrayEquals(aliceSharedSecret.encoded, bobSharedSecret.encoded)
    }

    @Test
    fun testAesGcmEncryptionAndDecryption() {
        val keyPairAlice = TapSecurity.generateKeyPair()
        val keyPairBob = TapSecurity.generateKeyPair()
        val sharedSecret = TapSecurity.deriveSharedSecret(keyPairAlice.private, keyPairBob.public)

        val originalData = "Hello TAP! Production-ready peer-to-peer file sharing.".toByteArray(StandardCharsets.UTF_8)
        val nonce = TapSecurity.generateNonce()

        val encrypted = TapSecurity.encrypt(originalData, sharedSecret, nonce)
        assertFalse(originalData.contentEquals(encrypted))

        val decrypted = TapSecurity.decrypt(encrypted, sharedSecret, nonce)
        assertArrayEquals(originalData, decrypted)
        assertEquals(String(originalData), String(decrypted))
    }

    @Test
    fun testSha256Hashing() {
        val text = "TAP File Integrity Verification"
        val data = text.toByteArray(StandardCharsets.UTF_8)

        val hashDirect = TapSecurity.hashSHA256(data)
        val hashStream = ByteArrayInputStream(data).use { TapSecurity.hashSHA256Stream(it) }

        assertEquals(64, hashDirect.length)
        assertEquals(hashDirect, hashStream)
    }

    @Test
    fun testFilenameSanitization() {
        // Path traversal attempts
        assertEquals("malicious.apk", TapSecurity.sanitizeFilename("../../malicious.apk"))
        assertEquals("passwd", TapSecurity.sanitizeFilename("/etc/passwd"))
        assertEquals("secret.doc", TapSecurity.sanitizeFilename("..\\..\\secret.doc"))

        // Special dangerous characters
        val clean = TapSecurity.sanitizeFilename("file*with:bad?chars<.png")
        assertFalse(clean.contains("*"))
        assertFalse(clean.contains(":"))
        assertFalse(clean.contains("?"))
        assertFalse(clean.contains("<"))

        // Blank fallback
        assertTrue(TapSecurity.sanitizeFilename("").startsWith("tap_file_"))
    }

    @Test
    fun testMimeTypeValidation() {
        assertEquals("image/png", TapSecurity.validateMimeType("image/png"))
        assertEquals("application/pdf", TapSecurity.validateMimeType("APPLICATION/PDF"))
        assertEquals("application/octet-stream", TapSecurity.validateMimeType("invalid-mime-type"))
    }
}
