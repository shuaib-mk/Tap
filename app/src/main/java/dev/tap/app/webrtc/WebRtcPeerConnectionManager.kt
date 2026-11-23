package dev.tap.app.webrtc

import android.content.Context
import dev.tap.app.webrtc.turn.TurnConfigProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.*
import java.nio.ByteBuffer

enum class WebRtcTransportType {
    UNKNOWN,
    DIRECT_P2P,
    TURN_RELAY,
    OFFLINE_DIRECT
}

enum class WebRtcConnectionState {
    IDLE,
    GATHERING_CANDIDATES,
    CONNECTING,
    CONNECTED,
    TRANSFERRING,
    DISCONNECTED,
    FAILED,
    CLOSED
}

interface WebRtcConnectionListener {
    fun onIceCandidateGenerated(candidate: IceCandidate)
    fun onConnectionStateChanged(state: WebRtcConnectionState, transportType: WebRtcTransportType)
    fun onDataChannelReady(isSender: Boolean)
    fun onBinaryMessageReceived(data: ByteArray)
    fun onError(message: String)
}

/**
 * Encapsulates the WebRTC PeerConnection and DataChannel lifecycle.
 * Manages DTLS encryption, STUN/TURN ICE candidates, and flow-controlled DataChannels.
 */
class WebRtcPeerConnectionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val turnConfigProvider: TurnConfigProvider
) {

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    var listener: WebRtcConnectionListener? = null
    var transportType: WebRtcTransportType = WebRtcTransportType.UNKNOWN
        private set

    var connectionState: WebRtcConnectionState = WebRtcConnectionState.IDLE
        private set

    companion object {
        @Volatile
        private var isInitialized = false

        fun initializeWebRtc(context: Context) {
            if (!isInitialized) {
                synchronized(this) {
                    if (!isInitialized) {
                        try {
                            val options = PeerConnectionFactory.InitializationOptions.builder(context)
                                .setEnableInternalTracer(false)
                                .createInitializationOptions()
                            PeerConnectionFactory.initialize(options)
                            isInitialized = true
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    init {
        initializeWebRtc(context)
        buildFactory()
    }

    private fun buildFactory() {
        val options = PeerConnectionFactory.Options()
        factory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(): Boolean {
        close()
        val iceServers = turnConfigProvider.getIceServers()
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }

        peerConnection = factory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    scope.launch(Dispatchers.Main) {
                        listener?.onIceCandidateGenerated(it)
                    }
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                scope.launch(Dispatchers.Main) {
                    when (newState) {
                        PeerConnection.IceConnectionState.CHECKING -> {
                            updateState(WebRtcConnectionState.CONNECTING)
                        }
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            inspectSelectedCandidatePair()
                            updateState(WebRtcConnectionState.CONNECTED)
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            updateState(WebRtcConnectionState.DISCONNECTED)
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            updateState(WebRtcConnectionState.FAILED)
                        }
                        PeerConnection.IceConnectionState.CLOSED -> {
                            updateState(WebRtcConnectionState.CLOSED)
                        }
                        else -> {}
                    }
                }
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                if (state == PeerConnection.IceGatheringState.GATHERING) {
                    updateState(WebRtcConnectionState.GATHERING_CANDIDATES)
                }
            }

            override fun onDataChannel(channel: DataChannel?) {
                channel?.let {
                    dataChannel = it
                    setupDataChannel(it, isSender = false)
                }
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
        })

        return peerConnection != null
    }

    /**
     * Inspects active ICE candidate pair to detect Direct P2P vs TURN Relay.
     */
    private fun inspectSelectedCandidatePair() {
        peerConnection?.getStats { report ->
            var isRelay = false
            for (stats in report.statsMap.values) {
                if (stats.type == "candidate-pair" && stats.members["state"] == "succeeded") {
                    val localType = stats.members["localCandidateType"]?.toString() ?: ""
                    val remoteType = stats.members["remoteCandidateType"]?.toString() ?: ""
                    if (localType.contains("relay", ignoreCase = true) || remoteType.contains("relay", ignoreCase = true)) {
                        isRelay = true
                        break
                    }
                }
            }
            transportType = if (isRelay) WebRtcTransportType.TURN_RELAY else WebRtcTransportType.DIRECT_P2P
            scope.launch(Dispatchers.Main) {
                listener?.onConnectionStateChanged(connectionState, transportType)
            }
        }
    }

    fun startSenderDataChannel() {
        val pc = peerConnection ?: return
        val init = DataChannel.Init().apply {
            ordered = true
            maxRetransmits = -1
        }
        val channel = pc.createDataChannel("tap_file_transfer", init)
        dataChannel = channel
        setupDataChannel(channel, isSender = true)
    }

    private fun setupDataChannel(channel: DataChannel, isSender: Boolean) {
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                val state = channel.state()
                if (state == DataChannel.State.OPEN) {
                    scope.launch(Dispatchers.Main) {
                        listener?.onDataChannelReady(isSender)
                    }
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                buffer?.let {
                    val bytes = ByteArray(it.data.remaining())
                    it.data.get(bytes)
                    scope.launch(Dispatchers.Default) {
                        listener?.onBinaryMessageReceived(bytes)
                    }
                }
            }
        })
    }

    fun sendBinaryMessage(data: ByteArray): Boolean {
        val channel = dataChannel ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false

        val buffer = DataChannel.Buffer(ByteBuffer.wrap(data), true)
        return channel.send(buffer)
    }

    fun getBufferedAmount(): Long {
        return dataChannel?.bufferedAmount() ?: 0L
    }

    fun createOffer(onOfferCreated: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { sdp ->
                    pc.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            scope.launch(Dispatchers.Main) { onOfferCreated(sdp) }
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(err: String?) {
                            listener?.onError("Failed to set local SDP: $err")
                        }
                    }, sdp)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                listener?.onError("Failed to create SDP offer: $err")
            }
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    fun handleRemoteOffer(offerSdp: String, onAnswerCreated: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: return
        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)

        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                val constraints = MediaConstraints()
                pc.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerDesc: SessionDescription?) {
                        answerDesc?.let { ans ->
                            pc.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    scope.launch(Dispatchers.Main) { onAnswerCreated(ans) }
                                }
                                override fun onCreateFailure(p0: String?) {}
                                override fun onSetFailure(err: String?) {
                                    listener?.onError("Failed to set local answer SDP: $err")
                                }
                            }, ans)
                        }
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(err: String?) {
                        listener?.onError("Failed to create SDP answer: $err")
                    }
                    override fun onSetFailure(p0: String?) {}
                }, constraints)
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String?) {
                listener?.onError("Failed to set remote offer SDP: $err")
            }
        }, remoteDesc)
    }

    fun handleRemoteAnswer(answerSdp: String) {
        val pc = peerConnection ?: return
        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        pc.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String?) {
                listener?.onError("Failed to set remote answer: $err")
            }
        }, remoteDesc)
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidateStr: String) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateStr)
        peerConnection?.addIceCandidate(candidate)
    }

    private fun updateState(newState: WebRtcConnectionState) {
        connectionState = newState
        listener?.onConnectionStateChanged(newState, transportType)
    }

    fun close() {
        try { dataChannel?.close() } catch (_: Exception) {}
        dataChannel = null

        try { peerConnection?.close() } catch (_: Exception) {}
        peerConnection = null
        connectionState = WebRtcConnectionState.IDLE
        transportType = WebRtcTransportType.UNKNOWN
    }
}
