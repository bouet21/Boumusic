package com.boumusic.desktop.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp)
    ) {
        Text(
            text = "Sincronización & Backup (Android <-> PC)",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary
        )
        Text(
            text = "Sincroniza tus servidores SMB, FTP y listas de reproducción directamente con la app de Android mediante archivos JSON universales.",
            color = TextSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Tarjeta informativa Android
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurface)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Compatible 100% con Boumusic para Android", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "En la app de tu móvil: entra a Ajustes > Migración & Backup > 'Exportar JSON'. Luego pega o importa ese archivo aquí para tener al instante los mismos servidores y listas.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Botones de acción
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Button(
                onClick = {
                    val date = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                    val file = File(System.getProperty("user.home"), "Boumusic_Backup_$date.json")
                    val ok = viewModel.exportBackup(file)
                    statusMessage = if (ok) "Copia exportada con éxito en: ${file.absolutePath}" else "Error al exportar copia"
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                modifier = Modifier.weight(1f).height(46.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Exportar Copia (JSON)", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Área de importación
        Text("Importar archivo JSON de copia de seguridad:", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = importJsonText,
            onValueChange = { importJsonText = it },
            placeholder = { Text("Pega aquí el contenido del archivo JSON generado por Boumusic Android...") },
            modifier = Modifier.fillMaxWidth().height(160.dp),
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
            colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(42.dp)
        ) {
            Icon(Icons.Default.FileUpload, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Procesar e Importar", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        statusMessage?.let { msg ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(msg, color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
