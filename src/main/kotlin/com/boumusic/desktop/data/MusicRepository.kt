package com.boumusic.desktop.data

import com.boumusic.desktop.audio.AudioMetadataHelper
import com.boumusic.desktop.model.FtpConfig
import com.boumusic.desktop.model.Playlist
import com.boumusic.desktop.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class MusicRepository {

    private val userHome: String = System.getProperty("user.home") ?: "."
    private val configDir = File(userHome, ".boumusic").apply { if (!exists()) mkdirs() }
    private val configFile = File(configDir, "config.json")
    private val cacheFile = File(configDir, "library_cache.json")

    private val candidateFolders: List<File> by lazy {
        listOf(
            File(userHome, "Music"),
            File(userHome, "Música"),
            File(userHome, "OneDrive" + File.separator + "Music"),
            File(userHome, "Downloads"),
            File(userHome, "Descargas")
        ).filter { it.exists() && it.isDirectory }
    }

    var defaultMusicDir: File = candidateFolders.firstOrNull() ?: File(userHome, "Music").apply { mkdirs() }

    fun loadCachedSongs(): List<Song> {
        if (!cacheFile.exists()) return emptyList()
        val songs = mutableListOf<Song>()
        try {
            val arr = JSONArray(cacheFile.readText(Charsets.UTF_8))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val path = o.optString("path", "")
                if (path.isNotBlank() && File(path).exists()) {
                    songs.add(
                        Song(
                            id = o.optLong("id", path.hashCode().toLong()),
                            title = o.optString("title", File(path).nameWithoutExtension),
                            artist = o.optString("artist", "Artista"),
                            album = o.optString("album", "Álbum"),
                            duration = o.optLong("duration", 0L),
                            path = path,
                            isRemote = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return songs
    }

    fun saveCachedSongs(songs: List<Song>) {
        try {
            val arr = JSONArray()
            for (s in songs) {
                if (s.isRemote) continue
                val o = JSONObject()
                o.put("id", s.id)
                o.put("title", s.title)
                o.put("artist", s.artist)
                o.put("album", s.album)
                o.put("duration", s.duration)
                o.put("path", s.path)
                arr.put(o)
            }
            cacheFile.writeText(arr.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun scanLocalSongs(folder: File = defaultMusicDir): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val foldersToScan = if (folder == defaultMusicDir && candidateFolders.isNotEmpty()) candidateFolders else listOf(folder)

        val audioExtensions = setOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus")
        val excludedDirNames = setOf("appdata", "node_modules", "temp", ".gradle", ".git", "windows", "program files")

        for (dir in foldersToScan) {
            if (!dir.exists() || !dir.isDirectory) continue
            dir.walkTopDown()
                .onEnter { f ->
                    !f.name.startsWith(".") && f.name.lowercase() !in excludedDirNames
                }
                .maxDepth(4)
                .filter { it.isFile && it.extension.lowercase() in audioExtensions }
                .forEach { file ->
                    val baseName = file.nameWithoutExtension
                    val parts = baseName.split(" - ", limit = 2)
                    val artist = if (parts.size == 2) parts[0].trim() else file.parentFile?.name ?: "Artista"
                    val title = if (parts.size == 2) parts[1].trim() else baseName

                    val durationMs = AudioMetadataHelper.detectFileDurationMs(file)
                    songs.add(
                        Song(
                            id = file.absolutePath.hashCode().toLong(),
                            title = title,
                            artist = artist,
                            album = file.parentFile?.name ?: "Álbum",
                            duration = durationMs,
                            path = file.absolutePath,
                            isRemote = false
                        )
                    )
                }
        }

        val result = songs.distinctBy { it.path }.sortedBy { it.title.lowercase() }
        saveCachedSongs(result)
        result
    }

    suspend fun loadSavedServers(): List<FtpConfig> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FtpConfig>()
        if (!configFile.exists()) return@withContext list
        try {
            val root = JSONObject(configFile.readText(Charsets.UTF_8))
            val arr = root.optJSONArray("servers") ?: return@withContext list
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    FtpConfig(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = o.optString("name", "Servidor"),
                        host = o.optString("host", ""),
                        port = o.optInt("port", 21),
                        username = o.optString("username", "anonymous"),
                        password = o.optString("password", ""),
                        isFtps = o.optBoolean("isFtps", false),
                        isWebDav = o.optBoolean("isWebDav", false),
                        webDavPath = o.optString("webDavPath", "/"),
                        isSmb = o.optBoolean("isSmb", false),
                        smbShare = o.optString("smbShare", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun saveServers(servers: List<FtpConfig>) = withContext(Dispatchers.IO) {
        try {
            val root = if (configFile.exists()) JSONObject(configFile.readText(Charsets.UTF_8)) else JSONObject()
            val arr = JSONArray()
            for (c in servers) {
                val o = JSONObject()
                o.put("id", c.id)
                o.put("name", c.name)
                o.put("host", c.host)
                o.put("port", c.port)
                o.put("username", c.username)
                o.put("password", c.password)
                o.put("isFtps", c.isFtps)
                o.put("isWebDav", c.isWebDav)
                o.put("webDavPath", c.webDavPath)
                o.put("isSmb", c.isSmb)
                o.put("smbShare", c.smbShare)
                arr.put(o)
            }
            root.put("servers", arr)
            configFile.writeText(root.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun loadPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Playlist>()
        if (!configFile.exists()) return@withContext list
        try {
            val root = JSONObject(configFile.readText(Charsets.UTF_8))
            val arr = root.optJSONArray("playlists") ?: return@withContext list
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.optString("id", UUID.randomUUID().toString())
                val name = o.optString("name", "Lista")
                val createdAt = o.optLong("createdAt", System.currentTimeMillis())

                val songIds = mutableListOf<Long>()
                val sArr = o.optJSONArray("songIds")
                if (sArr != null) {
                    for (j in 0 until sArr.length()) songIds.add(sArr.getLong(j))
                }

                val remoteSongs = mutableListOf<Song>()
                val rArr = o.optJSONArray("remoteSongs")
                if (rArr != null) {
                    for (k in 0 until rArr.length()) {
                        val r = rArr.getJSONObject(k)
                        remoteSongs.add(
                            Song(
                                id = r.optLong("id", System.currentTimeMillis()),
                                title = r.optString("title", "Canción Remota"),
                                artist = r.optString("artist", "Remoto"),
                                album = r.optString("album", ""),
                                duration = 0L,
                                path = r.optString("path", ""),
                                isRemote = true,
                                remoteHost = r.optString("remoteHost", ""),
                                remotePath = r.optString("remotePath", "")
                            )
                        )
                    }
                }

                list.add(Playlist(id, name, songIds, remoteSongs, createdAt))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun savePlaylists(playlists: List<Playlist>) = withContext(Dispatchers.IO) {
        try {
            val root = if (configFile.exists()) JSONObject(configFile.readText(Charsets.UTF_8)) else JSONObject()
            val arr = JSONArray()
            for (p in playlists) {
                val o = JSONObject()
                o.put("id", p.id)
                o.put("name", p.name)
                o.put("createdAt", p.createdAt)
                val sArr = JSONArray()
                p.songIds.forEach { sArr.put(it) }
                o.put("songIds", sArr)

                val rArr = JSONArray()
                for (r in p.remoteSongs) {
                    val ro = JSONObject()
                    ro.put("id", r.id)
                    ro.put("title", r.title)
                    ro.put("artist", r.artist)
                    ro.put("album", r.album)
                    ro.put("path", r.path)
                    ro.put("remoteHost", r.remoteHost)
                    ro.put("remotePath", r.remotePath)
                    rArr.put(ro)
                }
                o.put("remoteSongs", rArr)
                arr.put(o)
            }
            root.put("playlists", arr)
            configFile.writeText(root.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
