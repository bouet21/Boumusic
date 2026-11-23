package com.boumusic.desktop.audio

import com.boumusic.desktop.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10

class AudioPlayer {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val lock = Any()

    @Volatile
    private var currentGeneration = 0L

    @Volatile
    private var activeLine: SourceDataLine? = null

    @Volatile
    private var activeStream: InputStream? = null

    @Volatile
    private var isPaused = false

    private var activeStreamProvider: (() -> InputStream)? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionSec = MutableStateFlow(0L)
    val currentPositionSec: StateFlow<Long> = _currentPositionSec.asStateFlow()

    private val _durationSec = MutableStateFlow(0L)
    val durationSec: StateFlow<Long> = _durationSec.asStateFlow()

    private val _volume = MutableStateFlow(0.85f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    val equalizer = DesktopEqualizer()

    @Volatile
    private var softwareVolume: Float = 0.85f

    var onSongFinished: (() -> Unit)? = null
    var onSongPlaybackLogged: ((Song, Long) -> Unit)? = null
    var onMetadataUpdated: ((Song) -> Unit)? = null

    fun play(song: Song, startFromSec: Long = 0L, streamProvider: (() -> InputStream)? = null) {
        val previousSong = _currentSong.value
        val playedSec = _currentPositionSec.value
        if (previousSong != null && playedSec >= 5L) {
            onSongPlaybackLogged?.invoke(previousSong, playedSec * 1000L)
        }

        val generation: Long
        synchronized(lock) {
            generation = ++currentGeneration
            activeStreamProvider = streamProvider
            stopActiveLineAndStream()
        }

        _currentSong.value = song
        val initSec = if (song.duration in 1..86400000) song.duration / 1000L else 0L
        if (initSec > 0L) {
            _durationSec.value = initSec
        }
        _currentPositionSec.value = startFromSec
        isPaused = false

        scope.launch {
            startPlaybackWorker(generation, song, streamProvider, startFromSec)
        }
    }

    private fun startPlaybackWorker(
        generation: Long,
        song: Song,
        streamProvider: (() -> InputStream)?,
        startFromSec: Long
    ) {
        var rawStream: InputStream? = null
        var inStream: AudioInputStream? = null
        var decodedStream: AudioInputStream? = null
        var line: SourceDataLine? = null
        var triggerFinished = false

        try {
            if (generation != currentGeneration) return

            rawStream = if (streamProvider != null) {
                streamProvider()
            } else {
                val f = File(song.path)
                if (!f.exists()) return
                FileInputStream(f)
            }

            synchronized(lock) {
                if (generation != currentGeneration) {
                    try { rawStream.close() } catch (ignored: Exception) {}
                    return
                }
                activeStream = rawStream
            }

            val buffered = BufferedInputStream(rawStream, 256 * 1024)

            // Detección precisa de metadatos (ID3v2, FLAC Vorbis, duración) desde la cabecera
            val effectiveFileSize = if (!song.isRemote) {
                File(song.path).takeIf { it.exists() }?.length() ?: 0L
            } else {
                song.fileSize
            }
            val meta = AudioMetadataHelper.extractMetadata(buffered, effectiveFileSize)
            var detectedDurationSec = meta.durationSec

            inStream = AudioSystem.getAudioInputStream(buffered)
            val baseFormat = inStream.format

            if (detectedDurationSec <= 0L) {
                if (inStream.frameLength > 0 && baseFormat.frameRate > 0) {
                    detectedDurationSec = (inStream.frameLength / baseFormat.frameRate).toLong()
                }
            }

            if (detectedDurationSec <= 0L && song.duration in 1..86400000) {
                detectedDurationSec = song.duration / 1000L
            }

            if (detectedDurationSec > 0L) {
                _durationSec.value = detectedDurationSec
            }

            // Enriquecer la canción actual con título, artista, álbum y duración reales
            val cur = _currentSong.value
            if (cur != null && cur.id == song.id) {
                val newDuration = if (detectedDurationSec > 0L) detectedDurationSec * 1000L else cur.duration
                val newTitle = meta.title?.takeIf { it.isNotBlank() } ?: cur.title
                val newArtist = meta.artist?.takeIf { it.isNotBlank() } ?: cur.artist
                val newAlbum = meta.album?.takeIf { it.isNotBlank() } ?: cur.album
                if (newDuration != cur.duration || newTitle != cur.title || newArtist != cur.artist || newAlbum != cur.album) {
                    val updated = cur.copy(
                        duration = newDuration,
                        title = newTitle,
                        artist = newArtist,
                        album = newAlbum
                    )
                    _currentSong.value = updated
                    onMetadataUpdated?.invoke(updated)
                }
            }

            val sampleRate = if (baseFormat.sampleRate > 0) baseFormat.sampleRate else 44100.0f
            val channels = if (baseFormat.channels > 0) baseFormat.channels else 2

            val decodedFormat = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                16,
                channels,
                channels * 2,
                sampleRate,
                false
            )

            decodedStream = AudioSystem.getAudioInputStream(decodedFormat, inStream)

            val info = DataLine.Info(SourceDataLine::class.java, decodedFormat)
            if (!AudioSystem.isLineSupported(info)) {
                return
            }

            line = AudioSystem.getLine(info) as SourceDataLine

            equalizer.setSampleRate(sampleRate)

            // Búfer ultrarrápido (~80ms de audio) para respuesta instantánea de volumen y ecualizador sin cortes
            val lineBufferSize = (sampleRate * channels * 2 * 0.08).toInt().coerceIn(8192, 16384)
            line.open(decodedFormat, lineBufferSize)
            line.start()

            // Fijar ganancia de hardware a 0 dB para que no haya atenuación doble
            try {
                if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    val gainControl = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                    gainControl.value = 0f.coerceIn(gainControl.minimum, gainControl.maximum)
                }
            } catch (ignored: Exception) {}

            synchronized(lock) {
                if (generation != currentGeneration) {
                    try {
                        line.stop()
                        line.close()
                    } catch (ignored: Exception) {}
                    return
                }
                activeLine = line
            }

            _isPlaying.value = true

            val bytesPerSecond = (sampleRate * channels * 2).toLong()

            // Posicionamiento de búsqueda rápido y seguro (Seek)
            if (startFromSec > 0 && bytesPerSecond > 0) {
                var remainingToSkip = startFromSec * bytesPerSecond
                val skipBuf = ByteArray(32768)
                while (remainingToSkip > 0 && generation == currentGeneration) {
                    val skipped = try { decodedStream.skip(remainingToSkip) } catch (_: Exception) { 0L }
                    if (skipped > 0) {
                        remainingToSkip -= skipped
                    } else {
                        val toRead = remainingToSkip.coerceAtMost(skipBuf.size.toLong()).toInt()
                        val r = decodedStream.read(skipBuf, 0, toRead)
                        if (r <= 0) break
                        remainingToSkip -= r
                    }
                }
            }

            val buffer = ByteArray(4096)
            var bytesRead: Int
            var totalBytesRead = if (startFromSec > 0) startFromSec * bytesPerSecond else 0L
            var naturalEof = false

            while (generation == currentGeneration && line.isOpen) {
                if (isPaused) {
                    Thread.sleep(50)
                    continue
                }

                bytesRead = decodedStream.read(buffer, 0, buffer.size)
                if (bytesRead == -1) {
                    naturalEof = true
                    break
                }

                // Procesamiento instantáneo de ecualizador y volumen en PCM
                equalizer.processPcmBuffer(buffer, bytesRead, channels, softwareVolume)

                line.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                if (bytesPerSecond > 0) {
                    val currentSec = totalBytesRead / bytesPerSecond
                    if (currentSec != _currentPositionSec.value) {
                        _currentPositionSec.value = currentSec
                    }
                }
            }

            if (generation == currentGeneration && naturalEof && !isPaused) {
                try { line.drain() } catch (ignored: Exception) {}
                _isPlaying.value = false
                triggerFinished = true
            }
        } catch (e: Exception) {
            // Manejo de finalización de pista o cancelación intencional
            if (generation == currentGeneration) {
                val nearEnd = _durationSec.value > 0 && _currentPositionSec.value >= (_durationSec.value - 3)
                if (nearEnd || e is java.io.EOFException) {
                    triggerFinished = true
                } else {
                    e.printStackTrace()
                    _isPlaying.value = false
                }
            }
        } finally {
            try { line?.stop() } catch (ignored: Exception) {}
            try { line?.close() } catch (ignored: Exception) {}
            try { decodedStream?.close() } catch (ignored: Exception) {}
            try { inStream?.close() } catch (ignored: Exception) {}
            try { rawStream?.close() } catch (ignored: Exception) {}

            synchronized(lock) {
                if (generation == currentGeneration) {
                    activeLine = null
                    activeStream = null
                }
            }
        }

