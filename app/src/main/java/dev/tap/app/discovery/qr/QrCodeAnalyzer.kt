package dev.tap.app.discovery.qr

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.*
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer

/**
 * High-performance CameraX ImageAnalysis analyzer that detects and decodes QR codes in real-time.
 *
 * Correctly accounts for:
 * - CameraX YUV_420_888 plane rowStride padding (preventing diagonal skew/shearing).
 * - Sensor hardware rotation (90°, 180°, 270°) to supply an upright bitmap.
 * - Dual-pass binarization (HybridBinarizer + GlobalHistogramBinarizer fallback) for
 *   instant detection in dim, glare, and low-contrast conditions.
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
            val source = toPlanarYuvLuminanceSource(image)
            var result: Result? = null

            // Pass 1: HybridBinarizer (optimal for high-contrast QR codes)
            try {
                result = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            } catch (_: NotFoundException) {
                // Pass 2: GlobalHistogramBinarizer fallback (optimal for uneven lighting/screens)
                try {
                    result = reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source)))
                } catch (_: NotFoundException) {}
            }

            if (result != null && !result.text.isNullOrBlank()) {
                isThrottled = true
                onQrCodeScanned(result.text.trim())
            }
        } catch (_: Exception) {
            // Unhandled frame decoding exceptions, safely ignore frame
        } finally {
            reader.reset()
            image.close()
        }
    }

    /**
     * Extracts luminance byte data from ImageProxy, stripping row-stride padding
     * and applying sensor rotation so ZXing receives an upright image.
     */
    private fun toPlanarYuvLuminanceSource(image: ImageProxy): PlanarYUVLuminanceSource {
        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val width = image.width
        val height = image.height

        // Extract tightly packed Y data without rowStride padding
        val yData = ByteArray(width * height)
        val rowBuffer = ByteArray(rowStride)

        for (row in 0 until height) {
            val rowStart = row * rowStride
            yBuffer.position(rowStart)
            val bytesToRead = Math.min(rowStride, yBuffer.remaining())
            yBuffer.get(rowBuffer, 0, bytesToRead)
            System.arraycopy(rowBuffer, 0, yData, row * width, width)
        }

        // Apply physical sensor rotation
        val rotation = image.imageInfo.rotationDegrees
        val (rotatedData, finalWidth, finalHeight) = when (rotation) {
            90 -> {
                val rotated = ByteArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        rotated[x * height + (height - y - 1)] = yData[y * width + x]
                    }
                }
                Triple(rotated, height, width)
            }
            180 -> {
                val rotated = ByteArray(width * height)
                for (i in 0 until width * height) {
                    rotated[width * height - 1 - i] = yData[i]
                }
                Triple(rotated, width, height)
            }
            270 -> {
                val rotated = ByteArray(width * height)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        rotated[(width - x - 1) * height + y] = yData[y * width + x]
                    }
                }
                Triple(rotated, height, width)
            }
            else -> Triple(yData, width, height)
        }

        return PlanarYUVLuminanceSource(
            rotatedData,
            finalWidth,
            finalHeight,
            0,
            0,
            finalWidth,
            finalHeight,
            false
        )
    }

    fun reset() {
        isThrottled = false
    }
}
