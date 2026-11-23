package dev.tap.app.discovery

import android.app.Activity
import android.content.Context
import dev.tap.app.discovery.ble.BleDiscovery
import dev.tap.app.discovery.ble.BleDiscoveryEvent
import dev.tap.app.discovery.nfc.NfcSessionExchange
import dev.tap.app.nfc.NfcCapabilities
import dev.tap.app.protocol.ConnectionMethod
import dev.tap.app.protocol.SessionOffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class DiscoveryState {
    object Idle : DiscoveryState()
    data class Searching(val method: ConnectionMethod, val message: String) : DiscoveryState()
    data class Discovered(val offer: SessionOffer, val method: ConnectionMethod) : DiscoveryState()
    data class Error(val message: String) : DiscoveryState()
}

/**
 * Unified Discovery Manager for TAP.
 * Coordinates connection bootstrapping in order:
 * 1. NFC (Immediate tap)
 * 2. BLE (Nearby broadcast & scan)
 * 3. QR Code (Universal fallback)
 */
class DiscoveryManager(
    private val context: Context,
    private val bleDiscovery: BleDiscovery
) {
    private val _state = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val state: StateFlow<DiscoveryState> = _state.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<SessionOffer>>(emptyList())
    val discoveredDevices: StateFlow<List<SessionOffer>> = _discoveredDevices.asStateFlow()

    val nfcCapabilities: NfcCapabilities get() = NfcCapabilities.from(context)
    val bleAvailable: Boolean get() = bleDiscovery.isAvailable && bleDiscovery.isEnabled

    fun startListeningForNearby(activity: Activity, scope: CoroutineScope) {
        _state.value = DiscoveryState.Searching(
            ConnectionMethod.NFC_HCE,
            "Ready for NFC tap or BLE discovery..."
        )

        // 1. Enable NFC Reader Mode
        if (nfcCapabilities.enabled) {
            NfcSessionExchange.enableReaderMode(
                activity = activity,
                onSessionDiscovered = { offer ->
                    _state.value = DiscoveryState.Discovered(offer, ConnectionMethod.NFC_HCE)
                }
            )
        }

        // 2. Start BLE Scanning
        if (bleAvailable) {
            bleDiscovery.startScanning()
            scope.launch {
                bleDiscovery.events.collect { event ->
                    when (event) {
                        is BleDiscoveryEvent.DeviceFound -> {
                            event.offer?.let { offer ->
                                val current = _discoveredDevices.value.toMutableList()
                                if (current.none { it.sessionId == offer.sessionId }) {
                                    current.add(offer)
                                    _discoveredDevices.value = current
                                }
                            }
                        }
                        is BleDiscoveryEvent.Error -> {
                            // Non-fatal, QR fallback remains available
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    fun startAdvertising(offer: SessionOffer) {
        if (bleAvailable) {
            bleDiscovery.startAdvertising(offer)
        }
    }

    fun stopAll(activity: Activity?) {
        activity?.let { NfcSessionExchange.disableReaderMode(it) }
        bleDiscovery.stopScanning()
        bleDiscovery.stopAdvertising()
        _state.value = DiscoveryState.Idle
    }
}
