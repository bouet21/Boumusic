package com.boumusic.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boumusic.desktop.ui.theme.DarkBackground
import com.boumusic.desktop.ui.theme.DarkCardBorder
import com.boumusic.desktop.ui.theme.DarkSurface
import com.boumusic.desktop.ui.theme.DarkSurfaceVariant
import com.boumusic.desktop.ui.theme.ElectricPurple
import com.boumusic.desktop.ui.theme.NeonCyan
import com.boumusic.desktop.ui.theme.TextPrimary
import com.boumusic.desktop.ui.theme.TextSecondary
import com.boumusic.desktop.viewmodel.DesktopMusicViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(viewModel: DesktopMusicViewModel, modifier: Modifier = Modifier) {
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var importJsonText by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val autoSyncIds by viewModel.autoSyncDeviceIds.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(24.dp)
    ) {
        Text(
            text = "Sincronización & Red Local",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Text(
            text = "Sincroniza tus servidores SMB, FTP y listas de reproducción automáticamente con otros dispositivos Boumusic en tu misma red Wi-Fi o mediante archivos de copia.",
            color = TextSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // SECCIÓN 1: SINCRONIZACIÓN AUTOMÁTICA EN RED LOCAL
        Text(
            text = "DISPOSITIVOS EN TU RED LOCAL (WI-FI / LAN)",
            color = NeonCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Notificación de estado de sync
        syncStatus?.let { status ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, NeonCyan, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(status, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (discoveredDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(14.dp))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Buscando dispositivos en la misma red...",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Abre Boumusic en tu móvil Android u otro PC conectado al mismo Wi-Fi para sincronizar automáticamente tus servidores y listas sin cables ni configuraciones.",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                discoveredDevices.forEach { dev ->
                    val isAutoSync = autoSyncIds.contains(dev.id)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurface)
                            .border(1.dp, if (isAutoSync) NeonCyan.copy(alpha = 0.5f) else DarkCardBorder, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(if (isAutoSync) NeonCyan.copy(alpha = 0.15f) else DarkSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (dev.type.equals("ANDROID", ignoreCase = true)) Icons.Default.PhoneAndroid else Icons.Default.Computer,
                                        contentDescription = null,
                                        tint = if (isAutoSync) NeonCyan else TextSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(dev.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isAutoSync) NeonCyan.copy(alpha = 0.2f) else DarkSurfaceVariant)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (isAutoSync) "Auto-Sync Activo" else dev.type,
                                                color = if (isAutoSync) NeonCyan else TextSecondary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("${dev.ip}:${dev.port}", color = TextSecondary, fontSize = 12.sp)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { viewModel.syncWithDevice(dev) },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sincronizar ahora", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Auto", color = if (isAutoSync) NeonCyan else TextSecondary, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = isAutoSync,
                                        onCheckedChange = { viewModel.toggleAutoSync(dev.id) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = DarkBackground,
                                            checkedTrackColor = NeonCyan,
                                            uncheckedThumbColor = TextSecondary,
                                            uncheckedTrackColor = DarkSurfaceVariant
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // SECCIÓN 2: COPIA MANUAL JSON
        Text(
            text = "COPIA DE SEGURIDAD MANUAL (JSON)",
            color = ElectricPurple,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Botón exportar
        Button(
            onClick = {
                val date = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                val file = File(System.getProperty("user.home"), "Boumusic_Backup_$date.json")
                val ok = viewModel.exportBackup(file)
                statusMessage = if (ok) "Copia exportada con éxito en: ${file.absolutePath}" else "Error al exportar copia"
            },
            colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Exportar Copia (JSON)", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Área de importación
        Text("Importar archivo JSON de copia de seguridad:", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = importJsonText,
            onValueChange = { importJsonText = it },
            placeholder = { Text("Pega aquí el contenido del archivo JSON generado por Boumusic Android o Desktop...") },
            modifier = Modifier.fillMaxWidth().height(140.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface,
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = DarkCardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                if (importJsonText.isNotBlank()) {
                    val ok = viewModel.importBackup(importJsonText)
                    statusMessage = if (ok) "¡Configuración y listas importadas correctamente!" else "Error al procesar el archivo JSON"
                    if (ok) importJsonText = ""
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(42.dp)
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Procesar e Importar", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        statusMessage?.let { msg ->
            Spacer(modifier = Modifier.height(14.dp))
            Text(msg, color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
