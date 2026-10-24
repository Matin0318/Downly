package app.downly.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Mint -> sky gradient used by the Add button, the logo tile and highlights. */
val AccentFrom = Color(0xFF2DD4BF)
val AccentTo = Color(0xFF38BDF8)
val AccentBrush: Brush get() = Brush.linearGradient(listOf(AccentFrom, AccentTo))

/** Text and icons that sit on top of the accent gradient. */
val OnAccent = Color(0xFF00241D)

/** Spacing scale used by every screen, so margins look the same everywhere. */
object Dimens {
    val ScreenPadding = 16.dp
    val CardPadding = 16.dp
    val Gap = 12.dp
    val CardRadius = 22.dp
}

/** How much room the window has. Phones are Compact; tablets, foldables and landscape phones are wider. */
enum class WindowSize { Compact, Medium, Expanded }

val LocalWindowSize = compositionLocalOf { WindowSize.Compact }

fun windowSizeFor(width: Dp): WindowSize = when {
    width < 600.dp -> WindowSize.Compact
    width < 840.dp -> WindowSize.Medium
    else -> WindowSize.Expanded
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF007F6B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4F3E9),
    onPrimaryContainer = Color(0xFF00382E),
    secondary = Color(0xFF1F6FC4),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E8FB),
    onSecondaryContainer = Color(0xFF0A2F57),
    tertiary = Color(0xFFB26A00),
    background = Color(0xFFF2F6F9),
    onBackground = Color(0xFF0E1620),
    surface = Color(0xFFF2F6F9),
    onSurface = Color(0xFF0E1620),
    surfaceVariant = Color(0xFFE2E9EF),
    onSurfaceVariant = Color(0xFF52616E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FBFD),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEBF1F5),
    surfaceContainerHighest = Color(0xFFDFE7ED),
    outline = Color(0xFF8593A0),
    outlineVariant = Color(0xFFD5DEE5),
    error = Color(0xFFD93A3A),
    errorContainer = Color(0xFFFFDAD8),
    onErrorContainer = Color(0xFF5A0F12),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3DDAC0),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF0E4A40),
    onPrimaryContainer = Color(0xFFB8F5E8),
    secondary = Color(0xFF72B9FF),
    onSecondary = Color(0xFF00315A),
    secondaryContainer = Color(0xFF173F6A),
    onSecondaryContainer = Color(0xFFD3E8FF),
    tertiary = Color(0xFFFFB454),
    background = Color(0xFF070B11),
    onBackground = Color(0xFFE8EEF4),
    surface = Color(0xFF070B11),
    onSurface = Color(0xFFE8EEF4),
    surfaceVariant = Color(0xFF202B38),
    onSurfaceVariant = Color(0xFF9CAAB8),
    surfaceContainerLowest = Color(0xFF05080C),
    surfaceContainerLow = Color(0xFF0C1219),
    surfaceContainer = Color(0xFF111922),
    surfaceContainerHigh = Color(0xFF18212C),
    surfaceContainerHighest = Color(0xFF202B38),
    outline = Color(0xFF3B4959),
    outlineVariant = Color(0xFF222E3C),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF5A1F22),
    onErrorContainer = Color(0xFFFFDAD8),
)

private val DownlyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// No letter spacing anywhere: it would break the joined letters of Persian text.
private val DownlyTypography = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun DownlyTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = DownlyTypography,
        shapes = DownlyShapes,
        content = content,
    )
}
