package com.boumusic.desktop.data

import com.boumusic.desktop.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

data class PlaybackRecord(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val timestamp: Long,
    val durationPlayedMs: Long,
    val year: Int,
    val quarter: Int // 1, 2, 3 o 4
)

enum class StatsPeriodType(val label: String) {
    LAST_3_MONTHS("Últimos 3 Meses"),
    CURRENT_QUARTER("Trimestre Actual"),
    PREVIOUS_QUARTER("Trimestre Anterior"),
    ALL_TIME("Histórico Completo")
}

data class SongStatItem(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val playCount: Int
)

data class QuarterStats(
    val periodLabel: String,
    val totalMinutes: Long,
    val totalPlays: Int,
    val topSong: SongStatItem?,
    val topSongs: List<Pair<SongStatItem, Int>>,
    val topArtist: String?,
    val topArtists: List<Pair<String, Long>>, // Artista y minutos
    val topGenre: String?,
    val topGenres: List<Pair<String, Int>>,
    val favoriteTimeOfDay: String
)

class HistoryAndStatsManager {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val historyFile: File

    private val historyMap = ConcurrentHashMap<Long, SongStatItem>()
    private val recordsList = Collections.synchronizedList(mutableListOf<PlaybackRecord>())

    private val _topSongsFlow = MutableStateFlow<List<SongStatItem>>(emptyList())
    val topSongsFlow: StateFlow<List<SongStatItem>> = _topSongsFlow.asStateFlow()

    private var saveJob: Job? = null

    init {
        val userHome = System.getProperty("user.home") ?: "."
        val dir = File(userHome, ".boumusic-desktop")
        if (!dir.exists()) dir.mkdirs()
        historyFile = File(dir, "playback_history_stats.json")
        loadFromDisk()
    }

    fun recordPlayback(song: Song, durationPlayedMs: Long) {
        if (durationPlayedMs < 5000L) return // No contabilizar canciones saltadas inmediatamente

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val quarter = (month / 3) + 1

        val record = PlaybackRecord(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            genre = song.genre.ifBlank { "Varios" },
            timestamp = now,
            durationPlayedMs = durationPlayedMs,
            year = year,
            quarter = quarter
        )

        recordsList.add(record)

        val currentItem = historyMap[song.id]
        val newCount = (currentItem?.playCount ?: 0) + 1
        historyMap[song.id] = SongStatItem(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            genre = song.genre.ifBlank { "Varios" },
            playCount = newCount
        )

        updateFlows()
        scheduleSave()
    }

