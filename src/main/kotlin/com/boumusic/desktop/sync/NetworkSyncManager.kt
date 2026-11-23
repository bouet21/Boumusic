package com.boumusic.desktop.sync

import com.boumusic.desktop.data.BackupManager
import com.boumusic.desktop.model.FtpConfig
import com.boumusic.desktop.model.Playlist
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class NetworkDevice(
    val id: String,
    val name: String,
    val type: String, // "PC" o "ANDROID"
    val ip: String,
    val port: Int,
    val lastSeen: Long = System.currentTimeMillis()
)

class NetworkSyncManager(
    val deviceName: String = "PC (" + (System.getenv("COMPUTERNAME") ?: System.getProperty("user.name") ?: "Desktop") + ")",
    val httpPort: Int = 8089,
    val udpPort: Int = 42424,
    private val serverProvider: () -> List<FtpConfig>,
    private val playlistProvider: () -> List<Playlist>,
    private val statsProvider: (() -> org.json.JSONObject?)? = null,
    private val onDataReceived: (servers: List<FtpConfig>, playlists: List<Playlist>, stats: org.json.JSONObject?) -> Unit
) {

    val deviceId: String = "desktop-" + UUID.randomUUID().toString().take(8)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _discoveredDevices = MutableStateFlow<List<NetworkDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<NetworkDevice>> = _discoveredDevices.asStateFlow()

    private val _autoSyncDeviceIds = MutableStateFlow<Set<String>>(emptySet())
    val autoSyncDeviceIds: StateFlow<Set<String>> = _autoSyncDeviceIds.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    var actualHttpPort: Int = httpPort
        private set

    val localIp: String?
        get() = getLocalWifiIp()

    private var udpSocket: DatagramSocket? = null
    private var httpServer: HttpServer? = null
    private var discoveryJob: Job? = null
    private var broadcastJob: Job? = null
    private var lastSyncTimes = mutableMapOf<String, Long>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val fastProbeClient = OkHttpClient.Builder()
        .connectTimeout(700, TimeUnit.MILLISECONDS)
        .readTimeout(1200, TimeUnit.MILLISECONDS)
        .build()

    fun start() {
        startHttpServer()
        startUdpDiscovery()
        // Escaneo inicial en segundo plano tras 1 segundo para encontrar dispositivos que bloqueen UDP broadcast
        scope.launch {
            delay(1000)
            scanSubnet()
        }
    }

    fun stop() {
        discoveryJob?.cancel()
        broadcastJob?.cancel()
        try { udpSocket?.close() } catch (ignored: Exception) {}
        try { httpServer?.stop(0) } catch (ignored: Exception) {}
    }

    fun toggleAutoSync(deviceId: String) {
        val current = _autoSyncDeviceIds.value.toMutableSet()
        if (current.contains(deviceId)) {
            current.remove(deviceId)
        } else {
            current.add(deviceId)
            _discoveredDevices.value.find { it.id == deviceId }?.let { dev ->
                syncWithDevice(dev)
            }
        }
        _autoSyncDeviceIds.value = current
    }

    fun getLocalWifiIp(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val name = (iface.name + " " + iface.displayName).lowercase()
                if (name.contains("tailscale") || name.contains("tun") || name.contains("tap") ||
                    name.contains("wsl") || name.contains("docker") || name.contains("vethernet")) continue

                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("100.") && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun startHttpServer() {
        var p = httpPort
        while (p < httpPort + 10) {
            try {
                httpServer = HttpServer.create(InetSocketAddress(p), 0)
                actualHttpPort = p
                break
            } catch (_: Exception) {
                p++
            }
        }

        httpServer?.createContext("/api/sync", SyncHttpHandler())
        httpServer?.createContext("/api/info", InfoHttpHandler())
        httpServer?.executor = Executors.newFixedThreadPool(4)
        try {
            httpServer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private inner class InfoHttpHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val json = """{"app":"Boumusic","platform":"Desktop","id":"$deviceId","name":"$deviceName","port":$actualHttpPort}"""
                val bytes = json.toByteArray(Charsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
                exchange.responseBody.close()

                // Auto-descubrir a quien nos contacta
                val clientIp = exchange.remoteAddress?.address?.hostAddress
                if (clientIp != null && clientIp != localIp && !clientIp.startsWith("127.")) {
                    scope.launch { probeAndAddDevice(clientIp) }
                }
            } catch (_: Exception) {
                try { exchange.sendResponseHeaders(500, -1) } catch (_: Exception) {}
            }
        }
    }

    private inner class SyncHttpHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                val clientIp = exchange.remoteAddress?.address?.hostAddress
                if (clientIp != null && clientIp != localIp && !clientIp.startsWith("127.")) {
                    scope.launch { probeAndAddDevice(clientIp) }
                }

                if (exchange.requestMethod.equals("GET", ignoreCase = true)) {
                    val json = BackupManager.generateBackupJson(serverProvider(), playlistProvider(), statsJson = statsProvider?.invoke())
                    val bytes = json.toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                    exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.write(bytes)
                    exchange.responseBody.close()
                } else if (exchange.requestMethod.equals("POST", ignoreCase = true)) {
                    val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                    val data = BackupManager.parseBackupJson(body)
                    onDataReceived(data.servers, data.playlists, data.statsJson)

                    val resp = """{"success":true,"servers":${data.servers.size},"playlists":${data.playlists.size}}"""
                    val bytes = resp.toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                    exchange.responseHeaders.set("Access-Control-Allow-Origin", "*")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.write(bytes)
                    exchange.responseBody.close()
                } else {
                    exchange.sendResponseHeaders(405, -1)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                try { exchange.sendResponseHeaders(500, -1) } catch (_: Exception) {}
            }
        }
    }

    private fun getBroadcastAddresses(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val name = (iface.name + " " + iface.displayName).lowercase()
                if (name.contains("tailscale") || name.contains("tun") || name.contains("tap")) continue

                for (addr in iface.interfaceAddresses) {
                    val b = addr.broadcast
                    if (b != null) list.add(b)
                }
            }
        } catch (_: Exception) {}
        try { list.add(InetAddress.getByName("255.255.255.255")) } catch (_: Exception) {}
        return list.distinct()
    }

    private fun startUdpDiscovery() {
        try {
            udpSocket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(udpPort))
                broadcast = true
                soTimeout = 2000
            }

            discoveryJob = scope.launch {
                val buffer = ByteArray(2048)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        udpSocket?.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val senderIp = packet.address.hostAddress

                        if (text.startsWith("BOUMUSIC_DISCOVERY:")) {
                            val jsonStr = text.removePrefix("BOUMUSIC_DISCOVERY:")
                            val o = JSONObject(jsonStr)
                            val rId = o.optString("id", "")
                            val rName = o.optString("name", "Dispositivo")
                            val rType = o.optString("type", "DESCONOCIDO")
                            val rPort = o.optInt("port", actualHttpPort)

                            if (rId != deviceId) {
                                val device = NetworkDevice(
                                    id = rId,
                                    name = rName,
                                    type = rType,
                                    ip = senderIp,
                                    port = rPort,
                                    lastSeen = System.currentTimeMillis()
                                )
                                updateDiscoveredDevice(device)

                                val pong = "BOUMUSIC_PONG:{\"id\":\"$deviceId\",\"name\":\"$deviceName\",\"type\":\"PC\",\"port\":$actualHttpPort}"
                                val pongBytes = pong.toByteArray(Charsets.UTF_8)
                                val pongPacket = DatagramPacket(pongBytes, pongBytes.size, packet.address, packet.port)
                                udpSocket?.send(pongPacket)
                            }
                        } else if (text.startsWith("BOUMUSIC_PONG:")) {
                            val jsonStr = text.removePrefix("BOUMUSIC_PONG:")
                            val o = JSONObject(jsonStr)
                            val rId = o.optString("id", "")
                            val rName = o.optString("name", "Dispositivo")
                            val rType = o.optString("type", "DESCONOCIDO")
                            val rPort = o.optInt("port", actualHttpPort)

                            if (rId != deviceId) {
                                val device = NetworkDevice(
                                    id = rId,
                                    name = rName,
                                    type = rType,
                                    ip = senderIp,
                                    port = rPort,
                                    lastSeen = System.currentTimeMillis()
                                )
                                updateDiscoveredDevice(device)
                            }
                        }
                    } catch (ignored: Exception) {}
                }
            }

            broadcastJob = scope.launch {
                while (isActive) {
                    try {
                        val ping = "BOUMUSIC_DISCOVERY:{\"id\":\"$deviceId\",\"name\":\"$deviceName\",\"type\":\"PC\",\"port\":$actualHttpPort}"
                        val bytes = ping.toByteArray(Charsets.UTF_8)
                        for (bAddr in getBroadcastAddresses()) {
                            try {
                                val packet = DatagramPacket(bytes, bytes.size, bAddr, udpPort)
                                udpSocket?.send(packet)
                            } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}

                    val now = System.currentTimeMillis()
                    _discoveredDevices.value = _discoveredDevices.value.filter { now - it.lastSeen < 25000 }
                    delay(5000)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun probeAndAddDevice(ip: String, portCandidate: Int? = null): Boolean {
        return withContext(Dispatchers.IO) {
            val portsToTry = if (portCandidate != null) listOf(portCandidate) else listOf(actualHttpPort, 8089, 8090, 8091)
            for (p in portsToTry) {
                try {
                    val req = Request.Builder()
                        .url("http://$ip:$p/api/info")
                        .get()
                        .build()
                    val resp = fastProbeClient.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        resp.close()
                        if (body.contains("Boumusic")) {
                            val o = JSONObject(body)
                            val rId = o.optString("id", "direct-$ip")
                            val rName = o.optString("name", "Dispositivo ($ip)")
                            val rType = o.optString("platform", "Móvil / PC")
                            val rPort = o.optInt("port", p)

                            if (rId != deviceId) {
                                val dev = NetworkDevice(
                                    id = rId,
                                    name = rName,
                                    type = rType,
                                    ip = ip,
                                    port = rPort,
                                    lastSeen = System.currentTimeMillis()
                                )
                                updateDiscoveredDevice(dev)
                                return@withContext true
                            }
                        }
                    } else {
                        resp.close()
                    }
                } catch (_: Exception) {}
            }
            false
        }
    }

    fun scanSubnet(onDone: ((Int) -> Unit)? = null) {
        val ip = getLocalWifiIp() ?: return
        val prefix = ip.substringBeforeLast(".") + "."
        _isScanning.value = true
        _syncStatus.value = "Buscando en red local ($prefix*)..."
        scope.launch(Dispatchers.IO) {
            var found = 0
            val jobs = (1..254).map { i ->
                async {
                    val target = "$prefix$i"
                    if (target != ip) {
                        if (probeAndAddDevice(target)) found++
                    }
                }
            }
            jobs.awaitAll()
            _isScanning.value = false
            _syncStatus.value = if (found > 0) "¡Se encontraron $found dispositivo(s)!" else null
            onDone?.invoke(found)
        }
    }

    fun connectDirectly(targetIp: String, port: Int = actualHttpPort, onDone: (Boolean, String) -> Unit) {
        val cleanIp = targetIp.trim().removePrefix("http://").removePrefix("https://").substringBefore(":")
        if (cleanIp.isBlank()) {
            onDone(false, "Introduce una dirección IP válida")
            return
        }
        scope.launch(Dispatchers.IO) {
            _syncStatus.value = "Conectando a $cleanIp..."
            val ok = probeAndAddDevice(cleanIp, port)
            if (ok) {
                val dev = _discoveredDevices.value.find { it.ip == cleanIp }
                if (dev != null) {
                    syncWithDevice(dev)
                    onDone(true, "¡Conectado y sincronizado con ${dev.name}!")
                } else {
                    onDone(true, "¡Dispositivo encontrado en $cleanIp!")
                }
            } else {
                _syncStatus.value = null
                onDone(false, "No se encontró ningún Boumusic activo en $cleanIp:$port. Comprueba la IP y que la app esté abierta.")
            }
        }
    }

    private fun updateDiscoveredDevice(device: NetworkDevice) {
        val list = _discoveredDevices.value.toMutableList()
        val index = list.indexOfFirst { it.id == device.id || it.ip == device.ip }
        if (index >= 0) {
            list[index] = device
        } else {
            list.add(device)
        }
        _discoveredDevices.value = list

        if (_autoSyncDeviceIds.value.contains(device.id)) {
            val lastSync = lastSyncTimes[device.id] ?: 0L
            if (System.currentTimeMillis() - lastSync > 30000L) {
                syncWithDevice(device)
            }
        }
    }

    fun syncWithDevice(device: NetworkDevice) {
        scope.launch {
            try {
                _syncStatus.value = "Sincronizando con ${device.name}..."

                // 1. Enviar datos locales (POST)
                val localJson = BackupManager.generateBackupJson(serverProvider(), playlistProvider(), statsJson = statsProvider?.invoke())
                val postReq = Request.Builder()
                    .url("http://${device.ip}:${device.port}/api/sync")
                    .post(localJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(postReq).execute().close()

                // 2. Obtener datos remotos (GET)
                val getReq = Request.Builder()
                    .url("http://${device.ip}:${device.port}/api/sync")
                    .get()
                    .build()
                val resp = httpClient.newCall(getReq).execute()
                val body = resp.body?.string() ?: ""
                resp.close()

                if (body.isNotBlank()) {
                    val remoteData = BackupManager.parseBackupJson(body)
                    onDataReceived(remoteData.servers, remoteData.playlists, remoteData.statsJson)
                }

                lastSyncTimes[device.id] = System.currentTimeMillis()
                _syncStatus.value = "✓ Sincronizado con ${device.name} con éxito"
                delay(4000)
                _syncStatus.value = null
            } catch (e: Exception) {
                e.printStackTrace()
                _syncStatus.value = "Error al sincronizar con ${device.name}: ${e.message}"
            }
        }
    }
}
