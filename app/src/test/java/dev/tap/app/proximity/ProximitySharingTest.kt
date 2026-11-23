package dev.tap.app.proximity

import org.junit.Assert.*
import org.junit.Test

class ProximitySharingTest {

    @Test
    fun testFileCategoryDetection() {
        assertEquals(FileCategory.PHOTO, ProximityFile.detectCategory("image/jpeg", "vacation.jpg"))
        assertEquals(FileCategory.PHOTO, ProximityFile.detectCategory("image/png", "screenshot.png"))
        assertEquals(FileCategory.VIDEO, ProximityFile.detectCategory("video/mp4", "movie.mp4"))
        assertEquals(FileCategory.VIDEO, ProximityFile.detectCategory("video/quicktime", "clip.mov"))
        assertEquals(FileCategory.AUDIO, ProximityFile.detectCategory("audio/mpeg", "song.mp3"))
        assertEquals(FileCategory.AUDIO, ProximityFile.detectCategory("audio/flac", "audio.flac"))
        assertEquals(FileCategory.DOCUMENT, ProximityFile.detectCategory("application/pdf", "contract.pdf"))
        assertEquals(FileCategory.DOCUMENT, ProximityFile.detectCategory("application/msword", "doc.docx"))
        assertEquals(FileCategory.ARCHIVE, ProximityFile.detectCategory("application/zip", "backup.zip"))
        assertEquals(FileCategory.ARCHIVE, ProximityFile.detectCategory("application/x-tar", "archive.tar.gz"))
        assertEquals(FileCategory.ARBITRARY, ProximityFile.detectCategory("application/octet-stream", "firmware.bin"))
    }

    @Test
    fun testFormatBytes() {
        assertEquals("0 B", ProximityFile.formatBytes(0))
        assertEquals("500.00 B", ProximityFile.formatBytes(500))
        assertEquals("1.00 KB", ProximityFile.formatBytes(1024))
        assertEquals("2.50 MB", ProximityFile.formatBytes((2.5 * 1024 * 1024).toLong()))
        assertEquals("1.20 GB", ProximityFile.formatBytes((1.2 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testTransferSecurityKeyAgreementAndAesGcmEncryption() {
        // Endpoint 1 (Alice / Galaxy A54)
        val aliceSecurity = TransferSecurityManager()
        val alicePubKeyBase64 = aliceSecurity.getPublicKeyBase64()

        // Endpoint 2 (Bob / Galaxy S24)
        val bobSecurity = TransferSecurityManager()
        val bobPubKeyBase64 = bobSecurity.getPublicKeyBase64()

        // Ephemeral ECDH key agreement + HKDF-SHA256
        val aliceSharedKey = aliceSecurity.deriveSessionKey(bobPubKeyBase64)
        val bobSharedKey = bobSecurity.deriveSessionKey(alicePubKeyBase64)

        assertArrayEquals("Derived session keys must be cryptographically identical", aliceSharedKey, bobSharedKey)
        assertEquals("Derived key must be 32 bytes (AES-256)", 32, aliceSharedKey.size)

        // AES-256-GCM chunk encryption
        val plaintext = "Hello from Samsung Galaxy A54 Proximity File Sharing!".toByteArray(Charsets.UTF_8)
        val encryptedChunk = aliceSecurity.encryptChunk(aliceSharedKey, nonceCounter = 0L, plaintext = plaintext)

        // Verify cipher output contains 12-byte IV + ciphertext + 16-byte tag
        assertTrue(encryptedChunk.size >= 12 + plaintext.size + 16)

        // Decrypt with Bob's shared key
        val decrypted = bobSecurity.decryptChunk(bobSharedKey, encryptedChunk)
        assertEquals("Hello from Samsung Galaxy A54 Proximity File Sharing!", String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun testDeviceHandshakeSessionNegotiation() {
        val aliceSecurity = TransferSecurityManager()
        val aliceHandshake = DeviceHandshakeManager(aliceSecurity)

        // Alice creates session offer
        val offer = aliceHandshake.createSessionOffer(
            deviceName = "Galaxy A54",
            hostAddress = "192.168.1.100",
            port = 8990
        )

        assertEquals("Galaxy A54", offer.deviceName)
        assertEquals("192.168.1.100", offer.hostAddress)
        assertEquals(8990, offer.port)
        assertNotNull(offer.publicKeyBase64)

        // Bob receives offer and completes handshake
        val bobSecurity = TransferSecurityManager()
        val bobHandshake = DeviceHandshakeManager(bobSecurity)

        val handshakeResult = bobHandshake.completeHandshake(offer)
        assertTrue("Handshake state must be Authenticated", handshakeResult is HandshakeState.Authenticated)

        val authenticated = handshakeResult as HandshakeState.Authenticated
        assertEquals("Galaxy A54", authenticated.peer.deviceName)

        // Verify that Alice can derive the exact same session key using Bob's public key
        val aliceSessionKey = aliceSecurity.deriveSessionKey(bobSecurity.getPublicKeyBase64())
        assertArrayEquals("Handshake authenticated session keys must match", aliceSessionKey, authenticated.sessionKey)
    }

    @Test
    fun testProximityProgressFormatting() {
        val progress = ProximityProgress(
            currentFileName = "presentation.pdf",
            currentFileIndex = 1,
            totalFiles = 2,
            bytesTransferred = 50 * 1024 * 1024,
            totalBytes = 100 * 1024 * 1024,
            speedBytesPerSec = 15 * 1024 * 1024
        )

        assertEquals(0.5f, progress.percent, 0.001f)
        assertEquals("15.00 MB/s", progress.formattedSpeed)
    }
}
