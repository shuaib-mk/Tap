package dev.tap.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.tap.app.contacts.ContactCard
import dev.tap.app.discovery.qr.QrCodeGenerator
import dev.tap.app.navigation.TapScreen
import dev.tap.app.ui.components.*
import dev.tap.app.ui.theme.AnthropicSerifDisplay
import dev.tap.app.ui.theme.AnthropicSerifText
import dev.tap.app.ui.viewmodels.TransferViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactCardScreen(viewModel: TransferViewModel) {
    val card by viewModel.contactCard.collectAsState()
    val isHttpRunning by viewModel.isHttpServerRunning.collectAsState()
    val httpUrl by viewModel.httpServerUrl.collectAsState()

    var showEditSheet by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // QR Code Dialog
    if (showQrDialog) {
        Dialog(onDismissRequest = { showQrDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = TapPanel,
                border = BorderStroke(1.dp, TapBorder),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Contact QR",
                        fontFamily = AnthropicSerifDisplay,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Scan with any camera app to import contact directly.",
                        fontFamily = AnthropicSerifText,
                        color = TapMuted,
                        fontSize = 12.sp,
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
                        text = "Done",
                        onClick = { showQrDialog = false },
                        isPrimary = false
                    )
                }
            }
        }
    }

    // Keyboard-safe BottomSheet Editor
    if (showEditSheet) {
        EditContactSheet(
            initialCard = card,
            onDismiss = { showEditSheet = false },
            onSave = { updated ->
                viewModel.updateContactCard(updated)
                showEditSheet = false
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = TapInk) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.navigateBack() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(TapPanel)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Identity",
                        fontFamily = AnthropicSerifDisplay,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { showEditSheet = true },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(TapPanel)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit Card", tint = TapAccent, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Hero Contact Card Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(TapPanel)
                        .border(1.dp, TapBorder, RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(TapAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = card.displayName.take(2).uppercase().ifBlank { "TA" },
                                    fontFamily = AnthropicSerifDisplay,
                                    color = TapInk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = card.displayName.ifBlank { "Tap User" },
                                    fontFamily = AnthropicSerifDisplay,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (card.jobTitle.isNotBlank() || card.company.isNotBlank()) {
                                    Text(
                                        text = listOf(card.jobTitle, card.company).filter { it.isNotBlank() }.joinToString(" • "),
                                        fontFamily = AnthropicSerifText,
                                        color = TapMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = TapBorder)
                        Spacer(Modifier.height(14.dp))

                        ContactDetailRow(Icons.Rounded.Phone, "Phone", card.phone, card.isFieldSelected(ContactCard.FIELD_PHONE))
                        ContactDetailRow(Icons.Rounded.Email, "Email", card.email, card.isFieldSelected(ContactCard.FIELD_EMAIL))
                        ContactDetailRow(Icons.Rounded.Language, "Website", card.website, card.isFieldSelected(ContactCard.FIELD_WEBSITE))
                        if (card.notes.isNotBlank()) {
                            ContactDetailRow(Icons.Rounded.Notes, "Notes", card.notes, card.isFieldSelected(ContactCard.FIELD_NOTES))
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Field Privacy Filters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = card.isFieldSelected(ContactCard.FIELD_PHONE),
                        onClick = { viewModel.toggleContactCardField(ContactCard.FIELD_PHONE) },
                        label = { Text("Phone", fontFamily = AnthropicSerifText, fontSize = 12.sp) },
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
                        label = { Text("Email", fontFamily = AnthropicSerifText, fontSize = 12.sp) },
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
                        label = { Text("Website", fontFamily = AnthropicSerifText, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TapAccent,
                            selectedLabelColor = TapInk,
                            containerColor = TapPanel,
                            labelColor = TapMuted
                        )
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Minimal Sharing Methods Grid (Clean 4-Icon Layout)
                Text(
                    "QUICK SHARE",
                    fontFamily = AnthropicSerifDisplay,
                    color = TapMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. NFC Tag Writer
                    ShareMethodGridTile(
                        icon = Icons.Rounded.Nfc,
                        label = "NFC Tag",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.startNfcTagWriter() }
                    )

                    // 2. Display QR Code
                    ShareMethodGridTile(
                        icon = Icons.Rounded.QrCode2,
                        label = "QR Code",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val vcard = card.toVCard3(filterSelected = true)
                            qrBitmap = QrCodeGenerator.generateQrBitmap(vcard)
                            showQrDialog = true
                        }
                    )

                    // 3. Web Share
                    ShareMethodGridTile(
                        icon = Icons.Rounded.WifiTethering,
                        label = if (isHttpRunning) "Web Active" else "Browser",
                        active = isHttpRunning,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.toggleLocalHttpServer(includeFiles = false) }
                    )

                    // 4. Sharesheet Export
                    ShareMethodGridTile(
                        icon = Icons.Rounded.Share,
                        label = "Export",
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.shareContactViaSharesheet() }
                    )
                }

                if (isHttpRunning && httpUrl.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = TapAccent.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, TapAccent.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null, tint = TapAccent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Web active: $httpUrl",
                                fontFamily = AnthropicSerifText,
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(28.dp))
            }

            MetroBottomBar(
                currentScreen = TapScreen.ContactCard,
                onNavigate = { viewModel.navigateTo(it) }
            )
        }
    }
}

@Composable
private fun ShareMethodGridTile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (active) TapAccent.copy(alpha = 0.15f) else TapPanel,
        border = BorderStroke(1.dp, if (active) TapAccent else TapBorder),
        modifier = modifier.height(76.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (active) TapAccent else TapPanelLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (active) TapInk else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                fontFamily = AnthropicSerifText,
                color = if (active) TapAccent else TapMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ContactDetailRow(icon: ImageVector, label: String, value: String, isIncluded: Boolean) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = if (isIncluded) TapAccent else TapMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(12.dp))
        Text(value, fontFamily = AnthropicSerifText, color = if (isIncluded) Color.White else TapMuted, fontSize = 14.sp)
        if (!isIncluded) {
            Spacer(Modifier.width(8.dp))
            Text("(Hidden)", color = Color(0xFFF87171), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditContactSheet(
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TapPanel,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TapBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Edit Contact Card",
                fontFamily = AnthropicSerifDisplay,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            TapInputField(label = "Display Name", value = name, onValueChange = { name = it })
            TapInputField(label = "Job Title", value = title, onValueChange = { title = it })
            TapInputField(label = "Company / Org", value = company, onValueChange = { company = it })
            TapInputField(label = "Phone Number", value = phone, onValueChange = { phone = it })
            TapInputField(label = "Email Address", value = email, onValueChange = { email = it })
            TapInputField(label = "Website / Profile URL", value = website, onValueChange = { website = it })
            TapInputField(label = "Notes", value = notes, onValueChange = { notes = it })

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TapBorder)
                ) {
                    Text("Cancel", fontFamily = AnthropicSerifText, color = TapMuted)
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
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("Save Card", fontFamily = AnthropicSerifDisplay, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TapInputField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, fontFamily = AnthropicSerifText, color = TapMuted, fontSize = 12.sp)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = TapAccent,
                unfocusedBorderColor = TapBorder,
                focusedContainerColor = TapPanelLight,
                unfocusedContainerColor = TapPanelLight
            ),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
