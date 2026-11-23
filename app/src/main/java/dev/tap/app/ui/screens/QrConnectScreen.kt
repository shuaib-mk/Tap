package dev.tap.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.protocol.ConnectionMethod
import dev.tap.app.protocol.SessionOffer
import dev.tap.app.protocol.TapProtocol
import dev.tap.app.protocol.decodeQrPayload
import dev.tap.app.ui.components.*
import dev.tap.app.ui.viewmodels.TransferViewModel

@Composable
fun QrConnectScreen(viewModel: TransferViewModel) {
    val qrBitmap by viewModel.qrBitmap.collectAsState()
    val sessionOffer by viewModel.sessionOffer.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Show QR, 1 = Local Web Sharing, 2 = Direct Connect
    var targetIp by remember { mutableStateOf("") }
    var targetPort by remember { mutableStateOf(TapProtocol.DEFAULT_PORT.toString()) }
    var qrPayloadInput by remember { mutableStateOf("") }

    val isHttpRunning by viewModel.isHttpServerRunning.collectAsState()
    val httpUrl by viewModel.httpServerUrl.collectAsState()
    val capabilities by viewModel.capabilities.collectAsState()

    LaunchedEffect(Unit) {
        if (sessionOffer == null) {
            viewModel.prepareReceiverSession()
        }
    }

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
                title = "QR & Direct Connect",
                onBack = { viewModel.navigateBack() }
            )

            // Tab bar
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = TapPanel,
                contentColor = TapAccent,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("TAP QR", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Web Share", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Direct IP", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }

            Spacer(Modifier.height(20.dp))

            if (selectedTab == 0) {
                // Show QR Code for peer to scan
                Text(
                    "Scan to Connect (TAP-to-TAP)",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "The sender device running TAP scans this QR code to initiate transfer.",
                    color = TapMuted,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(TapPanel)
                        .border(2.dp, TapAccent, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "Session QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        CircularProgressIndicator(color = TapAccent)
                    }
                }

                Spacer(Modifier.height(20.dp))

                sessionOffer?.let { offer ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = TapPanel)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Receiver IP: ${offer.hostAddress}:${offer.port}", color = Color.White, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Session: ${offer.sessionId.take(12)}...", color = TapMuted, fontSize = 12.sp)
                        }
                    }
                }

            } else if (selectedTab == 1) {
                // Local Web Server (No app needed for receiver!)
                Text(
                    "Local Web Sharing",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Receivers on ANY phone or computer (iPhone, Android, Windows, Mac) can open their browser and download shared contacts & files. No app required!",
                    color = TapMuted,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                if (isHttpRunning) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(TapPanel)
                            .border(1.5.dp, TapAccent, RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("HTTP SERVER RUNNING", color = TapAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(httpUrl, color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("Point recipient's camera or browser to this URL", color = TapMuted, fontSize = 12.sp)

                            if (capabilities.isCellularOnly) {
                                Spacer(Modifier.height(14.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF2A2015),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Note: Direct browser download requires both phones on the same Wi-Fi or Mobile Hotspot. For cellular transfer, use 'Send via Code' instead.",
                                        color = Color(0xFFFDE68A),
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(10.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                TapButton(
                    text = if (isHttpRunning) "Stop Web Server" else "Start Web Server",
                    onClick = { viewModel.toggleLocalHttpServer(includeFiles = true) },
                    isPrimary = !isHttpRunning
                )

            } else {
                // Direct IP / Payload Connect
                Text(
                    "Connect to Receiver",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Enter the receiver's local IP address or paste their session offer payload.",
                    color = TapMuted,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = targetIp,
                    onValueChange = { targetIp = it },
                    label = { Text("Receiver IP Address", color = TapMuted) },
                    placeholder = { Text("192.168.1.100", color = TapMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TapAccent,
                        unfocusedBorderColor = TapBorder
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = targetPort,
                    onValueChange = { targetPort = it },
                    label = { Text("Port", color = TapMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TapAccent,
                        unfocusedBorderColor = TapBorder
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = qrPayloadInput,
                    onValueChange = {
                        qrPayloadInput = it
                        decodeQrPayload(it)?.let { offer ->
                            targetIp = offer.hostAddress
                            targetPort = offer.port.toString()
                        }
                    },
                    label = { Text("Or Paste QR Payload", color = TapMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TapAccent,
                        unfocusedBorderColor = TapBorder
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Spacer(Modifier.height(24.dp))

                TapButton(
                    text = "Connect & Transfer",
                    onClick = {
                        val portNum = targetPort.toIntOrNull() ?: TapProtocol.DEFAULT_PORT
                        val offer = SessionOffer(
                            sessionId = "direct_${System.currentTimeMillis()}",
                            deviceName = "Peer Receiver",
                            publicKeyBase64 = "",
                            connectionMethod = ConnectionMethod.DIRECT_IP,
                            hostAddress = targetIp.trim(),
                            port = portNum
                        )
                        viewModel.connectAndSend(offer)
                    },
                    enabled = targetIp.isNotBlank(),
                    isPrimary = true
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
