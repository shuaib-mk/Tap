package dev.tap.app.proximity

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ProximityEngineState {
    object Idle : ProximityEngineState()
    object Staging : ProximityEngineState()
    object Discovering : ProximityEngineState()
    data class Connecting(val peerName: String) : ProximityEngineState()
    data class Transferring(val progress: ProximityProgress) : ProximityEngineState()
    data class Completed(val fileCount: Int, val totalBytes: Long) : ProximityEngineState()
    data class Error(val message: String) : ProximityEngineState()
}

/**
 * Proximity File Sharing Engine for TAP.
 *
 * Coordinates:
 * - Multi-category file staging (Photos, Videos, Audio, PDFs, Documents, Archives)
 * - Proximity peer discovery
 * - Samsung Quick Share native handoff
 * - Direct TAP proximity peer-to-peer transport with AES-256-GCM & SHA-256
 */
class ProximitySharingEngine(private val context: Context) {

    private val securityManager = TransferSecurityManager()
    private val handshakeManager = DeviceHandshakeManager(securityManager)
    private val transportManager = TransferTransportManager(context)
    val sessionRepository = TransferSessionRepository()

    private val _engineState = MutableStateFlow<ProximityEngineState>(ProximityEngineState.Idle)
    val engineState: StateFlow<ProximityEngineState> = _engineState.asStateFlow()

    private val _stagedFiles = MutableStateFlow<List<ProximityFile>>(emptyList())
    val stagedFiles: StateFlow<List<ProximityFile>> = _stagedFiles.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<ProximityPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<ProximityPeer>> = _discoveredPeers.asStateFlow()

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

    fun startProximityDiscovery() {
        _engineState.value = ProximityEngineState.Discovering
        // Discovered peers model
    }

    fun stopProximityDiscovery() {
        if (_engineState.value is ProximityEngineState.Discovering) {
            _engineState.value = if (_stagedFiles.value.isNotEmpty()) ProximityEngineState.Staging else ProximityEngineState.Idle
        }
    }

    fun cancelActiveTransfer() {
        transportManager.cancel()
        _engineState.value = ProximityEngineState.Idle
    }

    /**
     * Official Samsung Quick Share Handoff:
     * Directly delegates the staged files to Samsung's native Quick Share activity,
     * activating native One UI Tap to Share proximity discovery.
     */
    fun launchSamsungQuickShareHandoff(): Result<Unit> {
        val uris = _stagedFiles.value.map { it.uri }
        return QuickShareCompatibilityAdapter.launchQuickShare(context, uris)
    }

    fun sendToPeer(peer: ProximityPeer, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            _engineState.value = ProximityEngineState.Connecting(peer.deviceName)
            when (val hs = handshakeManager.completeHandshake(peer)) {
                is HandshakeState.Authenticated -> {
                    val res = transportManager.sendFiles(
                        host = peer.hostAddress,
                        port = peer.port,
                        sessionKey = hs.sessionKey,
                        security = securityManager,
                        files = _stagedFiles.value
                    )
                    if (res.isSuccess) {
                        _engineState.value = ProximityEngineState.Completed(
                            fileCount = _stagedFiles.value.size,
                            totalBytes = totalStagedBytes
                        )
                        sessionRepository.recordSession(
                            ProximitySession(
                                remoteDeviceName = peer.deviceName,
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
                is HandshakeState.Failed -> {
                    _engineState.value = ProximityEngineState.Error(hs.error)
                }
                else -> {}
            }
        }
    }
}
