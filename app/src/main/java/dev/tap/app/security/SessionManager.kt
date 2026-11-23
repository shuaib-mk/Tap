package dev.tap.app.security

import dev.tap.app.protocol.ConnectionMethod
import dev.tap.app.protocol.SessionOffer
import dev.tap.app.protocol.TransferState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyPair
import java.security.PublicKey
import javax.crypto.SecretKey

/**
 * Manages active peer sessions, key exchange state, and replay attack prevention.
 */
data class TapSession(
    val sessionId: String,
    val localKeyPair: KeyPair,
    val deviceName: String,
    val connectionMethod: ConnectionMethod,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = createdAt + SESSION_TTL_MS,
    var peerPublicKey: PublicKey? = null,
    var peerName: String? = null,
    var sharedSecret: SecretKey? = null,
    var state: TransferState = TransferState.IDLE,
    var nonceCounter: Long = 0L
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt

    fun nextNonce(): ByteArray {
        val counter = ++nonceCounter
        return TapSecurity.generateNonceFromCounter(counter)
    }

    fun toSessionOffer(hostAddress: String = "", port: Int = 8989): SessionOffer {
        return SessionOffer(
            sessionId = sessionId,
            deviceName = deviceName,
            publicKeyBase64 = TapSecurity.encodePublicKey(localKeyPair.public),
            connectionMethod = connectionMethod,
            hostAddress = hostAddress,
            port = port
        )
    }

    companion object {
        const val SESSION_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }
}

class SessionManager {
    private val mutex = Mutex()
    private val activeSessions = mutableMapOf<String, TapSession>()

    suspend fun createSession(deviceName: String, method: ConnectionMethod): TapSession = mutex.withLock {
        cleanupExpired()
        val sessionId = TapSecurity.generateSessionId()
        val keyPair = TapSecurity.generateKeyPair()
        val session = TapSession(
            sessionId = sessionId,
            localKeyPair = keyPair,
            deviceName = deviceName,
            connectionMethod = method
        )
        activeSessions[sessionId] = session
        session
    }

    suspend fun getSession(sessionId: String): TapSession? = mutex.withLock {
        val session = activeSessions[sessionId]
        if (session != null && session.isExpired) {
            activeSessions.remove(sessionId)
            null
        } else {
            session
        }
    }

    suspend fun completeHandshake(
        sessionId: String,
        peerPublicKeyBase64: String,
        peerName: String
    ): SecretKey? = mutex.withLock {
        val session = activeSessions[sessionId] ?: return null
        if (session.isExpired) {
            activeSessions.remove(sessionId)
            return null
        }
        val peerKey = TapSecurity.decodePublicKey(peerPublicKeyBase64)
        val secret = TapSecurity.deriveSharedSecret(session.localKeyPair.private, peerKey)
        session.peerPublicKey = peerKey
        session.peerName = peerName
        session.sharedSecret = secret
        session.state = TransferState.CONNECTING
        secret
    }

    suspend fun updateState(sessionId: String, state: TransferState) = mutex.withLock {
        activeSessions[sessionId]?.state = state
    }

    suspend fun expireSession(sessionId: String) = mutex.withLock {
        activeSessions.remove(sessionId)
    }

    private fun cleanupExpired() {
        val now = System.currentTimeMillis()
        activeSessions.entries.removeAll { it.value.expiresAt < now }
    }
}
