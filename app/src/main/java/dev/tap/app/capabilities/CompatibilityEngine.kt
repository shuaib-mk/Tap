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

        val isHotspot = isHotspotActive()
        val localIp = getBestLocalIpv4Address()
        val isCellularOnly = !isWifi && !isHotspot

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
            isCellularOnly = isCellularOnly,
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

    fun getBestLocalIpv4Address(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null

            // Priority 1: Wi-Fi interfaces (wlan0, etc.)
            val wifiIntf = interfaces.firstOrNull { intf ->
                !intf.isLoopback && intf.isUp && intf.name.startsWith("wlan", ignoreCase = true)
            }
            if (wifiIntf != null) {
                val ip = extractIpv4(wifiIntf)
                if (ip != null) return ip
            }

            // Priority 2: Hotspot / SoftAP interfaces (ap0, softap, swlan, rndis)
            val hotspotIntf = interfaces.firstOrNull { intf ->
                !intf.isLoopback && intf.isUp && (
                    intf.name.startsWith("ap", ignoreCase = true) ||
                    intf.name.startsWith("softap", ignoreCase = true) ||
                    intf.name.startsWith("rndis", ignoreCase = true) ||
                    intf.name.startsWith("swlan", ignoreCase = true)
                )
            }
            if (hotspotIntf != null) {
                val ip = extractIpv4(hotspotIntf)
                if (ip != null) return ip
            }

            // Priority 3: Any non-cellular local LAN (eth0, etc.)
            val lanIntf = interfaces.firstOrNull { intf ->
                !intf.isLoopback && intf.isUp &&
                    !intf.name.startsWith("rmnet", ignoreCase = true) &&
                    !intf.name.startsWith("ccmni", ignoreCase = true) &&
                    !intf.name.startsWith("pdp", ignoreCase = true)
            }
            if (lanIntf != null) {
                val ip = extractIpv4(lanIntf)
                if (ip != null) return ip
            }

            // Priority 4: Cellular interface fallback
            val anyIntf = interfaces.firstOrNull { !it.isLoopback && it.isUp }
            if (anyIntf != null) {
                return extractIpv4(anyIntf)
            }
        } catch (_: Exception) {}
        return null
    }

    fun isHotspotActive(): Boolean {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return false
            return interfaces.any { intf ->
                !intf.isLoopback && intf.isUp && (
                    intf.name.startsWith("ap", ignoreCase = true) ||
                    intf.name.startsWith("softap", ignoreCase = true) ||
                    intf.name.startsWith("rndis", ignoreCase = true) ||
                    intf.name.startsWith("swlan", ignoreCase = true)
                ) && extractIpv4(intf) != null
            }
        } catch (_: Exception) {
            return false
        }
    }

    private fun extractIpv4(intf: NetworkInterface): String? {
        val addrs = intf.inetAddresses ?: return null
        while (addrs.hasMoreElements()) {
            val addr = addrs.nextElement()
            if (!addr.isLoopbackAddress && addr is Inet4Address) {
                val host = addr.hostAddress
                if (host != null && !host.startsWith("127.")) {
                    return host
                }
            }
        }
        return null
    }

    private fun getLocalIpv4Address(): String? = getBestLocalIpv4Address()

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
