package dev.tap.app.transfer

import dev.tap.app.protocol.TransferState
import java.util.Locale

data class TransferProgress(
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 1,
    val currentFileName: String = "",
    val speedBytesPerSec: Long = 0L,
    val estimatedSecondsRemaining: Long = 0L,
    val state: TransferState = TransferState.IDLE,
    val error: String? = null
) {
    fun percentComplete(): Float {
        if (totalBytes <= 0) return 0f
        return (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    }

    fun formattedPercent(): String {
        return "${(percentComplete() * 100).toInt()}%"
    }

    fun formattedSpeed(): String {
        return "${SelectedFile.formatBytes(speedBytesPerSec)}/s"
    }

    fun formattedEta(): String {
        if (estimatedSecondsRemaining <= 0) return "Calculating..."
        if (estimatedSecondsRemaining < 60) return "${estimatedSecondsRemaining}s left"
        val minutes = estimatedSecondsRemaining / 60
        val seconds = estimatedSecondsRemaining % 60
        return "${minutes}m ${seconds}s left"
    }

    fun formattedProgress(): String {
        return "${SelectedFile.formatBytes(bytesTransferred)} of ${SelectedFile.formatBytes(totalBytes)}"
    }

    fun formattedFileCount(): String {
        if (totalFiles <= 1) return ""
        return "File ${currentFileIndex + 1} of $totalFiles"
    }
}
