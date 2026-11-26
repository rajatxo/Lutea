package com.rajatxo.lutea.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import com.rajatxo.lutea.data.store.PlaybackStatsStore
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
/** Loop / repeat mode for the queue. Cycles OFF -> ALL -> ONE. */
enum class LoopMode { OFF, ALL, ONE }

@UnstableApi
object PlaybackConnection {

    private const val TAG = "PlaybackConnection"

    private var controller: MediaController? = null
    private var appContext: Context? = null
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

    /** Read-only snapshot of the current play queue (for the queue page). */
    val currentQueue: List<Song> get() = queue

    /** Index of the currently-playing song in [currentQueue], or -1. */
    val currentIndex: Int get() = _currentSong.value?.let { song ->
        queue.indexOfFirst { it.id == song.id }
    } ?: -1

    // ── Loop / repeat ──
    // Default matches the service's initial REPEAT_MODE_ALL, so the button and the
    // player never disagree on first launch. Persisted in lutea_player/loop_mode.
    private val _loopMode = MutableStateFlow(LoopMode.ALL)
    val loopMode: StateFlow<LoopMode> = _loopMode.asStateFlow()

    // Last playback error timestamp — used for the auto-skip cooldown in
    // onPlayerErrorChanged. Prevents the rapid-skipping loop where every song
    // in the queue errors out and the handler keeps calling seekToNextMediaItem.
    private var lastErrorTimeMs: Long = 0L

    // ── 8D state ──
    // Slider values stored as Int 1..25 (matches the numbers around the sphere).
    // Converted to rps/width when written to the Spatial8DProcessor.
    private val _is8DEnabled = MutableStateFlow(false)
    val is8DEnabled: StateFlow<Boolean> = _is8DEnabled.asStateFlow()

    // BALANCED preset = 13 for both. Default. Loaded from prefs on first access.
    private val _orbitSlider = MutableStateFlow(13)
    val orbitSlider: StateFlow<Int> = _orbitSlider.asStateFlow()

