package dev.tap.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.protocol.TapProtocol
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun AboutScreen(viewModel: TransferViewModel) {
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
            TapTopBar(
                title = "About TAP",
                onBack = { viewModel.navigateBack() }
            )

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(TapAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Nfc,
                    contentDescription = "TAP",
                    tint = TapInk,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "TAP",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            Text(
                "Production P2P File Sharing",
                color = TapAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(28.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TapPanel)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Protocol Architecture", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "TAP is built for privacy-first, serverless peer-to-peer sharing. Connections are bootstrapped via NFC proximity, Bluetooth Low Energy, or dynamic QR codes, then streamed at maximum wire speed across direct local Wi-Fi / IP channels.",
                        color = TapMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TapPanel)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Privacy & Security Principles", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("• Zero User Accounts: No signups, tracking IDs, or passwords.", color = TapMuted, fontSize = 13.sp)
                    Text("• Zero Cloud Relay: File bytes never touch external servers.", color = TapMuted, fontSize = 13.sp)
                    Text("• Authenticated Key Exchange: Ephemeral ECDH secp256r1 keys.", color = TapMuted, fontSize = 13.sp)
                    Text("• Authenticated Encryption: AES-256-GCM chunk streaming.", color = TapMuted, fontSize = 13.sp)
                    Text("• File Integrity: Pre- and post-transfer SHA-256 verification.", color = TapMuted, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TapPanel)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Protocol Version", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Version ${TapProtocol.PROTOCOL_VERSION} • Port ${TapProtocol.DEFAULT_PORT}", color = TapMuted, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}
