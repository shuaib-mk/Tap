package dev.tap.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.navigation.TapScreen
import dev.tap.app.ui.components.*
import dev.tap.app.ui.theme.AnthropicSerifDisplay
import dev.tap.app.ui.theme.AnthropicSerifText
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun SettingsScreen(viewModel: TransferViewModel) {
    val context = LocalContext.current
    val turnProvider = viewModel.anywhereEngine.turnConfigProvider
    val signalingClient = viewModel.anywhereEngine.signalingClient
    val isBackgroundNfc by viewModel.isBackgroundTapSharing.collectAsState()

    var customTurnEnabled by remember { mutableStateOf(turnProvider.isCustomTurnEnabled()) }
    var turnHost by remember { mutableStateOf(turnProvider.getTurnHost()) }
    var turnPort by remember { mutableStateOf(turnProvider.getTurnPort().toString()) }
    var turnSecret by remember { mutableStateOf("") }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = {
                Text(
                    "Clear Transfer History",
                    fontFamily = AnthropicSerifDisplay,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete all transfer logs from this device?",
                    fontFamily = AnthropicSerifText,
                    color = TapMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHistory()
                        showClearHistoryDialog = false
                        Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TapRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Clear All", fontFamily = AnthropicSerifDisplay, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", fontFamily = AnthropicSerifText, color = TapMuted)
                }
            },
            containerColor = TapPanel
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
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Settings",
                        fontFamily = AnthropicSerifDisplay,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Section 1: Device Info
                SettingsSectionTitle("DEVICE")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TapPanel,
                    border = BorderStroke(1.dp, TapBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(TapPanelLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Smartphone, contentDescription = null, tint = TapAccent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Device Name", fontFamily = AnthropicSerifText, color = TapMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(viewModel.deviceName, fontFamily = AnthropicSerifDisplay, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Section 2: Proximity & Tap Options
                SettingsSectionTitle("PROXIMITY & SHARING")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TapPanel,
                    border = BorderStroke(1.dp, TapBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Background NFC Sharing",
                                    fontFamily = AnthropicSerifDisplay,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Share identity card when phones touch, even if TAP is closed",
                                    fontFamily = AnthropicSerifText,
                                    color = TapMuted,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = isBackgroundNfc,
                                onCheckedChange = { viewModel.setBackgroundTapSharing(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = TapInk, checkedTrackColor = TapAccent)
                            )
                        }

                        HorizontalDivider(color = TapBorder, modifier = Modifier.padding(vertical = 12.dp))

                        SettingsNavRow(
                            icon = Icons.Rounded.Nfc,
                            title = "Physical NFC Card Writer",
                            subtitle = "Encode external NTAG cards with vCard",
                            onClick = { viewModel.navigateTo(TapScreen.NfcCardWriter) }
                        )

                        HorizontalDivider(color = TapBorder, modifier = Modifier.padding(vertical = 12.dp))

                        SettingsNavRow(
                            icon = Icons.Rounded.Memory,
                            title = "Hardware Diagnostics",
                            subtitle = "Audit NFC, HCE, BLE and Wi-Fi capabilities",
                            onClick = { viewModel.navigateTo(TapScreen.CompatibilityDiagnostics) }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Section 3: Relay & Network (COTURN)
                SettingsSectionTitle("RELAY & NETWORK")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TapPanel,
                    border = BorderStroke(1.dp, TapBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Custom COTURN Relay",
                                    fontFamily = AnthropicSerifDisplay,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Self-hosted TURN relay for restrictive firewalls",
                                    fontFamily = AnthropicSerifText,
                                    color = TapMuted,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = customTurnEnabled,
                                onCheckedChange = { customTurnEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = TapInk, checkedTrackColor = TapAccent)
                            )
                        }

                        if (customTurnEnabled) {
                            Spacer(Modifier.height(14.dp))
                            OutlinedTextField(
                                value = turnHost,
                                onValueChange = { turnHost = it },
                                placeholder = { Text("turn.yourdomain.com", color = TapMuted) },
                                label = { Text("Host", color = TapMuted, fontSize = 11.sp) },
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

                            Spacer(Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = turnPort,
                                    onValueChange = { turnPort = it },
                                    placeholder = { Text("3478", color = TapMuted) },
                                    label = { Text("Port", color = TapMuted, fontSize = 11.sp) },
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
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = turnSecret,
                                    onValueChange = { turnSecret = it },
                                    placeholder = { Text("Secret", color = TapMuted) },
                                    label = { Text("Shared Secret", color = TapMuted, fontSize = 11.sp) },
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
                                    modifier = Modifier.weight(2f)
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    val port = turnPort.toIntOrNull() ?: 3478
                                    turnProvider.saveCustomTurnConfig(
                                        enabled = customTurnEnabled,
                                        host = turnHost.trim(),
                                        port = port,
                                        secret = turnSecret.trim().ifEmpty { null },
                                        username = null,
                                        password = null,
                                        useTls = false
                                    )
                                    Toast.makeText(context, "TURN configuration saved", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TapAccent, contentColor = TapInk),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text("Save Settings", fontFamily = AnthropicSerifDisplay, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Section 4: Data & Reset
                SettingsSectionTitle("DATA")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = TapPanel,
                    border = BorderStroke(1.dp, TapBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showClearHistoryDialog = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = TapRed, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Clear Activity History", fontFamily = AnthropicSerifDisplay, color = TapRed, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(2.dp))
                                Text("Remove all logged transfer records", fontFamily = AnthropicSerifText, color = TapMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(30.dp))
            }

            MetroBottomBar(
                currentScreen = TapScreen.Settings,
                onNavigate = { viewModel.navigateTo(it) }
            )
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        fontFamily = AnthropicSerifDisplay,
        color = TapMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TapAccent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = AnthropicSerifDisplay, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontFamily = AnthropicSerifText, color = TapMuted, fontSize = 11.sp)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TapMuted, modifier = Modifier.size(18.dp))
    }
}
