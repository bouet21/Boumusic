package com.boumusic.desktop.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boumusic.desktop.model.Song
import com.boumusic.desktop.model.SongSortOrder
import com.boumusic.desktop.ui.theme.DarkBackground
import com.boumusic.desktop.ui.theme.DarkCardBorder
import com.boumusic.desktop.ui.theme.DarkSurface
import com.boumusic.desktop.ui.theme.DarkSurfaceVariant
import com.boumusic.desktop.ui.theme.NeonCyan
import com.boumusic.desktop.ui.theme.TextMuted
import com.boumusic.desktop.ui.theme.TextPrimary
import com.boumusic.desktop.ui.theme.TextSecondary
import com.boumusic.desktop.viewmodel.DesktopMusicViewModel

@Composable
fun LibraryScreen(viewModel: DesktopMusicViewModel, modifier: Modifier = Modifier) {
    val songs by viewModel.filteredSongs.collectAsState()
    val allSongs by viewModel.songs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val currentSong by viewModel.player.currentSong.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp)
    ) {
        // Cabecera superior
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Biblioteca de Música",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "${allSongs.size} canciones en tu PC",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            // Barra de búsqueda y ordenación
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Buscar canción, artista o álbum...", color = TextMuted, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.width(320.dp).height(46.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurface)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar", tint = NeonCyan, modifier = Modifier.size(20.dp))
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        SongSortOrder.values().forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        order.displayName,
                                        color = if (sortOrder == order) NeonCyan else TextPrimary,
                                        fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                },
                                onClick = {
                                    viewModel.setSortOrder(order)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Lista de canciones
        if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (allSongs.isEmpty()) "No se encontraron canciones en tu carpeta Música.\nCopia archivos MP3 o FLAC para comenzar." else "No hay canciones que coincidan con la búsqueda.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(songs) { index, song ->
                    val isCurrent = currentSong?.id == song.id
                    SongRowItem(
                        index = index + 1,
                        song = song,
                        isCurrent = isCurrent,
                        onClick = { viewModel.playSong(song, songs) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SongRowItem(
    index: Int,
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isCurrent) NeonCyan.copy(alpha = 0.12f) else DarkSurface
    val border = if (isCurrent) NeonCyan.copy(alpha = 0.3f) else androidx.compose.ui.graphics.Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString(),
            color = if (isCurrent) NeonCyan else TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.width(32.dp)
        )

        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Reproducir",
            tint = if (isCurrent) NeonCyan else TextMuted,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(2f)) {
            Text(
                text = song.title,
                color = if (isCurrent) NeonCyan else TextPrimary,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = song.album,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.weight(1.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = song.formattedDuration,
            color = TextMuted,
            fontSize = 11.sp
        )
    }
}
