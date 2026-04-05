package com.myapp.expensetracker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Black, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
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

@Composable
fun LedgerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFFC4D7FF),
            onPrimary = Color(0xFF002F68),
            primaryContainer = Color(0xFF004494),
            onPrimaryContainer = Color(0xFFD9E2FF),
            secondary = Color(0xFF90F7E0),
            onSecondary = Color(0xFF00382E),
            secondaryContainer = Color(0xFF005144),
            onSecondaryContainer = Color(0xFFADFCE9),
            tertiary = Color(0xFFFFB4AB),
            onTertiary = Color(0xFF690005),
            surface = Color(0xFF101317),
            onSurface = Color(0xFFE2E2E6),
            surfaceContainer = Color(0xFF1C1F24),
            surfaceContainerLow = Color(0xFF14171B),
            surfaceContainerHigh = Color(0xFF272A2F),
            background = Color(0xFF0B0D10),
            onBackground = Color(0xFFE2E2E6),
            outline = Color(0xFF8E9099),
            outlineVariant = Color(0xFF44474E),
            error = Color(0xFFFFB4AB),
            errorContainer = Color(0xFF93000A)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF005AC1),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD9E2FF),
            onPrimaryContainer = Color(0xFF001945),
            secondary = Color(0xFF006B5B),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF90F7E0),
            onSecondaryContainer = Color(0xFF00201A),
            tertiary = Color(0xFF9C4141),
            onTertiary = Color.White,
            surface = Color(0xFFF8F9FF),
            onSurface = Color(0xFF191C20),
            surfaceContainer = Color(0xFFEBEDF4),
            surfaceContainerLow = Color(0xFFF1F3F9),
            surfaceContainerHigh = Color(0xFFE1E2E9),
            background = Color(0xFFF5F7FA),
            onBackground = Color(0xFF191C20),
            outline = Color(0xFF74777F),
            outlineVariant = Color(0xFFC4C6D0),
            error = Color(0xFFBA1A1A),
            errorContainer = Color(0xFFFFDAD6)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
