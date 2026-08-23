package com.athea.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

// Fixed dark identity for the skeleton stage; light theme and dynamic
// color arrive with the theming/settings stage.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF82AAFF),
    onPrimary = Color(0xFF0A1B3D),
    primaryContainer = Color(0xFF1C3A6E),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF9ECEFF),
    onSecondary = Color(0xFF0A1B3D),
    secondaryContainer = Color(0xFF223047),
    onSecondaryContainer = Color(0xFFD6E3FF),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1C2430),
    onSurfaceVariant = Color(0xFF9DA7B3),
    surfaceContainer = Color(0xFF141A22),
    surfaceContainerHigh = Color(0xFF1B222C),
    outline = Color(0xFF30363D),
    error = Color(0xFFF85149),
    onError = Color(0xFF2D0A08),
    errorContainer = Color(0xFF3D1210),
    onErrorContainer = Color(0xFFFFB4AB),
)

/** Monospace style used across transcript, editor and key row. */
val CodeStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 19.sp,
)

val HighlightColor = Color(0xFFBB8009)
val OnHighlightColor = Color(0xFFE6EDF3)

@Composable
fun AtheaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content,
    )
}
