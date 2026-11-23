package dev.tap.app.proximity

import android.content.Context
import dev.tap.app.storage.FileManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.Inet4Address
import java.net.NetworkInterface
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
    val error: String? = null,
    val sasVerificationCode: String = "",
    val verifiedFilesCount: Int = 0
) {
    val percent: Float
        get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedSpeed: String
        get() = String.format("%.2f MB/s", speedBytesPerSec / (1024.0 * 1024.0))
}

/**
 * High-speed bidirectional TCP socket transport layer for Proximity File Sharing.
 * Supports:
 * - End-to-end authenticated session bootstrapping
 * - Streaming 256 KB encrypted chunks with AES-256-GCM
 * - Monotonic 12-byte IV nonce management
 * - Mandatory streaming SHA-256 integrity verification before atomic disk commit
 * - Full receiver-side server and sender-side client
 */
class TransferTransportManager(private val context: Context) {

    companion object {
        const val DEFAULT_PROXIMITY_PORT = 8990
        const val CHUNK_SIZE = 256 * 1024 // 256 KB
        val PROTOCOL_MAGIC = byteArrayOf(0x54, 0x41, 0x50, 0x32) // "TAP2"
        const val EOF_MARKER = -1
    }

    private val _progress = MutableStateFlow(ProximityProgress())
    val progress: StateFlow<ProximityProgress> = _progress.asStateFlow()

    @Volatile
    private var isCancelled = false

    private var serverSocket: ServerSocket? = null
    private var receiverJob: Job? = null

