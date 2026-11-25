package com.boumusic.desktop.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import java.io.File

@Composable
fun BoumusicDesktopTheme(content: @Composable () -> Unit) {
    val dynamicScheme = darkColorScheme(
        primary = AppThemeState.primaryColor,
        onPrimary = AppThemeState.backgroundColor,
        primaryContainer = AppThemeState.secondaryColor,
        onPrimaryContainer = AppThemeState.textPrimaryColor,
        secondary = AppThemeState.secondaryColor,
        onSecondary = AppThemeState.textPrimaryColor,
        background = AppThemeState.backgroundColor,
        onBackground = AppThemeState.textPrimaryColor,
        surface = AppThemeState.surfaceColor,
        onSurface = AppThemeState.textPrimaryColor,
        surfaceVariant = AppThemeState.surfaceVariantColor,
        onSurfaceVariant = AppThemeState.textSecondaryColor,
        outline = AppThemeState.cardBorderColor
    )

    MaterialTheme(
        colorScheme = dynamicScheme
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppThemeState.backgroundColor)
        ) {
            val wall = AppThemeState.wallpaperPath
            val wallBitmap = remember(wall) {
                if (!wall.isNullOrBlank()) {
                    val f = File(wall)
                    if (f.exists()) {
                        try {
                            f.inputStream().use { loadImageBitmap(it) }
                        } catch (_: Exception) {
                            null
                        }
                    } else null
                } else null
            }

            if (wallBitmap != null) {
                Image(
                    painter = BitmapPainter(wallBitmap),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = AppThemeState.wallpaperDim))
                )
            }
            content()
        }
    }
}
