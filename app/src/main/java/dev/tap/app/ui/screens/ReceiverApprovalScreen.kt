package dev.tap.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

@Composable
fun ReceiverApprovalScreen(viewModel: TransferViewModel) {
    val anywhereState by viewModel.anywhereState.collectAsState()

    val request = (anywhereState as? AnywhereEngineState.IncomingTransferRequest)

    Surface(modifier = Modifier.fillMaxSize(), color = TapInk) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column {
                TapTopBar(
                    title = "Incoming Transfer",
                    subtitle = "Verify sender identity before downloading",
                    onBack = {
                        viewModel.respondToTransferConsent(false)
                        viewModel.navigateBack()
                    }
                )

                Spacer(Modifier.height(16.dp))

                if (request != null) {
                    // Sender Identity Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TapPanel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(TapPanelLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = TapAccent, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(request.senderDeviceName, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    Text("${request.files.size} files • ${ProximityFile.formatBytes(request.totalBytes)}", color = TapMuted, fontSize = 12.sp)
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // SAS Verification Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TapAccent.copy(alpha = 0.12f))
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("SECURITY VERIFICATION CODE", color = TapAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                    Spacer(Modifier.height(2.dp))
                                    Text(request.sasCode, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                                    Spacer(Modifier.height(2.dp))
                                    Text("Check that sender shows this identical code", color = TapMuted, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text("Files to receive (${request.files.size}):", color = TapMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(request.files) { file ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = TapPanel,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.InsertDriveFile, contentDescription = null, tint = TapAccent, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                        Text(ProximityFile.formatBytes(file.sizeBytes), color = TapMuted, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text("No active incoming transfer", color = TapMuted)
                    }
                }
            }

            // Bottom Actions: Decline vs Accept
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.respondToTransferConsent(false)
                        viewModel.navigateTo(TapScreen.Home)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TapRed),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("Decline", fontSize = 14.sp)
                }

                Button(
                    onClick = {
                        viewModel.respondToTransferConsent(true)
                        viewModel.navigateTo(TapScreen.TransferProgress)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TapAccent, contentColor = TapInk),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.5f).height(50.dp)
                ) {
                    Text("Accept & Download", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
