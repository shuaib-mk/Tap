package dev.tap.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.capabilities.DeviceCapabilities
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun CompatibilityDiagnosticsScreen(viewModel: TransferViewModel) {
    val caps by viewModel.capabilities.collectAsState()

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
                    "Compatibility Diagnostics",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.refreshCapabilities() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = TapAccent)
                }
            }

            // Device Info Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TapPanel)
                    .border(1.dp, TapBorder, RoundedCornerShape(16.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Text(caps.deviceModel, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOf(caps.androidVersion, caps.oneUiVersion).filterNotNull().joinToString(" • "),
                        color = TapAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            SectionHeader("NFC HARDWARE & SERVICES")
            DiagnosticRow("NFC Controller Present", caps.nfcHardware)
            DiagnosticRow("NFC Subsystem Enabled", caps.nfcEnabled)
            DiagnosticRow("NFC Reader/Writer Mode", caps.nfcReaderWriter)
            DiagnosticRow("Host Card Emulation (HCE)", caps.nfcHce)

            Spacer(Modifier.height(16.dp))

            SectionHeader("BLUETOOTH & BLE PROXIMITY")
            DiagnosticRow("Bluetooth Controller", caps.bluetoothHardware)
            DiagnosticRow("Bluetooth Enabled", caps.bluetoothEnabled)
            DiagnosticRow("BLE Proximity Scanning", caps.bleScanSupported)
            DiagnosticRow("BLE Beacon Advertising", caps.bleAdvertiseSupported)

            Spacer(Modifier.height(16.dp))

            SectionHeader("NETWORK & UNIVERSAL WEB SHARING")
            DiagnosticRow("Wi-Fi Connected", caps.wifiConnected, detail = caps.wifiSsid)
            DiagnosticRow("Local IPv4 Address", caps.localIpAddress != null, detail = caps.localIpAddress ?: "Not connected")
            DiagnosticRow("Local Embedded HTTP Server", caps.isLocalWebSharingAvailable, detail = "Port 8989")

            Spacer(Modifier.height(16.dp))

            SectionHeader("NATIVE SHARING & CAMERA")
            DiagnosticRow("Optical Camera (QR)", caps.cameraAvailable)
            DiagnosticRow("Android Sharesheet", caps.sharesheetAvailable)
            DiagnosticRow("Quick Share / Nearby Share", caps.quickShareAvailable)

            Spacer(Modifier.height(24.dp))

            // Realistic Hardware Compatibility Notes
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TapPanel)
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = TapAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Hardware Interoperability Truth", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "• Phone-to-phone NDEF via Android Beam was removed in Android 14. TAP uses Reader Mode and Type 4 HCE tag emulation.\n" +
                        "• iPhones do not read emulated Android HCE tags in the background. To share with an iPhone without apps: use a physical NFC tag written with TAP, or show the Universal QR Contact Card, or use Local Web Sharing.\n" +
                        "• All cryptographic sessions use AES-256-GCM and SHA-256 checksums.",
                        color = TapMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = TapMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
    )
}

@Composable
private fun DiagnosticRow(label: String, isAvailable: Boolean, detail: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TapPanel)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (detail != null) {
                Text(detail, color = TapMuted, fontSize = 12.sp)
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isAvailable) TapAccent.copy(alpha = 0.2f) else Color(0xFFFF6B6B).copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                if (isAvailable) "SUPPORTED" else "UNAVAILABLE",
                color = if (isAvailable) TapAccent else Color(0xFFFF6B6B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}
