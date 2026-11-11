package com.rajatxo.lutea.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin

/**
 * Real-time 8D audio pan LFO — direct port of SpatialFlow's StereoBalanceProcessor
 * pattern, extended to oscillate the balance per-sample.
 *
 * Inserted into the ExoPlayer audio pipeline as a custom AudioProcessor. The LFO
 * runs in the audio thread, modulating L/R volume sample-by-sample. No FFmpeg,
 * no pre-processing, no temp files, no AAC re-encoding — pure real-time PCM.
 *
 * Performance: ~1024 sin() calls per queueInput (once per audio frame), which is
 * trivial on modern phones. Output is always stereo (forced via onConfigure).
 *
 * Adapted from SpatialFlow's StereoBalanceProcessor
 * (github.com/MythicalSHUB/SpatialFlow, Apache-2.0 with author's permission).
 */
@UnstableApi
class Spatial8DProcessor : BaseAudioProcessor() {

    @Volatile private var enabled = false
    @Volatile private var hz: Float = 0.20f
    @Volatile private var width: Float = 0.75f   // 0..1, depth of rotation

    // LFO phase — accumulated per sample, must be sample-accurate (not per queueInput).
    private var phase: Double = 0.0

    fun set8DEnabled(e: Boolean) {
        enabled = e
        // Reset phase on enable so rotation starts from center
        if (e) phase = 0.0
    }

    fun setOrbitHz(h: Float) {
        hz = h.coerceIn(0.03f, 1.5f)
    }

    fun setSpatialWidth(w: Float) {
        width = w.coerceIn(0f, 1f)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        // Force stereo output — 8D needs L/R channels to pan between
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, inputAudioFormat.encoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!enabled || inputAudioFormat.sampleRate <= 0) {
            // Pass through unchanged — replaceOutputBuffer + copy + flip
            val outBuffer = replaceOutputBuffer(inputBuffer.remaining())
            outBuffer.put(inputBuffer)
            outBuffer.flip()
            return
        }

        val isStereo = inputAudioFormat.channelCount == 2
        val is16Bit = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        val bytesPerSample = if (is16Bit) 2 else 4
        val inputFrameSize = bytesPerSample * inputAudioFormat.channelCount
        val frameCount = inputBuffer.remaining() / inputFrameSize
        // Output is forced stereo (2 channels)
        val outBuffer = replaceOutputBuffer(frameCount * bytesPerSample * 2)

        // Phase advances per sample: 2π × hz / sampleRate per sample
        val phaseStep = 2.0 * PI * hz.toDouble() / inputAudioFormat.sampleRate.toDouble()

        if (is16Bit) {
            var i = 0
            while (i < frameCount) {
                val left = inputBuffer.short
                val right = if (isStereo) inputBuffer.short else left
                // balance = sin(phase) × width — oscillates between -width and +width
                val balance = sin(phase).toFloat() * width
                val leftVol = if (balance > 0) 1f - balance else 1f
                val rightVol = if (balance < 0) 1f + balance else 1f
                outBuffer.putShort((left * leftVol).toInt().coerceIn(-32768, 32767).toShort())
                outBuffer.putShort((right * rightVol).toInt().coerceIn(-32768, 32767).toShort())
                phase += phaseStep
                if (phase > 2.0 * PI) phase -= 2.0 * PI
                i++
            }
        } else {
            // PCM_FLOAT — no clamping needed, range is [-1, 1]
            var i = 0
            while (i < frameCount) {
                val left = inputBuffer.float
                val right = if (isStereo) inputBuffer.float else left
                val balance = sin(phase).toFloat() * width
                val leftVol = if (balance > 0) 1f - balance else 1f
                val rightVol = if (balance < 0) 1f + balance else 1f
                outBuffer.putFloat(left * leftVol)
                outBuffer.putFloat(right * rightVol)
                phase += phaseStep
                if (phase > 2.0 * PI) phase -= 2.0 * PI
                i++
            }
        }
        outBuffer.flip()
    }
}
