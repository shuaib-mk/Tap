package dev.tap.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tap.app.navigation.TapScreen
import dev.tap.app.transfer.SelectedFile
import dev.tap.app.ui.theme.AnthropicSerifDisplay
import dev.tap.app.ui.theme.AnthropicSerifText

// MetroList-inspired refined palette
val TapInk = Color(0xFF090A0E)
val TapPanel = Color(0xFF13151B)
val TapPanelLight = Color(0xFF1A1D24)
val TapMuted = Color(0xFF8E939E)
val TapAccent = Color(0xFFA3E635)
val TapBorder = Color(0xFF1F242E)
val TapRed = Color(0xFFF87171)
val TapGreen = Color(0xFF4ADE80)

@Composable
fun TapTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(TapPanel)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = AnthropicSerifDisplay,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontFamily = AnthropicSerifText,
                    color = TapMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

@Composable
fun StatusChip(
    label: String,
    active: Boolean = true,
    accentColor: Color = TapAccent
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) accentColor.copy(alpha = 0.12f) else TapPanelLight)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (active) accentColor else TapMuted)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = if (active) Color.White else TapMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Clean, typography-led bottom navigation bar (MetroList style).
 * Keeps navigation focused on 4 core areas:
 * 1. Share (Home)
 * 2. Activity (History)
 * 3. Identity (Contacts)
 * 4. Settings
 */
@Composable
fun MetroBottomBar(
    currentScreen: TapScreen,
    onNavigate: (TapScreen) -> Unit
) {
    val items = listOf(
        Triple("Share", Icons.Rounded.SwapVert, TapScreen.Home),
        Triple("Activity", Icons.Rounded.ReceiptLong, TapScreen.History),
        Triple("Identity", Icons.Rounded.Badge, TapScreen.ContactCard),
        Triple("Settings", Icons.Rounded.Tune, TapScreen.Settings)
    )

    Surface(
        color = TapPanel,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (label, icon, destination) ->
                val selected = when (destination) {
                    is TapScreen.Home -> currentScreen is TapScreen.Home
                    is TapScreen.History -> currentScreen is TapScreen.History || currentScreen is TapScreen.Activity
                    is TapScreen.ContactCard -> currentScreen is TapScreen.ContactCard
                    is TapScreen.Settings -> currentScreen is TapScreen.Settings || currentScreen is TapScreen.CompatibilityDiagnostics
                    else -> currentScreen == destination
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onNavigate(destination) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (selected) TapAccent else TapMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = label,
                        fontFamily = AnthropicSerifDisplay,
                        color = if (selected) Color.White else TapMuted,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun TapButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) TapAccent else TapPanel,
            contentColor = if (isPrimary) TapInk else Color.White,
            disabledContainerColor = TapPanel.copy(alpha = 0.5f),
            disabledContentColor = TapMuted
        )
    ) {
        Text(
            text = text,
            fontFamily = AnthropicSerifDisplay,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun PulsingNfcCircle(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (active) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .size(240.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        Color(0xFF293B27),
                        Color(0xFF151D19),
                        TapInk
                    )
                )
            )
            .border(1.dp, Color(0xFF354B31), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(174.dp)
                .border(1.dp, Color(0x665D824C), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(TapAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Nfc,
                    contentDescription = "NFC",
                    tint = TapInk,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
    }
}

@Composable
fun FileCard(
    file: SelectedFile,
    onRemove: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TapPanel)
            .border(1.dp, TapBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when {
            file.mimeType.startsWith("image/") -> Icons.Rounded.Image
            file.mimeType.startsWith("video/") -> Icons.Rounded.VideoFile
            file.mimeType.startsWith("audio/") -> Icons.Rounded.AudioFile
            file.mimeType.contains("pdf") -> Icons.Rounded.PictureAsPdf
            file.mimeType.contains("vcard") -> Icons.Rounded.ContactPage
            file.mimeType.contains("uri") -> Icons.Rounded.Link
            else -> Icons.Rounded.InsertDriveFile
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TapPanelLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TapAccent, modifier = Modifier.size(22.dp))
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = file.formattedSize,
                color = TapMuted,
                fontSize = 12.sp
            )
        }

        if (onRemove != null) {
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove",
                    tint = TapMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ActionTile(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(78.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, TapBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = TapPanel,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(6.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = TapAccent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

