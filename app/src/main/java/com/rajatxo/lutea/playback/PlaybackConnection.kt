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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.rajatxo.lutea.domain.model.Song
import com.rajatxo.lutea.service.LuteaPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.min

/**
 * PlaybackConnection — Media3 MediaController wrapper.
 *
 * 8D audio = port of SpatialFlow's AudioPlaybackService.applyEffects():
 *   cancel in-flight FFmpeg -> cache lookup -> FFmpegKit.executeAsync(
 *   apulsator + aecho + stereotools + alimiter) -> swap processed file in at the same
 *   position -> volume = 1.0f.
 *
 * Adaptations for Lutea (MediaController, not direct ExoPlayer access):
 *  - All controller calls happen on the main thread (scope = Dispatchers.Main.immediate).
 *  - The swap keeps the whole playlist (setMediaItems with the same queue, only the current
 *    item's URI replaced) so next/prev/repeat keep working. SpatialFlow holds a single item
 *    in its player, Lutea holds the full queue.
 *  - Processed items are tagged via MediaMetadata.extras (MediaItem.localConfiguration is
 *    not reliably visible on the controller side), so we always know whether the item
 *    currently loaded is the original or the 8D file.
 *  - Auto-apply: on every media item transition (auto, next/prev, tapping another song)
 *    while 8D is ON and the new item is not already an 8D file, processing starts for it.
 *  - Stale-callback safety: each run has a token; callbacks from a cancelled run never touch
 *    state or the player. Output is written to a temp file and renamed on success, so a
 *    cancelled run can never leave a truncated file that looks like a cache hit.
 *
 * Public API is unchanged, so EffectVisual.kt needs no edits.
 */
object PlaybackConnection {

    private const val TAG = "PlaybackConnection"
    private const val EXTRA_8D_FILE = "lutea_8d_file"
    private const val CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000

    private var controller: MediaController? = null
    private var appContext: Context? = null

    // Main dispatcher: MediaController must be touched on the main thread.
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

    // ── 8D state ──
    private val _is8DEnabled = MutableStateFlow(false)
    val is8DEnabled: StateFlow<Boolean> = _is8DEnabled.asStateFlow()

    private val _orbitSpeed = MutableStateFlow(0.20f)
    val orbitSpeed: StateFlow<Float> = _orbitSpeed.asStateFlow()

    private val _spatialAmbience = MutableStateFlow(0.50f)
    val spatialAmbience: StateFlow<Float> = _spatialAmbience.asStateFlow()

    private val _isProcessing8D = MutableStateFlow(false)
    val isProcessing8D: StateFlow<Boolean> = _isProcessing8D.asStateFlow()

    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()

    private val _lastError = MutableStateFlow("")
    val eightDLastError: StateFlow<String> = _lastError.asStateFlow()

    private val _is8DActive = MutableStateFlow(false)
    val is8DActive: StateFlow<Boolean> = _is8DActive.asStateFlow()

    // Processing bookkeeping (SpatialFlow's `isProcessing`, made race-safe with a token).
    private var isProcessing = false
    private var activeJobKey: String? = null
    private var processingJob: Job? = null
    @Volatile private var processingToken = 0L

    // ─────────────────────────────────────────────────────────────────────────
    // Connection
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun connect(context: Context) {
        if (controller != null) return
        try {
            val app = context.applicationContext
            appContext = app
            val sessionToken = SessionToken(app, ComponentName(app, LuteaPlaybackService::class.java))
            val c = MediaController.Builder(app, sessionToken).buildAsync().await()
            controller = c

            scope.launch(Dispatchers.IO) { cleanCache(app) }

            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.value = playing
                    if (playing) c.volume = 1f
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val songId = mediaItem?.mediaId?.toLongOrNull()
                    val song = queue.firstOrNull { it.id == songId }
                    _currentSong.value = song
                    _positionMs.value = 0L
                    _durationMs.value = 0L

                    val processed = isProcessedItem(mediaItem)
                    _is8DActive.value = processed

                    if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                        if (!c.isPlaying) c.play()
                    }

                    // AUTO-APPLY ON SONG CHANGE: 8D is on and what's loaded is the original
                    // file (auto-advance, next/prev, or tapping a new song) -> process it.
                    // Our own swap loads an item tagged as processed, so it never loops.
                    if (_is8DEnabled.value && song != null && !processed) {
                        applyEffects(true, _orbitSpeed.value, _spatialAmbience.value)
                    }
                }

