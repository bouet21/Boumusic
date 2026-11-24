package com.boumusic.desktop.audio

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val name: String,
    val minGainDb: Float = -12f,
    val maxGainDb: Float = 12f
)

data class EqualizerPreset(
    val name: String,
    val bandGains: List<Float>,
    val bassBoostDb: Float = 0f
)

/**
 * Filtro Biquad IIR de segundo orden para ecualización en tiempo real de muestras PCM
 */
private class BiquadFilter {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    // Estados para estéreo: [canal][historia]
    private val x1 = DoubleArray(2)
    private val x2 = DoubleArray(2)
    private val y1 = DoubleArray(2)
    private val y2 = DoubleArray(2)

    fun reset() {
        x1.fill(0.0)
        x2.fill(0.0)
        y1.fill(0.0)
        y2.fill(0.0)
    }

    /**
     * Configura el filtro como ecualizador paramétrico (Peaking EQ)
     */
    fun configurePeaking(sampleRate: Float, centerFreq: Float, gainDb: Float, q: Float = 1.414f) {
        if (gainDb == 0f) {
            b0 = 1.0; b1 = 0.0; b2 = 0.0; a1 = 0.0; a2 = 0.0
            return
        }
        val clampedFreq = centerFreq.coerceIn(20f, (sampleRate * 0.45f).coerceAtLeast(1000f))
        val a = 10.0.pow((gainDb / 40.0))
        val omega = 2.0 * PI * clampedFreq / sampleRate
        val alpha = sin(omega) / (2.0 * q)
        val cosOmega = cos(omega)

        val a0 = 1.0 + alpha / a
        b0 = (1.0 + alpha * a) / a0
        b1 = (-2.0 * cosOmega) / a0
        b2 = (1.0 - alpha * a) / a0
        a1 = (-2.0 * cosOmega) / a0
        a2 = (1.0 - alpha / a) / a0
    }

    /**
     * Procesa una muestra PCM en el canal dado
     */
    fun processSample(channel: Int, x: Double): Double {
        val ch = if (channel in 0..1) channel else 0
        val y = b0 * x + b1 * x1[ch] + b2 * x2[ch] - a1 * y1[ch] - a2 * y2[ch]
        x2[ch] = x1[ch]
        x1[ch] = x
        y2[ch] = y1[ch]
        y1[ch] = y
        return y
    }
}

class DesktopEqualizer {
    val bands = listOf(
        EqualizerBand(0, 60, "60 Hz"),
        EqualizerBand(1, 230, "230 Hz"),
        EqualizerBand(2, 910, "910 Hz"),
        EqualizerBand(3, 3600, "3.6 kHz"),
        EqualizerBand(4, 14000, "14 kHz")
    )

    val presets = listOf(
        EqualizerPreset("Plano", listOf(0f, 0f, 0f, 0f, 0f), 0f),
        EqualizerPreset("Rock", listOf(4f, 2f, -1f, 3f, 5f), 2f),
        EqualizerPreset("Pop", listOf(2f, 3f, 1f, 2f, 3f), 1f),
        EqualizerPreset("Clásica", listOf(3f, 2f, -1f, 2f, 3f), 0f),
        EqualizerPreset("Jazz", listOf(3f, 1f, 2f, 1f, 3f), 0f),
        EqualizerPreset("Electrónica", listOf(5f, 3f, 0f, 2f, 4f), 4f),
        EqualizerPreset("Refuerzo de graves", listOf(6f, 4f, 1f, 0f, 0f), 6f),
        EqualizerPreset("Vocal", listOf(-2f, 1f, 4f, 3f, -1f), 0f),
        EqualizerPreset("Acústico", listOf(3f, 2f, 1f, 2f, 2f), 1f)
    )

    private val filters = Array(bands.size) { BiquadFilter() }
    private val bassBoostFilter = BiquadFilter()
    private val activeFilters = mutableListOf<Pair<BiquadFilter, Int>>()

    @Volatile
    var isEnabled: Boolean = true
        private set

    @Volatile
    private var hasActiveFilters: Boolean = false

    private val bandGains = FloatArray(bands.size) { 0f }
    var bassBoostDb: Float = 0f
        private set

    var currentPresetName: String = "Plano"
        private set

    private var currentSampleRate: Float = 44100f
    private val configFile: File

    init {
        val userHome = System.getProperty("user.home") ?: "."
        val dir = File(userHome, ".boumusic-desktop")
        if (!dir.exists()) dir.mkdirs()
        configFile = File(dir, "equalizer_settings.json")
        loadSettings()
        recomputeCoefficients()
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        recomputeCoefficients()
        saveSettings()
    }

    @Synchronized
    fun getBandGain(index: Int): Float {
        return bandGains.getOrElse(index) { 0f }
    }

    @Synchronized
    fun setBandGain(index: Int, gainDb: Float) {
        if (index in bandGains.indices) {
            bandGains[index] = gainDb.coerceIn(-12f, 12f)
            currentPresetName = "Personalizado"
            recomputeCoefficients()
            saveSettings()
        }
    }

