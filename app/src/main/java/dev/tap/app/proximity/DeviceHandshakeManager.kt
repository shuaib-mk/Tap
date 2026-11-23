package dev.tap.app.proximity

import java.util.UUID

/**
 * File manifest for Proximity transfers.
 */
data class ProximityFileManifest(
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val sha256: String
)

/**
 * Proximity session offer sent by the sender to bootstrap the transfer.
 */
data class ProximityHandshakeOffer(
    val sessionId: String,
    val senderDeviceName: String,
    val senderDeviceId: String,
    val senderPublicKeyBase64: String,
    val hostAddress: String,
    val port: Int,
    val files: List<ProximityFileManifest>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Proximity response sent by the receiver after evaluation and user approval.
 */
data class ProximityHandshakeResponse(
    val sessionId: String,
    val receiverDeviceName: String,
    val receiverDeviceId: String,
    val receiverPublicKeyBase64: String,
    val accepted: Boolean,
    val rejectionReason: String? = null,
    val sasCode: String = ""
)

sealed class HandshakeState {
    object Idle : HandshakeState()
    data class AwaitingApproval(
        val offer: ProximityHandshakeOffer,
        val sasCode: String
    ) : HandshakeState()
    data class Approved(
        val offer: ProximityHandshakeOffer,
        val sessionKey: ByteArray,
        val sasCode: String
    ) : HandshakeState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Approved) return false
            return offer == other.offer && sessionKey.contentEquals(other.sessionKey) && sasCode == other.sasCode
        }
        override fun hashCode(): Int = 31 * offer.hashCode() + sessionKey.contentHashCode() + sasCode.hashCode()
    }
    data class Rejected(val reason: String) : HandshakeState()
    data class Failed(val error: String) : HandshakeState()
}

/**
 * Manages proximity device-to-device mutual handshake, out-of-band SAS code computation,
 * and ephemeral session key derivation.
 */
class DeviceHandshakeManager(private val securityManager: TransferSecurityManager) {

    fun createSessionOffer(
        deviceName: String,
        deviceId: String,
        hostAddress: String,
        port: Int,
        files: List<ProximityFileManifest>,
        sessionId: String = UUID.randomUUID().toString()
    ): ProximityHandshakeOffer {
        return ProximityHandshakeOffer(
            sessionId = sessionId,
            senderDeviceName = deviceName,
            senderDeviceId = deviceId,
            senderPublicKeyBase64 = securityManager.getPublicKeyBase64(),
            hostAddress = hostAddress,
            port = port,
            files = files
        )
    }

    /**
     * Evaluates an incoming offer on the receiver device, derives the session key and SAS code,
     * and prepares the AwaitingApproval state.
     */
    fun evaluateIncomingOffer(
        offer: ProximityHandshakeOffer,
        receiverDeviceId: String
    ): HandshakeState {
        return try {
            val sessionKey = securityManager.deriveSessionKey(
                remotePublicKeyBase64 = offer.senderPublicKeyBase64,
                sessionId = offer.sessionId,
                senderId = offer.senderDeviceId,
                receiverId = receiverDeviceId
            )
            val sasCode = securityManager.calculateSasCode(
                remotePublicKeyBase64 = offer.senderPublicKeyBase64,
                sessionId = offer.sessionId
            )
            HandshakeState.AwaitingApproval(offer, sasCode)
        } catch (e: Exception) {
            HandshakeState.Failed("Cryptographic handshake derivation failed: ${e.message}")
        }
    }

    /**
     * Completes handshake on the sender device after receiving the receiver's response.
     */
    fun processReceiverResponse(
        offer: ProximityHandshakeOffer,
        response: ProximityHandshakeResponse
    ): HandshakeState {
        if (!response.accepted) {
            return HandshakeState.Rejected(response.rejectionReason ?: "Transfer declined by receiver.")
        }
        return try {
            val sessionKey = securityManager.deriveSessionKey(
                remotePublicKeyBase64 = response.receiverPublicKeyBase64,
                sessionId = offer.sessionId,
                senderId = offer.senderDeviceId,
                receiverId = response.receiverDeviceId
            )
            val expectedSas = securityManager.calculateSasCode(
                remotePublicKeyBase64 = response.receiverPublicKeyBase64,
                sessionId = offer.sessionId
            )
            if (response.sasCode.isNotBlank() && response.sasCode != expectedSas) {
                return HandshakeState.Failed("SAS verification mismatch. Possible MITM attack detected.")
            }
            HandshakeState.Approved(offer, sessionKey, expectedSas)
        } catch (e: Exception) {
            HandshakeState.Failed("Failed to complete receiver handshake: ${e.message}")
        }
    }
}
