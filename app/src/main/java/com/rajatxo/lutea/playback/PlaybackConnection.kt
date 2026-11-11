package com.rajatxo.lutea.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.rajatxo.lutea.domain.model.Song
import com.rajatxo.lutea.service.LuteaPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    // ── 8D effect state ──
    // The 8D effect uses FFmpeg to pre-process the current song with an
    // apulsator + aecho filter chain (copied from SpatialFlow's approach),
    // producing a temp file the player switches to. When toggled off, we
    // restore the original song.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var eightDProcessor: Effect8DProcessor? = null

    private val _is8DEnabled = MutableStateFlow(false)
    val is8DEnabled: StateFlow<Boolean> = _is8DEnabled.asStateFlow()

    private val _orbitSpeed = MutableStateFlow(0.10f)   // rotations per second
    val orbitSpeed: StateFlow<Float> = _orbitSpeed.asStateFlow()

    private val _spatialAmbience = MutableStateFlow(0.50f)   // 0..1
    val spatialAmbience: StateFlow<Float> = _spatialAmbience.asStateFlow()

    private val _isProcessing8D = MutableStateFlow(false)
    val isProcessing8D: StateFlow<Boolean> = _isProcessing8D.asStateFlow()

    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()

    // The original media item that was playing before 8D was toggled on, so we
    // can restore it when 8D is toggled off.
    private var pre8DPosition: Long = 0L
    private var pre8DSong: Song? = null

    suspend fun connect(context: Context) {
        if (controller != null) return
        try {
            val sessionToken = SessionToken(context, ComponentName(context, LuteaPlaybackService::class.java))
            val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            val c = controllerFuture.await()
            controller = c

            // Initialize the 8D processor now that we have a context.
            if (eightDProcessor == null) {
                eightDProcessor = Effect8DProcessor(
                    context = context.applicationContext,
                    scope = scope,
                    onProcessedUri = { uri -> swapTo8DFile(uri) },
                    onRestoreOriginal = { restoreOriginal() },
                )
            }

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
                    // Re-apply 8D if it's on and the song changed
                    if (_is8DEnabled.value) {
                        _currentSong.value?.let { song ->
                            eightDProcessor?.enable(song, _orbitSpeed.value, _spatialAmbience.value)
                        }
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

    fun set8DEnabled(enabled: Boolean) {
        _is8DEnabled.value = enabled
        if (enabled) {
            // Save current playback position so we can restore it after FFmpeg swap
            val c = controller
            val song = _currentSong.value
            if (c != null && song != null) {
                pre8DPosition = c.currentPosition
                pre8DSong = song
                eightDProcessor?.enable(song, _orbitSpeed.value, _spatialAmbience.value)
            }
        } else {
            eightDProcessor?.disable()
        }
    }

    fun setOrbitSpeed(rotationsPerSecond: Float) {
        _orbitSpeed.value = rotationsPerSecond.coerceIn(0.05f, 1.5f)
        if (_is8DEnabled.value) {
            eightDProcessor?.updateParams(_orbitSpeed.value, _spatialAmbience.value)
        }
    }

    fun setSpatialAmbience(level: Float) {
        _spatialAmbience.value = level.coerceIn(0f, 1f)
        if (_is8DEnabled.value) {
            eightDProcessor?.updateParams(_orbitSpeed.value, _spatialAmbience.value)
        }
    }

    private fun swapTo8DFile(uri: Uri) {
        val c = controller ?: return
        val song = pre8DSong ?: return
        try {
            // Build a new MediaItem pointing at the processed temp file.
            // Keep the same metadata (title/artist/album art) so the UI doesn't jump.
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMediaId(song.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(song.albumArtUri)
                        .build()
                )
                .build()
            // Replace just the current item in the queue with the 8D version,
            // then seek to the saved position so playback continues from where
            // it left off.
            c.setMediaItem(mediaItem, pre8DPosition)
            c.prepare()
            c.play()
        } catch (e: Exception) {
            Log.e("PlaybackConnection", "swapTo8DFile failed", e)
        }
    }

    private fun restoreOriginal() {
        val c = controller ?: return
        val song = pre8DSong ?: return
        try {
            // Build a MediaItem pointing at the original URI
            val mediaItem = MediaItem.Builder()
                .setUri(song.uri)
                .setMediaId(song.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(song.albumArtUri)
                        .build()
                )
                .build()
            c.setMediaItem(mediaItem, pre8DPosition)
            c.prepare()
            c.play()
        } catch (e: Exception) {
            Log.e("PlaybackConnection", "restoreOriginal failed", e)
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
