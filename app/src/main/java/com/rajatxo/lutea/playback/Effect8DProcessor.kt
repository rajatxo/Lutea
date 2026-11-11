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

    // Last error message — empty if no error. Surfaced to UI so we can see
    // what's actually going wrong (instead of try/catch silently swallowing it).
    private val _lastError = MutableStateFlow("")
    val lastError: StateFlow<String> = _lastError.asStateFlow()

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
        Log.i("Effect8DProcessor", "enable: song=${song.title} speed=$orbitSpeed ambience=$spatialAmbience pos=$startPosition")
        _lastError.value = ""
        val newParams = Params(orbitSpeed, spatialAmbience)
        val cacheKey = "${song.id}${orbitSpeed}${spatialAmbience}".hashCode()
        val outputFile = File(context.cacheDir, "8d_$cacheKey.m4a")
        Log.i("Effect8DProcessor", "cache file: ${outputFile.absolutePath} exists=${outputFile.exists()} size=${outputFile.length()}")

        // Already processed? Just play the cached file (SpatialFlow pattern).
        if (outputFile.exists() && outputFile.length() > 0) {
            Log.i("Effect8DProcessor", "cache hit — playing cached 8D file")
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
                // Catch Throwable (not just Exception) — native UnsatisfiedLinkError
                // and NoClassDefFoundError on FFmpegKit init would otherwise crash
                // the app. Log and surface to UI.
                Log.e("Effect8DProcessor", "FFmpeg failed", t)
                _lastError.value = "${t.javaClass.simpleName}: ${t.message}"
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
            val sourceTemp = File(context.cacheDir, "8d_source_${song.id}")
            Log.i("Effect8DProcessor", "copying source URI → ${sourceTemp.absolutePath}")
            var sourceOpened = false
            withContext(Dispatchers.IO) {
                sourceOpened = context.contentResolver.openInputStream(song.uri)?.use { input ->
                    FileOutputStream(sourceTemp).use { output -> input.copyTo(output) }
                } != null
            }
            if (!sourceOpened) {
                _lastError.value = "Can't open source URI: ${song.uri}"
                Log.e("Effect8DProcessor", _lastError.value)
                _isProcessing.value = false
                return
            }
            Log.i("Effect8DProcessor", "source copied, size=${sourceTemp.length()}")

            val cmd = FFmpegCommandBuilder.build8D(
                inputPath = sourceTemp.absolutePath,
                outputPath = outputFile.absolutePath,
                rotationSpeed = params.speed,
                spatialAmbience = params.ambience,
            )
            Log.i("Effect8DProcessor", "FFmpeg command: $cmd")

            val durationMs = song.duration.coerceAtLeast(1000L)
            Log.i("Effect8DProcessor", "starting FFmpegKit.executeAsync, song duration=$durationMs ms")
            val session = FFmpegKit.executeAsync(
                cmd,
                { completed ->
                    Log.i("Effect8DProcessor", "FFmpeg complete: rc=${completed.returnCode} sessionId=${completed.sessionId}")
                    scope.launch {
                        if (ReturnCode.isSuccess(completed.returnCode)) {
                            Log.i("Effect8DProcessor", "FFmpeg success — output size=${outputFile.length()}")
                            _progress.value = 1f
                            _isProcessing.value = false
                            sourceTemp.delete()
                            onProcessedUri(Uri.fromFile(outputFile), startPosition)
                        } else {
                            val msg = "FFmpeg rc=${completed.returnCode} logs=${completed.allLogsAsString.take(500)}"
                            Log.e("Effect8DProcessor", msg)
                            _lastError.value = msg
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
            Log.i("Effect8DProcessor", "FFmpegKit session started: id=${session.sessionId} state=${session.state}")
            withContext(Dispatchers.IO) {
                while (session.state != com.arthenica.ffmpegkit.SessionState.COMPLETED) {
                    kotlinx.coroutines.delay(100)
                }
            }
            Log.i("Effect8DProcessor", "session completed, final state=${session.state}")
        } catch (e: Exception) {
            Log.e("Effect8DProcessor", "processSong failed", e)
            _lastError.value = "${e.javaClass.simpleName}: ${e.message}"
            _isProcessing.value = false
        }
    }
}
