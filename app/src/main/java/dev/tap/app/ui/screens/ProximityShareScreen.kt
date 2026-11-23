package dev.tap.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.proximity.FileCategory
import dev.tap.app.proximity.ProximityEngineState
import dev.tap.app.proximity.ProximityFile
import dev.tap.app.proximity.QuickShareCompatibilityAdapter
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun ProximityShareScreen(viewModel: TransferViewModel) {
    val context = LocalContext.current
    val stagedFiles by viewModel.stagedProximityFiles.collectAsState()
    val engineState by viewModel.proximityEngineState.collectAsState()
    val progress by viewModel.proximityProgress.collectAsState()
    val isReceiverRunning by viewModel.isProximityReceiverRunning.collectAsState()
    val pendingApproval by viewModel.pendingProximityApproval.collectAsState()
    val discoveredP2pPeers by viewModel.discoveredP2pPeers.collectAsState()
    val isPhysicalTapToShareActive by viewModel.isPhysicalTapToShareActive.collectAsState()
    val currentSasCode by viewModel.currentSasCode.collectAsState()
    val webFallbackUrl by viewModel.proximityWebFallbackUrl.collectAsState()

    var activeTab by remember { mutableStateOf("Send") } // "Send" or "Receive"
    var showDirectIpDialog by remember { mutableStateOf(false) }
    var targetIpInput by remember { mutableStateOf("192.168.") }

    val quickShareReport = remember { QuickShareCompatibilityAdapter.checkCompatibility(context) }

    // Pickers
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.stageProximityUris(uris)
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.stageProximityUris(uris)
    }

    // Direct IP Connect Dialog
    if (showDirectIpDialog) {
        AlertDialog(
            onDismissRequest = { showDirectIpDialog = false },
            title = { Text("Enter Receiver IP Address", color = Color.White) },
            text = {
                Column {
                    Text(
                        "Enter the local IP address or Wi-Fi Direct address of the receiver device:",
                        color = TapMuted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = targetIpInput,
                        onValueChange = { targetIpInput = it },
                        placeholder = { Text("e.g. 192.168.1.15", color = TapMuted) },
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
                        if (targetIpInput.isNotBlank()) {
                            showDirectIpDialog = false
                            viewModel.sendProximityToHost(targetIpInput.trim())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TapAccent, contentColor = TapInk)
                ) { Text("Connect & Send") }
            },
            dismissButton = {
                TextButton(onClick = { showDirectIpDialog = false }) { Text("Cancel", color = TapMuted) }
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
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.navigateBack() }) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        "Proximity File Sharing",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Physical Tap to Share • Quick Share Handoff",
                        color = TapAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.weight(1f))
                if (activeTab == "Send" && stagedFiles.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearProximityFiles() }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear", tint = Color(0xFFFF6B6B))
                    }
                }
            }

            // Mode Selector Tabs: Send Files vs Receive Mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TapPanel)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Send", "Receive").forEach { tab ->
                    val selected = activeTab == tab
                    Button(
                        onClick = { activeTab = tab },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) TapAccent else TapPanel,
                            contentColor = if (selected) TapInk else TapMuted
                        )
                    ) {
                        Text(
                            if (tab == "Send") "Send Files" else "Receive Mode",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // =====================================================================================
            // INCOMING APPROVAL PROMPT (FLOATING FOR BOTH TABS)
            // =====================================================================================
            pendingApproval?.let { approval ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF131924),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, TapAccent),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Security, contentDescription = null, tint = TapAccent)
                            Spacer(Modifier.width(8.dp))
                            Text("Incoming Proximity Transfer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("From: ${approval.offer.senderDeviceName}", color = Color.White, fontSize = 14.sp)
                        Text("Files: ${approval.offer.files.size} (${ProximityFile.formatBytes(approval.offer.files.sumOf { it.sizeBytes })})", color = TapMuted, fontSize = 13.sp)

                        Spacer(Modifier.height(12.dp))

                        // Prominent SAS Verification Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(TapAccent.copy(alpha = 0.15f))
                                .border(1.dp, TapAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("VERIFICATION CODE", color = TapAccent, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(2.dp))
                                Text(approval.sasCode, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                                Spacer(Modifier.height(2.dp))
                                Text("Confirm this identical code appears on sender device", color = TapMuted, fontSize = 10.sp)
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.respondToProximityApproval(false) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B))
                            ) { Text("Decline") }

                            Button(
                                onClick = { viewModel.respondToProximityApproval(true) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TapAccent, contentColor = TapInk)
                            ) { Text("Accept", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            // =====================================================================================
            // LIVE TRANSFER PROGRESS (VISIBLE FOR BOTH TABS)
            // =====================================================================================
            if (progress.totalBytes > 0L) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = TapPanel,
                    border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (progress.isComplete) "Transfer Complete" else "Transferring: ${progress.currentFileName}",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(progress.formattedSpeed, color = TapAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress.percent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = TapAccent,
                            trackColor = TapBorder
                        )
                        Spacer(Modifier.height(6.dp))
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
                            if (progress.sasVerificationCode.isNotBlank()) {
                                Text(
                                    "SAS: ${progress.sasVerificationCode}",
                                    color = TapAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (progress.isComplete) {
                                Text("SHA-256 Verified ✓", color = TapAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // =====================================================================================
            // TAB 1: SEND FILES MODE
            // =====================================================================================
            if (activeTab == "Send") {
                // Quick Share Status Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TapPanel)
                        .border(1.dp, if (quickShareReport.canDirectLaunch) TapAccent else TapBorder, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (quickShareReport.canDirectLaunch) TapAccent else TapBorder),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = null,
                                tint = if (quickShareReport.canDirectLaunch) TapInk else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Quick Share Compatibility",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(Modifier.width(6.dp))
                                if (quickShareReport.canDirectLaunch) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(TapAccent.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("ONE UI READY", color = TapAccent, fontSize = 8.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                quickShareReport.description,
                                color = TapMuted,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Selection Category Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { photoPicker.launch("image/*") },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp), tint = TapAccent)
                        Spacer(Modifier.width(6.dp))
                        Text("Media", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { docPicker.launch(arrayOf("application/pdf", "application/msword", "text/*", "application/zip", "*/*")) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = TapAccent)
                        Spacer(Modifier.width(6.dp))
                        Text("Files", fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Staged Files Header
                if (stagedFiles.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "STAGED FILES (${stagedFiles.size})",
                            color = TapMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            ProximityFile.formatBytes(stagedFiles.sumOf { it.sizeBytes }),
                            color = TapAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // File List or Empty State
                if (stagedFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.ShareLocation,
                                contentDescription = null,
                                tint = TapMuted,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("No files staged for proximity transfer", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Pick photos, videos, or documents to share", color = TapMuted, fontSize = 12.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(stagedFiles, key = { it.uri.toString() }) { file ->
                            ProximityFileCard(file = file, onRemove = { viewModel.removeProximityFile(file) })
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Action Buttons / Physical Tap to Share Section
                if (stagedFiles.isNotEmpty()) {
                    if (isPhysicalTapToShareActive) {
                        // ACTIVE PULSING RADAR CARD
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val pulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(900, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "alpha"
                        )

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF0F1A28),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, TapAccent),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(TapAccent.copy(alpha = pulseAlpha * 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.Sensors,
                                            contentDescription = null,
                                            tint = TapAccent,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Ready to Tap!",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            "Hold backs of phones together to transfer",
                                            color = TapAccent,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))

                                // SAS Verification Display (if connection established)
                                val sas = currentSasCode ?: progress.sasVerificationCode
                                if (sas.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(TapAccent.copy(alpha = 0.15f))
                                            .border(1.dp, TapAccent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("SAS VERIFICATION CODE", color = TapAccent, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                            Text(sas, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                                            Text("Confirm matching code on receiver screen", color = TapMuted, fontSize = 9.sp)
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }

                                // Web Fallback indicator
                                webFallbackUrl?.let {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(TapPanel)
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Language, contentDescription = null, tint = TapMuted, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Zero-install web fallback active for non-TAP devices",
                                            color = TapMuted,
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }

                                Button(
                                    onClick = { viewModel.deactivatePhysicalTapToShare() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1C24), contentColor = Color(0xFFFF6B6B)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(38.dp)
                                ) {
                                    Text("Cancel Tap to Share", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // PRIMARY FEATURE: ACTIVATE PHYSICAL TAP TO SHARE
                        Button(
                            onClick = { viewModel.activatePhysicalTapToShare() },
                            colors = ButtonDefaults.buttonColors(containerColor = TapAccent, contentColor = TapInk),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text("Physical Tap to Share", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Touch phones back-to-back to transfer", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Secondary Option: Samsung Quick Share
                    OutlinedButton(
                        onClick = { viewModel.launchQuickShareHandoff() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = TapAccent)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (quickShareReport.canDirectLaunch) "Share via Samsung Quick Share" else "Share via System Sharesheet",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Tertiary Option: TAP Direct IP Connect
                    OutlinedButton(
                        onClick = { showDirectIpDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(Icons.Rounded.Sensors, contentDescription = null, tint = TapMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("TAP Proximity Direct IP Connect", fontSize = 12.sp, color = TapMuted)
                    }
                }
            }

            // =====================================================================================
            // TAB 2: RECEIVE MODE
            // =====================================================================================
            if (activeTab == "Receive") {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(10.dp))

                    // Receiver Control Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = TapPanel,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isReceiverRunning) TapAccent else TapBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(if (isReceiverRunning) TapAccent.copy(alpha = 0.2f) else TapBorder),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isReceiverRunning) Icons.Rounded.WifiTethering else Icons.Rounded.WifiTetheringOff,
                                    contentDescription = null,
                                    tint = if (isReceiverRunning) TapAccent else TapMuted,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (isReceiverRunning) "Proximity Receiver Active" else "Proximity Receiver Inactive",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (isReceiverRunning) "Awaiting connection on port 8990 (NFC Reader & Wi-Fi Direct ready)" else "Tap below to start listening for incoming proximity transfers",
                                color = TapMuted,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    if (isReceiverRunning) viewModel.stopProximityReceiver() else viewModel.startProximityReceiver()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isReceiverRunning) Color(0xFFFF6B6B) else TapAccent,
                                    contentColor = if (isReceiverRunning) Color.White else TapInk
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (isReceiverRunning) "Stop Receiver" else "Start Proximity Receiver", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProximityFileCard(file: ProximityFile, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = TapPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TapBorder),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (file.category) {
                    FileCategory.PHOTO -> Icons.Rounded.Image
                    FileCategory.VIDEO -> Icons.Rounded.VideoFile
                    FileCategory.AUDIO -> Icons.Rounded.AudioFile
                    FileCategory.DOCUMENT -> Icons.Rounded.Description
                    FileCategory.ARCHIVE -> Icons.Rounded.Archive
                    FileCategory.ARBITRARY -> Icons.Rounded.AttachFile
                }
                Icon(icon, contentDescription = null, tint = TapAccent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1
                )
                Text(
                    "${ProximityFile.formatBytes(file.sizeBytes)} • ${file.category.name}",
                    color = TapMuted,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = TapMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}
