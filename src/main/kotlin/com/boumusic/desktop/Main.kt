package com.boumusic.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.boumusic.desktop.ui.components.PlayerBar
import com.boumusic.desktop.ui.components.Sidebar
import com.boumusic.desktop.ui.screens.BackupScreen
import com.boumusic.desktop.ui.screens.LibraryScreen
import com.boumusic.desktop.ui.screens.PlaylistsScreen
import com.boumusic.desktop.ui.screens.ServersScreen
import com.boumusic.desktop.ui.theme.BoumusicDesktopTheme
import com.boumusic.desktop.viewmodel.DesktopMusicViewModel
import com.boumusic.desktop.viewmodel.DesktopSection

fun main() = application {
    val windowState = rememberWindowState(width = 1120.dp, height = 740.dp)
    val viewModel = remember { DesktopMusicViewModel() }
    val currentSection by viewModel.currentSection.collectAsState()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Boumusic Desktop — v0.1.0-beta",
        state = windowState
    ) {
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
                            DesktopSection.SERVERS -> ServersScreen(viewModel = viewModel)
                            DesktopSection.BACKUP -> BackupScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
