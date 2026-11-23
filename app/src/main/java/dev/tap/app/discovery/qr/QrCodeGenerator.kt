package dev.tap.app.discovery.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.tap.app.protocol.SessionOffer
import dev.tap.app.protocol.encodeToQrPayload
import java.util.EnumMap

/**
 * QR Code Generator styled to match TAP's premium aesthetic:
 * Dark background (0xFF090B10) with vibrant lime green modules (0xFFB7F36B).
 */
object QrCodeGenerator {

    private const val COLOR_BACKGROUND = 0xFF090B10.toInt()
    private const val COLOR_FOREGROUND = 0xFFB7F36B.toInt()

    fun generateQrBitmap(
        content: String,
        width: Int = 512,
        height: Int = 512
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
        } catch (e: Exception) {
            null
        }
    }

    fun generateSessionQr(offer: SessionOffer, size: Int = 512): Bitmap? {
        val payload = encodeToQrPayload(offer)
        return generateQrBitmap(payload, size, size)
    }
}
