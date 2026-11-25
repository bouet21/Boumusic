package com.boumusic.desktop.downloader

import com.boumusic.desktop.data.MusicRepository
import com.boumusic.desktop.ftp.FtpManager
import com.boumusic.desktop.model.Song
import com.boumusic.desktop.smb.SmbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class TrackDownloaderEngine(
    private val repository: MusicRepository,
    private val ftpManager: FtpManager,
    private val smbManager: SmbManager
) {
    private val userHome = System.getProperty("user.home") ?: "."
    val binDir = File(userHome, ".boumusic/bin").apply { if (!exists()) mkdirs() }
    val tempDir = File(userHome, ".boumusic/temp_downloads").apply { if (!exists()) mkdirs() }

    private val isWindows = System.getProperty("os.name", "").lowercase().contains("windows")
    private val ytdlpName = if (isWindows) "yt-dlp.exe" else "yt-dlp"
    private val ffmpegName = if (isWindows) "ffmpeg.exe" else "ffmpeg"

    fun getYtDlpBinary(): File {
        val local = File(binDir, ytdlpName)
        if (local.exists()) return local
        // Buscar en PATH
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator) ?: emptyList()
        for (dir in pathDirs) {
            val f = File(dir, ytdlpName)
            if (f.exists() && f.canExecute()) return f
        }
        return local
    }

    fun getFfmpegBinary(): File {
        val local = File(binDir, ffmpegName)
        if (local.exists()) return local
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator) ?: emptyList()
        for (dir in pathDirs) {
            val f = File(dir, ffmpegName)
            if (f.exists() && f.canExecute()) return f
        }
        return local
    }

    private val progressPattern = Pattern.compile("\\[download\\]\\s+([0-9.]+)%\\s+of")

    /**
     * Descarga la pista y la guarda directamente en el destino (Local, SMB o FTP).
     */
    suspend fun executeDownload(
        task: DownloadTask,
        onUpdate: (DownloadTask) -> Unit
    ): Result<File?> = withContext(Dispatchers.IO) {
        var ytdlp = getYtDlpBinary()
        if (!ytdlp.exists()) {
            onUpdate(task.copy(stage = DownloadStage.SEARCHING_STREAM, statusMessage = "Descargando motor yt-dlp..."))
            ytdlp = autoDownloadYtDlp() ?: getYtDlpBinary()
            if (!ytdlp.exists()) {
                val err = "Motor yt-dlp no encontrado en ${ytdlp.absolutePath}"
                onUpdate(task.copy(stage = DownloadStage.ERROR, error = err))
                return@withContext Result.failure(Exception(err))
            }
        }

        val ffmpeg = getFfmpegBinary()

        val track = task.track
        val sanitizedBaseName = "${track.title} - ${track.artist}".replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val outTemplate = "${tempDir.absolutePath}${File.separator}$sanitizedBaseName.%(ext)s"

        onUpdate(task.copy(stage = DownloadStage.SEARCHING_STREAM, statusMessage = "Buscando mejor fuente de audio..."))

        // Consulta de búsqueda o enlace directo de YouTube
        val targetQuery = if (track.spotifyUrl.startsWith("http") && (track.spotifyUrl.contains("youtube.com") || track.spotifyUrl.contains("youtu.be"))) {
            track.spotifyUrl
        } else {
            "ytsearch1:${track.artist} - ${track.title} audio"
        }

        val cmd = mutableListOf(
            ytdlp.absolutePath,
            "--no-playlist",
            "--extract-audio",
            "--audio-format", "mp3",
            "--audio-quality", "0",
            "--add-metadata",
            "--output", outTemplate
        )

        if (ffmpeg.exists()) {
            cmd.add("--ffmpeg-location")
            cmd.add(ffmpeg.parentFile?.absolutePath ?: ffmpeg.absolutePath)
        }

        cmd.add(targetQuery)

        try {
            val pb = ProcessBuilder(cmd)
            pb.redirectErrorStream(true)
            val proc = pb.start()

            onUpdate(task.copy(stage = DownloadStage.DOWNLOADING, statusMessage = "Descargando stream de audio..."))

            val reader = proc.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: break
                val m = progressPattern.matcher(l)
                if (m.find()) {
                    val pct = m.group(1).toFloatOrNull() ?: 0f
                    val normalized = (pct / 100f).coerceIn(0f, 0.95f)
                    onUpdate(
                        task.copy(
                            stage = DownloadStage.DOWNLOADING,
                            progress = normalized,
                            statusMessage = "Descargando: %.1f%%".format(pct)
                        )
                    )
                }
            }

            val exitCode = proc.waitFor()
            if (exitCode != 0) {
                val err = "Error en el extractor de audio (código $exitCode)"
                onUpdate(task.copy(stage = DownloadStage.ERROR, error = err))
                return@withContext Result.failure(Exception(err))
            }

            // Buscar el archivo generado en tempDir
            val generatedFile = File(tempDir, "$sanitizedBaseName.mp3").takeIf { it.exists() }
                ?: tempDir.listFiles()?.firstOrNull { it.name.startsWith(sanitizedBaseName) && it.extension.lowercase() in setOf("mp3", "m4a", "opus") }

            if (generatedFile == null || !generatedFile.exists() || generatedFile.length() < 1024) {
                val err = "No se generó el archivo de audio"
                onUpdate(task.copy(stage = DownloadStage.ERROR, error = err))
                return@withContext Result.failure(Exception(err))
            }

            onUpdate(task.copy(stage = DownloadStage.CONVERTING, progress = 0.96f, statusMessage = "Procesando metadatos..."))

            // 1. Destino LOCAL
            if (task.destination.type == DestinationType.LOCAL) {
                val targetDir = task.destination.localFolder ?: repository.defaultMusicDir
                if (!targetDir.exists()) targetDir.mkdirs()
                val destFile = File(targetDir, generatedFile.name)
                generatedFile.copyTo(destFile, overwrite = true)
                generatedFile.delete()

                // Registrar en la biblioteca local inmediatamente
                val newSong = Song(
                    id = destFile.absolutePath.hashCode().toLong(),
                    title = track.title,
                    artist = track.artist,
                    album = track.album.ifBlank { targetDir.name },
                    duration = if (track.durationMs > 0) track.durationMs else 180000L,
                    path = destFile.absolutePath,
                    isRemote = false,
                    fileSize = destFile.length()
                )

                val current = repository.loadCachedSongs().toMutableList()
                current.removeAll { it.path == destFile.absolutePath }
                current.add(0, newSong)
                repository.saveCachedSongs(current)

                onUpdate(
                    task.copy(
                        stage = DownloadStage.COMPLETED,
                        progress = 1.0f,
                        statusMessage = "Guardado en biblioteca local",
                        outputFile = destFile
                    )
                )
                return@withContext Result.success(destFile)
            }

            // 2. Destino REMOTO (SMB, FTP o WebDAV)
            val serverConfig = task.destination.serverConfig
            if (serverConfig == null) {
                val err = "Servidor remoto no configurado"
                onUpdate(task.copy(stage = DownloadStage.ERROR, error = err))
                return@withContext Result.failure(Exception(err))
            }

            onUpdate(task.copy(stage = DownloadStage.UPLOADING, progress = 0.97f, statusMessage = "Subiendo a ${serverConfig.name}..."))

            val remoteDir = task.destination.remoteSubPath.ifBlank { "/" }
            val uploadRes = if (serverConfig.isSmb) {
                smbManager.uploadFile(serverConfig, remoteDir, generatedFile) { p ->
                    onUpdate(task.copy(stage = DownloadStage.UPLOADING, progress = 0.97f + (p * 0.03f), statusMessage = "Subiendo a SMB: ${(p * 100).toInt()}%"))
                }
            } else {
                ftpManager.uploadSong(serverConfig, remoteDir, generatedFile) { p ->
                    onUpdate(task.copy(stage = DownloadStage.UPLOADING, progress = 0.97f + (p * 0.03f), statusMessage = "Subiendo a FTP: ${(p * 100).toInt()}%"))
                }
            }

            generatedFile.delete()

            if (uploadRes.isSuccess) {
                onUpdate(
                    task.copy(
                        stage = DownloadStage.COMPLETED,
                        progress = 1.0f,
                        statusMessage = "Subido con éxito a ${serverConfig.name} ($remoteDir)"
                    )
                )
                return@withContext Result.success(null)
            } else {
                val err = "Error al subir a servidor: ${uploadRes.exceptionOrNull()?.message}"
                onUpdate(task.copy(stage = DownloadStage.ERROR, error = err))
                return@withContext Result.failure(Exception(err))
            }
        } catch (t: Throwable) {
            val err = "Excepción en descarga: ${t.message}"
            onUpdate(task.copy(stage = DownloadStage.ERROR, error = err))
            return@withContext Result.failure(t)
        }
    }

    private fun autoDownloadYtDlp(): File? {
        try {
            val downloadUrl = if (isWindows) {
                "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
            } else {
                "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"
            }
            val req = Request.Builder().url(downloadUrl).build()
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful && resp.body != null) {
                val target = File(binDir, ytdlpName)
                target.parentFile?.mkdirs()
                resp.body!!.byteStream().use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                resp.close()
                target.setExecutable(true)
                return target
            }
            resp.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
