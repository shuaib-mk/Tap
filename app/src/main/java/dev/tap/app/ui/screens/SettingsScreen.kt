package dev.tap.app.ui.screens

import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.navigation.TapScreen
import dev.tap.app.proximity.QuickShareCompatibilityAdapter
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun SettingsScreen(viewModel: TransferViewModel) {
    val context = LocalContext.current
    val turnProvider = viewModel.anywhereEngine.turnConfigProvider
    val signalingClient = viewModel.anywhereEngine.signalingClient
    val isBackgroundNfc by viewModel.isBackgroundTapSharing.collectAsState()

    var signalingUrl by remember { mutableStateOf(signalingClient.getSignalingUrl()) }
    var customTurnEnabled by remember { mutableStateOf(turnProvider.isCustomTurnEnabled()) }
    var turnHost by remember { mutableStateOf(turnProvider.getTurnHost()) }
    var turnPort by remember { mutableStateOf(turnProvider.getTurnPort().toString()) }
    var turnSecret by remember { mutableStateOf("") }
    var useTls by remember { mutableStateOf(false) }

    val quickShareReport = remember { QuickShareCompatibilityAdapter.checkCompatibility(context) }

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
                TapTopBar(
                    title = "Settings",
                    subtitle = "Configuration, network & hardware",
                    onBack = null
                )

                // 1. Device Profile Section
                SectionHeader("DEVICE IDENTITY")
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = TapPanel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Device Name", color = TapMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(viewModel.deviceName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 2. WebRTC Anywhere Signaling & TURN Infrastructure
                SectionHeader("WEBRTC SIGNALING & NAT TRAVERSAL")
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = TapPanel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Signaling Server Endpoint", color = TapMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = signalingUrl,
                            onValueChange = { signalingUrl = it },
                            placeholder = { Text("wss://signal.tap.dev/ws", color = TapMuted) },
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

                        Spacer(Modifier.height(14.dp))

                        // Custom TURN Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Custom COTURN Relay", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Fallback when direct P2P NAT traversal fails", color = TapMuted, fontSize = 11.sp)
                            }
                            Switch(
                                checked = customTurnEnabled,
                                onCheckedChange = { customTurnEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = TapInk, checkedTrackColor = TapAccent)
                            )
                        }

                        if (customTurnEnabled) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = turnHost,
                                onValueChange = { turnHost = it },
                                placeholder = { Text("turn.yourdomain.com", color = TapMuted) },
                                label = { Text("COTURN Host", color = TapMuted, fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = TapAccent,
                                    unfocusedBorderColor = TapBorder
                                ),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = turnPort,
                                onValueChange = { turnPort = it },
                                placeholder = { Text("3478", color = TapMuted) },
                                label = { Text("Port", color = TapMuted, fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = TapAccent,
                                    unfocusedBorderColor = TapBorder
                                ),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = turnSecret,
                                onValueChange = { turnSecret = it },
                                placeholder = { Text("Shared secret for HMAC tokens", color = TapMuted) },
                                label = { Text("HMAC Shared Secret (Never static)", color = TapMuted, fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = TapAccent,
                                    unfocusedBorderColor = TapBorder
                                ),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                signalingClient.setSignalingUrl(signalingUrl.trim())
                                val port = turnPort.toIntOrNull() ?: 3478
                                turnProvider.saveCustomTurnConfig(
                                    enabled = customTurnEnabled,
                                    host = turnHost.trim(),
                                    port = port,
                                    secret = turnSecret.trim().ifEmpty { null },
                                    username = null,
                                    password = null,
                                    useTls = useTls
                                )
                                Toast.makeText(context, "Network configuration saved", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TapAccent, contentColor = TapInk),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Text("Save Network Settings", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 3. Hardware & Features
                SectionHeader("HARDWARE & INTEGRATIONS")
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = TapPanel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Background NFC Sharing Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Always-Ready NFC Tap Sharing", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Emulate contact card even when app is closed", color = TapMuted, fontSize = 11.sp)
                            }
                            Switch(
                                checked = isBackgroundNfc,
                                onCheckedChange = { viewModel.setBackgroundTapSharing(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = TapInk, checkedTrackColor = TapAccent)
                            )
                        }

                        HorizontalDivider(color = TapBorder, modifier = Modifier.padding(vertical = 12.dp))

                        // Physical Tag Writer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.navigateTo(TapScreen.NfcCardWriter) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Nfc, contentDescription = null, tint = TapAccent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Physical NFC Tag Writer", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Encode NTAG213/215/216 cards with your vCard", color = TapMuted, fontSize = 11.sp)
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TapMuted, modifier = Modifier.size(18.dp))
                        }

                        HorizontalDivider(color = TapBorder, modifier = Modifier.padding(vertical = 12.dp))

                        // Hardware Diagnostics
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.navigateTo(TapScreen.CompatibilityDiagnostics) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Analytics, contentDescription = null, tint = TapAccent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Compatibility Diagnostics", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Audit NFC HCE, WebRTC, Wi-Fi Direct & One UI status", color = TapMuted, fontSize = 11.sp)
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TapMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 4. About TAP
                SectionHeader("ABOUT")
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = TapPanel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("TAP Universal", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("v1.4.0 (Build 6)", color = TapAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "End-to-end encrypted proximity & anywhere transfer platform. Zero tracking, zero cloud storage, zero telemetry.",
                            color = TapMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            // Bottom Navigation
            MetroBottomBar(
                currentScreen = TapScreen.Settings,
                onNavigate = { screen -> viewModel.navigateTo(screen) }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TapMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
    )
}
