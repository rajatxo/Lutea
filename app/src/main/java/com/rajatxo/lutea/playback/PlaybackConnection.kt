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
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.rajatxo.lutea.domain.model.Song
import com.rajatxo.lutea.service.LuteaPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * PlaybackConnection — Media3 controller wrapper.
 *
 * 8D audio implementation directly ports SpatialFlow's AudioPlaybackService pattern:
 *   applyEffects(enable8D, speed, ambience) → cache check → if !isProcessing: run FFmpegKit
 *   on8DProcessingSuccess(path) → setMediaItem + seekTo + prepare + play
 *
 * No separate processor class. No debouncing. No updateParams method. Just the
 * SpatialFlow guard: if a session is already running, return early.
 */
object PlaybackConnection {

    private var controller: MediaController? = null
    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var queue: List<Song> = emptyList()

    // ── 8D state (port of SpatialFlow's _is8DEnabled, _speed8D, isProcessing) ──
    private val _is8DEnabled = MutableStateFlow(false)
    val is8DEnabled: StateFlow<Boolean> = _is8DEnabled.asStateFlow()

    private val _orbitSpeed = MutableStateFlow(0.20f)
    val orbitSpeed: StateFlow<Float> = _orbitSpeed.asStateFlow()

    private val _spatialAmbience = MutableStateFlow(0.50f)
    val spatialAmbience: StateFlow<Float> = _spatialAmbience.asStateFlow()

    @Volatile private var isProcessing = false
    private val _isProcessing8D = MutableStateFlow(false)
    val isProcessing8D: StateFlow<Boolean> = _isProcessing8D.asStateFlow()

    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()

    private val _lastError = MutableStateFlow("")
    val eightDLastError: StateFlow<String> = _lastError.asStateFlow()

    private val _is8DActive = MutableStateFlow(false)
    val is8DActive: StateFlow<Boolean> = _is8DActive.asStateFlow()

