package com.boumusic.desktop.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File

data class ThemePreset(
    val id: String,
    val name: String,
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val cardBorder: Color
)

object AppThemeState {
    var primaryColor by mutableStateOf(Color(0xFF00F5D4))
    var secondaryColor by mutableStateOf(Color(0xFF7B2CBF))
    var backgroundColor by mutableStateOf(Color(0xFF0D0F14))
    var surfaceColor by mutableStateOf(Color(0xFF161922))
    var surfaceVariantColor by mutableStateOf(Color(0xFF202430))
    var cardBorderColor by mutableStateOf(Color(0xFF2A3040))
    var textPrimaryColor by mutableStateOf(Color(0xFFF0F4F8))
    var textSecondaryColor by mutableStateOf(Color(0xFF94A3B8))

    var wallpaperPath by mutableStateOf<String?>(null)
    var wallpaperDim by mutableFloatStateOf(0.55f)
    var animationsEnabled by mutableStateOf(true)
    var currentPresetId by mutableStateOf("CYBERPUNK")
}

object DesktopThemeManager {
    private val configFile: File by lazy {
        val userHome = System.getProperty("user.home") ?: "."
        val dir = File(userHome, ".boumusic").apply { if (!exists()) mkdirs() }
        File(dir, "theme_config.json")
    }

    val PRESETS = listOf(
        ThemePreset(
            id = "CYBERPUNK",
            name = "Cyberpunk Neón",
            primary = Color(0xFF00F5D4),
            secondary = Color(0xFF7B2CBF),
            background = Color(0xFF0D0F14),
            surface = Color(0xFF161922),
            surfaceVariant = Color(0xFF202430),
            cardBorder = Color(0xFF2A3040)
        ),
        ThemePreset(
            id = "OLED",
            name = "OLED Negro Puro",
            primary = Color(0xFF00F5D4),
            secondary = Color(0xFF7B2CBF),
            background = Color(0xFF000000),
            surface = Color(0xFF0A0C10),
            surfaceVariant = Color(0xFF141820),
            cardBorder = Color(0xFF1E2330)
        ),
        ThemePreset(
            id = "EMERALD",
            name = "Esmeralda Synth",
            primary = Color(0xFF00E676),
            secondary = Color(0xFF00B0FF),
            background = Color(0xFF09140E),
            surface = Color(0xFF102117),
            surfaceVariant = Color(0xFF183022),
            cardBorder = Color(0xFF254734)
        ),
        ThemePreset(
            id = "SUNSET",
            name = "Vaporwave Rosa",
            primary = Color(0xFFFF4081),
            secondary = Color(0xFFFF9100),
            background = Color(0xFF140A13),
            surface = Color(0xFF20101E),
            surfaceVariant = Color(0xFF2D172A),
            cardBorder = Color(0xFF3F213B)
        ),
        ThemePreset(
            id = "MIDNIGHT",
            name = "Azul Medianoche",
            primary = Color(0xFF2979FF),
            secondary = Color(0xFF00F5D4),
            background = Color(0xFF080D1A),
            surface = Color(0xFF0F172E),
            surfaceVariant = Color(0xFF162242),
            cardBorder = Color(0xFF22325E)
        ),
        ThemePreset(
            id = "CRIMSON",
            name = "Rojo Carmesí",
            primary = Color(0xFFFF1744),
            secondary = Color(0xFFD500F9),
            background = Color(0xFF14080A),
            surface = Color(0xFF200E11),
            surfaceVariant = Color(0xFF2E1519),
            cardBorder = Color(0xFF421F25)
        ),
        ThemePreset(
            id = "SOLAR",
            name = "Ámbar Dorado",
            primary = Color(0xFFFFD600),
            secondary = Color(0xFFFF6D00),
            background = Color(0xFF141108),
            surface = Color(0xFF211D0E),
            surfaceVariant = Color(0xFF2F2914),
            cardBorder = Color(0xFF423A1D)
        )
    )

