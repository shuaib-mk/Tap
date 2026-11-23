package dev.tap.app.transfer

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedFileTest {

    @Test
    fun testFormatBytes() {
        assertEquals("500 B", SelectedFile.formatBytes(500L))
        assertEquals("1.0 KB", SelectedFile.formatBytes(1024L))
        assertEquals("1.5 MB", SelectedFile.formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.0 GB", SelectedFile.formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun testTransferProgressCalculations() {
        val progress = TransferProgress(
            bytesTransferred = 50L * 1024 * 1024,
            totalBytes = 100L * 1024 * 1024,
            speedBytesPerSec = 10L * 1024 * 1024,
            estimatedSecondsRemaining = 5L
        )

        assertEquals(0.5f, progress.percentComplete(), 0.01f)
        assertEquals("50%", progress.formattedPercent())
        assertEquals("10.0 MB/s", progress.formattedSpeed())
        assertEquals("5s left", progress.formattedEta())
    }
}
