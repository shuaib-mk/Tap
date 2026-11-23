package dev.tap.app.webrtc

import dev.tap.app.webrtc.model.FileManifestDto
import dev.tap.app.webrtc.model.SignalingPayload
import dev.tap.app.webrtc.model.SignalingType
import dev.tap.app.webrtc.model.TurnServerConfig
import dev.tap.app.webrtc.transfer.WebRtcChunkTransport
import dev.tap.app.webrtc.turn.TurnConfigProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import kotlin.random.Random

class WebRtcAnywhereTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ---------------------------------------------------------------------------------------------
    // 1. SIGNALING CLIENT & PAYLOAD TESTS
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testShortSessionCodeFormat() {
        val num = Random.nextInt(1000, 9999)
        val code = "TAP-$num"
        
        // Expected format: TAP-XXXX (4 digits)
        assertTrue("Session code should start with TAP-", code.startsWith("TAP-"))
        assertEquals("Session code total length should be 8", 8, code.length)
        
        val suffix = code.substring(4)
        assertTrue("Suffix must be 4 digits", suffix.matches(Regex("^[0-9]{4}$")))
    }

    @Test
    fun testSignalingPayloadSerialization() {
        val payload = SignalingPayload(
            type = SignalingType.SDP_OFFER,
            sessionId = "test-session-123",
            sessionCode = "TAP-4829",
            deviceName = "Galaxy A54",
            sdp = "v=0\r\no=- 12345 2 IN IP4 127.0.0.1...",
            totalBytes = 1048576L
        )

        val jsonStr = json.encodeToString(payload)
        assertTrue("JSON should contain SDP_OFFER", jsonStr.contains("SDP_OFFER"))
        assertTrue("JSON should contain sessionCode", jsonStr.contains("TAP-4829"))
        assertTrue("JSON should contain deviceName", jsonStr.contains("Galaxy A54"))

        val restored = json.decodeFromString<SignalingPayload>(jsonStr)
        assertEquals(payload.type, restored.type)
        assertEquals(payload.sessionId, restored.sessionId)
        assertEquals(payload.sessionCode, restored.sessionCode)
        assertEquals(payload.deviceName, restored.deviceName)
        assertEquals(payload.sdp, restored.sdp)
        assertEquals(payload.totalBytes, restored.totalBytes)
    }

    @Test
    fun testTransferManifestPayloadSerialization() {
        val files = listOf(
            FileManifestDto(
                name = "photo_sunset.jpg",
                sizeBytes = 2450123L,
                mimeType = "image/jpeg",
                sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            ),
            FileManifestDto(
                name = "contract.pdf",
                sizeBytes = 512000L,
                mimeType = "application/pdf",
                sha256 = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb"
            )
        )

        val payload = SignalingPayload(
            type = SignalingType.TRANSFER_MANIFEST,
            sessionId = "sess_manifest_test",
            sessionCode = "TAP-7721",
            deviceName = "Pixel 8 Pro",
            files = files,
            totalBytes = files.sumOf { it.sizeBytes },
            sasCode = "948210"
        )

        val encoded = json.encodeToString(payload)
        val decoded = json.decodeFromString<SignalingPayload>(encoded)

        assertEquals(2, decoded.files.size)
        assertEquals("photo_sunset.jpg", decoded.files[0].name)
        assertEquals(2450123L, decoded.files[0].sizeBytes)
        assertEquals("contract.pdf", decoded.files[1].name)
        assertEquals(512000L, decoded.files[1].sizeBytes)
        assertEquals("948210", decoded.sasCode)
    }

    // ---------------------------------------------------------------------------------------------
    // 2. TURN REST API & COTURN CONFIGURATION TESTS
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testTurnEphemeralCredentialGeneration() {
        val secret = "super-secret-turn-token-tap-2026"
        val username = "tap-user-device-1"
        val expiryTtlSec = 3600L

        val (authUsername, password) = TurnConfigProvider.generateEphemeralCredentials(
            secret = secret,
            identity = username,
            ttlSeconds = expiryTtlSec
        )

        assertTrue("Generated username should contain timestamp colon", authUsername.contains(":"))
        val parts = authUsername.split(":")
        assertEquals(2, parts.size)
        val timestamp = parts[0].toLong()
        assertTrue("Timestamp must be in the future", timestamp > System.currentTimeMillis() / 1000)
        assertEquals(username, parts[1])
        assertTrue("Password must be non-empty HMAC Base64", password.isNotBlank())
    }

    @Test
    fun testTurnServerConfigModel() {
        val config = TurnServerConfig(
            uri = "turn:turn.tap.dev:3478?transport=udp",
            username = "tapuser",
            credential = "authPassword123"
        )

        assertEquals("turn:turn.tap.dev:3478?transport=udp", config.uri)
        assertEquals("tapuser", config.username)
        assertEquals("authPassword123", config.credential)
    }

    // ---------------------------------------------------------------------------------------------
    // 3. CHUNK TRANSPORT & SHA-256 STREAMING TESTS
    // ---------------------------------------------------------------------------------------------

    @Test
    fun testChunkFrameHeaderEncodingAndDecoding() {
        val fileIndex = 3
        val sequenceNumber = 142L
        val totalChunks = 500L
        val mockData = "TAP-CHUNK-PAYLOAD-TEST-BYTES-1234567890".toByteArray(Charsets.UTF_8)

        val encodedFrame = WebRtcChunkTransport.encodeChunkFrame(
            fileIndex = fileIndex,
            chunkIndex = sequenceNumber,
            totalChunks = totalChunks,
            payload = mockData
        )

        // Header size: 1 byte (FRAME_CHUNK) + 4 bytes (fileIndex) + 8 bytes (chunkIndex) + 8 bytes (totalChunks) + 4 bytes (length) = 25 bytes
        assertEquals(25 + mockData.size, encodedFrame.size)
        assertEquals(WebRtcChunkTransport.FRAME_CHUNK, encodedFrame[0])

        val parsed = WebRtcChunkTransport.decodeChunkFrame(encodedFrame)
        assertNotNull("Frame should decode successfully", parsed)
        assertEquals(fileIndex, parsed!!.fileIndex)
        assertEquals(sequenceNumber, parsed.chunkIndex)
        assertEquals(totalChunks, parsed.totalChunks)
        assertArrayEquals(mockData, parsed.data)
    }

    @Test
    fun testStreamingSha256Verification() {
        val testData = "The quick brown fox jumps over the lazy dog. TAP Universal P2P Sharing 2026."
            .toByteArray(Charsets.UTF_8)

        val md = MessageDigest.getInstance("SHA-256")
        val expectedHashHex = md.digest(testData).joinToString("") { "%02x".format(it) }

        // Feed chunk by chunk (e.g. 10-byte slices) to simulate 64 KB chunk verification
        val streamVerifier = WebRtcChunkTransport.StreamingSha256()
        val chunkStream = ByteArrayInputStream(testData)
        val buffer = ByteArray(10)
        var bytesRead: Int
        while (chunkStream.read(buffer).also { bytesRead = it } != -1) {
            streamVerifier.update(buffer, 0, bytesRead)
        }

        val calculatedHashHex = streamVerifier.digestHex()
        assertEquals("Calculated streaming SHA-256 must match expected hash", expectedHashHex, calculatedHashHex)
    }
}
