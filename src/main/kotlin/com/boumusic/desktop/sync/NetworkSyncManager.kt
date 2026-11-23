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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
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
    val deviceName: String = "PC (${System.getProperty("os.name")})",
    val httpPort: Int = 8089,
    val udpPort: Int = 42424,
    private val serverProvider: () -> List<FtpConfig>,
    private val playlistProvider: () -> List<Playlist>,
    private val onDataReceived: (servers: List<FtpConfig>, playlists: List<Playlist>) -> Unit
) {

    val deviceId: String = "desktop-" + UUID.randomUUID().toString().take(8)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _discoveredDevices = MutableStateFlow<List<NetworkDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<NetworkDevice>> = _discoveredDevices.asStateFlow()

    private val _autoSyncDeviceIds = MutableStateFlow<Set<String>>(emptySet())
    val autoSyncDeviceIds: StateFlow<Set<String>> = _autoSyncDeviceIds.asStateFlow()

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus.asStateFlow()

    private var udpSocket: DatagramSocket? = null
    private var httpServer: HttpServer? = null
    private var discoveryJob: Job? = null
    private var broadcastJob: Job? = null
    private var lastSyncTimes = mutableMapOf<String, Long>()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun start() {
        startHttpServer()
        startUdpDiscovery()
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
            // Sincronizar inmediatamente al activarlo
            _discoveredDevices.value.find { it.id == deviceId }?.let { dev ->
                syncWithDevice(dev)
            }
        }
        _autoSyncDeviceIds.value = current
    }

    private fun startHttpServer() {
        try {
            httpServer = HttpServer.create(InetSocketAddress(httpPort), 0)
            httpServer?.createContext("/api/sync", SyncHttpHandler())
            httpServer?.executor = Executors.newFixedThreadPool(4)
            httpServer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private inner class SyncHttpHandler : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                if (exchange.requestMethod.equals("GET", ignoreCase = true)) {
                    val json = BackupManager.generateBackupJson(serverProvider(), playlistProvider())
                    val bytes = json.toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.write(bytes)
                    exchange.responseBody.close()
                } else if (exchange.requestMethod.equals("POST", ignoreCase = true)) {
                    val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                    val data = BackupManager.parseBackupJson(body)
                    onDataReceived(data.servers, data.playlists)

                    val resp = """{"success":true,"servers":${data.servers.size},"playlists":${data.playlists.size}}"""
                    val bytes = resp.toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.write(bytes)
                    exchange.responseBody.close()
                } else {
                    exchange.sendResponseHeaders(405, -1)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                exchange.sendResponseHeaders(500, -1)
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

            // Escuchar broadcasts entrantes
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
                            val rPort = o.optInt("port", 8089)

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

                                // Responder con PONG
                                val pong = "BOUMUSIC_PONG:{\"id\":\"$deviceId\",\"name\":\"$deviceName\",\"type\":\"PC\",\"port\":$httpPort}"
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
                            val rPort = o.optInt("port", 8089)

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
                    } catch (ignored: Exception) {
                        // Timeout esperado para chequear cancelación
                    }
                }
            }

            // Emitir ping de broadcast cada 5 segundos a todas las interfaces
            broadcastJob = scope.launch {
                while (isActive) {
                    try {
                        val ping = "BOUMUSIC_DISCOVERY:{\"id\":\"$deviceId\",\"name\":\"$deviceName\",\"type\":\"PC\",\"port\":$httpPort}"
                        val bytes = ping.toByteArray(Charsets.UTF_8)
                        for (bAddr in getBroadcastAddresses()) {
                            try {
                                val packet = DatagramPacket(bytes, bytes.size, bAddr, udpPort)
                                udpSocket?.send(packet)
                            } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        // Puede fallar si no hay interfaz de red activa
                    }

                    // Limpiar dispositivos no vistos en > 15s
                    val now = System.currentTimeMillis()
                    _discoveredDevices.value = _discoveredDevices.value.filter { now - it.lastSeen < 15000 }

                    delay(5000)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

        // Si este dispositivo tiene sincronización automática activada y han pasado > 30s desde la última sync
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

                // 1. Enviar datos locales al dispositivo remoto (POST)
                val localJson = BackupManager.generateBackupJson(serverProvider(), playlistProvider())
                val postReq = Request.Builder()
                    .url("http://${device.ip}:${device.port}/api/sync")
                    .post(localJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
                httpClient.newCall(postReq).execute().close()

                // 2. Obtener datos remotos del dispositivo (GET)
                val getReq = Request.Builder()
                    .url("http://${device.ip}:${device.port}/api/sync")
                    .get()
                    .build()
                val resp = httpClient.newCall(getReq).execute()
                val body = resp.body?.string() ?: ""
                resp.close()

                if (body.isNotBlank()) {
                    val remoteData = BackupManager.parseBackupJson(body)
                    onDataReceived(remoteData.servers, remoteData.playlists)
                }

                lastSyncTimes[device.id] = System.currentTimeMillis()
                _syncStatus.value = "✓ Sincronizado con ${device.name} con éxito"
                delay(4000)
                _syncStatus.value = null
            } catch (e: Exception) {
                e.printStackTrace()
                _syncStatus.value = "Error al sincronizar: ${e.message}"
            }
        }
    }
}
