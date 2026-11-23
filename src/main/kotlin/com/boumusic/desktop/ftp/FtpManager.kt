package com.boumusic.desktop.ftp

import com.boumusic.desktop.model.FtpConfig
import com.boumusic.desktop.model.FtpItem
import com.boumusic.desktop.smb.SmbManager
import com.boumusic.desktop.webdav.WebDavManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class FtpManager(
    val smbManager: SmbManager = SmbManager(),
    val webDavManager: WebDavManager = WebDavManager()
) {

    private fun createClient(config: FtpConfig): FTPClient {
        val client = if (config.isFtps) {
            FTPSClient("TLS", false).apply {
                needClientAuth = false
                wantClientAuth = false
            }
        } else {
            FTPClient()
        }
        client.connectTimeout = 10000
        client.defaultTimeout = 15000
        return client
    }

    suspend fun testConnection(config: FtpConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        if (config.isSmb) return@withContext smbManager.testConnection(config)
        if (config.isWebDav) return@withContext webDavManager.testConnection(config)

        var client: FTPClient? = null
        try {
            client = createClient(config)
            client.connect(config.host, config.port)
            val loginSuccess = client.login(config.username, config.password)
            client.enterLocalPassiveMode()
            client.disconnect()
            Result.success(loginSuccess)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { if (client?.isConnected == true) client.disconnect() } catch (ignored: Exception) {}
        }
    }

    suspend fun listDirectory(config: FtpConfig, path: String): Result<List<FtpItem>> = withContext(Dispatchers.IO) {
        if (config.isSmb) return@withContext smbManager.listDirectory(config, path)
        if (config.isWebDav) return@withContext webDavManager.listDirectory(config, path)

        var client: FTPClient? = null
        try {
            client = createClient(config)
            client.connect(config.host, config.port)
            client.login(config.username, config.password)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            val normalizedPath = if (path.isBlank()) "/" else path
            val files = client.listFiles(normalizedPath)

            val items = files.mapNotNull { f ->
                val name = f.name
                if (name == "." || name == "..") return@mapNotNull null
                val itemPath = if (normalizedPath.endsWith("/")) "$normalizedPath$name" else "$normalizedPath/$name"
                FtpItem(name = name, path = itemPath, isDirectory = f.isDirectory, size = f.size)
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            client.logout()
            client.disconnect()
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { if (client?.isConnected == true) client.disconnect() } catch (ignored: Exception) {}
        }
    }

    fun openStream(config: FtpConfig, remotePath: String): InputStream {
        if (config.isSmb) return smbManager.openStream(config, remotePath)
        if (config.isWebDav) return webDavManager.openStream(config, remotePath)

        val client = createClient(config)
        client.connect(config.host, config.port)
        client.login(config.username, config.password)
        client.enterLocalPassiveMode()
        client.setFileType(FTP.BINARY_FILE_TYPE)

        val ftpStream = client.retrieveFileStream(remotePath)
            ?: throw Exception("No se pudo abrir el archivo remoto: $remotePath")

        return object : InputStream() {
            override fun read(): Int = ftpStream.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = ftpStream.read(b, off, len)
            override fun available(): Int = ftpStream.available()
            override fun close() {
                try { ftpStream.close() } catch (ignored: Exception) {}
                try { client.completePendingCommand() } catch (ignored: Exception) {}
                try { if (client.isConnected) client.disconnect() } catch (ignored: Exception) {}
            }
        }
    }

    suspend fun downloadSong(config: FtpConfig, remotePath: String, destFile: File, onProgress: (Int) -> Unit): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (config.isSmb) {
                val ok = smbManager.downloadFile(config, remotePath, destFile, onProgress)
                return@withContext if (ok) Result.success(true) else Result.failure(Exception("Error SMB"))
            }
            if (config.isWebDav) {
                val ok = webDavManager.downloadFile(config, remotePath, destFile, onProgress)
                return@withContext if (ok) Result.success(true) else Result.failure(Exception("Error WebDAV"))
            }

            destFile.parentFile?.mkdirs()
            val client = createClient(config)
            client.connect(config.host, config.port)
            client.login(config.username, config.password)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            FileOutputStream(destFile).use { out ->
                client.retrieveFile(remotePath, out)
            }
            client.logout()
            client.disconnect()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
