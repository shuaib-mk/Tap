package dev.tap.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.history.data.TransferRecord
import dev.tap.app.protocol.TransferDirection
import dev.tap.app.protocol.TransferStatus
import dev.tap.app.transfer.SelectedFile
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.TransferViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(viewModel: TransferViewModel) {
    val context = LocalContext.current
    val records by viewModel.historyRepo.records.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Sent", "Received"
    var showClearDialog by remember { mutableStateOf(false) }

    val filteredRecords = remember(records, selectedFilter) {
        when (selectedFilter) {
            "Sent" -> records.filter { it.direction == TransferDirection.SENT }
            "Received" -> records.filter { it.direction == TransferDirection.RECEIVED }
            else -> records
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear History", color = Color.White) },
            text = { Text("Are you sure you want to delete all transfer records?", color = TapMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TapRed)
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel", color = TapMuted) }
            },
            containerColor = TapPanel
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = TapInk) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
        ) {
            TapTopBar(
                title = "Transfer History",
                onBack = { viewModel.navigateBack() },
                actions = {
                    if (records.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear", tint = TapRed)
                        }
                    }
                }
            )

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Sent", "Received").forEach { filter ->
                    val active = selectedFilter == filter
                    FilterChip(
                        selected = active,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TapAccent,
                            selectedLabelColor = TapInk,
                            containerColor = TapPanel,
                            labelColor = TapMuted
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (filteredRecords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No transfers yet.",
                        color = TapMuted,
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredRecords, key = { it.id }) { record ->
                        HistoryCard(
                            record = record,
                            onClick = {
                                record.filePath?.let { path ->
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(Uri.parse(path), record.mimeType)
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            },
                            onDelete = { viewModel.deleteHistoryRecord(record.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HistoryCard(
    record: TransferRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(record.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
        sdf.format(Date(record.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TapPanel)
            .border(1.dp, TapBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isSent = record.direction == TransferDirection.SENT
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isSent) Color(0x22B7F36B) else Color(0x224CA6FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isSent) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                contentDescription = null,
                tint = if (isSent) TapAccent else Color(0xFF4CA6FF),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                record.fileName,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${SelectedFile.formatBytes(record.fileSize)} • $dateStr",
                color = TapMuted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Peer: ${record.peerName} • ${record.connectionMethod}",
                color = TapAccent,
                fontSize = 11.sp
            )
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = TapMuted, modifier = Modifier.size(18.dp))
        }
    }
}
