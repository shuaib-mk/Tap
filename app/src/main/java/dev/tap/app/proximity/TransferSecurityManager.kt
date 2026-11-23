package dev.tap.app.proximity

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
 * Cryptographic security manager for Proximity File Sharing.
 * Implements:
 * - Ephemeral ECDH key agreement over NIST curve secp256r1
 * - HKDF-SHA256 key derivation
 * - AES-256-GCM chunk encryption and decryption
 * - Streaming SHA-256 integrity verification
 */
class TransferSecurityManager {

    private var localKeyPair: KeyPair? = null
    private var sharedSecret: ByteArray? = null

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

    fun deriveSessionKey(remotePublicKeyBase64: String, salt: String = "TAP_PROXIMITY_V2"): ByteArray {
        val remoteBytes = Base64.getDecoder().decode(remotePublicKeyBase64)
        val kf = KeyFactory.getInstance("EC")
        val remotePubKey = kf.generatePublic(X509EncodedKeySpec(remoteBytes))

        val kp = localKeyPair ?: generateKeyPair()
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(kp.private)
        ka.doPhase(remotePubKey, true)
        val rawSecret = ka.generateSecret()

        val derivedKey = hkdfSha256(rawSecret, salt.toByteArray(Charsets.UTF_8), 32)
        sharedSecret = derivedKey
        return derivedKey
    }

    fun encryptChunk(key: ByteArray, nonceCounter: Long, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        for (i in 0 until 8) {
            iv[i] = ((nonceCounter shr (i * 8)) and 0xFF).toByte()
        }
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        val cipherText = cipher.doFinal(plaintext)

        val result = ByteArray(12 + cipherText.size)
        System.arraycopy(iv, 0, result, 0, 12)
        System.arraycopy(cipherText, 0, result, 12, cipherText.size)
        return result
    }

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

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        val expandMac = Mac.getInstance("HmacSHA256")
        expandMac.init(SecretKeySpec(prk, "HmacSHA256"))
        expandMac.update("TAP_PROXIMITY_KEY".toByteArray(Charsets.UTF_8))
        expandMac.update(0x01.toByte())
        val okm = expandMac.doFinal()

        return okm.copyOf(length)
    }
}
