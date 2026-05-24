package com.splitpay.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val primary: Color,
    val primaryContainer: Color,
    val secondary: Color,
    val tertiary: Color,
    val surface: Color,
    val surfaceLowest: Color,
    val surfaceLow: Color,
    val surfaceHigh: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outlineVariant: Color,
    val isDark: Boolean
)

val LightAppColors = AppColors(
    primary          = Color(0xFF2B348D),
    primaryContainer = Color(0xFF444DA6),
    secondary        = Color(0xFF1B6D24),
    tertiary         = Color(0xFF84000C),
    surface          = Color(0xFFF9F9FC),
    surfaceLowest    = Color(0xFFFFFFFF),
    surfaceLow       = Color(0xFFF3F3F6),
    surfaceHigh      = Color(0xFFE8E8EA),
    onSurface        = Color(0xFF1A1C1E),
    onSurfaceVariant = Color(0xFF3F4949),
    outlineVariant   = Color(0xFFBEC8C9),
    isDark           = false
)

val DarkAppColors = AppColors(
    primary          = Color(0xFF8A93E0),
    primaryContainer = Color(0xFF5D66BB),
    secondary        = Color(0xFF4CAF6A),
    tertiary         = Color(0xFFCF6679),
    surface          = Color(0xFF111318),
    surfaceLowest    = Color(0xFF1C1F26),
    surfaceLow       = Color(0xFF23262E),
    surfaceHigh      = Color(0xFF2C303A),
    onSurface        = Color(0xFFE2E2E9),
    onSurfaceVariant = Color(0xFFC3C8D4),
    outlineVariant   = Color(0xFF44474F),
    isDark           = true
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }
