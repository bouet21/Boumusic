package com.boumusic.desktop.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boumusic.desktop.ui.theme.AppThemeState
import com.boumusic.desktop.ui.theme.DarkBackground
import com.boumusic.desktop.ui.theme.DarkCardBorder
import com.boumusic.desktop.ui.theme.DarkSurface
import com.boumusic.desktop.ui.theme.DarkSurfaceVariant
import com.boumusic.desktop.ui.theme.DesktopThemeManager
import com.boumusic.desktop.ui.theme.ElectricPurple
import com.boumusic.desktop.ui.theme.NeonCyan
import com.boumusic.desktop.ui.theme.TextPrimary
import com.boumusic.desktop.ui.theme.TextSecondary
import com.boumusic.desktop.viewmodel.DesktopMusicViewModel
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

@Composable
fun CustomizationScreen(
    viewModel: DesktopMusicViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Cabecera
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(NeonCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Personalización de Interfaz",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = TextPrimary
                )
                Text(
                    text = "Adapta los colores, paletas, fondo de pantalla y efectos visuales a tu estilo",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECCIÓN 1: TEMAS PREDEFINIDOS
        SectionTitle("TEMAS DE COLOR PREDEFINIDOS", Icons.Default.Palette)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Mostrar en filas de a 2
            val presets = DesktopThemeManager.PRESETS
            for (i in presets.indices step 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val p1 = presets[i]
                    Box(modifier = Modifier.weight(1f)) {
                        PresetCard(preset = p1)
                    }

                    if (i + 1 < presets.size) {
                        val p2 = presets[i + 1]
                        Box(modifier = Modifier.weight(1f)) {
                            PresetCard(preset = p2)
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECCIÓN 2: PALETA LIBRE PERSONALIZADA
        SectionTitle("PALETA LIBRE PERSONALIZADA", Icons.Default.ColorLens)

        val vibrantColors = listOf(
            Color(0xFF00F5D4), // Cyan Neón
            Color(0xFF00E676), // Verde Esmeralda
            Color(0xFF76FF03), // Lima
            Color(0xFF00B0FF), // Azul Cielo
            Color(0xFF2979FF), // Azul Eléctrico
            Color(0xFF7B2CBF), // Púrpura Intenso
            Color(0xFFFF4081), // Rosa Neón
            Color(0xFFFF1744), // Rojo
            Color(0xFFFF9100), // Naranja
            Color(0xFFFFD600)  // Oro
        )

        val backgroundOptions = listOf(
            Color(0xFF000000) to "OLED Negro",
            Color(0xFF0D0F14) to "Cyber Dark",
            Color(0xFF080D1A) to "Azul Medianoche",
            Color(0xFF140A13) to "Vaporwave",
            Color(0xFF09140E) to "Esmeralda",
            Color(0xFF15100C) to "Café Profundo"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Text("Color Primario (Acento de botones, sliders y badges):", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                vibrantColors.forEach { c ->
                    DesktopColorChip(
                        color = c,
                        isSelected = AppThemeState.primaryColor == c,
                        onClick = {
                            DesktopThemeManager.applyCustomColors(c, AppThemeState.secondaryColor, AppThemeState.backgroundColor)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text("Color Secundario (Gradientes y acentos secundarios):", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                vibrantColors.reversed().forEach { c ->
                    DesktopColorChip(
                        color = c,
                        isSelected = AppThemeState.secondaryColor == c,
                        onClick = {
                            DesktopThemeManager.applyCustomColors(AppThemeState.primaryColor, c, AppThemeState.backgroundColor)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text("Color de Fondo General de Ventana:", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                backgroundOptions.forEach { (bg, label) ->
                    val isSelected = AppThemeState.backgroundColor == bg
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) NeonCyan else DarkCardBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                DesktopThemeManager.applyCustomColors(AppThemeState.primaryColor, AppThemeState.secondaryColor, bg)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(if (isSelected) NeonCyan else TextSecondary))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, color = if (isSelected) NeonCyan else TextPrimary, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECCIÓN 3: FONDO DE PANTALLA PERSONALIZADO
        SectionTitle("FONDO DE PANTALLA PERSONALIZADO", Icons.Default.Wallpaper)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            val wall = AppThemeState.wallpaperPath
            val wallFile = if (!wall.isNullOrBlank()) File(wall) else null

            if (wallFile != null && wallFile.exists()) {
                val bitmap = remember(wall) {
                    try {
                        wallFile.inputStream().use { loadImageBitmap(it) }
                    } catch (_: Exception) {
                        null
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceVariant)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                painter = BitmapPainter(bitmap),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Default.Wallpaper, contentDescription = null, tint = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(wallFile.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(wallFile.absolutePath, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { DesktopThemeManager.clearWallpaper() }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Quitar fondo", color = Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Oscurecimiento del fondo: ${(AppThemeState.wallpaperDim * 100).toInt()}% (Mayor valor = más legible)",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Slider(
                    value = AppThemeState.wallpaperDim,
                    onValueChange = { DesktopThemeManager.setWallpaperDim(it) },
                    valueRange = 0.15f..0.90f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
            }

            Button(
                onClick = {
                    val fd = FileDialog(null as Frame?, "Seleccionar Fondo de Pantalla", FileDialog.LOAD)
                    fd.filenameFilter = FilenameFilter { _, name ->
                        val lower = name.lowercase()
                        lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
                    }
                    fd.isVisible = true
                    if (fd.file != null) {
                        val sel = File(fd.directory, fd.file)
                        DesktopThemeManager.setWallpaper(sel)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Wallpaper, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (wallFile != null) "Cambiar imagen de fondo..." else "Elegir archivo de imagen de fondo...",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECCIÓN 4: EFECTOS & ANIMACIONES
        SectionTitle("EFECTOS & ANIMACIONES", Icons.Default.Animation)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurface)
                .border(1.dp, DarkCardBorder, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Animaciones de interfaz", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Efectos fluidos, barras de espectro musical y pulsos de carátula en reproducción", color = TextSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = AppThemeState.animationsEnabled,
                onCheckedChange = { DesktopThemeManager.setAnimationsEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonCyan,
                    checkedTrackColor = NeonCyan.copy(alpha = 0.3f)
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // SECCIÓN 5: RESTABLECER
        Button(
            onClick = { DesktopThemeManager.resetToDefaults() },
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.border(1.dp, DarkCardBorder, RoundedCornerShape(10.dp))
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Restablecer apariencia original", color = TextSecondary, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SectionTitle(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
        Icon(icon, contentDescription = null, tint = ElectricPurple, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold),
            color = TextSecondary
        )
    }
}

@Composable
private fun PresetCard(preset: com.boumusic.desktop.ui.theme.ThemePreset) {
    val isSelected = AppThemeState.currentPresetId == preset.id

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) preset.primary.copy(alpha = 0.15f) else DarkSurfaceVariant)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) preset.primary else DarkCardBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { DesktopThemeManager.applyPreset(preset) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.name,
                color = if (isSelected) preset.primary else TextPrimary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(preset.primary))
                Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(preset.secondary))
                Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(preset.background).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape))
                Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(preset.surface).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape))
            }
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(preset.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DesktopColorChip(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
