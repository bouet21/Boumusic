package com.boumusic.desktop.smb

import com.boumusic.desktop.model.FtpConfig
import com.boumusic.desktop.model.FtpItem
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class SmbManager {

    private val clientConfig = SmbConfig.builder()
        .withTimeout(15, TimeUnit.SECONDS)
        .withSoTimeout(25, TimeUnit.SECONDS)
        .build()

    private val smbClient = SMBClient(clientConfig)

    private fun getAuthContext(config: FtpConfig): AuthenticationContext {
        val user = config.username.trim()
        val pass = config.password
        return if (user.isBlank() || user.equals("anonymous", ignoreCase = true) || user.equals("guest", ignoreCase = true)) {
            AuthenticationContext.anonymous()
        } else {
            val domain = when {
                user.contains("\\") -> user.substringBefore("\\")
                user.contains("/") -> user.substringBefore("/")
                else -> ""
            }
            val actualUser = when {
                user.contains("\\") -> user.substringAfter("\\")
                user.contains("/") -> user.substringAfter("/")
                else -> user
            }
            AuthenticationContext(actualUser, pass.toCharArray(), domain)
        }
    }

    suspend fun testConnection(config: FtpConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var session: Session? = null
        var share: DiskShare? = null
        try {
            connection = smbClient.connect(config.host, config.port)
            session = connection.authenticate(getAuthContext(config))
            val shareName = config.cleanSmbShare.ifBlank { "Musica" }
            share = session.connectShare(shareName) as DiskShare
            val isConnected = share.isConnected
            Result.success(isConnected)
        } catch (t: Throwable) {
            val msg = when {
                t.message?.contains("STATUS_BAD_NETWORK_NAME", ignoreCase = true) == true ->
                    "El recurso compartido '${config.cleanSmbShare.ifBlank { "Musica" }}' no existe en el servidor."
                t.message?.contains("STATUS_LOGON_FAILURE", ignoreCase = true) == true ->
                    "Usuario o contraseña incorrectos en SMB."
                else -> t.message ?: t.javaClass.simpleName
            }
            Result.failure(Exception(msg, t))
        } finally {
            try { share?.close() } catch (_: Throwable) {}
            try { session?.close() } catch (_: Throwable) {}
            try { connection?.close() } catch (_: Throwable) {}
        }
    }

    suspend fun listDirectory(config: FtpConfig, subPath: String): Result<List<FtpItem>> = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var session: Session? = null
        var share: DiskShare? = null
        try {
            connection = smbClient.connect(config.host, config.port)
            session = connection.authenticate(getAuthContext(config))
            val shareName = config.cleanSmbShare.ifBlank { "Musica" }
            share = session.connectShare(shareName) as DiskShare

            val normalizedPath = subPath.trim().removePrefix("/").removePrefix("\\").replace("/", "\\")
            val items = mutableListOf<FtpItem>()

            val fileInfoList = share.list(normalizedPath)
            for (fileInfo in fileInfoList) {
                val name = fileInfo.fileName
                if (name == "." || name == "..") continue

                val isDir = EnumWithValue.EnumUtils.isSet(
                    fileInfo.fileAttributes,
                    FileAttributes.FILE_ATTRIBUTE_DIRECTORY
                )

                val itemPath = if (normalizedPath.isEmpty()) name else "$normalizedPath\\$name"
                val webPath = "/" + itemPath.replace("\\", "/")

                items.add(
                    FtpItem(
                        name = name,
                        path = webPath,
                        isDirectory = isDir,
                        size = fileInfo.endOfFile
                    )
                )
            }

            items.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            Result.success(items)
        } catch (t: Throwable) {
            val msg = when {
                t.message?.contains("STATUS_BAD_NETWORK_NAME", ignoreCase = true) == true ->
                    "El recurso compartido '${config.cleanSmbShare.ifBlank { "Musica" }}' no existe en el servidor."
                t.message?.contains("STATUS_LOGON_FAILURE", ignoreCase = true) == true ->
                    "Usuario o contraseña incorrectos en SMB."
                else -> t.message ?: t.javaClass.simpleName
            }
            Result.failure(Exception(msg, t))
        } finally {
            try { share?.close() } catch (_: Throwable) {}
            try { session?.close() } catch (_: Throwable) {}
            try { connection?.close() } catch (_: Throwable) {}
        }
    }

    fun openStream(config: FtpConfig, remotePath: String): InputStream {
        val connection = smbClient.connect(config.host, config.port)
        val session = connection.authenticate(getAuthContext(config))
        val shareName = config.cleanSmbShare.ifBlank { "Musica" }
        val share = session.connectShare(shareName) as DiskShare
        val normalizedPath = remotePath.trim().removePrefix("/").removePrefix("\\").replace("/", "\\")

        val file = share.openFile(
            normalizedPath,
            setOf(AccessMask.FILE_READ_DATA),
            null,
            setOf(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            null
        )

        val smbInputStream = file.inputStream

        return object : InputStream() {
            override fun read(): Int = smbInputStream.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = smbInputStream.read(b, off, len)
            override fun available(): Int = smbInputStream.available()
            override fun close() {
                try { smbInputStream.close() } catch (ignored: Exception) {}
                try { file.close() } catch (ignored: Exception) {}
                try { share.close() } catch (ignored: Exception) {}
                try { session.close() } catch (ignored: Exception) {}
                try { connection.close() } catch (ignored: Exception) {}
            }
        }
    }

    suspend fun downloadFile(
        config: FtpConfig,
        remotePath: String,
        destFile: File,
        onProgress: (Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            destFile.parentFile?.mkdirs()
            inputStream = openStream(config, remotePath)
            outputStream = FileOutputStream(destFile)

            val buffer = ByteArray(32 * 1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            try { outputStream?.close() } catch (ignored: Exception) {}
            try { inputStream?.close() } catch (ignored: Exception) {}
        }
    }

    suspend fun listAllAudioRecursive(config: FtpConfig, maxItems: Int = 10000, maxDepth: Int = 6): Result<List<FtpItem>> = withContext(Dispatchers.IO) {
        var connection: Connection? = null
        var session: Session? = null
        var share: DiskShare? = null
        try {
            connection = smbClient.connect(config.host, config.port)
            session = connection.authenticate(getAuthContext(config))
            val shareName = config.cleanSmbShare.ifBlank { "Musica" }
            share = session.connectShare(shareName) as DiskShare

            val items = mutableListOf<FtpItem>()
            val queue = ArrayDeque<Pair<String, Int>>()
            val visited = mutableSetOf<String>()
            queue.add("" to 0)

            while (queue.isNotEmpty() && items.size < maxItems) {
                val (currentPath, depth) = queue.removeFirst()
                if (!visited.add(currentPath)) continue

                val fileInfoList = try {
                    share.list(currentPath)
                } catch (_: Exception) {
                    emptyList()
                }

                for (fileInfo in fileInfoList) {
                    val name = fileInfo.fileName
                    if (name == "." || name == "..") continue

                    val isDir = EnumWithValue.EnumUtils.isSet(
                        fileInfo.fileAttributes,
                        FileAttributes.FILE_ATTRIBUTE_DIRECTORY
                    )

                    val itemSubPath = if (currentPath.isEmpty()) name else "$currentPath\\$name"
                    val webPath = "/" + itemSubPath.replace("\\", "/")

                    if (isDir) {
                        if (depth < maxDepth && !name.startsWith(".")) {
                            queue.add(itemSubPath to depth + 1)
                        }
                    } else {
                        val item = FtpItem(name = name, path = webPath, isDirectory = false, size = fileInfo.endOfFile)
                        if (item.isAudio) {
                            items.add(item)
                        }
                    }
                }
            }
            Result.success(items)
        } catch (t: Throwable) {
            val msg = when {
                t.message?.contains("STATUS_BAD_NETWORK_NAME", ignoreCase = true) == true ->
                    "El recurso compartido '${config.cleanSmbShare.ifBlank { "Musica" }}' no existe en el servidor."
                t.message?.contains("STATUS_LOGON_FAILURE", ignoreCase = true) == true ->
                    "Usuario o contraseña incorrectos en SMB."
                else -> t.message ?: t.javaClass.simpleName
            }
            Result.failure(Exception(msg, t))
        } finally {
            try { share?.close() } catch (_: Throwable) {}
            try { session?.close() } catch (_: Throwable) {}
            try { connection?.close() } catch (_: Throwable) {}
        }
    }
}
