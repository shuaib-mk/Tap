package dev.tap.app.proximity

import android.content.Context
import android.net.Uri
import android.os.Build
import dev.tap.app.discovery.nfc.NfcSessionExchange
import dev.tap.app.transfer.LocalHttpServer
import dev.tap.app.transfer.SelectedFile
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
    data class AwaitingTap(val fileCount: Int, val totalBytes: Long) : ProximityEngineState()
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
 * Proximity File Sharing Engine for TAP v1.3.0.
 *
 * Coordinates:
 * - Multi-category file staging (Photos, Videos, Audio, PDFs, Documents, Archives)
 * - Physical Tap to Share via NFC Host Card Emulation (Type 4 NDEF multi-record bootstrap)
 * - Zero-install web fallback via embedded LocalHttpServer with single-session tokens
 * - Automated Wi-Fi Direct and Local IP Peer Discovery
 * - Receiver listening server on port 8990
 * - Receiver approval prompt with 6-digit SAS code comparison
 * - Streaming AES-256-GCM chunked transfer with monotonic nonces & SHA-256 integrity verification
 * - Preserved Samsung Quick Share native handoff
 */
class ProximitySharingEngine(private val context: Context) {

    val securityManager = TransferSecurityManager()
    val handshakeManager = DeviceHandshakeManager(securityManager)
    val transportManager = TransferTransportManager(context)
    val p2pManager = ProximityP2pManager(context)
    val sessionRepository = TransferSessionRepository()
    private val localHttpServer = LocalHttpServer(context)

    private val deviceId = "tap_${UUID.randomUUID().toString().take(8)}"
    val deviceName: String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

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

    private val _isPhysicalTapToShareActive = MutableStateFlow(false)
    val isPhysicalTapToShareActive: StateFlow<Boolean> = _isPhysicalTapToShareActive.asStateFlow()

    private val _currentSasCode = MutableStateFlow<String?>(null)
    val currentSasCode: StateFlow<String?> = _currentSasCode.asStateFlow()

