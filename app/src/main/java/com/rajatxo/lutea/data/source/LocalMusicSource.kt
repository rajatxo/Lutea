package com.rajatxo.lutea.data.source

import android.content.Context
import com.rajatxo.lutea.data.scanner.MusicScanner
import com.rajatxo.lutea.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LocalMusicSource — plays audio files from the device's local storage.
 *
 * Supports folder-based scanning: the user picks a folder via SAF, and only
 * songs from that folder (and subfolders) are loaded. If no folder is set,
 * scans the whole device.
 *
 * This is the ONLY source for now. Streaming sources (YouTube Music, JioSaavn)
 * can be added later by implementing the MusicSource interface — the UI doesn't
 * need to change.
 */
class LocalMusicSource(private val context: Context) : MusicSource {

    override val name: String = "Local"
    override val isStreaming: Boolean = false

    private val prefs = context.getSharedPreferences("lutea_prefs", Context.MODE_PRIVATE)

    /** The folder path to scan (null = scan whole device). */
    var folderPath: String?
        get() = prefs.getString("music_folder_path", null)
        private set(value) {
            prefs.edit().putString("music_folder_path", value).apply()
        }

    /** Set the folder path (from SAF URI converted to file path, or null to scan all). */
    fun setFolder(path: String?) {
        folderPath = path
    }

    /** Clear the folder filter — scan the whole device. */
    fun clearFolder() {
        folderPath = null
    }

    override suspend fun getAllSongs(): List<Song> = withContext(Dispatchers.IO) {
        MusicScanner.scanMusic(context, folderPath)
    }

    override suspend fun search(query: String): List<Song> {
        val all = getAllSongs()
        val q = query.lowercase().trim()
        if (q.isBlank()) return all
        return all.filter {
            it.title.lowercase().contains(q) ||
            it.artist.lowercase().contains(q) ||
            it.album.lowercase().contains(q)
        }
    }

    override suspend fun getArtists(): Map<String, List<Song>> {
        return getAllSongs().groupBy { it.artist.ifBlank { "Unknown Artist" } }
    }

    override suspend fun getAlbums(): Map<String, List<Song>> {
        return getAllSongs().groupBy { it.album.ifBlank { "Unknown Album" } }
    }

    override fun getPlaybackUri(song: Song): String = song.uri.toString()

    override fun getAlbumArtUri(song: Song): String? = song.albumArtUri?.toString()
}
