package dev.tap.app.capabilities

import org.junit.Assert.*
import org.junit.Test

class CapabilitiesTest {

    @Test
    fun testCapabilityStatusResolution() {
        val fullCaps = DeviceCapabilities(
            nfcHardware = true,
            nfcEnabled = true,
            nfcReaderWriter = true,
            nfcHce = true,
            bluetoothHardware = true,
            bluetoothEnabled = true,
            bleScanSupported = true,
            bleAdvertiseSupported = true,
            wifiConnected = true,
            localIpAddress = "192.168.1.150",
            cameraAvailable = true,
            androidVersion = "Android 14 (API 34)",
            deviceModel = "Samsung SM-A546B",
            manufacturer = "Samsung",
            oneUiVersion = "One UI 6.1"
        )

        assertEquals(DeviceCapabilities.CapabilityStatus.AVAILABLE, fullCaps.nfcStatus)
        assertEquals(DeviceCapabilities.CapabilityStatus.AVAILABLE, fullCaps.bleStatus)
        assertEquals(DeviceCapabilities.CapabilityStatus.AVAILABLE, fullCaps.wifiStatus)
        assertEquals(DeviceCapabilities.CapabilityStatus.AVAILABLE, fullCaps.qrScanningStatus)

        assertTrue(fullCaps.isTapToTapAvailable)
        assertTrue(fullCaps.isNfcTagWritingAvailable)
        assertTrue(fullCaps.isNfcHceAvailable)
        assertTrue(fullCaps.isLocalWebSharingAvailable)
        assertTrue(fullCaps.isSamsungDevice)
    }

    @Test
    fun testDisabledNfcCapability() {
        val disabledNfcCaps = DeviceCapabilities(
            nfcHardware = true,
            nfcEnabled = false
        )
        assertEquals(DeviceCapabilities.CapabilityStatus.DISABLED, disabledNfcCaps.nfcStatus)
        assertFalse(disabledNfcCaps.isNfcTagWritingAvailable)
        assertFalse(disabledNfcCaps.isNfcHceAvailable)
    }

    @Test
    fun testUnsupportedHardware() {
        val noHardwareCaps = DeviceCapabilities(
            nfcHardware = false,
            bluetoothHardware = false
        )
        assertEquals(DeviceCapabilities.CapabilityStatus.UNSUPPORTED, noHardwareCaps.nfcStatus)
        assertEquals(DeviceCapabilities.CapabilityStatus.UNSUPPORTED, noHardwareCaps.bleStatus)
    }
}
