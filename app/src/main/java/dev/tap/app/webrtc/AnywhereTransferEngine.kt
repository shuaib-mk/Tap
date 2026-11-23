package dev.tap.app.webrtc

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.Log
import dev.tap.app.discovery.nfc.NfcSessionExchange
import dev.tap.app.history.TransferHistoryRepository
import dev.tap.app.history.data.TransferRecord
import dev.tap.app.protocol.TransferDirection
import dev.tap.app.protocol.TransferStatus
import dev.tap.app.proximity.*
import dev.tap.app.webrtc.model.FileManifestDto
import dev.tap.app.webrtc.model.SignalingPayload
import dev.tap.app.webrtc.signaling.SignalingListener
import dev.tap.app.webrtc.signaling.TapSignalingClient
import dev.tap.app.webrtc.transfer.WebRtcChunkTransport
import dev.tap.app.webrtc.turn.TurnConfigProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.IceCandidate
import java.util.UUID

sealed class AnywhereEngineState {
    object Idle : AnywhereEngineState()
    object Staging : AnywhereEngineState()
    data class CreatingSession(val status: String) : AnywhereEngineState()
    data class WaitingForPeer(val sessionCode: String, val shareLink: String) : AnywhereEngineState()
    data class PeerConnected(
        val peerDeviceName: String,
        val peerDeviceId: String,
        val sessionCode: String,
        val sasCode: String
    ) : AnywhereEngineState()
    data class IncomingTransferRequest(
        val senderDeviceName: String,
        val senderDeviceId: String,
        val files: List<FileManifestDto>,
        val totalBytes: Long,
        val sasCode: String,
        val continuation: CompletableDeferred<Boolean>
    ) : AnywhereEngineState()
    data class Connecting(val peerName: String, val transportType: WebRtcTransportType) : AnywhereEngineState()
    data class Transferring(val progress: ProximityProgress, val transportType: WebRtcTransportType) : AnywhereEngineState()
    data class Completed(val fileCount: Int, val totalBytes: Long) : AnywhereEngineState()
    data class Error(val message: String) : AnywhereEngineState()
}

/**
 * Unified Anywhere File Transfer Engine for TAP.
 *
 * Coordinates:
 * - True Anywhere Transfer over WebRTC DataChannels (across Wi-Fi, 4G, 5G, NATs)
 * - Automatic STUN NAT discovery and COTURN relay fallback
 * - 6-character short session codes (e.g. TAP-4829) and instant share links
 * - Dual-channel signaling (public relay + local subnet UDP multicast)
 * - Ephemeral ECDH key agreement and 6-digit SAS verification
 * - Resumable 64 KB chunk transport with streaming SHA-256 validation
 * - Zero-install NFC proximity initiation & Physical Tap to Share
 * - Offline Nearby fallback over autonomous Wi-Fi Direct when internet is down
 */
