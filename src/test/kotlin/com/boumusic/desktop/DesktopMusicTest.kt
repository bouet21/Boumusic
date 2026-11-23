package com.boumusic.desktop

import com.boumusic.desktop.data.BackupManager
import com.boumusic.desktop.model.FtpConfig
import com.boumusic.desktop.model.FtpItem
import com.boumusic.desktop.model.Playlist
import com.boumusic.desktop.model.Song
import com.boumusic.desktop.model.SongSortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopMusicTest {

    @Test
    fun testFtpConfigProtocolLabels() {
        val smb = FtpConfig(name = "PC", host = "192.168.1.10", port = 445, isSmb = true, smbShare = "\\Musica\\")
        assertEquals("SMB (Windows/Linux)", smb.protocolLabel)
        assertEquals("Musica", smb.cleanSmbShare)

        val ftp = FtpConfig(name = "FTP Server", host = "192.168.1.20", port = 21, isFtps = false, isWebDav = false)
        assertEquals("FTP", ftp.protocolLabel)

        val webdav = FtpConfig(name = "Cloud", host = "cloud.lan", port = 443, isWebDav = true)
        assertEquals("WebDAV", webdav.protocolLabel)
    }

    @Test
    fun testFtpItemAudioDetection() {
        val mp3 = FtpItem("pista.mp3", "/pista.mp3", false, 1024L)
        assertTrue(mp3.isAudio)

        val flac = FtpItem("pista.FLAC", "/pista.FLAC", false, 1024L)
        assertTrue(flac.isAudio)

        val ogg = FtpItem("pista.ogg", "/pista.ogg", false, 1024L)
        assertTrue(ogg.isAudio)

        val folder = FtpItem("Rock", "/Rock", true, 0L)
        assertFalse(folder.isAudio)
    }

    @Test
    fun testBackupManagerJsonInterop() {
        val server = FtpConfig(name = "PC Windows", host = "192.168.1.150", port = 445, isSmb = true, smbShare = "Musica")
        val playlist = Playlist(id = "p-1", name = "Favoritos PC", songIds = listOf(101L, 102L))

        val json = BackupManager.generateBackupJson(listOf(server), listOf(playlist))
        assertTrue(json.contains("Boumusic"))
        assertTrue(json.contains("PC Windows"))
        assertTrue(json.contains("Favoritos PC"))

        val data = BackupManager.parseBackupJson(json)
        assertEquals(1, data.servers.size)
        assertEquals("PC Windows", data.servers.first().name)
        assertTrue(data.servers.first().isSmb)
        assertEquals("Musica", data.servers.first().smbShare)

        assertEquals(1, data.playlists.size)
        assertEquals("Favoritos PC", data.playlists.first().name)
        assertEquals(listOf(101L, 102L), data.playlists.first().songIds)
    }

    @Test
    fun testSongSortOrder() {
        assertEquals("Título (A-Z)", SongSortOrder.TITLE_ASC.displayName)
        assertEquals("Artista (A-Z)", SongSortOrder.ARTIST_ASC.displayName)
        assertEquals("Duración", SongSortOrder.DURATION_DESC.displayName)
    }

    @Test
    fun testM3u8ExportAndParse() {
        val song = Song(1L, "Canción Demo", "Bouet", "Álbum Demo", 180000L, "C:\\Music\\demo.mp3")
        val playlist = Playlist("p-m3u", "Lista M3U", listOf(1L))

        val m3u8 = BackupManager.exportPlaylistToM3u8(playlist, listOf(song))
        assertTrue(m3u8.contains("#EXTM3U"))
        assertTrue(m3u8.contains("#PLAYLIST:Lista M3U"))
        assertTrue(m3u8.contains("demo.mp3"))

        val parsed = BackupManager.parseM3u8(m3u8, "Lista M3U", listOf(song))
        assertEquals("Lista M3U", parsed.name)
        assertEquals(listOf(1L), parsed.songIds)
    }
}
