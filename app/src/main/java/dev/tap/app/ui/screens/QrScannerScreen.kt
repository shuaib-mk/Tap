package dev.tap.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.protocol.TapProtocol
import dev.tap.app.protocol.SessionOffer
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun QrScannerScreen(viewModel: TransferViewModel) {
    var manualSessionCode by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = TapInk) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
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
                    "Scan QR Code",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(24.dp))

            // Scanner Viewfinder
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(TapPanel)
                    .border(2.dp, TapAccent, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.QrCodeScanner,
                    contentDescription = null,
                    tint = TapAccent,
                    modifier = Modifier.size(96.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Point at Receiver's QR Code",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Scan the session QR code on the receiving device or enter the session JSON / token below.",
                color = TapMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = manualSessionCode,
                onValueChange = {
                    manualSessionCode = it
                    parseError = null
                },
                placeholder = { Text("Paste session offer JSON...", color = TapMuted, fontSize = 13.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = TapAccent,
                    unfocusedBorderColor = TapBorder
                ),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            if (parseError != null) {
                Spacer(Modifier.height(6.dp))
                Text(parseError!!, color = Color(0xFFFF6B6B), fontSize = 12.sp)
            }

            Spacer(Modifier.height(16.dp))

            TapButton(
                text = "Connect with Offer",
                onClick = {
                    try {
                        val offer = TapProtocol.json.decodeFromString<SessionOffer>(manualSessionCode.trim())
                        viewModel.connectAndSend(offer)
                    } catch (e: Exception) {
                        parseError = "Invalid session offer JSON: ${e.message}"
                    }
                },
                isPrimary = true
            )
        }
    }
}
