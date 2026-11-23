package dev.tap.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.NfcWriteUiState
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun NfcCardWriterScreen(viewModel: TransferViewModel) {
    val writeState by viewModel.nfcWriteState.collectAsState()
    val card by viewModel.contactCard.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = TapInk) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    viewModel.resetNfcWriteState()
                    viewModel.navigateBack()
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Write NFC Contact Tag",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.weight(0.5f))

            // State Display
            when (val state = writeState) {
                is NfcWriteUiState.Idle, is NfcWriteUiState.WaitingForTag -> {
                    PulsingNfcCircle(
                        active = true,
                        onClick = {}
                    )
                    Spacer(Modifier.height(32.dp))
                    Text(
                        "Hold Against NFC Tag",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Position the back of your phone near an NFC tag (NTAG213, NTAG215, NTAG216, or compatible card).",
                        color = TapMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(TapPanel)
                            .border(1.dp, TapBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "Encoding: ${card.displayName.ifBlank { "Personal Contact Card" }}",
                            color = TapAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                is NfcWriteUiState.Writing -> {
                    CircularProgressIndicator(
                        color = TapAccent,
                        modifier = Modifier.size(72.dp),
                        strokeWidth = 6.dp
                    )
                    Spacer(Modifier.height(32.dp))
                    Text(
                        "Writing NDEF Record...",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Do not move the device away from the tag.",
                        color = TapMuted,
                        fontSize = 14.sp
                    )
                }
                is NfcWriteUiState.Success -> {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(TapAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = TapInk, modifier = Modifier.size(44.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "NFC Tag Ready!",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Successfully wrote ${state.bytesWritten} bytes to tag (Capacity: ${state.tagCapacity} bytes).\nAny iPhone or Android phone can now tap this tag to save your contact card instantly.",
                        color = TapMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
                is NfcWriteUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PriorityHigh, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Write Failed",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.message,
                        color = TapMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (writeState is NfcWriteUiState.Success || writeState is NfcWriteUiState.Error) {
                TapButton(
                    text = if (writeState is NfcWriteUiState.Success) "Done" else "Try Again",
                    onClick = {
                        if (writeState is NfcWriteUiState.Success) {
                            viewModel.resetNfcWriteState()
                            viewModel.navigateBack()
                        } else {
                            viewModel.startNfcTagWriter()
                        }
                    },
                    isPrimary = true
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
