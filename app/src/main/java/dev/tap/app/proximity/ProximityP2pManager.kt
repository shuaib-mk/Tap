package dev.tap.app.proximity

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.os.Build
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class P2pDeviceInfo(
    val deviceName: String,
    val deviceAddress: String,
    val status: Int
)

sealed class P2pState {
    object Idle : P2pState()
    object Discovering : P2pState()
    data class GroupFormed(val isGroupOwner: Boolean, val groupOwnerAddress: String) : P2pState()
    data class Connected(val peerAddress: String, val groupOwnerAddress: String) : P2pState()
    data class Error(val message: String) : P2pState()
}

/**
 * Manages Android Wi-Fi Direct (WifiP2pManager) for TAP Proximity File Sharing.
 * Enables zero-router direct peer-to-peer Wi-Fi connections between compatible Android devices.
 */
class ProximityP2pManager(private val context: Context) {

    private val p2pManager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    private val _p2pState = MutableStateFlow<P2pState>(P2pState.Idle)
    val p2pState: StateFlow<P2pState> = _p2pState.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<P2pDeviceInfo>>(emptyList())
    val discoveredPeers: StateFlow<List<P2pDeviceInfo>> = _discoveredPeers.asStateFlow()

    val isSupported: Boolean get() = p2pManager != null

    init {
        try {
            p2pChannel = p2pManager?.initialize(context, Looper.getMainLooper(), null)
        } catch (_: Exception) {
            p2pChannel = null
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return

        registerReceiver()
        _p2pState.value = P2pState.Discovering

        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reasonCode: Int) {
                _p2pState.value = P2pState.Error("Wi-Fi Direct discovery failed (Code $reasonCode)")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return

        manager.stopPeerDiscovery(channel, null)
        unregisterReceiver()
        if (_p2pState.value is P2pState.Discovering) {
            _p2pState.value = P2pState.Idle
        }
    }

    @SuppressLint("MissingPermission")
    fun createAutonomousGroup(onGroupCreated: (ownerAddress: String) -> Unit) {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return

        registerReceiver()
        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                manager.requestConnectionInfo(channel) { info ->
                    if (info.groupFormed && info.isGroupOwner) {
                        val ownerIp = info.groupOwnerAddress?.hostAddress ?: "192.168.49.1"
                        _p2pState.value = P2pState.GroupFormed(isGroupOwner = true, groupOwnerAddress = ownerIp)
                        onGroupCreated(ownerIp)
                    }
                }
            }
            override fun onFailure(reason: Int) {
                _p2pState.value = P2pState.Error("Failed to create Wi-Fi Direct group (Code $reason)")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(deviceAddress: String, onConnected: (ownerAddress: String) -> Unit) {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return

        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                manager.requestConnectionInfo(channel) { info ->
                    if (info.groupFormed) {
                        val ownerIp = info.groupOwnerAddress?.hostAddress ?: "192.168.49.1"
                        _p2pState.value = P2pState.Connected(deviceAddress, ownerIp)
                        onConnected(ownerIp)
                    }
                }
            }
            override fun onFailure(reason: Int) {
                _p2pState.value = P2pState.Error("Failed to connect via Wi-Fi Direct (Code $reason)")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return

        manager.removeGroup(channel, null)
        unregisterReceiver()
        _p2pState.value = P2pState.Idle
    }

    private fun registerReceiver() {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        val manager = p2pManager ?: return
                        val channel = p2pChannel ?: return
                        manager.requestPeers(channel) { peerList ->
                            val devices = peerList.deviceList.map {
                                P2pDeviceInfo(it.deviceName ?: "Unknown Device", it.deviceAddress, it.status)
                            }
                            _discoveredPeers.value = devices
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, android.net.NetworkInfo::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                        }
                        if (networkInfo?.isConnected == true) {
                            p2pManager?.requestConnectionInfo(p2pChannel) { info ->
                                if (info.groupFormed) {
                                    val ip = info.groupOwnerAddress?.hostAddress ?: "192.168.49.1"
                                    _p2pState.value = P2pState.GroupFormed(info.isGroupOwner, ip)
                                }
                            }
                        }
                    }
                }
            }
        }

        context.registerReceiver(receiver, filter)
    }

    private fun unregisterReceiver() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
            receiver = null
        }
    }
}
