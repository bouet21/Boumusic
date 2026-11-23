package com.boumusic.desktop.model

data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<Long>,
    val remoteSongs: List<Song> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val songCount: Int
        get() = songIds.size + remoteSongs.size
}
