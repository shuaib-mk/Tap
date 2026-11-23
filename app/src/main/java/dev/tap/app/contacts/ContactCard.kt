package dev.tap.app.contacts

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Universal Digital Contact Card.
 *
 * Implements standards-compliant vCard 3.0 (RFC 2426) and vCard 4.0 (RFC 6350)
 * formatting, NDEF MIME/URI record generation for physical NFC tags and HCE emulation,
 * and granular field-level privacy controls.
 */
data class ContactCard(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String = "",
    val jobTitle: String = "",
    val company: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val notes: String = "",
    val socialLinks: Map<String, String> = emptyMap(),
    val selectedFields: Set<String> = setOf(
        FIELD_DISPLAY_NAME,
        FIELD_JOB_TITLE,
        FIELD_COMPANY,
        FIELD_PHONE,
        FIELD_EMAIL,
        FIELD_WEBSITE,
        FIELD_NOTES,
        FIELD_SOCIAL
    )
) {
    companion object {
        const val FIELD_DISPLAY_NAME = "displayName"
        const val FIELD_JOB_TITLE = "jobTitle"
        const val FIELD_COMPANY = "company"
        const val FIELD_PHONE = "phone"
        const val FIELD_EMAIL = "email"
        const val FIELD_WEBSITE = "website"
        const val FIELD_NOTES = "notes"
        const val FIELD_SOCIAL = "social"

        const val MIME_TYPE_VCARD = "text/vcard"

        fun createDefault(): ContactCard {
            return ContactCard(
                displayName = "Alex Morgan",
                jobTitle = "Product Architect",
                company = "TAP Technologies",
                phone = "+1 (555) 234-5678",
                email = "alex.morgan@tap-protocol.dev",
                website = "https://tap-protocol.dev",
                notes = "Shared instantly via TAP Universal Contact Card",
                socialLinks = mapOf(
                    "GitHub" to "https://github.com/shuaib-mk/Tap",
                    "LinkedIn" to "https://linkedin.com/in/alexmorgan"
                )
            )
        }

        /**
         * Parses a standard vCard string (vCard 2.1, 3.0, or 4.0) into a ContactCard.
         */
        fun parseVCard(vcardString: String): ContactCard {
            var name = ""
            var title = ""
            var org = ""
            var phone = ""
            var email = ""
            var web = ""
            var note = ""
            val social = mutableMapOf<String, String>()

            vcardString.lines().forEach { rawLine ->
                val line = rawLine.trim()
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val keyPart = line.substring(0, colonIdx).uppercase()
                    val value = line.substring(colonIdx + 1).trim()
                    when {
                        keyPart == "FN" || keyPart.startsWith("FN;") -> {
                            if (name.isBlank()) name = value
                        }
                        (keyPart == "N" || keyPart.startsWith("N;")) && name.isBlank() -> {
                            // "LastName;FirstName;..."
                            val parts = value.split(";").filter { it.isNotBlank() }
                            name = parts.reversed().joinToString(" ")
                        }
                        keyPart == "TITLE" || keyPart.startsWith("TITLE;") -> title = value
                        keyPart == "ORG" || keyPart.startsWith("ORG;") -> org = value.replace(";", " ").trim()
                        keyPart == "TEL" || keyPart.startsWith("TEL;") -> {
                            if (phone.isBlank()) phone = value.removePrefix("tel:")
                        }
                        keyPart == "EMAIL" || keyPart.startsWith("EMAIL;") -> {
                            if (email.isBlank()) email = value
                        }
                        keyPart == "URL" || keyPart.startsWith("URL;") -> {
                            if (web.isBlank()) web = value
                        }
                        keyPart == "NOTE" || keyPart.startsWith("NOTE;") -> note = value
                        keyPart.startsWith("X-SOCIALPROFILE") || keyPart.startsWith("X-SERVICE-") -> {
                            val svc = if (keyPart.contains("linkedin", ignoreCase = true)) "LinkedIn"
                            else if (keyPart.contains("github", ignoreCase = true)) "GitHub"
                            else if (keyPart.contains("twitter", ignoreCase = true) || keyPart.contains("x.com", ignoreCase = true)) "X"
                            else "Social"
                            social[svc] = value
                        }
                    }
                }
            }

            return ContactCard(
                displayName = name,
                jobTitle = title,
                company = org,
                phone = phone,
                email = email,
                website = web,
                notes = note,
                socialLinks = social
            )
        }
    }

    /**
     * Generates a vCard 3.0 format string adhering to RFC 2426.
     * Compatible with iOS Contacts, Android Contacts, macOS, Windows, and Outlook.
     */
    fun toVCard3(filterSelected: Boolean = true): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCARD\r\n")
        sb.append("VERSION:3.0\r\n")

        if (!filterSelected || selectedFields.contains(FIELD_DISPLAY_NAME)) {
            if (displayName.isNotBlank()) {
                sb.append("FN:$displayName\r\n")
                val parts = displayName.split(" ")
                if (parts.size >= 2) {
                    sb.append("N:${parts.last()};${parts.first()};;;\r\n")
                } else {
                    sb.append("N:$displayName;;;;\r\n")
                }
            }
        }

        if (!filterSelected || selectedFields.contains(FIELD_COMPANY)) {
            if (company.isNotBlank()) {
                sb.append("ORG:$company\r\n")
            }
        }

        if (!filterSelected || selectedFields.contains(FIELD_JOB_TITLE)) {
            if (jobTitle.isNotBlank()) {
                sb.append("TITLE:$jobTitle\r\n")
            }
        }

        if (!filterSelected || selectedFields.contains(FIELD_PHONE)) {
            if (phone.isNotBlank()) {
                sb.append("TEL;TYPE=CELL,VOICE:$phone\r\n")
            }
        }

        if (!filterSelected || selectedFields.contains(FIELD_EMAIL)) {
            if (email.isNotBlank()) {
                sb.append("EMAIL;TYPE=INTERNET,PREF:$email\r\n")
            }
        }

        if (!filterSelected || selectedFields.contains(FIELD_WEBSITE)) {
            if (website.isNotBlank()) {
                sb.append("URL:$website\r\n")
            }
        }

        if (!filterSelected || selectedFields.contains(FIELD_SOCIAL)) {
            socialLinks.forEach { (platform, url) ->
                if (url.isNotBlank()) {
                    sb.append("X-SOCIALPROFILE;TYPE=${platform.lowercase()}:$url\r\n")
                }
            }
        }

        if (!filterSelected || selectedFields.contains(FIELD_NOTES)) {
            if (notes.isNotBlank()) {
                sb.append("NOTE:$notes\r\n")
            }
        }

        sb.append("END:VCARD\r\n")
        return sb.toString()
    }

    /**
     * Generates a vCard 4.0 format string adhering to RFC 6350.
     */
    fun toVCard4(filterSelected: Boolean = true): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCARD\r\n")
        sb.append("VERSION:4.0\r\n")

        if (!filterSelected || selectedFields.contains(FIELD_DISPLAY_NAME)) {
            if (displayName.isNotBlank()) {
                sb.append("FN:$displayName\r\n")
                sb.append("N:$displayName;;;;\r\n")
            }
        }

        if (!filterSelected || selectedFields.contains(FIELD_COMPANY)) {
            if (company.isNotBlank()) sb.append("ORG:$company\r\n")
        }

        if (!filterSelected || selectedFields.contains(FIELD_JOB_TITLE)) {
            if (jobTitle.isNotBlank()) sb.append("TITLE:$jobTitle\r\n")
        }

        if (!filterSelected || selectedFields.contains(FIELD_PHONE)) {
            if (phone.isNotBlank()) sb.append("TEL;TYPE=cell,voice;VALUE=uri:tel:$phone\r\n")
        }

        if (!filterSelected || selectedFields.contains(FIELD_EMAIL)) {
            if (email.isNotBlank()) sb.append("EMAIL;TYPE=work:$email\r\n")
        }

        if (!filterSelected || selectedFields.contains(FIELD_WEBSITE)) {
            if (website.isNotBlank()) sb.append("URL:$website\r\n")
        }

        if (!filterSelected || selectedFields.contains(FIELD_NOTES)) {
            if (notes.isNotBlank()) sb.append("NOTE:$notes\r\n")
        }

        sb.append("END:VCARD\r\n")
        return sb.toString()
    }

    /**
     * Builds an NDEF MIME record with MIME type "text/vcard".
     * Standardized NDEF record compatible with NFC Forum specifications.
     */
    fun toNdefMimeRecord(filterSelected: Boolean = true): NdefRecord {
        val vcardBytes = toVCard3(filterSelected).toByteArray(StandardCharsets.UTF_8)
        return NdefRecord.createMime(MIME_TYPE_VCARD, vcardBytes)
    }

    /**
     * Builds an NDEF URI record pointing to a web profile or local HTTP download URL.
     * Compact record (<50 bytes) ideal for low-capacity NFC tags (e.g. NTAG213).
     */
    fun toNdefUriRecord(url: String): NdefRecord {
        return NdefRecord.createUri(url)
    }

    /**
     * Builds an NdefMessage.
     * If [includeFallbackUri] is provided, includes both the URI record and MIME record.
     */
    fun toNdefMessage(fallbackUri: String? = null, filterSelected: Boolean = true): NdefMessage {
        val records = mutableListOf<NdefRecord>()
        
        // Priority 1: If fallbackUri provided, place URI record first so generic NFC phones / iOS open the web card directly
        if (!fallbackUri.isNullOrBlank()) {
            records.add(toNdefUriRecord(fallbackUri))
        }
        
        // Priority 2: Standard vCard MIME record
        records.add(toNdefMimeRecord(filterSelected))

        return NdefMessage(records.toTypedArray())
    }

    fun isFieldSelected(field: String): Boolean = selectedFields.contains(field)

    fun toggleField(field: String): ContactCard {
        val updated = selectedFields.toMutableSet()
        if (updated.contains(field)) {
            updated.remove(field)
        } else {
            updated.add(field)
        }
        return copy(selectedFields = updated)
    }
}
