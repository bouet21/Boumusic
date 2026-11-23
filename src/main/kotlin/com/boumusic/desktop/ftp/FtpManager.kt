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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class FtpManager(
    val smbManager: SmbManager = SmbManager(),
    val webDavManager: WebDavManager = WebDavManager()
) {

    private val logFile by lazy {
        try {
            val dir = File(System.getProperty("user.home"), ".boumusic").apply { if (!exists()) mkdirs() }
            File(dir, "ftp_debug.log")
        } catch (_: Exception) {
            null
        }
    }

    fun log(msg: String) {
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val line = "[$time] $msg"
        println(line)
        try {
            logFile?.appendText("$line\n")
        } catch (_: Exception) {}
    }

    fun sanitizeHostAndPort(config: FtpConfig): Pair<String, Int> {
        var h = config.host.trim()
            .removePrefix("ftp://")
            .removePrefix("ftps://")
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("smb://")
            .removePrefix("\\\\")
            .trimEnd('/')

        var p = config.port
        if (h.contains(":")) {
            val idx = h.lastIndexOf(':')
            val pCandidate = h.substring(idx + 1).toIntOrNull()
            if (pCandidate != null) {
                p = pCandidate
                h = h.substring(0, idx).trim()
            }
        }
        return h to p
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

        client.connectTimeout = 10000
        client.defaultTimeout = 15000
        return client
    }

    private fun configurePostLogin(client: FTPClient) {
        if (client is FTPSClient) {
            try {
                client.execPBSZ(0)
                client.execPROT("P")
            } catch (e: Exception) {
                log("Error en configurePostLogin (FTPS): ${e.message}")
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

        log("testConnection: Conectando a $host:$port...")
        var client: FTPClient? = null
        try {
            client = createClient(config)
            client.connect(host, port)
            log("testConnection: Conectado. Reply: ${client.replyCode} - ${client.replyString.trim()}")

            val user = config.username.trim().ifBlank { "anonymous" }
            val pass = config.password
            log("testConnection: Intentando login con usuario '$user'...")
            val loginSuccess = client.login(user, pass)
            log("testConnection: Login resultado: $loginSuccess. Reply: ${client.replyCode} - ${client.replyString.trim()}")
            if (!loginSuccess) {
                return@withContext Result.failure(Exception("Autenticación fallida: usuario o contraseña incorrectos en $host:$port"))
            }

            configurePostLogin(client)
            client.enterLocalPassiveMode()
            try { client.disconnect() } catch (_: Exception) {}
            log("testConnection: Éxito total")
            Result.success(true)
        } catch (e: Exception) {
            log("testConnection ERROR: ${e.javaClass.simpleName} - ${e.message}")
            Result.failure(friendlyException(e, host, port))
        } finally {
            try { if (client?.isConnected == true) client.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun listDirectory(config: FtpConfig, path: String): Result<List<FtpItem>> = withContext(Dispatchers.IO) {
        if (config.isSmb) return@withContext smbManager.listDirectory(config, path)
        if (config.isWebDav) return@withContext webDavManager.listDirectory(config, path)

        val (host, port) = sanitizeHostAndPort(config)
        if (host.isBlank()) {
            return@withContext Result.failure(Exception("La dirección IP o host no puede estar vacía"))
        }

        log("listDirectory: Conectando a $host:$port para listar ruta '$path'...")
        var client: FTPClient? = null
        try {
            client = createClient(config)
            client.connect(host, port)
            log("listDirectory: Conectado. Reply: ${client.replyCode}")

            val user = config.username.trim().ifBlank { "anonymous" }
            val pass = config.password
            val loginSuccess = client.login(user, pass)
            log("listDirectory: Login con '$user': $loginSuccess")
            if (!loginSuccess) {
                return@withContext Result.failure(Exception("Autenticación fallida en $host:$port (usuario: $user)"))
            }

            configurePostLogin(client)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            val targetPath = if (path.isBlank()) "/" else path
            log("listDirectory: Solicitando listFiles('$targetPath')...")
            var files = client.listFiles(targetPath)

            if (files == null && targetPath == "/") {
                log("listDirectory: listFiles('/') devolvió null, probando con listFiles() relativo...")
                files = client.listFiles("") ?: client.listFiles()
            }

            log("listDirectory: Archivos recibidos: ${files?.size ?: "null"}")

            val items = files?.mapNotNull { f ->
                val name = f.name
                if (name != "." && name != "..") {
                    val fullPath = if (targetPath.endsWith("/")) "$targetPath$name" else "$targetPath/$name"
                    val isDir = f.isDirectory
                    FtpItem(name = name, path = fullPath, isDirectory = isDir, size = f.size)
                } else null
            }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()

            try { client.disconnect() } catch (_: Exception) {}
            log("listDirectory: Éxito con ${items.size} elementos devueltos")
            Result.success(items)
        } catch (e: Exception) {
            log("listDirectory ERROR: ${e.javaClass.simpleName} - ${e.message}")
            Result.failure(friendlyException(e, host, port))
        } finally {
            try { if (client?.isConnected == true) client.disconnect() } catch (_: Exception) {}
        }
    }

    private fun friendlyException(e: Exception, host: String, port: Int): Exception {
        val msg = e.message ?: ""
        return when {
            msg.contains("Connection refused", ignoreCase = true) ->
                Exception("Conexión rechazada en $host:$port. Compruebe que la IP y el puerto sean correctos y que el servidor FTP esté activo.")
            e is java.net.SocketTimeoutException || msg.contains("timed out", ignoreCase = true) ->
                Exception("Tiempo de espera agotado al conectar a $host:$port. Compruebe que el PC y el servidor estén en la misma red y que el servidor FTP esté activo.")
            e is java.net.UnknownHostException ->
                Exception("No se puede resolver el host '$host'. Compruebe la dirección IP.")
            else ->
                Exception("${e.javaClass.simpleName}: ${e.message ?: "Error al comunicar con el servidor FTP"}")
        }
    }

    fun openStream(config: FtpConfig, remotePath: String): InputStream {
        if (config.isSmb) return smbManager.openStream(config, remotePath)
        if (config.isWebDav) return webDavManager.openStream(config, remotePath)

        val (host, port) = sanitizeHostAndPort(config)
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
            val (host, port) = sanitizeHostAndPort(config)
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

            try { client.disconnect() } catch (_: Exception) {}
            if (success) Result.success(true) else Result.failure(Exception("No se pudo descargar el archivo: $remotePath"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
