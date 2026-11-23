package dev.tap.app.discovery.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import dev.tap.app.protocol.ConnectionMethod
import dev.tap.app.protocol.SessionOffer
import dev.tap.app.protocol.TapProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.util.UUID

sealed class BleDiscoveryEvent {
    object Idle : BleDiscoveryEvent()
    data class Advertising(val started: Boolean) : BleDiscoveryEvent()
    data class Scanning(val started: Boolean) : BleDiscoveryEvent()
    data class DeviceFound(val deviceName: String, val address: String, val offer: SessionOffer?) : BleDiscoveryEvent()
    data class Error(val message: String) : BleDiscoveryEvent()
}

/**
 * Bluetooth Low Energy Discovery for TAP.
 * Discovers nearby TAP devices without pairing, exchanging session bootstrap data.
 */
class BleDiscovery(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private val _events = MutableStateFlow<BleDiscoveryEvent>(BleDiscoveryEvent.Idle)
    val events: StateFlow<BleDiscoveryEvent> = _events.asStateFlow()

    private val serviceUuid = UUID.fromString(TapProtocol.BLE_SERVICE_UUID)
    private val parcelUuid = ParcelUuid(serviceUuid)

    val isAvailable: Boolean get() = bluetoothAdapter != null
    val isEnabled: Boolean get() = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startAdvertising(offer: SessionOffer) {
        if (!isEnabled) {
            _events.value = BleDiscoveryEvent.Error("Bluetooth is disabled")
            return
        }

        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            _events.value = BleDiscoveryEvent.Error("BLE Advertising not supported on this device")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(60000)
            .build()

        // Truncate payload to fit in BLE 31-byte advertising packet:
        // Host address + port + truncated session ID
        val hostParts = offer.hostAddress.split(".")
        val compactData = if (hostParts.size == 4) {
            val ipBytes = hostParts.map { it.toInt().toByte() }.toByteArray()
            val portBytes = byteArrayOf((offer.port shr 8).toByte(), (offer.port and 0xFF).toByte())
            val sessionPrefix = offer.sessionId.take(8).toByteArray(StandardCharsets.UTF_8)
            ipBytes + portBytes + sessionPrefix
        } else {
            offer.sessionId.take(16).toByteArray(StandardCharsets.UTF_8)
        }

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(parcelUuid)
            .addServiceData(parcelUuid, compactData)
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: Exception) {}
        advertiser = null
        _events.value = BleDiscoveryEvent.Advertising(false)
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (!isEnabled) {
            _events.value = BleDiscoveryEvent.Error("Bluetooth is disabled")
            return
        }

        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            _events.value = BleDiscoveryEvent.Error("BLE Scanner not available")
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(parcelUuid)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(listOf(filter), settings, scanCallback)
        _events.value = BleDiscoveryEvent.Scanning(true)
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        scanner = null
        _events.value = BleDiscoveryEvent.Scanning(false)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _events.value = BleDiscoveryEvent.Advertising(true)
        }

        override fun onStartFailure(errorCode: Int) {
            _events.value = BleDiscoveryEvent.Error("Advertising failed (error code $errorCode)")
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val name = result.device.name ?: result.scanRecord?.deviceName ?: "TAP Device"
            val address = result.device.address

            val serviceData = result.scanRecord?.getServiceData(parcelUuid)
            var offer: SessionOffer? = null

            if (serviceData != null && serviceData.size >= 6) {
                val ip = "${serviceData[0].toInt() and 0xFF}.${serviceData[1].toInt() and 0xFF}.${serviceData[2].toInt() and 0xFF}.${serviceData[3].toInt() and 0xFF}"
                val port = ((serviceData[4].toInt() and 0xFF) shl 8) or (serviceData[5].toInt() and 0xFF)
                val sessionPrefix = if (serviceData.size > 6) {
                    String(serviceData.copyOfRange(6, serviceData.size), StandardCharsets.UTF_8)
                } else "ble_session"

                offer = SessionOffer(
                    sessionId = sessionPrefix,
                    deviceName = name,
                    publicKeyBase64 = "",
                    connectionMethod = ConnectionMethod.BLE,
                    hostAddress = ip,
                    port = port
                )
            }

            _events.value = BleDiscoveryEvent.DeviceFound(name, address, offer)
        }

        override fun onScanFailed(errorCode: Int) {
            _events.value = BleDiscoveryEvent.Error("Scan failed (error code $errorCode)")
        }
    }
}
