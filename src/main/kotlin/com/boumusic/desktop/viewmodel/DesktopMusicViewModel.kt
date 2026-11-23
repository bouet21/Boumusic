package com.boumusic.desktop.viewmodel

import com.boumusic.desktop.audio.AudioMetadataHelper
import com.boumusic.desktop.audio.AudioPlayer
import com.boumusic.desktop.data.BackupManager
import com.boumusic.desktop.data.HistoryAndStatsManager
import com.boumusic.desktop.data.MusicRepository
import com.boumusic.desktop.ftp.FtpManager
import com.boumusic.desktop.model.FtpConfig
import com.boumusic.desktop.model.FtpItem
import com.boumusic.desktop.model.Playlist
import com.boumusic.desktop.model.Song
import com.boumusic.desktop.model.SongSortOrder
import com.boumusic.desktop.sync.NetworkDevice
import com.boumusic.desktop.sync.NetworkSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID

enum class DesktopSection(val title: String) {
    LIBRARY("Biblioteca"),
    PLAYLISTS("Listas"),
    QUEUE("Cola de reproducción"),
    SERVERS("Servidores (SMB / FTP)"),
    BACKUP("Sincronización & Backup"),
    EQUALIZER("Ecualizador"),
    WRAPPED("Boumusic Wrapped")
}

enum class RepeatMode(val label: String) {
    OFF("Desactivado"),
    ALL("Repetir todo"),
    ONE("Repetir una")
}

enum class LibrarySubTab(val title: String) {
    ALL_SONGS("Canciones"),
    ARTISTS("Artistas"),
    ALBUMS("Álbumes"),
    GENRES("Géneros")
}

