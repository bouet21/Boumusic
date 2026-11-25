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
    suspend fun resolve(query: String, binDir: java.io.File? = null, limit: Int = 30): List<DownloadTrackItem> = withContext(Dispatchers.IO) {
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

        // Búsqueda por texto: Deezer API -> iTunes API -> YouTube Web -> yt-dlp local
        resolveTextSearch(trimmed, binDir, limit)
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

    private fun resolveTextSearch(query: String, binDir: java.io.File?, limit: Int = 30): List<DownloadTrackItem> {
        // 1. Intentar Deezer API (Instantáneo, metadatos y carátulas de alta calidad)
        try {
            val deezerResults = resolveViaDeezer(query, limit)
            if (deezerResults.isNotEmpty()) return deezerResults
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Intentar iTunes API de Apple (Oficial, sin claves, portadas 600x600)
        try {
            val itunesResults = resolveViaItunes(query, limit)
            if (itunesResults.isNotEmpty()) return itunesResults
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Intentar YouTube Web Search directo vía HTTP (igual que en Android)
        try {
            val ytWebResults = resolveViaYouTubeWeb(query, limit)
            if (ytWebResults.isNotEmpty()) return ytWebResults
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Fallback a yt-dlp local si existe
        val ytdlp = java.io.File(binDir ?: java.io.File(System.getProperty("user.home"), ".boumusic/bin"), if (System.getProperty("os.name").lowercase().contains("windows")) "yt-dlp.exe" else "yt-dlp")
        if (ytdlp.exists()) {
            try {
                val pb = ProcessBuilder(
                    ytdlp.absolutePath,
                    "--dump-json",
                    "--flat-playlist",
                    "ytsearch$limit:$query"
                )
                pb.redirectErrorStream(true)
                val proc = pb.start()
                val lines = proc.inputStream.bufferedReader(Charsets.UTF_8).readLines()
                proc.waitFor(20, TimeUnit.SECONDS)

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

    private fun resolveViaDeezer(query: String, limit: Int): List<DownloadTrackItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://api.deezer.com/search?q=$encoded&limit=$limit"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            resp.close()
            return emptyList()
        }
        val bodyStr = resp.body?.string() ?: "{}"
        resp.close()

        val json = JSONObject(bodyStr)
        val data = json.optJSONArray("data") ?: return emptyList()
        val list = mutableListOf<DownloadTrackItem>()

        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val title = item.optString("title", "")
            val artistObj = item.optJSONObject("artist")
            val artist = artistObj?.optString("name", "Desconocido") ?: "Desconocido"
            val albumObj = item.optJSONObject("album")
            val album = albumObj?.optString("title", "") ?: ""
            val cover = albumObj?.optString("cover_big", albumObj.optString("cover_medium", ""))
            val durationSec = item.optLong("duration", 0L)
            val trackId = item.optLong("id", 0L).toString()
            val link = item.optString("link", "")

            if (title.isNotBlank()) {
                list.add(
                    DownloadTrackItem(
                        id = trackId,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationSec * 1000L,
                        coverUrl = cover?.ifBlank { null },
                        spotifyUrl = link
                    )
                )
            }
        }
        return list
    }

    private fun resolveViaItunes(query: String, limit: Int): List<DownloadTrackItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$encoded&media=music&entity=song&limit=$limit"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            resp.close()
            return emptyList()
        }
        val bodyStr = resp.body?.string() ?: "{}"
        resp.close()

        val json = JSONObject(bodyStr)
        val results = json.optJSONArray("results") ?: return emptyList()
        val list = mutableListOf<DownloadTrackItem>()

        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            val title = item.optString("trackName", "")
            val artist = item.optString("artistName", "Desconocido")
            val album = item.optString("collectionName", "")
            val cover = item.optString("artworkUrl100", "").replace("100x100bb", "600x600bb")
            val durationMs = item.optLong("trackTimeMillis", 0L)
            val trackId = item.optLong("trackId", 0L).toString()
            val trackViewUrl = item.optString("trackViewUrl", "")

            if (title.isNotBlank()) {
                list.add(
                    DownloadTrackItem(
                        id = trackId,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs,
                        coverUrl = cover.ifBlank { null },
                        spotifyUrl = trackViewUrl
                    )
                )
            }
        }
        return list
    }

    private fun resolveViaYouTubeWeb(query: String, limit: Int): List<DownloadTrackItem> {
        val list = mutableListOf<DownloadTrackItem>()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://www.youtube.com/results?search_query=$encoded"
        val req = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
            .build()

        val resp = client.newCall(req).execute()
        val html = resp.body?.string() ?: ""
        resp.close()

        val marker = "ytInitialData = "
        if (html.contains(marker)) {
            val jsonPart = html.substringAfter(marker).substringBefore(";</script>").substringBefore(";\n")
            val root = JSONObject(jsonPart)
            val sections = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (sections != null) {
                for (s in 0 until sections.length()) {
                    val secObj = sections.getJSONObject(s)
                    val itemSection = secObj.optJSONObject("itemSectionRenderer") ?: continue
                    val items = itemSection.optJSONArray("contents") ?: continue

                    for (i in 0 until items.length()) {
                        if (list.size >= limit) break
                        val item = items.getJSONObject(i)
                        val video = item.optJSONObject("videoRenderer") ?: continue

                        val videoId = video.optString("videoId", "")
                        if (videoId.isBlank()) continue

                        val titleRuns = video.optJSONObject("title")?.optJSONArray("runs")
                        val title = titleRuns?.optJSONObject(0)?.optString("text", "") ?: ""

                        val channelRuns = video.optJSONObject("ownerText")?.optJSONArray("runs")
                        val channel = channelRuns?.optJSONObject(0)?.optString("text", "Artista") ?: "Artista"

                        val lengthText = video.optJSONObject("lengthText")?.optString("simpleText", "") ?: ""
                        val durMs = parseLengthTextToMs(lengthText)

                        val thumbs = video.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        val thumb = if (thumbs != null && thumbs.length() > 0) {
                            thumbs.getJSONObject(thumbs.length() - 1).optString("url")
                        } else null

                        val (artist, cleanTitle) = AudioMetadataHelper.parseFileNameMetadata(title)
                        val finalArtist = if (artist.equals("Desconocido", ignoreCase = true)) channel else artist

                        list.add(
                            DownloadTrackItem(
                                id = videoId,
                                title = cleanTitle,
                                artist = finalArtist,
                                album = "YouTube Stream",
                                durationMs = durMs,
                                coverUrl = thumb,
                                spotifyUrl = "https://www.youtube.com/watch?v=$videoId"
                            )
                        )
                    }
                }
            }
        }
        return list
    }

    private fun parseLengthTextToMs(text: String): Long {
        if (text.isBlank()) return 180000L
        val parts = text.split(':')
        return try {
            when (parts.size) {
                2 -> ((parts[0].toLong() * 60) + parts[1].toLong()) * 1000L
                3 -> ((parts[0].toLong() * 3600) + (parts[1].toLong() * 60) + parts[2].toLong()) * 1000L
                else -> 180000L
            }
        } catch (_: Exception) {
            180000L
        }
    }
}
