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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boumusic.desktop.ftp.FtpManager
import com.boumusic.desktop.model.FtpConfig
import com.boumusic.desktop.ui.theme.DarkBackground
import com.boumusic.desktop.ui.theme.DarkCardBorder
import com.boumusic.desktop.ui.theme.DarkSurface
import com.boumusic.desktop.ui.theme.DarkSurfaceVariant
import com.boumusic.desktop.ui.theme.ElectricPurple
import com.boumusic.desktop.ui.theme.NeonCyan
import com.boumusic.desktop.ui.theme.TextPrimary
import com.boumusic.desktop.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun ServerDialog(
    initialConfig: FtpConfig? = null,
    ftpManager: FtpManager? = null,
    onSave: (FtpConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initialConfig?.name ?: "Mi Servidor") }
    var host by remember { mutableStateOf(initialConfig?.host ?: "") }
    var portStr by remember { mutableStateOf(initialConfig?.port?.toString() ?: "21") }
    var username by remember { mutableStateOf(initialConfig?.username ?: "") }
    var password by remember { mutableStateOf(initialConfig?.password ?: "") }
    var isFtps by remember { mutableStateOf(initialConfig?.isFtps ?: false) }
    var isWebDav by remember { mutableStateOf(initialConfig?.isWebDav ?: false) }
    var webDavPath by remember { mutableStateOf(initialConfig?.webDavPath ?: "/") }
    var isSmb by remember { mutableStateOf(initialConfig?.isSmb ?: false) }
    var smbShare by remember { mutableStateOf(initialConfig?.smbShare ?: "Musica") }
    var isSubsonic by remember { mutableStateOf(initialConfig?.isSubsonic ?: false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    val scrollState = rememberScrollState()

    fun buildConfig(): FtpConfig {
        val defaultPort = when {
            isSubsonic -> 4533
            isSmb -> 445
            isWebDav -> 443
            isFtps -> 21
            else -> 21
        }
        var cleanHost = host.trim()
            .removePrefix("ftp://")
            .removePrefix("ftps://")
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("smb://")
            .removePrefix("\\\\")
            .trimEnd('/')

        var port = portStr.toIntOrNull() ?: defaultPort
        if (cleanHost.contains(":")) {
            val colonIdx = cleanHost.lastIndexOf(':')
            val pCandidate = cleanHost.substring(colonIdx + 1).toIntOrNull()
            if (pCandidate != null) {
                port = pCandidate
                cleanHost = cleanHost.substring(0, colonIdx)
            }
        }

        return FtpConfig(
            id = initialConfig?.id ?: java.util.UUID.randomUUID().toString(),
            name = name.ifBlank { if (isSubsonic) "Navidrome / Subsonic" else "Servidor" },
            host = cleanHost,
            port = port,
            username = username.trim().ifBlank { if (isSmb) "guest" else "anonymous" },
            password = password,
            isFtps = isFtps,
            isWebDav = isWebDav,
            webDavPath = webDavPath,
            isSmb = isSmb,
            smbShare = smbShare,
            isSubsonic = isSubsonic
        )
    }

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp)
            ) {
                // Selector de protocolo
                Text("Protocolo de conexión:", color = TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = !isSmb && !isWebDav && !isFtps && !isSubsonic,
                        onClick = {
                            isSmb = false
                            isWebDav = false
                            isFtps = false
                            isSubsonic = false
                            portStr = "21"
                        },
                        label = { Text("FTP", fontSize = 11.sp) },
                        colors = chipColors()
                    )
                    FilterChip(
                        selected = isFtps,
                        onClick = {
                            isSmb = false
                            isWebDav = false
                            isFtps = true
                            isSubsonic = false
                            portStr = "21"
                        },
                        label = { Text("FTPS", fontSize = 11.sp) },
                        colors = chipColors()
                    )
                    FilterChip(
                        selected = isSmb,
                        onClick = {
                            isSmb = true
                            isWebDav = false
                            isFtps = false
                            isSubsonic = false
                            portStr = "445"
                        },
                        label = { Text("SMB", fontSize = 11.sp) },
                        colors = chipColors()
                    )
                    FilterChip(
                        selected = isWebDav,
                        onClick = {
                            isSmb = false
                            isWebDav = true
                            isFtps = false
                            isSubsonic = false
                            portStr = "443"
                        },
                        label = { Text("WebDAV", fontSize = 11.sp) },
                        colors = chipColors()
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = isSubsonic,
                        onClick = {
                            isSmb = false
                            isWebDav = false
                            isFtps = false
                            isSubsonic = true
                            portStr = "4533"
                        },
                        label = { Text("Subsonic / Navidrome / Jellyfin", fontSize = 11.sp) },
                        colors = chipColors()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                    onValueChange = { input ->
                        var h = input.trim()
                            .removePrefix("ftp://")
                            .removePrefix("ftps://")
                            .removePrefix("http://")
                            .removePrefix("https://")
                            .removePrefix("smb://")
                            .removePrefix("\\\\")
                        if (h.contains(":")) {
                            val parts = h.split(":")
                            h = parts[0]
                            val p = parts.getOrNull(1)?.filter { it.isDigit() } ?: ""
                            if (p.isNotBlank()) {
                                portStr = p
                            }
                        }
                        host = h
                    },
                    label = { Text("Dirección IP o Host (Ej: 192.168.1.50)", fontSize = 11.sp) },
                    placeholder = { Text("192.168.1.50 o midominio.com", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors()
                )

                if (isSmb) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = smbShare,
                        onValueChange = { smbShare = it },
                        label = { Text("Nombre del recurso compartido (Ej: Musica)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = fieldColors()
                    )
                }

                if (isWebDav) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = webDavPath,
                        onValueChange = { webDavPath = it },
                        label = { Text("Ruta WebDAV (Ej: /remote.php/webdav/)", fontSize = 11.sp) },
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
                        label = { Text("Usuario (vacío = anonymous)", fontSize = 11.sp) },
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

                Spacer(modifier = Modifier.height(14.dp))

                // Botón de probar conexión
                if (ftpManager != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                if (host.isNotBlank()) {
                                    isTesting = true
                                    testResult = null
                                    scope.launch {
                                        val conf = buildConfig()
                                        val res = ftpManager.testConnection(conf)
                                        isTesting = false
                                        testResult = if (res.isSuccess) {
                                            true to "✓ Conexión establecida con éxito"
                                        } else {
                                            false to (res.exceptionOrNull()?.message ?: "Error al conectar")
                                        }
                                    }
                                }
                            },
                            enabled = !isTesting && host.isNotBlank(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Probando...", fontSize = 11.sp)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Probar conexión", color = NeonCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    testResult?.let { (success, msg) ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (success) NeonCyan.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color(0xFFEF4444).copy(alpha = 0.15f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = msg,
                                color = if (success) NeonCyan else androidx.compose.ui.graphics.Color(0xFFEF4444),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val config = buildConfig()
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
