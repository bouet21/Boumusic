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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.boumusic.desktop.audio.AudioMetadataHelper
import com.boumusic.desktop.model.FtpConfig
import com.boumusic.desktop.model.FtpItem
import com.boumusic.desktop.model.Song
import com.boumusic.desktop.ui.components.ServerDialog
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
fun ServersScreen(viewModel: DesktopMusicViewModel, modifier: Modifier = Modifier) {
    val servers by viewModel.savedServers.collectAsState()
    val activeServer by viewModel.activeServer.collectAsState()
    val serverPath by viewModel.serverPath.collectAsState()
    val serverItems by viewModel.serverItems.collectAsState()
    val isLoading by viewModel.isServerLoading.collectAsState()
    val error by viewModel.serverError.collectAsState()
    val isScanningServerAudio by viewModel.isScanningServerAudio.collectAsState()
    val serverScanStatus by viewModel.serverScanStatus.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp)
    ) {
        if (activeServer == null) {
            // Vista de lista de servidores guardados
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Servidores Remotos",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Conéctate a carpetas compartidas de Windows (SMB), Linux (Samba), FTP o WebDAV.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Añadir Servidor", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (servers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No tienes servidores remotos configurados.", color = TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Pulsa 'Añadir Servidor' para conectarte a tu PC u otro equipo en red.", color = TextMuted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(servers) { config ->
                        ServerCardItem(
                            config = config,
                            onConnect = { viewModel.connectServer(config) },
                            onDelete = { viewModel.deleteServer(config.id) }
                        )
                    }
                }
            }
        } else {
            // Vista del servidor conectado y explorador de carpetas
            val config = activeServer!!

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.disconnectServer() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = config.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ElectricPurple.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(config.protocolLabel, color = ElectricPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = "${config.host}:${config.port} • $serverPath",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { viewModel.playServerShuffleAll() },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp),
                        enabled = !isScanningServerAudio
                    ) {
                        if (isScanningServerAudio) {
                            CircularProgressIndicator(
                                color = TextPrimary,
                                modifier = Modifier.size(15.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Buscando...", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Shuffle, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Aleatorio Todo el Servidor", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = { viewModel.disconnectServer() },
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Desconectar", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }

            if (isScanningServerAudio) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ElectricPurple.copy(alpha = 0.2f))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (serverScanStatus.isNotBlank()) serverScanStatus else "Buscando todas las canciones en las carpetas del servidor...",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Migas de pan (Breadcrumbs)
            BreadcrumbsRow(
                currentPath = serverPath,
                onNavigate = { viewModel.loadServerDirectory(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NeonCyan)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Conectando y cargando archivos de ${config.name}...",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${config.host}:${config.port} ($serverPath)",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = { viewModel.disconnectServer() },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                        ) {
                            Text("Cancelar", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            } else if (error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "⚠️ No se pudo conectar al servidor",
                            color = androidx.compose.ui.graphics.Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error!!,
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { viewModel.loadServerDirectory(serverPath) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                            ) {
                                Text("Reintentar", color = DarkBackground, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.disconnectServer() },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                            ) {
                                Text("Volver a la lista", color = TextSecondary)
                            }
                        }
                    }
                }
            } else if (serverItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Esta carpeta está vacía.", color = TextSecondary)
                        if (serverPath != "/") {
                            Spacer(modifier = Modifier.height(8.dp))
                            val parentPath = serverPath.substringBeforeLast("/", "").ifBlank { "/" }
                            Button(
                                onClick = { viewModel.loadServerDirectory(parentPath) },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
                            ) {
                                Text("Subir a carpeta superior", color = TextSecondary)
                            }
                        }
                    }
                }
            } else {
                val audioItems = serverItems.filter { it.isAudio }
                if (audioItems.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${audioItems.size} ${if (audioItems.size == 1) "pista de audio" else "pistas de audio"}",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.playAllRemote(audioItems, shuffle = false) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reproducir carpeta", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { viewModel.playAllRemote(audioItems, shuffle = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Aleatorio carpeta", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(serverItems) { item ->
                        RemoteFileRow(
                            item = item,
                            onClick = {
                                if (item.isDirectory) {
                                    viewModel.loadServerDirectory(item.path)
                                } else if (item.isAudio) {
                                    viewModel.playRemoteSong(item)
                                }
                            },
                            onAddToPlaylist = {
                                val (parsedArtist, parsedTitle) = AudioMetadataHelper.parseFileNameMetadata(
                                    fileName = item.name,
                                    parentFolderName = serverPath
                                )
                                val s = Song(
                                    id = item.path.hashCode().toLong(),
                                    title = parsedTitle,
                                    artist = parsedArtist,
                                    album = config.name,
                                    duration = if (item.size > 1024L) (item.size * 1000L) / 24000L else 0L,
                                    path = item.path,
                                    isRemote = true,
                                    remoteHost = config.host,
                                    remotePath = item.path,
                                    fileSize = if (item.size > 0L) item.size else 0L
                                )
                                viewModel.showAddToPlaylistDialog.value = s
                            },
                            onDownload = {
                                viewModel.downloadRemoteSong(item) {}
                            }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            ServerDialog(
                ftpManager = viewModel.ftpManager,
                onSave = {
                    viewModel.saveServer(it)
                    showAddDialog = false
                    viewModel.connectServer(it)
                },
                onDismiss = { showAddDialog = false }
            )
        }
    }
}

@Composable
private fun ServerCardItem(
    config: FtpConfig,
    onConnect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .clickable(onClick = onConnect)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ElectricPurple.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Dns, contentDescription = null, tint = ElectricPurple, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(config.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeonCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(config.protocolLabel, color = NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("${config.host}:${config.port} • Usuario: ${config.username}", color = TextSecondary, fontSize = 12.sp)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Conectar", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun BreadcrumbsRow(currentPath: String, onNavigate: (String) -> Unit) {
    val segments = currentPath.trim('/').split("/").filter { it.isNotBlank() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "🏠 Raíz",
            color = if (segments.isEmpty()) NeonCyan else TextSecondary,
            fontWeight = if (segments.isEmpty()) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp,
            modifier = Modifier.clickable { onNavigate("/") }
        )

        var accumulated = ""
        segments.forEachIndexed { index, seg ->
            accumulated += "/$seg"
            val target = accumulated
            val isLast = index == segments.size - 1
            Text("  /  ", color = TextMuted, fontSize = 11.sp)
            Text(
                text = seg,
                color = if (isLast) NeonCyan else TextSecondary,
                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                fontSize = 12.sp,
                modifier = Modifier.clickable { onNavigate(target) }
            )
        }
    }
}

@Composable
private fun RemoteFileRow(
    item: FtpItem,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.isDirectory) Icons.Default.FolderOpen else Icons.Default.MusicNote,
            contentDescription = null,
            tint = if (item.isDirectory) ElectricPurple else NeonCyan,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.formattedSize,
                color = TextMuted,
                fontSize = 11.sp
            )
        }

        if (item.isAudio) {
            IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = "Añadir a lista",
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Download, contentDescription = "Descargar al PC", tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
