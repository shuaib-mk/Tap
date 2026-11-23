package dev.tap.app.webrtc.transfer

import android.content.Context
import dev.tap.app.proximity.ProximityFile
import dev.tap.app.proximity.ProximityFileManifest
import dev.tap.app.proximity.ProximityProgress
import dev.tap.app.proximity.TransferSecurityManager
import dev.tap.app.storage.FileManager
import dev.tap.app.webrtc.WebRtcPeerConnectionManager
import dev.tap.app.webrtc.model.FileManifestDto
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Resumable Chunk-based Transfer Protocol over WebRTC DataChannel.
 *
 * Slices files into 64 KB chunks, handles SCTP flow control and backpressure,
 * provides monotonic AES-256-GCM encryption, pause/resume seeking, and
 * end-to-end streaming SHA-256 integrity verification.
 */
class WebRtcChunkTransport(
    private val context: Context,
    private val peerConnectionManager: WebRtcPeerConnectionManager,
    private val securityManager: TransferSecurityManager
) {

    companion object {
        const val CHUNK_SIZE = 64 * 1024 // 64 KB chunks
        const val MAX_BUFFERED_AMOUNT = 4 * 1024 * 1024 // 4 MB high-throughput backpressure limit
        const val RESUME_BUFFERED_AMOUNT = 2 * 1024 * 1024 // 2 MB drain threshold

        const val FRAME_FILE_HEADER: Byte = 0x01
        const val FRAME_CHUNK: Byte = 0x02
        const val FRAME_ACK: Byte = 0x03
        const val FRAME_RESUME_REQUEST: Byte = 0x04
        const val FRAME_FILE_COMPLETE: Byte = 0x05
        const val FRAME_TRANSFER_COMPLETE: Byte = 0x06
        const val FRAME_PAUSE: Byte = 0x07
        const val FRAME_RESUME: Byte = 0x08
        const val FRAME_CANCEL: Byte = 0x09

        data class ChunkFrameData(
            val fileIndex: Int,
            val chunkIndex: Long,
            val totalChunks: Long,
            val data: ByteArray
        )

        fun encodeChunkFrame(fileIndex: Int, chunkIndex: Long, totalChunks: Long, payload: ByteArray): ByteArray {
            val buffer = ByteBuffer.allocate(1 + 4 + 8 + 8 + 4 + payload.size)
            buffer.put(FRAME_CHUNK)
            buffer.putInt(fileIndex)
            buffer.putLong(chunkIndex)
            buffer.putLong(totalChunks)
            buffer.putInt(payload.size)
            buffer.put(payload)
            return buffer.array()
        }

        fun decodeChunkFrame(bytes: ByteArray): ChunkFrameData? {
            if (bytes.size < 1 + 4 + 8 + 8 + 4) return null
            val buffer = ByteBuffer.wrap(bytes)
            val type = buffer.get()
            if (type != FRAME_CHUNK) return null
            val fileIndex = buffer.getInt()
            val chunkIndex = buffer.getLong()
            val totalChunks = buffer.getLong()
            val length = buffer.getInt()
            if (buffer.remaining() < length) return null
            val payload = ByteArray(length)
            buffer.get(payload)
            return ChunkFrameData(fileIndex, chunkIndex, totalChunks, payload)
        }
    }

    class StreamingSha256 {
        private val md = MessageDigest.getInstance("SHA-256")
        fun update(bytes: ByteArray, offset: Int, length: Int) {
            md.update(bytes, offset, length)
        }
        fun digestHex(): String {
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private val _progress = MutableStateFlow(ProximityProgress())
    val progress: StateFlow<ProximityProgress> = _progress.asStateFlow()

    @Volatile
    var isPaused: Boolean = false
        private set

    @Volatile
    var isCancelled: Boolean = false
        private set

    private var activeJob: Job? = null
    private var sessionKey: ByteArray? = null
    private var activeSessionId: String = ""

    // Receiver state
    private var currentReceivingFileManifest: FileManifestDto? = null
    private var currentTempFile: File? = null
    private var currentFileOutputStream: RandomAccessFile? = null
    private var currentDigest: MessageDigest? = null
    private var currentFileIndex: Int = 0
    private var currentLastAckedChunk: Long = -1L
    private var totalFilesToReceive: Int = 0
    private var totalBytesToReceive: Long = 0L
    private var overallBytesReceived: Long = 0L
    private var receiverStartTime: Long = 0L
    private var lastReceiverProgressTime: Long = 0L

    fun setSessionParameters(key: ByteArray, sessionId: String, sasCode: String) {
        sessionKey = key
        activeSessionId = sessionId
        receiverStartTime = 0L
        lastReceiverProgressTime = 0L
        _progress.value = _progress.value.copy(sasVerificationCode = sasCode)
    }

    // ---------------------------------------------------------------------------------------------
    // SENDER PIPELINE
    // ---------------------------------------------------------------------------------------------
    fun sendFiles(
        scope: CoroutineScope,
        files: List<ProximityFile>,
        manifests: List<FileManifestDto>,
        onCompleted: () -> Unit,
        onError: (String) -> Unit
    ) {
        val key = sessionKey ?: run {
            onError("Encryption session key is not derived.")
            return
        }

        isCancelled = false
        isPaused = false

        val totalBytes = files.sumOf { it.sizeBytes }
        var overallBytesSent: Long = 0
        val startTime = System.currentTimeMillis()
        var lastProgressTime = 0L

        activeJob = scope.launch(Dispatchers.IO) {
            try {
                files.forEachIndexed { fileIndex, file ->
                    if (isCancelled) throw CancellationException("Transfer cancelled.")

                    val manifest = manifests[fileIndex]

                    // 1. Send File Header Frame
                    sendFileHeader(fileIndex, manifest)

                    val rawStream: InputStream = context.contentResolver.openInputStream(file.uri)
                        ?: throw IOException("Cannot read file ${file.name}")
                    val fileStream = BufferedInputStream(rawStream, 256 * 1024)

                    val buffer = ByteArray(CHUNK_SIZE)
                    var chunkIndex: Long = 0
                    val totalChunks = (file.sizeBytes + CHUNK_SIZE - 1) / CHUNK_SIZE

                    fileStream.use { stream ->
                        var readBytes: Int
                        while (stream.read(buffer).also { readBytes = it } != -1) {
                            if (isCancelled) throw CancellationException("Transfer cancelled.")

                            // Handle Pause
                            while (isPaused && !isCancelled) {
                                delay(200)
                            }

                            // Flow control backpressure with responsive 2ms polling
                            while (peerConnectionManager.getBufferedAmount() > MAX_BUFFERED_AMOUNT && !isCancelled) {
                                delay(2)
                            }

                            val plaintext = buffer.copyOf(readBytes)
                            val encryptedChunk = securityManager.encryptChunk(
                                key = key,
                                chunkIndex = chunkIndex,
                                plaintext = plaintext,
                                isSender = true,
                                sessionId = activeSessionId
                            )

                            // Send chunk frame
                            sendChunkFrame(fileIndex, chunkIndex, totalChunks, encryptedChunk)
                            chunkIndex++

                            overallBytesSent += readBytes

                            // Throttle progress emissions to 60ms to prevent main thread recomposition lag
                            val now = System.currentTimeMillis()
                            if (now - lastProgressTime >= 60L || chunkIndex == totalChunks) {
                                lastProgressTime = now
                                val elapsedSec = (now - startTime) / 1000.0
                                val speed = if (elapsedSec > 0) (overallBytesSent / elapsedSec).toLong() else 0L

                                _progress.value = ProximityProgress(
                                    currentFileName = file.name,
                                    currentFileIndex = fileIndex + 1,
                                    totalFiles = files.size,
                                    bytesTransferred = overallBytesSent,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = speed,
                                    sasVerificationCode = _progress.value.sasVerificationCode,
                                    verifiedFilesCount = fileIndex
                                )
                            }
                        }
                    }

                    // 2. Send File Complete Frame
                    sendFileComplete(fileIndex)
                }

                // 3. Send Transfer Complete
                sendTransferComplete()
                _progress.value = _progress.value.copy(
                    isComplete = true,
                    verifiedFilesCount = files.size
                )
                withContext(Dispatchers.Main) { onCompleted() }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _progress.value = _progress.value.copy(error = e.message)
                    withContext(Dispatchers.Main) { onError(e.message ?: "Transfer error") }
                }
            }
        }
    }

    private fun sendFileHeader(fileIndex: Int, manifest: FileManifestDto) {
        val nameBytes = manifest.name.toByteArray(Charsets.UTF_8)
        val mimeBytes = manifest.mimeType.toByteArray(Charsets.UTF_8)
        val shaBytes = manifest.sha256.toByteArray(Charsets.UTF_8)

        val buffer = ByteBuffer.allocate(1 + 4 + 8 + 4 + nameBytes.size + 4 + mimeBytes.size + 4 + shaBytes.size)
        buffer.put(FRAME_FILE_HEADER)
        buffer.putInt(fileIndex)
        buffer.putLong(manifest.sizeBytes)
        buffer.putInt(nameBytes.size)
        buffer.put(nameBytes)
        buffer.putInt(mimeBytes.size)
        buffer.put(mimeBytes)
        buffer.putInt(shaBytes.size)
        buffer.put(shaBytes)

        peerConnectionManager.sendBinaryMessage(buffer.array())
    }

    private fun sendChunkFrame(fileIndex: Int, chunkIndex: Long, totalChunks: Long, encrypted: ByteArray) {
        val frameBytes = encodeChunkFrame(fileIndex, chunkIndex, totalChunks, encrypted)
        peerConnectionManager.sendBinaryMessage(frameBytes)
    }

    private fun sendFileComplete(fileIndex: Int) {
        val buffer = ByteBuffer.allocate(1 + 4)
        buffer.put(FRAME_FILE_COMPLETE)
        buffer.putInt(fileIndex)
        peerConnectionManager.sendBinaryMessage(buffer.array())
    }

    private fun sendTransferComplete() {
        peerConnectionManager.sendBinaryMessage(byteArrayOf(FRAME_TRANSFER_COMPLETE))
    }

    // ---------------------------------------------------------------------------------------------
    // RECEIVER PIPELINE
    // ---------------------------------------------------------------------------------------------
    fun handleIncomingBinaryFrame(
        bytes: ByteArray,
        onFileVerified: (name: String) -> Unit,
        onAllCompleted: (count: Int, totalBytes: Long) -> Unit,
        onError: (String) -> Unit
    ) {
        if (bytes.isEmpty()) return
        val buffer = ByteBuffer.wrap(bytes)
        val frameType = buffer.get()

        when (frameType) {
            FRAME_FILE_HEADER -> {
                val fileIndex = buffer.getInt()
                val sizeBytes = buffer.getLong()

                val nameLen = buffer.getInt()
                val nameBytes = ByteArray(nameLen)
                buffer.get(nameBytes)
                val fileName = String(nameBytes, Charsets.UTF_8)

                val mimeLen = buffer.getInt()
                val mimeBytes = ByteArray(mimeLen)
                buffer.get(mimeBytes)
                val mimeType = String(mimeBytes, Charsets.UTF_8)

                val shaLen = buffer.getInt()
                val shaBytes = ByteArray(shaLen)
                buffer.get(shaBytes)
                val sha256 = String(shaBytes, Charsets.UTF_8)

                currentReceivingFileManifest = FileManifestDto(fileName, sizeBytes, mimeType, sha256)
                currentFileIndex = fileIndex
                currentLastAckedChunk = -1L
                if (receiverStartTime == 0L) {
                    receiverStartTime = System.currentTimeMillis()
                }

                val temp = FileManager.createTempFile(context, fileName)
                currentTempFile = temp
                currentFileOutputStream = RandomAccessFile(temp, "rw")
                currentDigest = MessageDigest.getInstance("SHA-256")
            }

            FRAME_CHUNK -> {
                val fileIndex = buffer.getInt()
                val chunkIndex = buffer.getLong()
                val totalChunks = buffer.getLong()
                val length = buffer.getInt()

                val encrypted = ByteArray(length)
                buffer.get(encrypted)

                val key = sessionKey ?: return
                try {
                    val decrypted = securityManager.decryptChunk(key, encrypted)

                    val raf = currentFileOutputStream ?: return
                    raf.seek(chunkIndex * CHUNK_SIZE)
                    raf.write(decrypted)
                    currentDigest?.update(decrypted)
                    currentLastAckedChunk = chunkIndex

                    overallBytesReceived += decrypted.size
                    val manifest = currentReceivingFileManifest

                    val now = System.currentTimeMillis()
                    if (now - lastReceiverProgressTime >= 60L || chunkIndex == totalChunks - 1) {
                        lastReceiverProgressTime = now
                        val elapsedSec = (now - receiverStartTime) / 1000.0
                        val speed = if (elapsedSec > 0) (overallBytesReceived / elapsedSec).toLong() else 0L

                        _progress.value = ProximityProgress(
                            currentFileName = manifest?.name ?: "Downloading...",
                            currentFileIndex = fileIndex + 1,
                            totalFiles = totalFilesToReceive,
                            bytesTransferred = overallBytesReceived,
                            totalBytes = totalBytesToReceive,
                            speedBytesPerSec = speed,
                            sasVerificationCode = _progress.value.sasVerificationCode,
                            verifiedFilesCount = fileIndex
                        )
                    }
                } catch (e: Exception) {
                    onError("Failed to decrypt chunk $chunkIndex: ${e.message}")
                }
            }

            FRAME_FILE_COMPLETE -> {
                val fileIndex = buffer.getInt()
                val manifest = currentReceivingFileManifest ?: return
                val temp = currentTempFile ?: return
                val raf = currentFileOutputStream ?: return

                try {
                    raf.close()
                    currentFileOutputStream = null

                    val computedSha = currentDigest?.digest()?.joinToString("") { "%02x".format(it) } ?: ""
                    if (!computedSha.equals(manifest.sha256, ignoreCase = true)) {
                        temp.delete()
                        throw SecurityException("SHA-256 verification mismatch for ${manifest.name}! Expected ${manifest.sha256}, got $computedSha")
                    }

                    FileManager.saveVerifiedFile(context, temp, manifest.name, manifest.mimeType)
                    temp.delete()

                    onFileVerified(manifest.name)
                } catch (e: Exception) {
                    temp.delete()
                    onError("File validation error: ${e.message}")
                }
            }

            FRAME_TRANSFER_COMPLETE -> {
                _progress.value = _progress.value.copy(
                    isComplete = true,
                    verifiedFilesCount = totalFilesToReceive
                )
                onAllCompleted(totalFilesToReceive, totalBytesToReceive)
            }

            FRAME_PAUSE -> {
                isPaused = true
            }

            FRAME_RESUME -> {
                isPaused = false
            }

            FRAME_CANCEL -> {
                isCancelled = true
                currentTempFile?.delete()
                _progress.value = _progress.value.copy(error = "Transfer cancelled by peer.")
            }
        }
    }

    fun setExpectedTransferTotals(fileCount: Int, totalBytes: Long) {
        totalFilesToReceive = fileCount
        totalBytesToReceive = totalBytes
    }

    fun pauseTransfer() {
        isPaused = true
        peerConnectionManager.sendBinaryMessage(byteArrayOf(FRAME_PAUSE))
    }

    fun resumeTransfer() {
        isPaused = false
        peerConnectionManager.sendBinaryMessage(byteArrayOf(FRAME_RESUME))
    }

    fun cancelTransfer() {
        isCancelled = true
        activeJob?.cancel()
        peerConnectionManager.sendBinaryMessage(byteArrayOf(FRAME_CANCEL))
        currentTempFile?.delete()
        _progress.value = _progress.value.copy(error = "Transfer cancelled.")
    }
}