    suspend fun connect(context: Context) {
        if (controller != null) return
        try {
            val sessionToken = SessionToken(context, ComponentName(context, LuteaPlaybackService::class.java))
            val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            val c = controllerFuture.await()
            controller = c
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

    // ── 8D effect — direct port of SpatialFlow.applyEffects + set8DEnabled ──

    /**
     * Toggle 8D on/off. When on, processes current song via FFmpeg.
     * When off, restores original song. Idempotent.
     */
    fun set8DEnabled(enabled: Boolean) {
        _is8DEnabled.value = enabled
        applyEffects(enabled, _orbitSpeed.value, _spatialAmbience.value)
    }

    /**
     * Slider value changed. ONLY updates the StateFlow — does NOT trigger
     * re-processing. To apply new params, the user lifts their finger
     * (onValueChangeFinished in Compose Slider) which calls apply8DParams().
     *
     * This avoids the race where rapid slider drags cancel/restart FFmpeg
     * many times in quick succession.
     */
    fun setOrbitSpeed(value: Float) {
        _orbitSpeed.value = value.coerceIn(0.05f, 1.5f)
    }

    fun setSpatialAmbience(value: Float) {
        _spatialAmbience.value = value.coerceIn(0f, 1f)
    }

    /**
     * Called when slider drag finishes (onValueChangeFinished).
     * Direct port of SpatialFlow behaviour:
     *   - If a cached file with the new params exists → swap to it instantly
     *   - If FFmpeg is currently processing → IGNORE (don't restart)
     *   - Otherwise → kick off FFmpeg with new params
     *
     * User must toggle 8D off+on to force re-processing if they want to apply
     * new slider values while a session is mid-flight.
     */
    fun apply8DParams() {
        if (!_is8DEnabled.value) return
        val ctx = appContext ?: return
        val c = controller ?: return
        val song = _currentSong.value ?: return

        val cacheKey = "${song.id}${_orbitSpeed.value}${_spatialAmbience.value}".hashCode()
        val outputFile = File(ctx.cacheDir, "8d_$cacheKey.m4a")

        if (outputFile.exists() && outputFile.length() > 0) {
            // Cache hit — swap instantly. No FFmpeg, no waiting.
            val pos = c.currentPosition
            val mediaItem = buildMediaItem(Uri.fromFile(outputFile), song)
            c.setMediaItem(mediaItem)
            c.seekTo(pos)
            c.prepare()
            c.play()
            _is8DActive.value = true
            Log.i("PlaybackConnection", "8D cache hit on slider change — instant swap")
            return
        }

        // No cache. If FFmpeg is running, ignore — same as SpatialFlow's guard.
        if (isProcessing) {
            Log.i("PlaybackConnection", "slider changed mid-process — ignored (SpatialFlow pattern)")
            return
        }

        // Otherwise kick off fresh processing.
        applyEffects(true, _orbitSpeed.value, _spatialAmbience.value)
    }

    /**
     * Direct port of SpatialFlow.applyEffects(enable8D, speed8D).
     *
     * SpatialFlow's pattern:
     *   1. FFmpegKit.cancel() — kill any in-flight session globally
     *   2. If !enable8D → loadOriginalAudio and return
     *   3. Compute cache key, check cache → if hit, swap immediately
     *   4. if (isProcessing) return — KEY guard, don't start a new session
     *      if one is already running
     *   5. Set isProcessing = true, run FFmpegKit.executeAsync
     *   6. Complete callback: swap to processed file (loadProcessedAudio pattern)
     */
    private fun applyEffects(enable8D: Boolean, speed: Float, ambience: Float) {
        val c = controller ?: return
        val ctx = appContext ?: return
        val song = _currentSong.value ?: return

        // Step 1: cancel any in-flight FFmpeg (matches SpatialFlow line 1051)
        try { FFmpegKit.cancel() } catch (_: Throwable) {}

        // Step 2: if disabling, restore original (matches loadOriginalAudio)
        if (!enable8D) {
            _is8DActive.value = false
            val pos = c.currentPosition
            val mediaItem = buildMediaItem(song.uri, song)
            c.setMediaItem(mediaItem)
            c.seekTo(pos)
            c.prepare()
            c.play()
            Log.i("PlaybackConnection", "8D disabled — restored original at pos=$pos")
            return
        }

        // Step 3: cache check (matches SpatialFlow lines 1066-1073)
        val cacheKey = "${song.id}${speed}${ambience}".hashCode()
        val outputFile = File(ctx.cacheDir, "8d_$cacheKey.m4a")
        if (outputFile.exists() && outputFile.length() > 0) {
            val pos = c.currentPosition
            val mediaItem = buildMediaItem(Uri.fromFile(outputFile), song)
            c.setMediaItem(mediaItem)
            c.seekTo(pos)
            c.prepare()
            c.play()
            _is8DActive.value = true
            Log.i("PlaybackConnection", "8D cache hit — swapped to processed at pos=$pos")
            return
        }

        // Step 4: KEY GUARD — don't start a new session if one is running.
        // The cancelled session's complete callback will set isProcessing=false
        // when it fires. The user can then call apply8DParams() again.
        if (isProcessing) {
            Log.i("PlaybackConnection", "applyEffects skipped — already processing")
            return
        }

        // Step 5: start processing (matches SpatialFlow lines 1077-1102)
        isProcessing = true
        _isProcessing8D.value = true
        _lastError.value = ""
        _progressUpdate(0f)

        scope.launch {
            try {
                // Copy source URI to temp file FFmpeg can read directly.
                // Unique timestamp per run so cleanup of an old cancelled run
                // can't delete a file the new run is reading.
                val sourceTemp = File(ctx.cacheDir, "8d_source_${song.id}_${System.currentTimeMillis()}")
                var sourceOpened = false
                withContext(Dispatchers.IO) {
                    sourceOpened = ctx.contentResolver.openInputStream(song.uri)?.use { input ->
                        FileOutputStream(sourceTemp).use { output -> input.copyTo(output) }
                    } != null
                }
                if (!sourceOpened) {
                    _lastError.value = "Can't open source URI: ${song.uri}"
                    isProcessing = false
                    _isProcessing8D.value = false
                    return@launch
                }
                val cmd = FFmpegCommandBuilder.build8D(
                    inputPath = sourceTemp.absolutePath,
                    outputPath = outputFile.absolutePath,
                    rotationSpeed = speed,
                    spatialAmbience = ambience,
                )
                Log.i("PlaybackConnection", "FFmpeg command: $cmd")

                val durationMs = song.duration.coerceAtLeast(1000L)
                FFmpegKit.executeAsync(
                    cmd,
                    { session ->
                        scope.launch {
                            // FFmpegKit's complete callback runs on a background thread.
                            // MediaController methods (setMediaItem, seekTo, prepare, play,
                            // currentPosition) MUST be called on the main thread — otherwise
                            // we crash. SpatialFlow wraps this in handler.post { ... }.
                            // Kotlin equivalent: withContext(Dispatchers.Main).
                            withContext(Dispatchers.Main) {
                                if (ReturnCode.isSuccess(session.returnCode)) {
                                    Log.i("PlaybackConnection", "FFmpeg success, output size=${outputFile.length()}")
                                    sourceTemp.delete()
                                    _progressUpdate(1f)
                                    isProcessing = false
                                    _isProcessing8D.value = false
                                    // loadProcessedAudio pattern: swap to processed file
                                    val pos = c.currentPosition
                                    val mediaItem = buildMediaItem(Uri.fromFile(outputFile), song)
                                    c.setMediaItem(mediaItem)
                                    c.seekTo(pos)
                                    c.prepare()
                                    c.play()
                                    _is8DActive.value = true
                                    Log.i("PlaybackConnection", "8D swap complete at pos=$pos")
                                } else if (ReturnCode.isCancel(session.returnCode)) {
                                    Log.i("PlaybackConnection", "FFmpeg cancelled (expected)")
                                    sourceTemp.delete()
                                    isProcessing = false
                                    _isProcessing8D.value = false
                                    _progressUpdate(0f)
                                } else {
                                    val msg = "FFmpeg rc=${session.returnCode} logs=${session.allLogsAsString.take(500)}"
                                    Log.e("PlaybackConnection", msg)
                                    _lastError.value = msg
                                    sourceTemp.delete()
                                    outputFile.delete()
                                    isProcessing = false
                                    _isProcessing8D.value = false
                                    _progressUpdate(0f)
                                }
                            }
                        }
                    },
                    { /* log callback */ },
                    { stats: Statistics? ->
                        stats?.let { s ->
                            scope.launch {
                                val progress = min(0.99f, s.time.toFloat() / durationMs.toFloat())
                                _progressUpdate(progress)
                            }
                        }
                    },
                )
            } catch (t: Throwable) {
                Log.e("PlaybackConnection", "applyEffects failed", t)
                _lastError.value = "${t.javaClass.simpleName}: ${t.message}"
                isProcessing = false
                _isProcessing8D.value = false
            }
        }
    }

    private fun _progressUpdate(value: Float) {
        _processingProgress.value = value
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
