package com.boumusic.desktop.webdav

import com.boumusic.desktop.model.FtpConfig
import com.boumusic.desktop.model.FtpItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.StringReader
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

class WebDavManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun buildBaseUrl(config: FtpConfig): String {
        val scheme = if (config.isFtps || config.port == 443) "https" else "http"
        val portPart = if ((scheme == "https" && config.port == 443) || (scheme == "http" && config.port == 80)) "" else ":${config.port}"
        return "$scheme://${config.host}$portPart"
    }

    private fun addAuth(requestBuilder: Request.Builder, config: FtpConfig) {
        val user = config.username.trim()
        if (user.isNotBlank() && !user.equals("anonymous", ignoreCase = true)) {
            requestBuilder.header("Authorization", Credentials.basic(user, config.password))
        }
    }

    suspend fun testConnection(config: FtpConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = buildBaseUrl(config) + (config.webDavPath.ifBlank { "/" })
            val propfindXml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <D:propfind xmlns:D="DAV:">
                    <D:prop><D:resourcetype/></D:prop>
                </D:propfind>
            """.trimIndent()

            val reqBuilder = Request.Builder()
                .url(url)
                .method("PROPFIND", propfindXml.toRequestBody(null))
                .header("Depth", "0")

            addAuth(reqBuilder, config)
            val response = client.newCall(reqBuilder.build()).execute()
            val ok = response.isSuccessful || response.code == 207
            response.close()
            Result.success(ok)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listDirectory(config: FtpConfig, remotePath: String): Result<List<FtpItem>> = withContext(Dispatchers.IO) {
        try {
            val normalizedPath = if (remotePath.startsWith("/")) remotePath else "/$remotePath"
            val url = buildBaseUrl(config) + normalizedPath

            val propfindXml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <D:propfind xmlns:D="DAV:">
                    <D:prop>
                        <D:resourcetype/>
                        <D:getcontentlength/>
                        <D:displayname/>
                    </D:prop>
                </D:propfind>
            """.trimIndent()

            val reqBuilder = Request.Builder()
                .url(url)
                .method("PROPFIND", propfindXml.toRequestBody(null))
                .header("Depth", "1")

            addAuth(reqBuilder, config)
            val response = client.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 207) {
                return@withContext Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val xmlBody = response.body?.string() ?: ""
            response.close()

            val items = parseWebDavXml(xmlBody, normalizedPath)
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseWebDavXml(xml: String, currentPath: String): List<FtpItem> {
        val list = mutableListOf<FtpItem>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xml)))
            val responseNodes = doc.getElementsByTagNameNS("*", "response")

            for (i in 0 until responseNodes.length) {
                val resp = responseNodes.item(i) as Element
                val hrefNode = resp.getElementsByTagNameNS("*", "href").item(0) ?: continue
                val rawHref = hrefNode.textContent.trim()
                val decodedHref = URLDecoder.decode(rawHref, "UTF-8").trimEnd('/')

                val normalizedCurrent = currentPath.trimEnd('/')
                if (decodedHref.equals(normalizedCurrent, ignoreCase = true) || decodedHref.isEmpty()) {
                    continue
                }

                val name = decodedHref.substringAfterLast("/")
                if (name.isBlank()) continue

                val isDir = resp.getElementsByTagNameNS("*", "collection").length > 0
                val lengthNode = resp.getElementsByTagNameNS("*", "getcontentlength").item(0)
                val size = lengthNode?.textContent?.toLongOrNull() ?: 0L

                list.add(FtpItem(name = name, path = decodedHref, isDirectory = isDir, size = size))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun openStream(config: FtpConfig, remotePath: String): InputStream {
        val normalizedPath = if (remotePath.startsWith("/")) remotePath else "/$remotePath"
        val url = buildBaseUrl(config) + normalizedPath
        val reqBuilder = Request.Builder().url(url).get()
        addAuth(reqBuilder, config)
        val response = client.newCall(reqBuilder.build()).execute()
        return response.body?.byteStream() ?: throw Exception("Empty body")
    }

    suspend fun downloadFile(config: FtpConfig, remotePath: String, destFile: File, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        var input: InputStream? = null
        var output: FileOutputStream? = null
        try {
            destFile.parentFile?.mkdirs()
            input = openStream(config, remotePath)
            output = FileOutputStream(destFile)
            val buffer = ByteArray(32 * 1024)
            var bytes: Int
            while (input.read(buffer).also { bytes = it } != -1) {
                output.write(buffer, 0, bytes)
            }
            output.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { output?.close() } catch (ignored: Exception) {}
            try { input?.close() } catch (ignored: Exception) {}
        }
    }

    suspend fun listAllAudioRecursive(config: FtpConfig, maxItems: Int = 10000, maxDepth: Int = 6): Result<List<FtpItem>> = withContext(Dispatchers.IO) {
        val items = mutableListOf<FtpItem>()
        val queue = ArrayDeque<Pair<String, Int>>()
        val visited = mutableSetOf<String>()
        val startPath = config.webDavPath.ifBlank { "/" }
        queue.add(startPath to 0)

        while (queue.isNotEmpty() && items.size < maxItems) {
            val (currentPath, depth) = queue.removeFirst()
            if (!visited.add(currentPath)) continue

            val res = listDirectory(config, currentPath)
            if (res.isSuccess) {
                for (item in res.getOrThrow()) {
                    if (item.isDirectory) {
                        if (depth < maxDepth && !item.name.startsWith(".")) {
                            queue.add(item.path to depth + 1)
                        }
                    } else if (item.isAudio) {
                        items.add(item)
                    }
                }
            }
        }
        Result.success(items)
    }
}
