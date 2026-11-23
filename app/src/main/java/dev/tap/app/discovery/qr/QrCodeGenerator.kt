package dev.tap.app.discovery.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.tap.app.protocol.SessionOffer
import java.util.EnumMap

/**
 * High-legibility, minimalist QR Code Generator.
 *
 * Produces crisp, high-contrast QR codes optimized for fast camera recognition:
 * - High-contrast Pure White quiet zone & Deep Onyx modules for instant camera binarization.
 * - Minimal data payloads for large, chunky, easily readable modules (Version 2-4 matrix).
 * - Standard Error Correction Level M with generous margins.
 */
object QrCodeGenerator {

    private const val COLOR_BACKGROUND = 0xFFFFFFFF.toInt()
    private const val COLOR_FOREGROUND = 0xFF090A0E.toInt()

    fun generateQrBitmap(
        content: String,
        width: Int = 600,
        height: Int = 600
    ): Bitmap? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                put(EncodeHintType.MARGIN, 2)
            }

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height, hints)

            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height
            val pixels = IntArray(matrixWidth * matrixHeight)

            for (y in 0 until matrixHeight) {
                val offset = y * matrixWidth
                for (x in 0 until matrixWidth) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) COLOR_FOREGROUND else COLOR_BACKGROUND
                }
            }

            val bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a minimal session QR code.
     * Uses short session code format for maximum module size and instant camera scanning.
     */
    fun generateSessionQr(sessionCode: String, size: Int = 600): Bitmap? {
        val payload = if (sessionCode.startsWith("TAP-", ignoreCase = true)) {
            "https://tap.dev/s/$sessionCode"
        } else {
            sessionCode
        }
        return generateQrBitmap(payload, size, size)
    }

    fun generateSessionQr(offer: SessionOffer, size: Int = 600): Bitmap? {
        // Prefer short code or URL if available, otherwise compact offer
        return generateQrBitmap(offer.sessionId, size, size)
    }
}
