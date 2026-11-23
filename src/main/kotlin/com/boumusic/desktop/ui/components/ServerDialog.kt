package com.boumusic.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boumusic.desktop.model.FtpConfig
import com.boumusic.desktop.ui.theme.DarkBackground
import com.boumusic.desktop.ui.theme.DarkCardBorder
import com.boumusic.desktop.ui.theme.DarkSurface
import com.boumusic.desktop.ui.theme.DarkSurfaceVariant
import com.boumusic.desktop.ui.theme.ElectricPurple
import com.boumusic.desktop.ui.theme.NeonCyan
import com.boumusic.desktop.ui.theme.TextPrimary
import com.boumusic.desktop.ui.theme.TextSecondary

@Composable
fun ServerDialog(
    initialConfig: FtpConfig? = null,
    onSave: (FtpConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialConfig?.name ?: "Mi PC") }
    var host by remember { mutableStateOf(initialConfig?.host ?: "") }
    var portStr by remember { mutableStateOf(initialConfig?.port?.toString() ?: "445") }
    var username by remember { mutableStateOf(initialConfig?.username ?: "") }
    var password by remember { mutableStateOf(initialConfig?.password ?: "") }
    var isFtps by remember { mutableStateOf(initialConfig?.isFtps ?: false) }
    var isWebDav by remember { mutableStateOf(initialConfig?.isWebDav ?: false) }
    var webDavPath by remember { mutableStateOf(initialConfig?.webDavPath ?: "/") }
    var isSmb by remember { mutableStateOf(initialConfig?.isSmb ?: true) }
    var smbShare by remember { mutableStateOf(initialConfig?.smbShare ?: "Musica") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (initialConfig == null) "Añadir Servidor Remoto" else "Editar Servidor",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextSecondary)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                // Selector de protocolo
                Text("Protocolo de conexión:", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = isSmb,
                        onClick = {
                            isSmb = true
                            isWebDav = false
                            isFtps = false
                            if (portStr == "21" || portStr == "443") portStr = "445"
                        },
                        label = { Text("SMB (Windows/Linux)", fontSize = 11.sp) },
                        colors = chipColors()
                    )
                    FilterChip(
                        selected = !isSmb && !isWebDav && !isFtps,
                        onClick = {
                            isSmb = false
                            isWebDav = false
                            isFtps = false
                            if (portStr == "445" || portStr == "443") portStr = "21"
                        },
                        label = { Text("FTP", fontSize = 11.sp) },
                        colors = chipColors()
                    )
                    FilterChip(
                        selected = isWebDav,
                        onClick = {
                            isSmb = false
                            isWebDav = true
                            isFtps = false
                            if (portStr == "21" || portStr == "445") portStr = "443"
                        },
                        label = { Text("WebDAV", fontSize = 11.sp) },
                        colors = chipColors()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre del servidor", fontSize = 11.sp) },
                        modifier = Modifier.weight(2f),
                        singleLine = true,
                        colors = fieldColors()
                    )
                    OutlinedTextField(
                        value = portStr,
                        onValueChange = { portStr = it },
                        label = { Text("Puerto", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = fieldColors()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Dirección IP o Host (Ej: 192.168.1.100)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors()
                )

                if (isSmb) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = smbShare,
                        onValueChange = { smbShare = it },
                        label = { Text("Nombre de la carpeta compartida (Ej: Musica)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = fieldColors()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(if (isSmb) "Usuario (vacío = invitado)" else "Usuario", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = fieldColors()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = fieldColors()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = portStr.toIntOrNull() ?: if (isSmb) 445 else if (isWebDav) 443 else 21
                    val config = FtpConfig(
                        id = initialConfig?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name.ifBlank { "Servidor" },
                        host = host.trim(),
                        port = port,
                        username = username.ifBlank { if (isSmb) "guest" else "anonymous" },
                        password = password,
                        isFtps = isFtps,
                        isWebDav = isWebDav,
                        webDavPath = webDavPath,
                        isSmb = isSmb,
                        smbShare = smbShare
                    )
                    onSave(config)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Text("Guardar y Conectar", color = DarkBackground, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = ElectricPurple,
    selectedLabelColor = TextPrimary,
    containerColor = DarkSurfaceVariant,
    labelColor = TextSecondary
)

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DarkSurfaceVariant,
    unfocusedContainerColor = DarkSurfaceVariant,
    focusedBorderColor = NeonCyan,
    unfocusedBorderColor = DarkCardBorder,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
