package dev.tap.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.history.data.TransferRecord
import dev.tap.app.navigation.TapScreen
import dev.tap.app.protocol.TransferDirection
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun HistoryScreen(viewModel: TransferViewModel) {
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
            title = { Text("Clear Activity History", color = Color.White) },
            text = { Text("Are you sure you want to delete all transfer logs?", color = TapMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TapRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
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
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                // Header
                TapTopBar(
                    title = "Activity",
                    subtitle = "${records.size} total transfers logged",
                    onBack = null,
                    actions = {
                        if (records.isNotEmpty()) {
                            IconButton(
                                onClick = { showClearDialog = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(TapPanel)
                            ) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear", tint = TapRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                )

                // Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Sent", "Received").forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            color = if (isSelected) TapAccent else TapPanel,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clickable { selectedFilter = filter }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    filter,
                                    color = if (isSelected) TapInk else TapMuted,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (filteredRecords.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.ReceiptLong, contentDescription = null, tint = TapMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No activity recorded yet", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text("Completed transfers will appear here", color = TapMuted, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredRecords, key = { it.id }) { record ->
                            ActivityRecordRow(
                                record = record,
                                onDelete = { viewModel.deleteHistoryRecord(record.id) }
                            )
                        }
                    }
                }
            }

            // Bottom Navigation
            MetroBottomBar(
                currentScreen = TapScreen.History,
                onNavigate = { screen -> viewModel.navigateTo(screen) }
            )
        }
    }
}

@Composable
private fun ActivityRecordRow(record: TransferRecord, onDelete: () -> Unit) {
    val isSender = record.direction == TransferDirection.SENT
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = TapPanel,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSender) TapAccent.copy(alpha = 0.15f) else TapPanelLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isSender) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    tint = if (isSender) TapAccent else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(record.fileName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatFileSize(record.fileSize)} • ${if (isSender) "Sent to " else "From "}${record.peerName}",
                        color = TapMuted,
                        fontSize = 11.sp
                    )
                }
            }
            Text("Verified ✓", color = TapGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = TapMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}
