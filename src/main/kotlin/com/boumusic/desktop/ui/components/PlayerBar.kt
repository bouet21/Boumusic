package com.boumusic.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boumusic.desktop.ui.theme.DarkBackground
import com.boumusic.desktop.ui.theme.DarkCardBorder
import com.boumusic.desktop.ui.theme.DarkSurface
import com.boumusic.desktop.ui.theme.ElectricPurple
import com.boumusic.desktop.ui.theme.NeonCyan
import com.boumusic.desktop.ui.theme.TextMuted
import com.boumusic.desktop.ui.theme.TextPrimary
import com.boumusic.desktop.ui.theme.TextSecondary
import com.boumusic.desktop.viewmodel.DesktopMusicViewModel
import com.boumusic.desktop.viewmodel.DesktopSection
import com.boumusic.desktop.viewmodel.RepeatMode

@Composable
fun PlayerBar(
    viewModel: DesktopMusicViewModel,
    modifier: Modifier = Modifier
) {
    val currentSong by viewModel.player.currentSong.collectAsState()
    val isPlaying by viewModel.player.isPlaying.collectAsState()
    val positionSec by viewModel.player.currentPositionSec.collectAsState()
    val durationSec by viewModel.player.durationSec.collectAsState()
    val volume by viewModel.player.volume.collectAsState()
    val isShuffle by viewModel.isShuffle.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val currentSection by viewModel.currentSection.collectAsState()
    val sleepTimer by viewModel.sleepTimerMinutes.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .background(DarkSurface)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Izquierda: Información de la pista
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkBackground),
                contentAlignment = Alignment.Center
            ) {
                Text("🎵", fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = currentSong?.title ?: "No hay canción seleccionada",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentSong?.let {
                        "${it.artist} • ${it.album}"
                    } ?: "Toca una pista para reproducir",
                    color = if (currentSong?.isRemote == true) NeonCyan else TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Centro: Controles de reproducción, repetición y barra de progreso interactiva
        Column(
            modifier = Modifier.weight(2f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Aleatorio
                IconButton(onClick = { viewModel.toggleShuffle() }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Aleatorio",
                        tint = if (isShuffle) NeonCyan else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Anterior
                IconButton(onClick = { viewModel.playPrevious() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior", tint = TextPrimary, modifier = Modifier.size(22.dp))
                }

                // Play / Pause
                IconButton(
                    onClick = { viewModel.player.togglePlayPause() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(NeonCyan)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = DarkBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Siguiente
                IconButton(onClick = { viewModel.playNext(fromUser = true) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Siguiente", tint = TextPrimary, modifier = Modifier.size(22.dp))
                }

                // Repetición (OFF, ALL, ONE)
                IconButton(onClick = { viewModel.toggleRepeatMode() }, modifier = Modifier.size(34.dp)) {
                    val repeatIcon = if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                    val repeatTint = if (repeatMode != RepeatMode.OFF) NeonCyan else TextMuted
                    Icon(
                        repeatIcon,
                        contentDescription = repeatMode.label,
                        tint = repeatTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Barra de progreso interactiva con búsqueda (Seek)
            Row(
                modifier = Modifier.fillMaxWidth(0.92f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatPos(positionSec, durationSec),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                val maxRange = if (durationSec > 0) durationSec.toFloat() else 100f
                val currentProgress = positionSec.toFloat().coerceIn(0f, maxRange)

                Slider(
                    value = currentProgress,
                    onValueChange = { newSec ->
                        viewModel.seekTo(newSec.toLong())
                    },
                    valueRange = 0f..maxRange,
                    modifier = Modifier.weight(1f).height(12.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = DarkCardBorder
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatDuration(durationSec),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        // Derecha: Temporizador, Cola y Volumen
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Temporizador de apagado
            IconButton(
                onClick = { viewModel.showSleepTimerDialog.value = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Bedtime,
                    contentDescription = "Temporizador de apagado",
                    tint = if (sleepTimer != null) ElectricPurple else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Botón de Cola de reproducción
            IconButton(
                onClick = {
                    if (currentSection == DesktopSection.QUEUE) {
                        viewModel.setSection(DesktopSection.LIBRARY)
                    } else {
                        viewModel.setSection(DesktopSection.QUEUE)
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Cola de reproducción",
                    tint = if (currentSection == DesktopSection.QUEUE) NeonCyan else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Control de volumen
            val volIcon = when {
                volume <= 0.01f -> Icons.AutoMirrored.Filled.VolumeMute
                volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                else -> Icons.AutoMirrored.Filled.VolumeUp
            }

            IconButton(
                onClick = {
                    if (volume > 0f) viewModel.player.setVolume(0f) else viewModel.player.setVolume(0.85f)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(volIcon, contentDescription = "Volumen", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }

            Slider(
                value = volume,
                onValueChange = { viewModel.player.setVolume(it) },
                valueRange = 0f..1f,
                modifier = Modifier.width(90.dp).height(12.dp),
                colors = SliderDefaults.colors(
                    thumbColor = NeonCyan,
                    activeTrackColor = NeonCyan,
                    inactiveTrackColor = DarkCardBorder
                )
            )
        }
    }
}

private fun formatDuration(sec: Long): String {
    if (sec <= 0) return "--:--"
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) {
        String.format("%02d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}

private fun formatPos(sec: Long, totalSec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (totalSec >= 3600 || h > 0) {
        String.format("%02d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}
