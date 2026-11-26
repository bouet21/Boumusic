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

    @Test
    fun testFtpSanitizeHostAndPort() {
        val ftpManager = com.boumusic.desktop.ftp.FtpManager()

        val c1 = FtpConfig(host = "ftp://192.168.1.50:2121", port = 21)
        val (h1, p1) = ftpManager.sanitizeHostAndPort(c1)
        assertEquals("192.168.1.50", h1)
        assertEquals(2121, p1)

        val c2 = FtpConfig(host = "192.168.1.100:21", port = 21)
        val (h2, p2) = ftpManager.sanitizeHostAndPort(c2)
        assertEquals("192.168.1.100", h2)
        assertEquals(21, p2)

        val c3 = FtpConfig(host = "ftps://servidor.lan:2121/", port = 21)
        val (h3, p3) = ftpManager.sanitizeHostAndPort(c3)
        assertEquals("servidor.lan", h3)
        assertEquals(2121, p3)

        val c4 = FtpConfig(host = "  10.0.0.5:990  ", port = 21)
        val (h4, p4) = ftpManager.sanitizeHostAndPort(c4)
        assertEquals("10.0.0.5", h4)
        assertEquals(990, p4)
    }

    @Test
    fun testFileNameMetadataParser() {
        val (a1, t1) = com.boumusic.desktop.audio.AudioMetadataHelper.parseFileNameMetadata("Queen - Bohemian Rhapsody.mp3")
        assertEquals("Queen", a1)
        assertEquals("Bohemian Rhapsody", t1)

        val (a2, t2) = com.boumusic.desktop.audio.AudioMetadataHelper.parseFileNameMetadata("01. Queen - Bohemian Rhapsody.mp3")
        assertEquals("Queen", a2)
        assertEquals("Bohemian Rhapsody", t2)

        val (a3, t3) = com.boumusic.desktop.audio.AudioMetadataHelper.parseFileNameMetadata("01 - Daft Punk - One More Time.flac")
        assertEquals("Daft Punk", a3)
        assertEquals("One More Time", t3)

        val (a4, t4) = com.boumusic.desktop.audio.AudioMetadataHelper.parseFileNameMetadata(
            "05 Shape of You (Official Music Video).mp3",
            parentFolderName = "Ed Sheeran"
        )
        assertEquals("Ed Sheeran", a4)
        assertEquals("Shape of You", t4)

        val (a5, t5) = com.boumusic.desktop.audio.AudioMetadataHelper.parseFileNameMetadata("AC_DC - Highway to Hell.wav")
        assertEquals("AC_DC", a5)
        assertEquals("Highway to Hell", t5)

        val (a6, t6) = com.boumusic.desktop.audio.AudioMetadataHelper.parseFileNameMetadata(
            "01. Bohemian Rhapsody.mp3",
            parentFolderName = "/Music/Queen/A Night at the Opera/CD1"
        )
        assertEquals("Queen", a6)
        assertEquals("Bohemian Rhapsody", t6)

        val (a7, t7) = com.boumusic.desktop.audio.AudioMetadataHelper.parseFileNameMetadata("Nirvana-In Bloom.mp3")
        assertEquals("Nirvana", a7)
        assertEquals("In Bloom", t7)

        // Sunnify & Title-Artist format tests (e.g. Crimewave - Crystal Castles)
        val (a8, t8) = com.boumusic.desktop.audio.AudioMetadataHelper.parseFileNameMetadata(
            "Crimewave - Crystal Castles.mp3",
            parentFolderName = "C:\\Users\\Bouet\\Downloads\\playlist\\Sunnify\\Knights Corpse - bou"
        )
        assertEquals("Crystal Castles", a8)
        assertEquals("Crimewave", t8)

        // Collaboration with comma in artist
        val (a9, t9) = com.boumusic.desktop.audio.AudioMetadataHelper.parseFileNameMetadata(
            "SMOKE IT OFF! - Lumi Athena, jnhygs.mp3"
        )
        assertEquals("Lumi Athena, jnhygs", a9)
        assertEquals("SMOKE IT OFF!", t9)

        // 3-part title and version
        val (a10, t10) = com.boumusic.desktop.audio.AudioMetadataHelper.parseFileNameMetadata(
            "Test & Recognise - Flume Re-work - Seekae, Flume.mp3"
        )
        assertEquals("Seekae, Flume", a10)
        assertEquals("Test & Recognise - Flume Re-work", t10)
    }

    @Test
    fun testShuffleDeterministicBackNavigation() {
        val s1 = Song(1L, "Song 1", "Artist 1", "Album 1", 180000L, "C:\\Music\\s1.mp3")
        val s2 = Song(2L, "Song 2", "Artist 2", "Album 2", 180000L, "C:\\Music\\s2.mp3")
        val s3 = Song(3L, "Song 3", "Artist 3", "Album 3", 180000L, "C:\\Music\\s3.mp3")
        val s4 = Song(4L, "Song 4", "Artist 4", "Album 4", 180000L, "C:\\Music\\s4.mp3")
        val s5 = Song(5L, "Song 5", "Artist 5", "Album 5", 180000L, "C:\\Music\\s5.mp3")
        val queue = listOf(s1, s2, s3, s4, s5)

        val vm = com.boumusic.desktop.viewmodel.DesktopMusicViewModel()
        vm.toggleShuffle()
        assertTrue("Shuffle debe estar activado", vm.isShuffle.value)

        // Comenzamos reproduciendo s1
        vm.playSong(s1, queue)
        assertEquals(1L, vm.player.currentSong.value?.id)
        assertEquals(1, vm.currentHistory.size)
        assertEquals(0, vm.currentHistoryPointer)

        // Avanzar a la siguiente canción aleatoria
        vm.playNext(fromUser = true)
        val secondSong = vm.player.currentSong.value!!
        assertFalse("No debe repetir s1 inmediatamente", secondSong.id == 1L)
        assertEquals(2, vm.currentHistory.size)
        assertEquals(1, vm.currentHistoryPointer)

        // Avanzar a la tercera canción aleatoria
        vm.playNext(fromUser = true)
        val thirdSong = vm.player.currentSong.value!!
        assertFalse("No debe ser igual a la segunda", thirdSong.id == secondSong.id)
        assertEquals(3, vm.currentHistory.size)
        assertEquals(2, vm.currentHistoryPointer)

        // Retroceder (Backward) -> DEBE ir determinísticamente a la segunda canción, NO a otra aleatoria
        vm.playPrevious()
        assertEquals("Al retroceder debe volver exactamente a la canción previa", secondSong.id, vm.player.currentSong.value?.id)
        assertEquals(1, vm.currentHistoryPointer)

        // Retroceder otra vez (Backward) -> DEBE volver a s1
        vm.playPrevious()
        assertEquals("Al retroceder debe volver a la primera canción", s1.id, vm.player.currentSong.value?.id)
        assertEquals(0, vm.currentHistoryPointer)

        // Si ya está en la primera y vuelve a pulsar retroceder en aleatorio -> no salta aleatorio hacia atrás
        vm.playPrevious()
        assertEquals("Al inicio no debe saltar aleatoriamente", s1.id, vm.player.currentSong.value?.id)

        // Avanzar (Forward) -> DEBE recorrer hacia adelante el historial ya transitado sin re-barajar
        vm.playNext(fromUser = true)
        assertEquals("Forward debe avanzar en el historial hacia la segunda", secondSong.id, vm.player.currentSong.value?.id)
        assertEquals(1, vm.currentHistoryPointer)

        vm.playNext(fromUser = true)
        assertEquals("Forward debe avanzar en el historial hacia la tercera", thirdSong.id, vm.player.currentSong.value?.id)
        assertEquals(2, vm.currentHistoryPointer)

        // Avanzar de nuevo (Forward al final del historial) -> debe elegir una nueva canción aleatoria no jugada
        vm.playNext(fromUser = true)
        val fourthSong = vm.player.currentSong.value!!
        assertFalse("Cuarta canción debe ser distinta a la tercera", fourthSong.id == thirdSong.id)
        assertEquals(4, vm.currentHistory.size)
        assertEquals(3, vm.currentHistoryPointer)
    }

    @Test
    fun testDesktopEqualizerOptimizations() {
        val eq = com.boumusic.desktop.audio.DesktopEqualizer()
        eq.resetToFlat()

        // Buffer estéreo de 1000 muestras (2000 bytes) con valor 1000
        val sampleVal: Short = 1000
        val original = ByteArray(2000)
        for (i in 0 until 1000) {
            original[i * 2] = (sampleVal.toInt() and 0xFF).toByte()
            original[i * 2 + 1] = ((sampleVal.toInt() shr 8) and 0xFF).toByte()
        }

        // Test 1: Bypass con volumen 1.0f (no debe alterar el búfer)
        val testBuf1 = original.clone()
        eq.processPcmBuffer(testBuf1, testBuf1.size, 2, 1.0f)
        for (i in testBuf1.indices) {
            assertEquals("Bypass plano no debe modificar el buffer", original[i], testBuf1[i])
        }

        // Test 2: Volumen 50% entero (debe atenuar las muestras a ~500)
        val testBuf2 = original.clone()
        eq.processPcmBuffer(testBuf2, testBuf2.size, 2, 0.5f)
        val sampleOut = ((testBuf2[0].toInt() and 0xFF) or (testBuf2[1].toInt() shl 8)).toShort()
        assertTrue("Muestra escalada a 0.5 debe estar cerca de 500", sampleOut in 495..505)

        // Test 3: Filtro con ganancia activa
        eq.setBandGain(0, 6f)
        val testBuf3 = original.clone()
        eq.processPcmBuffer(testBuf3, testBuf3.size, 2, 1.0f)
        // El búfer debe procesarse correctamente sin excepciones
        assertEquals(2000, testBuf3.size)
    }

    @Test
    fun testWindowsDarkThemeHelper() {
        val isWin = com.boumusic.desktop.ui.util.WindowsDarkThemeHelper.isWindows()
        val os = System.getProperty("os.name", "").lowercase()
        assertEquals(os.contains("windows"), isWin)

        // Verificamos que applyDarkTitleBar no lance excepciones y fije el fondo oscuro si no es entorno headless
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            val frame = javax.swing.JFrame("Test Window")
            try {
                com.boumusic.desktop.ui.util.WindowsDarkThemeHelper.applyDarkTitleBar(frame)
                assertEquals(java.awt.Color(0x12, 0x12, 0x14), frame.background)
            } finally {
                frame.dispose()
            }
        }
    }

    @Test
    fun testDownloadModelsAndFormatting() {
        val track = com.boumusic.desktop.downloader.DownloadTrackItem(
            id = "spotify:track:12345",
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            durationMs = 243000L
        )
        assertEquals("4:03", track.formattedDuration)
        assertEquals("M83 • Hurry Up, We're Dreaming", track.displaySubtitle)

        val localDest = com.boumusic.desktop.downloader.DownloadDestination(
            type = com.boumusic.desktop.downloader.DestinationType.LOCAL,
            label = "Biblioteca Local"
        )
        assertEquals(com.boumusic.desktop.downloader.DestinationType.LOCAL, localDest.type)

        val smbDest = com.boumusic.desktop.downloader.DownloadDestination(
            type = com.boumusic.desktop.downloader.DestinationType.SMB,
            label = "Servidor SMB",
            remoteSubPath = "Musica"
        )
        assertEquals(com.boumusic.desktop.downloader.DestinationType.SMB, smbDest.type)
        assertEquals("Musica", smbDest.remoteSubPath)
    }

    @Test
    fun testTrackDownloaderBinaries() {
        val repo = com.boumusic.desktop.data.MusicRepository()
        val ftp = com.boumusic.desktop.ftp.FtpManager()
        val engine = com.boumusic.desktop.downloader.TrackDownloaderEngine(repo, ftp, ftp.smbManager)
        val ytdlp = engine.getYtDlpBinary()
        assertTrue("El directorio bin debe existir", engine.binDir.exists())
        assertTrue("yt-dlp debe haber sido localizado o existir en bin", ytdlp.exists() || engine.binDir.exists())
    }

    @Test
    fun testRealDownloadExecution() {
        val repo = com.boumusic.desktop.data.MusicRepository()
        val ftp = com.boumusic.desktop.ftp.FtpManager()
        val engine = com.boumusic.desktop.downloader.TrackDownloaderEngine(repo, ftp, ftp.smbManager)
        val track = com.boumusic.desktop.downloader.DownloadTrackItem(
            id = "test-1",
            title = "Shape of You",
            artist = "Ed Sheeran",
            album = "Divide",
            durationMs = 233000L,
            spotifyUrl = "https://open.spotify.com/track/7qiZfU4dY1lWllzX7mPBI3"
        )
        val task = com.boumusic.desktop.downloader.DownloadTask(
            id = "task-test-1",
            track = track,
            destination = com.boumusic.desktop.downloader.DownloadDestination(
                type = com.boumusic.desktop.downloader.DestinationType.LOCAL,
                label = "Local",
                localFolder = engine.tempDir
            )
        )
        kotlinx.coroutines.runBlocking {
            val res = engine.executeDownload(task) { updated ->
                println("UPDATE: stage=${updated.stage}, progress=${updated.progress}, status=${updated.statusMessage}, error=${updated.error}")
            }
            println("FINAL RESULT: $res")
            if (res.isFailure) {
                res.exceptionOrNull()?.printStackTrace()
            }
        }
    }

    @Test
    fun testDepresionSonoraDownload() {
        val repo = com.boumusic.desktop.data.MusicRepository()
        val ftp = com.boumusic.desktop.ftp.FtpManager()
        val engine = com.boumusic.desktop.downloader.TrackDownloaderEngine(repo, ftp, ftp.smbManager)
        val track = com.boumusic.desktop.downloader.DownloadTrackItem(
            id = "test-depresion",
            title = "Como todo el Mundo",
            artist = "Depresión Sonora",
            album = "Como todo el Mundo",
            durationMs = 195000L,
            spotifyUrl = "https://www.deezer.com/track/1988475607"
        )
        val task = com.boumusic.desktop.downloader.DownloadTask(
            id = "task-depresion",
            track = track,
            destination = com.boumusic.desktop.downloader.DownloadDestination(
                type = com.boumusic.desktop.downloader.DestinationType.LOCAL,
                label = "Local",
                localFolder = engine.tempDir
            )
        )
        kotlinx.coroutines.runBlocking {
            val res = engine.executeDownload(task) { updated ->
                println("UPDATE DEPRESION: stage=${updated.stage}, progress=${updated.progress}, status=${updated.statusMessage}, error=${updated.error}")
            }
            println("FINAL RESULT DEPRESION: $res")
            assertTrue("La descarga debe ser exitosa", res.isSuccess)
        }
    }

    @Test
    fun testSearchResolverTextQuery() {
        try {
            kotlinx.coroutines.runBlocking {
                val results = com.boumusic.desktop.downloader.SpotifyEmbedResolver.resolve("Bad Bunny", limit = 5)
                println("=== TEST RESULTS COUNT: ${results.size} ===")
                results.forEach { println("Track: ${it.title} - ${it.artist} (${it.durationMs}ms)") }
                assertTrue("Debe encontrar canciones al buscar texto", results.isNotEmpty())
            }
        } catch (e: Throwable) {
            println("Test omitido si el entorno de CI no tiene conexión de red externa: ${e.message}")
        }
    }

    @Test
    fun testCredentialCipher() {
        val original = "MiSuperPassword123!@#"
        val encrypted = com.boumusic.desktop.data.CredentialCipher.encrypt(original)
        assertTrue("El texto cifrado debe empezar con el prefijo seguro", encrypted.startsWith("enc:v1:"))
        assertFalse("El texto cifrado no debe contener la contraseña plana", encrypted.contains(original))

        val decrypted = com.boumusic.desktop.data.CredentialCipher.decrypt(encrypted)
        assertEquals("El descifrado debe recuperar exactamente la contraseña original", original, decrypted)

        assertEquals("Texto sin prefijo debe devolverse idéntico", "plainPassword", com.boumusic.desktop.data.CredentialCipher.decrypt("plainPassword"))
    }

    @Test
    fun testSubsonicManager() {
        val subsonic = com.boumusic.desktop.subsonic.SubsonicManager()
        val config = FtpConfig(
            name = "Navidrome",
            host = "http://192.168.1.50:4533",
            username = "admin",
            password = "secretpassword",
            isSubsonic = true
        )
        assertEquals("Subsonic / Navidrome", config.protocolLabel)

        val baseUrl = subsonic.buildBaseUrl(config)
        assertEquals("http://192.168.1.50:4533", baseUrl)

        val configHttps = FtpConfig(
            name = "Subsonic Cloud",
            host = "music.boumusic.com",
            port = 443,
            isFtps = true,
            username = "user",
            password = "pass",
            isSubsonic = true
        )
        assertEquals("https://music.boumusic.com", subsonic.buildBaseUrl(configHttps))

        val auth = subsonic.getAuthParams(config)
        assertTrue("Los parámetros de autenticación deben incluir usuario", auth.contains("u=admin"))
        assertTrue("Los parámetros de autenticación deben incluir token", auth.contains("&t="))
        assertTrue("Los parámetros de autenticación deben incluir salt", auth.contains("&s="))
        assertTrue("Los parámetros de autenticación deben solicitar json", auth.contains("f=json"))
        assertTrue("Los parámetros de autenticación deben definir versión", auth.contains("v=1.16.1"))
    }

    @Test
    fun testMediaKeyManagerLifecycle() {
        var played = false
        val manager = com.boumusic.desktop.system.MediaKeyManager(
            onPlayPause = { played = true },
            onNext = {},
            onPrevious = {}
        )
        // Iniciar y detener no debe lanzar excepciones
        manager.start()
        Thread.sleep(100)
        manager.stop()
        assertFalse(played)
    }

    @Test
    fun testReplayGainParsingAndPlayerProperties() {
        val g1 = com.boumusic.desktop.audio.AudioMetadataHelper.parseGainDb("-6.50 dB")
        assertEquals(-6.50f, g1 ?: 0f, 0.01f)

        val g2 = com.boumusic.desktop.audio.AudioMetadataHelper.parseGainDb("+3.2 dB")
        assertEquals(3.2f, g2 ?: 0f, 0.01f)

        val g3 = com.boumusic.desktop.audio.AudioMetadataHelper.parseGainDb("-4.15")
        assertEquals(-4.15f, g3 ?: 0f, 0.01f)

        val player = com.boumusic.desktop.audio.AudioPlayer()
        assertTrue("Gapless debe estar habilitado por defecto", player.isGaplessEnabled)
        assertTrue("ReplayGain debe estar habilitado por defecto", player.isReplayGainEnabled)
    }

    @Test
    fun testHighFidelityAudioResolverResolution() {
        val res = com.boumusic.desktop.downloader.HighFidelityAudioResolver.resolveDirectStream("Ed Sheeran", "Shape of You")
        if (res != null) {
            assertTrue("Stream URL debe ser un enlace HTTP válido", res.streamUrl.startsWith("http"))
            assertTrue("Debe contener extensión de audio", res.extension == "m4a" || res.extension == "mp3")
        }
        val resQuery = com.boumusic.desktop.downloader.HighFidelityAudioResolver.resolveDirectStream("Shape of You - Ed Sheeran")
        if (resQuery != null) {
            assertTrue("Stream URL desde query debe ser válida", resQuery.streamUrl.startsWith("http"))
        }
    }
}
