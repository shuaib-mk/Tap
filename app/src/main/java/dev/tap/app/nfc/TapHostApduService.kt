package dev.tap.app.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import dev.tap.app.contacts.ContactCardRepository

/**
 * Universal HCE Endpoint for TAP.
 *
 * Implements two distinct NFC HCE profiles:
 * 1. Proprietary TAP-to-TAP Handshake (AID: F04E464354415000)
 * 2. Standard NFC Forum Type 4 Tag Emulation (AID: D2760000850101)
 *
 * Standard NFC readers and compatible phones can read the active NDEF Contact Card
 * directly via Type 4 Tag emulation.
 */
class TapHostApduService : HostApduService() {

    private enum class SelectedFile {
        NONE, CC_FILE, NDEF_FILE
    }

    private var currentFile = SelectedFile.NONE
    private var ndefFilePayload: ByteArray = byteArrayOf(0x00, 0x00) // Empty NDEF file

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.size < 4) return STATUS_FAILED

        val cla = commandApdu[0].toInt() and 0xFF
        val ins = commandApdu[1].toInt() and 0xFF
        val p1 = commandApdu[2].toInt() and 0xFF
        val p2 = commandApdu[3].toInt() and 0xFF

        // 1. SELECT command (INS = 0xA4)
        if (cla == 0x00 && ins == 0xA4) {
            // Select by AID (P1 = 0x04)
            if (p1 == 0x04) {
                // Check TAP AID
                if (commandApdu.contentEquals(SELECT_TAP_AID)) {
                    currentFile = SelectedFile.NONE
                    return byteArrayOf(0x54, 0x41, 0x50, 0x01, 0x90.toByte(), 0x00) // "TAP" + v1 + 9000
                }
                // Check NFC Forum Type 4 Tag AID
                if (matchesAid(commandApdu, NDEF_TYPE4_AID)) {
                    currentFile = SelectedFile.NONE
                    prepareNdefPayload()
                    return STATUS_SUCCESS
                }
            }

            // Select by File ID (P1 = 0x00)
            if (p1 == 0x00) {
                if (commandApdu.size >= 7) {
                    val fileIdHigh = commandApdu[5].toInt() and 0xFF
                    val fileIdLow = commandApdu[6].toInt() and 0xFF

                    // CC File: E1 03
                    if (fileIdHigh == 0xE1 && fileIdLow == 0x03) {
                        currentFile = SelectedFile.CC_FILE
                        return STATUS_SUCCESS
                    }

                    // NDEF File: E1 04
                    if (fileIdHigh == 0xE1 && fileIdLow == 0x04) {
                        currentFile = SelectedFile.NDEF_FILE
                        return STATUS_SUCCESS
                    }
                }
            }

            return STATUS_FILE_NOT_FOUND
        }

        // 2. READ BINARY command (INS = 0xB0)
        if (cla == 0x00 && ins == 0xB0) {
            val offset = (p1 shl 8) or p2
            val length = if (commandApdu.size >= 5) commandApdu[4].toInt() and 0xFF else 0

            return when (currentFile) {
                SelectedFile.CC_FILE -> {
                    readFromBuffer(CAPABILITY_CONTAINER, offset, length)
                }
                SelectedFile.NDEF_FILE -> {
                    readFromBuffer(ndefFilePayload, offset, length)
                }
                SelectedFile.NONE -> STATUS_FAILED
            }
        }

        return STATUS_FAILED
    }

    private fun prepareNdefPayload() {
        try {
            val repo = ContactCardRepository.getInstance(applicationContext)
            val card = repo.getContactCard()
            val ndefMsg = card.toNdefMessage(fallbackUri = null, filterSelected = true)
            val msgBytes = ndefMsg.toByteArray()

            // Type 4 NDEF file layout: 2 bytes NLEN (big-endian length) + NDEF message
            val nlen = msgBytes.size
            ndefFilePayload = ByteArray(2 + nlen)
            ndefFilePayload[0] = ((nlen shr 8) and 0xFF).toByte()
            ndefFilePayload[1] = (nlen and 0xFF).toByte()
            System.arraycopy(msgBytes, 0, ndefFilePayload, 2, nlen)
        } catch (_: Exception) {
            ndefFilePayload = byteArrayOf(0x00, 0x00)
        }
    }

    private fun readFromBuffer(buffer: ByteArray, offset: Int, length: Int): ByteArray {
        if (offset >= buffer.size) {
            return STATUS_FAILED
        }
        val bytesToRead = if (length == 0 || offset + length > buffer.size) {
            buffer.size - offset
        } else {
            length
        }

        val result = ByteArray(bytesToRead + 2)
        System.arraycopy(buffer, offset, result, 0, bytesToRead)
        result[bytesToRead] = 0x90.toByte()
        result[bytesToRead + 1] = 0x00
        return result
    }

    private fun matchesAid(apdu: ByteArray, targetAid: ByteArray): Boolean {
        if (apdu.size < 5 + targetAid.size) return false
        val lc = apdu[4].toInt() and 0xFF
        if (lc < targetAid.size) return false
        for (i in targetAid.indices) {
            if (apdu[5 + i] != targetAid[i]) return false
        }
        return true
    }

    override fun onDeactivated(reason: Int) {
        currentFile = SelectedFile.NONE
    }

    companion object {
        // Proprietary TAP AID
        private val SELECT_TAP_AID = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x08,
            0xF0.toByte(), 0x4E, 0x46, 0x43, 0x54, 0x41, 0x50, 0x00
        )

        // NFC Forum Type 4 Tag Application AID (D2760000850101)
        private val NDEF_TYPE4_AID = byteArrayOf(
            0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01
        )

        // NFC Forum Type 4 Tag Capability Container (CC File)
        private val CAPABILITY_CONTAINER = byteArrayOf(
            0x00, 0x0F, // CCLEN: 15 bytes
            0x20,       // Mapping Version: 2.0
            0x00, 0x7F, // MLe: 127 bytes max read
            0x00, 0x7F, // MLc: 127 bytes max write
            0x04,       // NDEF File Control TLV Tag
            0x06,       // TLV Length: 6 bytes
            0xE1.toByte(), 0x04, // NDEF File Identifier: E104
            0x08, 0x00, // Max NDEF File Size: 2048 bytes
            0x00,       // Read access: No security
            0xFF.toByte() // Write access: Read-only
        )

        private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00)
        private val STATUS_FAILED = byteArrayOf(0x6F, 0x00)
        private val STATUS_FILE_NOT_FOUND = byteArrayOf(0x6A, 0x82.toByte())
    }
}
