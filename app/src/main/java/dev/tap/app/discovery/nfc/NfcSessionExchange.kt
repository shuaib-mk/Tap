package dev.tap.app.discovery.nfc

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import dev.tap.app.contacts.ContactCard
import dev.tap.app.protocol.ConnectionMethod
import dev.tap.app.protocol.SessionOffer
import dev.tap.app.protocol.TapProtocol
import java.nio.charset.StandardCharsets

/**
 * NFC Session Bootstrap and Physical Tap Detection for TAP.
 * Transmits ONLY session handshake tokens or digital contact cards (never bulk file payloads).
 * Uses Android Reader Mode for reliable device-to-device tap detection.
 */
object NfcSessionExchange {
    const val TAP_MIME_TYPE = "application/vnd.dev.tap.session"
    const val PROXIMITY_MIME_TYPE = "application/vnd.dev.tap.proximity"

    fun createNdefMessage(offer: SessionOffer): NdefMessage {
        val payload = TapProtocol.json.encodeToString(offer).toByteArray(StandardCharsets.UTF_8)
        val record = NdefRecord.createMime(TAP_MIME_TYPE, payload)
        return NdefMessage(arrayOf(record))
    }

    /**
     * Creates a multi-record NDEF message for Physical Tap to Share:
     * Record 1: Custom MIME `application/vnd.dev.tap.proximity` (full session bootstrap)
     * Record 2: (Optional) NFC Forum URI record for zero-install web browser download or remote join
     * Record 3: Android Application Record (AAR) to prioritize TAP launch
     */
    fun createProximityNdefMessage(offerJson: String, webFallbackUrl: String? = null): NdefMessage {
        val records = mutableListOf<NdefRecord>()

        // Record 1: TAP Proximity MIME
        val mimePayload = offerJson.toByteArray(StandardCharsets.UTF_8)
        records.add(NdefRecord.createMime(PROXIMITY_MIME_TYPE, mimePayload))

        // Record 2: Zero-install Web Fallback URI (for devices without TAP)
        if (!webFallbackUrl.isNullOrBlank()) {
            try {
                records.add(NdefRecord.createUri(webFallbackUrl))
            } catch (_: Exception) {}
        }

        // Record 3: Android Application Record
        try {
            records.add(NdefRecord.createApplicationRecord("dev.tap.app"))
        } catch (_: Exception) {}

        return NdefMessage(records.toTypedArray())
    }

    fun parseProximityNdefMessage(message: NdefMessage): String? {
        return try {
            val record = message.records.firstOrNull { r ->
                val mime = String(r.type, StandardCharsets.US_ASCII)
                mime == PROXIMITY_MIME_TYPE || (mime.contains("proximity") && mime.contains("tap"))
            } ?: return null

            String(record.payload, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun parseNdefMessage(message: NdefMessage): SessionOffer? {
        return try {
            val record = message.records.firstOrNull { r ->
                val mime = String(r.type, StandardCharsets.US_ASCII)
                mime == TAP_MIME_TYPE || mime.contains("tap.session")
            } ?: return null

            val jsonStr = String(record.payload, StandardCharsets.UTF_8)
            TapProtocol.json.decodeFromString<SessionOffer>(jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    fun parseSessionCode(message: NdefMessage): String? {
        return try {
            for (record in message.records) {
                val uri = try { record.toUri()?.toString() } catch (_: Exception) { null }
                if (uri != null && uri.contains("tap.dev/s/", ignoreCase = true)) {
                    val code = uri.substringAfter("tap.dev/s/").substringBefore("?").substringBefore("/")
                    if (code.isNotBlank()) return code.uppercase()
                }

                val payloadStr = try { String(record.payload, StandardCharsets.UTF_8) } catch (_: Exception) { "" }
                if (payloadStr.startsWith("TAP-", ignoreCase = true)) {
                    return payloadStr.trim().uppercase()
                }
                if (payloadStr.contains("\"sessionCode\":\"")) {
                    val code = payloadStr.substringAfter("\"sessionCode\":\"").substringBefore("\"")
                    if (code.isNotBlank()) return code.uppercase()
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun parseVCard(message: NdefMessage): ContactCard? {
        return try {
            for (record in message.records) {
                val mime = try { record.toMimeType() ?: String(record.type, StandardCharsets.US_ASCII) } catch (_: Exception) { "" }
                val payloadStr = try { String(record.payload, StandardCharsets.UTF_8) } catch (_: Exception) { "" }
                if (mime.contains("vcard", ignoreCase = true) || payloadStr.contains("BEGIN:VCARD", ignoreCase = true)) {
                    return ContactCard.parseVCard(payloadStr)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun enableReaderMode(
        activity: Activity,
        onSessionDiscovered: (SessionOffer) -> Unit
    ): Boolean = enableReaderMode(activity, onSessionDiscovered, null, null, null, null)

    fun enableReaderMode(
        activity: Activity,
        onSessionDiscovered: (SessionOffer) -> Unit,
        onTagDiscovered: ((Tag) -> Unit)? = null,
        onProximityDiscovered: ((String) -> Unit)? = null,
        onSessionCodeDiscovered: ((String) -> Unit)? = null,
        onContactCardDiscovered: ((ContactCard) -> Unit)? = null
    ): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return false
        if (!adapter.isEnabled) return false

        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        adapter.enableReaderMode(
            activity,
            { tag: Tag ->
                activity.runOnUiThread {
                    onTagDiscovered?.invoke(tag)
                }

                val ndef = Ndef.get(tag)
                if (ndef != null) {
                    try {
                        ndef.connect()
                        val message = ndef.ndefMessage
                        if (message != null) {
                            // 1. Check for physical tap session offer
                            val proximityJson = parseProximityNdefMessage(message)
                            if (proximityJson != null && onProximityDiscovered != null) {
                                activity.runOnUiThread {
                                    onProximityDiscovered(proximityJson)
                                }
                                return@enableReaderMode
                            }

                            // 2. Check for Anywhere session code link
                            val sessionCode = parseSessionCode(message)
                            if (sessionCode != null && onSessionCodeDiscovered != null) {
                                activity.runOnUiThread {
                                    onSessionCodeDiscovered(sessionCode)
                                }
                                return@enableReaderMode
                            }

                            // 3. Check for Contact Card vCard
                            val card = parseVCard(message)
                            if (card != null && onContactCardDiscovered != null) {
                                activity.runOnUiThread {
                                    onContactCardDiscovered(card)
                                }
                                return@enableReaderMode
                            }

                            // 4. Check for legacy SessionOffer
                            parseNdefMessage(message)?.let { offer ->
                                activity.runOnUiThread {
                                    onSessionDiscovered(offer.copy(connectionMethod = ConnectionMethod.NFC_HCE))
                                }
                            }
                        }
                    } catch (_: Exception) {
                    } finally {
                        try { ndef.close() } catch (_: Exception) {}
                    }
                }
            },
            flags,
            Bundle()
        )
        return true
    }

    fun disableReaderMode(activity: Activity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        adapter?.disableReaderMode(activity)
    }
}
