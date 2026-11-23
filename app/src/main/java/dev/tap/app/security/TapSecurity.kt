package dev.tap.app.security

import android.util.Base64
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Production-ready cryptographic operations for TAP:
 * - ECDH Key Exchange (secp256r1 curve)
 * - HKDF-SHA256 for key derivation
 * - AES-256-GCM authenticated encryption with 12-byte nonce
 * - Streaming SHA-256 file integrity verification
 * - Path traversal and input sanitization
 */
object TapSecurity {
    private const val EC_CURVE = "secp256r1"
    private const val GCM_TAG_LENGTH = 128 // bits
    private const val GCM_NONCE_LENGTH = 12 // bytes
    private const val HKDF_SALT = "TAP_P2P_TRANSFER_V1"

    fun generateSessionId(): String = UUID.randomUUID().toString()

    fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec(EC_CURVE), SecureRandom())
        return keyPairGenerator.generateKeyPair()
    }

    fun encodePublicKey(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    fun decodePublicKey(base64PublicKey: String): PublicKey {
        val keyBytes = Base64.decode(base64PublicKey, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(keySpec)
    }

    fun deriveSharedSecret(privateKey: PrivateKey, peerPublicKey: PublicKey): SecretKey {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        val rawSharedSecret = keyAgreement.generateSecret()

        // Derive 256-bit AES key using HKDF-SHA256
        val derivedKeyBytes = hkdfExpand(rawSharedSecret, HKDF_SALT.toByteArray(StandardCharsets.UTF_8), 32)
        return SecretKeySpec(derivedKeyBytes, "AES")
    }

    private fun hkdfExpand(ikm: ByteArray, salt: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val info = "TAP_AES_GCM_KEY".toByteArray(StandardCharsets.UTF_8)
        mac.update(info)
        mac.update(0x01.toByte())
        val output = mac.doFinal()
        return output.copyOf(length)
    }

    fun generateNonce(): ByteArray {
        val nonce = ByteArray(GCM_NONCE_LENGTH)
        SecureRandom().nextBytes(nonce)
        return nonce
    }

    fun generateNonceFromCounter(counter: Long): ByteArray {
        val nonce = ByteArray(GCM_NONCE_LENGTH)
        for (i in 0..7) {
            nonce[11 - i] = ((counter shr (i * 8)) and 0xFF).toByte()
        }
        return nonce
    }

    fun encrypt(data: ByteArray, secretKey: SecretKey, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        return cipher.doFinal(data)
    }

    fun decrypt(encryptedData: ByteArray, secretKey: SecretKey, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, nonce)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(encryptedData)
    }

    fun hashSHA256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return bytesToHex(hash)
    }

    fun hashSHA256Stream(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        return bytesToHex(digest.digest())
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789abcdef".toCharArray()
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    fun sanitizeFilename(inputName: String): String {
        // Strip directory traversal patterns and null characters
        var clean = inputName.replace("\\", "/")
        val lastSlash = clean.lastIndexOf('/')
        if (lastSlash >= 0) {
            clean = clean.substring(lastSlash + 1)
        }
        // Remove dangerous characters: null, control chars, path navigation
        clean = clean.replace(Regex("[\\x00-\\x1F\\x7F/\\\\?%*:|\"<>]"), "_")
            .replace(Regex("^\\.+"), "") // Remove leading dots (hidden files)
            .trim()

        if (clean.isEmpty()) {
            clean = "tap_file_${System.currentTimeMillis()}"
        }

        // Limit length to 128 characters while preserving extension
        if (clean.length > 128) {
            val extIndex = clean.lastIndexOf('.')
            clean = if (extIndex in 1..120) {
                val ext = clean.substring(extIndex)
                clean.substring(0, 128 - ext.length) + ext
            } else {
                clean.substring(0, 128)
            }
        }
        return clean
    }

    fun validateMimeType(mimeType: String): String {
        val clean = mimeType.trim().lowercase()
        return if (clean.matches(Regex("^[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+$"))) {
            clean
        } else {
            "application/octet-stream"
        }
    }
}
