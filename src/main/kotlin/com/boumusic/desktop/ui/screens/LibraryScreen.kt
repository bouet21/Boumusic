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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.boumusic.desktop.ui.theme.ElectricPurple
import com.boumusic.desktop.ui.theme.NeonCyan
import com.boumusic.desktop.ui.theme.TextMuted
import com.boumusic.desktop.ui.theme.TextPrimary
import com.boumusic.desktop.ui.theme.TextSecondary
import com.boumusic.desktop.viewmodel.DesktopMusicViewModel
import com.boumusic.desktop.viewmodel.LibrarySubTab

@Composable
fun LibraryScreen(viewModel: DesktopMusicViewModel, modifier: Modifier = Modifier) {
    val songs by viewModel.filteredSongs.collectAsState()
    val allSongs by viewModel.songs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val currentSong by viewModel.player.currentSong.collectAsState()

    val currentTab by viewModel.librarySubTab.collectAsState()
    val selectedArtist by viewModel.selectedArtist.collectAsState()
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()

    val artists by viewModel.artists.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val genres by viewModel.genres.collectAsState()

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
                    text = "${allSongs.size} canciones en tu equipo",
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

        Spacer(modifier = Modifier.height(16.dp))

        // Pestañas de categoría (Canciones, Artistas, Álbumes, Géneros)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LibrarySubTab.values().forEach { tab ->
                val isSelected = currentTab == tab
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        viewModel.setLibrarySubTab(tab)
                        viewModel.selectArtist(null)
                        viewModel.selectAlbum(null)
                        viewModel.selectGenre(null)
                    },
                    label = {
                        val count = when (tab) {
                            LibrarySubTab.ALL_SONGS -> allSongs.size
                            LibrarySubTab.ARTISTS -> artists.size
                            LibrarySubTab.ALBUMS -> albums.size
                            LibrarySubTab.GENRES -> genres.size
                        }
                        Text("${tab.title} ($count)", fontSize = 12.sp)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricPurple,
                        selectedLabelColor = TextPrimary,
                        containerColor = DarkSurface,
                        labelColor = TextSecondary
                    )
                )
            }

            // Indicador de filtro activo
            if (selectedArtist != null || selectedAlbum != null || selectedGenre != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonCyan.copy(alpha = 0.15f))
                        .clickable {
                            viewModel.selectArtist(null)
                            viewModel.selectAlbum(null)
                            viewModel.selectGenre(null)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filtro: ${selectedArtist ?: selectedAlbum ?: selectedGenre} ✕",
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Vista según la pestaña activa
        when (currentTab) {
            LibrarySubTab.ARTISTS -> {
                if (selectedArtist == null) {
                    ArtistListView(artists = artists, onSelect = { viewModel.selectArtist(it) })
                } else {
                    SongListView(
                        songs = songs,
                        currentSong = currentSong,
                        allSongs = allSongs,
                        onPlay = { s -> viewModel.playSong(s, songs) },
                        viewModel = viewModel
                    )
                }
            }
            LibrarySubTab.ALBUMS -> {
                if (selectedAlbum == null) {
                    AlbumListView(albums = albums, onSelect = { viewModel.selectAlbum(it) })
                } else {
                    SongListView(
                        songs = songs,
                        currentSong = currentSong,
                        allSongs = allSongs,
                        onPlay = { s -> viewModel.playSong(s, songs) },
                        viewModel = viewModel
                    )
                }
            }
            LibrarySubTab.GENRES -> {
                if (selectedGenre == null) {
                    GenreListView(genres = genres, onSelect = { viewModel.selectGenre(it) })
                } else {
                    SongListView(
                        songs = songs,
                        currentSong = currentSong,
                        allSongs = allSongs,
                        onPlay = { s -> viewModel.playSong(s, songs) },
                        viewModel = viewModel
                    )
                }
            }
            LibrarySubTab.ALL_SONGS -> {
                SongListView(
                    songs = songs,
                    currentSong = currentSong,
                    allSongs = allSongs,
                    onPlay = { s -> viewModel.playSong(s, songs) },
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun SongListView(
    songs: List<Song>,
    currentSong: Song?,
    allSongs: List<Song>,
    onPlay: (Song) -> Unit,
    viewModel: DesktopMusicViewModel
) {
    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (allSongs.isEmpty()) "No se encontraron canciones en tu carpeta Música.\nCopia archivos MP3 o FLAC para comenzar." else "No hay canciones que coincidan con el filtro actual.",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(
                items = songs,
                key = { _, song -> song.id },
                contentType = { _, _ -> "song_row" }
            ) { index, song ->
                val isCurrent = currentSong?.id == song.id
                SongRowItem(
                    index = index + 1,
                    song = song,
                    isCurrent = isCurrent,
                    onClick = { onPlay(song) },
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun ArtistListView(
    artists: List<Pair<String, Int>>,
    onSelect: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(artists, key = { it.first }) { (artist, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .clickable { onSelect(artist) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(artist, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("$count canciones", color = TextSecondary, fontSize = 11.sp)
                }
                Text("Ver pistas →", color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AlbumListView(
    albums: List<Pair<String, Int>>,
    onSelect: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(albums, key = { it.first }) { (album, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .clickable { onSelect(album) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Album, contentDescription = null, tint = ElectricPurple, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(album, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("$count canciones", color = TextSecondary, fontSize = 11.sp)
                }
                Text("Ver álbum →", color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun GenreListView(
    genres: List<Pair<String, Int>>,
    onSelect: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(genres, key = { it.first }) { (genre, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .clickable { onSelect(genre) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🏷️", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(genre, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("$count pistas", color = TextSecondary, fontSize = 11.sp)
                }
                Text("Filtrar →", color = TextMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SongRowItem(
    index: Int,
    song: Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    viewModel: DesktopMusicViewModel
) {
    var showMenu by remember { mutableStateOf(false) }
    val bg = if (isCurrent) NeonCyan.copy(alpha = 0.12f) else DarkSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString(),
            color = if (isCurrent) NeonCyan else TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.width(28.dp)
        )

        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Reproducir",
            tint = if (isCurrent) NeonCyan else TextMuted,
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

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

        Spacer(modifier = Modifier.width(8.dp))

        // Menú de opciones de la pista (⋮)
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Opciones", tint = TextMuted, modifier = Modifier.size(16.dp))
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(DarkSurfaceVariant)
            ) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reproducir a continuación", color = TextPrimary, fontSize = 12.sp)
                        }
                    },
                    onClick = {
                        viewModel.playNextInQueue(song)
                        showMenu = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Añadir a la cola", color = TextPrimary, fontSize = 12.sp)
                        }
                    },
                    onClick = {
                        viewModel.addToQueue(song)
                        showMenu = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = ElectricPurple, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Añadir a lista...", color = TextPrimary, fontSize = 12.sp)
                        }
                    },
                    onClick = {
                        viewModel.showAddToPlaylistDialog.value = song
                        showMenu = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Detalles de la pista", color = TextPrimary, fontSize = 12.sp)
                        }
                    },
                    onClick = {
                        viewModel.showSongDetailsDialog.value = song
                        showMenu = false
                    }
                )
            }
        }
    }
}