    fun cancel() {
        isCancelled = true
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------------------------------------
    // SENDER CLIENT PIPELINE
    // ---------------------------------------------------------------------------------------------
    suspend fun sendFiles(
        host: String,
        port: Int,
        security: TransferSecurityManager,
        files: List<ProximityFile>,
        senderDeviceName: String,
        senderDeviceId: String,
        onSasReady: ((String) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        isCancelled = false
        val totalBytes = files.sumOf { it.sizeBytes }
        var overallBytes: Long = 0
        val startTime = System.currentTimeMillis()

        try {
            // 1. Pre-compute SHA-256 hash for each file to be sent in the manifest
            val manifests = files.map { file ->
                val sha256 = context.contentResolver.openInputStream(file.uri)?.use { stream ->
                    TransferSecurityManager.calculateStreamHash(stream)
                } ?: throw IllegalStateException("Cannot read file ${file.name}")

                ProximityFileManifest(
                    name = file.name,
                    sizeBytes = file.sizeBytes,
                    mimeType = file.mimeType,
                    sha256 = sha256
                )
            }

            // 2. Prepare Session Offer
            val sessionId = java.util.UUID.randomUUID().toString()
            val offer = ProximityHandshakeOffer(
                sessionId = sessionId,
                senderDeviceName = senderDeviceName,
                senderDeviceId = senderDeviceId,
                senderPublicKeyBase64 = security.getPublicKeyBase64(),
                hostAddress = getLocalIpAddress() ?: host,
                port = port,
                files = manifests
            )

            Socket(host, port).use { socket ->
                socket.tcpNoDelay = true
                val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

                // 3. Write protocol magic header & offer
                out.write(PROTOCOL_MAGIC)
                val offerJson = serializeOffer(offer)
                out.writeUTF(offerJson)
                out.flush()

                // 4. Read response from receiver
                val responseJson = input.readUTF()
                val response = deserializeResponse(responseJson)

                if (!response.accepted) {
                    throw IllegalStateException("Transfer rejected by receiver: ${response.rejectionReason ?: "Declined"}")
                }

                // 5. Complete cryptographic key agreement & verify SAS code
                val sessionKey = security.deriveSessionKey(
                    remotePublicKeyBase64 = response.receiverPublicKeyBase64,
                    sessionId = sessionId,
                    senderId = senderDeviceId,
                    receiverId = response.receiverDeviceId
                )
                val expectedSas = security.calculateSasCode(
                    remotePublicKeyBase64 = response.receiverPublicKeyBase64,
                    sessionId = sessionId
                )

                if (response.sasCode.isNotBlank() && response.sasCode != expectedSas) {
                    throw SecurityException("Security check failed: SAS code mismatch. Possible MITM attack!")
                }

                onSasReady?.invoke(expectedSas)
                _progress.value = _progress.value.copy(sasVerificationCode = expectedSas)

                // 6. Stream chunked encrypted files
                files.forEachIndexed { index, file ->
                    if (isCancelled) throw InterruptedException("Transfer cancelled.")

                    val fileManifest = manifests[index]
                    // Write file header: name, size, mime, sha256
                    out.writeUTF(fileManifest.name)
                    out.writeLong(fileManifest.sizeBytes)
                    out.writeUTF(fileManifest.mimeType)
                    out.writeUTF(fileManifest.sha256)
                    out.flush()

                    val fileStream: InputStream = context.contentResolver.openInputStream(file.uri)
                        ?: throw IllegalStateException("Cannot read file ${file.name}")

                    val buffer = ByteArray(CHUNK_SIZE)
                    var chunkIndex: Long = 0

                    fileStream.use { stream ->
                        var readBytes: Int
                        while (stream.read(buffer).also { readBytes = it } != -1) {
                            if (isCancelled) throw InterruptedException("Transfer cancelled.")

                            val plaintext = buffer.copyOf(readBytes)
                            val encrypted = security.encryptChunk(
                                key = sessionKey,
                                chunkIndex = chunkIndex++,
                                plaintext = plaintext,
                                isSender = true,
                                sessionId = sessionId
                            )

                            // Write chunk length (4 bytes) + encrypted payload
                            out.writeInt(encrypted.size)
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
                                speedBytesPerSec = speed,
                                sasVerificationCode = expectedSas,
                                verifiedFilesCount = index
                            )
                        }
                    }

                    // Write EOF marker for this file
                    out.writeInt(EOF_MARKER)
                    out.flush()
                }

                // 7. Read receiver final confirmation ACK
                val ack = input.readUTF()
                if (ack != "ACK_TRANSFER_COMPLETE") {
                    throw IOException("Receiver did not acknowledge transfer completion: $ack")
                }
            }

            _progress.value = _progress.value.copy(
                isComplete = true,
                verifiedFilesCount = files.size
            )
            Result.success(Unit)
        } catch (e: Exception) {
            _progress.value = _progress.value.copy(error = e.message)
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // RECEIVER SERVER PIPELINE
    // ---------------------------------------------------------------------------------------------
    fun startReceiverServer(
        scope: CoroutineScope,
        port: Int = DEFAULT_PROXIMITY_PORT,
        security: TransferSecurityManager,
        receiverDeviceName: String,
        receiverDeviceId: String,
        onTransferRequest: suspend (offer: ProximityHandshakeOffer, sasCode: String) -> Boolean,
        onTransferSuccess: (fileCount: Int, totalBytes: Long) -> Unit
    ) {
        stopReceiverServer()
        isCancelled = false

        receiverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }

                while (isActive && !isCancelled) {
                    val clientSocket = serverSocket?.accept() ?: break
                    clientSocket.tcpNoDelay = true

                    launch(Dispatchers.IO) {
                        handleIncomingClient(
                            socket = clientSocket,
                            security = security,
                            receiverDeviceName = receiverDeviceName,
                            receiverDeviceId = receiverDeviceId,
                            onTransferRequest = onTransferRequest,
                            onTransferSuccess = onTransferSuccess
                        )
                    }
                }
            } catch (e: Exception) {
                if (e !is InterruptedException && e !is java.net.SocketException) {
                    _progress.value = _progress.value.copy(error = e.message)
                }
            }
        }
    }

