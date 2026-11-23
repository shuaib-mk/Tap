package dev.tap.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import dev.tap.app.navigation.TapScreen
import dev.tap.app.proximity.ProximityFile
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.TransferViewModel
import dev.tap.app.webrtc.AnywhereEngineState
import dev.tap.app.webrtc.WebRtcTransportType

@Composable
fun TransferProgressScreen(viewModel: TransferViewModel) {
    val anywhereState by viewModel.anywhereState.collectAsState()
    val progress by viewModel.anywhereProgress.collectAsState()
    val isPaused = viewModel.anywhereEngine.chunkTransport.isPaused

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
                    onBack = { viewModel.navigateTo(TapScreen.Home) }
                )

                Spacer(Modifier.height(16.dp))

                // Peer and Connection Card
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
                            is AnywhereEngineState.WaitingForPeer -> "Waiting for peer (Code: ${s.sessionCode})"
                            is AnywhereEngineState.Completed -> "Transfer Finished"
                            else -> "Transferring"
                        }

                        Text(peerTitle, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (progress.totalFiles > 0) "${progress.currentFileIndex} of ${progress.totalFiles} files" else "Establishing secure link...",
                            color = TapMuted,
                            fontSize = 12.sp
                        )

                        // SAS Security Verification Box (when code is derived)
                        val sas = progress.sasVerificationCode
                        if (sas.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TapAccent.copy(alpha = 0.12f))
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("VERIFICATION CODE", color = TapAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (progress.isComplete) "Completed" else progress.currentFileName.ifBlank { "Preparing files..." },
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (progress.isComplete) "100%" else progress.formattedSpeed,
                                color = TapAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { if (progress.isComplete) 1f else progress.percent },
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

            // Bottom Action Controls
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                if (progress.isComplete) {
                    Button(
                        onClick = { viewModel.navigateTo(TapScreen.Home) },
                        colors = ButtonDefaults.buttonColors(containerColor = TapAccent, contentColor = TapInk),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
