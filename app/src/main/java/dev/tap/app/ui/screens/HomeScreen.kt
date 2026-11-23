package dev.tap.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import dev.tap.app.navigation.TapScreen
import dev.tap.app.transfer.SelectedFile
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun HomeScreen(viewModel: TransferViewModel) {
    val context = LocalContext.current
    val mode by viewModel.mode.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val sessionOffer by viewModel.sessionOffer.collectAsState()

    // Activity Result Launchers for file picking
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addFiles(uris)
            viewModel.navigateTo(TapScreen.FilePicker)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addFiles(uris)
            viewModel.navigateTo(TapScreen.FilePicker)
        }
    }

    var showLinkDialog by remember { mutableStateOf(false) }
    var linkUrl by remember { mutableStateOf("") }

    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Share Link", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = linkUrl,
                    onValueChange = { linkUrl = it },
                    placeholder = { Text("https://example.com", color = TapMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TapAccent,
                        unfocusedBorderColor = TapBorder
                    ),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (linkUrl.isNotBlank()) {
                            viewModel.addLink(linkUrl)
                            showLinkDialog = false
                            linkUrl = ""
                            viewModel.navigateTo(TapScreen.FilePicker)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TapAccent, contentColor = TapInk)
                ) { Text("Add Link") }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) { Text("Cancel", color = TapMuted) }
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TAP",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.width(12.dp))
                StatusChip("NFC", viewModel.nfcCapabilities.enabled)
                Spacer(Modifier.width(6.dp))
                StatusChip("BLE", viewModel.isBleAvailable)

                Spacer(Modifier.weight(1f))

                IconButton(onClick = { viewModel.navigateTo(TapScreen.History) }) {
                    Icon(Icons.Rounded.History, contentDescription = "History", tint = TapMuted)
                }
                IconButton(onClick = { viewModel.navigateTo(TapScreen.Settings) }) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TapMuted)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Pulsing NFC circle
            PulsingNfcCircle(
                active = mode == "Receive",
                onClick = {
                    if (mode == "Receive") {
                        viewModel.navigateTo(TapScreen.QrConnect)
                    } else if (selectedFiles.isNotEmpty()) {
                        viewModel.navigateTo(TapScreen.Discovery)
                    } else {
                        photoPickerLauncher.launch("image/*")
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = if (mode == "Receive") "Bring devices together." else "Ready to share.",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (mode == "Receive") {
                    "Listening for nearby NFC, BLE, or QR connections"
                } else if (selectedFiles.isNotEmpty()) {
                    "${selectedFiles.size} file(s) selected (${SelectedFile.formatBytes(viewModel.totalSelectedSize)})"
                } else {
                    "Select photos, files, links or contacts"
                },
                color = TapMuted,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(24.dp))

            // Send / Receive Toggle Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(TapPanel)
                    .padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Send", "Receive").forEach { item ->
                    val active = mode == item
                    Button(
                        onClick = { viewModel.setMode(item) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (active) TapAccent else TapPanel,
                            contentColor = if (active) TapInk else TapMuted
                        )
                    ) {
                        Text(item, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Action Tiles for File Picking
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ActionTile(
                    label = "Photos",
                    icon = Icons.Rounded.Image,
                    modifier = Modifier.weight(1f)
                ) {
                    photoPickerLauncher.launch("image/*")
                }
                ActionTile(
                    label = "Files",
                    icon = Icons.Rounded.FolderOpen,
                    modifier = Modifier.weight(1f)
                ) {
                    filePickerLauncher.launch(arrayOf("*/*"))
                }
                ActionTile(
                    label = "Links",
                    icon = Icons.Rounded.Link,
                    modifier = Modifier.weight(1f)
                ) {
                    showLinkDialog = true
                }
            }

            if (selectedFiles.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                TapButton(
                    text = "View ${selectedFiles.size} Selected Files",
                    onClick = { viewModel.navigateTo(TapScreen.FilePicker) },
                    isPrimary = true
                )
            }

            Spacer(Modifier.height(28.dp))

            // Footer Badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 16.dp)
            ) {
                Icon(
                    Icons.Rounded.QrCode2,
                    contentDescription = null,
                    tint = TapMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { viewModel.navigateTo(TapScreen.QrConnect) }) {
                    Text("QR Code Connect", color = TapMuted, fontSize = 13.sp)
                }
                Spacer(Modifier.width(16.dp))
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = TapAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("End-to-End Encrypted", color = TapMuted, fontSize = 13.sp)
            }
        }
    }
}
