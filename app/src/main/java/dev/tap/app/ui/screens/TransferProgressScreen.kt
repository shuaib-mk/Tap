package dev.tap.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.protocol.TransferState
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun TransferProgressScreen(viewModel: TransferViewModel) {
    val progress = viewModel.activeProgress
    val mode by viewModel.mode.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinRot"
    )

    Surface(modifier = Modifier.fillMaxSize(), color = TapInk) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TapTopBar(
                title = if (mode == "Send") "Sending..." else "Receiving..."
            )

            Spacer(Modifier.weight(0.5f))

            // Progress Circular Visualizer
            Box(
                modifier = Modifier.size(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress.percentComplete() },
                    modifier = Modifier.fillMaxSize(),
                    color = TapAccent,
                    trackColor = TapPanel,
                    strokeWidth = 10.dp
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = progress.formattedPercent(),
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = progress.state.name,
                        color = TapAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Current File Name
            Text(
                text = progress.currentFileName.ifBlank { "Preparing transfer..." },
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(6.dp))

            // File count
            if (progress.totalFiles > 1) {
                Text(
                    text = progress.formattedFileCount(),
                    color = TapMuted,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
            }

            // Transferred of Total
            Text(
                text = progress.formattedProgress(),
                color = TapMuted,
                fontSize = 15.sp
            )

            Spacer(Modifier.height(28.dp))

            // Transfer Stats Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TapPanel)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Speed", color = TapMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            progress.formattedSpeed(),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(TapBorder)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Time Left", color = TapMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            progress.formattedEta(),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Cancel Button
            OutlinedButton(
                onClick = { viewModel.cancelActiveTransfer() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TapRed)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = TapRed)
                Spacer(Modifier.width(8.dp))
                Text("Cancel Transfer", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
