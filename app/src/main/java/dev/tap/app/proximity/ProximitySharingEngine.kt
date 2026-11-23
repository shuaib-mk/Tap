package dev.tap.app.proximity

import android.content.Context
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

sealed class ProximityEngineState {
    object Idle : ProximityEngineState()
    object Staging : ProximityEngineState()
    object Discovering : ProximityEngineState()
    object ReceivingWaiting : ProximityEngineState()
    data class AwaitingApproval(val offer: ProximityHandshakeOffer, val sasCode: String) : ProximityEngineState()
    data class Connecting(val peerName: String) : ProximityEngineState()
    data class Transferring(val progress: ProximityProgress) : ProximityEngineState()
    data class Completed(val fileCount: Int, val totalBytes: Long) : ProximityEngineState()
    data class Error(val message: String) : ProximityEngineState()
}

data class ProximityPendingApproval(
    val offer: ProximityHandshakeOffer,
    val sasCode: String,
    val continuation: CompletableDeferred<Boolean>
)

/**
 * Proximity File Sharing Engine for TAP v1.2.1.
 *
 * Coordinates:
 * - Multi-category file staging (Photos, Videos, Audio, PDFs, Documents, Archives)
 * - Wi-Fi Direct and Local IP Peer Discovery
 * - Receiver listening server on port 8990
 * - Receiver approval prompt with 6-digit SAS code comparison
 * - Streaming AES-256-GCM chunked transfer with SHA-256 integrity verification
 * - Preserved Samsung Quick Share native handoff
 */
class ProximitySharingEngine(private val context: Context) {

    val securityManager = TransferSecurityManager()
    val handshakeManager = DeviceHandshakeManager(securityManager)
    val transportManager = TransferTransportManager(context)
    val p2pManager = ProximityP2pManager(context)
    val sessionRepository = TransferSessionRepository()

    private val deviceId = "tap_${UUID.randomUUID().toString().take(8)}"
    val deviceName: String = "${Build.MANUFACTURER.capitalize()} ${Build.MODEL}"

    private val _engineState = MutableStateFlow<ProximityEngineState>(ProximityEngineState.Idle)
    val engineState: StateFlow<ProximityEngineState> = _engineState.asStateFlow()

    private val _stagedFiles = MutableStateFlow<List<ProximityFile>>(emptyList())
    val stagedFiles: StateFlow<List<ProximityFile>> = _stagedFiles.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<P2pDeviceInfo>>(emptyList())
    val discoveredPeers: StateFlow<List<P2pDeviceInfo>> = _discoveredPeers.asStateFlow()

    private val _pendingApproval = MutableStateFlow<ProximityPendingApproval?>(null)
    val pendingApproval: StateFlow<ProximityPendingApproval?> = _pendingApproval.asStateFlow()

    private val _isReceiverRunning = MutableStateFlow(false)
    val isReceiverRunning: StateFlow<Boolean> = _isReceiverRunning.asStateFlow()

    val progress: StateFlow<ProximityProgress> = transportManager.progress

    val totalStagedBytes: Long
        get() = _stagedFiles.value.sumOf { it.sizeBytes }

    fun stageUris(uris: List<Uri>) {
        val current = _stagedFiles.value.toMutableList()
        uris.forEach { uri ->
            ProximityFile.fromUri(context, uri)?.let { file ->
                if (current.none { it.uri == file.uri }) {
                    current.add(file)
                }
            }
        }
        _stagedFiles.value = current
        if (_engineState.value is ProximityEngineState.Idle) {
            _engineState.value = ProximityEngineState.Staging
        }
    }

    fun removeStagedFile(file: ProximityFile) {
        _stagedFiles.value = _stagedFiles.value.filter { it.uri != file.uri }
        if (_stagedFiles.value.isEmpty()) {
            _engineState.value = ProximityEngineState.Idle
        }
    }

    fun clearStagedFiles() {
        _stagedFiles.value = emptyList()
        _engineState.value = ProximityEngineState.Idle
    }

