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
 * PlaybackConnection — copied from Coral's pattern.
 *
 * Uses MediaController.Builder().buildAsync() with await() (suspend)
 * instead of addListener + future.get(). Stores state as StateFlows
 * for Compose observation.
 *
 * Key differences from the old version:
 *  - connect() is suspend — uses await() instead of addListener
 *  - Uses onPlayerErrorChanged (new API) not onPlayerError (old API)
 *  - MediaItems are simple (no setIsPlayable — Coral doesn't use it)
 *  - No startForeground, no notification provider — Media3 handles it
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
            controllerFuture = MediaController.Builder(context, token).buildAsync()
            controllerFuture!!.addListener({
                try {
                    val c = controllerFuture!!.get()
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
            }, Runnable::run)
        } catch (e: Exception) {
            Log.e("PlaybackConnection", "Failed to start MediaController builder", e)
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
        val controller = controller ?: run {
            Log.e("PlaybackConnection", "playSong called but controller is null")
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
            controller.setMediaItems(mediaItems, index, 0)
            controller.prepare()
            controller.play()
            _currentSong.value = song
        } catch (e: Exception) {
            Log.e("PlaybackConnection", "playSong failed", e)
        }
    }

    fun playPause() {
        try {
            controller?.let { if (it.isPlaying) it.pause() else it.play() }
        } catch (e: Exception) {
            Log.e("PlaybackConnection", "playPause failed", e)
        }
    }

    fun next() {
        try { controller?.seekToNextMediaItem() } catch (e: Exception) { Log.e("PlaybackConnection", "next failed", e) }
    }

    fun prev() {
        try { controller?.seekToPreviousMediaItem() } catch (e: Exception) { Log.e("PlaybackConnection", "prev failed", e) }
    }

    fun seekTo(positionMs: Long) {
        try { controller?.seekTo(positionMs) } catch (e: Exception) { Log.e("PlaybackConnection", "seekTo failed", e) }
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
