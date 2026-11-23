package dev.tap.app.transfer

import android.content.Context
import dev.tap.app.history.TransferHistoryRepository
import dev.tap.app.history.data.TransferRecord
import dev.tap.app.protocol.*
import dev.tap.app.security.SessionManager
import dev.tap.app.security.TapSecurity
import dev.tap.app.storage.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import javax.crypto.SecretKey
import kotlin.coroutines.coroutineContext

/**
 * TCP Transfer Client running on sender device.
 * Connects to receiver, executes ECDH key handshake, submits transfer request,
 * awaits acceptance, streams chunked files encrypted with AES-256-GCM, and verifies hashes.
 */
class TransferClient(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val historyRepo: TransferHistoryRepository
) {
    private var activeSocket: Socket? = null

    private val _progress = MutableStateFlow(TransferProgress())
    val progress: StateFlow<TransferProgress> = _progress.asStateFlow()

    suspend fun sendFiles(
        host: String,
        port: Int,
        sessionId: String,
        senderName: String,
        files: List<SelectedFile>
    ) = withContext(Dispatchers.IO) {
        val session = sessionManager.getSession(sessionId)
            ?: throw IllegalStateException("Session $sessionId expired or not found")

        val totalBytes = files.sumOf { it.sizeBytes }
        _progress.value = TransferProgress(
            totalBytes = totalBytes,
            totalFiles = files.size,
            state = TransferState.CONNECTING
        )

        val socket = Socket()
        activeSocket = socket

        try {
            socket.connect(InetSocketAddress(host, port), 10000)
            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

            // 1. Send SessionOffer for ECDH Handshake
            val offer = session.toSessionOffer(TransferServer.getLocalIpAddress(), port)
            output.writeUTF(TapProtocol.json.encodeToString(offer))
            output.flush()

            // 2. Read peer SessionOffer
            val peerOfferJson = input.readUTF()
            val peerOffer = TapProtocol.json.decodeFromString<SessionOffer>(peerOfferJson)

            val sharedSecret = sessionManager.completeHandshake(
                sessionId = sessionId,
                peerPublicKeyBase64 = peerOffer.publicKeyBase64,
                peerName = peerOffer.deviceName
            ) ?: throw IllegalStateException("Key exchange derivation failed")

            // 3. Pre-compute file hashes and create TransferManifest
            _progress.value = _progress.value.copy(state = TransferState.VERIFYING)
            val manifests = files.mapIndexed { index, file ->
                val sha256 = FileManager.openInputStream(context, file.uri)?.use {
                    TapSecurity.hashSHA256Stream(it)
                } ?: "0000000000000000000000000000000000000000000000000000000000000000"

                TransferManifest(
                    transferId = "${sessionId}_$index",
                    fileName = file.name,
                    mimeType = file.mimeType,
                    sizeBytes = file.sizeBytes,
                    sha256 = sha256
                )
            }

            // 4. Send TransferRequest
            val request = TransferRequest(
                sessionId = sessionId,
                senderName = senderName,
                files = manifests,
                totalBytes = totalBytes
            )
            output.writeUTF(TapProtocol.json.encodeToString(request))
            output.flush()

            _progress.value = _progress.value.copy(state = TransferState.AWAITING_ACCEPTANCE)

            // 5. Await Receiver's TransferResponse
            val responseJson = input.readUTF()
            val response = TapProtocol.json.decodeFromString<TransferResponse>(responseJson)

            if (!response.accepted) {
                _progress.value = _progress.value.copy(
                    state = TransferState.CANCELLED,
                    error = response.reason ?: "Receiver declined the transfer"
                )
                return@withContext
            }

            // 6. Receiver accepted! Stream chunks
            _progress.value = _progress.value.copy(state = TransferState.TRANSFERRING)
            streamFiles(input, output, files, manifests, sharedSecret, totalBytes, peerOffer.deviceName)

        } catch (e: Exception) {
            _progress.value = _progress.value.copy(
                state = TransferState.FAILED,
                error = e.localizedMessage ?: "Transfer failed"
            )
        } finally {
            try { socket.close() } catch (_: Exception) {}
            activeSocket = null
        }
    }

    private suspend fun streamFiles(
        input: DataInputStream,
        output: DataOutputStream,
        files: List<SelectedFile>,
        manifests: List<TransferManifest>,
        sharedSecret: SecretKey,
        totalBytes: Long,
        receiverName: String
    ) = withContext(Dispatchers.IO) {
        var totalBytesTransferred = 0L
        val chunkSize = TapProtocol.DEFAULT_CHUNK_BYTES
        val buffer = ByteArray(chunkSize)
        val startTime = System.currentTimeMillis()
        var lastCalcTime = startTime
        var lastBytes = 0L

        files.forEachIndexed { fileIndex, file ->
            val manifest = manifests[fileIndex]
            _progress.value = _progress.value.copy(
                currentFileIndex = fileIndex,
                currentFileName = file.name,
                state = TransferState.TRANSFERRING
            )

            val inputStream = FileManager.openInputStream(context, file.uri)
                ?: throw FileNotFoundException("Could not open ${file.name}")

            inputStream.use { stream ->
                var chunkIndex = 0L
                val totalChunks = (file.sizeBytes + chunkSize - 1) / chunkSize
                var offset = 0L

                while (coroutineContext.isActive) {
                    val bytesRead = stream.read(buffer)
                    if (bytesRead == -1) break

                    val chunkData = if (bytesRead == chunkSize) buffer else buffer.copyOf(bytesRead)
                    val nonce = TapSecurity.generateNonce()
                    val encryptedChunk = TapSecurity.encrypt(chunkData, sharedSecret, nonce)

                    val isLast = (offset + bytesRead >= file.sizeBytes)
                    val header = ChunkHeader(
                        transferId = manifest.transferId,
                        fileIndex = fileIndex,
                        chunkIndex = chunkIndex,
                        totalChunks = totalChunks,
                        offset = offset,
                        size = encryptedChunk.size,
                        nonceBase64 = android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP),
                        isLastChunk = isLast
                    )

                    // Write header + encrypted chunk
                    output.writeUTF(TapProtocol.json.encodeToString(header))
                    output.write(encryptedChunk)
                    output.flush()

                    offset += bytesRead
                    chunkIndex++
                    totalBytesTransferred += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastCalcTime >= 500) {
                        val durationSec = (now - lastCalcTime) / 1000.0
                        val bytesSince = totalBytesTransferred - lastBytes
                        val speed = if (durationSec > 0) (bytesSince / durationSec).toLong() else 0L
                        val remainingBytes = totalBytes - totalBytesTransferred
                        val eta = if (speed > 0) remainingBytes / speed else 0L

                        _progress.value = _progress.value.copy(
                            bytesTransferred = totalBytesTransferred,
                            speedBytesPerSec = speed,
                            estimatedSecondsRemaining = eta
                        )
                        lastCalcTime = now
                        lastBytes = totalBytesTransferred
                    }
                }
            }

            // 7. Await Receiver's TransferComplete confirmation
            val completeJson = input.readUTF()
            val complete = TapProtocol.json.decodeFromString<TransferComplete>(completeJson)

            if (!complete.success) {
                throw SecurityException("Receiver failed to verify file ${file.name}")
            }

            // Save record to local history
            historyRepo.insert(
                TransferRecord(
                    transferId = manifest.transferId,
                    fileName = manifest.fileName,
                    fileSize = manifest.sizeBytes,
                    mimeType = manifest.mimeType,
                    direction = TransferDirection.SENT,
                    status = TransferStatus.COMPLETED,
                    peerName = receiverName,
                    connectionMethod = "Direct Wi-Fi",
                    filePath = file.uri.toString()
                )
            )
        }

        _progress.value = _progress.value.copy(
            bytesTransferred = totalBytes,
            state = TransferState.COMPLETE,
            speedBytesPerSec = 0L,
            estimatedSecondsRemaining = 0L
        )
    }

    fun cancel() {
        try { activeSocket?.close() } catch (_: Exception) {}
        activeSocket = null
        _progress.value = _progress.value.copy(state = TransferState.CANCELLED)
    }
}
