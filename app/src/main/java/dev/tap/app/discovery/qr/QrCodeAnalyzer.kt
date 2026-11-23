package dev.tap.app.discovery.qr

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer

/**
 * CameraX ImageAnalysis analyzer that detects and decodes QR codes in real-time using ZXing.
 */
class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        setHints(hints)
    }

    @Volatile
    private var isThrottled = false

    override fun analyze(image: ImageProxy) {
        if (isThrottled) {
            image.close()
            return
        }

        try {
            val plane = image.planes.firstOrNull() ?: run {
                image.close()
                return
            }

            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val source = PlanarYUVLuminanceSource(
                bytes,
                image.width,
                image.height,
                0,
                0,
                image.width,
                image.height,
                false
            )

            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val result = reader.decodeWithState(binaryBitmap)

            if (!result.text.isNullOrBlank()) {
                isThrottled = true
                onQrCodeScanned(result.text)
            }
        } catch (_: NotFoundException) {
            // Frame does not contain QR code, normal
        } catch (_: Exception) {
            // Other barcode exceptions, ignore
        } finally {
            reader.reset()
            image.close()
        }
    }

    fun reset() {
        isThrottled = false
    }
}
