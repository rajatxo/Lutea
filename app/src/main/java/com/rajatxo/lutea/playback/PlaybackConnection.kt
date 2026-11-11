package com.rajatxo.lutea.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
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

/**
 * PlaybackConnection — Media3 MediaController wrapper.
 *
 * 8D audio: NO FFmpeg. SpatialFlow's pattern ported — a custom AudioProcessor
 * (Spatial8DProcessor) is wired into the ExoPlayer's AudioSink by the service.
 * The processor runs in the audio thread, modulating L/R volume per-sample at
 * the configured Hz. Toggling 8D just flips the processor's enabled flag —
 * instant, no file processing, no cache, no swap, no FFmpeg.
 *
 * Volume normalization isn't done here — SonicAudioProcessor is in the chain
 * for that (Media3's built-in). The custom AudioSink built by LuteaPlaybackService
 * handles all audio effects at playback time.
 *
 * Slider changes (orbitSpeed, spatialAmbience) write directly to the processor
 * — also instant, no re-processing. Volume stays constant because there's no
 * AAC re-encoding step.
 *
 * Adaptations for Lutea (MediaController over IPC, not direct ExoPlayer):
 *  - We can't touch the AudioProcessor via the MediaController surface. Both
 *    sides reference the LuteaAudioProcessors singleton — service reads from
 *    it when building the AudioSink, controller writes to it when the user
 *    toggles 8D. Works because MediaSessionService runs in the same process
 *    as the UI.
 *  - No more setMediaItem swap dance — the original song stays loaded, the
 *    processor runs on the live audio stream.
 *  - No more cache, no more temp files, no more source copying.
 */
@UnstableApi
object PlaybackConnection {

    private const val TAG = "PlaybackConnection"

    private var controller: MediaController? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var queue: List<Song> = emptyList()

    // Last playback error timestamp — used for the auto-skip cooldown in
    // onPlayerErrorChanged. Prevents the rapid-skipping loop where every song
    // in the queue errors out and the handler keeps calling seekToNextMediaItem.
    private var lastErrorTimeMs: Long = 0L

    // ── 8D state ──
    // All three just write to the LuteaAudioProcessors singleton — the audio
    // thread reads from it on the next sample. Instant, no FFmpeg, no swap.
    private val _is8DEnabled = MutableStateFlow(false)
    val is8DEnabled: StateFlow<Boolean> = _is8DEnabled.asStateFlow()

    // SpatialFlow's exact defaults: 0.2 Hz, width 0.75 (no slider in their app).
    private val _orbitSpeed = MutableStateFlow(0.20f)
    val orbitSpeed: StateFlow<Float> = _orbitSpeed.asStateFlow()

    // SpatialFlow's apulsator width is fixed at 0.75 — that's our default too.
    private val _spatialAmbience = MutableStateFlow(0.75f)
    val spatialAmbience: StateFlow<Float> = _spatialAmbience.asStateFlow()

    // Current preset — Balanced is default, matches SpatialFlow exactly.
    private val _preset = MutableStateFlow(Effect8DPreset.BALANCED)
    val preset: StateFlow<Effect8DPreset> = _preset.asStateFlow()

    // With real-time processing, there's no "Processing 8D" state — the effect
    // is on the instant the flag flips. Kept for UI compatibility (EffectVisual.kt
    // still reads these) but they're always false / 0 / empty.
    val isProcessing8D: StateFlow<Boolean> = MutableStateFlow(false)
    val processingProgress: StateFlow<Float> = MutableStateFlow(0f)
    val eightDLastError: StateFlow<String> = MutableStateFlow("")

