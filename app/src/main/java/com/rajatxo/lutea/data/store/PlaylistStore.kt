package com.rajatxo.lutea.data.store

import android.content.Context
import com.rajatxo.lutea.data.model.Playlist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * PlaylistStore — persists playlists as JSON in internal storage.
 * Ported from Coral's PlaylistStore.kt (simplified).
 *
 * Singleton. Call init(context) from LuteaApplication.
 */
object PlaylistStore {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private lateinit var appContext: Context

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val playlistsFile get() = java.io.File(appContext.filesDir, "playlists.json")

    fun init(context: Context) {
        appContext = context.applicationContext
        loadPlaylists()
    }

    private fun loadPlaylists() {
        _playlists.value = try {
            if (playlistsFile.exists()) {
                json.decodeFromString(ListSerializer(Playlist.serializer()), playlistsFile.readText())
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persistPlaylists() {
        try {
            playlistsFile.writeText(json.encodeToString(ListSerializer(Playlist.serializer()), _playlists.value))
        } catch (e: Exception) { /* silent fail */ }
    }

    fun createPlaylist(name: String): Playlist {
        val playlist = Playlist(
            id = System.currentTimeMillis(),
            name = name.trim().ifEmpty { "Untitled playlist" }
        )
        _playlists.value = _playlists.value + playlist
        persistPlaylists()
        return playlist
    }

    fun deletePlaylist(playlistId: Long) {
        _playlists.value = _playlists.value.filter { it.id != playlistId }
        persistPlaylists()
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        _playlists.value = _playlists.value.map { p ->
            if (p.id == playlistId) p.copy(name = newName.trim().ifEmpty { "Untitled playlist" }) else p
        }
        persistPlaylists()
    }
}
