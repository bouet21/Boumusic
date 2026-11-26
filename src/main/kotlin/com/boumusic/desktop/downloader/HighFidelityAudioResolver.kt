package com.boumusic.desktop.downloader

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

data class DirectStreamResult(
    val streamUrl: String,
    val fallbackUrl: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String,
    val extension: String = "m4a"
)

object HighFidelityAudioResolver {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val desKey = SecretKeySpec("38346591".toByteArray(Charsets.UTF_8), "DES")

    fun resolveDirectStream(query: String): DirectStreamResult? {
        val clean = query.trim()
        val parts = if (clean.contains(" - ")) clean.split(" - ", limit = 2) else listOf(clean, "")
        val artist = if (parts.size > 1) parts[0].trim() else ""
        val title = if (parts.size > 1) parts[1].trim() else parts[0].trim()
        return resolveDirectStream(artist, title)
    }

    /**
     * Resuelve un stream de audio directo de alta fidelidad (320kbps / 160kbps) sin requerir conversiones.
     */
    fun resolveDirectStream(artist: String, title: String): DirectStreamResult? {
        val cleanArtist = artist.trim().replace(Regex("[^a-zA-Z0-9 ]"), " ")
        val cleanTitle = title.trim().replace(Regex("[^a-zA-Z0-9 ]"), " ")
        val query = "$cleanTitle $cleanArtist".trim()
        if (query.isBlank()) return null

        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.jiosaavn.com/api.php?__call=autocomplete.get&_format=json&_marker=0&cc=in&includeMetaTags=1&query=$encoded"
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful || resp.body == null) {
                resp.close()
                return null
            }
            val body = resp.body!!.string()
            resp.close()

            val json = JSONObject(body)
            val songsData = json.optJSONObject("songs")?.optJSONArray("data")
            if (songsData == null || songsData.length() == 0) return null

            val firstSong = songsData.getJSONObject(0)
            val songPid = firstSong.optString("id", "")
            if (songPid.isBlank()) return null

            // 2. Detalles de la canción y URL cifrada
            val detailsUrl = "https://www.jiosaavn.com/api.php?__call=song.getDetails&cc=in&_marker=0%3F_marker%3D0&_format=json&pids=$songPid"
            val detailsReq = Request.Builder()
                .url(detailsUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val detailsResp = client.newCall(detailsReq).execute()
            if (!detailsResp.isSuccessful || detailsResp.body == null) {
                detailsResp.close()
                return null
            }
            val detailsBody = detailsResp.body!!.string()
            detailsResp.close()

            val detailsJson = JSONObject(detailsBody)
            val songObj = detailsJson.optJSONObject(songPid) ?: return null
            val encMediaUrl = songObj.optString("encrypted_media_url", "")
            if (encMediaUrl.isBlank()) return null

            // 3. Descifrado DES ECB
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, desKey)
            val decodedBytes = Base64.getDecoder().decode(encMediaUrl)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            val rawUrl = String(decryptedBytes, Charsets.UTF_8).trim()

            // 4. Mejorar calidad a 320kbps
            val highQualityUrl = when {
                rawUrl.contains("_96.") -> rawUrl.replace("_96.", "_320.")
                rawUrl.contains("_160.") -> rawUrl.replace("_160.", "_320.")
                else -> rawUrl
            }

            val songTitle = songObj.optString("song", title)
            val songArtist = songObj.optString("primary_artists", artist)
            val songAlbum = songObj.optString("album", "")
            val songImage = songObj.optString("image", "").replace("150x150", "500x500")

            return DirectStreamResult(
                streamUrl = highQualityUrl,
                fallbackUrl = rawUrl,
                title = songTitle,
                artist = songArtist,
                album = songAlbum,
                coverUrl = songImage,
                extension = "m4a"
            )
        } catch (_: Exception) {
            return null
        }
    }
}