    // True when 8D is on (mirrors _is8DEnabled for UI compatibility).
    private val _is8DActive = MutableStateFlow(false)
    val is8DActive: StateFlow<Boolean> = _is8DActive.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────────
    // Connection
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun connect(context: Context) {
        if (controller != null) return
        try {
            val app = context.applicationContext
            val sessionToken = SessionToken(app, ComponentName(app, LuteaPlaybackService::class.java))
            val c = MediaController.Builder(app, sessionToken).buildAsync().await()
            controller = c

            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) c.volume = 1f
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val songId = mediaItem?.mediaId?.toLongOrNull()
                    _currentSong.value = queue.firstOrNull { it.id == songId }
                    _positionMs.value = 0L
                    _durationMs.value = 0L
                    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                        if (!c.isPlaying) c.play()
                    }
                    // Real-time 8D — no per-song processing needed. The processor
                    // keeps running across song changes because it's in the AudioSink.
                }

                override fun onPlayerErrorChanged(error: PlaybackException?) {
                    if (error != null) {
                        Log.e(TAG, "Playback error: ${error.errorCodeName}", error)
                        // Cooldown: only auto-skip if the last error was > 3 seconds ago.
                        // Without this, a chain of failing songs (e.g., unsupported encoding,
                        // corrupted file, missing permission) creates a rapid infinite loop
                        // of skip → error → skip → error → ... through the entire queue.
                        // The user can always manually hit next.
                        val now = System.currentTimeMillis()
                        if (now - lastErrorTimeMs > 3000) {
                            lastErrorTimeMs = now
                            try { c.seekToNextMediaItem(); c.prepare(); c.play() }
                            catch (e: Exception) { Log.e(TAG, "Skip after error failed", e) }
                        } else {
                            Log.w(TAG, "Rapid playback errors — stopping auto-skip to prevent queue thrash")
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to playback service", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8D public API — instant. No FFmpeg, no swap, no cache.
    // ─────────────────────────────────────────────────────────────────────────

    /** Toggle 8D. Writes directly to the AudioProcessor — takes effect on the
     *  next audio sample. No file processing, no cache miss, no swap. */
    fun set8DEnabled(enabled: Boolean) {
        _is8DEnabled.value = enabled
        LuteaAudioProcessors.spatial8D.set8DEnabled(enabled)
        _is8DActive.value = enabled
    }

    /** Slider drag: updates the processor's Hz in real-time. Per-drag-tick writes
     *  are fine — the processor reads @Volatile fields, no allocation.
     *  Value is in rps internally (0.01..1.0). UI converts slider 1-100 to rps. */
    fun setOrbitSpeed(value: Float) {
        val clamped = value.coerceIn(0.01f, 1.0f)
        _orbitSpeed.value = clamped
        LuteaAudioProcessors.spatial8D.setOrbitHz(clamped)
    }

    fun setSpatialAmbience(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _spatialAmbience.value = clamped
        LuteaAudioProcessors.spatial8D.setSpatialWidth(clamped)
    }

    /**
     * Apply a preset — Low / Balanced / High.
     *
     * Preset values are tuned to match SpatialFlow's behaviour at each intensity:
     *
     *  - LOW: 0.10 Hz, width 0.55 — subtle rotation, barely noticeable. Good for
     *    background listening or long sessions.
     *  - BALANCED: 0.20 Hz, width 0.75 — SpatialFlow's exact fixed values.
     *    This is the default.
     *  - HIGH: 0.35 Hz, width 1.0 — fast rotation with full L/R sweep. Most
     *    dramatic effect, can be nauseating for long periods.
     *
     * Setting a preset overrides the slider values. User can still fine-tune via
     * sliders after — preset just sets a starting point.
     */
    fun set8DPreset(p: Effect8DPreset) {
        _preset.value = p
        _orbitSpeed.value = p.orbitHz
        _spatialAmbience.value = p.width
        LuteaAudioProcessors.spatial8D.setOrbitHz(p.orbitHz)
        LuteaAudioProcessors.spatial8D.setSpatialWidth(p.width)
    }

    /** No-op with real-time processing. Kept for UI compatibility
     *  (EffectVisual.kt calls it on slider release). */
    fun apply8DParams() {
        // Nothing to do — setOrbitSpeed / setSpatialAmbience already wrote
        // to the processor live during the drag.
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Playback controls
    // ─────────────────────────────────────────────────────────────────────────

    fun pollPosition() {
        controller?.let { c ->
            _positionMs.value = c.currentPosition.coerceAtLeast(0L)
            _durationMs.value = c.duration.coerceAtLeast(0L)
        }
    }

    fun playSong(song: Song, allSongs: List<Song>) {
        queue = allSongs
        val c = controller ?: run {
            Log.e(TAG, "playSong: controller is null")
            return
        }
        try {
            val mediaItems = allSongs.map { buildMediaItem(it.uri, it) }
            val index = allSongs.indexOf(song).coerceAtLeast(0)
            c.setMediaItems(mediaItems, index, 0)
            c.prepare()
            c.play()
            _currentSong.value = song
        } catch (e: Exception) {
            Log.e(TAG, "playSong failed", e)
        }
    }

    fun playPause() {
        try { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
        catch (e: Exception) { Log.e(TAG, "playPause failed", e) }
    }

    fun next() {
        try { controller?.seekToNextMediaItem() }
        catch (e: Exception) { Log.e(TAG, "next failed", e) }
    }

    fun prev() {
        try { controller?.seekToPreviousMediaItem() }
        catch (e: Exception) { Log.e(TAG, "prev failed", e) }
    }

    fun seekTo(positionMs: Long) {
        try { controller?.seekTo(positionMs) }
        catch (e: Exception) { Log.e(TAG, "seekTo failed", e) }
    }

    fun disconnect() {
        controller?.release()
        controller = null
        _isPlaying.value = false
        _currentSong.value = null
        _is8DActive.value = false
    }

    private fun buildMediaItem(uri: Uri, song: Song): MediaItem = MediaItem.Builder()
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
}
