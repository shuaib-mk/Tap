package dev.tap.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.history.data.TransferRecord
import dev.tap.app.navigation.TapScreen
import dev.tap.app.protocol.TransferDirection
import dev.tap.app.ui.components.*
import dev.tap.app.ui.theme.AnthropicSerifDisplay
import dev.tap.app.ui.theme.AnthropicSerifText
import dev.tap.app.ui.viewmodels.TransferViewModel
import dev.tap.app.webrtc.AnywhereEngineState

@Composable
fun HomeScreen(viewModel: TransferViewModel) {
    val context = LocalContext.current
    val anywhereState by viewModel.anywhereState.collectAsState()
    val progress by viewModel.anywhereProgress.collectAsState()
    val historyRecords by viewModel.historyRepo.records.collectAsState()
    val isBeamActive by viewModel.isBackgroundTapSharing.collectAsState()

    var showReceiveDialog by remember { mutableStateOf(false) }
    var receiveCodeInput by remember { mutableStateOf("") }

    LaunchedEffect(showReceiveDialog) {
        viewModel.setReceiveModeActive(showReceiveDialog)
    }

    // Dialog for entering a 6-character remote pairing code
    if (showReceiveDialog) {
        AlertDialog(
            onDismissRequest = {
                showReceiveDialog = false
                viewModel.setReceiveModeActive(false)
            },
            title = {
                Text(
                    "Receive Files",
                    fontFamily = AnthropicSerifDisplay,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column {
                    Text(
                        "Enter the pairing code from sender, or hold phone back-to-back to tap & connect:",
                        fontFamily = AnthropicSerifText,
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
                            unfocusedBorderColor = TapBorder,
                            focusedContainerColor = TapPanelLight,
                            unfocusedContainerColor = TapPanelLight
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Rounded.Nfc, contentDescription = null, tint = TapAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "NFC active — physical tap connects automatically",
                            color = TapAccent,
                            fontSize = 11.sp,
                            fontFamily = AnthropicSerifText
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (receiveCodeInput.isNotBlank()) {
                            showReceiveDialog = false
                            viewModel.setReceiveModeActive(false)
                            val code = receiveCodeInput.trim()
                            viewModel.joinAnywhereShare(code)
                            viewModel.navigateTo(TapScreen.TransferProgress)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TapAccent, contentColor = TapInk),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Connect", fontFamily = AnthropicSerifDisplay, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReceiveDialog = false
                    viewModel.setReceiveModeActive(false)
                }) {
                    Text("Cancel", fontFamily = AnthropicSerifText, color = TapMuted)
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
                // Header: Pure Minimal Title & Action Icons (No unwanted technical labels)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TAP",
                        fontFamily = AnthropicSerifDisplay,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Minimalist TAP Beam Toggle Pill
                        Surface(
                            onClick = {
                                if (isBeamActive) {
                                    viewModel.deactivateBeam()
                                } else {
                                    viewModel.activateBeam(5)
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isBeamActive) TapAccent.copy(alpha = 0.15f) else TapPanel,
                            border = BorderStroke(1.dp, if (isBeamActive) TapAccent else TapBorder),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isBeamActive) TapAccent else TapMuted)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (isBeamActive) "Beam Active" else "Beam Off",
                                    fontFamily = AnthropicSerifText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isBeamActive) TapAccent else TapMuted
                                )
                            }
                        }

                        Spacer(Modifier.width(10.dp))

                        IconButton(
                            onClick = { viewModel.navigateTo(TapScreen.QrScanner) },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(TapPanel)
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan QR", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        IconButton(
                            onClick = { viewModel.navigateTo(TapScreen.Settings) },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(TapPanel)
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Hero Primary Action Cards: Share & Receive
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Share Action Card
                    Surface(
                        color = TapAccent,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(132.dp)
                            .clickable { viewModel.navigateTo(TapScreen.FilePicker) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
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
                                Text(
                                    "Share",
                                    fontFamily = AnthropicSerifDisplay,
                                    color = TapInk,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp
                                )
                                Text("Send anywhere", fontFamily = AnthropicSerifText, color = TapInk.copy(alpha = 0.75f), fontSize = 12.sp)
                            }
                        }
                    }

                    // 2. Receive Action Card
                    Surface(
                        color = TapPanel,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, TapBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(132.dp)
                            .clickable { showReceiveDialog = true }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
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
                                Text(
                                    "Receive",
                                    fontFamily = AnthropicSerifDisplay,
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.5).sp
                                )
                                Text("Enter code or tap", fontFamily = AnthropicSerifText, color = TapMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Active Transfer Banner (Animated when session created or transferring)
                when (val state = anywhereState) {
                    is AnywhereEngineState.WaitingForPeer -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = TapPanel,
                            border = BorderStroke(1.dp, TapAccent.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("READY TO TRANSFER", fontFamily = AnthropicSerifDisplay, color = TapAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
                                    fontFamily = AnthropicSerifDisplay,
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Receiver enters this code or scans QR to connect.",
                                    fontFamily = AnthropicSerifText,
                                    color = TapMuted,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Share Link", state.shareLink))
                                            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TapPanelLight, contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Copy Link", fontFamily = AnthropicSerifText, fontSize = 12.sp)
                                    }

                                    Button(
                                        onClick = { viewModel.navigateTo(TapScreen.TransferProgress) },
                                        colors = ButtonDefaults.buttonColors(containerColor = TapAccent, contentColor = TapInk),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Status", fontFamily = AnthropicSerifDisplay, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }

                    is AnywhereEngineState.Transferring -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = TapPanel,
                            border = BorderStroke(1.dp, TapAccent.copy(alpha = 0.3f)),
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
                                    Text("TRANSFERRING", fontFamily = AnthropicSerifDisplay, color = TapAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    Text(progress.formattedSpeed, fontFamily = AnthropicSerifText, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(progress.currentFileName, fontFamily = AnthropicSerifText, color = Color.White, fontSize = 13.sp, maxLines = 1)
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
                        Spacer(Modifier.height(14.dp))
                    }
                    else -> {}
                }

                // Minimal Quick Tools Grid (Fills empty space with sleek utilities)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickToolTile(
                        icon = Icons.Rounded.QrCodeScanner,
                        title = "Scan QR",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.navigateTo(TapScreen.QrScanner) }
                    )
                    QuickToolTile(
                        icon = Icons.Rounded.Nfc,
                        title = "Tap to Share",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.navigateTo(TapScreen.ProximityShare) }
                    )
                    QuickToolTile(
                        icon = Icons.Rounded.Badge,
                        title = "Identity",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.navigateTo(TapScreen.ContactCard) }
                    )
                }

                Spacer(Modifier.height(20.dp))

                // Subtle Ready Beacon (Minimal motion animation showing proximity readiness)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TapPanel,
                    border = BorderStroke(1.dp, TapBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.navigateTo(TapScreen.ProximityShare) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Minimal pulsing indicator
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.15f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1400, easing = EaseInOutCubic),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseScale"
                        )

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(TapPanelLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(TapAccent)
                            )
                        }

                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Proximity Ready",
                                fontFamily = AnthropicSerifDisplay,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                "Touch phones back-to-back to transfer",
                                fontFamily = AnthropicSerifText,
                                color = TapMuted,
                                fontSize = 12.sp
                            )
                        }

                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TapMuted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Recent Activity Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Activity",
                        fontFamily = AnthropicSerifDisplay,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
                    TextButton(onClick = { viewModel.navigateTo(TapScreen.History) }) {
                        Text("See all", fontFamily = AnthropicSerifText, color = TapAccent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                if (historyRecords.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No transfers yet",
                            fontFamily = AnthropicSerifText,
                            color = TapMuted,
                            fontSize = 13.sp
                        )
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
                                        Text(record.fileName, fontFamily = AnthropicSerifText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                        Text(
                                            "${formatFileSize(record.fileSize)} • ${if (isSender) "Sent to " else "From "}${record.peerName}",
                                            fontFamily = AnthropicSerifText,
                                            color = TapMuted,
                                            fontSize = 11.sp
                                        )
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

@Composable
private fun QuickToolTile(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = TapPanel,
        border = BorderStroke(1.dp, TapBorder),
        modifier = modifier.height(68.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(6.dp)
        ) {
            Icon(icon, contentDescription = title, tint = TapAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                fontFamily = AnthropicSerifText,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
