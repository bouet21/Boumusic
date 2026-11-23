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
import java.time.Duration
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
        client.setDataTimeout(Duration.ofMillis(12000))
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

            // Probar además lectura en canal de datos para verificar que el firewall permite listar
            val testFiles = try {
                client.listFiles() ?: client.listFiles("") ?: client.listFiles("/")
            } catch (eData: Exception) {
                log("testConnection: Advertencia en canal de datos: ${eData.message}")
                null
            }
            log("testConnection: Éxito total. Archivos detectados en raíz: ${testFiles?.size ?: "null"}")

            try { client.disconnect() } catch (_: Exception) {}
            Result.success(true)
        } catch (e: Exception) {
            log("testConnection ERROR: ${e.javaClass.simpleName} - ${e.message}")
            Result.failure(friendlyException(e, host, port))
        } finally {
            try { if (client?.isConnected == true) client.disconnect() } catch (_: Exception) {}
        }
    }

    private fun isAudioExtension(name: String): Boolean {
        val ext = name.substringAfterLast(".", "").lowercase()
        return ext in setOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus", "wma")
    }

    private fun fetchFiles(client: FTPClient, targetPath: String): Array<FTPFile>? {
        if (targetPath == "/" || targetPath.isEmpty()) {
            val current = client.listFiles()
            if (current != null && current.isNotEmpty()) return current
            val empty = client.listFiles("")
            if (empty != null && empty.isNotEmpty()) return empty
            val slash = client.listFiles("/")
            if (slash != null && slash.isNotEmpty()) return slash
            return current ?: empty ?: slash
        }

        val direct = client.listFiles(targetPath)
        if (direct != null && direct.isNotEmpty()) return direct

        if (client.changeWorkingDirectory(targetPath)) {
            val res = client.listFiles()
            try { client.changeToParentDirectory() } catch (_: Exception) {}
            if (res != null && res.isNotEmpty()) return res
        }

        val alt = if (targetPath.startsWith("/")) targetPath.removePrefix("/") else "/$targetPath"
        return client.listFiles(alt) ?: direct
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
            client.setFileType(FTP.BINARY_FILE_TYPE)

            val targetPath = if (path.isBlank()) "/" else path
            log("listDirectory: Intentando modo pasivo para '$targetPath'...")
            client.enterLocalPassiveMode()

            var files: Array<FTPFile>? = null
            var usedActive = false
            try {
                files = fetchFiles(client, targetPath)
            } catch (ePasv: Exception) {
                log("listDirectory: Falló canal pasivo (${ePasv.message}). Probando modo activo...")
                try {
                    client.enterLocalActiveMode()
                    usedActive = true
                    files = fetchFiles(client, targetPath)
                } catch (eAct: Exception) {
                    log("listDirectory: Falló también modo activo (${eAct.message})")
                    throw ePasv
                }
            }

            val items = mutableListOf<FtpItem>()
            if (files != null && files.isNotEmpty()) {
                for (f in files) {
                    val name = f.name
                    if (name == "." || name == "..") continue
                    val normalizedTarget = if (targetPath == "/") "" else targetPath.trimEnd('/')
                    val fullPath = if (normalizedTarget.isEmpty()) "/$name" else "$normalizedTarget/$name"
                    items.add(FtpItem(name = name, path = fullPath, isDirectory = f.isDirectory, size = f.size))
                }
            } else {
                log("listDirectory: listFiles devolvió null o vacío, probando listNames (NLST)...")
                val names = try {
                    if (targetPath == "/") client.listNames() ?: client.listNames("") ?: client.listNames("/")
                    else client.listNames(targetPath) ?: client.listNames(targetPath.removePrefix("/"))
                } catch (eNames: Exception) {
                    log("listDirectory: listNames error: ${eNames.message}")
                    null
                }

                if (names != null && names.isNotEmpty()) {
                    for (rawName in names) {
                        val name = rawName.substringAfterLast("/").substringAfterLast("\\").trim()
                        if (name.isEmpty() || name == "." || name == "..") continue
                        val normalizedTarget = if (targetPath == "/") "" else targetPath.trimEnd('/')
                        val fullPath = if (normalizedTarget.isEmpty()) "/$name" else "$normalizedTarget/$name"
                        val isAudio = isAudioExtension(name)
                        val isDir = !isAudio && !name.contains(".")
                        items.add(FtpItem(name = name, path = fullPath, isDirectory = isDir, size = 0L))
                    }
                }
            }

            try { client.disconnect() } catch (_: Exception) {}
            val sorted = items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            log("listDirectory: Éxito con ${sorted.size} elementos (modo activo=$usedActive)")
            Result.success(sorted)
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
                Exception("Tiempo de espera agotado al conectar o transferir datos con $host:$port. Compruebe que el firewall permita puertos pasivos o la conexión.")
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
            try { client.disconnect() } catch (_: Exception) {}
            throw Exception("Autenticación fallida al reproducir en FTP")
        }

        configurePostLogin(client)
        client.enterLocalPassiveMode()
        client.setFileType(FTP.BINARY_FILE_TYPE)

        val targetPath = if (remotePath.isBlank()) "/" else remotePath
        var ftpStream = client.retrieveFileStream(targetPath)
        if (ftpStream == null) {
            val alt = if (targetPath.startsWith("/")) targetPath.removePrefix("/") else "/$targetPath"
            ftpStream = client.retrieveFileStream(alt)
        }
        if (ftpStream == null) {
            try {
                client.enterLocalActiveMode()
                ftpStream = client.retrieveFileStream(targetPath) ?: client.retrieveFileStream(if (targetPath.startsWith("/")) targetPath.removePrefix("/") else "/$targetPath")
            } catch (_: Exception) {}
        }

        if (ftpStream == null) {
            try { client.disconnect() } catch (_: Exception) {}
            throw Exception("No se pudo abrir el archivo remoto: $remotePath")
        }

        return object : InputStream() {
            override fun read(): Int = ftpStream.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = ftpStream.read(b, off, len)
            override fun available(): Int = ftpStream.available()
            override fun close() {
                try { ftpStream.close() } catch (_: Exception) {}
                try {
                    if (client.isConnected) {
                        try { client.abort() } catch (_: Exception) {}
                        client.disconnect()
                    }
                } catch (_: Exception) {}
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
                try { client.disconnect() } catch (_: Exception) {}
                return@withContext Result.failure(Exception("Autenticación fallida al descargar"))
            }

            configurePostLogin(client)
            client.enterLocalPassiveMode()
            client.setFileType(FTP.BINARY_FILE_TYPE)

            val targetPath = if (remotePath.isBlank()) "/" else remotePath
            val alt = if (targetPath.startsWith("/")) targetPath.removePrefix("/") else "/$targetPath"
            var success = FileOutputStream(destFile).use { out ->
                client.retrieveFile(targetPath, out) || client.retrieveFile(alt, out)
            }

            if (!success) {
                try {
                    client.enterLocalActiveMode()
                    success = FileOutputStream(destFile).use { out ->
                        client.retrieveFile(targetPath, out) || client.retrieveFile(alt, out)
                    }
                } catch (_: Exception) {}
            }

            try { client.disconnect() } catch (_: Exception) {}
            if (success) Result.success(true) else Result.failure(Exception("No se pudo descargar el archivo: $remotePath"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
