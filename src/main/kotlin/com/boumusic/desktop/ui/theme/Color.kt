package com.boumusic.desktop.ui.theme

import androidx.compose.ui.graphics.Color

val DarkBackground: Color
    get() = if (AppThemeState.wallpaperPath != null) {
        AppThemeState.backgroundColor.copy(alpha = 0.45f)
    } else {
        AppThemeState.backgroundColor
    }

val DarkSurface: Color
    get() = if (AppThemeState.wallpaperPath != null) {
        AppThemeState.surfaceColor.copy(alpha = 0.82f)
    } else {
        AppThemeState.surfaceColor
    }

val DarkSurfaceVariant: Color
    get() = if (AppThemeState.wallpaperPath != null) {
        AppThemeState.surfaceVariantColor.copy(alpha = 0.88f)
    } else {
        AppThemeState.surfaceVariantColor
    }

val DarkCardBorder: Color
    get() = AppThemeState.cardBorderColor

val NeonCyan: Color
    get() = AppThemeState.primaryColor

val ElectricPurple: Color
    get() = AppThemeState.secondaryColor

val DeepCyan: Color
    get() = AppThemeState.primaryColor

val TextPrimary: Color
    get() = AppThemeState.textPrimaryColor

val TextSecondary: Color
    get() = AppThemeState.textSecondaryColor

val TextMuted: Color
    get() = AppThemeState.textSecondaryColor.copy(alpha = 0.7f)

val AccentSuccess: Color = Color(0xFF10B981)
val AccentError: Color = Color(0xFFEF4444)
