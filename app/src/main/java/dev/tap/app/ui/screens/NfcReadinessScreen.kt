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
import dev.tap.app.navigation.TapScreen
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.NfcMode
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun NfcReadinessScreen(viewModel: TransferViewModel) {
    val activeMode by viewModel.nfcMode.collectAsState()
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
                    "NFC Universal Modes",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Status Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TapPanel)
                    .border(1.dp, if (caps.nfcEnabled) TapAccent else TapBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (caps.nfcEnabled) Icons.Rounded.Nfc else Icons.Rounded.Close,
                        contentDescription = null,
                        tint = if (caps.nfcEnabled) TapAccent else Color(0xFFFF6B6B),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (caps.nfcEnabled) "NFC Subsystem Active" else "NFC Is Disabled",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (caps.nfcEnabled) "Reader Mode & Type 4 HCE Tag Emulation ready" else "Enable NFC in Android Settings to activate proximity sharing",
                            color = TapMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Background Tap Sharing Quick Switch
            val isBackgroundActive by viewModel.isBackgroundTapSharing.collectAsState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TapPanel)
                    .border(1.dp, if (isBackgroundActive) TapAccent else TapBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background Tap Sharing", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            if (isBackgroundActive) "Always-Ready: tap from Home/Lockscreen" else "Disabled: tap sharing inactive",
                            color = if (isBackgroundActive) TapAccent else TapMuted,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = isBackgroundActive,
                        onCheckedChange = { viewModel.setBackgroundTapSharing(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TapInk,
                            checkedTrackColor = TapAccent,
                            uncheckedThumbColor = TapMuted,
                            uncheckedTrackColor = TapInk
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "SELECT NFC OPERATING MODE",
                color = TapMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)
            )

            // Mode A Card
            NfcModeCard(
                title = "Mode A: TAP-to-TAP Handshake",
                subtitle = "For two devices with TAP installed",
                description = "Transmits short-lived session bootstrap token over NFC HCE/Reader Mode. Wireless transfer negotiates via direct Wi-Fi sockets or BLE.",
                selected = activeMode == NfcMode.MODE_A_TAP_TO_TAP,
                onClick = { viewModel.setNfcMode(NfcMode.MODE_A_TAP_TO_TAP) }
            )

            Spacer(Modifier.height(12.dp))

            // Mode B Card
            NfcModeCard(
                title = "Mode B: Universal NFC Contact Card",
                subtitle = "Works with ANY phone (No TAP needed on receiver)",
                description = "Standard NDEF vCard 3.0 records written to physical NFC tags (NTAG213/215/216) or shared via Type 4 HCE. Compatible with iOS and Android native contacts.",
                selected = activeMode == NfcMode.MODE_B_CONTACT_CARD,
                badge = "Recommended",
                onClick = { viewModel.setNfcMode(NfcMode.MODE_B_CONTACT_CARD) }
            )

            Spacer(Modifier.height(12.dp))

            // Mode C Card
            NfcModeCard(
                title = "Mode C: Background HCE Tag Emulation",
                subtitle = "Emulates NFC Forum Type 4 Tag (AID D2760000850101)",
                description = "Emulates standard CC and NDEF files directly from phone hardware. Handles device unlock requirements, screen states, and payment service coexistence.",
                selected = activeMode == NfcMode.MODE_C_BACKGROUND_HCE,
                onClick = { viewModel.setNfcMode(NfcMode.MODE_C_BACKGROUND_HCE) }
            )

            Spacer(Modifier.height(24.dp))

            TapButton(
                text = "Open Contact Card",
                onClick = { viewModel.navigateTo(TapScreen.ContactCard) },
                isPrimary = true
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NfcModeCard(
    title: String,
    subtitle: String,
    description: String,
    selected: Boolean,
    badge: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = TapPanel,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (selected) TapAccent else TapBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(TapAccent.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(badge, color = TapAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                RadioButton(
                    selected = selected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(selectedColor = TapAccent, unselectedColor = TapMuted)
                )
            }
            Text(subtitle, color = TapAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(description, color = TapMuted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
