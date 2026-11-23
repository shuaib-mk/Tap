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
 * - Physical Tap to Share Sender Server and Auto-Pull Receiver Client
 */
class TransferTransportManager(private val context: Context) {

    companion object {
        const val DEFAULT_PROXIMITY_PORT = 8990
        const val CHUNK_SIZE = 256 * 1024 // 256 KB
        val PROTOCOL_MAGIC = byteArrayOf(0x54, 0x41, 0x50, 0x32) // "TAP2"
        const val EOF_MARKER = -1

        fun serializeOffer(offer: ProximityHandshakeOffer): String {
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

        fun deserializeOffer(json: String): ProximityHandshakeOffer {
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

        fun serializeResponse(response: ProximityHandshakeResponse): String {
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

        fun deserializeResponse(json: String): ProximityHandshakeResponse {
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
    }

    private val _progress = MutableStateFlow(ProximityProgress())
    val progress: StateFlow<ProximityProgress> = _progress.asStateFlow()

    @Volatile
    private var isCancelled = false

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    fun cancel() {
        isCancelled = true
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
    }

    // ---------------------------------------------------------------------------------------------
    // PHYSICAL TAP TO SHARE: SENDER SERVER (Phone A listens, streams when Phone B connects)
    // ---------------------------------------------------------------------------------------------
    fun startSenderServer(
        scope: CoroutineScope,
        port: Int = DEFAULT_PROXIMITY_PORT,
        security: TransferSecurityManager,
        files: List<ProximityFile>,
        senderDeviceName: String,
        senderDeviceId: String,
        sessionId: String,
        onSasReady: (sasCode: String) -> Unit,
        onCompleted: (fileCount: Int, totalBytes: Long) -> Unit
    ) {
        stopServer()
        isCancelled = false

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }

                while (isActive && !isCancelled) {
                    val clientSocket = serverSocket?.accept() ?: break
                    clientSocket.tcpNoDelay = true

                    launch(Dispatchers.IO) {
                        handleSenderConnection(
                            socket = clientSocket,
                            security = security,
                            files = files,
                            senderDeviceName = senderDeviceName,
                            senderDeviceId = senderDeviceId,
                            sessionId = sessionId,
                            onSasReady = onSasReady,
                            onCompleted = onCompleted
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

    private suspend fun handleSenderConnection(
        socket: Socket,
        security: TransferSecurityManager,
        files: List<ProximityFile>,
        senderDeviceName: String,
        senderDeviceId: String,
        sessionId: String,
        onSasReady: (sasCode: String) -> Unit,
        onCompleted: (fileCount: Int, totalBytes: Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            socket.use { s ->
                val input = DataInputStream(BufferedInputStream(s.getInputStream()))
                val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))

                // 1. Verify Magic Header
                val magic = ByteArray(4)
                input.readFully(magic)
                if (!magic.contentEquals(PROTOCOL_MAGIC)) {
                    throw IOException("Invalid TAP protocol header")
                }

                // 2. Prepare manifests & Offer
                val manifests = files.map { file ->
                    val sha256 = context.contentResolver.openInputStream(file.uri)?.use { stream ->
                        TransferSecurityManager.calculateStreamHash(stream)
                    } ?: "0000000000000000000000000000000000000000000000000000000000000000"

                    ProximityFileManifest(
                        name = file.name,
                        sizeBytes = file.sizeBytes,
                        mimeType = file.mimeType,
                        sha256 = sha256
                    )
                }

                val offer = ProximityHandshakeOffer(
                    sessionId = sessionId,
                    senderDeviceName = senderDeviceName,
                    senderDeviceId = senderDeviceId,
                    senderPublicKeyBase64 = security.getPublicKeyBase64(),
                    hostAddress = getLocalIpAddress() ?: "127.0.0.1",
                    port = s.localPort,
                    files = manifests
                )

                // Write offer to client
                out.writeUTF(serializeOffer(offer))
                out.flush()

                // 3. Read client response
                val responseJson = input.readUTF()
                val response = deserializeResponse(responseJson)

                if (!response.accepted) {
                    throw IllegalStateException("Transfer declined by receiver: ${response.rejectionReason ?: "Declined"}")
                }

                // 4. Derive key and compute SAS
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
                    throw SecurityException("Security check failed: SAS code mismatch!")
                }

                onSasReady(expectedSas)
                _progress.value = _progress.value.copy(sasVerificationCode = expectedSas)

                // 5. Stream encrypted chunks
                val totalBytes = files.sumOf { it.sizeBytes }
                var overallBytes = 0L
                val startTime = System.currentTimeMillis()

                files.forEachIndexed { index, file ->
                    if (isCancelled) throw InterruptedException("Transfer cancelled.")

                    val fileManifest = manifests[index]
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

                    out.writeInt(EOF_MARKER)
                    out.flush()
                }

                // 6. Read ACK
                val ack = input.readUTF()
                if (ack == "ACK_TRANSFER_COMPLETE") {
                    _progress.value = _progress.value.copy(isComplete = true, verifiedFilesCount = files.size)
                    onCompleted(files.size, totalBytes)
                }
            }
        } catch (e: Exception) {
            _progress.value = _progress.value.copy(error = e.message)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PHYSICAL TAP TO SHARE: RECEIVER AUTO-PULL CLIENT (Phone B connects to Phone A after NFC tap)
    // ---------------------------------------------------------------------------------------------
    suspend fun pullFilesFromHost(
        host: String,
        port: Int,
        security: TransferSecurityManager,
        receiverDeviceName: String,
        receiverDeviceId: String,
        onTransferRequest: suspend (offer: ProximityHandshakeOffer, sasCode: String) -> Boolean,
        onTransferSuccess: (fileCount: Int, totalBytes: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        isCancelled = false
        try {
            Socket(host, port).use { socket ->
                socket.tcpNoDelay = true
                val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

                // 1. Send Magic Header
                out.write(PROTOCOL_MAGIC)
                out.flush()

                // 2. Read Offer from Sender
                val offerJson = input.readUTF()
                val offer = deserializeOffer(offerJson)

                // 3. Compute session key & SAS code
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

                // 4. Ask recipient for consent
                val accepted = onTransferRequest(offer, sasCode)

                if (!accepted) {
                    val reject = ProximityHandshakeResponse(
                        sessionId = offer.sessionId,
                        receiverDeviceName = receiverDeviceName,
                        receiverDeviceId = receiverDeviceId,
                        receiverPublicKeyBase64 = security.getPublicKeyBase64(),
                        accepted = false,
                        rejectionReason = "User declined transfer.",
                        sasCode = sasCode
                    )
                    out.writeUTF(serializeResponse(reject))
                    out.flush()
                    return@use
                }

                // Send acceptance
                val approve = ProximityHandshakeResponse(
                    sessionId = offer.sessionId,
                    receiverDeviceName = receiverDeviceName,
                    receiverDeviceId = receiverDeviceId,
                    receiverPublicKeyBase64 = security.getPublicKeyBase64(),
                    accepted = true,
                    sasCode = sasCode
                )
                out.writeUTF(serializeResponse(approve))
                out.flush()

                // 5. Receive, decrypt, and verify each file
                var overallReceived = 0L
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
                            if (chunkSize == EOF_MARKER) break

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

                    // 6. Strict SHA-256 Verification
                    val computedSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!computedSha256.equals(expectedSha256, ignoreCase = true)) {
                        tempFile.delete()
                        throw SecurityException("Checksum failure: Expected $expectedSha256, got $computedSha256")
                    }

                    FileManager.saveVerifiedFile(context, tempFile, fileName, mimeType)
                    tempFile.delete()
                }

                out.writeUTF("ACK_TRANSFER_COMPLETE")
                out.flush()

                _progress.value = _progress.value.copy(isComplete = true, verifiedFilesCount = offer.files.size)
                onTransferSuccess(offer.files.size, totalBytes)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            _progress.value = _progress.value.copy(error = e.message)
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // LEGACY SENDER PUSH CLIENT
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

                out.write(PROTOCOL_MAGIC)
                val offerJson = serializeOffer(offer)
                out.writeUTF(offerJson)
                out.flush()

                val responseJson = input.readUTF()
                val response = deserializeResponse(responseJson)

                if (!response.accepted) {
                    throw IllegalStateException("Transfer rejected by receiver: ${response.rejectionReason ?: "Declined"}")
                }

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
                    throw SecurityException("Security check failed: SAS code mismatch!")
                }

                onSasReady?.invoke(expectedSas)
                _progress.value = _progress.value.copy(sasVerificationCode = expectedSas)

                files.forEachIndexed { index, file ->
                    if (isCancelled) throw InterruptedException("Transfer cancelled.")

                    val fileManifest = manifests[index]
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

                    out.writeInt(EOF_MARKER)
                    out.flush()
                }

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
    // RECEIVER SERVER (Push model)
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
        stopServer()
        isCancelled = false

        serverJob = scope.launch(Dispatchers.IO) {
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

    fun stopServer() {
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    fun stopReceiverServer() = stopServer()

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

                val magic = ByteArray(4)
                input.readFully(magic)
                if (!magic.contentEquals(PROTOCOL_MAGIC)) {
                    throw IOException("Invalid TAP Proximity protocol magic header")
                }

                val offerJson = input.readUTF()
                val offer = deserializeOffer(offerJson)

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
                            if (chunkSize == EOF_MARKER) break

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

                    val computedSha256 = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!computedSha256.equals(expectedSha256, ignoreCase = true)) {
                        tempFile.delete()
                        throw SecurityException("Corrupted file payload! Expected SHA-256: $expectedSha256 but got $computedSha256")
                    }

                    FileManager.saveVerifiedFile(context, tempFile, fileName, mimeType)
                    tempFile.delete()
                }

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
