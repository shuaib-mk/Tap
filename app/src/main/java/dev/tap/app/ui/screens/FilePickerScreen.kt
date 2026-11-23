package dev.tap.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.navigation.TapScreen
import dev.tap.app.proximity.FileCategory
import dev.tap.app.proximity.ProximityFile
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun FilePickerScreen(viewModel: TransferViewModel) {
    val stagedFiles by viewModel.stagedAnywhereFiles.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    // Pickers
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.stageAnywhereUris(uris)
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.stageAnywhereUris(uris)
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.stageAnywhereUris(uris)
    }

    val filteredFiles = remember(stagedFiles, searchQuery) {
        if (searchQuery.isBlank()) stagedFiles
        else stagedFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
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
            TapTopBar(
                title = "Select Files",
                subtitle = if (stagedFiles.isNotEmpty()) "${stagedFiles.size} items • ${ProximityFile.formatBytes(stagedFiles.sumOf { it.sizeBytes })}" else "Choose content to transfer",
                onBack = { viewModel.navigateBack() },
                actions = {
                    if (stagedFiles.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearStagedAnywhereFiles() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear", tint = TapRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            )

            // Category Selection Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChip("Media", Icons.Rounded.Image, modifier = Modifier.weight(1f)) {
                    mediaPicker.launch("image/*")
                }
                CategoryChip("Audio", Icons.Rounded.AudioFile, modifier = Modifier.weight(1f)) {
                    audioPicker.launch("audio/*")
                }
                CategoryChip("Docs", Icons.Rounded.Description, modifier = Modifier.weight(1f)) {
                    docPicker.launch(arrayOf("application/pdf", "application/msword", "text/*"))
                }
                CategoryChip("All Files", Icons.Rounded.FolderOpen, modifier = Modifier.weight(1f)) {
                    docPicker.launch(arrayOf("*/*"))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Search Bar (if files are staged)
            if (stagedFiles.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter staged files...", color = TapMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = TapMuted, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = TapMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TapBorder,
                        unfocusedBorderColor = TapPanel,
                        focusedContainerColor = TapPanel,
                        unfocusedContainerColor = TapPanel
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
                Spacer(Modifier.height(10.dp))
            }

            // Staged Files List or Empty State
            if (stagedFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(TapPanel),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = TapMuted, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("No files selected yet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap a category above to pick photos, videos, or documents", color = TapMuted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredFiles, key = { it.uri.toString() }) { file ->
                        FileItemRow(file = file, onRemove = { viewModel.removeStagedAnywhereFile(file) })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action Buttons
            if (stagedFiles.isNotEmpty()) {
                // Primary Action: Transfer Anywhere
                Button(
                    onClick = {
                        viewModel.startAnywhereShare()
                        viewModel.navigateTo(TapScreen.TransferProgress)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TapAccent, contentColor = TapInk),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Transfer Anywhere (${stagedFiles.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(Modifier.height(8.dp))

                // Secondary Options Row: Physical Tap & Quick Share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.activatePhysicalTapToShare()
                            viewModel.navigateTo(TapScreen.ProximityShare)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Rounded.Nfc, contentDescription = null, tint = TapAccent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Physical Tap", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { viewModel.launchQuickShareHandoff() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Quick Share", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = TapPanel,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .height(44.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = TapAccent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FileItemRow(file: ProximityFile, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = TapPanel,
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
                    .background(TapPanelLight),
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
                Icon(icon, contentDescription = null, tint = TapAccent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text("${ProximityFile.formatBytes(file.sizeBytes)} • ${file.category.name}", color = TapMuted, fontSize = 11.sp)
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = TapMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}