class DesktopMusicViewModel(
    val repository: MusicRepository = MusicRepository(),
    val ftpManager: FtpManager = FtpManager(),
    val player: AudioPlayer = AudioPlayer(),
    val historyManager: HistoryAndStatsManager = HistoryAndStatsManager()
) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _currentSection = MutableStateFlow(DesktopSection.LIBRARY)
    val currentSection: StateFlow<DesktopSection> = _currentSection.asStateFlow()

    fun setSection(section: DesktopSection) {
        _currentSection.value = section
    }

    // SUB-PESTAÑAS DE BIBLIOTECA
    private val _librarySubTab = MutableStateFlow(LibrarySubTab.ALL_SONGS)
    val librarySubTab: StateFlow<LibrarySubTab> = _librarySubTab.asStateFlow()

    fun setLibrarySubTab(tab: LibrarySubTab) {
        _librarySubTab.value = tab
    }

    private val _selectedArtist = MutableStateFlow<String?>(null)
    val selectedArtist: StateFlow<String?> = _selectedArtist.asStateFlow()
    fun selectArtist(artist: String?) { _selectedArtist.value = artist }

    private val _selectedAlbum = MutableStateFlow<String?>(null)
    val selectedAlbum: StateFlow<String?> = _selectedAlbum.asStateFlow()
    fun selectAlbum(album: String?) { _selectedAlbum.value = album }

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre: StateFlow<String?> = _selectedGenre.asStateFlow()
    fun selectGenre(genre: String?) { _selectedGenre.value = genre }

    // BIBLIOTECA LOCAL
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _sortOrder = MutableStateFlow(SongSortOrder.DEFAULT)
    val sortOrder: StateFlow<SongSortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: SongSortOrder) {
        _sortOrder.value = order
    }

    private data class LibraryFilterParams(
        val query: String,
        val sort: SongSortOrder,
        val artist: String?,
        val album: String?,
        val genre: String?
    )

    private val _filterParams = combine(
        _searchQuery,
        _sortOrder,
        _selectedArtist,
        _selectedAlbum,
        _selectedGenre
    ) { query, sort, artist, album, genre ->
        LibraryFilterParams(query, sort, artist, album, genre)
    }

    val filteredSongs: StateFlow<List<Song>> = combine(
        _songs,
        _filterParams
    ) { songList, filters ->
        var list = songList

        // Filtro por artista si hay uno seleccionado
        if (filters.artist != null) {
            list = list.filter { it.artist.equals(filters.artist, ignoreCase = true) }
        }
        // Filtro por álbum si hay uno seleccionado
        if (filters.album != null) {
            list = list.filter { it.album.equals(filters.album, ignoreCase = true) }
        }
        // Filtro por género si hay uno seleccionado
        if (filters.genre != null) {
            list = list.filter { it.genre.equals(filters.genre, ignoreCase = true) }
        }

        val filtered = if (filters.query.isBlank()) {
            list
        } else {
            val q = filters.query.lowercase().trim()
            list.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }
        when (filters.sort) {
            SongSortOrder.DEFAULT -> filtered
            SongSortOrder.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            SongSortOrder.ARTIST_ASC -> filtered.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
            SongSortOrder.DURATION_DESC -> filtered.sortedByDescending { it.duration }
            SongSortOrder.RECENT -> filtered.sortedByDescending { it.id }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artists: StateFlow<List<Pair<String, Int>>> = _songs.map { list ->
        list.groupBy { it.artist.ifBlank { "Desconocido" } }
            .map { (artist, sList) -> artist to sList.size }
            .sortedBy { it.first.lowercase() }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums: StateFlow<List<Pair<String, Int>>> = _songs.map { list ->
        list.groupBy { it.album.ifBlank { "Varios" } }
            .map { (album, sList) -> album to sList.size }
            .sortedBy { it.first.lowercase() }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    val genres: StateFlow<List<Pair<String, Int>>> = _songs.map { list ->
        list.groupBy { it.genre.ifBlank { "General" } }
            .map { (genre, sList) -> genre to sList.size }
            .sortedBy { it.first.lowercase() }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    // COLA Y REPRODUCCIÓN
    private val _playbackQueue = MutableStateFlow<List<Song>>(emptyList())
    val playbackQueue: StateFlow<List<Song>> = _playbackQueue.asStateFlow()

    private var currentIndex = 0
    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun toggleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun playSong(song: Song, queue: List<Song> = emptyList()) {
        _playbackQueue.value = if (queue.isNotEmpty()) queue else _songs.value
        currentIndex = _playbackQueue.value.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playSongInternal(song)
    }

    fun findServerForSong(song: Song): FtpConfig? {
        val current = _activeServer.value
        if (current != null && (current.host.equals(song.remoteHost, ignoreCase = true) || song.remoteHost.isBlank())) {
            return current
        }
        return _savedServers.value.firstOrNull { it.host.equals(song.remoteHost, ignoreCase = true) }
            ?: _savedServers.value.firstOrNull { it.name.equals(song.album, ignoreCase = true) }
            ?: current
            ?: _savedServers.value.firstOrNull()
    }

    private fun playSongInternal(song: Song) {
        if (song.isRemote) {
            val config = findServerForSong(song)
            if (config != null) {
                player.play(song, startFromSec = 0L) { ftpManager.openStream(config, song.remotePath) }
            }
        } else {
            player.play(song, startFromSec = 0L)
        }
    }

    fun playNext(fromUser: Boolean = false) {
        val q = _playbackQueue.value
        if (q.isEmpty()) return

        if (!fromUser && _repeatMode.value == RepeatMode.ONE) {
            val sameSong = q.getOrNull(currentIndex) ?: return
            playSongInternal(sameSong)
            return
        }

        if (_isShuffle.value) {
            currentIndex = (0 until q.size).random()
        } else {
            if (currentIndex + 1 >= q.size) {
                if (_repeatMode.value == RepeatMode.ALL || fromUser) {
                    currentIndex = 0
                } else {
                    player.stop()
                    return
                }
            } else {
                currentIndex++
            }
        }
        val nextSong = q.getOrNull(currentIndex) ?: return
        playSongInternal(nextSong)
    }

    fun playPrevious() {
        val q = _playbackQueue.value
        if (q.isEmpty()) return
        currentIndex = if (currentIndex - 1 < 0) q.size - 1 else currentIndex - 1
        val prevSong = q.getOrNull(currentIndex) ?: return
        playSongInternal(prevSong)
    }

    fun playSongAtQueueIndex(index: Int) {
        val q = _playbackQueue.value
        if (index in q.indices) {
            currentIndex = index
            val song = q[index]
            playSongInternal(song)
        }
    }

    fun addToQueue(song: Song) {
        _playbackQueue.value = _playbackQueue.value + song
    }

    fun playNextInQueue(song: Song) {
        val current = _playbackQueue.value.toMutableList()
        val insertIdx = (currentIndex + 1).coerceAtMost(current.size)
        current.add(insertIdx, song)
        _playbackQueue.value = current
    }

    fun removeFromQueue(index: Int) {
        val current = _playbackQueue.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _playbackQueue.value = current
            if (index < currentIndex) {
                currentIndex = (currentIndex - 1).coerceAtLeast(0)
            }
        }
    }

    fun clearQueue() {
        _playbackQueue.value = emptyList()
        currentIndex = 0
        player.stop()
    }

    fun seekTo(seconds: Long) {
        player.seekTo(seconds)
    }

    // TEMPORIZADOR DE APAGADO (SLEEP TIMER)
    private var sleepTimerJob: Job? = null
    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerMinutes.value = null
            return
        }
        _sleepTimerMinutes.value = minutes
        sleepTimerJob = scope.launch {
            var rem = minutes
            while (rem > 0) {
                delay(60000L)
                rem--
                _sleepTimerMinutes.value = if (rem > 0) rem else null
            }
            player.pause()
            _sleepTimerMinutes.value = null
        }
    }

    // DIÁLOGOS GLOBALES
    val showSleepTimerDialog = MutableStateFlow(false)
    val showSongDetailsDialog = MutableStateFlow<Song?>(null)
    val showAddToPlaylistDialog = MutableStateFlow<Song?>(null)

    // SERVIDORES REMOTOS (SMB / FTP / WebDAV)
    private val _savedServers = MutableStateFlow<List<FtpConfig>>(emptyList())
    val savedServers: StateFlow<List<FtpConfig>> = _savedServers.asStateFlow()

    private val _activeServer = MutableStateFlow<FtpConfig?>(null)
    val activeServer: StateFlow<FtpConfig?> = _activeServer.asStateFlow()

    private val _serverPath = MutableStateFlow("/")
    val serverPath: StateFlow<String> = _serverPath.asStateFlow()

    private val _serverItems = MutableStateFlow<List<FtpItem>>(emptyList())
    val serverItems: StateFlow<List<FtpItem>> = _serverItems.asStateFlow()

    private val _isServerLoading = MutableStateFlow(false)
    val isServerLoading: StateFlow<Boolean> = _isServerLoading.asStateFlow()

    private val _serverError = MutableStateFlow<String?>(null)
    val serverError: StateFlow<String?> = _serverError.asStateFlow()

    fun connectServer(config: FtpConfig) {
        _activeServer.value = config
        _serverPath.value = "/"
        _serverItems.value = emptyList()
        loadServerDirectory("/")
    }

    fun disconnectServer() {
        _activeServer.value = null
        _serverItems.value = emptyList()
        _serverPath.value = "/"
    }

    fun loadServerDirectory(path: String) {
        val config = _activeServer.value ?: return
        _isServerLoading.value = true
        _serverError.value = null
        scope.launch(Dispatchers.IO) {
            try {
                val result = ftpManager.listDirectory(config, path)
                result.onSuccess { items ->
                    _serverItems.value = items
                    _serverPath.value = path
                    _isServerLoading.value = false
                }.onFailure { err ->
                    _serverError.value = err.message ?: "Error al conectar al servidor"
                    _isServerLoading.value = false
                }
            } catch (t: Throwable) {
                _serverError.value = t.message ?: "Error inesperado al conectar al servidor"
                _isServerLoading.value = false
            }
        }
    }

    private val _isScanningServerAudio = MutableStateFlow(false)
    val isScanningServerAudio: StateFlow<Boolean> = _isScanningServerAudio.asStateFlow()

    private val _serverScanStatus = MutableStateFlow("")
    val serverScanStatus: StateFlow<String> = _serverScanStatus.asStateFlow()

    fun playServerShuffleAll() {
        val config = _activeServer.value ?: return
        scope.launch(Dispatchers.IO) {
            _isScanningServerAudio.value = true
            _serverScanStatus.value = "Buscando todas las canciones en ${config.name}..."
            try {
                val result = ftpManager.listAllAudioRecursive(config)
                result.onSuccess { allItems ->
                    _isScanningServerAudio.value = false
                    if (allItems.isEmpty()) {
                        _serverError.value = "No se encontraron canciones en el servidor ${config.name}."
                        return@onSuccess
                    }

                    val songs = allItems.map { item ->
                        val estimatedMs = if (item.size > 1024L) (item.size * 1000L) / 24000L else 0L
                        val (artist, title) = AudioMetadataHelper.parseFileNameMetadata(
                            fileName = item.name,
                            parentFolderName = item.path.substringBeforeLast("/", "")
                        )
                        Song(
                            id = item.path.hashCode().toLong(),
                            title = title,
                            artist = artist,
                            album = config.name,
                            duration = estimatedMs,
                            path = item.path,
                            isRemote = true,
                            remoteHost = config.host,
                            remotePath = item.path,
                            fileSize = if (item.size > 0L) item.size else 0L
                        )
                    }.shuffled()

                    _isShuffle.value = true
                    _playbackQueue.value = songs
                    currentIndex = 0
                    playSongInternal(songs[0])
                }.onFailure { err ->
                    _isScanningServerAudio.value = false
                    _serverError.value = "Error al escanear servidor: ${err.message}"
                }
            } catch (t: Throwable) {
                _isScanningServerAudio.value = false
                _serverError.value = "Error al escanear servidor: ${t.message}"
            }
        }
    }

    fun playRemoteSong(item: FtpItem) {
        val config = _activeServer.value ?: return
        val audios = _serverItems.value.filter { it.isAudio }.map {
            val estimatedMs = if (it.size > 1024L) (it.size * 1000L) / 24000L else 0L
            val (artist, title) = AudioMetadataHelper.parseFileNameMetadata(
                fileName = it.name,
                parentFolderName = _serverPath.value
            )
            Song(
                id = it.path.hashCode().toLong(),
                title = title,
                artist = artist,
                album = config.name,
                duration = estimatedMs,
                path = it.path,
                isRemote = true,
                remoteHost = config.host,
                remotePath = it.path,
                fileSize = if (it.size > 0L) it.size else 0L
            )
        }
        if (audios.isEmpty()) return
        val song = audios.firstOrNull { it.remotePath == item.path } ?: audios[0]
        _playbackQueue.value = audios
        currentIndex = audios.indexOfFirst { it.remotePath == song.remotePath }.coerceAtLeast(0)
        playSongInternal(song)
    }

    fun playAllRemote(items: List<FtpItem>, shuffle: Boolean = false) {
        val config = _activeServer.value ?: return
        val audioSongs = items.filter { it.isAudio }.map {
            val estimatedMs = if (it.size > 1024L) (it.size * 1000L) / 24000L else 0L
            val (artist, title) = AudioMetadataHelper.parseFileNameMetadata(
                fileName = it.name,
                parentFolderName = _serverPath.value
            )
            Song(
                id = it.path.hashCode().toLong(),
                title = title,
                artist = artist,
                album = config.name,
                duration = estimatedMs,
                path = it.path,
                isRemote = true,
                remoteHost = config.host,
                remotePath = it.path,
                fileSize = if (it.size > 0L) it.size else 0L
            )
        }
        if (audioSongs.isEmpty()) return

        val queue = if (shuffle) audioSongs.shuffled() else audioSongs
        _isShuffle.value = shuffle
        _playbackQueue.value = queue
        currentIndex = 0
        playSongInternal(queue[0])
    }

    fun downloadRemoteSong(item: FtpItem, onDone: (File?) -> Unit) {
        val config = _activeServer.value ?: return
        scope.launch(Dispatchers.IO) {
            val destDir = File(repository.defaultMusicDir, "Boumusic")
            val destFile = File(destDir, item.name)
            val result = ftpManager.downloadSong(config, item.path, destFile) {}
            if (result.isSuccess) {
                rescanLibrary()
                onDone(destFile)
            } else {
                onDone(null)
            }
        }
    }

    fun saveServer(config: FtpConfig) {
        scope.launch {
            val list = _savedServers.value.filter { it.id != config.id }.toMutableList()
            list.add(0, config)
            _savedServers.value = list
            repository.saveServers(list)
        }
    }

    fun deleteServer(id: String) {
        scope.launch {
            val list = _savedServers.value.filter { it.id != id }
            _savedServers.value = list
            repository.saveServers(list)
            if (_activeServer.value?.id == id) disconnectServer()
        }
    }

    // LISTAS DE REPRODUCCIÓN
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        scope.launch {
            val newP = Playlist(UUID.randomUUID().toString(), name, emptyList())
            val list = _playlists.value + newP
            _playlists.value = list
            repository.savePlaylists(list)
        }
    }

    fun deletePlaylist(id: String) {
        scope.launch {
            val list = _playlists.value.filter { it.id != id }
            _playlists.value = list
            repository.savePlaylists(list)
            if (_selectedPlaylist.value?.id == id) _selectedPlaylist.value = null
        }
    }

    fun addSongToPlaylist(playlist: Playlist, song: Song) {
        scope.launch {
            val updated = if (song.isRemote) {
                playlist.copy(remoteSongs = playlist.remoteSongs + song)
            } else {
                playlist.copy(songIds = playlist.songIds + song.id)
            }
            val list = _playlists.value.map { if (it.id == playlist.id) updated else it }
            _playlists.value = list
            repository.savePlaylists(list)
            if (_selectedPlaylist.value?.id == playlist.id) _selectedPlaylist.value = updated
        }
    }

    // BACKUP & SYNC
    fun exportBackup(destFile: File): Boolean {
        return try {
            val json = BackupManager.generateBackupJson(_savedServers.value, _playlists.value)
            destFile.writeText(json, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importBackup(jsonContent: String): Boolean {
        return try {
            val data = BackupManager.parseBackupJson(jsonContent)
            scope.launch {
                _savedServers.value = data.servers
                repository.saveServers(data.servers)

                val current = _playlists.value.toMutableList()
                for (p in data.playlists) {
                    current.removeAll { it.id == p.id || it.name.equals(p.name, ignoreCase = true) }
                    current.add(p)
                }
                _playlists.value = current
                repository.savePlaylists(current)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun rescanLibrary() {
        scope.launch {
            val list = repository.scanLocalSongs()
            _songs.value = list
        }
    }

    // SINCRONIZACIÓN AUTOMÁTICA EN RED LOCAL (UDP / HTTP)
    val syncManager = NetworkSyncManager(
        deviceName = "PC (" + (System.getenv("COMPUTERNAME") ?: System.getProperty("user.name") ?: "Desktop") + ")",
        serverProvider = { _savedServers.value },
        playlistProvider = { _playlists.value },
        statsProvider = { historyManager.exportSyncData() },
        onDataReceived = { newServers, newPlaylists, statsJson ->
            scope.launch {
                val currentServers = _savedServers.value.toMutableList()
                for (s in newServers) {
                    currentServers.removeAll { it.id == s.id || (it.host == s.host && it.port == s.port && it.protocolLabel == s.protocolLabel) }
                    currentServers.add(s)
                }
                _savedServers.value = currentServers
                repository.saveServers(currentServers)

                val currentPlaylists = _playlists.value.toMutableList()
                for (p in newPlaylists) {
                    currentPlaylists.removeAll { it.id == p.id || it.name.equals(p.name, ignoreCase = true) }
                    currentPlaylists.add(p)
                }
                _playlists.value = currentPlaylists
                repository.savePlaylists(currentPlaylists)

                if (statsJson != null) {
                    historyManager.mergeSyncData(statsJson)
                }
            }
        }
    )

    val discoveredDevices: StateFlow<List<NetworkDevice>> = syncManager.discoveredDevices
    val autoSyncDeviceIds: StateFlow<Set<String>> = syncManager.autoSyncDeviceIds
    val syncStatus: StateFlow<String?> = syncManager.syncStatus
    val isScanning: StateFlow<Boolean> = syncManager.isScanning
    val localIp: String? get() = syncManager.localIp
    val httpPort: Int get() = syncManager.actualHttpPort

    fun toggleAutoSync(deviceId: String) = syncManager.toggleAutoSync(deviceId)
    fun syncWithDevice(device: NetworkDevice) = syncManager.syncWithDevice(device)
    fun scanSubnet() = syncManager.scanSubnet()
    fun connectDirectly(ip: String, onDone: (Boolean, String) -> Unit) = syncManager.connectDirectly(ip, onDone = onDone)

    init {
        player.onSongFinished = { playNext(fromUser = false) }
        player.onSongPlaybackLogged = { song, durationMs ->
            historyManager.recordPlayback(song, durationMs)
        }
        player.onMetadataUpdated = { updatedSong ->
            val curQueue = _playbackQueue.value.toMutableList()
            val idx = curQueue.indexOfFirst {
                it.id == updatedSong.id || (it.isRemote && it.remotePath == updatedSong.remotePath)
            }
            if (idx != -1) {
                curQueue[idx] = updatedSong
                _playbackQueue.value = curQueue
            }
        }
        // Carga ultra-rápida desde caché local para arranque instantáneo (< 2ms)
        val cached = repository.loadCachedSongs()
        _songs.value = cached

        scope.launch {
            _savedServers.value = repository.loadSavedServers()
            _playlists.value = repository.loadPlaylists()
            if (cached.isEmpty()) {
                _songs.value = repository.scanLocalSongs()
            }
            syncManager.start()
        }
    }
}
