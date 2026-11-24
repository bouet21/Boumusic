package com.boumusic.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.boumusic.desktop.ui.components.AddToPlaylistDialog
import com.boumusic.desktop.ui.components.PlayerBar
import com.boumusic.desktop.ui.components.Sidebar
import com.boumusic.desktop.ui.components.SleepTimerDialog
import com.boumusic.desktop.ui.components.SongDetailsDialog
import com.boumusic.desktop.ui.screens.BackupScreen
import com.boumusic.desktop.ui.screens.EqualizerScreen
import com.boumusic.desktop.ui.screens.LibraryScreen
import com.boumusic.desktop.ui.screens.PlaylistsScreen
import com.boumusic.desktop.ui.screens.QueueScreen
import com.boumusic.desktop.ui.screens.ServersScreen
import com.boumusic.desktop.ui.screens.WrappedScreen
import com.boumusic.desktop.ui.theme.BoumusicDesktopTheme
import com.boumusic.desktop.ui.util.WindowsDarkThemeHelper
import com.boumusic.desktop.viewmodel.DesktopMusicViewModel
import com.boumusic.desktop.viewmodel.DesktopSection
import java.io.File

fun main() = application {
    val windowState = rememberWindowState(width = 1140.dp, height = 760.dp)
    val viewModel = remember { DesktopMusicViewModel() }
    val currentSection by viewModel.currentSection.collectAsState()

    val showSleepTimer by viewModel.showSleepTimerDialog.collectAsState()
    val songDetails by viewModel.showSongDetailsDialog.collectAsState()
    val songToAddToPlaylist by viewModel.showAddToPlaylistDialog.collectAsState()

    val currentSong by viewModel.player.currentSong.collectAsState()
    val isPlaying by viewModel.player.isPlaying.collectAsState()

    val windowTitle = remember(currentSong, isPlaying) {
        val song = currentSong
        if (song != null) {
            val track = if (song.artist.isNotBlank() && !song.artist.equals("Desconocido", ignoreCase = true)) {
                "${song.title} - ${song.artist}"
            } else {
                song.title
            }
            if (isPlaying) "Boumusic — ▶ $track" else "Boumusic — $track"
        } else {
            "Boumusic"
        }
    }

    val appIcon = remember {
        try {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("icons/icon.png")
                ?: File("src/main/resources/icons/icon.png").takeIf { it.exists() }?.inputStream()
            stream?.use {
                loadImageBitmap(it)
            }?.let {
                BitmapPainter(it)
            }
        } catch (_: Throwable) {
            null
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = windowTitle,
        state = windowState,
        icon = appIcon
    ) {
        // Aplica barra de título nativa oscura (DWM) y color de fondo nativo de inmediato
        WindowsDarkThemeHelper.applyDarkTitleBar(window)
        LaunchedEffect(window) {
            WindowsDarkThemeHelper.applyDarkTitleBar(window)
        }

        BoumusicDesktopTheme {
            Scaffold(
                bottomBar = {
                    PlayerBar(viewModel = viewModel)
                }
            ) { paddingValues ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    Sidebar(
                        viewModel = viewModel,
                        currentSection = currentSection
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        when (currentSection) {
                            DesktopSection.LIBRARY -> LibraryScreen(viewModel = viewModel)
                            DesktopSection.PLAYLISTS -> PlaylistsScreen(viewModel = viewModel)
                            DesktopSection.QUEUE -> QueueScreen(viewModel = viewModel)
                            DesktopSection.SERVERS -> ServersScreen(viewModel = viewModel)
                            DesktopSection.BACKUP -> BackupScreen(viewModel = viewModel)
                            DesktopSection.EQUALIZER -> EqualizerScreen(viewModel = viewModel)
                            DesktopSection.WRAPPED -> WrappedScreen(viewModel = viewModel)
                        }
                    }
                }

                // Diálogos modales globales
                if (showSleepTimer) {
                    SleepTimerDialog(
                        viewModel = viewModel,
                        onDismiss = { viewModel.showSleepTimerDialog.value = false }
                    )
                }

                songDetails?.let { s ->
                    SongDetailsDialog(
                        song = s,
                        onDismiss = { viewModel.showSongDetailsDialog.value = null }
                    )
                }

                songToAddToPlaylist?.let { s ->
                    AddToPlaylistDialog(
                        song = s,
                        viewModel = viewModel,
                        onDismiss = { viewModel.showAddToPlaylistDialog.value = null }
                    )
                }
            }
        }
    }
}
