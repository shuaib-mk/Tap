package dev.tap.app.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64

object TapProtocol {
    const val PROTOCOL_VERSION = 1
    const val SERVICE_NAME = "TAP"
    const val DEFAULT_PORT = 8989
    const val MAX_CONTROL_MESSAGE_BYTES = 16 * 1024
    const val DEFAULT_CHUNK_BYTES = 256 * 1024 // 256 KB chunk size
    const val BLE_SERVICE_UUID = "0000FA01-0000-1000-8000-00805F9B34FB"
    const val NFC_AID = "F04E464354415000"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }
}

enum class ConnectionMethod {
    NFC_HCE, BLE, QR, DIRECT_IP
}

enum class TransferState {
    IDLE, DISCOVERING, CONNECTING, AWAITING_ACCEPTANCE,
    TRANSFERRING, VERIFYING, COMPLETE, FAILED, CANCELLED
}

enum class TransferDirection {
    SENT, RECEIVED
}

enum class TransferStatus {
    COMPLETED, FAILED, CANCELLED, IN_PROGRESS
}

@Serializable
data class TransferManifest(
    val transferId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String
)

@Serializable
data class SessionOffer(
    val sessionId: String,
    val deviceName: String,
    val publicKeyBase64: String,
    val connectionMethod: ConnectionMethod = ConnectionMethod.QR,
    val protocolVersion: Int = TapProtocol.PROTOCOL_VERSION,
    val hostAddress: String = "",
    val port: Int = TapProtocol.DEFAULT_PORT
)

@Serializable
data class TransferRequest(
    val sessionId: String,
    val senderName: String,
    val files: List<TransferManifest>,
    val totalBytes: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class TransferResponse(
    val sessionId: String,
    val accepted: Boolean,
    val reason: String? = null
)

@Serializable
data class ChunkHeader(
    val transferId: String,
    val fileIndex: Int,
    val chunkIndex: Long,
    val totalChunks: Long,
    val offset: Long,
    val size: Int,
    val nonceBase64: String,
    val isLastChunk: Boolean = false
)

@Serializable
data class TransferComplete(
    val transferId: String,
    val fileIndex: Int,
    val sha256: String,
    val success: Boolean = true
)

@Serializable
sealed class ControlMessage {
    @Serializable
    data class Offer(val offer: SessionOffer) : ControlMessage()

    @Serializable
    data class Request(val request: TransferRequest) : ControlMessage()

    @Serializable
    data class Response(val response: TransferResponse) : ControlMessage()

    @Serializable
    data class Complete(val complete: TransferComplete) : ControlMessage()

    @Serializable
    data class Cancel(val sessionId: String, val reason: String) : ControlMessage()

    fun toJson(): String = TapProtocol.json.encodeToString(this)

    companion object {
        fun fromJson(jsonStr: String): ControlMessage = TapProtocol.json.decodeFromString(jsonStr)
    }
}

fun encodeToQrPayload(offer: SessionOffer): String {
    return TapProtocol.json.encodeToString(offer)
}

fun decodeQrPayload(payload: String): SessionOffer? {
    return try {
        TapProtocol.json.decodeFromString<SessionOffer>(payload)
    } catch (e: Exception) {
        null
    }
}
