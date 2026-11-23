# 🖥️ Boumusic Desktop — Reproductor de Música Moderno y Ligero (Windows & Linux)

[![Release](https://img.shields.io/github/v/release/bouet21/Boumusic-Desktop?color=00F5D4&label=Versi%C3%B3n%20Oficial%20v1.0.1)](https://github.com/bouet21/Boumusic-Desktop/releases)
[![Platform](https://img.shields.io/badge/Plataforma-Windows%20%7C%20Linux-7B2CBF)](#)
[![Compose Desktop](https://img.shields.io/badge/Compose%20Multiplatform-1.6.11-brightgreen)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue)](https://kotlinlang.org)
[![RAM Usage](https://img.shields.io/badge/RAM-~250%20MB%20(Eficiente%20vs%20Electron)-success)](#)
[![No Ads](https://img.shields.io/badge/Publicidad-0%25%20(Sin%20Anuncios)-success)](#)

**Boumusic Desktop** es la versión para ordenador (Windows y Linux) del reproductor **Boumusic**. Ha sido diseñada bajo una premisa fundamental: **máxima fluidez, ergonomía y rendimiento sin motores web**.

A diferencia de los reproductores modernos basados en Electron o navegadores embebidos (como Spotify o Discord) que consumen entre 500 MB y 1 GB de memoria RAM con múltiples procesos auxiliares de Chromium, Boumusic Desktop está construido sobre **Compose Multiplatform (Skia)** con renderizado directo por aceleración gráfica de hardware (DirectX en Windows / OpenGL y Vulkan en Linux), manteniendo un consumo estable de **~250 MB de memoria RAM**, un **0% de uso de procesador en reposo** y una interfaz fluida a 60/120 FPS.

---

## ✨ Características Principales

* 🖥️ **Soporte Nativo SMB (Windows & Linux Samba)**:
  * Conéctate directamente a carpetas compartidas de red de otros PCs con Windows o servidores Linux (Samba) a través del puerto 445.
  * Streaming fluido en directo sin esperas y descarga de pistas en segundo plano a tu ordenador local.
* 🌐 **Protocolos Remotos Adicionales**:
  * Compatibilidad con servidores **FTP**, **FTPS Seguro** y **WebDAV** (Nextcloud, ownCloud, NAS).
* 🔄 **Sincronización Total con Boumusic Android (JSON & M3U8)**:
  * Importa directamente el archivo `Boumusic_Backup_*.json` generado en tu móvil para tener al instante los mismos servidores remotos, credenciales y listas de reproducción.
  * Exporta e importa listas de reproducción universales en formato `.m3u8` compatibles con VLC, foobar2000 y Audacious.
* 🎧 **Motor de Audio Eficiente**:
  * Decodificadores nativos para **MP3, FLAC, WAV, OGG Vorbis, AAC y OPUS**.
  * Control de volumen analógico de ganancia maestra y barra de posición precisa.
* 🧭 **Interfaz Ergonómica y Futurista**:
  * Tema oscuro neón (*Neon Cyan* `#00F5D4` y *Electric Purple* `#7B2CBF`).
  * Barra lateral de navegación rápida (Biblioteca, Listas, Servidores, Sincronización).
  * Barra de reproducción persistente en la parte inferior.
  * Migas de pan interactivas (*Breadcrumbs*) para explorar carpetas de red con total comodidad.
  * Selector de ordenación rápida: Título (A-Z), Artista, Duración o Recientes.

---

## 🚀 Cómo Ejecutar en Desarrollo

Requisitos: JDK 17 o JDK 21 instalado.

```bash
# Clonar el repositorio
git clone https://github.com/bouet21/Boumusic-Desktop.git
cd Boumusic-Desktop

# Ejecutar la aplicación
./gradlew run
```

---

## 📦 Compilación de Instaladores Nativos

Boumusic Desktop utiliza `jpackage` para generar instaladores compactos y ejecutables autónomos:

* **En Windows**:
  ```powershell
  .\gradlew packageMsi
  # Genera el instalador MSI en build/compose/binaries/main/msi/
  ```
* **En Linux**:
  ```bash
  ./gradlew packageDeb
  # Genera el paquete DEB para Debian/Ubuntu en build/compose/binaries/main/deb/
  ```

---

## 📜 Licencia y Filosofía
Proyecto 100% de código abierto, gratuito, sin anuncios y respetuoso con la privacidad de tus datos.
Desarrollado para complementar la app móvil de [Boumusic para Android](https://github.com/bouet21/Bouetmusic).
