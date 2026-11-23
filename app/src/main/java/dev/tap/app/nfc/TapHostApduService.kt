package dev.tap.app.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Minimal HCE endpoint for the TAP proof-of-concept.
 *
 * The first protocol milestone is deliberately tiny: a reader selects the TAP AID,
 * sends SELECT, and receives a protocol-version response. Do not put file data,
 * secrets, or long-lived credentials in this APDU exchange.
 */
class TapHostApduService : HostApduService() {
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return STATUS_FAILED
        return if (commandApdu.contentEquals(SELECT_TAP_AID)) {
            // "TAP" + protocol version 1 + success status word.
            byteArrayOf(0x54, 0x41, 0x50, 0x01, 0x90.toByte(), 0x00)
        } else {
            STATUS_FAILED
        }
    }

    override fun onDeactivated(reason: Int) = Unit

    companion object {
        private val SELECT_TAP_AID = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x08,
            0xF0.toByte(), 0x4E, 0x46, 0x43, 0x54, 0x41, 0x50, 0x00
        )
        private val STATUS_FAILED = byteArrayOf(0x6F, 0x00)
    }
}
