package com.silpa.core.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Brand colors taken from the Silpa icon. Used only as accents; the studio UI stays neutral. */
object SilpaColors {
    val Cobalt = Color(0xFF1F3FD1)
    val Sky = Color(0xFF4CB4E6)
    val Coral = Color(0xFFF28B6B)
    /** Mid-dark neutral grey behind the canvas so it doesn't bias color perception. */
    val CanvasBackdrop = Color(0xFF2A2B2F)
    val Paper = Color(0xFFFFFFFF)
}

/** 4dp/8dp spacing grid. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Minimum touch target from the Android accessibility guide. */
val MinTouchTarget = 48.dp

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB8C4FF),
    onPrimary = Color(0xFF0B1F87),
    primaryContainer = SilpaColors.Cobalt,
    onPrimaryContainer = Color(0xFFE2E6FF),
    secondary = Color(0xFF8CCFF2),
    onSecondary = Color(0xFF00344B),
    secondaryContainer = Color(0xFF0E4D6B),
    onSecondaryContainer = Color(0xFFC9E8FF),
    tertiary = Color(0xFFFFB59E),
    onTertiary = Color(0xFF5C1A06),
    tertiaryContainer = Color(0xFF7D2D14),
    onTertiaryContainer = Color(0xFFFFDBD0),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE4E4E8),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE4E4E8),
    surfaceVariant = Color(0xFF2E3036),
    onSurfaceVariant = Color(0xFFC3C5CE),
    outline = Color(0xFF8D9099),
    outlineVariant = Color(0xFF43464E),
    surfaceContainerLowest = Color(0xFF0D0E10),
    surfaceContainerLow = Color(0xFF1A1B1E),
    surfaceContainer = Color(0xFF1E1F23),
    surfaceContainerHigh = Color(0xFF282A2E),
    surfaceContainerHighest = Color(0xFF333539),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = SilpaColors.Cobalt,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2FF),
    onPrimaryContainer = Color(0xFF001257),
    secondary = Color(0xFF00658D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC6E7FF),
    onSecondaryContainer = Color(0xFF001E2D),
    tertiary = Color(0xFF9A4428),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBD0),
    onTertiaryContainer = Color(0xFF3A0B00),
    background = Color(0xFFF7F7FA),
    onBackground = Color(0xFF1A1B1F),
    surface = Color(0xFFF7F7FA),
    onSurface = Color(0xFF1A1B1F),
    surfaceVariant = Color(0xFFE1E2EA),
    onSurfaceVariant = Color(0xFF44464E),
    outline = Color(0xFF75777F),
    outlineVariant = Color(0xFFC5C6CF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F1F5),
    surfaceContainer = Color(0xFFEBEBF0),
    surfaceContainerHigh = Color(0xFFE5E5EA),
    surfaceContainerHighest = Color(0xFFDFDFE4),
)

private val SilpaTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val SilpaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Dark by default; the light theme becomes a user setting later. */
@Composable
fun SilpaTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = SilpaTypography,
        shapes = SilpaShapes,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides (if (darkTheme) DarkScheme.onBackground else LightScheme.onBackground),
            content = content,
        )
    }
}
