package com.pinta.phone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---- P1 phosphor surfaces ------------------------------------------------
val Ink = Color(0xFF030805)
val SurfaceLow = Color(0xFF07110B)
val SurfaceContainer = Color(0xFF0A150E)
val SurfaceHigh = Color(0xFF102117)
val SurfaceHighest = Color(0xFF162C1D)

// ---- Phosphor accents ----------------------------------------------------
val Phosphor = Color(0xFF4DFF7C)
val PhosphorHot = Color(0xFFB6FFC9)
val PhosphorDim = Color(0xFF2E8F55)
val Amber = Color(0xFFFFB020)
val Danger = Color(0xFFFF4D4D)

// Legacy names used by the other screens; keep the app coherent while those
// pages are migrated to the same token names.
val Orange = Phosphor
val OrangeSoft = PhosphorHot
val Mint = Color(0xFF8CFFB0)

// ---- Text ----------------------------------------------------------------
val TextPrimary = Color(0xFFCFFFE0)
val TextDim = Color(0xFF7FAE8D)
val TextFaint = Color(0xFF5E8C70)

// A green-tinted hairline reads as a phosphor grid, not a white divider.
val Hairline = Color(0x1F58FF8C)
val HairlineStrong = Color(0x3858FF8C)

val Mono = FontFamily.Monospace
val Sans = FontFamily.SansSerif

private val PintaScheme = darkColorScheme(
    primary = Phosphor,
    onPrimary = Color.Black,
    primaryContainer = SurfaceHigh,
    onPrimaryContainer = PhosphorHot,
    secondary = Mint,
    onSecondary = Color.Black,
    background = Ink,
    onBackground = TextPrimary,
    surface = SurfaceContainer,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextDim,
    outline = TextFaint,
    error = Danger,
    onError = Color.Black,
    surfaceTint = Color.Transparent,
)

// v0.2 density scale: 26 / 18 / 15 / 12 / 10 / 9.
val PintaTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        letterSpacing = (-0.4).sp,
        fontFeatureSettings = "tnum",
    ),
    headlineMedium = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = "tnum",
    ),
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 0.6.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 9.sp,
        letterSpacing = 1.0.sp,
    ),
)

@Composable
fun PintaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PintaScheme,
        typography = PintaTypography,
        content = content,
    )
}
