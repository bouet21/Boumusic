package com.boumusic.desktop.downloader

import com.boumusic.desktop.model.FtpConfig
import java.io.File

data class DownloadTrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val coverUrl: String? = null,
    val spotifyUrl: String = ""
) {
    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "--:--"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val displaySubtitle: String
        get() = if (album.isNotBlank()) "$artist • $album" else artist
}

enum class DestinationType(val label: String) {
    LOCAL("Biblioteca Local"),
    SMB("Servidor SMB (Carpeta de red)"),
    FTP("Servidor FTP / FTPS"),
    WEBDAV("Nube WebDAV")
}

data class DownloadDestination(
    val type: DestinationType,
    val label: String,
    val localFolder: File? = null,
    val serverConfig: FtpConfig? = null,
    val remoteSubPath: String = ""
)

enum class DownloadStage(val label: String) {
    QUEUED("En cola"),
    SEARCHING_STREAM("Localizando audio de alta calidad..."),
    DOWNLOADING("Descargando audio..."),
    CONVERTING("Codificando MP3 y metadatos..."),
    UPLOADING("Subiendo al servidor remoto..."),
    COMPLETED("Descarga completada"),
    ERROR("Error en la descarga")
}

data class DownloadTask(
    val id: String,
    val track: DownloadTrackItem,
    val destination: DownloadDestination,
    val stage: DownloadStage = DownloadStage.QUEUED,
    val progress: Float = 0f, // 0.0 a 1.0
    val statusMessage: String = "",
    val error: String? = null,
    val outputFile: File? = null
)