    fun stopReceiverServer() {
        receiverJob?.cancel()
        receiverJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private suspend fun handleIncomingClient(
        socket: Socket,
        security: TransferSecurityManager,
        receiverDeviceName: String,
        receiverDeviceId: String,
        onTransferRequest: suspend (offer: ProximityHandshakeOffer, sasCode: String) -> Boolean,
        onTransferSuccess: (fileCount: Int, totalBytes: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            socket.use { s ->
                val input = DataInputStream(BufferedInputStream(s.getInputStream()))
                val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))

                // 1. Verify Magic Header
                val magic = ByteArray(4)
                input.readFully(magic)
                if (!magic.contentEquals(PROTOCOL_MAGIC)) {
                    throw IOException("Invalid TAP Proximity protocol magic header")
                }

                // 2. Read Offer JSON
                val offerJson = input.readUTF()
                val offer = deserializeOffer(offerJson)

                // 3. Derive Session Key & SAS Code
                val sessionKey = security.deriveSessionKey(
                    remotePublicKeyBase64 = offer.senderPublicKeyBase64,
                    sessionId = offer.sessionId,
                    senderId = offer.senderDeviceId,
                    receiverId = receiverDeviceId
                )
                val sasCode = security.calculateSasCode(
                    remotePublicKeyBase64 = offer.senderPublicKeyBase64,
                    sessionId = offer.sessionId
                )

                _progress.value = ProximityProgress(
                    totalFiles = offer.files.size,
                    totalBytes = offer.files.sumOf { it.sizeBytes },
                    sasVerificationCode = sasCode
                )

                // 4. Request User Approval
                val isApproved = onTransferRequest(offer, sasCode)

                if (!isApproved) {
                    val rejectResponse = ProximityHandshakeResponse(
                        sessionId = offer.sessionId,
                        receiverDeviceName = receiverDeviceName,
                        receiverDeviceId = receiverDeviceId,
                        receiverPublicKeyBase64 = security.getPublicKeyBase64(),
                        accepted = false,
                        rejectionReason = "User rejected incoming proximity transfer.",
                        sasCode = sasCode
                    )
                    out.writeUTF(serializeResponse(rejectResponse))
                    out.flush()
                    return@use
                }

                // Write approval response
                val approveResponse = ProximityHandshakeResponse(
                    sessionId = offer.sessionId,
                    receiverDeviceName = receiverDeviceName,
                    receiverDeviceId = receiverDeviceId,
                    receiverPublicKeyBase64 = security.getPublicKeyBase64(),
                    accepted = true,
                    sasCode = sasCode
                )
                out.writeUTF(serializeResponse(approveResponse))
                out.flush()

                // 5. Receive, Decrypt, and Verify SHA-256 for each file
                var overallReceived: Long = 0
                val totalBytes = offer.files.sumOf { it.sizeBytes }
                val startTime = System.currentTimeMillis()

                offer.files.forEachIndexed { index, fileManifest ->
                    if (isCancelled) throw InterruptedException("Transfer cancelled.")

                    val fileName = input.readUTF()
                    val fileSize = input.readLong()
                    val mimeType = input.readUTF()
                    val expectedSha256 = input.readUTF()

                    val tempFile = FileManager.createTempFile(context, fileName)
                    val digest = MessageDigest.getInstance("SHA-256")

                    FileOutputStream(tempFile).use { fileOut ->
                        while (true) {
                            if (isCancelled) throw InterruptedException("Transfer cancelled.")

                            val chunkSize = input.readInt()
                            if (chunkSize == EOF_MARKER) break // End of this file

                            val encryptedChunk = ByteArray(chunkSize)
                            input.readFully(encryptedChunk)

                            val plaintext = security.decryptChunk(sessionKey, encryptedChunk)
                            fileOut.write(plaintext)
                            digest.update(plaintext)

                            overallReceived += plaintext.size
                            val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                            val speed = if (elapsedSec > 0) (overallReceived / elapsedSec).toLong() else 0L

                            _progress.value = ProximityProgress(
                                currentFileName = fileName,
                                currentFileIndex = index + 1,
                                totalFiles = offer.files.size,
                                bytesTransferred = overallReceived,
                                totalBytes = totalBytes,
                                speedBytesPerSec = speed,
                                sasVerificationCode = sasCode,
                                verifiedFilesCount = index
                            )
                        }
                    }

                    // 6. Strict SHA-256 Integrity Verification
                    val computedSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!computedSha256.equals(expectedSha256, ignoreCase = true)) {
                        tempFile.delete()
                        throw SecurityException("Corrupted file payload! Expected SHA-256: $expectedSha256 but got $computedSha256")
                    }

                    // 7. Atomic Commit to Storage
                    FileManager.saveVerifiedFile(context, tempFile, fileName, mimeType)
                    tempFile.delete()
                }

                // 8. Write Final ACK to Sender
                out.writeUTF("ACK_TRANSFER_COMPLETE")
                out.flush()

                _progress.value = _progress.value.copy(
                    isComplete = true,
                    verifiedFilesCount = offer.files.size
                )
                onTransferSuccess(offer.files.size, totalBytes)
            }
        } catch (e: Exception) {
            _progress.value = _progress.value.copy(error = e.message)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // SERIALIZATION HELPERS
    // ---------------------------------------------------------------------------------------------
    private fun serializeOffer(offer: ProximityHandshakeOffer): String {
        val root = JSONObject().apply {
            put("sessionId", offer.sessionId)
            put("senderDeviceName", offer.senderDeviceName)
            put("senderDeviceId", offer.senderDeviceId)
            put("senderPublicKeyBase64", offer.senderPublicKeyBase64)
            put("hostAddress", offer.hostAddress)
            put("port", offer.port)
            put("timestamp", offer.timestamp)

            val filesArray = JSONArray()
            offer.files.forEach { f ->
                val fObj = JSONObject().apply {
                    put("name", f.name)
                    put("sizeBytes", f.sizeBytes)
                    put("mimeType", f.mimeType)
                    put("sha256", f.sha256)
                }
                filesArray.put(fObj)
            }
            put("files", filesArray)
        }
        return root.toString()
    }

    private fun deserializeOffer(json: String): ProximityHandshakeOffer {
        val root = JSONObject(json)
        val filesList = mutableListOf<ProximityFileManifest>()
        val filesArray = root.getJSONArray("files")
        for (i in 0 until filesArray.length()) {
            val fObj = filesArray.getJSONObject(i)
            filesList.add(
                ProximityFileManifest(
                    name = fObj.getString("name"),
                    sizeBytes = fObj.getLong("sizeBytes"),
                    mimeType = fObj.getString("mimeType"),
                    sha256 = fObj.getString("sha256")
                )
            )
        }

        return ProximityHandshakeOffer(
            sessionId = root.getString("sessionId"),
            senderDeviceName = root.getString("senderDeviceName"),
            senderDeviceId = root.getString("senderDeviceId"),
            senderPublicKeyBase64 = root.getString("senderPublicKeyBase64"),
            hostAddress = root.getString("hostAddress"),
            port = root.getInt("port"),
            files = filesList,
            timestamp = root.optLong("timestamp", System.currentTimeMillis())
        )
    }

    private fun serializeResponse(response: ProximityHandshakeResponse): String {
        return JSONObject().apply {
            put("sessionId", response.sessionId)
            put("receiverDeviceName", response.receiverDeviceName)
            put("receiverDeviceId", response.receiverDeviceId)
            put("receiverPublicKeyBase64", response.receiverPublicKeyBase64)
            put("accepted", response.accepted)
            put("rejectionReason", response.rejectionReason ?: "")
            put("sasCode", response.sasCode)
        }.toString()
    }

    private fun deserializeResponse(json: String): ProximityHandshakeResponse {
        val root = JSONObject(json)
        return ProximityHandshakeResponse(
            sessionId = root.getString("sessionId"),
            receiverDeviceName = root.getString("receiverDeviceName"),
            receiverDeviceId = root.getString("receiverDeviceId"),
            receiverPublicKeyBase64 = root.getString("receiverPublicKeyBase64"),
            accepted = root.getBoolean("accepted"),
            rejectionReason = root.optString("rejectionReason").takeIf { it.isNotBlank() },
            sasCode = root.optString("sasCode", "")
        )
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
