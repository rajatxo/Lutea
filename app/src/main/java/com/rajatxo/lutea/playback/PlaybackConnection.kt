package com.rajatxo.lutea.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.rajatxo.lutea.domain.model.Song
import com.rajatxo.lutea.service.LuteaPlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await

object PlaybackConnection {

    private var controller: MediaController? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var queue: List<Song> = emptyList()

    suspend fun connect(context: Context) {
        if (controller != null) return
        try {
            val sessionToken = SessionToken(context, ComponentName(context, LuteaPlaybackService::class.java))
            val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            val c = controllerFuture.await()
            controller = c

            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) { c.volume = 1f }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val songId = mediaItem?.mediaId?.toLongOrNull()
                    _currentSong.value = queue.firstOrNull { it.id == songId }
                    _positionMs.value = 0L
                    _durationMs.value = 0L
                    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                        if (!c.isPlaying) c.play()
                    }
                }

                override fun onPlayerErrorChanged(error: androidx.media3.common.PlaybackException?) {
                    if (error != null) {
                        Log.e("PlaybackConnection", "Playback error: ${error.errorCodeName}", error)
                        try { c.seekToNextMediaItem(); c.prepare(); c.play() }
                        catch (e: Exception) { Log.e("PlaybackConnection", "Skip after error failed", e) }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("PlaybackConnection", "Failed to connect to playback service", e)
        }
    }

    fun pollPosition() {
        controller?.let { c ->
            _positionMs.value = c.currentPosition.coerceAtLeast(0L)
            _durationMs.value = c.duration.coerceAtLeast(0L)
        }
    }

    fun playSong(song: Song, allSongs: List<Song>) {
        queue = allSongs
        val c = controller ?: run {
            Log.e("PlaybackConnection", "playSong: controller is null")
            return
        }
        try {
            val mediaItems = allSongs.map { s ->
                MediaItem.Builder()
                    .setUri(s.uri)
                    .setMediaId(s.id.toString())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(s.title)
                            .setArtist(s.artist)
                            .setAlbumTitle(s.album)
                            .setArtworkUri(s.albumArtUri)
                            .build()
                    )
                    .build()
            }
            val index = allSongs.indexOf(song).coerceAtLeast(0)
            c.setMediaItems(mediaItems, index, 0)
            c.prepare()
            c.play()
            _currentSong.value = song
        } catch (e: Exception) {
            Log.e("PlaybackConnection", "playSong failed", e)
        }
    }

    fun playPause() {
        try { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
        catch (e: Exception) { Log.e("PlaybackConnection", "playPause failed", e) }
    }

    fun next() {
        try { controller?.seekToNextMediaItem() }
        catch (e: Exception) { Log.e("PlaybackConnection", "next failed", e) }
    }

    fun prev() {
        try { controller?.seekToPreviousMediaItem() }
        catch (e: Exception) { Log.e("PlaybackConnection", "prev failed", e) }
    }

    fun seekTo(positionMs: Long) {
        try { controller?.seekTo(positionMs) }
        catch (e: Exception) { Log.e("PlaybackConnection", "seekTo failed", e) }
    }

    fun disconnect() {
        controller?.release()
        controller = null
        _isPlaying.value = false
        _currentSong.value = null
    }
}