    private val _ambienceSlider = MutableStateFlow(13)
    val ambienceSlider: StateFlow<Int> = _ambienceSlider.asStateFlow()

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
        controller?.let { existing ->
            syncFromController(existing)
            return
        }
        try {
            val app = context.applicationContext
            appContext = app
            // Restore the last 8D session: on/off plus both slider values.
            val prefs = app.getSharedPreferences("lutea_8d", android.content.Context.MODE_PRIVATE)
            _orbitSlider.value = prefs.getInt("orbit_slider", 13).coerceIn(1, 25)
            _ambienceSlider.value = prefs.getInt("ambience_slider", 13).coerceIn(1, 25)
            val savedOn = prefs.getBoolean("enabled", false)
            _is8DEnabled.value = savedOn
            _is8DActive.value = savedOn
            LuteaAudioProcessors.spatial8D.set8DEnabled(savedOn)
            LuteaAudioProcessors.spatial8D.setOrbitHz(sliderToRps(_orbitSlider.value))
            LuteaAudioProcessors.spatial8D.setSpatialWidth(sliderToWidth(_ambienceSlider.value))

            // Restore the last loop mode before the controller comes up, then push it.
            val playerPrefs = app.getSharedPreferences("lutea_player", android.content.Context.MODE_PRIVATE)
            _loopMode.value = runCatching {
                LoopMode.valueOf(playerPrefs.getString("loop_mode", LoopMode.ALL.name) ?: LoopMode.ALL.name)
            }.getOrDefault(LoopMode.ALL)

            val sessionToken = SessionToken(app, ComponentName(app, LuteaPlaybackService::class.java))
            val c = MediaController.Builder(app, sessionToken).buildAsync().await()
            controller = c
            c.repeatMode = _loopMode.value.toPlayerRepeatMode()

            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) c.volume = 1f
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val songId = mediaItem?.mediaId?.toLongOrNull()
                    val queued = songId?.let { id -> queue.firstOrNull { it.id == id } }
                    // Flush the outgoing song's play segment before swapping.
                    flushPlayTime()
                    playingSongId = songId
                    // After the activity (and sometimes the whole UI process) is
                    // recreated, the controller can reconnect to a service that
                    // already has a song loaded without replaying the transition
                    // event to us. Rebuild the display model from the media item
                    // so the mini player can restore immediately.
                    _currentSong.value = queued
                        ?: mediaItem?.let { mediaItemToSong(it, c.duration) }
                    _positionMs.value = 0L
                    _durationMs.value = 0L
                    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                        if (!c.isPlaying) c.play()
                    }
                    // Real-time 8D — no per-song processing needed. The processor
                    // keeps running across song changes because it's in the AudioSink.
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        syncFromController(c)
                    }
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

            // The service may already be playing a song when the activity is
            // rebuilt after being removed from recents. The initial transition
            // event is not guaranteed, so seed the UI state from the live
            // controller before returning.
            syncFromController(c)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to playback service", e)
        }
    }

    /**
     * Pull the current song/position from a connected controller. This is the
     * restore path for a service that survived while the activity was gone.
     */
    private fun syncFromController(c: MediaController) {
        _isPlaying.value = c.isPlaying
        _positionMs.value = c.currentPosition.coerceAtLeast(0L)
        val duration = c.duration.coerceAtLeast(0L)
        _durationMs.value = duration

        val item = c.currentMediaItem
        if (item == null) {
            _currentSong.value = null
            return
        }
        val songId = item.mediaId.toLongOrNull()
        val queued = songId?.let { id -> queue.firstOrNull { it.id == id } }
        _currentSong.value = queued ?: mediaItemToSong(item, duration)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8D public API — instant. No FFmpeg, no swap, no cache.
    // ─────────────────────────────────────────────────────────────────────────

    /** Toggle 8D. Writes directly to the AudioProcessor. */
    fun set8DEnabled(enabled: Boolean) {
        _is8DEnabled.value = enabled
        LuteaAudioProcessors.spatial8D.set8DEnabled(enabled)
        _is8DActive.value = enabled
        // Apply current slider values to the processor
        LuteaAudioProcessors.spatial8D.setOrbitHz(sliderToRps(_orbitSlider.value))
        LuteaAudioProcessors.spatial8D.setSpatialWidth(sliderToWidth(_ambienceSlider.value))
        // Persist, so the next launch starts in the same state
        appContext?.getSharedPreferences("lutea_8d", android.content.Context.MODE_PRIVATE)
            ?.edit()?.putBoolean("enabled", enabled)?.apply()
    }

    /** Slider drag: writes to the processor in real-time. Value is 1..25. */
    fun setOrbitSpeed(slider: Int) {
        val clamped = slider.coerceIn(1, 25)
        _orbitSlider.value = clamped
        LuteaAudioProcessors.spatial8D.setOrbitHz(sliderToRps(clamped))
        // Persist
        appContext?.getSharedPreferences("lutea_8d", android.content.Context.MODE_PRIVATE)
            ?.edit()?.putInt("orbit_slider", clamped)?.apply()
    }

    fun setSpatialAmbience(slider: Int) {
        val clamped = slider.coerceIn(1, 25)
        _ambienceSlider.value = clamped
        LuteaAudioProcessors.spatial8D.setSpatialWidth(sliderToWidth(clamped))
        // Persist
        appContext?.getSharedPreferences("lutea_8d", android.content.Context.MODE_PRIVATE)
            ?.edit()?.putInt("ambience_slider", clamped)?.apply()
    }

    /** Apply a preset — sets slider values for both orbit and ambience. */
    fun set8DPreset(p: Effect8DPreset) {
        _preset.value = p
        _orbitSlider.value = p.orbitSlider
        _ambienceSlider.value = p.ambienceSlider
        LuteaAudioProcessors.spatial8D.setOrbitHz(sliderToRps(p.orbitSlider))
        LuteaAudioProcessors.spatial8D.setSpatialWidth(sliderToWidth(p.ambienceSlider))
        // Persist
        appContext?.getSharedPreferences("lutea_8d", android.content.Context.MODE_PRIVATE)
            ?.edit()
            ?.putInt("orbit_slider", p.orbitSlider)
            ?.putInt("ambience_slider", p.ambienceSlider)
            ?.apply()
    }

    /** No-op with real-time processing. Kept for UI compatibility. */
    fun apply8DParams() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Playback controls
    // ─────────────────────────────────────────────────────────────────────────

    // ── Per-song listening-time accounting ──
    // Tracks wall-clock time actually spent playing the current song, so it can
    // be flushed into PlaybackStatsStore. We sample in pollPosition() (called
    // every 500ms by FullPlayer/LyricsScreen) and reset/flush whenever the
    // current song changes.
    private var playingSongId: Long? = null
    private var playingSinceMs: Long = 0L   // wall-clock ms when current play segment started

    /** Flush any accumulated play time for the current segment into the stats store. */
    private fun flushPlayTime() {
        val id = playingSongId ?: return
        if (playingSinceMs == 0L) return
        val now = System.currentTimeMillis()
        val delta = now - playingSinceMs
        if (delta > 0L) {
            // Cap a single segment at 10 minutes so a paused-but-not-detected
            // state can't inflate a song's count by hours.
            PlaybackStatsStore.addPlayedMs(id, delta.coerceAtMost(600_000L))
        }
        playingSinceMs = 0L
    }

    fun pollPosition() {
        controller?.let { c ->
            _positionMs.value = c.currentPosition.coerceAtLeast(0L)
            _durationMs.value = c.duration.coerceAtLeast(0L)
            // Listening-time accounting: while playing, flush the open segment
            // EVERY tick (~500ms) so the per-song badge ticks up live — not
            // only on pause/skip. The segment timer restarts after each flush.
            val id = _currentSong.value?.id
            if (c.isPlaying && id != null) {
                if (playingSongId != id) {
                    // Song changed since last tick — flush the previous one.
                    flushPlayTime()
                    playingSongId = id
                }
                if (playingSinceMs == 0L) playingSinceMs = System.currentTimeMillis()
                // Live flush: write whatever has accumulated this segment and
                // restart the clock so the next tick adds the next ~500ms.
                flushPlayTime()
                playingSinceMs = System.currentTimeMillis()
            } else {
                // Paused or no song: close the open segment.
                if (playingSinceMs != 0L) flushPlayTime()
                playingSongId = id
            }
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

    /**
     * Append a single song to the bottom of the current queue without
     * interrupting playback. If nothing is playing, it starts the song.
     */
    fun addToQueue(song: Song) {
        val c = controller ?: run {
            Log.e(TAG, "addToQueue: controller is null")
            return
        }
        try {
            if (queue.isEmpty() && c.mediaItemCount == 0) {
                playSong(song, listOf(song))
                return
            }
            queue = queue + song
            c.addMediaItem(buildMediaItem(song.uri, song))
        } catch (e: Exception) {
            Log.e(TAG, "addToQueue failed", e)
        }
    }

    /**
     * Jump to a specific song in the CURRENT queue by index (used by the queue
     * page: tapping a row plays that song without rebuilding the queue).
     */
    fun playQueueItem(index: Int) {
        val c = controller ?: run { return }
        val q = queue
        if (index !in q.indices) return
        try {
            c.seekToDefaultPosition(index)
            c.prepare()
            c.play()
            _currentSong.value = q[index]
        } catch (e: Exception) {
            Log.e(TAG, "playQueueItem failed", e)
        }
    }

    fun playPause() {
        try { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
        catch (e: Exception) { Log.e(TAG, "playPause failed", e) }
    }

    /** Stop playback and clear the song so the mini player goes away. */
    fun stop() {
        try {
            flushPlayTime()
            controller?.stop()
            controller?.clearMediaItems()
        } catch (e: Exception) { Log.e(TAG, "stop failed", e) }
        _isPlaying.value = false
        _currentSong.value = null
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

    /** Loop button: OFF -> ALL -> ONE -> OFF. */
    fun cycleLoopMode() {
        val next = when (_loopMode.value) {
            LoopMode.OFF -> LoopMode.ALL
            LoopMode.ALL -> LoopMode.ONE
            LoopMode.ONE -> LoopMode.OFF
        }
        setLoopMode(next)
    }

    fun setLoopMode(mode: LoopMode) {
        _loopMode.value = mode
        try { controller?.repeatMode = mode.toPlayerRepeatMode() }
        catch (e: Exception) { Log.e(TAG, "setLoopMode failed", e) }
        appContext?.getSharedPreferences("lutea_player", android.content.Context.MODE_PRIVATE)
            ?.edit()?.putString("loop_mode", mode.name)?.apply()
    }

    private fun LoopMode.toPlayerRepeatMode(): Int = when (this) {
        LoopMode.OFF -> Player.REPEAT_MODE_OFF
        LoopMode.ALL -> Player.REPEAT_MODE_ALL
        LoopMode.ONE -> Player.REPEAT_MODE_ONE
    }

    fun disconnect() {
        controller?.release()
        controller = null
        _isPlaying.value = false
        _currentSong.value = null
        _is8DActive.value = false
    }

    /** Rebuild just enough of a [Song] to render the mini/full player after a restore. */
    private fun mediaItemToSong(item: MediaItem, fallbackDurationMs: Long = 0L): Song? {
        val metadata = item.mediaMetadata
        val title = metadata.title?.toString().orEmpty()
        if (title.isBlank()) return null
        val storedDuration = metadata.durationMs ?: 0L
        return Song(
            id = item.mediaId.toLongOrNull() ?: 0L,
            title = title,
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(),
            duration = if (storedDuration > 0L) storedDuration else fallbackDurationMs.coerceAtLeast(0L),
            uri = item.localConfiguration?.uri ?: Uri.EMPTY,
            albumArtUri = metadata.artworkUri,
            dateAdded = 0L,
        )
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
                .setDurationMs(song.duration.coerceAtLeast(0L))
                .build()
        )
        .build()
}
