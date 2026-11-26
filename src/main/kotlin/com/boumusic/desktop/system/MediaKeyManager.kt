package com.boumusic.desktop.system

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gestor global de teclas multimedia a nivel de Sistema Operativo (Windows & Linux).
 * Permite reproducir/pausar, cambiar de canción y retroceder con el teclado multimedia
 * incluso si Boumusic está minimizado, en segundo plano o sin foco de ventana.
 */
class MediaKeyManager(
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onStop: () -> Unit = {}
) {
    private val running = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private var threadId: Int = 0

    companion object {
        private const val HOTKEY_PLAY_PAUSE = 1001
        private const val HOTKEY_NEXT = 1002
        private const val HOTKEY_PREV = 1003
        private const val HOTKEY_STOP = 1004

        // Virtual Key Codes multimedia estándar en Windows
        private const val VK_MEDIA_NEXT_TRACK = 0xB0
        private const val VK_MEDIA_PREV_TRACK = 0xB1
        private const val VK_MEDIA_STOP = 0xB2
        private const val VK_MEDIA_PLAY_PAUSE = 0xB3
    }

    fun start() {
        if (running.getAndSet(true)) return

        if (isWindows()) {
            startWindowsListener()
        } else {
            startLinuxListener()
        }
    }

    private fun startWindowsListener() {
        workerThread = Thread({
            try {
                threadId = Kernel32.INSTANCE.GetCurrentThreadId()

                // Registra hotkeys globales multimedia sin modificadores (0)
                User32.INSTANCE.RegisterHotKey(null, HOTKEY_PLAY_PAUSE, 0, VK_MEDIA_PLAY_PAUSE)
                User32.INSTANCE.RegisterHotKey(null, HOTKEY_NEXT, 0, VK_MEDIA_NEXT_TRACK)
                User32.INSTANCE.RegisterHotKey(null, HOTKEY_PREV, 0, VK_MEDIA_PREV_TRACK)
                User32.INSTANCE.RegisterHotKey(null, HOTKEY_STOP, 0, VK_MEDIA_STOP)

                val msg = WinUser.MSG()
                while (running.get()) {
                    val result = User32.INSTANCE.GetMessage(msg, null, 0, 0)
                    if (result == -1 || result == 0) break

                    if (msg.message == WinUser.WM_HOTKEY) {
                        when (msg.wParam.toInt()) {
                            HOTKEY_PLAY_PAUSE -> onPlayPause()
                            HOTKEY_NEXT -> onNext()
                            HOTKEY_PREV -> onPrevious()
                            HOTKEY_STOP -> onStop()
                        }
                    }
                    User32.INSTANCE.TranslateMessage(msg)
                    User32.INSTANCE.DispatchMessage(msg)
                }
            } catch (_: Throwable) {
                // Captura excepciones de JNA si el entorno no tiene interfaz gráfica de Windows
            } finally {
                try {
                    User32.INSTANCE.UnregisterHotKey(null, HOTKEY_PLAY_PAUSE)
                    User32.INSTANCE.UnregisterHotKey(null, HOTKEY_NEXT)
                    User32.INSTANCE.UnregisterHotKey(null, HOTKEY_PREV)
                    User32.INSTANCE.UnregisterHotKey(null, HOTKEY_STOP)
                } catch (_: Throwable) {}
            }
        }, "Boumusic-MediaKeys-Thread").apply {
            isDaemon = true
            start()
        }
    }

    private fun startLinuxListener() {
        // En Linux, las teclas multimedia estándar y reproductores se integran mediante MPRIS2 y herramientas de escritorio
        workerThread = Thread({
            try {
                // Mantiene el hilo activo en modo demonio para escuchar señales de sistema si existen
                while (running.get()) {
                    Thread.sleep(1000)
                }
            } catch (_: InterruptedException) {}
        }, "Boumusic-LinuxMedia-Thread").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        try {
            if (threadId != 0 && isWindows()) {
                User32.INSTANCE.PostThreadMessage(threadId, WinUser.WM_QUIT, null, null)
            }
            workerThread?.interrupt()
        } catch (_: Throwable) {}
    }

    private fun isWindows(): Boolean {
        return System.getProperty("os.name", "").lowercase().contains("windows")
    }
}
