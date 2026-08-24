package com.athea.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/*
 * Chat-like identity: pure black canvas, two grays, near-white text.
 * Many Material roles intentionally share one value - fewer colors,
 * calmer screen.
 *
 *   #000000  canvas (background, surface)
 *   #141414  quiet containers (drawer, key row)
 *   #2F2F2F  raised containers (bubbles, chips, composer, selection)
 *   #ECECEC  primary text
 *   #9B9B9B  muted text and icons
 *   #FFFFFF  accent (send button, cursor, live dot)
 *   #FF453A  errors
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF2F2F2F),
    onPrimaryContainer = Color(0xFFECECEC),
    secondary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF2F2F2F),
    onSecondaryContainer = Color(0xFFECECEC),
    background = Color(0xFF000000),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF2F2F2F),
    onSurfaceVariant = Color(0xFF9B9B9B),
    surfaceContainer = Color(0xFF141414),
    surfaceContainerHigh = Color(0xFF2F2F2F),
    outline = Color(0xFF3A3A3C),
    error = Color(0xFFE5484D),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF2F2F2F),
    onErrorContainer = Color(0xFFE5484D),
)

/** Monospace style for terminal output, editor and key row. */
val CodeStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 19.sp,
)

/** Output font size chosen in settings; defaults match [CodeStyle]. */
val LocalOutputFontSize = staticCompositionLocalOf { 13 }

/** [CodeStyle] honoring the user-selected output font size. */
@Composable
fun codeStyle(): TextStyle {
    val size = LocalOutputFontSize.current
    return CodeStyle.copy(fontSize = size.sp, lineHeight = (size * 1.45f).sp)
}

/** Chat-message style for command bubbles and the composer, like chat apps. */
val MessageStyle = TextStyle(
    fontSize = 16.sp,
    lineHeight = 22.sp,
)

/** Message font size chosen in settings; defaults match [MessageStyle]. */
val LocalMessageFontSize = staticCompositionLocalOf { 16 }

/** [MessageStyle] honoring the user-selected message font size. */
@Composable
fun messageStyle(): TextStyle {
    val size = LocalMessageFontSize.current
    return MessageStyle.copy(fontSize = size.sp, lineHeight = (size * 1.375f).sp)
}

val HighlightColor = Color(0xFFBB8009)
val OnHighlightColor = Color(0xFFECECEC)

@Composable
fun AtheaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content,
    )
}
