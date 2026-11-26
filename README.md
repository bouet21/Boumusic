# 🖥️ Boumusic Desktop — Reproductor de Música Moderno y Ligero (Windows & Linux)

[![Release](https://img.shields.io/github/v/release/bouet21/Boumusic-Desktop?color=00F5D4&label=Versi%C3%B3n%20Oficial%20v2.0.1)](https://github.com/bouet21/Boumusic-Desktop/releases)
[![Platform](https://img.shields.io/badge/Plataforma-Windows%20%7C%20Linux-7B2CBF)](#)
[![Compose Desktop](https://img.shields.io/badge/Compose%20Multiplatform-1.6.11-brightgreen)](https://www.jetbrains.com/lp/compose-multiplatform/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue)](https://kotlinlang.org)
[![RAM Usage](https://img.shields.io/badge/RAM-~250%20MB%20(Eficiente%20vs%20Electron)-success)](#)
[![No Ads](https://img.shields.io/badge/Publicidad-0%25%20(Sin%20Anuncios)-success)](#)

**Boumusic Desktop** es un reproductor de música moderno para ordenador (Windows y Linux) creado desde cero con **Compose Multiplatform (Kotlin)**. Ha sido diseñado para amantes de la música local y self-hosters que buscan una experiencia fluida, estética ciberpunk personalizable, cero publicidad y conexión directa a carpetas compartidas y servidores remotos.

---

## ⚡ Rendimiento y Eficiencia

A diferencia de los reproductores modernos construidos sobre Electron o navegadores Chromium embebidos (que suelen devorar entre 500 MB y más de 1 GB de memoria RAM con múltiples procesos en segundo plano), Boumusic Desktop se apoya directamente en el motor gráfico nativo **Skia (DirectX en Windows / OpenGL y Vulkan en Linux)**:
- **Consumo moderado de memoria**: Estable en ~250 MB de RAM.
- **0% de uso de CPU en reposo**: Sin procesos auxiliares de navegador.
- **Interfaz fluida**: Renderizado acelerado por hardware a 60/120 FPS.

---

## 📲 Descarga Directa (v2.0.1)

| Sistema | Paquete | Enlace de Descarga |
| :--- | :--- | :--- |
| **Windows x64** 🪟 | **Instalador Guiado (Setup EXE)** | 👉 **[Descargar Boumusic-2.0.1-Setup.exe](https://github.com/bouet21/Boumusic-Desktop/releases/download/v2.0.1/Boumusic-2.0.1-Setup.exe)** |
| **Windows x64** 🪟 | **Portable (ZIP)** | 👉 **[Descargar Boumusic-Desktop-v2.0.1-windows-x64.zip](https://github.com/bouet21/Boumusic-Desktop/releases/download/v2.0.1/Boumusic-Desktop-v2.0.1-windows-x64.zip)** |
| **Linux x64** 🐧 | **Paquete Lanzador (Script + JAR)** | 👉 **[Descargar Boumusic-Linux-x64-v2.0.1.zip](https://github.com/bouet21/Boumusic-Desktop/releases/download/v2.0.1/Boumusic-Linux-x64-v2.0.1.zip)** |

*(Consulta todas las versiones anteriores en la sección de [Releases de GitHub](https://github.com/bouet21/Boumusic-Desktop/releases) o revisa el historial detallado en [CHANGELOG.md](CHANGELOG.md)).*

### 🔐 Verificación de Integridad (SHA-256)

Para verificar que tus archivos descargados son auténticos y no han sufrido alteraciones:

```text
d9fdd87d08597961e12c438b8d1914c90ff27a1b7a18b11404d34d5d90b33f84  Boumusic-2.0.1-Setup.exe
03a6aeb94e59fe4ed1677445dd17710c9c24e709d807c1d61d9b3bfac323f3b8  Boumusic-Desktop-v2.0.1-windows-x64.zip
8efbc0d1bf9387f314eb3d00dc5850bcb7e4263aec003ed5586225854dce9a7d  Boumusic-Linux-x64-v2.0.1.zip
```

---

## ✨ Características Principales

* 🖥️ **Soporte Nativo SMB2/SMB3 (Windows y Linux Samba)**:
  * Conéctate directamente a carpetas compartidas de red local a través del puerto 445 sin necesidad de montar unidades de red manuales.
  * Streaming instantáneo sin esperas con extracción de carátulas y metadatos en directo.
* 🌐 **Protocolos de Almacenamiento Remoto (FTP / FTPS / WebDAV)**:
  * Accede a tu música alojada en servidores NAS, Nextcloud, ownCloud o servidores FTP seguros con navegación jerárquica por carpetas.
* 🎨 **Personalización Visual Completa**:
  * 7 temas predefinidos (*Cyberpunk*, *Midnight AMOLED*, *Neon Sunset*, *Emerald Matrix*, *Synthwave 80s*, *Solar Amber*, *Rose Gold*).
  * Paleta libre de colores primarios y secundarios, fondos base personalizados e imágenes propias de wallpaper con regulador de atenuación (*dimming*).
* 📥 **Módulo SpotDL & Sincronización de Pistas**:
  * Búsqueda multi-fuente de alta velocidad (Deezer, iTunes y fuentes abiertas) con carátulas en alta resolución y metadatos limpios.
  * Guardado con un solo clic directamente a tus carpetas de red SMB, servidores FTP o biblioteca local.
* 🔄 **Sincronización Total con Boumusic Android (JSON & M3U8)**:
  * Importa y exporta en un clic copias de seguridad de servidores remotos y listas universales M3U8.
* 🛡️ **Seguridad de Credenciales**:
  * Las contraseñas de servidores remotos se guardan cifradas (AES-GCM), evitando el almacenamiento en texto plano en disco.
* 🎛️ **Motor de Audio Multipropósito**:
  * Decodificadores nativos para **MP3, FLAC, WAV, OGG Vorbis, AAC y OPUS** con barra de posición exacta y modo aleatorio determinista.
* 📌 **Integración en Bandeja del Sistema (System Tray)**:
  * Icono persistente en la barra de tareas con controles de reproducción rápida (Pausar/Reanudar, Pista siguiente, Salir).

---

## 🚀 Cómo Ejecutar en Desarrollo

Requisitos: **JDK 17 o JDK 21**.

```bash
# Clonar el repositorio
git clone https://github.com/bouet21/Boumusic-Desktop.git
cd Boumusic-Desktop

# Ejecutar la aplicación
./gradlew run

# Ejecutar las pruebas unitarias
./gradlew test
```

---

## 📦 Compilación de Instaladores y Paquetes

Boumusic Desktop utiliza el plugin oficial de Compose Desktop con `jpackage`:

* **Instalador ejecutable de Windows (`Setup.exe`)**:
  ```powershell
  .\gradlew packageExe
  # Genera el instalador en build/compose/binaries/main/exe/Boumusic-2.0.1.exe
  ```
* **Distribución completa del sistema operativo en curso**:
  ```powershell
  .\gradlew createDistributable
  # Genera la carpeta ejecutable lista en build/compose/binaries/main/app/Boumusic/
  ```
* **Paquete JAR autónomo para Linux**:
  ```bash
  ./gradlew packageUberJarForCurrentOS
  # Genera el JAR ejecutable en build/compose/jars/
  ```

---

## 🗺️ Hoja de Ruta (Roadmap)

- [ ] Soporte para protocolos self-hosted de streaming musical (**Subsonic / Navidrome / Jellyfin**).
- [ ] Reproducción continua sin silencios (*Gapless Playback*) y normalización de volumen (**ReplayGain / EBU R128**).
- [ ] Integración profunda con Controles Multimedia del Sistema (**Windows SMTC** y **Linux MPRIS2**).
- [ ] Visualizador de letras de canciones sincronizadas (*.lrc* y letras embebidas).
- [ ] Paquetes universales para Linux (**AppImage** y **Flatpak**).

---

## 📜 Licencia y Comunidad

Proyecto 100% de código abierto, gratuito, sin publicidad y respetuoso con la privacidad del usuario.
Si deseas colaborar, consulta nuestra [Guía de Contribución](CONTRIBUTING.md) o reporta incidencias mediante las [plantillas de Issues](.github/ISSUE_TEMPLATE/).
Desarrollado en sintonía con la app móvil de [Boumusic para Android](https://github.com/bouet21/Bouetmusic).
