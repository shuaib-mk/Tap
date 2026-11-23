package dev.tap.app.proximity

/**
 * Thread-safe bridge holding active proximity file sharing session payloads for NFC HCE.
 *
 * When the user activates Physical Tap to Share with staged files, this repository
 * holds the multi-record NDEF message. [dev.tap.app.nfc.TapHostApduService] checks this
 * repository first:
 * - If an active proximity session exists: serves the Proximity Bootstrap NDEF.
 * - If no session exists: serves the Digital Contact Card vCard.
 *
 * This guarantees 100% coexistence between Contact Card sharing and Physical Tap to Share!
 */
object ProximityNdefRepository {

    @Volatile
    var activeSessionNdef: ByteArray? = null
        private set

    @Volatile
    var activeWebFallbackUrl: String? = null
        private set

    @Volatile
    var activeSessionId: String? = null
        private set

    fun setProximitySession(
        sessionId: String,
        ndefBytes: ByteArray,
        webFallbackUrl: String? = null
    ) {
        activeSessionId = sessionId
        activeSessionNdef = ndefBytes
        activeWebFallbackUrl = webFallbackUrl
    }

    fun clearProximitySession() {
        activeSessionId = null
        activeSessionNdef = null
        activeWebFallbackUrl = null
    }

    fun hasActiveSession(): Boolean = activeSessionNdef != null
}
