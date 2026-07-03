package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    secondary = NeonEmerald,
    tertiary = ElectricBlue,
    background = CharcoalBg,
    surface = DeepNavySurface,
    surfaceVariant = CardSlate,
    onPrimary = Color(0xFF0F1115), // Dark text on gold button for readability
    onSecondary = CharcoalBg,
    onTertiary = CharcoalBg,
    onBackground = SlateTextPrimary,
    onSurface = SlateTextPrimary,
    onSurfaceVariant = SlateTextSecondary,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB), // Vibrant Telegram Blue
    secondary = Color(0xFF10B981), // Emerald Green
    tertiary = Color(0xFF8B5CF6), // Royal Violet
    background = Color(0xFFF1F5F9), // Clean Light Slate
    surface = Color(0xFFFFFFFF), // Pure White Surface
    surfaceVariant = Color(0xFFE2E8F0), // Light Slate Cards
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0F172A), // Deep Slate Text
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569), // Muted Slate Text
    error = Color(0xFFEF4444)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep branding consistent instead of standard system wallpapers
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
