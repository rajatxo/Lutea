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
    // LAZY: processor is only created the first time the user toggles 8D on.
    // Creating it in connect() would force FFmpegKit native library load on
    // every app launch — and if that native load crashes, the whole app dies.
    private var eightDProcessor: Effect8DProcessor? = null
    private var appContext: Context? = null

    private val _is8DEnabled = MutableStateFlow(false)
    val is8DEnabled: StateFlow<Boolean> = _is8DEnabled.asStateFlow()

    private val _orbitSpeed = MutableStateFlow(0.10f)   // rotations per second
    val orbitSpeed: StateFlow<Float> = _orbitSpeed.asStateFlow()

    private val _spatialAmbience = MutableStateFlow(0.50f)   // 0..1
    val spatialAmbience: StateFlow<Float> = _spatialAmbience.asStateFlow()

    // 8D processing state — delegated to the processor when it exists, else a
    // stub MutableStateFlow. The UI reads these so it can show progress + errors.
    private val stubProcessing = MutableStateFlow(false)
    private val stubProgress = MutableStateFlow(0f)
    private val stubError = MutableStateFlow("")
    val isProcessing8D: StateFlow<Boolean> get() = eightDProcessor?.isProcessing ?: stubProcessing
    val processingProgress: StateFlow<Float> get() = eightDProcessor?.progress ?: stubProgress
    val eightDLastError: StateFlow<String> get() = eightDProcessor?.lastError ?: stubError

    // True when 8D is enabled AND the player has swapped to the processed file.
    // Lets the UI show a clear "8D AUDIO ACTIVE" indicator so the user knows
    // the effect is actually playing (vs still processing).
    private val _is8DActive = MutableStateFlow(false)
    val is8DActive: StateFlow<Boolean> = _is8DActive.asStateFlow()

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
            // Stash app context for lazy 8D processor creation later.
            appContext = context.applicationContext

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
                    // If 8D is enabled and a new song started, process it.
                    // Skip if we just swapped to an 8D-processed file ourselves
                    // (reason would be REPEAT_MODE_CHANGED or similar). The swap
                    // path sets _currentSong.value BEFORE calling setMediaItem,
                    // so this re-trigger only fires on natural song transitions.
                    if (_is8DEnabled.value && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        _currentSong.value?.let { song ->
                            pre8DPosition = 0L  // start of new song
                            pre8DSong = song
                            eightDProcessor?.enable(song, _orbitSpeed.value, _spatialAmbience.value, 0L)
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
        val c = controller ?: return
        if (enabled) {
            // Lazy-init the processor on first use.
            val ctx = appContext ?: return
            if (eightDProcessor == null) {
                eightDProcessor = Effect8DProcessor(
                    context = ctx,
                    scope = scope,
                    onProcessedUri = { uri, position -> swapTo8DFile(uri, position) },
                    onRestoreOriginal = { position -> restoreOriginal(position) },
                )
            }
            // If a song is playing right now, kick off processing immediately.
            // If nothing's playing, just stash the flag — when the user starts a song,
            // onMediaItemTransition (below) will trigger 8D processing automatically.
            val song = _currentSong.value
            if (song != null && c.isPlaying) {
                pre8DPosition = c.currentPosition
                pre8DSong = song
                eightDProcessor?.enable(song, _orbitSpeed.value, _spatialAmbience.value, pre8DPosition)
            }
        } else {
            // Toggle off — restore the original song if we had one
            val pos = c.currentPosition
            eightDProcessor?.disable(pos)
        }
    }

    fun setOrbitSpeed(rotationsPerSecond: Float) {
        _orbitSpeed.value = rotationsPerSecond.coerceIn(0.05f, 1.5f)
        scheduleReprocess()
    }

    fun setSpatialAmbience(level: Float) {
        _spatialAmbience.value = level.coerceIn(0f, 1f)
        scheduleReprocess()
    }

    // Debounce slider changes — when user drags, many value updates fire in
    // rapid succession. Don't re-run FFmpeg on each one. Wait 500ms after the
    // last drag, then process once with the final values.
    private var reprocessJob: kotlinx.coroutines.Job? = null
    private fun scheduleReprocess() {
        val c = controller ?: return
        val song = _currentSong.value ?: return
        if (!_is8DEnabled.value) return
        reprocessJob?.cancel()
        reprocessJob = scope.launch {
            kotlinx.coroutines.delay(500)
            val pos = c.currentPosition
            eightDProcessor?.updateParams(song, _orbitSpeed.value, _spatialAmbience.value, pos)
        }
    }

    private fun swapTo8DFile(uri: Uri, position: Long) {
        val c = controller ?: run {
            Log.e("PlaybackConnection", "swapTo8DFile: controller is null")
            return
        }
        val song = pre8DSong ?: _currentSong.value ?: run {
            Log.e("PlaybackConnection", "swapTo8DFile: no current song")
            return
        }
        Log.i("PlaybackConnection", "swapTo8DFile: uri=$uri song=${song.title} position=$position")
        try {
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
            // EXACT SpatialFlow pattern (loadProcessedAudio):
            //   player.setMediaItem(mediaItem)
            //   player.seekTo(currentPos)
            //   player.prepare()
            //   play()
            // NO pause before setMediaItem — setMediaItem already resets the player.
            // Pausing first caused a race where seekTo happened before the new media
            // item was loaded, and ExoPlayer saw the previous media item's duration
            // (2s) which clipped playback to that range.
            c.setMediaItem(mediaItem)
            c.seekTo(position)
            c.prepare()
            c.play()
            _is8DActive.value = true
            Log.i("PlaybackConnection", "swapTo8DFile: swap complete, playing 8D file at pos=$position")
        } catch (e: Exception) {
            Log.e("PlaybackConnection", "swapTo8DFile failed", e)
        }
    }

    private fun restoreOriginal(position: Long) {
        val c = controller ?: run {
            Log.e("PlaybackConnection", "restoreOriginal: controller is null")
            return
        }
        val song = pre8DSong ?: _currentSong.value ?: run {
            Log.e("PlaybackConnection", "restoreOriginal: no current song")
            return
        }
        Log.i("PlaybackConnection", "restoreOriginal: song=${song.title} position=$position")
        try {
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
            c.setMediaItem(mediaItem)
            c.seekTo(position)
            c.prepare()
            c.play()
            _is8DActive.value = false
            Log.i("PlaybackConnection", "restoreOriginal: restore complete")
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