    private fun updateFlows() {
        val top = historyMap.values.sortedByDescending { it.playCount }.take(20)
        _topSongsFlow.value = top
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1000)
            saveToDisk()
        }
    }

    @Synchronized
    private fun saveToDisk() {
        try {
            val root = JSONObject()
            val historyArr = JSONArray()
            for (item in historyMap.values) {
                val o = JSONObject()
                o.put("songId", item.songId)
                o.put("title", item.title)
                o.put("artist", item.artist)
                o.put("album", item.album)
                o.put("genre", item.genre)
                o.put("playCount", item.playCount)
                historyArr.put(o)
            }
            root.put("history", historyArr)

            val recordsArr = JSONArray()
            val snapshot = synchronized(recordsList) { recordsList.toList() }
            for (r in snapshot) {
                val o = JSONObject()
                o.put("songId", r.songId)
                o.put("title", r.title)
                o.put("artist", r.artist)
                o.put("album", r.album)
                o.put("genre", r.genre)
                o.put("timestamp", r.timestamp)
                o.put("durationPlayedMs", r.durationPlayedMs)
                o.put("year", r.year)
                o.put("quarter", r.quarter)
                recordsArr.put(o)
            }
            root.put("records", recordsArr)

            historyFile.writeText(root.toString())
        } catch (_: Exception) {}
    }

    private fun loadFromDisk() {
        if (!historyFile.exists()) return
        try {
            val json = historyFile.readText()
            if (json.isBlank()) return
            val root = JSONObject(json)

            val historyArr = root.optJSONArray("history") ?: JSONArray()
            for (i in 0 until historyArr.length()) {
                val o = historyArr.getJSONObject(i)
                val item = SongStatItem(
                    songId = o.getLong("songId"),
                    title = o.getString("title"),
                    artist = o.getString("artist"),
                    album = o.optString("album", ""),
                    genre = o.optString("genre", "Varios"),
                    playCount = o.optInt("playCount", 1)
                )
                historyMap[item.songId] = item
            }

            val recordsArr = root.optJSONArray("records") ?: JSONArray()
            val loaded = mutableListOf<PlaybackRecord>()
            for (i in 0 until recordsArr.length()) {
                val o = recordsArr.getJSONObject(i)
                loaded.add(
                    PlaybackRecord(
                        songId = o.getLong("songId"),
                        title = o.getString("title"),
                        artist = o.getString("artist"),
                        album = o.optString("album", ""),
                        genre = o.optString("genre", "Varios"),
                        timestamp = o.getLong("timestamp"),
                        durationPlayedMs = o.optLong("durationPlayedMs", 0L),
                        year = o.optInt("year", 2026),
                        quarter = o.optInt("quarter", 4)
                    )
                )
            }
            synchronized(recordsList) {
                recordsList.clear()
                recordsList.addAll(loaded)
            }
            updateFlows()
        } catch (_: Exception) {}
    }

    fun calculateStats(periodType: StatsPeriodType): QuarterStats {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH)
        val currentQuarter = (currentMonth / 3) + 1

        val filtered = synchronized(recordsList) {
            when (periodType) {
                StatsPeriodType.LAST_3_MONTHS -> {
                    val cutoff = now - (90L * 24 * 60 * 60 * 1000)
                    recordsList.filter { it.timestamp >= cutoff }
                }
                StatsPeriodType.CURRENT_QUARTER -> {
                    recordsList.filter { it.year == currentYear && it.quarter == currentQuarter }
                }
                StatsPeriodType.PREVIOUS_QUARTER -> {
                    val prevQuarter = if (currentQuarter == 1) 4 else currentQuarter - 1
                    val prevYear = if (currentQuarter == 1) currentYear - 1 else currentYear
                    recordsList.filter { it.year == prevYear && it.quarter == prevQuarter }
                }
                StatsPeriodType.ALL_TIME -> recordsList.toList()
            }
        }

        val label = when (periodType) {
            StatsPeriodType.LAST_3_MONTHS -> "Últimos 3 Meses"
            StatsPeriodType.CURRENT_QUARTER -> "Trimestre Actual (T$currentQuarter $currentYear)"
            StatsPeriodType.PREVIOUS_QUARTER -> {
                val prevQuarter = if (currentQuarter == 1) 4 else currentQuarter - 1
                val prevYear = if (currentQuarter == 1) currentYear - 1 else currentYear
                "Trimestre Anterior (T$prevQuarter $prevYear)"
            }
            StatsPeriodType.ALL_TIME -> "Histórico Completo"
        }

        val totalPlayedMs = filtered.sumOf { it.durationPlayedMs }
        val totalMinutes = totalPlayedMs / 60000

        // Canciones más escuchadas
        val songPlayCounts = filtered.groupBy { "${it.title.lowercase()}||${it.artist.lowercase()}" }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }

        val topSongs = songPlayCounts.take(5).mapNotNull { (key, count) ->
            val sample = filtered.find { "${it.title.lowercase()}||${it.artist.lowercase()}" == key }
            if (sample != null) {
                Pair(
                    SongStatItem(
                        songId = sample.songId,
                        title = sample.title,
                        artist = sample.artist,
                        album = sample.album,
                        genre = sample.genre,
                        playCount = count
                    ),
                    count
                )
            } else null
        }

        val topSong = topSongs.firstOrNull()?.first

        // Artistas más escuchados
        val artistDurations = filtered.groupBy { it.artist.ifBlank { "Desconocido" } }
            .mapValues { it.value.sumOf { r -> r.durationPlayedMs } / 60000 }
            .toList()
            .sortedByDescending { it.second }

        val topArtists = artistDurations.take(5)
        val topArtist = topArtists.firstOrNull()?.first

        // Géneros dominantes
        val genreCounts = filtered.groupBy { it.genre.ifBlank { "Varios" } }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }

        val topGenres = genreCounts.take(5)
        val topGenre = topGenres.firstOrNull()?.first

        // Momento del día
        val timeOfDayCount = mutableMapOf(
            "Mañanas (06:00 - 12:00) 🌅" to 0,
            "Tardes (12:00 - 18:00) 🌇" to 0,
            "Noches (18:00 - 24:00) 🌙" to 0,
            "Madrugadas (00:00 - 06:00) 🌌" to 0
        )
        val c = Calendar.getInstance()
        for (r in filtered) {
            c.timeInMillis = r.timestamp
            when (c.get(Calendar.HOUR_OF_DAY)) {
                in 6..11 -> timeOfDayCount["Mañanas (06:00 - 12:00) 🌅"] = (timeOfDayCount["Mañanas (06:00 - 12:00) 🌅"] ?: 0) + 1
                in 12..17 -> timeOfDayCount["Tardes (12:00 - 18:00) 🌇"] = (timeOfDayCount["Tardes (12:00 - 18:00) 🌇"] ?: 0) + 1
                in 18..23 -> timeOfDayCount["Noches (18:00 - 24:00) 🌙"] = (timeOfDayCount["Noches (18:00 - 24:00) 🌙"] ?: 0) + 1
                else -> timeOfDayCount["Madrugadas (00:00 - 06:00) 🌌"] = (timeOfDayCount["Madrugadas (00:00 - 06:00) 🌌"] ?: 0) + 1
            }
        }
        val favoriteTime = timeOfDayCount.maxByOrNull { it.value }?.key ?: "Tardes (12:00 - 18:00) 🌇"

        return QuarterStats(
            periodLabel = label,
            totalMinutes = totalMinutes,
            totalPlays = filtered.size,
            topSong = topSong,
            topSongs = topSongs,
            topArtist = topArtist,
            topArtists = topArtists,
            topGenre = topGenre,
            topGenres = topGenres,
            favoriteTimeOfDay = favoriteTime
        )
    }

    /**
     * Sincroniza datos de reproducción bidireccionalmente con el móvil
     */
    fun exportSyncData(): JSONObject {
        val root = JSONObject()
        val historyArr = JSONArray()
        for (item in historyMap.values) {
            val o = JSONObject()
            o.put("songId", item.songId)
            o.put("title", item.title)
            o.put("artist", item.artist)
            o.put("album", item.album)
            o.put("genre", item.genre)
            o.put("playCount", item.playCount)
            historyArr.put(o)
        }
        root.put("history", historyArr)

        val recordsArr = JSONArray()
        val snapshot = synchronized(recordsList) { recordsList.toList() }
        for (r in snapshot) {
            val o = JSONObject()
            o.put("songId", r.songId)
            o.put("title", r.title)
            o.put("artist", r.artist)
            o.put("album", r.album)
            o.put("genre", r.genre)
            o.put("timestamp", r.timestamp)
            o.put("durationPlayedMs", r.durationPlayedMs)
            o.put("year", r.year)
            o.put("quarter", r.quarter)
            recordsArr.put(o)
        }
        root.put("records", recordsArr)
        return root
    }

    fun mergeSyncData(syncJson: JSONObject) {
        val recordsArr = syncJson.optJSONArray("records") ?: return
        val existingTimestamps = synchronized(recordsList) { recordsList.map { it.timestamp }.toSet() }
        val newRecords = mutableListOf<PlaybackRecord>()

        for (i in 0 until recordsArr.length()) {
            val o = recordsArr.getJSONObject(i)
            val ts = o.getLong("timestamp")
            if (!existingTimestamps.contains(ts)) {
                newRecords.add(
                    PlaybackRecord(
                        songId = o.optLong("songId", 0L),
                        title = o.getString("title"),
                        artist = o.getString("artist"),
                        album = o.optString("album", ""),
                        genre = o.optString("genre", "Varios"),
                        timestamp = ts,
                        durationPlayedMs = o.optLong("durationPlayedMs", 0L),
                        year = o.optInt("year", 2026),
                        quarter = o.optInt("quarter", 4)
                    )
                )
            }
        }

        if (newRecords.isNotEmpty()) {
            synchronized(recordsList) {
                recordsList.addAll(newRecords)
                recordsList.sortBy { it.timestamp }
            }
            // Recalcular contadores en historyMap
            for (r in newRecords) {
                val existing = historyMap[r.songId]
                historyMap[r.songId] = SongStatItem(
                    songId = r.songId,
                    title = r.title,
                    artist = r.artist,
                    album = r.album,
                    genre = r.genre,
                    playCount = (existing?.playCount ?: 0) + 1
                )
            }
            updateFlows()
            scheduleSave()
        }
    }
}
