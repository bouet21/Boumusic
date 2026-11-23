package com.boumusic.desktop.audio

import com.boumusic.desktop.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var playbackJob: Job? = null

    private var currentLine: SourceDataLine? = null
    private var isPaused = false

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionSec = MutableStateFlow(0L)
    val currentPositionSec: StateFlow<Long> = _currentPositionSec.asStateFlow()

    private val _durationSec = MutableStateFlow(0L)
    val durationSec: StateFlow<Long> = _durationSec.asStateFlow()

    private val _volume = MutableStateFlow(0.8f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    var onSongFinished: (() -> Unit)? = null

    fun play(song: Song, streamProvider: (() -> InputStream)? = null) {
        stop()
        _currentSong.value = song
        _durationSec.value = if (song.duration > 0) song.duration / 1000 else 0L
        _currentPositionSec.value = 0L
        isPaused = false

        playbackJob = scope.launch {
            var rawStream: InputStream? = null
            var inStream: AudioInputStream? = null
            var decodedStream: AudioInputStream? = null
            var line: SourceDataLine? = null

            try {
                rawStream = if (streamProvider != null) {
                    streamProvider()
                } else {
                    FileInputStream(File(song.path))
                }

                val buffered = BufferedInputStream(rawStream, 64 * 1024)
                inStream = AudioSystem.getAudioInputStream(buffered)
                val baseFormat = inStream.format

                val decodedFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.sampleRate,
                    16,
                    baseFormat.channels,
                    baseFormat.channels * 2,
                    baseFormat.sampleRate,
                    false
                )

                decodedStream = AudioSystem.getAudioInputStream(decodedFormat, inStream)
                val info = DataLine.Info(SourceDataLine::class.java, decodedFormat)
                line = AudioSystem.getLine(info) as SourceDataLine
                line.open(decodedFormat)
                line.start()
                currentLine = line

                applyVolumeToLine(line, _volume.value)
                _isPlaying.value = true

                val buffer = ByteArray(4096)
                var bytesRead: Int
                var totalBytesRead = 0L
                val bytesPerSecond = decodedFormat.sampleRate * decodedFormat.frameSize

                while (line.isOpen) {
                    if (isPaused) {
                        Thread.sleep(100)
                        continue
                    }

                    bytesRead = decodedStream.read(buffer, 0, buffer.size)
                    if (bytesRead == -1) break

                    line.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    if (bytesPerSecond > 0) {
                        val currentSec = totalBytesRead / bytesPerSecond.toLong()
                        _currentPositionSec.value = currentSec
                    }
                }

                line.drain()
                _isPlaying.value = false
                if (!isPaused) {
                    onSongFinished?.invoke()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _isPlaying.value = false
            } finally {
                try { line?.close() } catch (ignored: Exception) {}
                try { decodedStream?.close() } catch (ignored: Exception) {}
                try { inStream?.close() } catch (ignored: Exception) {}
                try { rawStream?.close() } catch (ignored: Exception) {}
                currentLine = null
            }
        }
    }

    fun pause() {
        if (_isPlaying.value) {
            isPaused = true
            _isPlaying.value = false
            currentLine?.stop()
        }
    }

    fun resume() {
        if (isPaused && currentLine != null) {
            isPaused = false
            _isPlaying.value = true
            currentLine?.start()
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else if (isPaused) {
            resume()
        }
    }

    fun stop() {
        isPaused = false
        _isPlaying.value = false
        _currentPositionSec.value = 0L
        playbackJob?.cancel()
        playbackJob = null
        try {
            currentLine?.stop()
            currentLine?.close()
        } catch (ignored: Exception) {}
        currentLine = null
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        currentLine?.let { applyVolumeToLine(it, clamped) }
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
            e.printStackTrace()
        }
    }
}
