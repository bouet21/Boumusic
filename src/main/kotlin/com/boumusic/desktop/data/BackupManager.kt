package com.boumusic.desktop.data

import com.boumusic.desktop.model.FtpConfig
import com.boumusic.desktop.model.Playlist
import com.boumusic.desktop.model.Song
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class DesktopBackupData(
    val servers: List<FtpConfig>,
    val playlists: List<Playlist>,
    val settings: Map<String, Any>
)

object BackupManager {

    fun generateBackupJson(
        servers: List<FtpConfig>,
        playlists: List<Playlist>,
        settings: Map<String, Any> = emptyMap()
    ): String {
        val root = JSONObject()
        root.put("app", "Boumusic")
        root.put("platform", "Desktop")
        root.put("schemaVersion", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val serversArr = JSONArray()
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
            serversArr.put(o)
        }
        root.put("servers", serversArr)

        val playlistsArr = JSONArray()
        for (p in playlists) {
            val pObj = JSONObject()
            pObj.put("id", p.id)
            pObj.put("name", p.name)
            pObj.put("createdAt", p.createdAt)

            val sArr = JSONArray()
            p.songIds.forEach { sArr.put(it) }
            pObj.put("songIds", sArr)

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
            pObj.put("remoteSongs", rArr)
            playlistsArr.put(pObj)
        }
        root.put("playlists", playlistsArr)

        val settingsObj = JSONObject()
        for ((k, v) in settings) settingsObj.put(k, v)
        root.put("settings", settingsObj)

        return root.toString(2)
    }

    fun parseBackupJson(jsonStr: String): DesktopBackupData {
        val root = JSONObject(jsonStr)

        val servers = mutableListOf<FtpConfig>()
        val serversArr = root.optJSONArray("servers")
        if (serversArr != null) {
            for (i in 0 until serversArr.length()) {
                val o = serversArr.getJSONObject(i)
                servers.add(
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
        }

        val playlists = mutableListOf<Playlist>()
        val playlistsArr = root.optJSONArray("playlists")
        if (playlistsArr != null) {
            for (i in 0 until playlistsArr.length()) {
                val pObj = playlistsArr.getJSONObject(i)
                val id = pObj.optString("id", UUID.randomUUID().toString())
                val name = pObj.optString("name", "Lista")
                val createdAt = pObj.optLong("createdAt", System.currentTimeMillis())

                val songIds = mutableListOf<Long>()
                val sArr = pObj.optJSONArray("songIds")
                if (sArr != null) {
                    for (j in 0 until sArr.length()) songIds.add(sArr.getLong(j))
                }

                val remoteSongs = mutableListOf<Song>()
                val rArr = pObj.optJSONArray("remoteSongs") ?: pObj.optJSONArray("ftpSongs")
                if (rArr != null) {
                    for (k in 0 until rArr.length()) {
                        val ro = rArr.getJSONObject(k)
                        remoteSongs.add(
                            Song(
                                id = ro.optLong("id", System.currentTimeMillis()),
                                title = ro.optString("title", "Canción"),
                                artist = ro.optString("artist", "Remoto"),
                                album = ro.optString("album", ""),
                                duration = 0L,
                                path = ro.optString("path", ro.optString("contentUri", "")),
                                isRemote = true,
                                remoteHost = ro.optString("remoteHost", ro.optString("ftpServerHost", "")),
                                remotePath = ro.optString("remotePath", ro.optString("ftpPath", ""))
                            )
                        )
                    }
                }

                playlists.add(Playlist(id, name, songIds, remoteSongs, createdAt))
            }
        }

        val settings = mutableMapOf<String, Any>()
        val settingsObj = root.optJSONObject("settings")
        if (settingsObj != null) {
            val keys = settingsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                settings[k] = settingsObj.get(k)
            }
        }

        return DesktopBackupData(servers, playlists, settings)
    }

    fun exportPlaylistToM3u8(playlist: Playlist, allSongs: List<Song>): String {
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#PLAYLIST:${playlist.name}")

        val songMap = allSongs.associateBy { it.id }
        for (id in playlist.songIds) {
            val song = songMap[id] ?: continue
            val durationSec = (song.duration / 1000).coerceAtLeast(0)
            sb.appendLine("#EXTINF:$durationSec,${song.artist} - ${song.title}")
            sb.appendLine(song.path)
        }

        for (r in playlist.remoteSongs) {
            sb.appendLine("#EXTINF:0,${r.artist} - ${r.title}")
            sb.appendLine(r.path)
        }

        return sb.toString()
    }

    fun parseM3u8(m3u8Content: String, fallbackName: String, allSongs: List<Song>): Playlist {
        var name = fallbackName
        val matchedIds = mutableListOf<Long>()
        val remoteSongs = mutableListOf<Song>()
        val songsByFilename = allSongs.associateBy { File(it.path).name.lowercase() }

        var nextTitle: String? = null

        for (line in m3u8Content.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#PLAYLIST:")) {
                name = trimmed.removePrefix("#PLAYLIST:").trim().ifBlank { fallbackName }
            } else if (trimmed.startsWith("#EXTINF:")) {
                nextTitle = trimmed.substringAfter(",", "").trim()
            } else if (!trimmed.startsWith("#")) {
                if (trimmed.startsWith("smb://") || trimmed.startsWith("ftp://") || trimmed.startsWith("http://")) {
                    remoteSongs.add(
                        Song(
                            id = System.currentTimeMillis() + remoteSongs.size,
                            title = nextTitle ?: File(trimmed).name,
                            artist = "Remoto",
                            album = "",
                            duration = 0L,
                            path = trimmed,
                            isRemote = true
                        )
                    )
                } else {
                    val filename = File(trimmed).name.lowercase()
                    val match = songsByFilename[filename]
                    if (match != null) matchedIds.add(match.id)
                }
                nextTitle = null
            }
        }

        return Playlist(
            id = UUID.randomUUID().toString(),
            name = name,
            songIds = matchedIds,
            remoteSongs = remoteSongs,
            createdAt = System.currentTimeMillis()
        )
    }
}
