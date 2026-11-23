package dev.tap.app.history.data

import dev.tap.app.protocol.TransferDirection
import dev.tap.app.protocol.TransferStatus

data class TransferRecord(
    val id: Long = 0,
    val transferId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val direction: TransferDirection,
    val status: TransferStatus,
    val peerName: String,
    val connectionMethod: String,
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String? = null
)
