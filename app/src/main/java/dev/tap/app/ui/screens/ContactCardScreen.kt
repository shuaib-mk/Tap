package dev.tap.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.tap.app.contacts.ContactCard
import dev.tap.app.discovery.qr.QrCodeGenerator
import dev.tap.app.navigation.TapScreen
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun ContactCardScreen(viewModel: TransferViewModel) {
    val card by viewModel.contactCard.collectAsState()
    val isHttpRunning by viewModel.isHttpServerRunning.collectAsState()
    val httpUrl by viewModel.httpServerUrl.collectAsState()

    var showEditDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    if (showQrDialog) {
        Dialog(onDismissRequest = { showQrDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = TapPanel,
                border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Universal Contact QR",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Scan with ANY iPhone or Android Camera app to add contact instantly (No app required).",
                        color = TapMuted,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))

                    qrBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Contact vCard QR",
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(12.dp)
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    TapButton(
                        text = "Close",
                        onClick = { showQrDialog = false },
                        isPrimary = false
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        EditContactDialog(
            initialCard = card,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                viewModel.updateContactCard(updated)
                showEditDialog = false
            }
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
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.navigateBack() }) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Digital Contact Card",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit Card", tint = TapAccent)
                }
            }

            // Hero Contact Card Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(TapPanel)
                    .border(1.dp, TapBorder, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(TapAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = card.displayName.take(2).uppercase().ifBlank { "TA" },
                                color = TapInk,
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = card.displayName.ifBlank { "Tap User" },
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (card.jobTitle.isNotBlank() || card.company.isNotBlank()) {
                                Text(
                                    text = listOf(card.jobTitle, card.company).filter { it.isNotBlank() }.joinToString(" • "),
                                    color = TapMuted,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = TapBorder)
                    Spacer(Modifier.height(16.dp))

                    ContactDetailRow(Icons.Rounded.Phone, "Phone", card.phone, card.isFieldSelected(ContactCard.FIELD_PHONE))
                    ContactDetailRow(Icons.Rounded.Email, "Email", card.email, card.isFieldSelected(ContactCard.FIELD_EMAIL))
                    ContactDetailRow(Icons.Rounded.Language, "Website", card.website, card.isFieldSelected(ContactCard.FIELD_WEBSITE))
                    if (card.notes.isNotBlank()) {
                        ContactDetailRow(Icons.Rounded.Notes, "Notes", card.notes, card.isFieldSelected(ContactCard.FIELD_NOTES))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Privacy field selectors
            Text(
                "FIELD PRIVACY CONTROLS",
                color = TapMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = card.isFieldSelected(ContactCard.FIELD_PHONE),
                    onClick = { viewModel.toggleContactCardField(ContactCard.FIELD_PHONE) },
                    label = { Text("Phone") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TapAccent,
                        selectedLabelColor = TapInk,
                        containerColor = TapPanel,
                        labelColor = TapMuted
                    )
                )
                FilterChip(
                    selected = card.isFieldSelected(ContactCard.FIELD_EMAIL),
                    onClick = { viewModel.toggleContactCardField(ContactCard.FIELD_EMAIL) },
                    label = { Text("Email") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TapAccent,
                        selectedLabelColor = TapInk,
                        containerColor = TapPanel,
                        labelColor = TapMuted
                    )
                )
                FilterChip(
                    selected = card.isFieldSelected(ContactCard.FIELD_WEBSITE),
                    onClick = { viewModel.toggleContactCardField(ContactCard.FIELD_WEBSITE) },
                    label = { Text("Website") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TapAccent,
                        selectedLabelColor = TapInk,
                        containerColor = TapPanel,
                        labelColor = TapMuted
                    )
                )
            }

            Spacer(Modifier.height(24.dp))

            // Sharing Options Header
            Text(
                "SHARING METHODS (NO RECIPIENT APP NEEDED)",
                color = TapMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
            )

            // Option 1: Write to NFC Tag
            SharingActionCard(
                icon = Icons.Rounded.Nfc,
                title = "Write to Physical NFC Tag",
                description = "Encode vCard NDEF record to NTAG213/215/216 tag. Works with any iPhone or Android tap.",
                badgeText = "Physical NFC",
                onClick = {
                    viewModel.startNfcTagWriter()
                }
            )

            Spacer(Modifier.height(10.dp))

            // Option 2: QR Contact Card
            SharingActionCard(
                icon = Icons.Rounded.QrCode2,
                title = "Display Contact QR Code",
                description = "Scannable with any native iOS or Android Camera app. Adds contact with 1 tap.",
                badgeText = "Universal QR",
                onClick = {
                    val vcard = card.toVCard3(filterSelected = true)
                    qrBitmap = QrCodeGenerator.generateQrBitmap(vcard)
                    showQrDialog = true
                }
            )

            Spacer(Modifier.height(10.dp))

            // Option 3: Local HTTP Browser Sharing
            SharingActionCard(
                icon = Icons.Rounded.WifiTethering,
                title = if (isHttpRunning) "Web Sharing Active: $httpUrl" else "Share via Web Browser",
                description = if (isHttpRunning) "Recipient opens URL in browser on same Wi-Fi/Hotspot." else "Start local HTTP server to download .vcf without apps.",
                badgeText = if (isHttpRunning) "Active" else "Local Web",
                badgeActive = isHttpRunning,
                onClick = {
                    viewModel.toggleLocalHttpServer(includeFiles = false)
                }
            )

            Spacer(Modifier.height(10.dp))

            // Option 4: System Sharesheet Export (.vcf)
            SharingActionCard(
                icon = Icons.Rounded.Share,
                title = "Export vCard (.vcf) via System",
                description = "Send contact card via Quick Share, WhatsApp, Email, or Messages.",
                badgeText = "Sharesheet",
                onClick = {
                    viewModel.shareContactViaSharesheet()
                }
            )

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ContactDetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, isIncluded: Boolean) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = if (isIncluded) TapAccent else TapMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(value, color = if (isIncluded) Color.White else TapMuted, fontSize = 14.sp)
        if (!isIncluded) {
            Spacer(Modifier.width(8.dp))
            Text("(Hidden)", color = Color(0xFFFF6B6B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SharingActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    badgeText: String,
    badgeActive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = TapPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (badgeActive) TapAccent else TapBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (badgeActive) TapAccent else TapBorder),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (badgeActive) TapInk else Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (badgeActive) TapAccent.copy(alpha = 0.2f) else TapBorder)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            badgeText,
                            color = if (badgeActive) TapAccent else TapMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(description, color = TapMuted, fontSize = 12.sp)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TapMuted)
        }
    }
}

