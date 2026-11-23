package dev.tap.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.navigation.TapScreen
import dev.tap.app.transfer.SelectedFile
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun FilePickerScreen(viewModel: TransferViewModel) {
    val selectedFiles by viewModel.selectedFiles.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addFiles(uris)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = TapInk) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
        ) {
            TapTopBar(
                title = "Selected Files",
                onBack = { viewModel.navigateBack() },
                actions = {
                    if (selectedFiles.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearFiles() }) {
                            Icon(
                                Icons.Rounded.DeleteSweep,
                                contentDescription = "Clear All",
                                tint = TapRed
                            )
                        }
                    }
                }
            )

            // Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                colors = CardDefaults.cardColors(containerColor = TapPanel)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${selectedFiles.size} items",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Total: ${SelectedFile.formatBytes(viewModel.totalSelectedSize)}",
                            color = TapAccent,
                            fontSize = 14.sp
                        )
                    }

                    OutlinedButton(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TapAccent),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TapAccent)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add More")
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (selectedFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No files selected yet.",
                        color = TapMuted,
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(selectedFiles, key = { it.uri.toString() }) { file ->
                        FileCard(file = file, onRemove = { viewModel.removeFile(file) })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            TapButton(
                text = "Continue to Send",
                onClick = { viewModel.navigateTo(TapScreen.Discovery) },
                enabled = selectedFiles.isNotEmpty(),
                isPrimary = true
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
