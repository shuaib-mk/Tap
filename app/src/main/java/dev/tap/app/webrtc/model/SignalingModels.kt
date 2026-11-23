package dev.tap.app.webrtc.model

import kotlinx.serialization.Serializable

@Serializable
enum class SignalingType {
    SESSION_CREATE,
    SESSION_CREATED,
    SESSION_JOIN,
    SESSION_JOINED,
    SDP_OFFER,
    SDP_ANSWER,
    ICE_CANDIDATE,
    TRANSFER_MANIFEST,
    TRANSFER_RESPONSE,
    TRANSFER_PROGRESS,
    TRANSFER_CANCEL,
    SESSION_ERROR,
    HEARTBEAT
}

@Serializable
data class FileManifestDto(
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val sha256: String
)

@Serializable
data class SignalingPayload(
    val type: SignalingType,
    val sessionId: String,
    val sessionCode: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val deviceName: String = "",
    val sdp: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int = 0,
    val candidate: String? = null,
    val files: List<FileManifestDto> = emptyList(),
    val totalBytes: Long = 0L,
    val accepted: Boolean = false,
    val rejectionReason: String? = null,
    val sasCode: String? = null,
    val publicKeyBase64: String? = null,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class TurnServerConfig(
    val uri: String,
    val username: String? = null,
    val credential: String? = null
)
