package dev.tap.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TapColors = darkColorScheme(
    primary = Color(0xFFB7F36B),
    onPrimary = Color(0xFF10140C),
    background = Color(0xFF090B10),
    surface = Color(0xFF141923),
    onSurface = Color(0xFFF4F6F8),
    secondary = Color(0xFF9BA6B5)
)

@Composable
fun TapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TapColors,
        content = content
    )
}
