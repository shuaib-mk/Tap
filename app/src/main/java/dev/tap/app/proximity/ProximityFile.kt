package dev.tap.app.proximity

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

enum class FileCategory {
    PHOTO,
    VIDEO,
    AUDIO,
    DOCUMENT,
    ARCHIVE,
    ARBITRARY
}

/**
 * File metadata model for Proximity File Sharing.
 * Supports Photos, Videos, Audio, PDFs, Documents, Archives, and Arbitrary files.
 */
data class ProximityFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val category: FileCategory
) {
    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            val clamped = digitGroups.coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, clamped.toDouble())
            return String.format("%.2f %s", value, units[clamped])
        }

        fun detectCategory(mimeType: String, filename: String): FileCategory {
            val mime = mimeType.lowercase()
            val ext = filename.substringAfterLast('.', "").lowercase()

            return when {
                mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "dng") -> FileCategory.PHOTO
                mime.startsWith("video/") || ext in listOf("mp4", "mkv", "mov", "avi", "webm", "3gp") -> FileCategory.VIDEO
                mime.startsWith("audio/") || ext in listOf("mp3", "m4a", "flac", "wav", "aac", "ogg") -> FileCategory.AUDIO
                mime.contains("pdf") || ext == "pdf" -> FileCategory.DOCUMENT
                mime.contains("zip") || mime.contains("tar") || mime.contains("gzip") || ext in listOf("zip", "rar", "7z", "tar", "gz") -> FileCategory.ARCHIVE
                mime.startsWith("text/") || ext in listOf("txt", "md", "csv", "doc", "docx", "xls", "xlsx", "ppt", "pptx") -> FileCategory.DOCUMENT
                else -> FileCategory.ARBITRARY
            }
        }

        fun fromUri(context: Context, uri: Uri): ProximityFile? {
            return try {
                var name = "file_${System.currentTimeMillis()}"
                var size: Long = 0
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) name = cursor.getString(nameIdx) ?: name
                        if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                    }
                }

                if (size == 0L) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            size = stream.available().toLong()
                        }
                    } catch (_: Exception) {}
                }

                ProximityFile(
                    uri = uri,
                    name = name,
                    sizeBytes = size,
                    mimeType = mimeType,
                    category = detectCategory(mimeType, name)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
