package com.boumusic.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boumusic.desktop.audio.EqualizerPreset
import com.boumusic.desktop.ui.theme.DarkBackground
import com.boumusic.desktop.ui.theme.DarkCardBorder
import com.boumusic.desktop.ui.theme.DarkSurface
import com.boumusic.desktop.ui.theme.DarkSurfaceVariant
import com.boumusic.desktop.ui.theme.ElectricPurple
import com.boumusic.desktop.ui.theme.NeonCyan
import com.boumusic.desktop.ui.theme.TextMuted
import com.boumusic.desktop.ui.theme.TextPrimary
import com.boumusic.desktop.ui.theme.TextSecondary
import com.boumusic.desktop.viewmodel.DesktopMusicViewModel

@Composable
fun EqualizerScreen(
    viewModel: DesktopMusicViewModel,
    modifier: Modifier = Modifier
) {
    val eq = viewModel.player.equalizer
    val currentSong by viewModel.player.currentSong.collectAsState()
    val isPlaying by viewModel.player.isPlaying.collectAsState()

    var isEnabled by remember { mutableStateOf(eq.isEnabled) }
    var currentPresetName by remember { mutableStateOf(eq.currentPresetName) }
    var bassBoost by remember { mutableStateOf(eq.bassBoostDb) }

    val bandGains = remember {
        mutableStateListOf<Float>().apply {
            for (i in eq.bands.indices) {
                add(eq.getBandGain(i))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Cabecera Principal
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.25f), ElectricPurple.copy(alpha = 0.25f)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Ecualizador de Audio",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "Ajuste acústico en tiempo real de 5 bandas con filtros Biquad IIR de estudio",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Switch On/Off
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .border(1.dp, if (isEnabled) NeonCyan.copy(alpha = 0.5f) else DarkCardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isEnabled) "Activado" else "Desactivado",
                    color = if (isEnabled) NeonCyan else TextMuted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        isEnabled = checked
                        eq.setEnabled(checked)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NeonCyan,
                        checkedTrackColor = NeonCyan.copy(alpha = 0.35f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkCardBorder
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Fila de Presets
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ajustes Preestablecidos (Presets)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                eq.resetToFlat()
                                currentPresetName = "Plano"
                                bassBoost = 0f
                                for (i in bandGains.indices) bandGains[i] = 0f
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Plano", fontSize = 12.sp, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(eq.presets) { preset ->
                        val isSelected = currentPresetName == preset.name
                        val bg = if (isSelected) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceVariant
                        val borderCol = if (isSelected) NeonCyan else DarkCardBorder
                        val textCol = if (isSelected) NeonCyan else TextSecondary

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(bg)
                                .border(1.dp, borderCol, RoundedCornerShape(10.dp))
                                .clickable {
                                    eq.applyPreset(preset)
                                    currentPresetName = preset.name
                                    bassBoost = preset.bassBoostDb
                                    for (i in bandGains.indices) {
                                        bandGains[i] = preset.bandGains.getOrElse(i) { 0f }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.name,
                                color = textCol,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Panel Central: 5 Bandas de Frecuencia
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Bandas de Frecuencia (-12 dB a +12 dB)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    eq.bands.forEachIndexed { index, band ->
                        val gain = bandGains.getOrElse(index) { 0f }
                        val isBoosted = gain > 0.05f
                        val isCut = gain < -0.05f
                        val gainColor = when {
                            !isEnabled -> TextMuted
                            isBoosted -> NeonCyan
                            isCut -> ElectricPurple
                            else -> TextSecondary
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(130.dp)
                        ) {
                            // Valor dB
                            Text(
                                text = if (gain > 0) "+${String.format("%.1f", gain)} dB" else "${String.format("%.1f", gain)} dB",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = gainColor
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Slider de ganancia
                            Slider(
                                value = gain,
                                onValueChange = { newVal ->
                                    if (isEnabled) {
                                        bandGains[index] = newVal
                                        eq.setBandGain(index, newVal)
                                        currentPresetName = "Personalizado"
                                    }
                                },
                                valueRange = -12f..12f,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = isEnabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = if (isEnabled) NeonCyan else TextMuted,
                                    activeTrackColor = if (isEnabled) NeonCyan else DarkCardBorder,
                                    inactiveTrackColor = DarkCardBorder
                                )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = band.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isEnabled) TextPrimary else TextMuted
                            )
                            val label = when (index) {
                                0 -> "Sub-graves"
                                1 -> "Graves"
                                2 -> "Medios"
                                3 -> "Presencia"
                                else -> "Agudos"
                            }
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Refuerzo de Graves (Bass Boost)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ElectricPurple.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Speaker,
                        contentDescription = null,
                        tint = ElectricPurple,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Refuerzo Dinámico de Graves (Bass Boost)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "+${String.format("%.1f", bassBoost)} dB",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (bassBoost > 0.05f && isEnabled) ElectricPurple else TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = bassBoost,
                        onValueChange = { newVal ->
                            if (isEnabled) {
                                bassBoost = newVal
                                eq.setBassBoost(newVal)
                            }
                        },
                        valueRange = 0f..10f,
                        enabled = isEnabled,
                        colors = SliderDefaults.colors(
                            thumbColor = if (isEnabled) ElectricPurple else TextMuted,
                            activeTrackColor = if (isEnabled) ElectricPurple else DarkCardBorder,
                            inactiveTrackColor = DarkCardBorder
                        )
                    )
                }
            }
        }

        // Indicador de estado de reproducción
        if (currentSong != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sonando ahora: ${currentSong?.title} — ${currentSong?.artist} (${if (isPlaying) "Reproduciendo con EQ en vivo" else "En pausa"})",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
