package dev.tap.app.nfc

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TapHostApduServiceTest {

    private lateinit var service: TapHostApduService

    @Before
    fun setUp() {
        service = TapHostApduService()
    }

    @Test
    fun testSelectNdefApplication() {
        val selectNdefApdu = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01, 0x00
        )
        val response = service.processCommandApdu(selectNdefApdu, null)
        assertArrayEquals(byteArrayOf(0x90.toByte(), 0x00), response)
    }

    @Test
    fun testSelectCcFile() {
        // First select application
        val selectNdefApdu = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01
        )
        service.processCommandApdu(selectNdefApdu, null)

        // Select CC File (E1 03)
        val selectCcApdu = byteArrayOf(
            0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02,
            0xE1.toByte(), 0x03
        )
        val response = service.processCommandApdu(selectCcApdu, null)
        assertArrayEquals(byteArrayOf(0x90.toByte(), 0x00), response)
    }

    @Test
    fun testReadCcFile() {
        // Select App
        service.processCommandApdu(
            byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07, 0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01),
            null
        )
        // Select CC File
        service.processCommandApdu(
            byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x03),
            null
        )
        // Read CC File: Offset = 0, Length = 15 (0x0F)
        val readCcApdu = byteArrayOf(
            0x00, 0xB0.toByte(), 0x00, 0x00, 0x0F
        )
        val response = service.processCommandApdu(readCcApdu, null)
        assertEquals(17, response.size) // 15 bytes data + 2 bytes status (90 00)
        assertEquals(0x90.toByte(), response[15])
        assertEquals(0x00.toByte(), response[16])

        // Verify CC Structure
        assertEquals(0x00.toByte(), response[0]) // CCLEN high
        assertEquals(0x0F.toByte(), response[1]) // CCLEN low (15 bytes)
        assertEquals(0x20.toByte(), response[2]) // Mapping Version 2.0
        assertEquals(0x00.toByte(), response[3]) // MLe high
        assertEquals(0x7F.toByte(), response[4]) // MLe low (127 bytes)
        assertEquals(0x04.toByte(), response[7]) // NDEF TLV Tag
        assertEquals(0x06.toByte(), response[8]) // TLV Length
        assertEquals(0xE1.toByte(), response[9]) // NDEF File ID high
        assertEquals(0x04.toByte(), response[10]) // NDEF File ID low
    }

    @Test
    fun testSelectNdefFileAndReadNlen() {
        service.processCommandApdu(
            byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07, 0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01),
            null
        )

        // Select NDEF File (E1 04)
        val selectNdefFileApdu = byteArrayOf(
            0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02,
            0xE1.toByte(), 0x04
        )
        val selectResp = service.processCommandApdu(selectNdefFileApdu, null)
        assertArrayEquals(byteArrayOf(0x90.toByte(), 0x00), selectResp)

        // Read 2-byte NLEN
        val readNlenApdu = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x02)
        val nlenResp = service.processCommandApdu(readNlenApdu, null)
        assertEquals(4, nlenResp.size) // 2 bytes NLEN + 2 bytes status (90 00)
        assertEquals(0x90.toByte(), nlenResp[2])
        assertEquals(0x00.toByte(), nlenResp[3])
        val nlen = ((nlenResp[0].toInt() and 0xFF) shl 8) or (nlenResp[1].toInt() and 0xFF)
        assertTrue("NDEF length must be greater than 0", nlen > 0)
    }

    @Test
    fun testSelectProprietaryTapAid() {
        val tapAidApdu = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x08,
            0xF0.toByte(), 0x4E, 0x46, 0x43, 0x54, 0x41, 0x50, 0x00
        )
        val response = service.processCommandApdu(tapAidApdu, null)
        // "TAP" (0x54, 0x41, 0x50) + v1 (0x01) + 90 00
        val expected = byteArrayOf(0x54, 0x41, 0x50, 0x01, 0x90.toByte(), 0x00)
        assertArrayEquals(expected, response)
    }

    @Test
    fun testBackgroundSharingDisabledReturnsFileNotFound() {
        service.isSharingEnabledOverride = false
        val selectNdefApdu = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01
        )
        val response = service.processCommandApdu(selectNdefApdu, null)
        assertArrayEquals(byteArrayOf(0x6A, 0x82.toByte()), response)
    }

    @Test
    fun testUnknownAidReturnsFileNotFound() {
        val unknownAidApdu = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x07,
            0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77
        )
        val response = service.processCommandApdu(unknownAidApdu, null)
        assertArrayEquals(byteArrayOf(0x6A, 0x82.toByte()), response)
    }

    @Test
    fun testProximityNdefPayloadServedWhenActive() {
        val customPayload = "TAP_PROXIMITY_NDEF_PAYLOAD".toByteArray(Charsets.UTF_8)
        dev.tap.app.proximity.ProximityNdefRepository.setProximitySession("session_apdu_1", customPayload)

        try {
            // Select App
            service.processCommandApdu(
                byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07, 0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01),
                null
            )
            // Select NDEF File (E1 04)
            service.processCommandApdu(
                byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x04),
                null
            )
            // Read first 2 bytes (NLEN)
            val readNlenApdu = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x02)
            val nlenResponse = service.processCommandApdu(readNlenApdu, null)

            val expectedNlen = customPayload.size
            assertEquals(((expectedNlen shr 8) and 0xFF).toByte(), nlenResponse[0])
            assertEquals((expectedNlen and 0xFF).toByte(), nlenResponse[1])
            assertEquals(0x90.toByte(), nlenResponse[2])
            assertEquals(0x00.toByte(), nlenResponse[3])

            // Read the custom payload
            val readPayloadApdu = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x02, customPayload.size.toByte())
            val payloadResponse = service.processCommandApdu(readPayloadApdu, null)
            val extracted = payloadResponse.copyOfRange(0, customPayload.size)
            assertArrayEquals(customPayload, extracted)
        } finally {
            dev.tap.app.proximity.ProximityNdefRepository.clearProximitySession()
        }
    }
}