        // Se invoca el paso a la siguiente canción DESPUÉS de haber liberado todos los recursos (línea de audio y sockets)
        if (triggerFinished && generation == currentGeneration) {
            val finishedSong = _currentSong.value
            val playedSec = _currentPositionSec.value
            if (finishedSong != null && playedSec >= 5L) {
                onSongPlaybackLogged?.invoke(finishedSong, playedSec * 1000L)
            }
            scope.launch {
                delay(120)
                if (generation == currentGeneration) {
                    onSongFinished?.invoke()
                }
            }
        }
    }

    fun pause() {
        if (_isPlaying.value) {
            isPaused = true
            _isPlaying.value = false
            try {
                activeLine?.stop()
            } catch (ignored: Exception) {}
        }
    }

    fun resume() {
        if (isPaused && activeLine != null) {
            isPaused = false
            _isPlaying.value = true
            try {
                activeLine?.start()
            } catch (ignored: Exception) {}
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else if (isPaused) {
            resume()
        } else {
            _currentSong.value?.let { play(it, startFromSec = 0L, streamProvider = activeStreamProvider) }
        }
    }

    fun stop() {
        synchronized(lock) {
            currentGeneration++
            isPaused = false
            stopActiveLineAndStream()
        }
        _isPlaying.value = false
        _currentPositionSec.value = 0L
    }

    fun seekTo(seconds: Long) {
        val song = _currentSong.value ?: return
        val clampedSec = seconds.coerceIn(0L, _durationSec.value.coerceAtLeast(100L))
        _currentPositionSec.value = clampedSec
        play(song, startFromSec = clampedSec, streamProvider = activeStreamProvider)
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        softwareVolume = clamped
    }

    private fun stopActiveLineAndStream() {
        try {
            activeLine?.stop()
            activeLine?.flush()
            activeLine?.close()
        } catch (ignored: Exception) {}
        activeLine = null

        try {
            activeStream?.close()
        } catch (ignored: Exception) {}
        activeStream = null
    }

    private fun applyVolumeToLine(line: SourceDataLine, vol: Float) {
        try {
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val gainControl = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                val min = gainControl.minimum
                val max = gainControl.maximum
                val dB = if (vol <= 0.0001f) min else (20f * log10(vol)).coerceIn(min, max)
                gainControl.value = dB
            }
        } catch (e: Exception) {
            // Ignorado si el controlador de hardware no soporta control de ganancia directo
        }
    }
}
