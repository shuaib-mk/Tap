package dev.tap.app.proximity

import java.util.UUID

data class ProximitySession(
    val sessionId: String = UUID.randomUUID().toString(),
    val remoteDeviceName: String,
    val fileCount: Int,
    val totalBytes: Long,
    val isSender: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean = false
)

/**
 * Tracks active and completed proximity file sharing sessions.
 */
class TransferSessionRepository {
    private val sessions = mutableListOf<ProximitySession>()

    fun recordSession(session: ProximitySession) {
        sessions.add(session)
    }

    fun getAllSessions(): List<ProximitySession> = sessions.toList()

    fun clearSessions() {
        sessions.clear()
    }
}
