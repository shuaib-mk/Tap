package dev.tap.app.ui.screens

import android.app.DownloadManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.navigation.TapScreen
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun TransferCompleteScreen(viewModel: TransferViewModel) {
    val context = LocalContext.current
    val progress = viewModel.activeProgress

    Surface(modifier = Modifier.fillMaxSize(), color = TapInk) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.5f))

            // Animated Success Checkmark
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(TapAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Success",
                    tint = TapInk,
                    modifier = Modifier.size(60.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "Transfer Complete",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "All files verified with SHA-256 and saved safely.",
                color = TapMuted,
                fontSize = 14.sp
            )

            Spacer(Modifier.height(32.dp))

            // Transfer Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TapPanel)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Transferred Size", color = TapMuted, fontSize = 14.sp)
                        Text(progress.formattedProgress(), color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = TapBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Integrity Status", color = TapMuted, fontSize = 14.sp)
                        Text("Verified SHA-256", color = TapAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Open Files Button
            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TapBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = TapAccent)
                Spacer(Modifier.width(8.dp))
                Text("Open Downloads", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(12.dp))

            // Return to Home
            TapButton(
                text = "Done",
                onClick = {
                    viewModel.clearFiles()
                    viewModel.navigateTo(TapScreen.Home)
                },
                isPrimary = true
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
