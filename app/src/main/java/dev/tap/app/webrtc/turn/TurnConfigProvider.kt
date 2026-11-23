package dev.tap.app.webrtc.turn

import android.content.Context
import dev.tap.app.webrtc.model.TurnServerConfig
import org.webrtc.PeerConnection
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * Manages STUN and TURN server configurations for WebRTC ICE candidate gathering.
 * 
 * Supports:
 * - Default public STUN servers for direct NAT traversal.
 * - Dynamic COTURN ephemeral credentials using HMAC-SHA1 shared secrets.
 * - Managed TURN service integration without hardcoded credentials in the APK.
 * - User-configured self-hosted COTURN settings stored in SharedPreferences.
 */
class TurnConfigProvider(private val context: Context) {

    private val prefs = context.getSharedPreferences("tap_turn_prefs", Context.MODE_PRIVATE)

    companion object {
        const val PREF_CUSTOM_TURN_ENABLED = "custom_turn_enabled"
        const val PREF_TURN_HOST = "turn_host"
        const val PREF_TURN_PORT = "turn_port"
        const val PREF_TURN_SECRET = "turn_secret"
        const val PREF_TURN_USERNAME = "turn_username"
        const val PREF_TURN_PASSWORD = "turn_password"
        const val PREF_USE_TLS = "turn_use_tls"

        val DEFAULT_STUN_SERVERS = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302",
            "stun:stun2.l.google.com:19302",
            "stun:stun.cloudflare.com:3478"
        )

        /**
         * Generates time-limited ephemeral COTURN credentials from a shared secret.
         * Username format: <expiry_timestamp>:<user_identity>
         * Password format: Base64(HMAC-SHA1(secret, username))
         */
        fun generateEphemeralCredentials(
            secret: String,
            identity: String = "tap_user",
            ttlSeconds: Long = 86400
        ): Pair<String, String> {
            val expiryTime = (System.currentTimeMillis() / 1000) + ttlSeconds
            val username = "$expiryTime:$identity"
            val mac = Mac.getInstance("HmacSHA1")
            val secretKey = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA1")
            mac.init(secretKey)
            val hmacBytes = mac.doFinal(username.toByteArray(Charsets.UTF_8))
            val password = java.util.Base64.getEncoder().encodeToString(hmacBytes)
            return Pair(username, password)
        }
    }

    /**
     * Returns the full list of PeerConnection.IceServer instances for ICE gathering.
     */
    fun getIceServers(): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()

        // 1. Always add STUN servers for direct P2P NAT resolution
        DEFAULT_STUN_SERVERS.forEach { uri ->
            servers.add(
                PeerConnection.IceServer.builder(uri).createIceServer()
            )
        }

        // 2. Add custom TURN server if enabled
        val isCustomEnabled = prefs.getBoolean(PREF_CUSTOM_TURN_ENABLED, false)
        if (isCustomEnabled) {
            val host = prefs.getString(PREF_TURN_HOST, null)
            val port = prefs.getInt(PREF_TURN_PORT, 3478)
            val useTls = prefs.getBoolean(PREF_USE_TLS, false)
            val secret = prefs.getString(PREF_TURN_SECRET, null)

            if (!host.isNullOrBlank()) {
                val scheme = if (useTls) "turns" else "turn"
                val udpUri = "$scheme:$host:$port?transport=udp"
                val tcpUri = "$scheme:$host:$port?transport=tcp"

                val (user, pass) = if (!secret.isNullOrBlank()) {
                    generateEphemeralCredentials(secret)
                } else {
                    Pair(
                        prefs.getString(PREF_TURN_USERNAME, "") ?: "",
                        prefs.getString(PREF_TURN_PASSWORD, "") ?: ""
                    )
                }

                if (user.isNotBlank() && pass.isNotBlank()) {
                    servers.add(
                        PeerConnection.IceServer.builder(udpUri)
                            .setUsername(user)
                            .setPassword(pass)
                            .createIceServer()
                    )
                    servers.add(
                        PeerConnection.IceServer.builder(tcpUri)
                            .setUsername(user)
                            .setPassword(pass)
                            .createIceServer()
                    )
                }
            }
        }

        return servers
    }

    fun saveCustomTurnConfig(
        enabled: Boolean,
        host: String,
        port: Int,
        secret: String?,
        username: String?,
        password: String?,
        useTls: Boolean
    ) {
        prefs.edit().apply {
            putBoolean(PREF_CUSTOM_TURN_ENABLED, enabled)
            putString(PREF_TURN_HOST, host)
            putInt(PREF_TURN_PORT, port)
            putString(PREF_TURN_SECRET, secret)
            putString(PREF_TURN_USERNAME, username)
            putString(PREF_TURN_PASSWORD, password)
            putBoolean(PREF_USE_TLS, useTls)
            apply()
        }
    }

    fun isCustomTurnEnabled(): Boolean = prefs.getBoolean(PREF_CUSTOM_TURN_ENABLED, false)
    fun getTurnHost(): String = prefs.getString(PREF_TURN_HOST, "") ?: ""
    fun getTurnPort(): Int = prefs.getInt(PREF_TURN_PORT, 3478)
}
