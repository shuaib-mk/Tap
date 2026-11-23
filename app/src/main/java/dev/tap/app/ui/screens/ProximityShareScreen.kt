package dev.tap.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

    val quickShareReport = remember { QuickShareCompatibilityAdapter.checkCompatibility(context) }

    // Pickers
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.stageProximityUris(uris)
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.stageProximityUris(uris)
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
                        "Samsung Quick Share & TAP Proximity",
                        color = TapAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.weight(1f))
                if (stagedFiles.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearProximityFiles() }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear", tint = Color(0xFFFF6B6B))
                    }
                }
            }

            // Quick Share Status Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TapPanel)
                    .border(1.dp, if (quickShareReport.canDirectLaunch) TapAccent else TapBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (quickShareReport.canDirectLaunch) TapAccent else TapBorder),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = null,
                            tint = if (quickShareReport.canDirectLaunch) TapInk else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Quick Share Compatibility",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            if (quickShareReport.canDirectLaunch) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(TapAccent.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("ONE UI READY", color = TapAccent, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            quickShareReport.description,
                            color = TapMuted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

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

            Spacer(Modifier.height(14.dp))

            // Staged Files Summary
            if (stagedFiles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "STAGED FILES (${stagedFiles.size})",
                        color = TapMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        ProximityFile.formatBytes(stagedFiles.sumOf { it.sizeBytes }),
                        color = TapAccent,
                        fontSize = 12.sp,
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
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No files staged for proximity transfer", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Select photos, videos, audio, PDFs, or archives above", color = TapMuted, fontSize = 13.sp)
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

            Spacer(Modifier.height(12.dp))

            // Action Buttons
            if (stagedFiles.isNotEmpty()) {
                // Primary Action: Samsung Quick Share Tap to Share Handoff
                Button(
                    onClick = { viewModel.launchQuickShareHandoff() },
                    colors = ButtonDefaults.buttonColors(containerColor = TapAccent, contentColor = TapInk),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (quickShareReport.canDirectLaunch) "Share via Samsung Quick Share" else "Share via System Quick Share",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Secondary Action: TAP Proximity E2E Encrypted Transfer
                OutlinedButton(
                    onClick = { viewModel.navigateTo(dev.tap.app.navigation.TapScreen.NearbyDevices) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.Sensors, contentDescription = null, tint = TapAccent)
                    Spacer(Modifier.width(8.dp))
                    Text("TAP Proximity Transfer (E2E Encrypted)", fontSize = 14.sp)
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ProximityFileCard(file: ProximityFile, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = TapPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TapBorder),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (file.category) {
                    FileCategory.PHOTO -> Icons.Rounded.Image
                    FileCategory.VIDEO -> Icons.Rounded.VideoFile
                    FileCategory.AUDIO -> Icons.Rounded.AudioFile
                    FileCategory.DOCUMENT -> Icons.Rounded.Description
                    FileCategory.ARCHIVE -> Icons.Rounded.Archive
                    FileCategory.ARBITRARY -> Icons.Rounded.InsertDriveFile
                }
                Icon(icon, contentDescription = null, tint = TapAccent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Text(
                    "${ProximityFile.formatBytes(file.sizeBytes)} • ${file.category.name}",
                    color = TapMuted,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = TapMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}