                override fun onPlayerErrorChanged(error: PlaybackException?) {
                    if (error != null) {
                        Log.e(TAG, "Playback error: ${error.errorCodeName}", error)
                        try { c.seekToNextMediaItem(); c.prepare(); c.play() }
                        catch (e: Exception) { Log.e(TAG, "Skip after error failed", e) }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to playback service", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8D public API (names unchanged — EffectVisual.kt depends on them)
    // ─────────────────────────────────────────────────────────────────────────

    /** Toggle 8D. On: process current song (cache hit = instant). Off: restore original. */
    fun set8DEnabled(enabled: Boolean) {
        _is8DEnabled.value = enabled
        applyEffects(enabled, _orbitSpeed.value, _spatialAmbience.value)
    }

    /** Slider drag: only updates the value (no FFmpeg per drag tick). */
    fun setOrbitSpeed(value: Float) {
        _orbitSpeed.value = value.coerceIn(0.05f, 1.5f)
    }

    fun setSpatialAmbience(value: Float) {
        _spatialAmbience.value = value.coerceIn(0f, 1f)
    }

    /**
     * Slider released (onValueChangeFinished). Cache hit -> instant swap; otherwise a new
     * FFmpeg run with the new values (a run in progress with different values is cancelled).
     */
    fun apply8DParams() {
        if (!_is8DEnabled.value) return
        applyEffects(true, _orbitSpeed.value, _spatialAmbience.value)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Core: SpatialFlow.applyEffects()
    // ─────────────────────────────────────────────────────────────────────────

    private fun applyEffects(enable8D: Boolean, speedIn: Float, ambienceIn: Float) {
        val c = controller ?: return
        val ctx = appContext ?: return
        val song = _currentSong.value ?: return

        // Round once so the cache key and the FFmpeg command use identical values.
        val speed = r2(speedIn)
        val ambience = r2(ambienceIn)

        // ── Disable: cancel + restore original (SpatialFlow.loadOriginalAudio) ──
        if (!enable8D) {
            cancelProcessing()
            if (isProcessedItem(c.currentMediaItem)) {
                swapCurrentItem(song, song.uri, processedName = null)
            }
            _is8DActive.value = false
            return
        }

        // ── Cache check (SpatialFlow: outputFile.exists()) ──
        val outputFile = processedFile(ctx, song, speed, ambience)
        if (outputFile.exists() && outputFile.length() > 0) {
            cancelProcessing()
            val alreadyLoaded =
                c.currentMediaItem?.mediaMetadata?.extras?.getString(EXTRA_8D_FILE) == outputFile.name
            if (!alreadyLoaded) swapCurrentItem(song, Uri.fromFile(outputFile), outputFile.name)
            outputFile.setLastModified(System.currentTimeMillis())
            _is8DActive.value = true
            Log.i(TAG, "8D cache hit: ${outputFile.name}")
            return
        }

        // ── Guard (SpatialFlow: if (isProcessing) return) ──
        // Same job already running -> leave it alone. A *different* job (song/params changed)
        // is cancelled and replaced, so a skipped song never blocks the new one.
        if (isProcessing && activeJobKey == outputFile.name) return
        cancelProcessing()

        // ── Start processing ──
        val token = ++processingToken
        isProcessing = true
        activeJobKey = outputFile.name
        _isProcessing8D.value = true
        _processingProgress.value = 0f
        _lastError.value = ""

        val partFile = File(ctx.cacheDir, "8dtmp_${outputFile.name}")        // ends with .m4a
        val sourceTemp = File(ctx.cacheDir, "8dsrc_${song.id}_${System.currentTimeMillis()}")
        val durationMs = song.duration.coerceAtLeast(1000L)

        processingJob = scope.launch {
            try {
                // content:// -> real file FFmpeg can read.
                val copied = withContext(Dispatchers.IO) {
                    runCatching {
                        ctx.contentResolver.openInputStream(song.uri)?.use { input ->
                            FileOutputStream(sourceTemp).use { out -> input.copyTo(out) }
                            true
                        } ?: false
                    }.getOrDefault(false) && sourceTemp.length() > 0
                }
                if (token != processingToken) { sourceTemp.delete(); return@launch }
                if (!copied) {
                    sourceTemp.delete()
                    failRun(token, "Can't open source: ${song.uri}")
                    return@launch
                }

                partFile.delete()
                val cmd = FFmpegCommandBuilder.build8D(
                    inputPath = sourceTemp.absolutePath,
                    outputPath = partFile.absolutePath,
                    rotationSpeed = speed,
                    spatialAmbience = ambience,
                )
                Log.i(TAG, "FFmpeg: $cmd")

                FFmpegKit.executeAsync(
                    cmd,
                    { session ->
                        // FFmpegKit calls back on its own thread -> hop to main.
                        scope.launch {
                            onFfmpegComplete(session, token, song, sourceTemp, partFile, outputFile)
                        }
                    },
                    { /* log callback */ },
                    { stats: Statistics? ->
                        if (stats != null && token == processingToken) {
                            _processingProgress.value =
                                min(0.99f, stats.time.toFloat() / durationMs.toFloat())
                        }
                    },
                )
            } catch (t: Throwable) {
                Log.e(TAG, "applyEffects failed", t)
                sourceTemp.delete()
                partFile.delete()
                failRun(token, "${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    /** Runs on the main thread. */
    private fun onFfmpegComplete(
        session: FFmpegSession,
        token: Long,
        song: Song,
        sourceTemp: File,
        partFile: File,
        outputFile: File,
    ) {
        sourceTemp.delete()
        val stale = token != processingToken

        if (ReturnCode.isSuccess(session.returnCode)) {
            val ok = partFile.exists() && partFile.length() > 0 &&
                run { outputFile.delete(); partFile.renameTo(outputFile) }
            if (!ok) {
                partFile.delete()
                if (!stale) failRun(token, "FFmpeg finished but output file missing")
                return
            }
            if (stale) return // finished file stays cached for later use

            isProcessing = false
            activeJobKey = null
            _isProcessing8D.value = false
            _processingProgress.value = 1f

            // Only swap if the user is still on this song with 8D still on.
            if (_is8DEnabled.value && _currentSong.value?.id == song.id) {
                if (swapCurrentItem(song, Uri.fromFile(outputFile), outputFile.name)) {
                    _is8DActive.value = true
                    Log.i(TAG, "8D swap complete (${outputFile.length()} bytes)")
                }
            }
        } else {
            partFile.delete()
            if (stale) return
            if (ReturnCode.isCancel(session.returnCode)) {
                resetProcessingState()
            } else {
                failRun(token, "FFmpeg rc=${session.returnCode} logs=${session.allLogsAsString.take(500)}")
            }
        }
    }

    /**
     * SpatialFlow.loadProcessedAudio / loadOriginalAudio:
     *   keep position -> set item -> prepare -> (play state kept) -> volume = 1.0f
     *
     * Uses replaceMediaItem (single-item swap, fast) instead of setMediaItems (rebuilds the
     * entire playlist — slow on long queues, caused the '20 sec to load' user reported).
     * The playlist queue stays intact so next/prev/repeat keep working.
     */
    private fun swapCurrentItem(song: Song, uri: Uri, processedName: String?): Boolean {
        val c = controller ?: return false
        val index = c.currentMediaItemIndex
        if (index < 0 || index >= c.mediaItemCount) return false
        if (c.currentMediaItem?.mediaId != song.id.toString()) return false // user moved on

        val pos = c.currentPosition.coerceAtLeast(0L)

        try {
            // replaceMediaItem swaps only the current item — much faster than setMediaItems
            // which rebuilds the whole playlist. ExoPlayer preserves playback position
            // across replaceMediaItem if we seekTo after.
            c.replaceMediaItem(index, buildMediaItem(uri, song, processedName))
            c.seekTo(index, pos)
            c.prepare()
            // SpatialFlow calls updateBaseVolume() after every load -> volume back to 1.0f.
            c.volume = 1.0f
            return true
        } catch (e: Exception) {
            Log.e(TAG, "swapCurrentItem failed", e)
            return false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun cancelProcessing() {
        processingToken++                       // invalidate any in-flight callbacks
        processingJob?.cancel()
        processingJob = null
        try { FFmpegKit.cancel() } catch (_: Throwable) {}
        resetProcessingState()
    }

    private fun resetProcessingState() {
        isProcessing = false
        activeJobKey = null
        _isProcessing8D.value = false
        _processingProgress.value = 0f
    }

    private fun failRun(token: Long, message: String) {
        if (token != processingToken) return
        Log.e(TAG, message)
        _lastError.value = message
        resetProcessingState()
    }

    private fun r2(v: Float): Float = Math.round(v * 100f) / 100f

    private fun processedFile(ctx: Context, song: Song, speed: Float, ambience: Float): File =
        File(
            ctx.cacheDir,
            "8d_${song.id}_${String.format(Locale.US, "%.2f", speed)}_${String.format(Locale.US, "%.2f", ambience)}.m4a"
        )

    private fun isProcessedItem(item: MediaItem?): Boolean =
        item?.mediaMetadata?.extras?.getString(EXTRA_8D_FILE) != null

    private fun buildMediaItem(uri: Uri, song: Song, processedName: String? = null): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUri)
        if (processedName != null) {
            meta.setExtras(Bundle().apply { putString(EXTRA_8D_FILE, processedName) })
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(song.id.toString())
            .setMediaMetadata(meta.build())
            .build()
    }

    /** Same idea as SpatialFlow.cleanCache(): drop 8D files older than a day + leftovers. */
    private fun cleanCache(ctx: Context) {
        val now = System.currentTimeMillis()
        ctx.cacheDir.listFiles()?.forEach { f ->
            val n = f.name
            if (!n.startsWith("8d")) return@forEach
            val leftover = n.startsWith("8dtmp_") || n.startsWith("8dsrc_")
            if (leftover || f.lastModified() < now - CACHE_MAX_AGE_MS) f.delete()
        }
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
            // If 8D is on, onMediaItemTransition sees an un-processed item and auto-applies.
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
        cancelProcessing()
        controller?.release()
        controller = null
        _isPlaying.value = false
        _currentSong.value = null
        _is8DActive.value = false
    }
}
