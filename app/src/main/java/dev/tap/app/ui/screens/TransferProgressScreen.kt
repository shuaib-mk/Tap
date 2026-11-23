package dev.tap.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.discovery.qr.QrCodeGenerator
import dev.tap.app.navigation.TapScreen
import dev.tap.app.proximity.ProximityFile
import dev.tap.app.ui.components.*
import dev.tap.app.ui.theme.AnthropicSerifDisplay
import dev.tap.app.ui.theme.AnthropicSerifText
import dev.tap.app.ui.viewmodels.TransferViewModel
import dev.tap.app.webrtc.AnywhereEngineState
import dev.tap.app.webrtc.WebRtcTransportType

@Composable
fun TransferProgressScreen(viewModel: TransferViewModel) {
    val context = LocalContext.current
    val anywhereState by viewModel.anywhereState.collectAsState()
    val progress by viewModel.anywhereProgress.collectAsState()
    val isPaused = viewModel.anywhereEngine.chunkTransport.isPaused

    // Sleek continuous interpolated progress animation
    val animatedPercent by animateFloatAsState(
        targetValue = if (progress.isComplete) 1f else progress.percent,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "transferAnimatedPercent"
    )

    // Minimal subtle pulse dot for active transfer
    val infiniteTransition = rememberInfiniteTransition(label = "transferPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "transferPulseAlpha"
    )

    Surface(modifier = Modifier.fillMaxSize(), color = TapInk) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Column {
                TapTopBar(
                    title = "Transfer",
                    subtitle = "Encrypted peer-to-peer data channel",
                    onBack = {
                        viewModel.cancelAnywhereTransfer()
                        viewModel.navigateTo(TapScreen.Home)
                    }
                )

                Spacer(Modifier.height(12.dp))

                // 1. SENDER WAITING FOR RECIPIENT STATE
                if (anywhereState is AnywhereEngineState.WaitingForPeer) {
                    val waiting = anywhereState as AnywhereEngineState.WaitingForPeer
                    val qrBitmap = remember(waiting.sessionCode) {
                        QrCodeGenerator.generateQrBitmap("https://tap.dev/s/${waiting.sessionCode}", 500, 500)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = TapPanel,
                            border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "PAIRING CODE",
                                    color = TapAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp,
                                    fontFamily = AnthropicSerifDisplay
                                )
                                Spacer(Modifier.height(6.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(TapPanelLight)
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("TAP Code", waiting.sessionCode))
                                            Toast.makeText(context, "Code copied: ${waiting.sessionCode}", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = waiting.sessionCode,
                                        fontFamily = AnthropicSerifDisplay,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        letterSpacing = 2.sp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = TapAccent, modifier = Modifier.size(20.dp))
                                }

                                Spacer(Modifier.height(16.dp))

                                // QR Code Box
                                Box(
                                    modifier = Modifier
                                        .size(210.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (qrBitmap != null) {
                                        Image(
                                            bitmap = qrBitmap.asImageBitmap(),
                                            contentDescription = "Session QR Code",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        CircularProgressIndicator(color = TapInk)
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                Text(
                                    "Enter this code on the other phone, scan the QR code, or bring phones together to tap & connect via NFC.",
                                    color = TapMuted,
                                    fontSize = 12.sp,
                                    fontFamily = AnthropicSerifText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )

                                Spacer(Modifier.height(14.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(TapInk)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(TapAccent)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Listening for receiver...",
                                        color = TapAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = AnthropicSerifText
                                    )
                                }
                            }
                        }
                    }
                } else if (anywhereState is AnywhereEngineState.PeerConnected) {
                    // 2. PEER CONNECTED — VERIFICATION STATE
                    val peer = anywhereState as AnywhereEngineState.PeerConnected
                    val files by viewModel.stagedAnywhereFiles.collectAsState()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = TapPanel,
                            border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = peer.peerDeviceName,
                                    fontFamily = AnthropicSerifDisplay,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${files.size} ${if (files.size == 1) "item" else "items"} • ${ProximityFile.formatBytes(files.sumOf { it.sizeBytes })}",
                                    color = TapMuted,
                                    fontSize = 12.sp,
                                    fontFamily = AnthropicSerifText
                                )

                                Spacer(Modifier.height(24.dp))

                                // Minimalist Security Verification Code Box
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(TapPanelLight)
                                        .border(1.dp, TapBorder, RoundedCornerShape(14.dp))
                                        .padding(vertical = 18.dp, horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "SECURITY CODE",
                                            color = TapMuted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp,
                                            fontFamily = AnthropicSerifDisplay
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = peer.sasCode,
                                            color = Color.White,
                                            fontSize = 34.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 4.sp,
                                            fontFamily = AnthropicSerifDisplay
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            "Confirm this code matches recipient's screen",
                                            color = TapMuted,
                                            fontSize = 11.sp,
                                            fontFamily = AnthropicSerifText
                                        )
                                    }
                                }

                                Spacer(Modifier.height(22.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = TapAccent,
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Recipient is verifying files and accepting download...",
                                        color = TapMuted,
                                        fontSize = 12.sp,
                                        fontFamily = AnthropicSerifText
                                    )
                                }
                            }
                        }
                    }
                } else if (anywhereState is AnywhereEngineState.Error) {
                    // 3. ERROR STATE
                    val err = (anywhereState as AnywhereEngineState.Error).message
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF2A1519),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TapRed.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = TapRed, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Connection Failed", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = AnthropicSerifDisplay)
                            Spacer(Modifier.height(6.dp))
                            Text(err, color = TapMuted, fontSize = 13.sp, textAlign = TextAlign.Center, fontFamily = AnthropicSerifText)
                        }
                    }
                } else {
                    // 4. CONNECTING OR ACTIVE TRANSFER STATE
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TapPanel,
                        border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("PEER CONNECTION", color = TapMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                val transportLabel = when (viewModel.anywhereEngine.peerConnectionManager.transportType) {
                                    WebRtcTransportType.DIRECT_P2P -> "Direct P2P"
                                    WebRtcTransportType.TURN_RELAY -> "TURN Relay"
                                    WebRtcTransportType.OFFLINE_DIRECT -> "Wi-Fi Direct"
                                    WebRtcTransportType.UNKNOWN -> "Connecting..."
                                }
                                StatusChip(
                                    label = transportLabel,
                                    active = true,
                                    accentColor = if (viewModel.anywhereEngine.peerConnectionManager.transportType == WebRtcTransportType.TURN_RELAY) Color(0xFFFBBF24) else TapAccent
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            val peerTitle = when (val s = anywhereState) {
                                is AnywhereEngineState.Connecting -> s.peerName
                                is AnywhereEngineState.IncomingTransferRequest -> s.senderDeviceName
                                is AnywhereEngineState.Completed -> "Transfer Finished"
                                is AnywhereEngineState.CreatingSession -> s.status
                                else -> "Transferring"
                            }

                            Text(peerTitle, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = AnthropicSerifDisplay)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (progress.totalFiles > 0) "${progress.currentFileIndex} of ${progress.totalFiles} files" else "Establishing secure link...",
                                color = TapMuted,
                                fontSize = 12.sp,
                                fontFamily = AnthropicSerifText
                            )

                            // SAS Security Verification Box
                            val sas = progress.sasVerificationCode
                            if (sas.isNotBlank()) {
                                Spacer(Modifier.height(14.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(TapPanelLight)
                                        .border(1.dp, TapBorder, RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("SECURITY CODE", color = TapMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                        Spacer(Modifier.height(2.dp))
                                        Text(sas, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                                        Spacer(Modifier.height(2.dp))
                                        Text("Confirm matching code on peer screen", color = TapMuted, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Live Transfer Progress Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TapPanel,
                        border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    if (!progress.isComplete && progress.bytesTransferred > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(TapAccent.copy(alpha = pulseAlpha))
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(
                                        if (progress.isComplete) "Completed" else progress.currentFileName.ifBlank { "Preparing files..." },
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = AnthropicSerifText,
                                        maxLines = 1
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (progress.isComplete) "100%" else progress.formattedSpeed,
                                    color = TapAccent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = AnthropicSerifDisplay,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { animatedPercent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (progress.isComplete) TapGreen else TapAccent,
                                trackColor = TapPanelLight
                            )

                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${ProximityFile.formatBytes(progress.bytesTransferred)} / ${ProximityFile.formatBytes(progress.totalBytes)}",
                                    color = TapMuted,
                                    fontSize = 11.sp
                                )
                                if (progress.isComplete) {
                                    Text("SHA-256 Verified ✓", color = TapGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                } else if (progress.speedBytesPerSec > 0 && progress.totalBytes > progress.bytesTransferred) {
                                    val remainingBytes = progress.totalBytes - progress.bytesTransferred
                                    val remainingSec = remainingBytes / progress.speedBytesPerSec
                                    Text("~${remainingSec}s remaining", color = TapMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Action Controls
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                if (progress.isComplete || anywhereState is AnywhereEngineState.Completed) {
                    Button(
                        onClick = {
                            viewModel.cancelAnywhereTransfer()
                            viewModel.navigateTo(TapScreen.Home)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TapAccent, contentColor = TapInk),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else if (anywhereState is AnywhereEngineState.Error) {
                    Button(
                        onClick = {
                            viewModel.cancelAnywhereTransfer()
                            viewModel.navigateTo(TapScreen.Home)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TapPanelLight, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Return Home", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else if (anywhereState is AnywhereEngineState.WaitingForPeer || anywhereState is AnywhereEngineState.PeerConnected) {
                    Button(
                        onClick = {
                            viewModel.cancelAnywhereTransfer()
                            viewModel.navigateTo(TapScreen.Home)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TapPanel, contentColor = TapMuted),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Cancel Transfer", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (isPaused) viewModel.resumeTransfer() else viewModel.pauseTransfer()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(
                                if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isPaused) "Resume" else "Pause", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.cancelAnywhereTransfer()
                                viewModel.navigateTo(TapScreen.Home)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1519), contentColor = TapRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
