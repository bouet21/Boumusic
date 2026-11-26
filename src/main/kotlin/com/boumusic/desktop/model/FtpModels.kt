package com.boumusic.desktop.model

import java.util.UUID

data class FtpConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Servidor",
    val host: String = "",
    val port: Int = 21,
    val username: String = "anonymous",
    val password: String = "",
    val isFtps: Boolean = false,
    val isWebDav: Boolean = false,
    val webDavPath: String = "/",
    val isSmb: Boolean = false,
    val smbShare: String = "",
    val isSubsonic: Boolean = false
) {
    val protocolLabel: String
        get() = when {
            isSubsonic -> "Subsonic / Navidrome"
            isSmb -> "SMB (Windows/Linux)"
            isWebDav -> "WebDAV"
            isFtps -> "FTPS"
            else -> "FTP"
        }

    val cleanSmbShare: String
        get() = smbShare.trim().removePrefix("/").removePrefix("\\").trimEnd('/', '\\')
}

data class FtpItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "Carpeta"
            val mb = size / (1024.0 * 1024.0)
            return String.format("%.2f MB", mb)
        }

    val isAudio: Boolean
        get() = !isDirectory && name.matches(Regex("(?i).*\\.(mp3|flac|m4a|wav|ogg|aac|opus)$"))
}
