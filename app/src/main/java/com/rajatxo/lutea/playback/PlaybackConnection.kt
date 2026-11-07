package com.rajatxo.lutea.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.rajatxo.lutea.domain.model.Song
import com.rajatxo.lutea.service.LuteaPlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PlaybackConnection — manages the MediaController lifecycle and exposes
 * playback state as Compose-observable StateFlows.
 * Ported from Coral X's PlaybackConnection.kt (simplified).
 *
 * Singleton so all composables share the same controller + state.
 */
object PlaybackConnection {

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var queue: List<Song> = emptyList()

    fun connect(context: Context) {
        if (controller != null) return
        try {
            val token = SessionToken(context, ComponentName(context, LuteaPlaybackService::class.java))
            controllerFuture = MediaController.Builder(context, token).buildAsync().also { future ->
                future.addListener({
                    try {
                        controller = future.get()
                        controller?.addListener(object : Player.Listener {
                            override fun onIsPlayingChanged(playing: Boolean) {
                                try {
                                    _isPlaying.value = playing
                                } catch (e: Exception) {
                                    Log.e("PlaybackConnection", "onIsPlayingChanged failed", e)
                                }
                            }

                            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                                try {
                                    val songId = mediaItem?.mediaId?.toLongOrNull()
                                    _currentSong.value = queue.firstOrNull { it.id == songId }
                                    _positionMs.value = 0L
                                    _durationMs.value = 0L
                                } catch (e: Exception) {
                                    Log.e("PlaybackConnection", "onMediaItemTransition failed", e)
                                }
                            }

                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                Log.e("PlaybackConnection", "Player error: ${error.errorCodeName}", error)
                            }
                        })
                    } catch (e: Exception) {
                        Log.e("PlaybackConnection", "Failed to connect to playback service", e)
                    }
                }, Runnable::run)
            }
        } catch (e: Exception) {
            Log.e("PlaybackConnection", "Failed to start MediaController builder", e)
        }
    }

    fun pollPosition() {
        try {
            controller?.let { c ->
                _positionMs.value = c.currentPosition.coerceAtLeast(0L)
                _durationMs.value = c.duration.coerceAtLeast(0L)
            }
        } catch (e: Exception) {
            // Ignore polling errors
        }
    }

    fun playSong(song: Song, allSongs: List<Song>) {
        queue = allSongs
        val controller = controller ?: run {
            android.util.Log.e("PlaybackConnection", "playSong called but controller is null — connect() not ready")
            return
        }
        try {
            val mediaItems = allSongs.map { s ->
                MediaItem.Builder()
                    .setUri(s.uri)
                    .setMediaId(s.id.toString())
                    .setCustomCacheKey(s.id.toString())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(s.title)
                            .setArtist(s.artist)
                            .setAlbumTitle(s.album)
                            .setArtworkUri(s.albumArtUri)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                            .setIsBrowsable(false)
                            .setIsPlayable(true)
                            .build()
                    )
                    .build()
            }
            val index = allSongs.indexOf(song).coerceAtLeast(0)
            controller.setMediaItems(mediaItems, index, 0)
            controller.prepare()
            controller.play()
            _currentSong.value = song
        } catch (e: Exception) {
            android.util.Log.e("PlaybackConnection", "playSong failed", e)
        }
    }

    fun playPause() {
        try {
            val controller = controller ?: return
            if (controller.isPlaying) controller.pause() else controller.play()
        } catch (e: Exception) {
            android.util.Log.e("PlaybackConnection", "playPause failed", e)
        }
    }

    fun next() {
        try {
            controller?.seekToNextMediaItem()
        } catch (e: Exception) {
            android.util.Log.e("PlaybackConnection", "next failed", e)
        }
    }

    fun prev() {
        try {
            controller?.seekToPreviousMediaItem()
        } catch (e: Exception) {
            android.util.Log.e("PlaybackConnection", "prev failed", e)
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            controller?.seekTo(positionMs)
        } catch (e: Exception) {
            android.util.Log.e("PlaybackConnection", "seekTo failed", e)
        }
    }

    fun disconnect() {
        controllerFuture?.let { future ->
            if (!future.isDone) {
                future.cancel(true)
            }
        }
        controller?.release()
        controller = null
        controllerFuture = null
        _isPlaying.value = false
        _currentSong.value = null
    }
}