    private val _webFallbackUrl = MutableStateFlow<String?>(null)
    val webFallbackUrl: StateFlow<String?> = _webFallbackUrl.asStateFlow()

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
            deactivatePhysicalTapToShare()
            _engineState.value = ProximityEngineState.Idle
        }
    }

    fun clearStagedFiles() {
        deactivatePhysicalTapToShare()
        _stagedFiles.value = emptyList()
        _engineState.value = ProximityEngineState.Idle
    }

    // ---------------------------------------------------------------------------------------------
    // PHYSICAL TAP TO SHARE: SENDER ACTIVATION & NDEF BOOTSTRAP
    // ---------------------------------------------------------------------------------------------
    fun activatePhysicalTapToShare(scope: CoroutineScope) {
        if (_stagedFiles.value.isEmpty()) {
            _engineState.value = ProximityEngineState.Error("No files staged for Tap to Share.")
            return
        }

        val sessionId = "tap_p2p_${UUID.randomUUID().toString().take(8)}"
        val sessionToken = UUID.randomUUID().toString().take(8)

        // Resolve host IP
        val hostIp = transportManager.getLocalIpAddress() ?: "192.168.49.1"

        // 1. Start Zero-Install Web Server on Port 8989
        val selectedFiles = _stagedFiles.value.map {
            SelectedFile(uri = it.uri, name = it.name, mimeType = it.mimeType, sizeBytes = it.sizeBytes)
        }
        localHttpServer.start(
            hostAddress = hostIp,
            port = LocalHttpServer.DEFAULT_WEB_PORT,
            files = selectedFiles,
            sessionToken = sessionToken
        )
        val webUrl = localHttpServer.serverUrl
        _webFallbackUrl.value = webUrl

        // 2. Start Sender TCP Streaming Server on Port 8990
        transportManager.startSenderServer(
            scope = scope,
            port = TransferTransportManager.DEFAULT_PROXIMITY_PORT,
            security = securityManager,
            files = _stagedFiles.value,
            senderDeviceName = deviceName,
            senderDeviceId = deviceId,
            sessionId = sessionId,
            onSasReady = { sas ->
                _currentSasCode.value = sas
            },
            onCompleted = { count, bytes ->
                _engineState.value = ProximityEngineState.Completed(count, bytes)
                deactivatePhysicalTapToShare()
                sessionRepository.recordSession(
                    ProximitySession(
                        remoteDeviceName = "Receiver Device",
                        fileCount = count,
                        totalBytes = bytes,
                        isSender = true,
                        success = true
                    )
                )
            }
        )

        // 3. Precompute file manifests with SHA-256 for Offer
        scope.launch(Dispatchers.IO) {
            try {
                val manifests = _stagedFiles.value.map { file ->
                    val sha = context.contentResolver.openInputStream(file.uri)?.use { stream ->
                        TransferSecurityManager.calculateStreamHash(stream)
                    } ?: "0000000000000000000000000000000000000000000000000000000000000000"
                    ProximityFileManifest(
                        name = file.name,
                        sizeBytes = file.sizeBytes,
                        mimeType = file.mimeType,
                        sha256 = sha
                    )
                }

                val offer = ProximityHandshakeOffer(
                    sessionId = sessionId,
                    senderDeviceName = deviceName,
                    senderDeviceId = deviceId,
                    senderPublicKeyBase64 = securityManager.getPublicKeyBase64(),
                    hostAddress = hostIp,
                    port = TransferTransportManager.DEFAULT_PROXIMITY_PORT,
                    files = manifests
                )

                val offerJson = TransferTransportManager.serializeOffer(offer)
                val ndefMessage = NfcSessionExchange.createProximityNdefMessage(offerJson, webUrl)

                // 4. Register in ProximityNdefRepository for NFC HCE Type 4 Tag Emulation
                ProximityNdefRepository.setProximitySession(
                    sessionId = sessionId,
                    ndefBytes = ndefMessage.toByteArray(),
                    webFallbackUrl = webUrl
                )

                _isPhysicalTapToShareActive.value = true
                _engineState.value = ProximityEngineState.AwaitingTap(
                    fileCount = _stagedFiles.value.size,
                    totalBytes = totalStagedBytes
                )
            } catch (e: Exception) {
                _engineState.value = ProximityEngineState.Error("Failed to initialize tap session: ${e.message}")
            }
        }
    }

    fun deactivatePhysicalTapToShare() {
        _isPhysicalTapToShareActive.value = false
        _currentSasCode.value = null
        _webFallbackUrl.value = null
        ProximityNdefRepository.clearProximitySession()
        transportManager.stopServer()
        localHttpServer.stop()
        if (_engineState.value is ProximityEngineState.AwaitingTap) {
            _engineState.value = if (_stagedFiles.value.isNotEmpty()) ProximityEngineState.Staging else ProximityEngineState.Idle
        }
    }

    // ---------------------------------------------------------------------------------------------
    // INCOMING PHYSICAL TAP DISCOVERY & APPROVAL (RECEIVER SIDE)
    // ---------------------------------------------------------------------------------------------
    fun handleIncomingProximityNdefJson(json: String, scope: CoroutineScope) {
        try {
            val offer = TransferTransportManager.deserializeOffer(json)
            handleIncomingProximityOffer(offer, scope)
        } catch (e: Exception) {
            _engineState.value = ProximityEngineState.Error("Invalid proximity offer: ${e.message}")
        }
    }

    fun handleIncomingProximityOffer(offer: ProximityHandshakeOffer, scope: CoroutineScope) {
        scope.launch(Dispatchers.Main) {
            val sasCode = securityManager.calculateSasCode(offer.senderPublicKeyBase64, offer.sessionId)
            _currentSasCode.value = sasCode

            val deferred = CompletableDeferred<Boolean>()
            _pendingApproval.value = ProximityPendingApproval(offer, sasCode, deferred)
            _engineState.value = ProximityEngineState.AwaitingApproval(offer, sasCode)

            val accepted = deferred.await()
            _pendingApproval.value = null

            if (!accepted) {
                _engineState.value = ProximityEngineState.Idle
                _currentSasCode.value = null
                return@launch
            }

            _engineState.value = ProximityEngineState.Connecting(offer.senderDeviceName)

            withContext(Dispatchers.IO) {
                val res = transportManager.pullFilesFromHost(
                    host = offer.hostAddress,
                    port = offer.port,
                    security = securityManager,
                    receiverDeviceName = deviceName,
                    receiverDeviceId = deviceId,
                    onTransferRequest = { _, _ -> true },
                    onTransferSuccess = { count, bytes ->
                        _engineState.value = ProximityEngineState.Completed(count, bytes)
                        sessionRepository.recordSession(
                            ProximitySession(
                                remoteDeviceName = offer.senderDeviceName,
                                fileCount = count,
                                totalBytes = bytes,
                                isSender = false,
                                success = true
                            )
                        )
                    }
                )

                if (res.isFailure) {
                    _engineState.value = ProximityEngineState.Error(res.exceptionOrNull()?.message ?: "Transfer failed")
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // RECEIVER MODE (MANUAL / STANDING LISTENER)
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
        deactivatePhysicalTapToShare()
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
