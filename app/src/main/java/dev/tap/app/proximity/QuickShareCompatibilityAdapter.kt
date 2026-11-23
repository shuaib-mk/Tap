package dev.tap.app.proximity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * Samsung Quick Share & One UI Tap to Share Compatibility Adapter.
 *
 * Implements officially supported system integration with Samsung Quick Share:
 * - Detects Samsung Quick Share package (`com.samsung.android.app.sharelive`)
 * - Detects Google Quick Share package (`com.google.android.gms`)
 * - Detects Samsung One UI version and system capabilities
 * - Directly invokes Samsung Quick Share proximity activity for instant device discovery
 */
object QuickShareCompatibilityAdapter {

    const val SAMSUNG_QUICK_SHARE_PKG = "com.samsung.android.app.sharelive"
    const val SAMSUNG_QUICK_SHARE_ACTIVITY = "com.samsung.android.app.sharelive.ShareActivity"
    const val GOOGLE_PLAY_SERVICES_PKG = "com.google.android.gms"

    enum class QuickShareAvailability {
        SAMSUNG_NATIVE_AVAILABLE,
        GOOGLE_QUICK_SHARE_AVAILABLE,
        UNAVAILABLE
    }

    data class CompatibilityReport(
        val availability: QuickShareAvailability,
        val isSamsungDevice: Boolean,
        val oneUiVersion: String?,
        val packageName: String?,
        val canDirectLaunch: Boolean,
        val description: String
    )

    fun checkCompatibility(context: Context): CompatibilityReport {
        val pm = context.packageManager
        val isSamsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        val oneUi = detectOneUiVersion()

        // 1. Check Samsung Quick Share
        val hasSamsungQuickShare = try {
            pm.getPackageInfo(SAMSUNG_QUICK_SHARE_PKG, 0)
            true
        } catch (_: Exception) {
            false
        }

        if (hasSamsungQuickShare) {
            val directIntent = Intent().apply {
                component = ComponentName(SAMSUNG_QUICK_SHARE_PKG, SAMSUNG_QUICK_SHARE_ACTIVITY)
            }
            val canDirect = pm.resolveActivity(directIntent, 0) != null

            return CompatibilityReport(
                availability = QuickShareAvailability.SAMSUNG_NATIVE_AVAILABLE,
                isSamsungDevice = isSamsung,
                oneUiVersion = oneUi,
                packageName = SAMSUNG_QUICK_SHARE_PKG,
                canDirectLaunch = canDirect,
                description = "Samsung Quick Share (One UI) is installed and ready for Tap to Share / Proximity handoff."
            )
        }

        // 2. Check Google Quick Share
        val hasGoogleQuickShare = try {
            pm.getPackageInfo(GOOGLE_PLAY_SERVICES_PKG, 0)
            true
        } catch (_: Exception) {
            false
        }

        if (hasGoogleQuickShare) {
            return CompatibilityReport(
                availability = QuickShareAvailability.GOOGLE_QUICK_SHARE_AVAILABLE,
                isSamsungDevice = isSamsung,
                oneUiVersion = oneUi,
                packageName = GOOGLE_PLAY_SERVICES_PKG,
                canDirectLaunch = false,
                description = "Google Quick Share is available via Android system sharesheet handoff."
            )
        }

        return CompatibilityReport(
            availability = QuickShareAvailability.UNAVAILABLE,
            isSamsungDevice = isSamsung,
            oneUiVersion = oneUi,
            packageName = null,
            canDirectLaunch = false,
            description = "Quick Share packages not detected. TAP high-speed proximity transport will be used."
        )
    }

    /**
     * Handoff selected files directly to Samsung Quick Share.
     * When invoked on a Galaxy device, this immediately presents Samsung's native Quick Share
     * proximity radar, allowing 1-tap transfer to nearby One UI devices.
     */
    fun launchQuickShare(context: Context, uris: List<Uri>, title: String = "Share via Quick Share"): Result<Unit> {
        if (uris.isEmpty()) {
            return Result.failure(IllegalArgumentException("No files selected to share."))
        }

        return try {
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                }
            }

            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // Try explicit Samsung Quick Share component first
            val samsungIntent = Intent(intent).apply {
                component = ComponentName(SAMSUNG_QUICK_SHARE_PKG, SAMSUNG_QUICK_SHARE_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (context.packageManager.resolveActivity(samsungIntent, 0) != null) {
                context.startActivity(samsungIntent)
                return Result.success(Unit)
            }

            // Fallback: System chooser targeting Quick Share / Nearby
            val chooser = Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun detectOneUiVersion(): String? {
        if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return null
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val getMethod = systemProperties.getMethod("get", String::class.java)
            val ver = getMethod.invoke(null, "ro.build.version.oneui") as? String
            if (!ver.isNullOrBlank()) "One UI $ver"
            else {
                val sep = getMethod.invoke(null, "ro.build.version.sep") as? String
                if (!sep.isNullOrBlank()) "One UI (SEP $sep)" else "One UI"
            }
        } catch (_: Exception) {
            "One UI"
        }
    }
}
