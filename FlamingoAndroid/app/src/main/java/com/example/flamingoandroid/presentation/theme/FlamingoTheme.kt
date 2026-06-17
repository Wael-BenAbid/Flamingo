package com.example.flamingoandroid.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme

// ── Palette Flamingo ────────────────────────────────────────────────────────
private object FlamingoColors {
    // Light palette
    val FlamingoRose        = Color(0xFFFF7A85)   // Primary — Rose Flamingo
    val FlamingoRoseDark    = Color(0xFFE05A6A)   // Primary pressed
    val FlamingoRoseLight   = Color(0xFFFFE4E7)   // Primary container
    val Navy                = Color(0xFF1A365D)   // Secondary — Bleu Marine
    val NavyLight           = Color(0xFFD1E0F5)   // Secondary container
    val GoldSun             = Color(0xFFFCD34D)   // Tertiary — Or Soleil
    val GoldLight           = Color(0xFFFFF3C0)   // Tertiary container
    val SandWarm            = Color(0xFFFAFAF9)   // Background
    val SurfaceWhite        = Color(0xFFFFFFFF)   // Surface
    val SurfaceTinted       = Color(0xFFFFF0F1)   // Surface variant
    val OnNavy              = Color(0xFFFFFFFF)   // Text on navy
    val NavyText            = Color(0xFF1A365D)   // Text color (light mode)
    val SlateText           = Color(0xFF4A5568)   // Secondary text
    val RoseBorder          = Color(0xFFE8B4B8)   // Outline
    val ErrorRed            = Color(0xFFE63946)

    // Dark palette
    val DarkBackground      = Color(0xFF0E1C2E)   // Nuit marine profonde
    val DarkSurface         = Color(0xFF1A2E42)   // Surface sombre
    val DarkSurfaceVariant  = Color(0xFF243852)   // Surface variante
    val DarkOutline         = Color(0xFF2E4A6A)   // Bordure sombre
    val PearlWhite          = Color(0xFFF7F7F6)   // Texte clair
    val MistGray            = Color(0xFF8FA3B5)   // Texte secondaire clair
    val NavyMedium          = Color(0xFF3A6099)   // Secondary en dark mode
}

// ── Light Color Scheme — Flamingo ───────────────────────────────────────────
private val FlamingoLightColors: ColorScheme = lightColorScheme(
    primary                 = FlamingoColors.FlamingoRose,
    onPrimary               = Color.White,
    primaryContainer        = FlamingoColors.FlamingoRoseLight,
    onPrimaryContainer      = Color(0xFF3D0010),

    secondary               = FlamingoColors.Navy,
    onSecondary             = Color.White,
    secondaryContainer      = FlamingoColors.NavyLight,
    onSecondaryContainer    = Color(0xFF001020),

    tertiary                = FlamingoColors.GoldSun,
    onTertiary              = Color(0xFF1A1000),
    tertiaryContainer       = FlamingoColors.GoldLight,
    onTertiaryContainer     = Color(0xFF2D2200),

    background              = FlamingoColors.SandWarm,
    onBackground            = FlamingoColors.NavyText,

    surface                 = FlamingoColors.SurfaceWhite,
    onSurface               = FlamingoColors.NavyText,
    surfaceVariant          = FlamingoColors.SurfaceTinted,
    onSurfaceVariant        = FlamingoColors.SlateText,

    outline                 = FlamingoColors.RoseBorder,
    outlineVariant          = Color(0xFFF5D5D8),

    error                   = FlamingoColors.ErrorRed,
    onError                 = Color.White,
    errorContainer          = Color(0xFFFFD9DC),
    onErrorContainer        = Color(0xFF3D0008),

    inverseSurface          = FlamingoColors.Navy,
    inverseOnSurface        = Color.White,
    inversePrimary          = FlamingoColors.FlamingoRoseLight,
)

// ── Dark Color Scheme — Flamingo Night ──────────────────────────────────────
private val FlamingoDarkColors: ColorScheme = darkColorScheme(
    primary                 = FlamingoColors.FlamingoRose,
    onPrimary               = Color.White,
    primaryContainer        = Color(0xFF5C0020),
    onPrimaryContainer      = FlamingoColors.FlamingoRoseLight,

    secondary               = FlamingoColors.NavyMedium,
    onSecondary             = Color.White,
    secondaryContainer      = FlamingoColors.Navy,
    onSecondaryContainer    = FlamingoColors.NavyLight,

    tertiary                = FlamingoColors.GoldSun,
    onTertiary              = Color(0xFF1A1000),
    tertiaryContainer       = Color(0xFF3D2E00),
    onTertiaryContainer     = FlamingoColors.GoldLight,

    background              = FlamingoColors.DarkBackground,
    onBackground            = FlamingoColors.PearlWhite,

    surface                 = FlamingoColors.DarkSurface,
    onSurface               = FlamingoColors.PearlWhite,
    surfaceVariant          = FlamingoColors.DarkSurfaceVariant,
    onSurfaceVariant        = FlamingoColors.MistGray,

    outline                 = FlamingoColors.DarkOutline,
    outlineVariant          = Color(0xFF1E3A54),

    error                   = FlamingoColors.ErrorRed,
    onError                 = Color.White,
    errorContainer          = Color(0xFF4A0010),
    onErrorContainer        = FlamingoColors.ErrorRed,

    inverseSurface          = FlamingoColors.PearlWhite,
    inverseOnSurface        = FlamingoColors.DarkBackground,
    inversePrimary          = FlamingoColors.FlamingoRoseDark,
)

// ── Theme Composable ────────────────────────────────────────────────────────
@Composable
fun FlamingoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) FlamingoDarkColors else FlamingoLightColors

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
