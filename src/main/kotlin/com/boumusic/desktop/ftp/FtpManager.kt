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

    fun sanitizeHostAndPort(config: FtpConfig): Triple<String, Int, String> {
        var host = config.host.trim()
            .removePrefix("ftp://")
            .removePrefix("ftps://")
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("smb://")
            .removePrefix("\\\\")
            .trim()

        var initialPath = "/"
        if (host.contains("/")) {
            val slashIdx = host.indexOf('/')
            val pathPart = host.substring(slashIdx).trimEnd('/')
            if (pathPart.isNotBlank()) {
                initialPath = pathPart
            }
            host = host.substring(0, slashIdx).trim()
        }

        var port = config.port
        if (host.contains(":")) {
            val colonIdx = host.lastIndexOf(':')
            val portCandidate = host.substring(colonIdx + 1).toIntOrNull()
            if (portCandidate != null) {
                port = portCandidate
                host = host.substring(0, colonIdx).trim()
            }
        }
        return Triple(host, port, initialPath)
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

        val (host, port, _) = sanitizeHostAndPort(config)
        if (host.isBlank()) {
            return@withContext Result.failure(Exception("La dirección IP o host no puede estar vacía"))
        }

        var client: FTPClient? = null
        try {
            println("[FTP] Probando conexión con $host:$port...")
            client = createClient(config)
            client.connect(host, port)

            val reply = client.replyCode
            if (!FTPReply.isPositiveCompletion(reply)) {
                return@withContext Result.failure(Exception("El servidor FTP rechazó la conexión (Código: $reply)"))
            }

            val user = config.username.trim().ifBlank { "anonymous" }
            val pass = config.password
            val loginSuccess = client.login(user, pass)
            if (!loginSuccess) {
                return@withContext Result.failure(Exception("Autenticación fallida: usuario o contraseña incorrectos en $host:$port"))
            }

            configurePostLogin(client)
            client.enterLocalPassiveMode()
            try { client.logout() } catch (_: Exception) {}
            println("[FTP] Conexión de prueba exitosa a $host:$port")
            Result.success(true)
        } catch (e: Exception) {
            println("[FTP] Error de prueba en $host:$port: ${e.message}")
            Result.failure(e)
        } finally {
            try { if (client?.isConnected == true) client.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun listDirectory(config: FtpConfig, path: String): Result<List<FtpItem>> = withContext(Dispatchers.IO) {
        if (config.isSmb) return@withContext smbManager.listDirectory(config, path)
        if (config.isWebDav) return@withContext webDavManager.listDirectory(config, path)

        val (host, port, defaultPath) = sanitizeHostAndPort(config)
        if (host.isBlank()) {
            return@withContext Result.failure(Exception("La dirección IP o host no puede estar vacía"))
        }

        val effectivePath = if (path.isBlank() || path == "/") defaultPath else path

        var client: FTPClient? = null
        try {
            println("[FTP] Conectando a $host:$port para listar '$effectivePath'...")
            client = createClient(config)
            client.connect(host, port)

            val reply = client.replyCode
            if (!FTPReply.isPositiveCompletion(reply)) {
                return@withContext Result.failure(Exception("El servidor FTP rechazó la conexión (Código: $reply)"))
            }

            val user = config.username.trim().ifBlank { "anonymous" }
            val pass = config.password
            val loginSuccess = client.login(user, pass)
            if (!loginSuccess) {
                return@withContext Result.failure(Exception("Autenticación fallida: usuario o contraseña incorrectos en $host:$port"))
            }

            configurePostLogin(client)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            val targetPath = if (effectivePath.isBlank()) "/" else effectivePath
            var files: Array<FTPFile>? = null

            // 1. Intentar listar directamente la ruta
            try {
                files = client.listFiles(targetPath)
                println("[FTP] listFiles('$targetPath') -> ${files?.size ?: "null"}")
            } catch (e: Exception) {
                println("[FTP] Error en listFiles('$targetPath'): ${e.message}")
            }

            // 2. Si files es nulo, probar con formato alternativo (ej. "" vs "/" para servidores relativos)
            if (files == null) {
                try {
                    val altPath = if (targetPath == "/") "" else if (targetPath.startsWith("/")) targetPath.removePrefix("/") else "/$targetPath"
                    files = client.listFiles(altPath)
                    println("[FTP] listFiles('$altPath') -> ${files?.size ?: "null"}")
                } catch (e: Exception) {
                    println("[FTP] Error en listFiles alternativo: ${e.message}")
                }
            }

            // 3. Si aún es nulo, probar cambiando de directorio de trabajo
            if (files == null) {
                try {
                    if (client.changeWorkingDirectory(targetPath)) {
                        files = client.listFiles()
                        println("[FTP] changeWorkingDirectory('$targetPath') + listFiles() -> ${files?.size ?: "null"}")
                    }
                } catch (e: Exception) {
                    println("[FTP] Error en changeWorkingDirectory: ${e.message}")
                }
            }

            if (files == null) {
                return@withContext Result.failure(Exception("No se pudieron listar los archivos de '$targetPath'. Compruebe los permisos o la ruta en el servidor."))
            }

            val items = files.mapNotNull { f ->
                val name = f.name
                if (name == "." || name == "..") return@mapNotNull null
                val normalizedTarget = if (targetPath == "/") "" else targetPath.trimEnd('/')
                val itemPath = if (normalizedTarget.isEmpty()) "/$name" else "$normalizedTarget/$name"
                FtpItem(name = name, path = itemPath, isDirectory = f.isDirectory, size = f.size)
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            try { client.logout() } catch (_: Exception) {}
            println("[FTP] Listado completado con éxito: ${items.size} elementos encontrados")
            Result.success(items)
        } catch (e: Exception) {
            println("[FTP] Error en listDirectory: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        } finally {
            try { if (client?.isConnected == true) client.disconnect() } catch (_: Exception) {}
        }
    }

    fun openStream(config: FtpConfig, remotePath: String): InputStream {
        if (config.isSmb) return smbManager.openStream(config, remotePath)
        if (config.isWebDav) return webDavManager.openStream(config, remotePath)

        val (host, port, _) = sanitizeHostAndPort(config)
        val client = createClient(config)
        client.connect(host, port)
        val user = config.username.trim().ifBlank { "anonymous" }
        val loggedIn = client.login(user, config.password)
        if (!loggedIn) {
            client.disconnect()
            throw Exception("Autenticación fallida al reproducir en FTP")
        }

        configurePostLogin(client)
        client.enterLocalPassiveMode()
        client.setFileType(FTP.BINARY_FILE_TYPE)

        val targetPath = if (remotePath.isBlank()) "/" else remotePath
        val ftpStream = client.retrieveFileStream(targetPath)
            ?: run {
                val alt = if (targetPath.startsWith("/")) targetPath.removePrefix("/") else "/$targetPath"
                client.retrieveFileStream(alt)
            }
            ?: throw Exception("No se pudo abrir el archivo remoto: $remotePath")

        return object : InputStream() {
            override fun read(): Int = ftpStream.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = ftpStream.read(b, off, len)
            override fun available(): Int = ftpStream.available()
            override fun close() {
                try { ftpStream.close() } catch (_: Exception) {}
                try { client.completePendingCommand() } catch (_: Exception) {}
                try { if (client.isConnected) client.disconnect() } catch (_: Exception) {}
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
            val (host, port, _) = sanitizeHostAndPort(config)
            val client = createClient(config)
            client.connect(host, port)
            val user = config.username.trim().ifBlank { "anonymous" }
            val loggedIn = client.login(user, config.password)
            if (!loggedIn) {
                client.disconnect()
                return@withContext Result.failure(Exception("Autenticación fallida al descargar"))
            }

            configurePostLogin(client)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            val targetPath = if (remotePath.isBlank()) "/" else remotePath
            val success = FileOutputStream(destFile).use { out ->
                client.retrieveFile(targetPath, out) || run {
                    val alt = if (targetPath.startsWith("/")) targetPath.removePrefix("/") else "/$targetPath"
                    client.retrieveFile(alt, out)
                }
            }

            try { client.logout() } catch (_: Exception) {}
            try { client.disconnect() } catch (_: Exception) {}
            if (success) Result.success(true) else Result.failure(Exception("No se pudo descargar el archivo: $remotePath"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
