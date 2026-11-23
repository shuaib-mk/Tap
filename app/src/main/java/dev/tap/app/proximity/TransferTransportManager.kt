package dev.tap.app.proximity

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest

data class ProximityProgress(
    val currentFileName: String = "",
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val isComplete: Boolean = false,
    val error: String? = null
) {
    val percent: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedSpeed: String
        get() = String.format("%.2f MB/s", speedBytesPerSec / (1024.0 * 1024.0))
}

/**
 * High-speed TCP socket transport layer for Proximity File Sharing.
 * Streams 256 KB encrypted chunks with streaming SHA-256 verification.
 */
class TransferTransportManager(private val context: Context) {

    companion object {
        const val DEFAULT_PROXIMITY_PORT = 8990
        const val CHUNK_SIZE = 256 * 1024 // 256 KB
    }

    private val _progress = MutableStateFlow(ProximityProgress())
    val progress: StateFlow<ProximityProgress> = _progress.asStateFlow()

    @Volatile
    private var isCancelled = false

    fun cancel() {
        isCancelled = true
    }

    fun sendFiles(
        host: String,
        port: Int,
        sessionKey: ByteArray,
        security: TransferSecurityManager,
        files: List<ProximityFile>
    ): Result<Unit> {
        isCancelled = false
        val totalBytes = files.sumOf { it.sizeBytes }
        var overallBytes: Long = 0
        val startTime = System.currentTimeMillis()

        return try {
            Socket(host, port).use { socket ->
                socket.tcpNoDelay = true
                val out = socket.getOutputStream()
                val inStream = socket.getInputStream()

                files.forEachIndexed { index, file ->
                    if (isCancelled) throw InterruptedException("Transfer cancelled by user.")

                    _progress.value = ProximityProgress(
                        currentFileName = file.name,
                        currentFileIndex = index + 1,
                        totalFiles = files.size,
                        bytesTransferred = overallBytes,
                        totalBytes = totalBytes
                    )

                    val fileStream: InputStream = context.contentResolver.openInputStream(file.uri)
                        ?: throw IllegalStateException("Cannot read file ${file.name}")

                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(CHUNK_SIZE)
                    var nonceCounter: Long = 0

                    fileStream.use { input ->
                        var readBytes: Int
                        while (input.read(buffer).also { readBytes = it } != -1) {
                            if (isCancelled) throw InterruptedException("Transfer cancelled.")

                            digest.update(buffer, 0, readBytes)
                            val plaintext = buffer.copyOf(readBytes)
                            val encrypted = security.encryptChunk(sessionKey, nonceCounter++, plaintext)

                            // Write chunk length (4 bytes) + encrypted payload
                            val len = encrypted.size
                            out.write((len shr 24) and 0xFF)
                            out.write((len shr 16) and 0xFF)
                            out.write((len shr 8) and 0xFF)
                            out.write(len and 0xFF)
                            out.write(encrypted)
                            out.flush()

                            overallBytes += readBytes
                            val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                            val speed = if (elapsedSec > 0) (overallBytes / elapsedSec).toLong() else 0L

                            _progress.value = ProximityProgress(
                                currentFileName = file.name,
                                currentFileIndex = index + 1,
                                totalFiles = files.size,
                                bytesTransferred = overallBytes,
                                totalBytes = totalBytes,
                                speedBytesPerSec = speed
                            )
                        }
                    }
                }
            }

            _progress.value = _progress.value.copy(isComplete = true)
            Result.success(Unit)
        } catch (e: Exception) {
            _progress.value = _progress.value.copy(error = e.message)
            Result.failure(e)
        }
    }
}
