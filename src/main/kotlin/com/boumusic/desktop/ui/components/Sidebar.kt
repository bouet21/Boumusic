package com.boumusic.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun Sidebar(
    viewModel: DesktopMusicViewModel,
    currentSection: DesktopSection,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(240.dp)
            .background(DarkSurface)
            .padding(16.dp)
    ) {
        // Logo & Nombre
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(NeonCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Boumusic",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = TextPrimary
                )
                Text(
                    text = "PC Desktop v1.0.3",
                    fontSize = 10.sp,
                    color = NeonCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Elementos de navegación
        SidebarItem(
            title = "Biblioteca",
            icon = Icons.Default.Folder,
            isSelected = currentSection == DesktopSection.LIBRARY,
            onClick = { viewModel.setSection(DesktopSection.LIBRARY) }
        )

        Spacer(modifier = Modifier.height(6.dp))

        SidebarItem(
            title = "Listas",
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            isSelected = currentSection == DesktopSection.PLAYLISTS,
            onClick = { viewModel.setSection(DesktopSection.PLAYLISTS) }
        )

        Spacer(modifier = Modifier.height(6.dp))

        SidebarItem(
            title = "Cola de reproducción",
            icon = Icons.Default.MusicNote,
            isSelected = currentSection == DesktopSection.QUEUE,
            onClick = { viewModel.setSection(DesktopSection.QUEUE) }
        )

        Spacer(modifier = Modifier.height(6.dp))

        SidebarItem(
            title = "Servidores (SMB / FTP)",
            icon = Icons.Default.Dns,
            isSelected = currentSection == DesktopSection.SERVERS,
            onClick = { viewModel.setSection(DesktopSection.SERVERS) }
        )

        Spacer(modifier = Modifier.height(6.dp))

        SidebarItem(
            title = "Sincronización & Backup",
            icon = Icons.Default.CloudSync,
            isSelected = currentSection == DesktopSection.BACKUP,
            onClick = { viewModel.setSection(DesktopSection.BACKUP) }
        )

        Spacer(modifier = Modifier.height(6.dp))

        SidebarItem(
            title = "Ecualizador",
            icon = Icons.Default.GraphicEq,
            isSelected = currentSection == DesktopSection.EQUALIZER,
            onClick = { viewModel.setSection(DesktopSection.EQUALIZER) }
        )

        Spacer(modifier = Modifier.height(6.dp))

        SidebarItem(
            title = "Boumusic Wrapped",
            icon = Icons.Default.AutoAwesome,
            isSelected = currentSection == DesktopSection.WRAPPED,
            onClick = { viewModel.setSection(DesktopSection.WRAPPED) }
        )

        Spacer(modifier = Modifier.weight(1f))

        // Botón de reescanear biblioteca
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(DarkBackground)
                .clickable { viewModel.rescanLibrary() }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reescanear música", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SidebarItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) NeonCyan.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent
    val tint = if (isSelected) NeonCyan else TextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            color = if (isSelected) TextPrimary else TextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}
