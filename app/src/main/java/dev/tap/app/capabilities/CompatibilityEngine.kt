package dev.tap.app.capabilities

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Universal Compatibility Engine.
 *
 * Scans hardware and software APIs at runtime to produce an uncompromised,
 * honest capability assessment for the host device (including Samsung Galaxy A54 5G).
 */
object CompatibilityEngine {

    fun detectCapabilities(context: Context): DeviceCapabilities {
        val pm = context.packageManager

        // 1. NFC Detection
        val nfcHardware = pm.hasSystemFeature(PackageManager.FEATURE_NFC)
        val nfcHce = pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
        val nfcAdapter = try { NfcAdapter.getDefaultAdapter(context) } catch (_: Exception) { null }
        val nfcEnabled = nfcAdapter?.isEnabled == true
        val nfcReaderWriter = nfcHardware && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)

        // 2. Bluetooth & BLE Detection
        val btHardware = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        val bleScan = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter = btManager?.adapter
        val btEnabled = btAdapter?.isEnabled == true
        val bleAdvertise = btAdapter?.isMultipleAdvertisementSupported == true

        // 3. Wi-Fi & Network Detection
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = connManager?.activeNetwork
        val caps = connManager?.getNetworkCapabilities(activeNet)
        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        var wifiSsid: String? = null
        if (isWifi) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val info = wifiManager?.connectionInfo
            val rawSsid = info?.ssid
            if (!rawSsid.isNullOrBlank() && rawSsid != "<unknown ssid>") {
                wifiSsid = rawSsid.removePrefix("\"").removeSuffix("\"")
            }
        }

        val localIp = getLocalIpv4Address()

        // 4. Optical Camera Detection
        val cameraAvailable = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

        // 5. Native Sharing & Quick Share Detection
        val sharesheetAvailable = true
        val quickShareAvailable = checkQuickShareInstalled(pm)

        // 6. Platform & One UI Detection
        val oneUiVersion = detectSamsungOneUiVersion()

        return DeviceCapabilities(
            nfcHardware = nfcHardware,
            nfcEnabled = nfcEnabled,
            nfcReaderWriter = nfcReaderWriter,
            nfcHce = nfcHce,
            bluetoothHardware = btHardware,
            bluetoothEnabled = btEnabled,
            bleScanSupported = bleScan,
            bleAdvertiseSupported = bleAdvertise,
            wifiConnected = isWifi,
            wifiSsid = wifiSsid,
            localIpAddress = localIp,
            cameraAvailable = cameraAvailable,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            sdkInt = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            manufacturer = Build.MANUFACTURER,
            oneUiVersion = oneUiVersion,
            sharesheetAvailable = sharesheetAvailable,
            quickShareAvailable = quickShareAvailable
        )
    }

    private fun getLocalIpv4Address(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress
                        if (hostAddress != null && !hostAddress.startsWith("127.")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun checkQuickShareInstalled(pm: PackageManager): Boolean {
        val quickSharePackages = listOf(
            "com.samsung.android.app.sharelive", // Samsung Quick Share
            "com.google.android.gms"            // Google Nearby Share / Quick Share
        )
        return quickSharePackages.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun detectSamsungOneUiVersion(): String? {
        if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return null
        return try {
            val semPlatformIntField = Class.forName("com.samsung.android.feature.SemFloatingFeature")
                .getDeclaredMethod("getString", String::class.java)
            // Attempt reading sem platform property if available
            null
        } catch (_: Exception) {
            // Check system properties via reflection
            try {
                val systemProperties = Class.forName("android.os.SystemProperties")
                val getMethod = systemProperties.getMethod("get", String::class.java)
                val ver = getMethod.invoke(null, "ro.build.version.oneui") as? String
                if (!ver.isNullOrBlank()) "One UI $ver"
                else {
                    val sepVer = getMethod.invoke(null, "ro.build.version.sep") as? String
                    if (!sepVer.isNullOrBlank()) "One UI (SEP $sepVer)" else "One UI (Standard)"
                }
            } catch (_: Exception) {
                "One UI"
            }
        }
    }
}
