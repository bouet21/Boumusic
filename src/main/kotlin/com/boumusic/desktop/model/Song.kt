package com.boumusic.desktop.model

import java.io.File

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val isRemote: Boolean = false,
    val remoteHost: String = "",
    val remotePath: String = "",
    val genre: String = "Varios",
    val fileSize: Long = 0L
) {
    val formattedDuration: String
        get() {
            if (duration <= 0) return if (isRemote) "--:--" else "00:00"
            val totalSeconds = (duration / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val remainingSeconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, remainingSeconds)
        }

    val fileName: String
        get() = if (path.isNotBlank()) File(path).name else "$title.mp3"
}
