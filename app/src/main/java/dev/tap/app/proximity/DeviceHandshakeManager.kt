package dev.tap.app.proximity

import java.util.UUID

data class ProximityPeer(
    val peerId: String,
    val deviceName: String,
    val hostAddress: String,
    val port: Int,
    val publicKeyBase64: String,
    val rssi: Int = 0,
    val isSamsungDevice: Boolean = false
)

sealed class HandshakeState {
    object Idle : HandshakeState()
    data class Authenticating(val peerDeviceName: String) : HandshakeState()
    data class Authenticated(val peer: ProximityPeer, val sessionKey: ByteArray) : HandshakeState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Authenticated) return false
            return peer == other.peer && sessionKey.contentEquals(other.sessionKey)
        }
        override fun hashCode(): Int = 31 * peer.hashCode() + sessionKey.contentHashCode()
    }
    data class Failed(val error: String) : HandshakeState()
}

/**
 * Manages device-to-device mutual handshake and ephemeral key exchange for proximity sharing.
 */
class DeviceHandshakeManager(private val securityManager: TransferSecurityManager) {

    fun createSessionOffer(deviceName: String, hostAddress: String, port: Int): ProximityPeer {
        return ProximityPeer(
            peerId = UUID.randomUUID().toString(),
            deviceName = deviceName,
            hostAddress = hostAddress,
            port = port,
            publicKeyBase64 = securityManager.getPublicKeyBase64(),
            isSamsungDevice = android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        )
    }

    fun completeHandshake(peer: ProximityPeer): HandshakeState {
        return try {
            val sessionKey = securityManager.deriveSessionKey(peer.publicKeyBase64)
            HandshakeState.Authenticated(peer, sessionKey)
        } catch (e: Exception) {
            HandshakeState.Failed("Handshake key agreement failed: ${e.message}")
        }
    }
}
