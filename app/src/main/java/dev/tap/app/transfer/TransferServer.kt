package dev.tap.app.transfer

import android.content.Context
import dev.tap.app.history.TransferHistoryRepository
import dev.tap.app.history.data.TransferRecord
import dev.tap.app.protocol.*
import dev.tap.app.security.SessionManager
import dev.tap.app.security.TapSecurity
import dev.tap.app.storage.FileManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import javax.crypto.SecretKey

/**
 * TCP Transfer Server running on receiver device.
 * Awaits peer connections, coordinates receiver acceptance, streams incoming file chunks,
 * decrypts with AES-256-GCM, verifies SHA-256 integrity, and saves to storage.
 */
class TransferServer(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val historyRepo: TransferHistoryRepository,
    private val port: Int = TapProtocol.DEFAULT_PORT
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var activeSocket: Socket? = null

    private val _progress = MutableStateFlow(TransferProgress())
    val progress: StateFlow<TransferProgress> = _progress.asStateFlow()

    private val _pendingRequest = MutableStateFlow<TransferRequest?>(null)
    val pendingRequest: StateFlow<TransferRequest?> = _pendingRequest.asStateFlow()

    private var acceptContinuation: CompletableDeferred<Boolean>? = null

    fun start(scope: CoroutineScope) {
        stop()
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    activeSocket = socket
                    handleClient(socket)
                }
            } catch (e: Exception) {
                if (e !is InterruptedException && e !is java.net.SocketException) {
                    _progress.value = _progress.value.copy(
                        state = TransferState.FAILED,
                        error = e.localizedMessage
                    )
                }
            }
        }
    }

    private suspend fun handleClient(socket: Socket) = withContext(Dispatchers.IO) {
        val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

        try {
            // 1. Read peer SessionOffer / Handshake
            val offerJson = input.readUTF()
            val peerOffer = TapProtocol.json.decodeFromString<SessionOffer>(offerJson)

            // Complete ECDH Key Exchange
            val sharedSecret = sessionManager.completeHandshake(
                sessionId = peerOffer.sessionId,
                peerPublicKeyBase64 = peerOffer.publicKeyBase64,
                peerName = peerOffer.deviceName
            ) ?: throw IllegalStateException("Key handshake failed: session invalid or expired")

            // Send our SessionOffer back to confirm handshake
            val session = sessionManager.getSession(peerOffer.sessionId)
                ?: throw IllegalStateException("Session not found")
            val myOffer = session.toSessionOffer(getLocalIpAddress(), port)
            output.writeUTF(TapProtocol.json.encodeToString(myOffer))
            output.flush()

            // 2. Read TransferRequest
            val requestJson = input.readUTF()
            val request = TapProtocol.json.decodeFromString<TransferRequest>(requestJson)
            _pendingRequest.value = request
            _progress.value = TransferProgress(
                totalBytes = request.totalBytes,
                totalFiles = request.files.size,
                state = TransferState.AWAITING_ACCEPTANCE
            )

            // 3. Await receiver user's explicit accept/reject decision
            val deferred = CompletableDeferred<Boolean>()
            acceptContinuation = deferred
            val accepted = deferred.await()

            // Send acceptance response to peer
            val response = TransferResponse(
                sessionId = request.sessionId,
                accepted = accepted,
                reason = if (!accepted) "Receiver declined the transfer" else null
            )
            output.writeUTF(TapProtocol.json.encodeToString(response))
            output.flush()

            if (!accepted) {
                _progress.value = _progress.value.copy(state = TransferState.CANCELLED)
                return@withContext
            }

            // 4. Receiver accepted: Stream and receive files
            _progress.value = _progress.value.copy(state = TransferState.TRANSFERRING)
            receiveFiles(input, output, request, sharedSecret)

        } catch (e: Exception) {
            _progress.value = _progress.value.copy(
                state = TransferState.FAILED,
                error = e.localizedMessage ?: "Connection error"
            )
        } finally {
            try { socket.close() } catch (_: Exception) {}
            activeSocket = null
            _pendingRequest.value = null
        }
    }

    private suspend fun receiveFiles(
        input: DataInputStream,
        output: DataOutputStream,
        request: TransferRequest,
        sharedSecret: SecretKey
    ) = withContext(Dispatchers.IO) {
        var totalBytesReceived = 0L
        val totalBytes = request.totalBytes
        var startTime = System.currentTimeMillis()
        var lastCalcTime = startTime
        var lastBytes = 0L

        request.files.forEachIndexed { fileIndex, manifest ->
            val tempFile = FileManager.createTempFile(context, manifest.fileName)
            val fileOut = FileOutputStream(tempFile)

            _progress.value = _progress.value.copy(
                currentFileIndex = fileIndex,
                currentFileName = manifest.fileName,
                state = TransferState.TRANSFERRING
            )

            try {
                var fileBytesReceived = 0L
                while (fileBytesReceived < manifest.sizeBytes) {
                    val headerJson = input.readUTF()
                    val header = TapProtocol.json.decodeFromString<ChunkHeader>(headerJson)

                    val encryptedChunk = ByteArray(header.size)
                    input.readFully(encryptedChunk)

                    val nonce = android.util.Base64.decode(header.nonceBase64, android.util.Base64.NO_WRAP)
                    val decryptedChunk = TapSecurity.decrypt(encryptedChunk, sharedSecret, nonce)

                    fileOut.write(decryptedChunk)
                    fileBytesReceived += decryptedChunk.size
                    totalBytesReceived += decryptedChunk.size

                    val now = System.currentTimeMillis()
                    if (now - lastCalcTime >= 500) {
                        val durationSec = (now - lastCalcTime) / 1000.0
                        val bytesSince = totalBytesReceived - lastBytes
                        val speed = if (durationSec > 0) (bytesSince / durationSec).toLong() else 0L
                        val remainingBytes = totalBytes - totalBytesReceived
                        val eta = if (speed > 0) remainingBytes / speed else 0L

                        _progress.value = _progress.value.copy(
                            bytesTransferred = totalBytesReceived,
                            speedBytesPerSec = speed,
                            estimatedSecondsRemaining = eta
                        )
                        lastCalcTime = now
                        lastBytes = totalBytesReceived
                    }
                }
                fileOut.flush()
                fileOut.close()

                // 5. Verify SHA-256 integrity
                _progress.value = _progress.value.copy(state = TransferState.VERIFYING)
                val calculatedHash = FileInputStream(tempFile).use { TapSecurity.hashSHA256Stream(it) }

                if (!calculatedHash.equals(manifest.sha256, ignoreCase = true)) {
                    tempFile.delete()
                    throw SecurityException("File integrity check failed for ${manifest.fileName}! Expected: ${manifest.sha256}, Got: $calculatedHash")
                }

                // 6. Save verified file to public storage
                val finalUri = FileManager.saveVerifiedFile(context, tempFile, manifest.fileName, manifest.mimeType)

                // Send confirmation to sender
                val completeMsg = TransferComplete(
                    transferId = manifest.transferId,
                    fileIndex = fileIndex,
                    sha256 = calculatedHash,
                    success = true
                )
                output.writeUTF(TapProtocol.json.encodeToString(completeMsg))
                output.flush()

                // Record in transfer history
                historyRepo.insert(
                    TransferRecord(
                        transferId = manifest.transferId,
                        fileName = manifest.fileName,
                        fileSize = manifest.sizeBytes,
                        mimeType = manifest.mimeType,
                        direction = TransferDirection.RECEIVED,
                        status = TransferStatus.COMPLETED,
                        peerName = request.senderName,
                        connectionMethod = "Direct Wi-Fi",
                        filePath = finalUri?.toString()
                    )
                )

            } catch (e: Exception) {
                fileOut.close()
                tempFile.delete()
                throw e
            }
        }

        _progress.value = _progress.value.copy(
            bytesTransferred = totalBytes,
            state = TransferState.COMPLETE,
            speedBytesPerSec = 0L,
            estimatedSecondsRemaining = 0L
        )
    }

    fun respondToRequest(accepted: Boolean) {
        acceptContinuation?.complete(accepted)
        acceptContinuation = null
    }

    fun stop() {
        try { activeSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        serverJob?.cancel()
        serverSocket = null
        serverJob = null
        activeSocket = null
    }

    companion object {
        fun getLocalIpAddress(): String {
            return dev.tap.app.capabilities.CompatibilityEngine.getBestLocalIpv4Address() ?: "127.0.0.1"
        }
    }
}
