package com.boumusic.desktop.audio

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import javax.sound.sampled.AudioSystem

data class AudioTrackMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationSec: Long = 0L
)

object AudioMetadataHelper {

    private val BITRATES_MPEG1_L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
    private val BITRATES_MPEG2_L3 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)
    private val SAMPLE_RATES_MPEG1 = intArrayOf(44100, 48000, 32000, 0)
    private val SAMPLE_RATES_MPEG2 = intArrayOf(22050, 24000, 16000, 0)
    private val SAMPLE_RATES_MPEG25 = intArrayOf(11025, 12000, 8000, 0)

    /**
     * Extrae información de metadatos (Título, Artista, Álbum y Duración) desde la cabecera
     * del stream sin necesidad de descargar o leer el archivo completo.
     * Soporta MP3 (ID3v2.2, ID3v2.3, ID3v2.4 y frames MPEG/Xing), FLAC (STREAMINFO y Vorbis) y WAV.
     */
    fun extractMetadata(stream: BufferedInputStream, fileSize: Long = 0L): AudioTrackMetadata {
        var durationSec = 0L
        var title: String? = null
        var artist: String? = null
        var album: String? = null

        try {
            val maxHeaderRead = 128 * 1024
            stream.mark(maxHeaderRead)
            val buffer = ByteArray(maxHeaderRead)
            var bytesRead = 0
            while (bytesRead < maxHeaderRead) {
                val r = stream.read(buffer, bytesRead, maxHeaderRead - bytesRead)
                if (r <= 0) break
                bytesRead += r
            }
            stream.reset()

            if (bytesRead < 16) return AudioTrackMetadata()

            // 1. WAV
            if (buffer[0] == 'R'.code.toByte() && buffer[1] == 'I'.code.toByte() &&
                buffer[2] == 'F'.code.toByte() && buffer[3] == 'F'.code.toByte() &&
                bytesRead > 36 && buffer[8] == 'W'.code.toByte() && buffer[9] == 'A'.code.toByte() &&
                buffer[10] == 'V'.code.toByte() && buffer[11] == 'E'.code.toByte()
            ) {
                durationSec = parseWavDuration(buffer, bytesRead, fileSize)
                return AudioTrackMetadata(durationSec = durationSec)
            }

            // 2. FLAC
            if (buffer[0] == 'f'.code.toByte() && buffer[1] == 'L'.code.toByte() &&
                buffer[2] == 'a'.code.toByte() && buffer[3] == 'C'.code.toByte() && bytesRead >= 42
            ) {
                durationSec = parseFlacDuration(buffer, bytesRead)
                val flacMeta = parseFlacVorbisComment(buffer, bytesRead)
                return AudioTrackMetadata(
                    title = flacMeta.title,
                    artist = flacMeta.artist,
                    album = flacMeta.album,
                    durationSec = durationSec
                )
            }

            // 3. MP3 (con cabecera ID3v2 o sin ella)
            val id3Meta = parseId3v2Tags(buffer, bytesRead)
            title = id3Meta.title
            artist = id3Meta.artist
            album = id3Meta.album
            durationSec = id3Meta.durationSec

            val mp3Dur = parseMp3Duration(buffer, bytesRead, fileSize)
            if (mp3Dur > 0L && durationSec <= 0L) {
                durationSec = mp3Dur
            }

        } catch (_: Throwable) {
            try { stream.reset() } catch (_: Throwable) {}
        }

        return AudioTrackMetadata(
            title = title,
            artist = artist,
            album = album,
            durationSec = durationSec
        )
    }

    /**
     * Detecta la duración en segundos desde el búfer inicial del archivo o stream.
     */
    fun detectDurationSec(stream: BufferedInputStream, fileSize: Long = 0L): Long {
        return extractMetadata(stream, fileSize).durationSec
    }

    /**
     * Extrae la duración precisa en milisegundos de un archivo local.
     */
    fun detectFileDurationMs(file: File): Long {
        if (!file.exists() || !file.isFile || file.length() < 128) return 0L

        try {
            FileInputStream(file).use { fis ->
                BufferedInputStream(fis, 128 * 1024).use { bis ->
                    val sec = detectDurationSec(bis, file.length())
                    if (sec > 0) return sec * 1000L
                }
            }
        } catch (_: Throwable) {}

        // Fallback secundario mediante Java Sound
        try {
            val aff = AudioSystem.getAudioFileFormat(file)
            val props = aff.properties()
            val microSec = (props?.get("duration") as? Number)?.toLong()
                ?: (props?.get("mp3.duration.microseconds") as? Number)?.toLong()
            if (microSec != null && microSec > 0) {
                return microSec / 1000L
            }
            if (aff.frameLength > 0 && aff.format.frameRate > 0) {
                return ((aff.frameLength / aff.format.frameRate) * 1000L).toLong()
            }
        } catch (_: Throwable) {}

        return 0L
    }

    /**
     * Analiza de forma inteligente el nombre del archivo para extraer el Artista y el Título
     * sin mostrar nombres feos ni configurar el nombre del servidor como artista.
     * Soporta patrones como:
     * - "Artista - Canción.mp3"
     * - "01. Artista - Canción.mp3"
     * - "01 - Artista - Canción.mp3"
     * - "01 Canción.mp3"
     * - "Canción [Official Video].mp3"
     */
    fun parseFileNameMetadata(fileName: String, parentFolderName: String? = null): Pair<String, String> {
        var clean = fileName.substringBeforeLast(".")
        if (clean.isBlank()) return "Desconocido" to fileName

        // Eliminar número de pista al inicio: "01. ", "01 - ", "1. ", "01 ", "01_"
        clean = clean.replace(Regex("""^\s*\d{1,3}\s*[\.\-_]\s*"""), "").trim()
        clean = clean.replace(Regex("""^\s*\d{1,3}\s+"""), "").trim()

        // Eliminar sufijos típicos de ripeo o vídeo de YouTube:
        // [Official Video], (Audio Oficial), [320kbps], (Lyric Video), etc.
        clean = clean.replace(
            Regex("""\s*[\(\[]\s*(official\s*(music\s*)?video|video\s*oficial|audio\s*oficial|official\s*audio|lyric\s*video|video\s*lyric|audio|hq|hd|320\s*kbps|128\s*kbps|remastered|remaster|clip\s*officiel)\s*[\)\]]""", RegexOption.IGNORE_CASE),
            ""
        ).trim()

        var artistCandidate: String? = null
        var titleCandidate: String? = null

        val dashParts = clean.split(Regex("""\s+[-—]\s+"""))
        if (dashParts.size >= 3) {
            artistCandidate = dashParts[1].trim()
            titleCandidate = dashParts.drop(2).joinToString(" - ").trim()
        } else if (dashParts.size == 2) {
            val p0 = dashParts[0].trim()
            val p1 = dashParts[1].trim()
            if (p0.matches(Regex("""^\d+$"""))) {
                titleCandidate = p1
            } else {
                artistCandidate = p0
                titleCandidate = p1
            }
        } else {
            val underscoreParts = clean.split(Regex("""\s+_\s+"""))
            if (underscoreParts.size == 2) {
                artistCandidate = underscoreParts[0].trim()
                titleCandidate = underscoreParts[1].trim()
            } else {
                val hyphenParts = clean.split(Regex("""(?<=[a-zA-Z0-9áéíóúÁÉÍÓÚñÑ])-(?=[a-zA-Z0-9áéíóúÁÉÍÓÚñÑ])"""))
                if (hyphenParts.size == 2 && !hyphenParts[0].all { it.isDigit() }) {
                    artistCandidate = hyphenParts[0].trim()
                    titleCandidate = hyphenParts[1].trim()
                }
            }
        }

        var finalTitle = titleCandidate?.ifBlank { null } ?: clean
        var finalArtist = artistCandidate?.ifBlank { null }

        // Si no se encontró artista en el nombre del archivo, comprobar la jerarquía de carpetas
        if (finalArtist == null && !parentFolderName.isNullOrBlank()) {
            val segments = parentFolderName.replace('\\', '/')
                .split('/')
                .map { it.trim() }
                .filter { it.isNotBlank() }

            val genericFolders = setOf(
                "music", "música", "musica", "downloads", "descargas", "boumusic",
                "desktop", "audio", "canciones", "songs", "sound", "root", "media",
                "files", "archivos", "share", "storage", "disk", "", "/"
            )

            val valid = segments.filter { seg ->
                val lower = seg.lowercase()
                lower !in genericFolders &&
                    !lower.startsWith("192.") && !lower.startsWith("10.") && !lower.startsWith("172.") &&
                    !lower.matches(Regex("""^\d+$""")) &&
                    !lower.matches(Regex("""^disc\s*\d+.*""", RegexOption.IGNORE_CASE)) &&
                    !lower.matches(Regex("""^cd\s*\d+.*""", RegexOption.IGNORE_CASE))
            }

            if (valid.size >= 2) {
                // Estructura universal estándar: .../Artista/Álbum/
                finalArtist = valid[valid.size - 2]
            } else if (valid.isNotEmpty()) {
                // Estructura directa: .../Artista/
                finalArtist = valid.last()
            }
        }

        if (finalArtist.isNullOrBlank()) {
            finalArtist = "Desconocido"
        }

        return finalArtist to finalTitle
    }

    private fun parseId3v2Tags(buffer: ByteArray, len: Int): AudioTrackMetadata {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationSec = 0L

        if (len < 10) return AudioTrackMetadata()
        if (buffer[0] != 0x49.toByte() || buffer[1] != 0x44.toByte() || buffer[2] != 0x33.toByte()) {
            return AudioTrackMetadata()
        }

        val majorVersion = buffer[3].toInt() and 0xFF
        val flags = buffer[5].toInt() and 0xFF
        val tagSize = ((buffer[6].toInt() and 0x7F) shl 21) or
                ((buffer[7].toInt() and 0x7F) shl 14) or
                ((buffer[8].toInt() and 0x7F) shl 7) or
                (buffer[9].toInt() and 0x7F)
        val fullId3Size = 10 + tagSize
        val maxSearch = len.coerceAtMost(fullId3Size)

        if (majorVersion == 2) {
            var offset = 10
            while (offset + 6 <= maxSearch) {
                if (buffer[offset] == 0.toByte()) break
                val id = String(buffer, offset, 3, Charsets.US_ASCII)
                val frameSize = ((buffer[offset + 3].toInt() and 0xFF) shl 16) or
                        ((buffer[offset + 4].toInt() and 0xFF) shl 8) or
                        (buffer[offset + 5].toInt() and 0xFF)
                if (frameSize <= 0 || offset + 6 + frameSize > maxSearch) break
                val encoding = buffer[offset + 6].toInt() and 0xFF
                val text = decodeId3String(buffer, offset + 7, frameSize - 1, encoding)
                when (id) {
                    "TT2" -> if (title == null && text.isNotBlank()) title = text
                    "TP1" -> if (artist == null && text.isNotBlank()) artist = text
                    "TAL" -> if (album == null && text.isNotBlank()) album = text
                }
                offset += 6 + frameSize
            }
        } else if (majorVersion == 3 || majorVersion == 4) {
            var offset = 10
            if ((flags and 0x40) != 0 && offset + 4 <= maxSearch) {
                val extSize = if (majorVersion == 4) {
                    ((buffer[offset].toInt() and 0x7F) shl 21) or
                            ((buffer[offset + 1].toInt() and 0x7F) shl 14) or
                            ((buffer[offset + 2].toInt() and 0x7F) shl 7) or
                            (buffer[offset + 3].toInt() and 0x7F)
                } else {
                    ((buffer[offset].toInt() and 0xFF) shl 24) or
                            ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
                            ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
                            (buffer[offset + 3].toInt() and 0xFF)
                }
                offset += (4 + extSize).coerceAtLeast(4)
            }

            while (offset + 10 <= maxSearch) {
                if (buffer[offset] == 0.toByte()) break
                val id = String(buffer, offset, 4, Charsets.US_ASCII)
                if (!id.all { (it in 'A'..'Z') || (it in '0'..'9') }) break

                val frameSize = if (majorVersion == 4) {
                    ((buffer[offset + 4].toInt() and 0x7F) shl 21) or
                            ((buffer[offset + 5].toInt() and 0x7F) shl 14) or
                            ((buffer[offset + 6].toInt() and 0x7F) shl 7) or
                            (buffer[offset + 7].toInt() and 0x7F)
                } else {
                    ((buffer[offset + 4].toInt() and 0xFF) shl 24) or
                            ((buffer[offset + 5].toInt() and 0xFF) shl 16) or
                            ((buffer[offset + 6].toInt() and 0xFF) shl 8) or
                            (buffer[offset + 7].toInt() and 0xFF)
                }

                if (frameSize <= 0 || offset + 10 + frameSize > maxSearch) break

                val encoding = buffer[offset + 10].toInt() and 0xFF
                val text = decodeId3String(buffer, offset + 11, frameSize - 1, encoding)

                when (id) {
                    "TIT2" -> if (title == null && text.isNotBlank()) title = text
                    "TPE1" -> if (artist == null && text.isNotBlank()) artist = text
                    "TALB" -> if (album == null && text.isNotBlank()) album = text
                    "TLEN" -> {
                        val ms = text.toLongOrNull() ?: text.filter { it.isDigit() }.toLongOrNull()
                        if (ms != null && ms > 1000L && durationSec <= 0L) {
                            durationSec = ms / 1000L
                        }
                    }
                }
                offset += 10 + frameSize
            }
        }

        return AudioTrackMetadata(
            title = title,
            artist = artist,
            album = album,
            durationSec = durationSec
        )
    }

    private fun decodeId3String(buffer: ByteArray, offset: Int, length: Int, encoding: Int): String {
        if (length <= 0 || offset < 0 || offset + length > buffer.size) return ""
        return try {
            val s = when (encoding) {
                0 -> String(buffer, offset, length, Charsets.ISO_8859_1)
                1 -> String(buffer, offset, length, Charsets.UTF_16)
                2 -> String(buffer, offset, length, Charsets.UTF_16BE)
                3 -> String(buffer, offset, length, Charsets.UTF_8)
                else -> String(buffer, offset, length, Charsets.UTF_8)
            }
            s.trim('\u0000', ' ', '\t', '\r', '\n')
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseFlacVorbisComment(buffer: ByteArray, len: Int): AudioTrackMetadata {
        var title: String? = null
        var artist: String? = null
        var album: String? = null

        try {
            var blockOffset = 4
            while (blockOffset + 4 <= len) {
                val b0 = buffer[blockOffset].toInt() and 0xFF
                val isLast = (b0 and 0x80) != 0
                val blockType = b0 and 0x7F
                val blockSize = ((buffer[blockOffset + 1].toInt() and 0xFF) shl 16) or
                        ((buffer[blockOffset + 2].toInt() and 0xFF) shl 8) or
                        (buffer[blockOffset + 3].toInt() and 0xFF)

                val dataStart = blockOffset + 4
                if (blockType == 4 && dataStart + blockSize <= len) {
                    var ptr = dataStart
                    val vendorLen = (buffer[ptr].toInt() and 0xFF) or
                            ((buffer[ptr + 1].toInt() and 0xFF) shl 8) or
                            ((buffer[ptr + 2].toInt() and 0xFF) shl 16) or
                            ((buffer[ptr + 3].toInt() and 0xFF) shl 24)
                    ptr += 4 + vendorLen
                    if (ptr + 4 <= dataStart + blockSize) {
                        val userCommentCount = (buffer[ptr].toInt() and 0xFF) or
                                ((buffer[ptr + 1].toInt() and 0xFF) shl 8) or
                                ((buffer[ptr + 2].toInt() and 0xFF) shl 16) or
                                ((buffer[ptr + 3].toInt() and 0xFF) shl 24)
                        ptr += 4
                        for (c in 0 until userCommentCount) {
                            if (ptr + 4 > dataStart + blockSize) break
                            val cLen = (buffer[ptr].toInt() and 0xFF) or
                                    ((buffer[ptr + 1].toInt() and 0xFF) shl 8) or
                                    ((buffer[ptr + 2].toInt() and 0xFF) shl 16) or
                                    ((buffer[ptr + 3].toInt() and 0xFF) shl 24)
                            ptr += 4
                            if (cLen > 0 && ptr + cLen <= dataStart + blockSize) {
                                val commentStr = String(buffer, ptr, cLen, Charsets.UTF_8)
                                val eqIdx = commentStr.indexOf('=')
                                if (eqIdx > 0) {
                                    val key = commentStr.substring(0, eqIdx).trim().uppercase()
                                    val value = commentStr.substring(eqIdx + 1).trim()
                                    when (key) {
                                        "TITLE" -> if (title == null) title = value
                                        "ARTIST" -> if (artist == null) artist = value
                                        "ALBUM" -> if (album == null) album = value
                                    }
                                }
                            }
                            ptr += cLen
                        }
                    }
                }

                blockOffset = dataStart + blockSize
                if (isLast) break
            }
        } catch (_: Exception) {}

        return AudioTrackMetadata(title = title, artist = artist, album = album)
    }

    private fun parseMp3Duration(buffer: ByteArray, len: Int, fileSize: Long): Long {
        var offset = 0

        // Parsear cabecera ID3v2 si está presente ("ID3")
        if (len >= 10 && buffer[0] == 0x49.toByte() && buffer[1] == 0x44.toByte() && buffer[2] == 0x33.toByte()) {
            val tagSize = ((buffer[6].toInt() and 0x7F) shl 21) or
                    ((buffer[7].toInt() and 0x7F) shl 14) or
                    ((buffer[8].toInt() and 0x7F) shl 7) or
                    (buffer[9].toInt() and 0x7F)
            val fullId3Size = 10 + tagSize

            // Buscar si existe el frame TLEN (Length in milliseconds)
            val maxSearch = len.coerceAtMost(fullId3Size.coerceAtMost(16384))
            for (i in 10 until maxSearch - 10) {
                if (buffer[i] == 'T'.code.toByte() && buffer[i + 1] == 'L'.code.toByte() &&
                    buffer[i + 2] == 'E'.code.toByte() && buffer[i + 3] == 'N'.code.toByte()
                ) {
                    val frameSize = ((buffer[i + 4].toInt() and 0xFF) shl 24) or
                            ((buffer[i + 5].toInt() and 0xFF) shl 16) or
                            ((buffer[i + 6].toInt() and 0xFF) shl 8) or
                            (buffer[i + 7].toInt() and 0xFF)
                    if (frameSize in 1..20 && i + 10 + frameSize <= len) {
                        val text = String(buffer, i + 11, (frameSize - 1).coerceAtLeast(1), Charsets.ISO_8859_1).trim()
                        val ms = text.toLongOrNull() ?: text.filter { it.isDigit() }.toLongOrNull()
                        if (ms != null && ms > 1000L) {
                            return ms / 1000L
                        }
                    }
                }
            }
            offset = fullId3Size
        }

        // Buscar primer frame de audio MPEG (Sync 0xFF con 0xE0)
        val searchLimit = (offset + 65536).coerceAtMost(len - 4)
        for (i in offset until searchLimit) {
            val b0 = buffer[i].toInt() and 0xFF
            val b1 = buffer[i + 1].toInt() and 0xFF
            if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
                val b2 = buffer[i + 2].toInt() and 0xFF
                val b3 = buffer[i + 3].toInt() and 0xFF

                val versionBits = (b1 shr 3) and 0x03
                val layerBits = (b1 shr 1) and 0x03
                val brIdx = (b2 shr 4) and 0x0F
                val srIdx = (b2 shr 2) and 0x03
                val channelMode = (b3 shr 6) and 0x03

                if (brIdx == 0 || brIdx == 15 || srIdx == 3) continue

                var bitrate = 0
                if (versionBits == 3) { // MPEG-1
                    if (layerBits == 1) bitrate = BITRATES_MPEG1_L3[brIdx]
                } else if (versionBits == 2 || versionBits == 0) { // MPEG-2 o MPEG-2.5
                    if (layerBits == 1) bitrate = BITRATES_MPEG2_L3[brIdx]
                }

                if (bitrate > 0) {
                    val sampleRate = when (versionBits) {
                        3 -> SAMPLE_RATES_MPEG1[srIdx]
                        2 -> SAMPLE_RATES_MPEG2[srIdx]
                        else -> SAMPLE_RATES_MPEG25[srIdx]
                    }

                    // Verificar presencia de cabecera Xing o Info (VBR)
                    val xingOffset = if (versionBits == 3) {
                        if (channelMode == 3) i + 21 else i + 36
                    } else {
                        if (channelMode == 3) i + 13 else i + 21
                    }

                    if (xingOffset + 12 < len) {
                        val tag = String(buffer, xingOffset, 4, Charsets.US_ASCII)
                        if (tag == "Xing" || tag == "Info") {
                            val flags = ((buffer[xingOffset + 4].toLong() and 0xFF) shl 24) or
                                    ((buffer[xingOffset + 5].toLong() and 0xFF) shl 16) or
                                    ((buffer[xingOffset + 6].toLong() and 0xFF) shl 8) or
                                    (buffer[xingOffset + 7].toLong() and 0xFF)
                            if ((flags and 0x01L) != 0L) {
                                val frames = ((buffer[xingOffset + 8].toLong() and 0xFF) shl 24) or
                                        ((buffer[xingOffset + 9].toLong() and 0xFF) shl 16) or
                                        ((buffer[xingOffset + 10].toLong() and 0xFF) shl 8) or
                                        (buffer[xingOffset + 11].toLong() and 0xFF)
                                if (sampleRate > 0 && frames > 0) {
                                    val samplesPerFrame = if (versionBits == 3) 1152L else 576L
                                    return (frames * samplesPerFrame) / sampleRate
                                }
                            }
                        }
                    }

                    // Fallback CBR mediante tamaño del archivo y tasa de bits
                    if (fileSize > offset) {
                        val audioDataSize = fileSize - offset
                        val sec = (audioDataSize * 8L) / (bitrate * 1000L)
                        if (sec in 1..86400) return sec
                    }
                }
            }
        }
        return 0L
    }

    private fun parseWavDuration(buffer: ByteArray, len: Int, fileSize: Long): Long {
        try {
            var i = 12
            var byteRate = 0
            while (i < len - 8) {
                val chunkId = String(buffer, i, 4, Charsets.US_ASCII)
                val chunkSize = ((buffer[i + 4].toInt() and 0xFF)) or
                        ((buffer[i + 5].toInt() and 0xFF) shl 8) or
                        ((buffer[i + 6].toInt() and 0xFF) shl 16) or
                        ((buffer[i + 7].toInt() and 0xFF) shl 24)

                if (chunkId == "fmt " && chunkSize >= 16 && i + 16 <= len) {
                    byteRate = ((buffer[i + 16].toInt() and 0xFF)) or
                            ((buffer[i + 17].toInt() and 0xFF) shl 8) or
                            ((buffer[i + 18].toInt() and 0xFF) shl 16) or
                            ((buffer[i + 19].toInt() and 0xFF) shl 24)
                } else if (chunkId == "data" && byteRate > 0) {
                    val dataSize = if (chunkSize > 0) chunkSize.toLong() else (fileSize - (i + 8)).coerceAtLeast(0L)
                    return dataSize / byteRate
                }
                i += 8 + chunkSize.coerceAtLeast(0)
            }
        } catch (_: Exception) {}
        return 0L
    }

    private fun parseFlacDuration(buffer: ByteArray, len: Int): Long {
        try {
            val blockType = buffer[4].toInt() and 0x7F
            if (blockType == 0 && len >= 26) {
                val b14 = buffer[14].toLong() and 0xFF
                val b15 = buffer[15].toLong() and 0xFF
                val b16 = buffer[16].toLong() and 0xFF
                val b17 = buffer[17].toLong() and 0xFF

                val sampleRate = (b14 shl 12) or (b15 shl 4) or ((b16 and 0xF0) shr 4)

                val b18 = buffer[18].toLong() and 0xFF
                val b19 = buffer[19].toLong() and 0xFF
                val b20 = buffer[20].toLong() and 0xFF
                val b21 = buffer[21].toLong() and 0xFF

                val totalSamples = ((b17 and 0x0F) shl 32) or (b18 shl 24) or (b19 shl 16) or (b20 shl 8) or b21

                if (sampleRate > 0 && totalSamples > 0) {
                    return totalSamples / sampleRate
                }
            }
        } catch (_: Exception) {}
        return 0L
    }
}