    // ---------------------------------------------------------------------------------------------
    // RECEIVER MODE
    // ---------------------------------------------------------------------------------------------
    fun startReceiverMode(scope: CoroutineScope) {
        _isReceiverRunning.value = true
        _engineState.value = ProximityEngineState.ReceivingWaiting

        // 1. Try Wi-Fi Direct Autonomous Group
        if (p2pManager.isSupported) {
            p2pManager.createAutonomousGroup { _ -> }
        }

        // 2. Start Receiver Server
        transportManager.startReceiverServer(
            scope = scope,
            port = TransferTransportManager.DEFAULT_PROXIMITY_PORT,
            security = securityManager,
            receiverDeviceName = deviceName,
            receiverDeviceId = deviceId,
            onTransferRequest = { offer, sasCode ->
                val deferred = CompletableDeferred<Boolean>()
                _pendingApproval.value = ProximityPendingApproval(offer, sasCode, deferred)
                _engineState.value = ProximityEngineState.AwaitingApproval(offer, sasCode)

                val decision = deferred.await()
                _pendingApproval.value = null
                decision
            },
            onTransferSuccess = { count, bytes ->
                _engineState.value = ProximityEngineState.Completed(count, bytes)
                sessionRepository.recordSession(
                    ProximitySession(
                        remoteDeviceName = "Sender Device",
                        fileCount = count,
                        totalBytes = bytes,
                        isSender = false,
                        success = true
                    )
                )
            }
        )
    }

    fun stopReceiverMode() {
        _isReceiverRunning.value = false
        transportManager.stopReceiverServer()
        if (p2pManager.isSupported) {
            p2pManager.disconnect()
        }
        if (_engineState.value is ProximityEngineState.ReceivingWaiting || _engineState.value is ProximityEngineState.AwaitingApproval) {
            _engineState.value = ProximityEngineState.Idle
        }
    }

    fun respondToApproval(accepted: Boolean) {
        _pendingApproval.value?.continuation?.complete(accepted)
    }

    // ---------------------------------------------------------------------------------------------
    // SENDER MODE & DISCOVERY
    // ---------------------------------------------------------------------------------------------
    fun startProximityDiscovery(scope: CoroutineScope) {
        _engineState.value = ProximityEngineState.Discovering
        if (p2pManager.isSupported) {
            p2pManager.startDiscovery()
            scope.launch {
                p2pManager.discoveredPeers.collect { peers ->
                    _discoveredPeers.value = peers
                }
            }
        }
    }

    fun stopProximityDiscovery() {
        if (p2pManager.isSupported) {
            p2pManager.stopDiscovery()
        }
        if (_engineState.value is ProximityEngineState.Discovering) {
            _engineState.value = if (_stagedFiles.value.isNotEmpty()) ProximityEngineState.Staging else ProximityEngineState.Idle
        }
    }

    fun cancelActiveTransfer() {
        transportManager.cancel()
        _engineState.value = ProximityEngineState.Idle
    }

    /**
     * Preserved Samsung Quick Share Handoff:
     * Directly delegates the staged files to Samsung's native Quick Share activity.
     */
    fun launchSamsungQuickShareHandoff(): Result<Unit> {
        val uris = _stagedFiles.value.map { it.uri }
        return QuickShareCompatibilityAdapter.launchQuickShare(context, uris)
    }

    /**
     * Sends staged files to a specific target host IP (Wi-Fi Direct or local subnet).
     */
    fun sendToHost(host: String, port: Int = TransferTransportManager.DEFAULT_PROXIMITY_PORT, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            _engineState.value = ProximityEngineState.Connecting(host)
            val res = transportManager.sendFiles(
                host = host,
                port = port,
                security = securityManager,
                files = _stagedFiles.value,
                senderDeviceName = deviceName,
                senderDeviceId = deviceId
            )
            if (res.isSuccess) {
                _engineState.value = ProximityEngineState.Completed(
                    fileCount = _stagedFiles.value.size,
                    totalBytes = totalStagedBytes
                )
                sessionRepository.recordSession(
                    ProximitySession(
                        remoteDeviceName = host,
                        fileCount = _stagedFiles.value.size,
                        totalBytes = totalStagedBytes,
                        isSender = true,
                        success = true
                    )
                )
            } else {
                _engineState.value = ProximityEngineState.Error(res.exceptionOrNull()?.message ?: "Transfer failed")
            }
        }
    }
}
