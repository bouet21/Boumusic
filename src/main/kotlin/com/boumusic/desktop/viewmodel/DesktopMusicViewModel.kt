package com.boumusic.desktop.viewmodel

import com.boumusic.desktop.audio.AudioPlayer
import com.boumusic.desktop.data.BackupManager
import com.boumusic.desktop.data.MusicRepository
import com.boumusic.desktop.ftp.FtpManager
import com.boumusic.desktop.model.FtpConfig
import com.boumusic.desktop.model.FtpItem
import com.boumusic.desktop.model.Playlist
import com.boumusic.desktop.model.Song
import com.boumusic.desktop.model.SongSortOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class DesktopSection(val title: String) {
    LIBRARY("Biblioteca"),
    PLAYLISTS("Listas"),
    SERVERS("Servidores (SMB / FTP)"),
    BACKUP("Sincronización & Backup")
}

class DesktopMusicViewModel(
    val repository: MusicRepository = MusicRepository(),
    val ftpManager: FtpManager = FtpManager(),
    val player: AudioPlayer = AudioPlayer()
) {

    private val scope = CoroutineScope(Dispatchers.Main)

    private val _currentSection = MutableStateFlow(DesktopSection.LIBRARY)
    val currentSection: StateFlow<DesktopSection> = _currentSection.asStateFlow()

    fun setSection(section: DesktopSection) {
        _currentSection.value = section
    }

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

    val filteredSongs: StateFlow<List<Song>> = combine(
        _songs,
        _searchQuery,
        _sortOrder
    ) { songList, query, sort ->
        val filtered = if (query.isBlank()) {
            songList
        } else {
            val q = query.lowercase().trim()
            songList.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }
        when (sort) {
            SongSortOrder.DEFAULT -> filtered
            SongSortOrder.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            SongSortOrder.ARTIST_ASC -> filtered.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
            SongSortOrder.DURATION_DESC -> filtered.sortedByDescending { it.duration }
            SongSortOrder.RECENT -> filtered.sortedByDescending { it.id }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    // COLA Y REPRODUCCIÓN
    private val _playbackQueue = MutableStateFlow<List<Song>>(emptyList())
    val playbackQueue: StateFlow<List<Song>> = _playbackQueue.asStateFlow()

    private var currentIndex = 0
    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun playSong(song: Song, queue: List<Song> = emptyList()) {
        _playbackQueue.value = if (queue.isNotEmpty()) queue else _songs.value
        currentIndex = _playbackQueue.value.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        player.play(song)
    }

    fun playNext() {
        val q = _playbackQueue.value
        if (q.isEmpty()) return
        currentIndex = if (_isShuffle.value) {
            (0 until q.size).random()
        } else {
            (currentIndex + 1) % q.size
        }
        val nextSong = q.getOrNull(currentIndex) ?: return
        if (nextSong.isRemote) {
            val config = _activeServer.value
            if (config != null) {
                player.play(nextSong) { ftpManager.openStream(config, nextSong.remotePath) }
            }
        } else {
            player.play(nextSong)
        }
    }

    fun playPrevious() {
        val q = _playbackQueue.value
        if (q.isEmpty()) return
        currentIndex = (currentIndex - 1 + q.size) % q.size
        val prevSong = q.getOrNull(currentIndex) ?: return
        if (prevSong.isRemote) {
            val config = _activeServer.value
            if (config != null) {
                player.play(prevSong) { ftpManager.openStream(config, prevSong.remotePath) }
            }
        } else {
            player.play(prevSong)
        }
    }

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
            val result = ftpManager.listDirectory(config, path)
            result.onSuccess { items ->
                _serverItems.value = items
                _serverPath.value = path
                _isServerLoading.value = false
            }.onFailure { err ->
                _serverError.value = err.message ?: "Error al conectar"
                _isServerLoading.value = false
            }
        }
    }

    fun playRemoteSong(item: FtpItem) {
        val config = _activeServer.value ?: return
        val song = Song(
            id = item.path.hashCode().toLong(),
            title = item.name.substringBeforeLast("."),
            artist = config.name,
            album = config.protocolLabel,
            duration = 0L,
            path = item.path,
            isRemote = true,
            remoteHost = config.host,
            remotePath = item.path
        )
        val audios = _serverItems.value.filter { it.isAudio }.map {
            Song(
                id = it.path.hashCode().toLong(),
                title = it.name.substringBeforeLast("."),
                artist = config.name,
                album = config.protocolLabel,
                duration = 0L,
                path = it.path,
                isRemote = true,
                remoteHost = config.host,
                remotePath = it.path
            )
        }
        _playbackQueue.value = audios
        currentIndex = audios.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        player.play(song) {
            ftpManager.openStream(config, item.path)
        }
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

    init {
        player.onSongFinished = { playNext() }
        scope.launch {
            _songs.value = repository.scanLocalSongs()
            _savedServers.value = repository.loadSavedServers()
            _playlists.value = repository.loadPlaylists()
        }
    }
}
