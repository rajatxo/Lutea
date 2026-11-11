package com.rajatxo.lutea.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
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

/**
 * Effect8DProcessor — runs FFmpeg on the current song to produce a real 8D
 * audio file (apulsator + aecho filter chain), then swaps the player's media
 * item to the processed file. When toggled off, cancels any running job and
 * restores the original song.
 *
 * Adapted from SpatialFlow's AudioPlaybackService.set8DEnabled + applyEffects
 * pattern, but uses Compose-friendly StateFlow instead of LiveData and runs
 * in a coroutine scope tied to PlaybackConnection instead of a service field.
 *
 * The processed file is cached in cacheDir/8d/{songId}.m4a — re-processed
 * only when orbit speed or ambience change. Re-toggling 8D on the same song
 * with the same params just plays the cached file.
 */
class Effect8DProcessor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onProcessedUri: (Uri) -> Unit,
    private val onRestoreOriginal: () -> Unit,
) {
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private var currentJob: Job? = null
    private var currentSong: Song? = null
    private var currentParams: Params? = null
    private var cacheDir: File? = null

    private data class Params(val speed: Float, val ambience: Float)

    // Note: do NOT call FFmpegKitConfig or any FFmpegKit method in init or on
    // app launch — it forces native library load which can crash on certain
    // devices if the native lib packaging is misconfigured. All FFmpegKit
    // access happens lazily inside processSong(), only when the user toggles
    // 8D on.

    fun enable(song: Song, orbitSpeed: Float, spatialAmbience: Float) {
        val newParams = Params(orbitSpeed, spatialAmbience)
        // If the same song is already processed with the same params, just play it.
        if (currentSong?.id == song.id && currentParams == newParams && cacheFor(song) != null) {
            cacheFor(song)?.let { onProcessedUri(Uri.fromFile(it)) }
            return
        }
        currentJob?.cancel()
        currentSong = song
        currentParams = newParams
        currentJob = scope.launch {
            try {
                processSong(song, newParams)
            } catch (t: Throwable) {
                // Catch Throwable (not just Exception) — native UnsatisfiedLinkError
                // and NoClassDefFoundError on FFmpegKit init would otherwise crash
                // the app. Log and bail.
                Log.e("Effect8DProcessor", "FFmpegKit failed to load or run", t)
                _isProcessing.value = false
            }
        }
    }

    fun disable() {
        currentJob?.cancel()
        currentJob = null
        currentSong = null
        currentParams = null
        _isProcessing.value = false
        _progress.value = 0f
        // Cancel any FFmpeg session that's still running
        try { FFmpegKit.cancel() } catch (_: Exception) {}
        onRestoreOriginal()
    }

    fun updateParams(orbitSpeed: Float, spatialAmbience: Float) {
        val song = currentSong ?: return
        val newParams = Params(orbitSpeed, spatialAmbience)
        if (newParams == currentParams) return
        currentParams = newParams
        // Re-process from scratch — different speed needs different apulsator hz.
        // The cache is invalidated (file deleted) so the next enable() rebuilds it.
        cacheFor(song)?.delete()
        currentJob?.cancel()
        currentJob = scope.launch {
            processSong(song, newParams)
        }
    }

    private suspend fun processSong(song: Song, params: Params) {
        _isProcessing.value = true
        _progress.value = 0f
        try {
            val cache = ensureCache(song)
            // Copy the source URI content to a temp file FFmpeg can read.
            val sourceTemp = File(context.cacheDir, "8d_source_${song.id}")
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(song.uri)?.use { input ->
                    sourceTemp.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("Can't open source URI: ${song.uri}")
            }
            val cmd = FFmpegCommandBuilder.build8D(
                inputPath = sourceTemp.absolutePath,
                outputPath = cache.absolutePath,
                rotationSpeed = params.speed,
                spatialAmbience = params.ambience,
            )
            Log.i("Effect8DProcessor", "Running FFmpeg: $cmd")
            val session = FFmpegKit.executeAsync(cmd) { completed ->
                scope.launch {
                    if (ReturnCode.isSuccess(completed.returnCode)) {
                        _progress.value = 1f
                        _isProcessing.value = false
                        sourceTemp.delete()
                        onProcessedUri(Uri.fromFile(cache))
                    } else {
                        Log.e("Effect8DProcessor", "FFmpeg failed: ${completed.returnCode} / ${completed.allLogsAsString}")
                        _isProcessing.value = false
                        sourceTemp.delete()
                    }
                }
            }
            // Poll session statistics for progress updates (rough — based on processed time)
            val totalMs = song.duration.coerceAtLeast(1000L)
            while (session.state != com.arthenica.ffmpegkit.SessionState.COMPLETED) {
                val stats: Statistics? = session.lastReceivedStatistics
                val pos = stats?.time?.toFloat() ?: 0f
                _progress.value = (pos / totalMs).coerceIn(0f, 0.99f)
                kotlinx.coroutines.delay(200)
            }
        } catch (e: Exception) {
            Log.e("Effect8DProcessor", "processSong failed", e)
            _isProcessing.value = false
        }
    }

    private fun ensureCache(song: Song): File {
        val dir = File(context.cacheDir, "8d").apply { mkdirs() }
        return File(dir, "${song.id}.m4a")
    }

    private fun cacheFor(song: Song): File? {
        val f = ensureCache(song)
        return if (f.exists() && f.length() > 0) f else null
    }
}