    @Synchronized
    fun setBassBoost(gainDb: Float) {
        bassBoostDb = gainDb.coerceIn(0f, 10f)
        recomputeCoefficients()
        saveSettings()
    }

    @Synchronized
    fun applyPreset(preset: EqualizerPreset) {
        currentPresetName = preset.name
        for (i in bandGains.indices) {
            bandGains[i] = preset.bandGains.getOrElse(i) { 0f }
        }
        bassBoostDb = preset.bassBoostDb
        recomputeCoefficients()
        saveSettings()
    }

    @Synchronized
    fun resetToFlat() {
        applyPreset(presets[0])
    }

    @Synchronized
    fun setSampleRate(sampleRate: Float) {
        if (sampleRate > 0 && sampleRate != currentSampleRate) {
            currentSampleRate = sampleRate
            for (f in filters) f.reset()
            bassBoostFilter.reset()
            recomputeCoefficients()
        }
    }

    private fun recomputeCoefficients() {
        activeFilters.clear()
        var nonZero = false

        for (i in bands.indices) {
            val gain = bandGains[i]
            filters[i].configurePeaking(
                sampleRate = currentSampleRate,
                centerFreq = bands[i].centerFreqHz.toFloat(),
                gainDb = gain,
                q = 1.414f
            )
            if (gain != 0f) {
                activeFilters.add(filters[i] to i)
                nonZero = true
            }
        }
        bassBoostFilter.configurePeaking(
            sampleRate = currentSampleRate,
            centerFreq = 65f,
            gainDb = bassBoostDb,
            q = 1.0f
        )
        if (bassBoostDb > 0.05f) {
            nonZero = true
        }
        hasActiveFilters = isEnabled && nonZero
    }

    /**
     * Procesa el búfer de audio PCM en 16-bit little-endian estéreo o mono in-place.
     * Optimizado para consumo de CPU mínimo (bypass zero-copy y aritmética fija ultrarrápida).
     */
    fun processPcmBuffer(buffer: ByteArray, bytesRead: Int, channels: Int, volume: Float) {
        if (bytesRead <= 0) return

        val eqActive = hasActiveFilters

        // 1. Máxima optimización: Sin ecualizador y volumen al 100% -> Retorno inmediato con 0% de CPU
        if (!eqActive && volume >= 0.999f) {
            return
        }

        // 2. Sin ecualizador pero con control de volumen: Aritmética entera de 16-bit directa (~10x más rápida)
        if (!eqActive) {
            val volMultiplier = (volume.coerceIn(0f, 1f) * 32768f).toInt()
            var i = 0
            while (i < bytesRead - 1) {
                val raw = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                val sample = raw.toShort().toInt()
                val scaled = (sample * volMultiplier) shr 15
                val clamped = scaled.coerceIn(-32768, 32767)
                buffer[i] = (clamped and 0xFF).toByte()
                buffer[i + 1] = ((clamped shr 8) and 0xFF).toByte()
                i += 2
            }
            return
        }

        // 3. Ecualizador activo: Filtra únicamente las bandas activas con ganancia distinta de 0 dB
        val numChannels = channels.coerceIn(1, 2)
        val activeList = activeFilters
        val applyBass = bassBoostDb > 0.05f
        val clampedVol = volume.coerceIn(0f, 1f)

        var sampleIndex = 0
        var i = 0
        while (i < bytesRead - 1) {
            val channel = sampleIndex % numChannels
            sampleIndex++

            val rawSample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            var sampleDouble = rawSample.toShort().toDouble()

            for (pair in activeList) {
                sampleDouble = pair.first.processSample(channel, sampleDouble)
            }
            if (applyBass) {
                sampleDouble = bassBoostFilter.processSample(channel, sampleDouble)
            }

            if (clampedVol < 0.999f) {
                sampleDouble *= clampedVol
            }

            val clamped = sampleDouble.toInt().coerceIn(-32768, 32767)
            buffer[i] = (clamped and 0xFF).toByte()
            buffer[i + 1] = ((clamped shr 8) and 0xFF).toByte()

            i += 2
        }
    }

    private fun saveSettings() {
        try {
            val root = JSONObject()
            root.put("isEnabled", isEnabled)
            root.put("preset", currentPresetName)
            root.put("bassBoost", bassBoostDb.toDouble())
            val arr = JSONArray()
            for (g in bandGains) arr.put(g.toDouble())
            root.put("gains", arr)
            configFile.writeText(root.toString())
        } catch (_: Exception) {}
    }

    private fun loadSettings() {
        try {
            if (!configFile.exists()) return
            val json = configFile.readText()
            if (json.isBlank()) return
            val root = JSONObject(json)
            isEnabled = root.optBoolean("isEnabled", true)
            currentPresetName = root.optString("preset", "Plano")
            bassBoostDb = root.optDouble("bassBoost", 0.0).toFloat()
            val arr = root.optJSONArray("gains")
            if (arr != null) {
                for (i in 0 until arr.length().coerceAtMost(bandGains.size)) {
                    bandGains[i] = arr.getDouble(i).toFloat()
                }
            }
        } catch (_: Exception) {}
    }
}
