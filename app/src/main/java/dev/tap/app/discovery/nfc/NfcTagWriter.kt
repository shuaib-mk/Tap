package dev.tap.app.discovery.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import dev.tap.app.contacts.ContactCard
import java.io.IOException

/**
 * Universal NFC Tag Writer and Reader.
 *
 * Interacts directly with physical NFC Forum tags (e.g. NTAG213, NTAG215, NTAG216, Mifare).
 * Safely writes NDEF records with capacity checks and handles tag formatting.
 */
object NfcTagWriter {

    sealed class WriteResult {
        data class Success(val bytesWritten: Int, val tagCapacity: Int, val isReadOnly: Boolean) : WriteResult()
        data class Error(val message: String, val cause: Throwable? = null) : WriteResult()
    }

    sealed class TagInfo {
        data class Compatible(val type: String, val capacity: Int, val isWritable: Boolean) : TagInfo()
        data class Unsupported(val reason: String) : TagInfo()
    }

    /**
     * Inspects a physical NFC tag's capabilities and storage capacity.
     */
    fun inspectTag(tag: Tag): TagInfo {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return TagInfo.Compatible(
                type = ndef.type ?: "NDEF Forum Tag",
                capacity = ndef.maxSize,
                isWritable = ndef.isWritable
            )
        }

        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            return TagInfo.Compatible(
                type = "Unformatted Tag (NdefFormatable)",
                capacity = 500, // Estimated standard tag capacity
                isWritable = true
            )
        }

        return TagInfo.Unsupported("Tag does not support standard NDEF or NdefFormatable technology.")
    }

    /**
     * Writes a ContactCard to an NFC tag as an NDEF vCard record.
     * If the tag capacity is too small for full vCard bytes (e.g. NTAG213 with ~144 bytes),
     * automatically falls back to an NDEF URI record (e.g. [fallbackUrl]) which fits easily.
     */
    fun writeContactCard(
        tag: Tag,
        contactCard: ContactCard,
        fallbackUrl: String? = null
    ): WriteResult {
        val fullNdefMessage = contactCard.toNdefMessage(fallbackUri = fallbackUrl, filterSelected = true)
        val mimeOnlyMessage = contactCard.toNdefMessage(fallbackUri = null, filterSelected = true)
        val uriOnlyMessage = if (!fallbackUrl.isNullOrBlank()) {
            NdefMessage(arrayOf(contactCard.toNdefUriRecord(fallbackUrl)))
        } else null

        // Try writing full message first, fallback to mime only, then uri only if tag size is constrained
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return writeToNdef(ndef, fullNdefMessage, mimeOnlyMessage, uriOnlyMessage)
        }

        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            return writeToFormatable(formatable, fullNdefMessage, uriOnlyMessage)
        }

        return WriteResult.Error("Tag does not support NDEF operations. Tag techs: ${tag.techList.joinToString()}")
    }

    /**
     * Writes an arbitrary NDEF message directly to an NFC tag.
     */
    fun writeNdefMessage(tag: Tag, message: NdefMessage): WriteResult {
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                if (!ndef.isWritable) {
                    ndef.close()
                    return WriteResult.Error("NFC tag is permanently read-only or locked.")
                }
                val msgSize = message.byteArrayLength
                if (msgSize > ndef.maxSize) {
                    ndef.close()
                    return WriteResult.Error("NDEF message size ($msgSize bytes) exceeds tag capacity (${ndef.maxSize} bytes).")
                }
                ndef.writeNdefMessage(message)
                val capacity = ndef.maxSize
                ndef.close()
                WriteResult.Success(msgSize, capacity, false)
            } catch (e: TagLostException) {
                WriteResult.Error("Tag lost during write. Hold device steady against the tag.", e)
            } catch (e: FormatException) {
                WriteResult.Error("NDEF formatting error.", e)
            } catch (e: IOException) {
                WriteResult.Error("I/O error communicating with NFC tag: ${e.message}", e)
            }
        }

        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            return try {
                formatable.connect()
                formatable.format(message)
                formatable.close()
                WriteResult.Success(message.byteArrayLength, 500, false)
            } catch (e: Exception) {
                WriteResult.Error("Failed to format NFC tag: ${e.message}", e)
            }
        }

        return WriteResult.Error("NFC tag does not support NDEF writing.")
    }

    private fun writeToNdef(
        ndef: Ndef,
        fullMsg: NdefMessage,
        mimeMsg: NdefMessage,
        uriMsg: NdefMessage?
    ): WriteResult {
        return try {
            ndef.connect()
            if (!ndef.isWritable) {
                ndef.close()
                return WriteResult.Error("NFC tag is permanently locked or read-only.")
            }

            val targetMsg = when {
                fullMsg.byteArrayLength <= ndef.maxSize -> fullMsg
                mimeMsg.byteArrayLength <= ndef.maxSize -> mimeMsg
                uriMsg != null && uriMsg.byteArrayLength <= ndef.maxSize -> uriMsg
                else -> {
                    val required = uriMsg?.byteArrayLength ?: mimeMsg.byteArrayLength
                    ndef.close()
                    return WriteResult.Error("Contact card ($required bytes) is too large for this tag (${ndef.maxSize} bytes). Try selecting fewer fields or using an NDEF URI fallback.")
                }
            }

            ndef.writeNdefMessage(targetMsg)
            val writtenBytes = targetMsg.byteArrayLength
            val capacity = ndef.maxSize
            ndef.close()
            WriteResult.Success(writtenBytes, capacity, !ndef.isWritable)
        } catch (e: TagLostException) {
            WriteResult.Error("Tag moved away too quickly. Hold tag steady against phone.", e)
        } catch (e: FormatException) {
            WriteResult.Error("Invalid NDEF structure: ${e.message}", e)
        } catch (e: IOException) {
            WriteResult.Error("NFC communication error: ${e.message}", e)
        }
    }

    private fun writeToFormatable(
        formatable: NdefFormatable,
        fullMsg: NdefMessage,
        uriMsg: NdefMessage?
    ): WriteResult {
        return try {
            formatable.connect()
            val msgToFormat = if (fullMsg.byteArrayLength < 450) fullMsg else (uriMsg ?: fullMsg)
            formatable.format(msgToFormat)
            val written = msgToFormat.byteArrayLength
            formatable.close()
            WriteResult.Success(written, 500, false)
        } catch (e: Exception) {
            WriteResult.Error("Failed to format and write to NFC tag: ${e.message}", e)
        }
    }

    /**
     * Reads a ContactCard from an NFC tag if it contains an NDEF vCard record.
     */
    fun readContactCard(tag: Tag): Result<ContactCard> {
        val ndef = Ndef.get(tag) ?: return Result.failure(IllegalStateException("Tag is not NDEF compliant."))
        return try {
            ndef.connect()
            val msg = ndef.ndefMessage ?: return Result.failure(IllegalStateException("NDEF tag is empty."))
            ndef.close()

            for (record in msg.records) {
                // Check if MIME text/vcard
                val mimeType = record.toMimeType()
                if (mimeType != null && mimeType.contains("vcard", ignoreCase = true)) {
                    val vcardString = String(record.payload, Charsets.UTF_8)
                    return Result.success(ContactCard.parseVCard(vcardString))
                }

                // Check if payload starts with BEGIN:VCARD
                val rawStr = try { String(record.payload, Charsets.UTF_8) } catch (_: Exception) { "" }
                if (rawStr.contains("BEGIN:VCARD", ignoreCase = true)) {
                    return Result.success(ContactCard.parseVCard(rawStr))
                }
            }

            Result.failure(NoSuchElementException("No vCard record found on NFC tag."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
