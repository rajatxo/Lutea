package com.rajatxo.lutea.data.source

import android.content.Context
import com.rajatxo.lutea.domain.model.Song

/**
 * MusicSource — the extension point for Lutea's music backend.
 *
 * Implement this interface to add a new music source (local files, YouTube Music,
 * JioSaavn, etc.). The core app discovers available sources and lets the user
 * switch between them.
 *
 * For now, only LocalMusicSource is implemented. Streaming sources can be added
 * later by implementing this interface — no changes to the UI layer needed.
 */
interface MusicSource {
    /** Display name shown in the source picker (e.g. "Local", "YouTube Music"). */
    val name: String

    /** Whether this source streams music (vs. playing local files). */
    val isStreaming: Boolean

    /** Load all songs from this source. */
    suspend fun getAllSongs(): List<Song>

    /** Search songs by query (title, artist, album). */
    suspend fun search(query: String): List<Song>

    /** Get all songs grouped by artist name. */
    suspend fun getArtists(): Map<String, List<Song>>

    /** Get all songs grouped by album name. */
    suspend fun getAlbums(): Map<String, List<Song>>

    /** Get the stream/playback URI for a song. For local sources, this is the file URI. */
    fun getPlaybackUri(song: Song): String

    /** Get album art URI for a song (nullable if not available). */
    fun getAlbumArtUri(song: Song): String?
}
