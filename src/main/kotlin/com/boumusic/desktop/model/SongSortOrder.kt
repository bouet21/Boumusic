package com.boumusic.desktop.model

enum class SongSortOrder(val displayName: String) {
    DEFAULT("Por defecto"),
    TITLE_ASC("Título (A-Z)"),
    ARTIST_ASC("Artista (A-Z)"),
    DURATION_DESC("Duración"),
    RECENT("Más recientes")
}
