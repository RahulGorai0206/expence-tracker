package com.myapp.expensetracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Dark color scheme for the application.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8FB6FF),
    onPrimary = Color(0xFF061A36),
    primaryContainer = Color(0xFF173861),
    onPrimaryContainer = Color(0xFFD9E6FF),
    secondary = Color(0xFF72D5BF),
    onSecondary = Color(0xFF04231E),
    secondaryContainer = Color(0xFF164D43),
    onSecondaryContainer = Color(0xFFD1F4EA),
    tertiary = Color(0xFFFFC06D),
    onTertiary = Color(0xFF2C1700),
    tertiaryContainer = Color(0xFF5B3A10),
    onTertiaryContainer = Color(0xFFFFE0B2),
    surface = Color(0xFF111417),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF3F4650),
    onSurfaceVariant = Color(0xFFC1C7D0),
    surfaceContainerLowest = Color(0xFF0C0E11),
    surfaceContainerLow = Color(0xFF171A1E),
    surfaceContainer = Color(0xFF1D2227),
    surfaceContainerHigh = Color(0xFF272C33),
    surfaceContainerHighest = Color(0xFF323942),
    background = Color(0xFF0C0E11),
    onBackground = Color(0xFFE6E8EC),
    outline = Color(0xFF8C939E),
    outlineVariant = Color(0xFF424952),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF6E2320),
    onErrorContainer = Color(0xFFFFDAD6)
)

/**
 * Light color scheme for the application.
 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E5F9F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E6FF),
    onPrimaryContainer = Color(0xFF102A4D),
    secondary = Color(0xFF197160),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFEFE6),
    onSecondaryContainer = Color(0xFF06342D),
    tertiary = Color(0xFF9A5B00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDFB0),
    onTertiaryContainer = Color(0xFF3A2100),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF171B20),
    surfaceVariant = Color(0xFFE0E5EC),
    onSurfaceVariant = Color(0xFF4C5662),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F7FA),
    surfaceContainer = Color(0xFFEEF2F6),
    surfaceContainerHigh = Color(0xFFE7ECF2),
    surfaceContainerHighest = Color(0xFFDDE4EC),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF171B20),
    outline = Color(0xFF737D89),
    outlineVariant = Color(0xFFC7D0DA),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

/**
 * Typography configuration for the application.
 */
private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
)

/**
 * The main theme composable for the application.
 *
 * @param darkTheme Whether the theme should be in dark mode. Defaults to the system setting.
 * @param dynamicColor Whether to use dynamic color (Material You) on supported devices (Android 12+).
 * @param content The composable content to be displayed within this theme.
 */
@Composable
fun LedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
                !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
