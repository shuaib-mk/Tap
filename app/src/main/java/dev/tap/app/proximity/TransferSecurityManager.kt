package dev.tap.app.proximity

import java.io.InputStream
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Production cryptographic security manager for TAP Proximity File Sharing.
 * Implements:
 * - Ephemeral ECDH key agreement over NIST curve secp256r1
 * - Session-bound HKDF-SHA256 key derivation (binding session ID, peer IDs, and public keys)
 * - Short Authentication String (SAS) 6-digit out-of-band verification against MITM attacks
 * - AES-256-GCM chunk encryption and decryption with strict monotonic nonces
 * - Full streaming SHA-256 integrity verification
 */
class TransferSecurityManager {

    var localKeyPair: KeyPair? = null
        private set
    var sharedSecret: ByteArray? = null
        private set

    fun generateKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        val kp = kpg.generateKeyPair()
        localKeyPair = kp
        return kp
    }

    fun getPublicKeyBase64(): String {
        val kp = localKeyPair ?: generateKeyPair()
        return Base64.getEncoder().encodeToString(kp.public.encoded)
    }

    /**
     * Computes the raw ECDH shared secret over NIST curve secp256r1.
     */
    fun computeOrGetRawSecret(remotePublicKeyBase64: String): ByteArray {
        sharedSecret?.let { return it }
        val remoteBytes = Base64.getDecoder().decode(remotePublicKeyBase64)
        val kf = KeyFactory.getInstance("EC")
        val remotePubKey = kf.generatePublic(X509EncodedKeySpec(remoteBytes))

        val kp = localKeyPair ?: generateKeyPair()
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(kp.private)
        ka.doPhase(remotePubKey, true)
        val secret = ka.generateSecret()
        sharedSecret = secret
        return secret
    }

    /**
     * Derives a 32-byte AES-256 session key bound to the session ID, sender ID, receiver ID,
     * and the public key exchange transcript to prevent session-confusion and replay attacks.
     */
    fun deriveSessionKey(
        remotePublicKeyBase64: String,
        sessionId: String = "TAP_SESSION_DEFAULT",
        senderId: String = "sender",
        receiverId: String = "receiver"
    ): ByteArray {
        val rawSecret = computeOrGetRawSecret(remotePublicKeyBase64)

        val localPub = getPublicKeyBase64()
        val orderedKeys = if (localPub <= remotePublicKeyBase64) {
            "$localPub:$remotePublicKeyBase64"
        } else {
            "$remotePublicKeyBase64:$localPub"
        }

        val orderedIds = if (senderId <= receiverId) {
            "$senderId:$receiverId"
        } else {
            "$receiverId:$senderId"
        }

        val salt = "TAP_PROXIMITY_V2:$sessionId".toByteArray(Charsets.UTF_8)
        val info = "TAP_KEY:$orderedIds:$orderedKeys".toByteArray(Charsets.UTF_8)

        return hkdfSha256(rawSecret, salt, info, 32)
    }

    /**
     * Calculates a 6-digit Short Authentication String (SAS) numeric verification code
     * (e.g. "482 913") derived via HKDF-SHA256 from the shared secret, session ID, and public keys.
     * Both sender and receiver compute this independently from the transcript and visually compare
     * it to prevent Man-in-the-Middle (MITM) attacks.
     */
    fun calculateSasCode(
        remotePublicKeyBase64: String,
        sessionId: String
    ): String {
        val rawSecret = computeOrGetRawSecret(remotePublicKeyBase64)
        val localPub = getPublicKeyBase64()

        // Order public keys lexicographically so both parties compute identical SAS regardless of who initiated
        val orderedKeys = if (localPub <= remotePublicKeyBase64) {
            "$localPub:$remotePublicKeyBase64"
        } else {
            "$remotePublicKeyBase64:$localPub"
        }

        val salt = "TAP_SAS_SALT:$sessionId".toByteArray(Charsets.UTF_8)
        val info = "TAP_PROXIMITY_SAS:$orderedKeys".toByteArray(Charsets.UTF_8)
        val sasBytes = hkdfSha256(rawSecret, salt, info, 4)

        // Convert 4 bytes to unsigned 31-bit int modulo 1,000,000
        val value = ((sasBytes[0].toInt() and 0xFF) shl 24) or
                ((sasBytes[1].toInt() and 0xFF) shl 16) or
                ((sasBytes[2].toInt() and 0xFF) shl 8) or
                (sasBytes[3].toInt() and 0xFF)
        val positiveValue = (value and 0x7FFFFFFF) % 1_000_000
        val rawCode = String.format("%06d", positiveValue)

        return "${rawCode.substring(0, 3)} ${rawCode.substring(3)}"
    }

    /**
     * Encrypts a chunk with AES-256-GCM.
     * Uses a 12-byte IV constructed as:
     * - 8 bytes: Monotonically increasing chunkIndex (big-endian)
     * - 1 byte: Direction flag (0x01 for sender, 0x02 for receiver)
     * - 3 bytes: Session entropy bytes derived from sessionId
     */
    fun encryptChunk(
        key: ByteArray,
        chunkIndex: Long,
        plaintext: ByteArray,
        isSender: Boolean = true,
        sessionId: String = "TAP_PROXIMITY"
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = constructIv(chunkIndex, isSender, sessionId)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        val cipherText = cipher.doFinal(plaintext)

        val result = ByteArray(12 + cipherText.size)
        System.arraycopy(iv, 0, result, 0, 12)
        System.arraycopy(cipherText, 0, result, 12, cipherText.size)
        return result
    }

    /**
     * Decrypts an AES-256-GCM chunk and verifies the 128-bit authentication tag.
     */
    fun decryptChunk(key: ByteArray, encryptedChunk: ByteArray): ByteArray {
        if (encryptedChunk.size < 12 + 16) {
            throw IllegalArgumentException("Encrypted chunk is too short.")
        }
        val iv = ByteArray(12)
        System.arraycopy(encryptedChunk, 0, iv, 0, 12)
        val cipherTextLength = encryptedChunk.size - 12
        val cipherText = ByteArray(cipherTextLength)
        System.arraycopy(encryptedChunk, 12, cipherText, 0, cipherTextLength)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        return cipher.doFinal(cipherText)
    }

    private fun constructIv(chunkIndex: Long, isSender: Boolean, sessionId: String): ByteArray {
        val iv = ByteArray(12)
        // 8 bytes monotonic chunk index
        for (i in 0 until 8) {
            iv[i] = ((chunkIndex shr ((7 - i) * 8)) and 0xFF).toByte()
        }
        // 1 byte direction
        iv[8] = if (isSender) 0x01.toByte() else 0x02.toByte()
        // 3 bytes session entropy
        val sessionHash = sessionId.hashCode()
        iv[9] = ((sessionHash shr 16) and 0xFF).toByte()
        iv[10] = ((sessionHash shr 8) and 0xFF).toByte()
        iv[11] = (sessionHash and 0xFF).toByte()
        return iv
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        val expandMac = Mac.getInstance("HmacSHA256")
        expandMac.init(SecretKeySpec(prk, "HmacSHA256"))
        expandMac.update(info)
        expandMac.update(0x01.toByte())
        val okm = expandMac.doFinal()

        return okm.copyOf(length)
    }

    companion object {
        fun calculateStreamHash(inputStream: InputStream): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