    fun init() {
        try {
            if (!configFile.exists()) return
            val text = configFile.readText()
            val json = org.json.JSONObject(text)

            val presetId = json.optString("preset_id", "CYBERPUNK")
            AppThemeState.currentPresetId = presetId

            val preset = PRESETS.firstOrNull { it.id == presetId }
            if (preset != null && presetId != "CUSTOM") {
                applyPresetInternal(preset)
            } else {
                val p = json.optLong("primary_color", Color(0xFF00F5D4).toArgb().toLong()).toInt()
                val s = json.optLong("secondary_color", Color(0xFF7B2CBF).toArgb().toLong()).toInt()
                val b = json.optLong("background_color", Color(0xFF0D0F14).toArgb().toLong()).toInt()
                val surf = json.optLong("surface_color", Color(0xFF161922).toArgb().toLong()).toInt()
                val surfVar = json.optLong("surface_var_color", Color(0xFF202430).toArgb().toLong()).toInt()
                val border = json.optLong("card_border_color", Color(0xFF2A3040).toArgb().toLong()).toInt()

                AppThemeState.primaryColor = Color(p)
                AppThemeState.secondaryColor = Color(s)
                AppThemeState.backgroundColor = Color(b)
                AppThemeState.surfaceColor = Color(surf)
                AppThemeState.surfaceVariantColor = Color(surfVar)
                AppThemeState.cardBorderColor = Color(border)
            }

            val wallPath = json.optString("wallpaper_path", "")
            if (wallPath.isNotBlank() && File(wallPath).exists()) {
                AppThemeState.wallpaperPath = wallPath
            } else {
                AppThemeState.wallpaperPath = null
            }

            AppThemeState.wallpaperDim = json.optDouble("wallpaper_dim", 0.55).toFloat()
            AppThemeState.animationsEnabled = json.optBoolean("animations_enabled", true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyPresetInternal(preset: ThemePreset) {
        AppThemeState.primaryColor = preset.primary
        AppThemeState.secondaryColor = preset.secondary
        AppThemeState.backgroundColor = preset.background
        AppThemeState.surfaceColor = preset.surface
        AppThemeState.surfaceVariantColor = preset.surfaceVariant
        AppThemeState.cardBorderColor = preset.cardBorder
    }

    fun applyPreset(preset: ThemePreset) {
        AppThemeState.currentPresetId = preset.id
        applyPresetInternal(preset)
        save()
    }

    fun applyCustomColors(primary: Color, secondary: Color, background: Color) {
        AppThemeState.currentPresetId = "CUSTOM"
        AppThemeState.primaryColor = primary
        AppThemeState.secondaryColor = secondary
        AppThemeState.backgroundColor = background

        val isPureBlack = background == Color(0xFF000000)
        val surf = if (isPureBlack) Color(0xFF0A0C10) else Color(
            (background.red * 1.35f).coerceAtMost(1f),
            (background.green * 1.35f).coerceAtMost(1f),
            (background.blue * 1.35f).coerceAtMost(1f),
            1f
        )
        val surfVar = if (isPureBlack) Color(0xFF141820) else Color(
            (background.red * 1.7f).coerceAtMost(1f),
            (background.green * 1.7f).coerceAtMost(1f),
            (background.blue * 1.7f).coerceAtMost(1f),
            1f
        )
        val border = if (isPureBlack) Color(0xFF1E2330) else Color(
            (background.red * 2.1f).coerceAtMost(1f),
            (background.green * 2.1f).coerceAtMost(1f),
            (background.blue * 2.1f).coerceAtMost(1f),
            1f
        )

        AppThemeState.surfaceColor = surf
        AppThemeState.surfaceVariantColor = surfVar
        AppThemeState.cardBorderColor = border
        save()
    }

    fun setWallpaper(file: File?) {
        if (file != null && file.exists()) {
            AppThemeState.wallpaperPath = file.absolutePath
        } else {
            AppThemeState.wallpaperPath = null
        }
        save()
    }

    fun clearWallpaper() {
        AppThemeState.wallpaperPath = null
        save()
    }

    fun setWallpaperDim(dim: Float) {
        AppThemeState.wallpaperDim = dim.coerceIn(0.15f, 0.95f)
        save()
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        AppThemeState.animationsEnabled = enabled
        save()
    }

    fun resetToDefaults() {
        clearWallpaper()
        setWallpaperDim(0.55f)
        setAnimationsEnabled(true)
        val defaultPreset = PRESETS.first()
        applyPreset(defaultPreset)
    }

    private fun save() {
        try {
            val json = org.json.JSONObject()
            json.put("preset_id", AppThemeState.currentPresetId)
            json.put("primary_color", AppThemeState.primaryColor.toArgb())
            json.put("secondary_color", AppThemeState.secondaryColor.toArgb())
            json.put("background_color", AppThemeState.backgroundColor.toArgb())
            json.put("surface_color", AppThemeState.surfaceColor.toArgb())
            json.put("surface_var_color", AppThemeState.surfaceVariantColor.toArgb())
            json.put("card_border_color", AppThemeState.cardBorderColor.toArgb())
            json.put("wallpaper_path", AppThemeState.wallpaperPath ?: "")
            json.put("wallpaper_dim", AppThemeState.wallpaperDim.toDouble())
            json.put("animations_enabled", AppThemeState.animationsEnabled)

            configFile.writeText(json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
