package com.boumusic.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boumusic.desktop.ui.theme.DarkBackground
import com.boumusic.desktop.ui.theme.DarkCardBorder
import com.boumusic.desktop.ui.theme.NeonCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val imageCache = ConcurrentHashMap<String, ImageBitmap>()

private val imageHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

@Composable
fun AsyncCoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    var bitmap by remember(url) { mutableStateOf(url?.let { imageCache[it] }) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        if (imageCache.containsKey(url)) {
            bitmap = imageCache[url]
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val resp = imageHttpClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val stream: InputStream? = resp.body?.byteStream()
                    if (stream != null) {
                        val bmp = loadImageBitmap(stream)
                        imageCache[url] = bmp
                        bitmap = bmp
                    }
                }
                resp.close()
            } catch (_: Throwable) {}
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "Carátula",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = NeonCyan.copy(alpha = 0.6f),
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}
