package dev.tap.app.transfer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.tap.app.security.TapSecurity
import java.io.File
import java.io.FileOutputStream

data class SelectedFile(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long
) {
    val formattedSize: String get() = formatBytes(sizeBytes)

    companion object {
        fun fromUri(context: Context, uri: Uri): SelectedFile? {
            return try {
                var name: String? = null
                var size: Long = -1L

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) name = cursor.getString(nameIndex)
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                    }
                }

                if (name == null) {
                    name = uri.lastPathSegment ?: "shared_file_${System.currentTimeMillis()}"
                }

                val cleanName = TapSecurity.sanitizeFilename(name!!)
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val cleanMime = TapSecurity.validateMimeType(mime)

                // If size could not be determined via ContentResolver, query stream
                if (size <= 0) {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                        size = it.length
                    }
                }
                if (size <= 0) size = 0L

                SelectedFile(
                    uri = uri,
                    name = cleanName,
                    mimeType = cleanMime,
                    sizeBytes = size
                )
            } catch (e: Exception) {
                null
            }
        }

        fun fromLink(context: Context, url: String): SelectedFile {
            val fileName = "link_${System.currentTimeMillis()}.url"
            val content = "[InternetShortcut]\nURL=$url\n"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { it.write(content.toByteArray()) }

            return SelectedFile(
                uri = Uri.fromFile(file),
                name = fileName,
                mimeType = "text/x-uri",
                sizeBytes = file.length()
            )
        }

        fun fromContact(context: Context, name: String, vcardContent: String): SelectedFile {
            val fileName = "${TapSecurity.sanitizeFilename(name)}.vcf"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { it.write(vcardContent.toByteArray()) }

            return SelectedFile(
                uri = Uri.fromFile(file),
                name = fileName,
                mimeType = "text/x-vcard",
                sizeBytes = file.length()
            )
        }

        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val z = (63 - java.lang.Long.numberOfLeadingZeros(bytes)) / 10
            val value = bytes.toDouble() / (1L shl (z * 10))
            val prefix = " KMGTPE"[z]
            return String.format(java.util.Locale.US, "%.1f %cB", value, prefix)
        }
    }
}
