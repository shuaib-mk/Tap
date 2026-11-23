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
import dev.tap.app.transfer.SelectedFile

val TapInk = Color(0xFF090B10)
val TapPanel = Color(0xFF141923)
val TapPanelLight = Color(0xFF1C2331)
val TapMuted = Color(0xFF9BA6B5)
val TapAccent = Color(0xFFB7F36B)
val TapBorder = Color(0xFF28303D)
val TapRed = Color(0xFFFF5252)

@Composable
fun TapTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
fun StatusChip(label: String, active: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Color(0x22B7F36B) else Color(0x229BA6B5))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (active) TapAccent else TapMuted)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = if (active) TapAccent else TapMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
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
                .background(Color(0x22B7F36B)),
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
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPrimary) TapAccent else TapPanel,
            contentColor = if (isPrimary) TapInk else Color.White,
            disabledContainerColor = TapPanel.copy(alpha = 0.5f),
            disabledContentColor = TapMuted
        )
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
