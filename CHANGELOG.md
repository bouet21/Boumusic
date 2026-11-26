# 📋 Registro de Cambios (Changelog) - Boumusic Desktop

Todas las novedades, mejoras y correcciones notables de **Boumusic Desktop** se documentan en este archivo.
El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y este proyecto se adhiere a [Semantic Versioning](https://semver.org/lang/es/).

---

## [2.0.1] - 2026-11-25

### Añadido
- **Suite Integral de Personalización**:
  - Sección dedicada en la barra lateral con icono de paleta.
  - 7 Presets visuales listos para usar (*Cyberpunk*, *Midnight AMOLED*, *Neon Sunset*, *Emerald Matrix*, *Synthwave 80s*, *Solar Amber*, *Rose Gold*).
  - Selector libre de colores primarios y secundarios para acentos, barras y botones.
  - Selector nativo de archivos para fondos de pantalla personalizados (`.png`, `.jpg`, `.jpeg`, `.webp`).
  - Control de atenuación (*dimming*) de 0% a 90% para garantizar legibilidad en toda la interfaz.
  - Conmutador de animaciones visuales y botón para restaurar valores por defecto.
  - Propagación translúcida del fondo en todas las pantallas (Biblioteca, Descargas, Servidores, etc.).
- **Buscador Multi-Fuente de Alta Velocidad**:
  - Integración directa con Deezer API e iTunes Search API para resolución instantánea (<200ms) de metadatos, álbumes y portadas en alta resolución sin necesidad de binarios locales.
  - Fallback automático a YouTube Web y auto-descarga de `yt-dlp` en segundo plano cuando se solicita una descarga por primera vez.
- **Distribución Multiplataforma**:
  - Paquete oficial para Linux x64 con script lanzador `boumusic.sh` y archivo de integración de escritorio `.desktop`.
  - Instalador ejecutable guiado para Windows (`Setup.exe`) y versión portable (`.zip`).

---

## [2.0.0] - 2026-11-25

### Añadido
- **Módulo de Descargas SpotDL & Audio Streams**:
  - Resolución inteligente de enlaces de Spotify (canciones, álbumes, playlists) y búsqueda por texto.
  - Guardado directo a carpetas compartidas de red **SMB** (Windows / Linux Samba), servidores **FTP / FTPS / WebDAV** o biblioteca local.
  - Cola de descargas en tiempo real con monitoreo de progreso y estado contextual.
  - Búsqueda extendida a 30 resultados iniciales con paginación interactiva (+25 pistas).

---

## [1.0.9] - 2026-11-24

### Añadido
- **Integración Nativa con Windows DWM**: Barra de título oscura nativa mediante JNA sin parpadeos blancos.
- **Título Dinámico**: La barra de la ventana muestra en tiempo real *"Boumusic — [Artista] - [Canción]"*.
- **Icono Oficial de Aplicación**: Integración de icono nativo `.ico` en Windows y `.png` en Linux.

---

## [1.0.8] - 2026-11-24

### Añadido
- **Detección Inteligente de Artista vs Título**: Corrección de inversiones de metadatos en canciones descargadas de YouTube o Sunnify con nomenclatura `Título - Artista`.
- **Aleatorio Inteligente y Determinista**: Avance sin repeticiones y retroceso determinista por el historial exacto de canciones escuchadas.
- **Protección de Retroceso**: Pulsar 'Anterior' pasados 3 segundos reinicia la pista actual; una segunda pulsación salta a la anterior.
- **Barra de Progreso Precisa**: Duración calculada al segundo para archivos remotos en streaming (SMB/FTP) con refresco optimizado a 1 Hz.

---

## [1.0.0] - 2026-11-23

### Añadido
- **Lanzamiento Estable de Boumusic Desktop**:
  - Renderizado nativo por GPU con Compose Multiplatform (DirectX / OpenGL / Vulkan).
  - Soporte completo para protocolos SMB2/SMB3, FTP, FTPS y WebDAV con streaming en directo.
  - Sincronización bidireccional de copias de seguridad (JSON) y listas de reproducción universales (M3U8) con Boumusic Android.
  - Decodificadores Java Sound de baja latencia para MP3, FLAC, WAV, OGG Vorbis, AAC y OPUS.
