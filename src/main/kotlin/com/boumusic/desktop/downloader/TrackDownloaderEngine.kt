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
import java.io.FileOutputStream
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

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun getYtDlpBinary(): File {
        val local = File(binDir, ytdlpName)
        if (local.exists()) return local
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
        val track = task.track
        val sanitizedBaseName = "${track.title} - ${track.artist}".replace(Regex("[\\\\/:*?\"<>|]"), "_")

        onUpdate(task.copy(stage = DownloadStage.SEARCHING_STREAM, statusMessage = "Buscando mejor fuente de audio..."))

        // =========================================================================
        // 1. INTENTO DE DESCARGA DIRECTA DE ALTA FIDELIDAD (JioSaavn CDN 320/160kbps)
        // Rápido, sin dependencias externas de ffmpeg ni bloqueos de Cloudflare.
        // =========================================================================
        try {
            val directStream = HighFidelityAudioResolver.resolveDirectStream(track.artist, track.title)
            if (directStream != null) {
                onUpdate(task.copy(stage = DownloadStage.DOWNLOADING, statusMessage = "Descargando stream de alta fidelidad (320kbps)...", progress = 0.05f))
                val targetExt = if (directStream.streamUrl.endsWith(".mp4") || directStream.streamUrl.endsWith(".m4a")) "m4a" else "mp3"
                val outFile = File(tempDir, "$sanitizedBaseName.$targetExt")

                val downloaded = downloadHttpStream(directStream.streamUrl, directStream.fallbackUrl, outFile) { progress ->
                    onUpdate(
                        task.copy(
                            stage = DownloadStage.DOWNLOADING,
                            progress = 0.05f + (progress * 0.90f),
                            statusMessage = "Descargando: ${(progress * 100).toInt()}%"
                        )
                    )
                }

                if (downloaded && outFile.exists() && outFile.length() > 1024) {
                    return@withContext finalizeDownload(task, outFile, onUpdate)
                }
            }
        } catch (_: Exception) {}

        // =========================================================================
        // 2. FALLBACK A MOTOR YT-DLP (YouTube Music / YouTube)
        // =========================================================================
        var ytdlp = getYtDlpBinary()
        if (!ytdlp.exists()) {
            onUpdate(task.copy(stage = DownloadStage.SEARCHING_STREAM, statusMessage = "Descargando motor de extracción..."))
            ytdlp = autoDownloadYtDlp() ?: getYtDlpBinary()
            if (!ytdlp.exists()) {
                val err = "Motor yt-dlp no encontrado en ${ytdlp.absolutePath}"
                onUpdate(task.copy(stage = DownloadStage.ERROR, error = err, statusMessage = err))
                return@withContext Result.failure(Exception(err))
            }
        }

        val ffmpeg = getFfmpegBinary()
        val outTemplate = "${tempDir.absolutePath}${File.separator}$sanitizedBaseName.%(ext)s"

        onUpdate(task.copy(stage = DownloadStage.SEARCHING_STREAM, statusMessage = "Localizando stream en YouTube..."))

        val cleanArtist = java.text.Normalizer.normalize(track.artist, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        val cleanTitle = java.text.Normalizer.normalize(track.title, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

        val targetQuery = if (track.spotifyUrl.startsWith("http") && (track.spotifyUrl.contains("youtube.com") || track.spotifyUrl.contains("youtu.be"))) {
            track.spotifyUrl
        } else {
            "ytsearch1:$cleanArtist - $cleanTitle audio"
        }

        val cmd = mutableListOf(
            ytdlp.absolutePath,
            "--no-playlist",
            "--add-metadata",
            "--output", outTemplate
        )

        if (ffmpeg.exists()) {
            // Si hay ffmpeg, extraer y convertir a MP3 limpio
            cmd.addAll(listOf("--extract-audio", "--audio-format", "mp3", "--audio-quality", "0"))
            cmd.add("--ffmpeg-location")
            cmd.add(ffmpeg.parentFile?.absolutePath ?: ffmpeg.absolutePath)
        } else {
            // Si NO hay ffmpeg, descargar stream nativo M4A/AAC sin requerir conversión
            cmd.addAll(listOf("-f", "ba[ext=m4a]/ba/b"))
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
                onUpdate(task.copy(stage = DownloadStage.ERROR, error = err, statusMessage = err))
                return@withContext Result.failure(Exception(err))
            }

            // Buscar el archivo generado en tempDir
            val generatedFile = File(tempDir, "$sanitizedBaseName.mp3").takeIf { it.exists() }
                ?: File(tempDir, "$sanitizedBaseName.m4a").takeIf { it.exists() }
                ?: tempDir.listFiles()?.firstOrNull { it.name.startsWith(sanitizedBaseName) && it.extension.lowercase() in setOf("mp3", "m4a", "opus", "webm") }

            if (generatedFile == null || !generatedFile.exists() || generatedFile.length() < 1024) {
                val err = "No se generó el archivo de audio"
                onUpdate(task.copy(stage = DownloadStage.ERROR, error = err, statusMessage = err))
                return@withContext Result.failure(Exception(err))
            }

            return@withContext finalizeDownload(task, generatedFile, onUpdate)
        } catch (t: Throwable) {
            val err = "Excepción en descarga: ${t.message}"
            onUpdate(task.copy(stage = DownloadStage.ERROR, error = err, statusMessage = err))
            return@withContext Result.failure(t)
        }
    }

    private suspend fun finalizeDownload(
        task: DownloadTask,
        generatedFile: File,
        onUpdate: (DownloadTask) -> Unit
    ): Result<File?> = withContext(Dispatchers.IO) {
        val track = task.track
        onUpdate(task.copy(stage = DownloadStage.CONVERTING, progress = 0.96f, statusMessage = "Procesando metadatos..."))

        // 1. Destino LOCAL
        if (task.destination.type == DestinationType.LOCAL) {
            val targetDir = task.destination.localFolder ?: repository.defaultMusicDir
            if (!targetDir.exists()) targetDir.mkdirs()
            val destFile = File(targetDir, generatedFile.name)

            if (destFile.canonicalPath != generatedFile.canonicalPath) {
                generatedFile.copyTo(destFile, overwrite = true)
                generatedFile.delete()
            }

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
            onUpdate(task.copy(stage = DownloadStage.ERROR, error = err, statusMessage = err))
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
            onUpdate(task.copy(stage = DownloadStage.ERROR, error = err, statusMessage = err))
            return@withContext Result.failure(Exception(err))
        }
    }

    private fun downloadHttpStream(
        primaryUrl: String,
        fallbackUrl: String,
        destFile: File,
        onProgress: (Float) -> Unit
    ): Boolean {
        val urls = listOf(primaryUrl, fallbackUrl).distinct()
        for (u in urls) {
            try {
                val req = Request.Builder()
                    .url(u)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                val resp = httpClient.newCall(req).execute()
                if (resp.isSuccessful && resp.body != null) {
                    val body = resp.body!!
                    val total = body.contentLength()
                    val inStream = body.byteStream()
                    val outStream = FileOutputStream(destFile)
                    inStream.use { inp ->
                        outStream.use { outp ->
                            val buf = ByteArray(32 * 1024)
                            var read: Int
                            var written = 0L
                            while (inp.read(buf).also { read = it } != -1) {
                                outp.write(buf, 0, read)
                                written += read
                                if (total > 0) {
                                    onProgress(written.toFloat() / total)
                                }
                            }
                        }
                    }
                    resp.close()
                    if (destFile.exists() && destFile.length() > 1024) return true
                } else {
                    resp.close()
                }
            } catch (_: Exception) {}
        }
        return false
    }

    private fun autoDownloadYtDlp(): File? {
        try {
            val downloadUrl = if (isWindows) {
                "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
            } else {
                "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"
            }
            val req = Request.Builder().url(downloadUrl).build()
            val resp = httpClient.newCall(req).execute()
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
                if (!isWindows) {
                    try {
                        Runtime.getRuntime().exec(arrayOf("chmod", "+x", target.absolutePath)).waitFor()
                    } catch (_: Exception) {}
                }
                return target
            }
            resp.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
