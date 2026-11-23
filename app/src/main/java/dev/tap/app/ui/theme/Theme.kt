package dev.tap.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA3E635),       // Refined Sage/Lime
    onPrimary = Color(0xFF0F170A),
    background = Color(0xFF090A0E),    // Deepest Obsidian Slate
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF13151B),       // Minimal Flat Surface
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF1A1D24),
    onSurfaceVariant = Color(0xFF8E939E),
    secondary = Color(0xFF8E939E),
    outline = Color(0xFF222733)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4D7C0F),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF6B7280),
    secondary = Color(0xFF6B7280),
    outline = Color(0xFFE5E7EB)
)

@Composable
fun TapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else DarkColorScheme // Optimized for sleek dark native aesthetic
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TapTypography,
        content = content
    )
}
