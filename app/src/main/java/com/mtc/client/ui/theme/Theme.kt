package com.mtc.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Element X–inspired dark theme (green accent)
val TgBg = Color(0xFF101317)
val TgSidebar = Color(0xFF181C21)
val TgChat = Color(0xFF101317)
val TgBubbleOut = Color(0xFF133A2E)      // muted green bubble
val TgBubbleIn = Color(0xFF1A1F26)
val TgAccent = Color(0xFF0DBD8B)         // Element green
val TgTextPrimary = Color(0xFFE3E8ED)
val TgTextSecondary = Color(0xFF8E99A4)
val TgTextMeta = Color(0xFF6F7A85)
val TgInput = Color(0xFF242A32)
val TgHover = Color(0xFF2A313A)
val TgDanger = Color(0xFFFF6B6B)
val TgLock = Color(0xFFF5C542)

private val ElementDarkColorScheme = darkColorScheme(
    primary = TgAccent,
    onPrimary = Color.Black,
    secondary = TgAccent,
    background = TgBg,
    surface = TgSidebar,
    onBackground = TgTextPrimary,
    onSurface = TgTextPrimary,
    surfaceVariant = TgInput,
    onSurfaceVariant = TgTextSecondary,
    outline = TgTextMeta,
)

@Composable
fun MatrixTelegramTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ElementDarkColorScheme,
        content = content
    )
}
