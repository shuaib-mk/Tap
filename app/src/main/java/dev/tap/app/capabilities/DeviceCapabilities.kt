package dev.tap.app.capabilities

/**
 * Detailed device capability model.
 * Reflects runtime hardware and software capabilities without fabrication.
 */
data class DeviceCapabilities(
    // NFC capabilities
    val nfcHardware: Boolean = false,
    val nfcEnabled: Boolean = false,
    val nfcReaderWriter: Boolean = false,
    val nfcHce: Boolean = false,

    // Bluetooth / BLE
    val bluetoothHardware: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val bleScanSupported: Boolean = false,
    val bleAdvertiseSupported: Boolean = false,

    // Wi-Fi & Local Network
    val wifiConnected: Boolean = false,
    val wifiSsid: String? = null,
    val localIpAddress: String? = null,

    // Optical / Camera
    val cameraAvailable: Boolean = false,

    // OS & Platform details
    val androidVersion: String = "",
    val sdkInt: Int = 0,
    val deviceModel: String = "",
    val manufacturer: String = "",
    val oneUiVersion: String? = null,

    // Native Sharing System
    val sharesheetAvailable: Boolean = true,
    val quickShareAvailable: Boolean = false
) {
    enum class CapabilityStatus {
        AVAILABLE,
        DISABLED,
        PERMISSION_REQUIRED,
        UNSUPPORTED
    }

    val nfcStatus: CapabilityStatus
        get() = when {
            !nfcHardware -> CapabilityStatus.UNSUPPORTED
            !nfcEnabled -> CapabilityStatus.DISABLED
            else -> CapabilityStatus.AVAILABLE
        }

    val bleStatus: CapabilityStatus
        get() = when {
            !bluetoothHardware -> CapabilityStatus.UNSUPPORTED
            !bluetoothEnabled -> CapabilityStatus.DISABLED
            bleScanSupported -> CapabilityStatus.AVAILABLE
            else -> CapabilityStatus.UNSUPPORTED
        }

    val wifiStatus: CapabilityStatus
        get() = if (wifiConnected && !localIpAddress.isNullOrBlank()) {
            CapabilityStatus.AVAILABLE
        } else {
            CapabilityStatus.DISABLED
        }

    val qrScanningStatus: CapabilityStatus
        get() = if (cameraAvailable) CapabilityStatus.AVAILABLE else CapabilityStatus.UNSUPPORTED

    val isSamsungDevice: Boolean
        get() = manufacturer.equals("Samsung", ignoreCase = true) || oneUiVersion != null

    /**
     * Determines which TAP sharing modes are available on this device.
     */
    val isTapToTapAvailable: Boolean
        get() = (wifiConnected || bleStatus == CapabilityStatus.AVAILABLE)

    val isNfcTagWritingAvailable: Boolean
        get() = nfcStatus == CapabilityStatus.AVAILABLE && nfcReaderWriter

    val isNfcHceAvailable: Boolean
        get() = nfcStatus == CapabilityStatus.AVAILABLE && nfcHce

    val isLocalWebSharingAvailable: Boolean
        get() = !localIpAddress.isNullOrBlank()
}
