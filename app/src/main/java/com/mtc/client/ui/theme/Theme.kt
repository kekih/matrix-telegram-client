package com.mtc.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Telegram-inspired colors
val TgBg = Color(0xFF0E1621)
val TgSidebar = Color(0xFF17212B)
val TgChat = Color(0xFF0E1621)
val TgBubbleOut = Color(0xFF2B5278)
val TgBubbleIn = Color(0xFF182533)
val TgAccent = Color(0xFF2AABEE)
val TgTextPrimary = Color(0xFFF5F5F5)
val TgTextSecondary = Color(0xFF7D8B99)
val TgTextMeta = Color(0xFF6D7F8F)
val TgInput = Color(0xFF242F3D)
val TgHover = Color(0xFF232E3C)

private val TelegramDarkColorScheme = darkColorScheme(
    primary = TgAccent,
    onPrimary = Color.White,
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
    darkTheme: Boolean = true, // always dark by default like Telegram
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TelegramDarkColorScheme,
        content = content
    )
}
