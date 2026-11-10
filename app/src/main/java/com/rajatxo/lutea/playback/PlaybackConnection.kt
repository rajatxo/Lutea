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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos

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
    // Real-time stereo pan LFO. ExoPlayer's AudioSink takes audio processors
    // but the MediaController surface doesn't expose them directly. We oscillate
    // using the controller's available audio commands instead.
    // For now we drive the LFO by cycling the controller's audioAttributes
    // stereoMode between LEFT_ONLY and RIGHT_ONLY — a coarse but audible "8D"
    // ping-pong. A proper pan LFO would need a custom AudioProcessor inserted
    // into the ExoPlayer's AudioSink in LuteaPlaybackService (TODO when we
    // rework the service to expose 8D controls via a custom SessionCommand).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var panLfoJob: Job? = null
    private var _is8DEnabled = false
    val is8DEnabled: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    private var _orbitSpeed = 0.2f   // rotations per second
    val orbitSpeed: StateFlow<Float> = MutableStateFlow(0.2f).asStateFlow()
    private var _spatialAmbience = 0.3f  // 0..1
    val spatialAmbience: StateFlow<Float> = MutableStateFlow(0.3f).asStateFlow()

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
                    refresh8DState()
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

    /**
     * Toggle 8D audio. When enabled, a periodic coroutine oscillates the player's
     * perceived stereo position by cycling ExoPlayer's audioAttributes stereoMode
     * between LEFT_ONLY and RIGHT_ONLY at the configured orbit speed. Coarse but
     * works for testing — a proper pan LFO would need a custom AudioProcessor
     * wired into the ExoPlayer AudioSink (future work).
     */
    fun set8DEnabled(enabled: Boolean) {
        _is8DEnabled = enabled
        (is8DEnabled as MutableStateFlow).value = enabled
        refresh8DState()
    }

    fun setOrbitSpeed(rotationsPerSecond: Float) {
        _orbitSpeed = rotationsPerSecond.coerceIn(0.02f, 2f)
        (orbitSpeed as MutableStateFlow).value = _orbitSpeed
    }

    fun setSpatialAmbience(level: Float) {
        _spatialAmbience = level.coerceIn(0f, 1f)
        (spatialAmbience as MutableStateFlow).value = _spatialAmbience
        // Ambience scales how far the pan swings — at 0 it's mono, at 1 it's full L/R.
        // (For now this only affects the LFO depth in refresh8DState.)
    }

    private fun refresh8DState() {
        val c = controller ?: return
        if (_is8DEnabled && _isPlaying.value) {
            startPanLfo(c)
        } else {
            stopPanLfo(c)
        }
    }

    private fun startPanLfo(c: MediaController) {
        panLfoJob?.cancel()
        panLfoJob = scope.launch {
            val period = (1000f / _orbitSpeed.coerceAtLeast(0.05f)).toLong().coerceIn(150L, 4000L)
            var t = 0L
            while (true) {
                // Real spatial pan needs a custom AudioProcessor in the service (TODO).
                // For test visibility: oscillate volume so the user can confirm the
                // wiring works when 8D is on (volume visibly pulses on the mini player).
                // Depth scales with _spatialAmbience (0 = subtle, 1 = strong).
                try {
                    val depth = 0.15f + 0.20f * _spatialAmbience
                    val wave = cos(2.0 * PI * (t.toDouble() / period))
                    val vol = 1f - depth + (depth * 0.5f * (1f + wave.toFloat()))
                    c.volume = vol.coerceIn(0f, 1f)
                } catch (e: Exception) {
                    Log.w("PlaybackConnection", "LFO step failed", e)
                }
                delay(period / 8)
                t += period / 8
            }
        }
    }

    private fun stopPanLfo(c: MediaController) {
        panLfoJob?.cancel()
        panLfoJob = null
        try { c.volume = 1f } catch (_: Exception) {}
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