@Composable
private fun EditContactDialog(
    initialCard: ContactCard,
    onDismiss: () -> Unit,
    onSave: (ContactCard) -> Unit
) {
    var name by remember { mutableStateOf(initialCard.displayName) }
    var title by remember { mutableStateOf(initialCard.jobTitle) }
    var company by remember { mutableStateOf(initialCard.company) }
    var phone by remember { mutableStateOf(initialCard.phone) }
    var email by remember { mutableStateOf(initialCard.email) }
    var website by remember { mutableStateOf(initialCard.website) }
    var notes by remember { mutableStateOf(initialCard.notes) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = TapPanel,
            border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Edit Contact Card", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))

                TapInputField(label = "Display Name", value = name, onValueChange = { name = it })
                TapInputField(label = "Job Title", value = title, onValueChange = { title = it })
                TapInputField(label = "Company / Org", value = company, onValueChange = { company = it })
                TapInputField(label = "Phone Number", value = phone, onValueChange = { phone = it })
                TapInputField(label = "Email Address", value = email, onValueChange = { email = it })
                TapInputField(label = "Website / Profile URL", value = website, onValueChange = { website = it })
                TapInputField(label = "Notes", value = notes, onValueChange = { notes = it })

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancel", color = TapMuted)
                    }
                    Button(
                        onClick = {
                            onSave(
                                initialCard.copy(
                                    displayName = name,
                                    jobTitle = title,
                                    company = company,
                                    phone = phone,
                                    email = email,
                                    website = website,
                                    notes = notes
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TapAccent, contentColor = TapInk),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Card")
                    }
                }
            }
        }
    }
}

@Composable
private fun TapInputField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = TapMuted, fontSize = 12.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
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
}
