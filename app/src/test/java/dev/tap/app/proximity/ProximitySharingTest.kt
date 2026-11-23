package dev.tap.app.proximity

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

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
    fun testSessionBoundKeyDerivationAndAesGcm() {
        val sessionId = "session_test_9981"
        val senderId = "device_a54"
        val receiverId = "device_s24"

        val aliceSecurity = TransferSecurityManager()
        val alicePub = aliceSecurity.getPublicKeyBase64()

        val bobSecurity = TransferSecurityManager()
        val bobPub = bobSecurity.getPublicKeyBase64()

        // Derive keys bound to session and identity
        val aliceKey = aliceSecurity.deriveSessionKey(bobPub, sessionId, senderId, receiverId)
        val bobKey = bobSecurity.deriveSessionKey(alicePub, sessionId, senderId, receiverId)

        assertArrayEquals("Derived session keys must match", aliceKey, bobKey)
        assertEquals(32, aliceKey.size)

        // Encrypt with monotonic chunk index 0
        val plaintext = "Encrypted chunk payload with monotonic nonce".toByteArray(Charsets.UTF_8)
        val encrypted = aliceSecurity.encryptChunk(aliceKey, chunkIndex = 0L, plaintext = plaintext, isSender = true, sessionId = sessionId)

        // Must contain 12-byte IV + ciphertext + 16-byte GCM tag
        assertTrue(encrypted.size >= 12 + plaintext.size + 16)

        // Decrypt
        val decrypted = bobSecurity.decryptChunk(bobKey, encrypted)
        assertEquals("Encrypted chunk payload with monotonic nonce", String(decrypted, Charsets.UTF_8))
    }

    @Test
    fun testShortAuthenticationStringMitmProtection() {
        val sessionId = "session_secure_sas_123"

        val alice = TransferSecurityManager()
        val alicePub = alice.getPublicKeyBase64()

        val bob = TransferSecurityManager()
        val bobPub = bob.getPublicKeyBase64()

        // Both compute shared secret and SAS independently
        val aliceSas = alice.calculateSasCode(bobPub, sessionId)
        val bobSas = bob.calculateSasCode(alicePub, sessionId)

        // SAS code must be identical on both physical devices
        assertEquals("Alice and Bob SAS codes must match identically", aliceSas, bobSas)
        // Must be formatted as 6 digits with a space: "XXX XXX"
        assertEquals(7, aliceSas.length)
        assertTrue(aliceSas.matches(Regex("\\d{3} \\d{3}")))

        // Test MITM attacker Eve with different key
        val eve = TransferSecurityManager()
        val evePub = eve.getPublicKeyBase64()
        val eveSas = eve.calculateSasCode(alicePub, sessionId)

        assertNotEquals("Eve cannot produce matching SAS code", aliceSas, eveSas)
    }

    @Test
    fun testHandshakeProtocolOfferAndApproval() {
        val sessionId = "session_handshake_456"
        val alice = TransferSecurityManager()
        val bob = TransferSecurityManager()

        val aliceHandshake = DeviceHandshakeManager(alice)
        val bobHandshake = DeviceHandshakeManager(bob)

        val manifests = listOf(
            ProximityFileManifest(
                name = "report.pdf",
                sizeBytes = 2048,
                mimeType = "application/pdf",
                sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            )
        )

        // Alice creates offer
        val offer = aliceHandshake.createSessionOffer(
            deviceName = "Galaxy A54",
            deviceId = "a54_device",
            hostAddress = "192.168.1.50",
            port = 8990,
            files = manifests,
            sessionId = sessionId
        )

        assertEquals("Galaxy A54", offer.senderDeviceName)
        assertEquals(1, offer.files.size)

        // Bob evaluates offer -> enters AwaitingApproval with SAS code
        val evalState = bobHandshake.evaluateIncomingOffer(offer, receiverDeviceId = "s24_device")
        assertTrue("State must be AwaitingApproval", evalState is HandshakeState.AwaitingApproval)
        val awaiting = evalState as HandshakeState.AwaitingApproval

        val aliceSas = alice.calculateSasCode(bob.getPublicKeyBase64(), sessionId)
        assertEquals("Awaiting approval SAS code must match Alice SAS code", aliceSas, awaiting.sasCode)

        // Bob accepts and creates response
        val bobResponse = ProximityHandshakeResponse(
            sessionId = sessionId,
            receiverDeviceName = "Galaxy S24",
            receiverDeviceId = "s24_device",
            receiverPublicKeyBase64 = bob.getPublicKeyBase64(),
            accepted = true,
            sasCode = awaiting.sasCode
        )

        // Alice processes response -> enters Approved with matching session key
        val finalState = aliceHandshake.processReceiverResponse(offer, bobResponse)
        assertTrue("State must be Approved", finalState is HandshakeState.Approved)
        val approved = finalState as HandshakeState.Approved

        val expectedKey = alice.deriveSessionKey(bob.getPublicKeyBase64(), sessionId, "a54_device", "s24_device")
        assertArrayEquals("Session keys derived after handshake must match", expectedKey, approved.sessionKey)
    }

    @Test
    fun testHandshakeRejection() {
        val sessionId = "session_reject_789"
        val alice = TransferSecurityManager()
        val aliceHandshake = DeviceHandshakeManager(alice)

        val offer = aliceHandshake.createSessionOffer(
            deviceName = "Galaxy A54",
            deviceId = "a54_device",
            hostAddress = "192.168.1.50",
            port = 8990,
            files = emptyList(),
            sessionId = sessionId
        )

        val rejectedResponse = ProximityHandshakeResponse(
            sessionId = sessionId,
            receiverDeviceName = "Galaxy S24",
            receiverDeviceId = "s24_device",
            receiverPublicKeyBase64 = "invalid_key",
            accepted = false,
            rejectionReason = "User rejected"
        )

        val state = aliceHandshake.processReceiverResponse(offer, rejectedResponse)
        assertTrue("State must be Rejected", state is HandshakeState.Rejected)
        assertEquals("User rejected", (state as HandshakeState.Rejected).reason)
    }

    @Test
    fun testStreamingSha256ChecksumIntegrity() {
        val payload = "Streaming large file payload integrity verification bytes test 1234567890".toByteArray(Charsets.UTF_8)
        val hash = TransferSecurityManager.calculateStreamHash(ByteArrayInputStream(payload))

        assertNotNull(hash)
        assertEquals(64, hash.length)

        val corruptPayload = "Streaming large file payload integrity verification bytes test 1234567891".toByteArray(Charsets.UTF_8)
        val corruptHash = TransferSecurityManager.calculateStreamHash(ByteArrayInputStream(corruptPayload))

        assertNotEquals("Corrupted bytes must yield different SHA-256 hash", hash, corruptHash)
    }

    @Test
    fun testProximityProgressSpeedCalculation() {
        val progress = ProximityProgress(
            currentFileName = "document.pdf",
            currentFileIndex = 1,
            totalFiles = 1,
            bytesTransferred = 25 * 1024 * 1024,
            totalBytes = 50 * 1024 * 1024,
            speedBytesPerSec = 12 * 1024 * 1024
        )

        assertEquals(0.5f, progress.percent, 0.001f)
        assertEquals("12.00 MB/s", progress.formattedSpeed)
    }

    @Test
    fun testProximityNdefRepositorySessionLifecycle() {
        ProximityNdefRepository.clearProximitySession()
        assertFalse(ProximityNdefRepository.hasActiveSession())
        assertNull(ProximityNdefRepository.activeSessionId)
        assertNull(ProximityNdefRepository.activeSessionNdef)
        assertNull(ProximityNdefRepository.activeWebFallbackUrl)

        val testPayload = "PROXIMITY_NDEF_PAYLOAD_TEST".toByteArray(Charsets.UTF_8)
        val testUrl = "http://192.168.1.50:8989/?token=sec123"
        ProximityNdefRepository.setProximitySession(
            sessionId = "session_tap_9988",
            ndefBytes = testPayload,
            webFallbackUrl = testUrl
        )

        assertTrue(ProximityNdefRepository.hasActiveSession())
        assertEquals("session_tap_9988", ProximityNdefRepository.activeSessionId)
        assertEquals(testUrl, ProximityNdefRepository.activeWebFallbackUrl)
        assertArrayEquals(testPayload, ProximityNdefRepository.activeSessionNdef)

        ProximityNdefRepository.clearProximitySession()
        assertFalse(ProximityNdefRepository.hasActiveSession())
        assertNull(ProximityNdefRepository.activeSessionId)
    }

    @Test
    fun testOfferSerializationRoundTrip() {
        val files = listOf(
            ProximityFileManifest("photo.jpg", 1024 * 1024 * 3, "image/jpeg", "abcd1234efgh5678"),
            ProximityFileManifest("archive.zip", 1024 * 1024 * 10, "application/zip", "1234abcd5678efgh")
        )

        val originalOffer = ProximityHandshakeOffer(
            sessionId = "sess_p2p_4455",
            senderDeviceName = "Galaxy A54 5G",
            senderDeviceId = "tap_device_a54",
            senderPublicKeyBase64 = "BMk38dj92kd82==",
            hostAddress = "192.168.49.1",
            port = 8990,
            files = files,
            timestamp = 1700000000000L
        )

        val json = TransferTransportManager.serializeOffer(originalOffer)
        val deserialized = TransferTransportManager.deserializeOffer(json)

        assertEquals(originalOffer.sessionId, deserialized.sessionId)
        assertEquals(originalOffer.senderDeviceName, deserialized.senderDeviceName)
        assertEquals(originalOffer.senderDeviceId, deserialized.senderDeviceId)
        assertEquals(originalOffer.senderPublicKeyBase64, deserialized.senderPublicKeyBase64)
        assertEquals(originalOffer.hostAddress, deserialized.hostAddress)
        assertEquals(originalOffer.port, deserialized.port)
        assertEquals(originalOffer.timestamp, deserialized.timestamp)
        assertEquals(2, deserialized.files.size)

        assertEquals("photo.jpg", deserialized.files[0].name)
        assertEquals(1024 * 1024 * 3L, deserialized.files[0].sizeBytes)
        assertEquals("image/jpeg", deserialized.files[0].mimeType)
        assertEquals("abcd1234efgh5678", deserialized.files[0].sha256)

        assertEquals("archive.zip", deserialized.files[1].name)
        assertEquals(1024 * 1024 * 10L, deserialized.files[1].sizeBytes)
        assertEquals("application/zip", deserialized.files[1].mimeType)
        assertEquals("1234abcd5678efgh", deserialized.files[1].sha256)
    }

    @Test
    fun testResponseSerializationRoundTrip() {
        val originalResponse = ProximityHandshakeResponse(
            sessionId = "sess_p2p_4455",
            receiverDeviceName = "Galaxy S24 Ultra",
            receiverDeviceId = "tap_device_s24",
            receiverPublicKeyBase64 = "CMk99dj92kd82==",
            accepted = true,
            rejectionReason = null,
            sasCode = "789 012"
        )

        val json = TransferTransportManager.serializeResponse(originalResponse)
        val deserialized = TransferTransportManager.deserializeResponse(json)

        assertEquals(originalResponse.sessionId, deserialized.sessionId)
        assertEquals(originalResponse.receiverDeviceName, deserialized.receiverDeviceName)
        assertEquals(originalResponse.receiverDeviceId, deserialized.receiverDeviceId)
        assertEquals(originalResponse.receiverPublicKeyBase64, deserialized.receiverPublicKeyBase64)
        assertTrue(deserialized.accepted)
        assertEquals("789 012", deserialized.sasCode)
    }

    @Test
    fun testRejectionResponseSerializationRoundTrip() {
        val originalResponse = ProximityHandshakeResponse(
            sessionId = "sess_p2p_4455",
            receiverDeviceName = "Galaxy S24 Ultra",
            receiverDeviceId = "tap_device_s24",
            receiverPublicKeyBase64 = "CMk99dj92kd82==",
            accepted = false,
            rejectionReason = "User rejected proximity transfer.",
            sasCode = "789 012"
        )

        val json = TransferTransportManager.serializeResponse(originalResponse)
        val deserialized = TransferTransportManager.deserializeResponse(json)

        assertFalse(deserialized.accepted)
        assertEquals("User rejected proximity transfer.", deserialized.rejectionReason)
    }
}
