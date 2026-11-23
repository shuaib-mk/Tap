package dev.tap.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    val caps by viewModel.capabilities.collectAsState()
    val contactCard by viewModel.contactCard.collectAsState()

    // Activity Result Launchers for file picking
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addFiles(uris)
            viewModel.navigateTo(TapScreen.Send)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addFiles(uris)
            viewModel.navigateTo(TapScreen.Send)
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
                            viewModel.navigateTo(TapScreen.Send)
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TAP",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(TapAccent.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("UNIVERSAL", color = TapAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.weight(1f))

                IconButton(onClick = { viewModel.navigateTo(TapScreen.CompatibilityDiagnostics) }) {
                    Icon(Icons.Rounded.Analytics, contentDescription = "Diagnostics", tint = TapMuted)
                }
                IconButton(onClick = { viewModel.navigateTo(TapScreen.History) }) {
                    Icon(Icons.Rounded.History, contentDescription = "History", tint = TapMuted)
                }
                IconButton(onClick = { viewModel.navigateTo(TapScreen.Settings) }) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TapMuted)
                }
            }

            // Universal Status Pills
            val isBgTapSharing by viewModel.isBackgroundTapSharing.collectAsState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatusChip("Tap Sharing: ${if (isBgTapSharing) "Always Ready" else "Off"}", isBgTapSharing)
                StatusChip("NFC: ${if (caps.nfcEnabled) "Ready" else "Off"}", caps.nfcEnabled)
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = { viewModel.navigateTo(TapScreen.NfcReadiness) },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Modes", color = TapAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Pulsing NFC circle
            PulsingNfcCircle(
                active = mode == "Receive",
                onClick = {
                    if (mode == "Receive") {
                        viewModel.navigateTo(TapScreen.Receive)
                    } else if (selectedFiles.isNotEmpty()) {
                        viewModel.navigateTo(TapScreen.NearbyDevices)
                    } else {
                        photoPickerLauncher.launch("image/*")
                    }
                }
            )

            Spacer(Modifier.height(18.dp))

            Text(
                text = if (mode == "Receive") "Bring devices together." else "Ready to share.",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (mode == "Receive") {
                    "Listening for nearby NFC, BLE, QR or Local Web clients"
                } else if (selectedFiles.isNotEmpty()) {
                    "${selectedFiles.size} file(s) selected (${SelectedFile.formatBytes(viewModel.totalSelectedSize)})"
                } else {
                    "Tap to send files, or tap Contact Card below"
                },
                color = TapMuted,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(18.dp))

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

            Spacer(Modifier.height(16.dp))

            // PRIORITY HERO BANNER: Universal Digital Contact Card
            Surface(
                onClick = { viewModel.navigateTo(TapScreen.ContactCard) },
                shape = RoundedCornerShape(18.dp),
                color = TapPanel,
                border = androidx.compose.foundation.BorderStroke(1.2.dp, TapAccent.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(TapAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.ContactPhone, contentDescription = null, tint = TapInk, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Digital Contact Card",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(TapAccent.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("NO APP REQUIRED", color = TapAccent, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Share ${contactCard.displayName.ifBlank { "Contact" }} via NFC Tag, QR or Web",
                            color = TapMuted,
                            fontSize = 12.sp
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TapAccent)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action Tiles for File Picking
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                ActionTile(
                    label = "Scan QR",
                    icon = Icons.Rounded.QrCodeScanner,
                    modifier = Modifier.weight(1f)
                ) {
                    viewModel.navigateTo(TapScreen.QrScanner)
                }
            }

            if (selectedFiles.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                TapButton(
                    text = "Review & Send ${selectedFiles.size} File(s)",
                    onClick = { viewModel.navigateTo(TapScreen.Send) },
                    isPrimary = true
                )
            }

            Spacer(Modifier.height(20.dp))

            // Footer Badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    Icons.Rounded.QrCode2,
                    contentDescription = null,
                    tint = TapMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { viewModel.navigateTo(TapScreen.Receive) }) {
                    Text("Receive Screen", color = TapMuted, fontSize = 13.sp)
                }
                Spacer(Modifier.width(12.dp))
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = TapAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("E2E Encrypted", color = TapMuted, fontSize = 13.sp)
            }
        }
    }
}
