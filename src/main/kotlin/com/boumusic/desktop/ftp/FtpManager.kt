package com.boumusic.desktop.ftp

import com.boumusic.desktop.model.FtpConfig
import com.boumusic.desktop.model.FtpItem
import com.boumusic.desktop.smb.SmbManager
import com.boumusic.desktop.webdav.WebDavManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class FtpManager(
    val smbManager: SmbManager = SmbManager(),
    val webDavManager: WebDavManager = WebDavManager()
) {

    fun sanitizeHostAndPort(config: FtpConfig): Pair<String, Int> {
        var host = config.host.trim()
            .removePrefix("ftp://")
            .removePrefix("ftps://")
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("smb://")
            .removePrefix("\\\\")
            .trimEnd('/')

        var port = config.port
        if (host.contains(":")) {
            val colonIdx = host.lastIndexOf(':')
            val portCandidate = host.substring(colonIdx + 1).toIntOrNull()
            if (portCandidate != null) {
                port = portCandidate
                host = host.substring(0, colonIdx)
            }
        }
        return host to port
    }

    private fun createClient(config: FtpConfig): FTPClient {
        val client = if (config.isFtps) {
            val trustAllManager = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
                }
            )
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllManager, SecureRandom())
            FTPSClient(sslContext).apply {
                needClientAuth = false
                wantClientAuth = false
            }
        } else {
            FTPClient()
        }

        client.connectTimeout = 8000
        client.defaultTimeout = 12000
        client.setDataTimeout(java.time.Duration.ofMillis(12000))
        client.controlEncoding = "UTF-8"
        client.autodetectUTF8 = true
        try {
            client.setPassiveNatWorkaroundStrategy { client.remoteAddress?.hostAddress ?: it }
        } catch (_: Throwable) {}
        return client
    }

    private fun configurePostLogin(client: FTPClient) {
        if (client is FTPSClient) {
            try {
                client.execPBSZ(0)
                client.execPROT("P")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun testConnection(config: FtpConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        if (config.isSmb) return@withContext smbManager.testConnection(config)
        if (config.isWebDav) return@withContext webDavManager.testConnection(config)

        val (host, port) = sanitizeHostAndPort(config)
        if (host.isBlank()) {
            return@withContext Result.failure(Exception("La dirección IP o host no puede estar vacía"))
        }

        var client: FTPClient? = null
        try {
            client = createClient(config)
            client.connect(host, port)

            val reply = client.replyCode
            if (!FTPReply.isPositiveCompletion(reply)) {
                return@withContext Result.failure(Exception("El servidor FTP rechazó la conexión (Código: $reply)"))
            }

            val user = config.username.ifBlank { "anonymous" }
            val pass = config.password
            val loginSuccess = client.login(user, pass)
            if (!loginSuccess) {
                return@withContext Result.failure(Exception("Autenticación fallida: usuario o contraseña incorrectos en $host:$port"))
            }

            configurePostLogin(client)
            client.enterLocalPassiveMode()
            try { client.logout() } catch (ignored: Exception) {}
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { if (client?.isConnected == true) client.disconnect() } catch (ignored: Exception) {}
        }
    }

    suspend fun listDirectory(config: FtpConfig, path: String): Result<List<FtpItem>> = withContext(Dispatchers.IO) {
        if (config.isSmb) return@withContext smbManager.listDirectory(config, path)
        if (config.isWebDav) return@withContext webDavManager.listDirectory(config, path)

        val (host, port) = sanitizeHostAndPort(config)
        if (host.isBlank()) {
            return@withContext Result.failure(Exception("La dirección IP o host no puede estar vacía"))
        }

        var client: FTPClient? = null
        try {
            client = createClient(config)
            client.connect(host, port)

            val reply = client.replyCode
            if (!FTPReply.isPositiveCompletion(reply)) {
                return@withContext Result.failure(Exception("El servidor FTP rechazó la conexión (Código: $reply)"))
            }

            val user = config.username.ifBlank { "anonymous" }
            val pass = config.password
            val loginSuccess = client.login(user, pass)
            if (!loginSuccess) {
                return@withContext Result.failure(Exception("Autenticación fallida: usuario o contraseña incorrectos en $host:$port"))
            }

            configurePostLogin(client)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            // Intentar listar con passive mode; si falla o viene vacío en root, probar active mode
            var files = listFilesSafe(client, path)
            if (files == null) {
                client.enterLocalActiveMode()
                files = listFilesSafe(client, path)
            }

            val fileList = files ?: emptyArray()
            val items = fileList.mapNotNull { f ->
                val name = f.name
                if (name == "." || name == "..") return@mapNotNull null
                val normalizedPath = if (path.isBlank() || path == "/") "" else if (path.endsWith("/")) path.dropLast(1) else path
                val itemPath = if (normalizedPath.isEmpty()) "/$name" else "$normalizedPath/$name"
                FtpItem(name = name, path = itemPath, isDirectory = f.isDirectory, size = f.size)
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            try { client.logout() } catch (ignored: Exception) {}
            Result.success(items)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            try { if (client?.isConnected == true) client.disconnect() } catch (ignored: Exception) {}
        }
    }

    private fun listFilesSafe(client: FTPClient, path: String): Array<FTPFile>? {
        return try {
            val target = path.trim()
            val result = if (target.isEmpty() || target == "/") {
                client.listFiles() ?: client.listFiles("") ?: client.listFiles("/")
            } else {
                client.listFiles(target) ?: run {
                    if (client.changeWorkingDirectory(target)) {
                        val res = client.listFiles()
                        client.changeToParentDirectory()
                        res
                    } else null
                }
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun openStream(config: FtpConfig, remotePath: String): InputStream {
        if (config.isSmb) return smbManager.openStream(config, remotePath)
        if (config.isWebDav) return webDavManager.openStream(config, remotePath)

        val (host, port) = sanitizeHostAndPort(config)
        val client = createClient(config)
        client.connect(host, port)
        val user = config.username.ifBlank { "anonymous" }
        val loggedIn = client.login(user, config.password)
        if (!loggedIn) {
            client.disconnect()
            throw Exception("Autenticación fallida al reproducir en FTP")
        }

        configurePostLogin(client)
        client.enterLocalPassiveMode()
        client.setFileType(FTP.BINARY_FILE_TYPE)

        val normalized = if (remotePath.startsWith("/")) remotePath else "/$remotePath"
        val ftpStream = client.retrieveFileStream(normalized)
            ?: client.retrieveFileStream(remotePath.removePrefix("/"))
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

            val (host, port) = sanitizeHostAndPort(config)
            destFile.parentFile?.mkdirs()
            val client = createClient(config)
            client.connect(host, port)
            val user = config.username.ifBlank { "anonymous" }
            client.login(user, config.password)
            configurePostLogin(client)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            val normalized = if (remotePath.startsWith("/")) remotePath else "/$remotePath"
            FileOutputStream(destFile).use { out ->
                val success = client.retrieveFile(normalized, out) || client.retrieveFile(remotePath.removePrefix("/"), out)
                if (!success) {
                    return@withContext Result.failure(Exception("No se pudo descargar el archivo de $remotePath"))
                }
            }
            try { client.logout() } catch (ignored: Exception) {}
            try { client.disconnect() } catch (ignored: Exception) {}
            Result.success(true)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
