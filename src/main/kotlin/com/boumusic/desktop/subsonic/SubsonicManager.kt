package com.boumusic.desktop.subsonic

import com.boumusic.desktop.model.FtpConfig
import com.boumusic.desktop.model.FtpItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

class SubsonicManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun buildBaseUrl(config: FtpConfig): String {
        var rawHost = config.host.trim().removeSuffix("/")
        if (!rawHost.startsWith("http://") && !rawHost.startsWith("https://")) {
            val scheme = if (config.isFtps || config.port == 443) "https" else "http"
            rawHost = "$scheme://$rawHost"
        }

        return try {
            val uri = URI(rawHost)
            val scheme = uri.scheme ?: "http"
            val host = uri.host ?: config.host.trim()
            val hasExplicitPort = uri.port != -1
            val effectivePort = if (hasExplicitPort) uri.port else config.port
            val isDefaultPort = (scheme == "http" && effectivePort == 80) || (scheme == "https" && effectivePort == 443)
            val portPart = if (isDefaultPort) "" else ":$effectivePort"
            val path = (uri.path ?: "").trimEnd('/')
            "$scheme://$host$portPart$path"
        } catch (_: Exception) {
            rawHost
        }
    }

    fun getAuthParams(config: FtpConfig): String {
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val md = MessageDigest.getInstance("MD5")
        val raw = config.password + salt
        val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
        val token = digest.joinToString("") { "%02x".format(it) }
        val userEncoded = URLEncoder.encode(config.username.trim(), "UTF-8")
        return "u=$userEncoded&t=$token&s=$salt&v=1.16.1&c=Boumusic&f=json"
    }

    private fun executeRequest(config: FtpConfig, endpoint: String, extraParams: String = ""): JSONObject {
        val baseUrl = buildBaseUrl(config)
        val auth = getAuthParams(config)
        val sep = if (extraParams.isNotBlank()) "&$extraParams" else ""
        val fullUrl = "$baseUrl/rest/$endpoint?$auth$sep"

        val request = Request.Builder()
            .url(fullUrl)
            .get()
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: throw Exception("Respuesta vacía del servidor Subsonic")
        response.close()

        val root = JSONObject(bodyStr)
        val subResp = root.optJSONObject("subsonic-response")
            ?: throw Exception("Respuesta inválida de Subsonic: no contiene 'subsonic-response'")

        val status = subResp.optString("status", "")
        if (status != "ok") {
            val error = subResp.optJSONObject("error")
            val msg = error?.optString("message") ?: "Error desconocido en el servidor Subsonic (status=$status)"
            throw Exception(msg)
        }
        return subResp
    }

    suspend fun testConnection(config: FtpConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val resp = executeRequest(config, "ping.view")
            val status = resp.optString("status", "")
            val version = resp.optString("version", "desconocida")
            if (status == "ok") {
                Result.success(true)
            } else {
                Result.failure(Exception("Subsonic ping falló con estado '$status' (v$version)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listDirectory(config: FtpConfig, remotePath: String): Result<List<FtpItem>> = withContext(Dispatchers.IO) {
        try {
            val cleanPath = remotePath.trim()
            val items = mutableListOf<FtpItem>()

            when {
                cleanPath.isBlank() || cleanPath == "/" -> {
                    items.add(FtpItem(name = "🎵 Artistas", path = "/artists", isDirectory = true))
                    items.add(FtpItem(name = "📁 Carpetas de Música", path = "/folders", isDirectory = true))
                    items.add(FtpItem(name = "🔥 Álbumes Recientes", path = "/recent", isDirectory = true))
                    items.add(FtpItem(name = "🎲 Canciones Aleatorias", path = "/random", isDirectory = true))
                }

                cleanPath == "/artists" -> {
                    val resp = executeRequest(config, "getArtists.view")
                    val artistsObj = resp.optJSONObject("artists")
                    val indexArray = artistsObj?.optJSONArray("index")
                    if (indexArray != null) {
                        for (i in 0 until indexArray.length()) {
                            val idxObj = indexArray.getJSONObject(i)
                            val artistArray = idxObj.optJSONArray("artist") ?: continue
                            for (j in 0 until artistArray.length()) {
                                val art = artistArray.getJSONObject(j)
                                val id = art.optString("id")
                                val name = art.optString("name", "Artista desconocido")
                                items.add(FtpItem(name = name, path = "/artist/$id", isDirectory = true))
                            }
                        }
                    }
                }

                cleanPath.startsWith("/artist/") -> {
                    val artistId = cleanPath.removePrefix("/artist/")
                    val resp = executeRequest(config, "getArtist.view", "id=${URLEncoder.encode(artistId, "UTF-8")}")
                    val artistObj = resp.optJSONObject("artist")
                    val albumArray = artistObj?.optJSONArray("album")
                    if (albumArray != null) {
                        for (i in 0 until albumArray.length()) {
                            val alb = albumArray.getJSONObject(i)
                            val id = alb.optString("id")
                            val title = alb.optString("name", alb.optString("title", "Álbum desconocido"))
                            val year = alb.optInt("year", 0)
                            val displayName = if (year > 0) "$title ($year)" else title
                            items.add(FtpItem(name = displayName, path = "/album/$id", isDirectory = true))
                        }
                    }
                }

                cleanPath.startsWith("/album/") -> {
                    val albumId = cleanPath.removePrefix("/album/")
                    val resp = executeRequest(config, "getAlbum.view", "id=${URLEncoder.encode(albumId, "UTF-8")}")
                    val albumObj = resp.optJSONObject("album")
                    val songArray = albumObj?.optJSONArray("song")
                    if (songArray != null) {
                        for (i in 0 until songArray.length()) {
                            val s = songArray.getJSONObject(i)
                            val id = s.optString("id")
                            val title = s.optString("title", "Pista ${i + 1}")
                            val suffix = s.optString("suffix", "mp3")
                            val track = s.optInt("track", i + 1)
                            val size = s.optLong("size", 0L)
                            val trackPrefix = if (track > 0) String.format("%02d. ", track) else ""
                            val fileName = "$trackPrefix$title.$suffix"
                            items.add(FtpItem(name = fileName, path = "/song/$id", isDirectory = false, size = size))
                        }
                    }
                }

                cleanPath == "/folders" -> {
                    val resp = executeRequest(config, "getMusicFolders.view")
                    val foldersObj = resp.optJSONObject("musicFolders")
                    val folderArray = foldersObj?.optJSONArray("musicFolder")
                    if (folderArray != null) {
                        for (i in 0 until folderArray.length()) {
                            val f = folderArray.getJSONObject(i)
                            val id = f.optString("id")
                            val name = f.optString("name", "Carpeta ${i + 1}")
                            items.add(FtpItem(name = name, path = "/folder/$id", isDirectory = true))
                        }
                    }
                }

                cleanPath.startsWith("/folder/") -> {
                    val folderId = cleanPath.removePrefix("/folder/")
                    val resp = executeRequest(config, "getMusicDirectory.view", "id=${URLEncoder.encode(folderId, "UTF-8")}")
                    val dirObj = resp.optJSONObject("directory")
                    val childArray = dirObj?.optJSONArray("child")
                    if (childArray != null) {
                        for (i in 0 until childArray.length()) {
                            val c = childArray.getJSONObject(i)
                            val isDir = c.optBoolean("isDir", false)
                            val id = c.optString("id")
                            val title = c.optString("title", c.optString("name", "Elemento"))
                            val suffix = c.optString("suffix", "mp3")
                            val size = c.optLong("size", 0L)
                            if (isDir) {
                                items.add(FtpItem(name = title, path = "/folder/$id", isDirectory = true))
                            } else {
                                items.add(FtpItem(name = "$title.$suffix", path = "/song/$id", isDirectory = false, size = size))
                            }
                        }
                    }
                }

                cleanPath == "/recent" -> {
                    val resp = executeRequest(config, "getAlbumList2.view", "type=recent&size=50")
                    val listObj = resp.optJSONObject("albumList2")
                    val albumArray = listObj?.optJSONArray("album")
                    if (albumArray != null) {
                        for (i in 0 until albumArray.length()) {
                            val alb = albumArray.getJSONObject(i)
                            val id = alb.optString("id")
                            val title = alb.optString("title", alb.optString("name", "Álbum"))
                            val artist = alb.optString("artist", "")
                            val displayName = if (artist.isNotBlank()) "$artist - $title" else title
                            items.add(FtpItem(name = displayName, path = "/album/$id", isDirectory = true))
                        }
                    }
                }

                cleanPath == "/random" -> {
                    val resp = executeRequest(config, "getRandomSongs.view", "size=50")
                    val randomObj = resp.optJSONObject("randomSongs")
                    val songArray = randomObj?.optJSONArray("song")
                    if (songArray != null) {
                        for (i in 0 until songArray.length()) {
                            val s = songArray.getJSONObject(i)
                            val id = s.optString("id")
                            val title = s.optString("title", "Canción ${i + 1}")
                            val artist = s.optString("artist", "")
                            val suffix = s.optString("suffix", "mp3")
                            val size = s.optLong("size", 0L)
                            val displayName = if (artist.isNotBlank()) "$artist - $title.$suffix" else "$title.$suffix"
                            items.add(FtpItem(name = displayName, path = "/song/$id", isDirectory = false, size = size))
                        }
                    }
                }
            }

            val sorted = items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openStream(config: FtpConfig, remotePath: String): InputStream {
        val songId = when {
            remotePath.startsWith("/song/") -> remotePath.removePrefix("/song/").substringBefore("?")
            remotePath.contains("id=") -> remotePath.substringAfter("id=").substringBefore("&")
            else -> remotePath.trim().removePrefix("/")
        }

        val baseUrl = buildBaseUrl(config)
        val auth = getAuthParams(config)
        val streamUrl = "$baseUrl/rest/stream.view?id=${URLEncoder.encode(songId, "UTF-8")}&$auth"

        val request = Request.Builder()
            .url(streamUrl)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw Exception("HTTP ${response.code} al obtener stream de audio en Subsonic: ${response.message}")
        }

        val bodyStream = response.body?.byteStream() ?: throw Exception("Stream de audio nulo en respuesta Subsonic")

        return object : InputStream() {
            override fun read(): Int = bodyStream.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = bodyStream.read(b, off, len)
            override fun available(): Int = bodyStream.available()
            override fun close() {
                try { bodyStream.close() } catch (_: Exception) {}
                try { response.close() } catch (_: Exception) {}
            }
        }
    }

    suspend fun downloadFile(config: FtpConfig, remotePath: String, destFile: File, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val songId = when {
            remotePath.startsWith("/song/") -> remotePath.removePrefix("/song/").substringBefore("?")
            remotePath.contains("id=") -> remotePath.substringAfter("id=").substringBefore("&")
            else -> remotePath.trim().removePrefix("/")
        }

        val baseUrl = buildBaseUrl(config)
        val auth = getAuthParams(config)
        val downloadUrl = "$baseUrl/rest/download.view?id=${URLEncoder.encode(songId, "UTF-8")}&$auth"

        val request = Request.Builder().url(downloadUrl).get().build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            return@withContext false
        }

        val body = response.body ?: run {
            response.close()
            return@withContext false
        }

        destFile.parentFile?.mkdirs()
        val totalBytes = body.contentLength()
        var downloadedBytes = 0L

        body.byteStream().use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8192)
                var bytes: Int
                while (input.read(buffer).also { bytes = it } >= 0) {
                    output.write(buffer, 0, bytes)
                    downloadedBytes += bytes
                    if (totalBytes > 0) {
                        val progress = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        onProgress(progress)
                    }
                }
            }
        }
        response.close()
        true
    }

    suspend fun listAllAudioRecursive(config: FtpConfig): Result<List<FtpItem>> = withContext(Dispatchers.IO) {
        try {
            // Trae un catálogo grande de canciones aleatorias desde la biblioteca del servidor
            val resp = executeRequest(config, "getRandomSongs.view", "size=500")
            val randomObj = resp.optJSONObject("randomSongs")
            val songArray = randomObj?.optJSONArray("song")
            val items = mutableListOf<FtpItem>()
            if (songArray != null) {
                for (i in 0 until songArray.length()) {
                    val s = songArray.getJSONObject(i)
                    val id = s.optString("id")
                    val title = s.optString("title", "Canción ${i + 1}")
                    val artist = s.optString("artist", "")
                    val suffix = s.optString("suffix", "mp3")
                    val size = s.optLong("size", 0L)
                    val displayName = if (artist.isNotBlank()) "$artist - $title.$suffix" else "$title.$suffix"
                    items.add(FtpItem(name = displayName, path = "/song/$id", isDirectory = false, size = size))
                }
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
