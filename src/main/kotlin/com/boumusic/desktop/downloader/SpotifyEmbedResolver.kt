package com.boumusic.desktop.downloader

import com.boumusic.desktop.audio.AudioMetadataHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object SpotifyEmbedResolver {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val SPOTIFY_LINK_REGEX = Pattern.compile(
        "(?:https?://)?open\\.spotify\\.com/(?:intl-[^/\\s]+/)?(track|album|playlist|artist)/([A-Za-z0-9]+)",
        Pattern.CASE_INSENSITIVE
    )

    private val SPOTIFY_URI_REGEX = Pattern.compile(
        "spotify:(track|album|playlist|artist):([A-Za-z0-9]+)",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Resuelve un término de búsqueda o enlace de Spotify y devuelve la lista de canciones encontradas.
     */
    suspend fun resolve(query: String, binDir: java.io.File? = null): List<DownloadTrackItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val matcher = SPOTIFY_LINK_REGEX.matcher(trimmed)
        if (matcher.find()) {
            val type = matcher.group(1).lowercase()
            val id = matcher.group(2)
            return@withContext resolveSpotifyEmbed(type, id, trimmed)
        }

        val uriMatcher = SPOTIFY_URI_REGEX.matcher(trimmed)
        if (uriMatcher.find()) {
            val type = uriMatcher.group(1).lowercase()
            val id = uriMatcher.group(2)
            return@withContext resolveSpotifyEmbed(type, id, "https://open.spotify.com/$type/$id")
        }

        // Si es búsqueda por texto plano, intentamos buscar mediante yt-dlp o Spotify oEmbed
        resolveTextSearch(trimmed, binDir)
    }

    private fun resolveSpotifyEmbed(type: String, id: String, originalUrl: String): List<DownloadTrackItem> {
        val embedUrl = "https://open.spotify.com/embed/$type/$id"
        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        try {
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""
            response.close()

            val scriptTag = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
            if (html.contains(scriptTag)) {
                val jsonStr = html.substringAfter(scriptTag).substringBefore("</script>")
                val root = JSONObject(jsonStr)
                val entity = root.optJSONObject("props")
                    ?.optJSONObject("pageProps")
                    ?.optJSONObject("state")
                    ?.optJSONObject("data")
                    ?.optJSONObject("entity")

                if (entity != null) {
                    val entityName = entity.optString("name", entity.optString("title", ""))
                    val entityCover = entity.optJSONObject("visualIdentity")
                        ?.optJSONObject("image")
                        ?.optJSONArray("sources")
                        ?.optJSONObject(0)?.optString("url")
                        ?: entity.optString("coverArt", "")

                    val trackList = entity.optJSONArray("trackList")
                    if (trackList != null && trackList.length() > 0) {
                        val list = mutableListOf<DownloadTrackItem>()
                        for (i in 0 until trackList.length()) {
                            val item = trackList.getJSONObject(i)
                            val tTitle = item.optString("title", "")
                            val tArtist = item.optString("subtitle", "")
                            val tDur = item.optLong("duration", 0L)
                            val tUri = item.optString("uri", "")
                            val tId = if (tUri.contains(":")) tUri.substringAfterLast(":") else "$id-$i"

                            if (tTitle.isNotBlank()) {
                                list.add(
                                    DownloadTrackItem(
                                        id = tId,
                                        title = tTitle,
                                        artist = if (tArtist.isNotBlank()) tArtist else entityName,
                                        album = entityName,
                                        durationMs = tDur,
                                        coverUrl = entityCover.ifBlank { null },
                                        spotifyUrl = if (tUri.isNotBlank()) "https://open.spotify.com/track/$tId" else originalUrl
                                    )
                                )
                            }
                        }
                        if (list.isNotEmpty()) return list
                    }
                }
            }

            // Fallback para pista única mediante oEmbed
            val oembedUrl = "https://open.spotify.com/oembed?url=" + URLEncoder.encode(originalUrl, "UTF-8")
            val oembedReq = Request.Builder().url(oembedUrl).build()
            val oembedResp = client.newCall(oembedReq).execute()
            if (oembedResp.isSuccessful) {
                val oJson = JSONObject(oembedResp.body?.string() ?: "{}")
                oembedResp.close()
                val rawTitle = oJson.optString("title", "Desconocido")
                val cover = oJson.optString("thumbnail_url", "")
                val (artist, title) = AudioMetadataHelper.parseFileNameMetadata(rawTitle)
                return listOf(
                    DownloadTrackItem(
                        id = id,
                        title = title,
                        artist = artist,
                        album = "",
                        durationMs = 0L,
                        coverUrl = cover.ifBlank { null },
                        spotifyUrl = originalUrl
                    )
                )
            }
            oembedResp.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return emptyList()
    }

    private fun resolveTextSearch(query: String, binDir: java.io.File?): List<DownloadTrackItem> {
        val ytdlp = java.io.File(binDir ?: java.io.File(System.getProperty("user.home"), ".boumusic/bin"), if (System.getProperty("os.name").lowercase().contains("windows")) "yt-dlp.exe" else "yt-dlp")
        if (ytdlp.exists()) {
            try {
                val pb = ProcessBuilder(
                    ytdlp.absolutePath,
                    "--dump-json",
                    "--flat-playlist",
                    "ytsearch6:$query"
                )
                pb.redirectErrorStream(true)
                val proc = pb.start()
                val lines = proc.inputStream.bufferedReader().readLines()
                proc.waitFor(8, TimeUnit.SECONDS)

                val results = mutableListOf<DownloadTrackItem>()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("{")) continue
                    try {
                        val obj = JSONObject(trimmed)
                        val title = obj.optString("title", "")
                        val durSec = obj.optLong("duration", 0L)
                        val url = obj.optString("webpage_url", obj.optString("url", ""))
                        val id = obj.optString("id", url.hashCode().toString())

                        var thumb: String? = null
                        val thumbs = obj.optJSONArray("thumbnails")
                        if (thumbs != null && thumbs.length() > 0) {
                            thumb = thumbs.getJSONObject(thumbs.length() - 1).optString("url", null)
                        }

                        val (parsedArtist, parsedTitle) = AudioMetadataHelper.parseFileNameMetadata(title)
                        results.add(
                            DownloadTrackItem(
                                id = id,
                                title = parsedTitle,
                                artist = parsedArtist,
                                album = "YouTube Music",
                                durationMs = durSec * 1000L,
                                coverUrl = thumb,
                                spotifyUrl = url
                            )
                        )
                    } catch (_: Exception) {}
                }
                if (results.isNotEmpty()) return results
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return emptyList()
    }
}
