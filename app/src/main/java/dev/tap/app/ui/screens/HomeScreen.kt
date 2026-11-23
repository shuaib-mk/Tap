package dev.tap.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import dev.tap.app.navigation.TapScreen
import dev.tap.app.protocol.TransferDirection
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.TransferViewModel
import dev.tap.app.webrtc.AnywhereEngineState

@Composable
fun HomeScreen(viewModel: TransferViewModel) {
    val context = LocalContext.current
    val anywhereState by viewModel.anywhereState.collectAsState()
    val progress by viewModel.anywhereProgress.collectAsState()
    val isInternet = viewModel.isInternetAvailable
    val historyRecords by viewModel.historyRepo.records.collectAsState()

    var showReceiveDialog by remember { mutableStateOf(false) }
    var receiveCodeInput by remember { mutableStateOf("") }

    // Dialog for entering a 6-character remote pairing code
    if (showReceiveDialog) {
        AlertDialog(
            onDismissRequest = { showReceiveDialog = false },
            title = { Text("Receive Files", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Enter the 6-character pairing code displayed on the sender's phone:",
                        color = TapMuted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = receiveCodeInput,
                        onValueChange = { receiveCodeInput = it.uppercase() },
                        placeholder = { Text("e.g. TAP-4829", color = TapMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = TapAccent,
                            unfocusedBorderColor = TapBorder
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (receiveCodeInput.isNotBlank()) {
                            showReceiveDialog = false
                            val code = receiveCodeInput.trim()
                            viewModel.joinAnywhereShare(code)
                            viewModel.navigateTo(TapScreen.TransferProgress)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TapAccent, contentColor = TapInk),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Connect", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReceiveDialog = false }) {
                    Text("Cancel", color = TapMuted)
                }
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
            // Main Scrollable Body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header: Minimal TAP + Status Indicator + Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TAP",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp
                        )
                        Spacer(Modifier.height(4.dp))
                        StatusChip(
                            label = if (isInternet) "Anywhere Ready (WebRTC)" else "Offline Ready (Wi-Fi Direct)",
                            active = isInternet,
                            accentColor = if (isInternet) TapAccent else Color(0xFFFBBF24)
                        )
                    }

                    // Top Action Icons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.navigateTo(TapScreen.QrScanner) },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(TapPanel)
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan QR", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.navigateTo(TapScreen.Settings) },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(TapPanel)
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Hero Primary Action Tiles: Share & Receive
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Share Action Card
                    Surface(
                        color = TapAccent,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clickable { viewModel.navigateTo(TapScreen.FilePicker) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(TapInk),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = TapAccent, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Share", color = TapInk, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                                Text("Send anywhere", color = TapInk.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                        }
                    }

                    // 2. Receive Action Card
                    Surface(
                        color = TapPanel,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(130.dp)
                            .clickable { showReceiveDialog = true }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(TapPanelLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.ArrowDownward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                            Column {
                                Text("Receive", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                                Text("Enter code or tap", color = TapMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Active Transfer Banner (when session created or transferring)
                when (val state = anywhereState) {
                    is AnywhereEngineState.WaitingForPeer -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = TapPanel,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("READY TO TRANSFER", color = TapAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    IconButton(
                                        onClick = { viewModel.cancelAnywhereTransfer() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Cancel", tint = TapMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = state.sessionCode,
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 2.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Tell receiver to enter this code, or bring phones together to tap.",
                                    color = TapMuted,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Share Link", state.shareLink))
                                            Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TapPanelLight, contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Copy Link", fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = { viewModel.navigateTo(TapScreen.TransferProgress) },
                                        colors = ButtonDefaults.buttonColors(containerColor = TapAccent, contentColor = TapInk),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("View Status", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    is AnywhereEngineState.Transferring -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = TapPanel,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.navigateTo(TapScreen.TransferProgress) }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("TRANSFER IN PROGRESS", color = TapAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    Text(progress.formattedSpeed, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(progress.currentFileName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { progress.percent },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = TapAccent,
                                    trackColor = TapPanelLight
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                    else -> {}
                }

                // Physical Proximity Notice
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = TapPanel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.navigateTo(TapScreen.ProximityShare) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(TapPanelLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Nfc, contentDescription = null, tint = TapAccent, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Physical Tap to Share", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Always ready • Touch phones back-to-back", color = TapMuted, fontSize = 11.sp)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TapMuted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Recent Activity Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Activity",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
                    TextButton(onClick = { viewModel.navigateTo(TapScreen.History) }) {
                        Text("See all", color = TapAccent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                if (historyRecords.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No recent transfers yet", color = TapMuted, fontSize = 13.sp)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (record in historyRecords.take(3)) {
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
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(if (isSender) TapAccent.copy(alpha = 0.15f) else TapPanelLight),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (isSender) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                            contentDescription = null,
                                            tint = if (isSender) TapAccent else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(record.fileName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                        Text("${formatFileSize(record.fileSize)} • ${if (isSender) "Sent to " else "From "}${record.peerName}", color = TapMuted, fontSize = 11.sp)
                                    }
                                    Text("✓", color = TapGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            // Bottom Navigation Bar
            MetroBottomBar(
                currentScreen = TapScreen.Home,
                onNavigate = { screen -> viewModel.navigateTo(screen) }
            )
        }
    }
}