class AnywhereTransferEngine(
    private val context: Context,
    val scope: CoroutineScope
) : WebRtcConnectionListener, SignalingListener {

    val deviceId = "tap_${UUID.randomUUID().toString().take(8)}"
    val deviceName: String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    val securityManager = TransferSecurityManager()
    val turnConfigProvider = TurnConfigProvider(context)
    val signalingClient = TapSignalingClient(context, scope)
    val peerConnectionManager = WebRtcPeerConnectionManager(context, scope, turnConfigProvider)
    val chunkTransport = WebRtcChunkTransport(context, peerConnectionManager, securityManager)
    val p2pManager = ProximityP2pManager(context)
    val proximityEngine = ProximitySharingEngine(context)
    private val historyRepo = TransferHistoryRepository.getInstance(context)

    private val _engineState = MutableStateFlow<AnywhereEngineState>(AnywhereEngineState.Idle)
    val engineState: StateFlow<AnywhereEngineState> = _engineState.asStateFlow()

    private val _stagedFiles = MutableStateFlow<List<ProximityFile>>(emptyList())
    val stagedFiles: StateFlow<List<ProximityFile>> = _stagedFiles.asStateFlow()

    val progress: StateFlow<ProximityProgress> = chunkTransport.progress

    private var pendingManifests: List<FileManifestDto> = emptyList()
    private var remotePublicKeyBase64: String? = null
    private var remoteDeviceName: String = "Remote Device"
    private var remoteDeviceId: String = "receiver"

    // Synchronization flags to eliminate race conditions
    private var isDataChannelReady = false
    private var isPeerAccepted = false

    init {
        signalingClient.listener = this
        peerConnectionManager.listener = this
    }

    val isInternetAvailable: Boolean
        get() {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

    // ---------------------------------------------------------------------------------------------
    // FILE STAGING
    // ---------------------------------------------------------------------------------------------
    fun stageUris(uris: List<Uri>) {
        val current = _stagedFiles.value.toMutableList()
        uris.forEach { uri ->
            ProximityFile.fromUri(context, uri)?.let { file ->
                if (current.none { it.uri == file.uri }) current.add(file)
            }
        }
        _stagedFiles.value = current
        if (_engineState.value is AnywhereEngineState.Idle) {
            _engineState.value = AnywhereEngineState.Staging
        }
    }

    fun removeStagedFile(file: ProximityFile) {
        _stagedFiles.value = _stagedFiles.value.filter { it.uri != file.uri }
        if (_stagedFiles.value.isEmpty()) {
            _engineState.value = AnywhereEngineState.Idle
        }
    }

    fun clearStagedFiles() {
        _stagedFiles.value = emptyList()
        _engineState.value = AnywhereEngineState.Idle
        ProximityNdefRepository.clearProximitySession()
    }

    // ---------------------------------------------------------------------------------------------
    // SENDER WORKFLOW
    // ---------------------------------------------------------------------------------------------
    fun startAnywhereShare() {
        val files = _stagedFiles.value
        if (files.isEmpty()) {
            _engineState.value = AnywhereEngineState.Error("No files selected to share.")
            return
        }

        isDataChannelReady = false
        isPeerAccepted = false

        _engineState.value = AnywhereEngineState.CreatingSession("Initializing transfer network...")

        scope.launch(Dispatchers.IO) {
            try {
                // Compute manifests with SHA-256
                val manifests = files.map { file ->
                    val sha = context.contentResolver.openInputStream(file.uri)?.use { stream ->
                        TransferSecurityManager.calculateStreamHash(stream)
                    } ?: "0000000000000000000000000000000000000000000000000000000000000000"
                    FileManifestDto(
                        name = file.name,
                        sizeBytes = file.sizeBytes,
                        mimeType = file.mimeType,
                        sha256 = sha
                    )
                }
                pendingManifests = manifests

                val pubKey = securityManager.getPublicKeyBase64()
                val code = signalingClient.createSession(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    files = manifests,
                    publicKeyBase64 = pubKey
                )

                // Initialize WebRTC PeerConnection
                peerConnectionManager.createPeerConnection()
                peerConnectionManager.startSenderDataChannel()

                // Generate Offer SDP
                peerConnectionManager.createOffer { sdp ->
                    signalingClient.sendSdpOffer(sdp.description)
                }

                // Update Proximity NDEF repository so bringing another phone close initiates via NFC!
                val shareUrl = "https://tap.dev/s/$code"
                val proximityPayload = """{"sessionId":"${signalingClient.currentSessionId}","sessionCode":"$code","senderDeviceName":"$deviceName","host":"$shareUrl"}"""
                val proximityNdef = NfcSessionExchange.createProximityNdefMessage(proximityPayload, shareUrl)
                ProximityNdefRepository.setProximitySession(
                    sessionId = signalingClient.currentSessionId ?: code,
                    ndefBytes = proximityNdef.toByteArray(),
                    webFallbackUrl = shareUrl
                )

                _engineState.value = AnywhereEngineState.WaitingForPeer(sessionCode = code, shareLink = shareUrl)
            } catch (e: Exception) {
                _engineState.value = AnywhereEngineState.Error("Failed to initiate transfer session: ${e.message}")
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // RECEIVER WORKFLOW
    // ---------------------------------------------------------------------------------------------
    fun joinAnywhereShare(codeOrId: String) {
        val normalized = TapSignalingClient.normalizeSessionCode(codeOrId)
        _engineState.value = AnywhereEngineState.CreatingSession("Connecting to $normalized...")

        isDataChannelReady = false
        isPeerAccepted = false

        scope.launch(Dispatchers.IO) {
            peerConnectionManager.createPeerConnection()
            val pubKey = securityManager.getPublicKeyBase64()
            signalingClient.joinSession(
                codeOrId = normalized,
                deviceId = deviceId,
                deviceName = deviceName,
                publicKeyBase64 = pubKey
            )
        }
    }

    fun respondToTransferConsent(accepted: Boolean) {
        val currentState = _engineState.value
        if (currentState is AnywhereEngineState.IncomingTransferRequest) {
            currentState.continuation.complete(accepted)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // OFFLINE WI-FI DIRECT FALLBACK
    // ---------------------------------------------------------------------------------------------
    fun startOfflineNearbyShare() {
        _engineState.value = AnywhereEngineState.Connecting("Creating autonomous Wi-Fi Direct group...", WebRtcTransportType.OFFLINE_DIRECT)
        if (p2pManager.isSupported) {
            p2pManager.createAutonomousGroup { ownerIp ->
                if (ownerIp.isNotBlank()) {
                    proximityEngine.activatePhysicalTapToShare(scope)
                } else {
                    _engineState.value = AnywhereEngineState.Error("Failed to create Wi-Fi Direct group. Ensure Wi-Fi is enabled.")
                }
            }
        } else {
            proximityEngine.activatePhysicalTapToShare(scope)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // SIGNALING LISTENER CALLBACKS
    // ---------------------------------------------------------------------------------------------
    override fun onSessionCreated(sessionId: String, sessionCode: String) {
        val shareLink = "https://tap.dev/s/$sessionCode"
        _engineState.value = AnywhereEngineState.WaitingForPeer(sessionCode, shareLink)
    }

    override fun onPeerJoined(peerDeviceName: String, peerDeviceId: String, peerPublicKeyBase64: String?) {
        this.remoteDeviceName = peerDeviceName
        this.remoteDeviceId = peerDeviceId
        this.remotePublicKeyBase64 = peerPublicKeyBase64

        // Derive SAS code immediately so sender sees the verification code
        val sid = signalingClient.currentSessionId ?: ""
        val sas = if (!peerPublicKeyBase64.isNullOrBlank()) {
            securityManager.calculateSasCode(peerPublicKeyBase64, sid)
        } else "000 000"

        _engineState.value = AnywhereEngineState.PeerConnected(
            peerDeviceName = peerDeviceName,
            peerDeviceId = peerDeviceId,
            sessionCode = signalingClient.currentSessionCode ?: "",
            sasCode = sas
        )

        // 1. Send transfer manifest to peer
        signalingClient.sendTransferManifest(pendingManifests, securityManager.getPublicKeyBase64(), deviceName)

        // 2. Re-send SDP Offer to peer now that peer is actively listening
        val offer = peerConnectionManager.lastGeneratedOffer
        if (offer != null) {
            signalingClient.sendSdpOffer(offer.description)
        } else {
            peerConnectionManager.createOffer { sdp ->
                signalingClient.sendSdpOffer(sdp.description)
            }
        }

        // 3. Re-send all gathered local ICE candidates
        peerConnectionManager.getGatheredLocalCandidates().forEach { cand ->
            signalingClient.sendIceCandidate(cand.sdpMid, cand.sdpMLineIndex, cand.sdp)
        }
    }

    override fun onSdpOfferReceived(sdp: String) {
        peerConnectionManager.handleRemoteOffer(sdp) { answer ->
            signalingClient.sendSdpAnswer(answer.description)
        }
    }

    override fun onSdpAnswerReceived(sdp: String) {
        peerConnectionManager.handleRemoteAnswer(sdp)
    }

    override fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        peerConnectionManager.addRemoteIceCandidate(sdpMid, sdpMLineIndex, candidate)
    }

    override fun onTransferManifestReceived(payload: SignalingPayload) {
        val sid = payload.sessionId
        val remotePub = payload.publicKeyBase64 ?: ""
        remotePublicKeyBase64 = remotePub
        remoteDeviceName = payload.deviceName
        remoteDeviceId = payload.senderId

        // Ephemeral ECDH + SAS
        val sas = if (remotePub.isNotBlank()) securityManager.calculateSasCode(remotePub, sid) else "000 000"
        val derivedKey = if (remotePub.isNotBlank()) {
            securityManager.deriveSessionKey(remotePub, sid, payload.senderId, deviceId)
        } else ByteArray(32)

        chunkTransport.setSessionParameters(derivedKey, sid, sas)
        chunkTransport.setExpectedTransferTotals(payload.files.size, payload.totalBytes)

        val deferred = CompletableDeferred<Boolean>()
        _engineState.value = AnywhereEngineState.IncomingTransferRequest(
            senderDeviceName = payload.deviceName,
            senderDeviceId = payload.senderId,
            files = payload.files,
            totalBytes = payload.totalBytes,
            sasCode = sas,
            continuation = deferred
        )

        scope.launch(Dispatchers.Main) {
            val accepted = deferred.await()
            signalingClient.sendTransferResponse(
                accepted = accepted,
                sasCode = sas,
                publicKey = securityManager.getPublicKeyBase64()
            )

            if (!accepted) {
                _engineState.value = AnywhereEngineState.Idle
                peerConnectionManager.close()
            } else {
                _engineState.value = if (isDataChannelReady) {
                    AnywhereEngineState.Transferring(chunkTransport.progress.value, peerConnectionManager.transportType)
                } else {
                    AnywhereEngineState.Connecting(payload.deviceName, peerConnectionManager.transportType)
                }
            }
        }
    }

    override fun onTransferResponseReceived(accepted: Boolean, sasCode: String?, publicKey: String?, reason: String?) {
        if (!accepted) {
            _engineState.value = AnywhereEngineState.Error("Transfer declined by recipient: ${reason ?: "Declined"}")
            peerConnectionManager.close()
            return
        }

        val sid = signalingClient.currentSessionId ?: ""
        if (!publicKey.isNullOrBlank()) {
            val sas = securityManager.calculateSasCode(publicKey, sid)
            val key = securityManager.deriveSessionKey(publicKey, sid, deviceId, remoteDeviceId)
            chunkTransport.setSessionParameters(key, sid, sas)
        }

        isPeerAccepted = true
        _engineState.value = if (isDataChannelReady) {
            AnywhereEngineState.Transferring(chunkTransport.progress.value, peerConnectionManager.transportType)
        } else {
            AnywhereEngineState.Connecting(remoteDeviceName, peerConnectionManager.transportType)
        }
        checkAndStartSenderTransfer()
    }

    override fun onSessionError(error: String) {
        _engineState.value = AnywhereEngineState.Error(error)
    }

    // ---------------------------------------------------------------------------------------------
    // WEBRTC CONNECTION LISTENER CALLBACKS
    // ---------------------------------------------------------------------------------------------
    override fun onIceCandidateGenerated(candidate: IceCandidate) {
        signalingClient.sendIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
    }

    override fun onConnectionStateChanged(state: WebRtcConnectionState, transportType: WebRtcTransportType) {
        when (state) {
            WebRtcConnectionState.CONNECTED -> {
                Log.d("AnywhereEngine", "WebRTC Connection established via $transportType")
            }
            WebRtcConnectionState.DISCONNECTED -> {
                // Transient state during mobile handover or candidate gathering - await automatic reconnection
                Log.w("AnywhereEngine", "WebRTC temporary ICE disconnection, awaiting reconnection...")
            }
            WebRtcConnectionState.FAILED -> {
                if (_engineState.value !is AnywhereEngineState.Completed) {
                    _engineState.value = AnywhereEngineState.Error("WebRTC connection lost.")
                }
            }
            else -> {}
        }
    }

    override fun onDataChannelReady(isSender: Boolean) {
        isDataChannelReady = true
        if (isSender) {
            checkAndStartSenderTransfer()
        } else {
            _engineState.value = AnywhereEngineState.Transferring(chunkTransport.progress.value, peerConnectionManager.transportType)
        }
    }

    private fun checkAndStartSenderTransfer() {
        if (isDataChannelReady && isPeerAccepted) {
            _engineState.value = AnywhereEngineState.Transferring(chunkTransport.progress.value, peerConnectionManager.transportType)
            chunkTransport.sendFiles(
                scope = scope,
                files = _stagedFiles.value,
                manifests = pendingManifests,
                onCompleted = {
                    val count = _stagedFiles.value.size
                    val bytes = _stagedFiles.value.sumOf { it.sizeBytes }
                    ProximityNdefRepository.clearProximitySession()
                    _engineState.value = AnywhereEngineState.Completed(count, bytes)
                    scope.launch {
                        historyRepo.insert(
                            TransferRecord(
                                transferId = signalingClient.currentSessionId ?: UUID.randomUUID().toString(),
                                fileName = if (count == 1) _stagedFiles.value.first().name else "$count files",
                                fileSize = bytes,
                                mimeType = "application/octet-stream",
                                direction = TransferDirection.SENT,
                                status = TransferStatus.COMPLETED,
                                peerName = remoteDeviceName,
                                connectionMethod = "WebRTC Anywhere"
                            )
                        )
                    }
                },
                onError = { err ->
                    _engineState.value = AnywhereEngineState.Error(err)
                }
            )
        }
    }

    override fun onBinaryMessageReceived(data: ByteArray) {
        chunkTransport.handleIncomingBinaryFrame(
            bytes = data,
            onFileVerified = { name -> },
            onAllCompleted = { count, bytes ->
                ProximityNdefRepository.clearProximitySession()
                _engineState.value = AnywhereEngineState.Completed(count, bytes)
                scope.launch {
                    historyRepo.insert(
                        TransferRecord(
                            transferId = signalingClient.currentSessionId ?: UUID.randomUUID().toString(),
                            fileName = "$count files",
                            fileSize = bytes,
                            mimeType = "application/octet-stream",
                            direction = TransferDirection.RECEIVED,
                            status = TransferStatus.COMPLETED,
                            peerName = remoteDeviceName,
                            connectionMethod = "WebRTC Anywhere"
                        )
                    )
                }
            },
            onError = { err ->
                _engineState.value = AnywhereEngineState.Error(err)
            }
        )
    }

    override fun onError(message: String) {
        _engineState.value = AnywhereEngineState.Error(message)
    }

    fun cancelActiveTransfer() {
        isDataChannelReady = false
        isPeerAccepted = false
        ProximityNdefRepository.clearProximitySession()
        chunkTransport.cancelTransfer()
        peerConnectionManager.close()
        signalingClient.disconnect()
        _engineState.value = AnywhereEngineState.Idle
    }

    fun pauseTransfer() {
        chunkTransport.pauseTransfer()
    }

    fun resumeTransfer() {
        chunkTransport.resumeTransfer()
    }
}
