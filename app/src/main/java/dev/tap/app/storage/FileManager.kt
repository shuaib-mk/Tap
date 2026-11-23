package dev.tap.app.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dev.tap.app.security.TapSecurity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Handles safe file storage:
 * - Scoped storage compliance (Android 10+)
 * - MediaStore routing by MIME type (Images, Videos, Downloads)
 * - Safe temp file isolation in app-controlled cache
 * - Atomic commit upon SHA-256 verification
 * - Automatic cleanup of abandoned/cancelled transfers
 */
object FileManager {

    fun createTempFile(context: Context, fileName: String): File {
        val safeName = TapSecurity.sanitizeFilename(fileName)
        val tempDir = File(context.cacheDir, "tap_incoming").apply { mkdirs() }
        return File(tempDir, "tmp_${System.currentTimeMillis()}_$safeName")
    }

    fun cleanupTempFiles(context: Context) {
        try {
            val tempDir = File(context.cacheDir, "tap_incoming")
            if (tempDir.exists() && tempDir.isDirectory) {
                tempDir.listFiles()?.forEach { it.delete() }
            }
        } catch (_: Exception) {}
    }

    fun saveVerifiedFile(
        context: Context,
        tempFile: File,
        targetFileName: String,
        mimeType: String
    ): Uri? {
        val safeName = TapSecurity.sanitizeFilename(targetFileName)
        val cleanMime = TapSecurity.validateMimeType(mimeType)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, tempFile, safeName, cleanMime)
        } else {
            saveViaLegacyStorage(tempFile, safeName, cleanMime)
        }
    }

    private fun saveViaMediaStore(
        context: Context,
        tempFile: File,
        fileName: String,
        mimeType: String
    ): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val targetCollection = when {
            mimeType.startsWith("image/") -> {
                contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/TAP")
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            mimeType.startsWith("video/") -> {
                contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/TAP")
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            mimeType.startsWith("audio/") -> {
                contentValues.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/TAP")
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            else -> {
                contentValues.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TAP")
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }
        }

        val uri = resolver.insert(targetCollection, contentValues) ?: return null

        try {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(tempFile).use { input ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                }
            }
            // Mark pending = 0 so other apps can access the completed file
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)

            // Remove temp file
            tempFile.delete()
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            tempFile.delete()
            return null
        }
    }

    private fun saveViaLegacyStorage(
        tempFile: File,
        fileName: String,
        mimeType: String
    ): Uri? {
        val baseDir = when {
            mimeType.startsWith("image/") -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            mimeType.startsWith("video/") -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            else -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }
        val tapDir = File(baseDir, "TAP").apply { mkdirs() }
        var destFile = File(tapDir, fileName)

        // Ensure non-collision
        var counter = 1
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        while (destFile.exists()) {
            destFile = File(tapDir, "${base}_$counter$ext")
            counter++
        }

        try {
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()
            return Uri.fromFile(destFile)
        } catch (e: Exception) {
            tempFile.delete()
            return null
        }
    }

    fun openInputStream(context: Context, uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }
}
