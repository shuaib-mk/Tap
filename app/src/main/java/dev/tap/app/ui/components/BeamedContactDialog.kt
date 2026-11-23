package dev.tap.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.tap.app.contacts.ContactCard
import dev.tap.app.ui.theme.AnthropicSerifDisplay
import dev.tap.app.ui.theme.AnthropicSerifText

/**
 * Minimalist Apple NameDrop-style Contact Exchange Dialog.
 *
 * Triggered when two TAP devices tap together to beam contacts.
 * Features an expanding fluid ripple halo, spring scale entry,
 * rich digital card display, and a 1-tap "Save to Contacts" action.
 */
@Composable
fun BeamedContactDialog(
    card: ContactCard,
    isTwoWay: Boolean,
    onSaveToContacts: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Fluid halo ripple animation
    val infiniteTransition = rememberInfiniteTransition(label = "namedropRipple")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveScale"
    )
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseOutCubic),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveAlpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = TapPanel,
                border = BorderStroke(1.dp, TapBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Fluid Ripple Beacon & Avatar
                    Box(
                        modifier = Modifier
                            .size(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Expanding fluid wave 1
                        Box(
                            modifier = Modifier
                                .size(95.dp)
                                .scale(waveScale)
                                .clip(CircleShape)
                                .background(TapAccent.copy(alpha = waveAlpha * 0.4f))
                        )

                        // Core Avatar
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(TapAccent, TapAccent.copy(alpha = 0.8f))
                                    )
                                )
                                .border(2.dp, TapInk, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = card.displayName.take(2).uppercase().ifBlank { "TA" },
                                fontFamily = AnthropicSerifDisplay,
                                color = TapInk,
                                fontWeight = FontWeight.Black,
                                fontSize = 28.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Minimal Exchange Status Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isTwoWay) TapAccent.copy(alpha = 0.15f) else TapPanelLight)
                            .border(1.dp, if (isTwoWay) TapAccent.copy(alpha = 0.4f) else TapBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            if (isTwoWay) Icons.Rounded.SyncAlt else Icons.Rounded.ArrowDownward,
                            contentDescription = null,
                            tint = if (isTwoWay) TapAccent else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isTwoWay) "CONTACTS EXCHANGED" else "CONTACT RECEIVED",
                            fontFamily = AnthropicSerifDisplay,
                            color = if (isTwoWay) TapAccent else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Display Name
                    Text(
                        text = card.displayName.ifBlank { "Tap Contact" },
                        fontFamily = AnthropicSerifDisplay,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        textAlign = TextAlign.Center
                    )

                    // Job Title & Company
                    val subtitle = listOf(card.jobTitle, card.company).filter { it.isNotBlank() }.joinToString(" • ")
                    if (subtitle.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            fontFamily = AnthropicSerifText,
                            color = TapMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = TapBorder)
                    Spacer(Modifier.height(16.dp))

                    // Contact Details
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (card.phone.isNotBlank()) {
                            ContactItemRow(
                                icon = Icons.Rounded.Phone,
                                label = "Phone",
                                value = card.phone,
                                onAction = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${card.phone}"))
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                }
                            )
                        }

                        if (card.email.isNotBlank()) {
                            ContactItemRow(
                                icon = Icons.Rounded.Email,
                                label = "Email",
                                value = card.email,
                                onAction = {
                                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${card.email}"))
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                }
                            )
                        }

                        if (card.website.isNotBlank()) {
                            ContactItemRow(
                                icon = Icons.Rounded.Language,
                                label = "Website",
                                value = card.website,
                                onAction = {
                                    val url = if (!card.website.startsWith("http")) "https://${card.website}" else card.website
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                }
                            )
                        }

                        if (card.notes.isNotBlank()) {
                            ContactItemRow(
                                icon = Icons.Rounded.Notes,
                                label = "Note",
                                value = card.notes,
                                onAction = null
                            )
                        }
                    }

                    Spacer(Modifier.height(26.dp))

                    // Bottom Action Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, TapBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Dismiss", fontFamily = AnthropicSerifText, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                onSaveToContacts()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TapAccent, contentColor = TapInk),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Save to Contacts",
                                fontFamily = AnthropicSerifDisplay,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactItemRow(
    icon: ImageVector,
    label: String,
    value: String,
    onAction: (() -> Unit)?
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = TapPanelLight,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onAction != null) Modifier.clickable { onAction() } else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(TapPanel),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = TapAccent, modifier = Modifier.size(15.dp))
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontFamily = AnthropicSerifText,
                    color = TapMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    fontFamily = AnthropicSerifText,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = "Copy",
                    tint = TapMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
