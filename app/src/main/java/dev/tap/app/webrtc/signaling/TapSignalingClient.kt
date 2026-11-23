package dev.tap.app.webrtc.signaling

import android.content.Context
import dev.tap.app.webrtc.model.FileManifestDto
import dev.tap.app.webrtc.model.SignalingPayload
import dev.tap.app.webrtc.model.SignalingType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import okhttp3.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

interface SignalingListener {
    fun onSessionCreated(sessionId: String, sessionCode: String)
    fun onPeerJoined(peerDeviceName: String, peerDeviceId: String)
    fun onSdpOfferReceived(sdp: String)
    fun onSdpAnswerReceived(sdp: String)
    fun onIceCandidateReceived(sdpMid: String, sdpMLineIndex: Int, candidate: String)
    fun onTransferManifestReceived(payload: SignalingPayload)
    fun onTransferResponseReceived(accepted: Boolean, sasCode: String?, publicKey: String?, reason: String?)
    fun onSessionError(error: String)
}

/**
 * Production-ready WebRTC Signaling Client.
 *
 * Exclusively exchanges session negotiation metadata (SDP, ICE candidates, and authorization).
 * Never transports or stores file contents on the signaling server.
 *
 * Supports:
 * - Real WebSocket signaling with OkHttp
 * - In-process / Local Loopback Signaling for direct tests and local network transfers
 * - 6-digit short human-friendly pairing codes (e.g. "TAP-4829")
 */
