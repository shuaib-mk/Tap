package dev.tap.app.native

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dev.tap.app.contacts.ContactCard
import java.io.File
import java.io.FileOutputStream

/**
 * Native Sharing Adapter.
 *
 * Bridges TAP with native OS sharing mechanisms:
 * - Android Sharesheet (Intent.ACTION_SEND / ACTION_SEND_MULTIPLE)
 * - Quick Share / Nearby Share system handoff
 * - Direct vCard file export
 */
object NativeSharingAdapter {

    /**
     * Shares a single file via the system Sharesheet.
     */
    fun shareFile(context: Context, uri: Uri, mimeType: String = "*/*", title: String = "Share File via TAP") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Shares multiple files via the system Sharesheet.
     */
    fun shareFiles(context: Context, uris: ArrayList<Uri>, title: String = "Share Files via TAP") {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Exports a ContactCard to a standard .vcf file and opens the system Sharesheet.
     * Receivers on iOS, Android, macOS, or Windows can import the vCard directly.
     */
    fun shareContactCard(context: Context, card: ContactCard): Result<Unit> {
        return try {
            val vcardContent = card.toVCard3(filterSelected = true)
            val cacheDir = File(context.cacheDir, "shared_contacts")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val sanitizedName = if (card.displayName.isNotBlank()) {
                card.displayName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            } else "contact"
            val vcfFile = File(cacheDir, "$sanitizedName.vcf")

            FileOutputStream(vcfFile).use { out ->
                out.write(vcardContent.toByteArray(Charsets.UTF_8))
            }

            // Using Uri.fromFile for cache file or content Uri if configured
            val uri = try {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", vcfFile)
            } catch (_: Exception) {
                Uri.fromFile(vcfFile)
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/x-vcard"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Contact: ${card.displayName}")
                putExtra(Intent.EXTRA_TEXT, "Here is the contact card for ${card.displayName}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Share Contact Card").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Shares plain text or a link via system Sharesheet.
     */
    fun shareText(context: Context, text: String, title: String = "Share Link via TAP") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Handoff to Samsung Quick Share or Google Nearby Share directly when available.
     * Falls back seamlessly to the standard Android Sharesheet.
     */
    fun launchQuickShareHandoff(context: Context, uri: Uri, mimeType: String = "*/*") {
        val quickShareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Attempt to target Samsung Quick Share directly
            component = ComponentName("com.samsung.android.app.sharelive", "com.samsung.android.app.sharelive.ShareActivity")
        }

        try {
            if (context.packageManager.resolveActivity(quickShareIntent, 0) != null) {
                quickShareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(quickShareIntent)
                return
            }
        } catch (_: Exception) {}

        // Fallback to general system sharesheet
        shareFile(context, uri, mimeType, "Share via Quick Share / Nearby")
    }
}
