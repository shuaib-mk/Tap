package dev.tap.app.protocol

import org.junit.Assert.*
import org.junit.Test

class TapProtocolTest {

    @Test
    fun testSessionOfferSerialization() {
        val offer = SessionOffer(
            sessionId = "test-session-12345",
            deviceName = "Galaxy A54 5G",
            publicKeyBase64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...",
            connectionMethod = ConnectionMethod.QR,
            hostAddress = "192.168.1.50",
            port = 8989
        )

        val jsonStr = TapProtocol.json.encodeToString(offer)
        val decoded = TapProtocol.json.decodeFromString<SessionOffer>(jsonStr)

        assertEquals(offer.sessionId, decoded.sessionId)
        assertEquals(offer.deviceName, decoded.deviceName)
        assertEquals(offer.hostAddress, decoded.hostAddress)
        assertEquals(offer.port, decoded.port)
        assertEquals(offer.connectionMethod, decoded.connectionMethod)
    }

    @Test
    fun testQrPayloadEncodingDecoding() {
        val offer = SessionOffer(
            sessionId = "qr-bootstrap-987",
            deviceName = "TAP Sender",
            publicKeyBase64 = "KEY123",
            connectionMethod = ConnectionMethod.QR,
            hostAddress = "10.0.0.15",
            port = 8989
        )

        val payload = encodeToQrPayload(offer)
        val decoded = decodeQrPayload(payload)

        assertNotNull(decoded)
        assertEquals(offer.sessionId, decoded?.sessionId)
        assertEquals(offer.hostAddress, decoded?.hostAddress)
    }

    @Test
    fun testTransferRequestSerialization() {
        val manifest = TransferManifest(
            transferId = "transfer_0",
            fileName = "vacation.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 2048576L,
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        )

        val request = TransferRequest(
            sessionId = "session_abc",
            senderName = "Alice Phone",
            files = listOf(manifest),
            totalBytes = 2048576L
        )

        val jsonStr = TapProtocol.json.encodeToString(request)
        val decoded = TapProtocol.json.decodeFromString<TransferRequest>(jsonStr)

        assertEquals(request.sessionId, decoded.sessionId)
        assertEquals(request.senderName, decoded.senderName)
        assertEquals(1, decoded.files.size)
        assertEquals("vacation.jpg", decoded.files[0].fileName)
    }
}
