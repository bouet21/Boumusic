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

    var onSongFinished: (() -> Unit)? = null

    fun play(song: Song, startFromSec: Long = 0L, streamProvider: (() -> InputStream)? = null) {
        val generation: Long
        synchronized(lock) {
            generation = ++currentGeneration
            activeStreamProvider = streamProvider
            stopActiveLineAndStream()
        }

        _currentSong.value = song
        _durationSec.value = if (song.duration > 0) song.duration / 1000 else 0L
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

            // Detección dinámica de duración desde cabecera de audio (MP3, FLAC, WAV, OGG)
            var detectedDurationSec = 0L
            try {
                buffered.mark(256 * 1024)
                val fileFormat = AudioSystem.getAudioFileFormat(buffered)
                val props = fileFormat.properties()
                val microSec = (props?.get("duration") as? Number)?.toLong()
                    ?: (props?.get("mp3.duration.microseconds") as? Number)?.toLong()
                if (microSec != null && microSec > 0) {
                    detectedDurationSec = microSec / 1_000_000L
                } else if (fileFormat.frameLength > 0 && fileFormat.format.frameRate > 0) {
                    detectedDurationSec = (fileFormat.frameLength / fileFormat.format.frameRate).toLong()
                }
                buffered.reset()
            } catch (_: Throwable) {
                try { buffered.reset() } catch (_: Throwable) {}
            }

            inStream = AudioSystem.getAudioInputStream(buffered)
            val baseFormat = inStream.format

            if (detectedDurationSec <= 0) {
                if (inStream.frameLength > 0 && baseFormat.frameRate > 0) {
                    detectedDurationSec = (inStream.frameLength / baseFormat.frameRate).toLong()
                }
            }

            if (detectedDurationSec <= 0 && song.duration > 0) {
                detectedDurationSec = song.duration / 1000
            }

            if (detectedDurationSec > 0) {
                _durationSec.value = detectedDurationSec
                _currentSong.value = _currentSong.value?.takeIf { it.id == song.id }?.copy(duration = detectedDurationSec * 1000)
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

            // Búfer amplio (~750ms de audio) para evitar cualquier tipo de corte o "ralladura" por buffer underrun
            val lineBufferSize = (sampleRate * channels * 2 * 0.75).toInt().coerceAtLeast(65536)
            line.open(decodedFormat, lineBufferSize)
            line.start()

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

            applyVolumeToLine(line, _volume.value)
            _isPlaying.value = true

            val bytesPerSecond = (sampleRate * channels * 2).toLong()

            // Si hay que posicionarse en un segundo concreto (seek)
            if (startFromSec > 0 && bytesPerSecond > 0) {
                val bytesToSkip = startFromSec * bytesPerSecond
                var skipped = 0L
                while (skipped < bytesToSkip && generation == currentGeneration) {
                    val s = decodedStream.skip(bytesToSkip - skipped)
                    if (s <= 0) break
                    skipped += s
                }
            }

            val buffer = ByteArray(8192)
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

                line.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                if (bytesPerSecond > 0) {
                    val currentSec = totalBytesRead / bytesPerSecond
                    _currentPositionSec.value = currentSec
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
        activeLine?.let { applyVolumeToLine(it, clamped) }
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