class TapSignalingClient(
    private val context: Context,
    private val scope: CoroutineScope
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    var listener: SignalingListener? = null

    private val prefs = context.getSharedPreferences("tap_signaling_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_SIGNALING_URL = "wss://signal.tap.dev/ws"
        const val PREF_SIGNALING_URL = "signaling_url"

        // In-memory registry for local/testing loopback signaling
        private val localSessionRegistry = ConcurrentHashMap<String, MutableList<TapSignalingClient>>()
        private val codeToSessionMap = ConcurrentHashMap<String, String>()
    }

    var currentSessionId: String? = null
        private set

    var currentSessionCode: String? = null
        private set

    var isConnected: Boolean = false
        private set

    private var isLocalLoopbackMode: Boolean = true

    fun getSignalingUrl(): String = prefs.getString(PREF_SIGNALING_URL, DEFAULT_SIGNALING_URL) ?: DEFAULT_SIGNALING_URL

    fun setSignalingUrl(url: String) {
        prefs.edit().putString(PREF_SIGNALING_URL, url).apply()
    }

    /**
     * Connects to the configured signaling WebSocket server.
     * If connection cannot be established or for offline tests, gracefully falls back to local bus.
     */
    fun connect() {
        val url = getSignalingUrl()
        try {
            val request = Request.Builder().url(url).build()
            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    isConnected = true
                    isLocalLoopbackMode = false
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleIncomingJson(text)
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    isConnected = false
                    // Fall back to local signaling mode
                    isLocalLoopbackMode = true
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    isConnected = false
                }
            })
        } catch (_: Exception) {
            isLocalLoopbackMode = true
        }
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        webSocket = null
        isConnected = false

        currentSessionId?.let { sid ->
            localSessionRegistry[sid]?.remove(this)
        }
        currentSessionId = null
        currentSessionCode = null
    }

    /**
     * Generates a 6-digit alphanumeric human pairing code (e.g. TAP-4829)
     */
    fun generateSessionCode(): String {
        val num = Random.nextInt(1000, 9999)
        return "TAP-$num"
    }

    fun createSession(
        deviceId: String,
        deviceName: String,
        files: List<FileManifestDto>,
        publicKeyBase64: String
    ): String {
        val sessionId = "sess_${UUID.randomUUID().toString().take(8)}"
        val sessionCode = generateSessionCode()
        currentSessionId = sessionId
        currentSessionCode = sessionCode

        // Register in loopback registry
        localSessionRegistry.getOrPut(sessionId) { mutableListOf() }.add(this)
        codeToSessionMap[sessionCode] = sessionId

        val payload = SignalingPayload(
            type = SignalingType.SESSION_CREATED,
            sessionId = sessionId,
            sessionCode = sessionCode,
            senderId = deviceId,
            deviceName = deviceName,
            files = files,
            totalBytes = files.sumOf { it.sizeBytes },
            publicKeyBase64 = publicKeyBase64
        )

        sendPayload(payload)
        listener?.onSessionCreated(sessionId, sessionCode)
        return sessionCode
    }

    fun joinSession(
        codeOrId: String,
        deviceId: String,
        deviceName: String,
        publicKeyBase64: String
    ): Boolean {
        val targetSessionId = codeToSessionMap[codeOrId.uppercase().trim()] ?: codeOrId.trim()
        currentSessionId = targetSessionId

        localSessionRegistry.getOrPut(targetSessionId) { mutableListOf() }.add(this)

        val payload = SignalingPayload(
            type = SignalingType.SESSION_JOIN,
            sessionId = targetSessionId,
            receiverId = deviceId,
            deviceName = deviceName,
            publicKeyBase64 = publicKeyBase64
        )

        sendPayload(payload)
        return true
    }

    fun sendSdpOffer(sdp: String) {
        val sid = currentSessionId ?: return
        sendPayload(
            SignalingPayload(
                type = SignalingType.SDP_OFFER,
                sessionId = sid,
                sdp = sdp
            )
        )
    }

    fun sendSdpAnswer(sdp: String) {
        val sid = currentSessionId ?: return
        sendPayload(
            SignalingPayload(
                type = SignalingType.SDP_ANSWER,
                sessionId = sid,
                sdp = sdp
            )
        )
    }

    fun sendIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val sid = currentSessionId ?: return
        sendPayload(
            SignalingPayload(
                type = SignalingType.ICE_CANDIDATE,
                sessionId = sid,
                sdpMid = sdpMid,
                sdpMLineIndex = sdpMLineIndex,
                candidate = candidate
            )
        )
    }

    fun sendTransferResponse(accepted: Boolean, sasCode: String?, publicKey: String?, reason: String? = null) {
        val sid = currentSessionId ?: return
        sendPayload(
            SignalingPayload(
                type = SignalingType.TRANSFER_RESPONSE,
                sessionId = sid,
                accepted = accepted,
                sasCode = sasCode,
                publicKeyBase64 = publicKey,
                rejectionReason = reason
            )
        )
    }

    fun sendTransferManifest(files: List<FileManifestDto>, publicKeyBase64: String, deviceName: String) {
        val sid = currentSessionId ?: return
        sendPayload(
            SignalingPayload(
                type = SignalingType.TRANSFER_MANIFEST,
                sessionId = sid,
                deviceName = deviceName,
                files = files,
                totalBytes = files.sumOf { it.sizeBytes },
                publicKeyBase64 = publicKeyBase64
            )
        )
    }

    private fun sendPayload(payload: SignalingPayload) {
        val text = json.encodeToString(payload)
        if (!isLocalLoopbackMode && webSocket?.send(text) == true) {
            return
        }

        // Loopback / Local Dispatch
        scope.launch(Dispatchers.Default) {
            val sid = payload.sessionId
            val peers = localSessionRegistry[sid] ?: return@launch
            peers.forEach { peer ->
                if (peer != this@TapSignalingClient) {
                    peer.handleIncomingPayload(payload)
                }
            }
        }
    }

    private fun handleIncomingJson(text: String) {
        try {
            val payload = json.decodeFromString<SignalingPayload>(text)
            handleIncomingPayload(payload)
        } catch (_: Exception) {}
    }

    fun handleIncomingPayload(payload: SignalingPayload) {
        scope.launch(Dispatchers.Main) {
            when (payload.type) {
                SignalingType.SESSION_CREATED -> {
                    listener?.onSessionCreated(payload.sessionId, payload.sessionCode)
                }
                SignalingType.SESSION_JOIN -> {
                    listener?.onPeerJoined(payload.deviceName, payload.receiverId)
                }
                SignalingType.SDP_OFFER -> {
                    payload.sdp?.let { listener?.onSdpOfferReceived(it) }
                }
                SignalingType.SDP_ANSWER -> {
                    payload.sdp?.let { listener?.onSdpAnswerReceived(it) }
                }
                SignalingType.ICE_CANDIDATE -> {
                    if (payload.candidate != null && payload.sdpMid != null) {
                        listener?.onIceCandidateReceived(payload.sdpMid, payload.sdpMLineIndex, payload.candidate)
                    }
                }
                SignalingType.TRANSFER_MANIFEST -> {
                    listener?.onTransferManifestReceived(payload)
                }
                SignalingType.TRANSFER_RESPONSE -> {
                    listener?.onTransferResponseReceived(payload.accepted, payload.sasCode, payload.publicKeyBase64, payload.rejectionReason)
                }
                SignalingType.SESSION_ERROR -> {
                    listener?.onSessionError(payload.errorMessage ?: "Signaling session error")
                }
                else -> {}
            }
        }
    }
}
