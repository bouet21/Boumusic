package com.boumusic.desktop.ui.util

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.ptr.IntByReference
import java.awt.Color
import java.awt.Window
import javax.swing.JDialog
import javax.swing.JFrame

object WindowsDarkThemeHelper {

    private interface Dwmapi : Library {
        fun DwmSetWindowAttribute(hwnd: HWND, dwAttribute: Int, pvAttribute: IntByReference, cbAttribute: Int): Int
    }

    private val dwmapi: Dwmapi? by lazy {
        try {
            if (isWindows()) {
                Native.load("dwmapi", Dwmapi::class.java)
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun isWindows(): Boolean {
        return System.getProperty("os.name", "").lowercase().contains("windows")
    }

    /**
     * Aplica el tema oscuro a la barra de título de la ventana nativa de Windows (DWM),
     * eliminando el marco blanco predeterminado y sincronizándolo con el tema oscuro de Boumusic.
     */
    fun applyDarkTitleBar(window: Window) {
        // Fijar color de fondo nativo AWT para evitar cualquier destello blanco al redimensionar o abrir
        window.background = Color(0x12, 0x12, 0x14)

        try {
            when (window) {
                is JFrame -> window.rootPane.putClientProperty("jetbrains.awt.windowDarkAppearance", true)
                is JDialog -> window.rootPane.putClientProperty("jetbrains.awt.windowDarkAppearance", true)
            }
        } catch (_: Throwable) {}

        if (!isWindows()) return

        try {
            val hwndVal = Native.getWindowID(window)
            if (hwndVal <= 0L) return
            val hwnd = HWND(Pointer.createConstant(hwndVal))
            val dapi = dwmapi ?: return

            // 1. DWMWA_USE_IMMERSIVE_DARK_MODE
            // 20 = Windows 11 y Windows 10 (Build 18985+)
            // 19 = Windows 10 (Build 17763 a 18363)
            val darkEnabled = IntByReference(1)
            val hr20 = dapi.DwmSetWindowAttribute(hwnd, 20, darkEnabled, 4)
            if (hr20 != 0) {
                dapi.DwmSetWindowAttribute(hwnd, 19, darkEnabled, 4)
            }

            // 2. DWMWA_CAPTION_COLOR = 35 (Windows 11 Build 22000+)
            // Formato COLORREF: 0x00BBGGRR -> #121214 (R=12, G=12, B=14) = 0x00141212
            val captionColor = IntByReference(0x00141212)
            dapi.DwmSetWindowAttribute(hwnd, 35, captionColor, 4)

            // 3. DWMWA_TEXT_COLOR = 36 (Windows 11) -> Blanco 0x00FFFFFF
            val textColor = IntByReference(0x00FFFFFF)
            dapi.DwmSetWindowAttribute(hwnd, 36, textColor, 4)

            // 4. DWMWA_BORDER_COLOR = 34 (Windows 11) -> Borde sutil oscuro 0x00242220
            val borderColor = IntByReference(0x00242220)
            dapi.DwmSetWindowAttribute(hwnd, 34, borderColor, 4)
        } catch (_: Throwable) {
            // Silencioso en caso de entornos sin DWM
        }
    }
}
