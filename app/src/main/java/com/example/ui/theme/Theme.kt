package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SheraDarkColorScheme = darkColorScheme(
    primary = SheraGold,
    onPrimary = Color(0xFF0A0D14),
    primaryContainer = SheraGoldMuted,
    onPrimaryContainer = SheraGoldBright,
    secondary = EmeraldBull,
    onSecondary = Color(0xFF041B13),
    secondaryContainer = EmeraldBullSoft,
    onSecondaryContainer = EmeraldBull,
    tertiary = SheraGoldBright,
    error = CrimsonBear,
    errorContainer = CrimsonBearSoft,
    onError = Color.White,
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianCardBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SheraDarkColorScheme,
        typography = Typography,
        content = content
    )
}
