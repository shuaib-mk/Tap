package dev.tap.app.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.navigation.TapScreen
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun SettingsScreen(viewModel: TransferViewModel) {
    Surface(modifier = Modifier.fillMaxSize(), color = TapInk) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .verticalScroll(rememberScrollState())
        ) {
            TapTopBar(
                title = "Settings",
                onBack = { viewModel.navigateBack() }
            )

            Spacer(Modifier.height(10.dp))

            Text("Device Identity", color = TapMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TapPanel)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Smartphone, contentDescription = null, tint = TapAccent)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Device Name", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(viewModel.deviceName, color = TapMuted, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Hardware & Capabilities", color = TapMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TapPanel)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("NFC Hardware", color = Color.White, fontSize = 14.sp)
                        StatusChip("Available", viewModel.nfcCapabilities.hardwarePresent)
                    }
                    HorizontalDivider(color = TapBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("NFC Status", color = Color.White, fontSize = 14.sp)
                        StatusChip(if (viewModel.nfcCapabilities.enabled) "Active" else "Disabled", viewModel.nfcCapabilities.enabled)
                    }
                    HorizontalDivider(color = TapBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Host Card Emulation (HCE)", color = Color.White, fontSize = 14.sp)
                        StatusChip("Supported", viewModel.nfcCapabilities.hceSupported)
                    }
                    HorizontalDivider(color = TapBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bluetooth LE", color = Color.White, fontSize = 14.sp)
                        StatusChip(if (viewModel.isBleAvailable) "Ready" else "Disabled", viewModel.isBleAvailable)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("Security & Privacy", color = TapMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TapPanel)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = TapAccent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Encryption", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("AES-256-GCM + ECDH secp256r1", color = TapMuted, fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(color = TapBorder)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = TapAccent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Integrity Verification", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Streaming SHA-256 hash checks", color = TapMuted, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            OutlinedButton(
                onClick = { viewModel.navigateTo(TapScreen.About) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("About TAP", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
