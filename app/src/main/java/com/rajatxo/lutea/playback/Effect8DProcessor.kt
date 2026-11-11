package com.rajatxo.lutea.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.rajatxo.lutea.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Effect8DProcessor — adapted from SpatialFlow's AudioPlaybackService 8D logic.
 *
 * Direct port of github.com/MythicalSHUB/SpatialFlow's applyEffects / on8DProcessingSuccess /
 * loadProcessedAudio / loadOriginalAudio methods, simplified for Lutea's MediaController-based
 * architecture (SpatialFlow runs everything inside the service; we drive it from the controller
 * surface, but the FFmpeg lifecycle and media-item swap logic is identical).
 *
 * Cache key uses songId + speed + ambience — re-running with same params just plays the cache.
 */
class Effect8DProcessor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onProcessedUri: (Uri, Long) -> Unit,
    private val onRestoreOriginal: (Long) -> Unit,
) {
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private var currentJob: Job? = null
    private var currentSong: Song? = null
    private var currentParams: Params? = null

    private data class Params(val speed: Float, val ambience: Float)

    /**
     * Enable 8D on [song] with the given params. If [song] is null, do nothing
     * (caller's responsibility to guard).
     *
     * Mirrors SpatialFlow.applyEffects(enable8D=true, ...):
     *   - Cache key = sourcePath + speed + ambience → hashCode
     *   - If cached file exists, call onProcessedUri immediately
     *   - Otherwise copy source URI to a temp file (because MediaStore URIs aren't
     *     file paths), then run FFmpegKit.executeAsync with a statistics callback
     *     for progress updates
     */
    fun enable(song: Song, orbitSpeed: Float, spatialAmbience: Float, startPosition: Long = 0L) {
        val newParams = Params(orbitSpeed, spatialAmbience)
        val cacheKey = "${song.id}${orbitSpeed}${spatialAmbience}".hashCode()
        val outputFile = File(context.cacheDir, "8d_$cacheKey.m4a")

        // Already processed? Just play the cached file (SpatialFlow pattern).
        if (outputFile.exists() && outputFile.length() > 0) {
            currentSong = song
            currentParams = newParams
            onProcessedUri(Uri.fromFile(outputFile), startPosition)
            return
        }

        // Cancel any in-flight job before starting a new one
        currentJob?.cancel()
        currentSong = song
        currentParams = newParams
        currentJob = scope.launch {
            try {
                processSong(song, newParams, outputFile, startPosition)
            } catch (t: Throwable) {
                Log.e("Effect8DProcessor", "FFmpeg failed", t)
                _isProcessing.value = false
                _progress.value = 0f
            }
        }
    }

    /**
     * Disable 8D. Cancels any in-flight FFmpeg session, restores original song at
     * [startPosition]. Idempotent — calling when 8D was never actually running is safe.
     */
    fun disable(startPosition: Long = 0L) {
        currentJob?.cancel()
        currentJob = null
        currentSong = null
        currentParams = null
        _isProcessing.value = false
        _progress.value = 0f
        try { FFmpegKit.cancel() } catch (_: Throwable) {}
        onRestoreOriginal(startPosition)
    }

    /**
     * Slider changed while 8D is on. Re-process with new params.
     */
    fun updateParams(song: Song, orbitSpeed: Float, spatialAmbience: Float, startPosition: Long = 0L) {
        val newParams = Params(orbitSpeed, spatialAmbience)
        if (newParams == currentParams && currentSong?.id == song.id) return
        enable(song, orbitSpeed, spatialAmbience, startPosition)
    }

    private suspend fun processSong(
        song: Song,
        params: Params,
        outputFile: File,
        startPosition: Long,
    ) {
        _isProcessing.value = true
        _progress.value = 0f
        try {
            // Copy source URI content to a temp file FFmpeg can read directly.
            // (SpatialFlow uses AudioFileManager.getRealPathFromURI; we just copy because
            // Lutea doesn't have that helper and MediaStore URIs aren't raw file paths.)
            val sourceTemp = File(context.cacheDir, "8d_source_${song.id}")
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(song.uri)?.use { input ->
                    FileOutputStream(sourceTemp).use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("Can't open source URI: ${song.uri}")
            }
            val cmd = FFmpegCommandBuilder.build8D(
                inputPath = sourceTemp.absolutePath,
                outputPath = outputFile.absolutePath,
                rotationSpeed = params.speed,
                spatialAmbience = params.ambience,
            )
            Log.i("Effect8DProcessor", "Running FFmpeg: $cmd")

            // SpatialFlow uses FFmpegKit.executeAsync(cmd, completeCb, logCb, statsCb).
            // We mirror that — the stats callback drives progress, the complete callback
            // triggers onProcessedUri or sets the error state.
            val durationMs = song.duration.coerceAtLeast(1000L)
            val session = FFmpegKit.executeAsync(
                cmd,
                { completed ->
                    scope.launch {
                        if (ReturnCode.isSuccess(completed.returnCode)) {
                            _progress.value = 1f
                            _isProcessing.value = false
                            sourceTemp.delete()
                            onProcessedUri(Uri.fromFile(outputFile), startPosition)
                        } else {
                            Log.e(
                                "Effect8DProcessor",
                                "FFmpeg failed: rc=${completed.returnCode} logs=${completed.allLogsAsString}",
                            )
                            _isProcessing.value = false
                            _progress.value = 0f
                            sourceTemp.delete()
                            outputFile.delete()
                        }
                    }
                },
                { /* log callback — ignore, we read allLogsAsString on failure */ },
                { stats: Statistics? ->
                    stats?.let { s ->
                        scope.launch {
                            val progress = min(0.99f, s.time.toFloat() / durationMs.toFloat())
                            _progress.value = progress
                        }
                    }
                },
            )
            // Wait for the session to complete so the Job doesn't end early.
            // We poll state with a short delay — FFmpegKit's callback handles the work.
            withContext(Dispatchers.IO) {
                while (session.state != com.arthenica.ffmpegkit.SessionState.COMPLETED) {
                    kotlinx.coroutines.delay(100)
                }
            }
        } catch (e: Exception) {
            Log.e("Effect8DProcessor", "processSong failed", e)
            _isProcessing.value = false
        }
    }
}
