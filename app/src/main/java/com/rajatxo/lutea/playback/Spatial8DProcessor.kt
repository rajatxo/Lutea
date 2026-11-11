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
        // Force stereo output — 8D needs L/R channels to pan between.
        // Even when 8D is OFF, we still output stereo (the bypass path duplicates
        // mono to stereo). This keeps the AudioSink's expected format stable
        // regardless of the 8D toggle state, so toggling doesn't trigger a
        // re-configure glitch that could stall playback.
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, inputAudioFormat.encoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val isStereo = inputAudioFormat.channelCount == 2
        val is16Bit = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        val bytesPerSample = if (is16Bit) 2 else 4
        val inputFrameSize = bytesPerSample * inputAudioFormat.channelCount
        val frameCount = inputBuffer.remaining() / inputFrameSize
        // Output is always stereo (2 channels) regardless of 8D state.
        val outBuffer = replaceOutputBuffer(frameCount * bytesPerSample * 2)

        // ★ KEY INSIGHT from SpatialFlow: there's no separate "disabled" path.
        // When their balance is 0, leftVol = rightVol = 1f → multiply is a no-op.
        // We do the same: when 8D is off, set width=0 → balance always = 0 →
        // leftVol = rightVol = 1f → audio passes through unchanged.
        // This is critical: a separate bypass path caused format-mismatch glitches
        // and instant pause when toggling. Same code path always = stable.

        // Effective width: 0 when disabled (no rotation, audio unchanged),
        // otherwise the configured width.
        val effectiveWidth = if (enabled) width else 0f

        // LFO phase advances per sample: 2π × hz / sampleRate per sample.
        // When width=0, sin(phase) doesn't matter (multiplied by 0) but we still
        // advance phase so re-enabling is smooth (no sudden jump from 0 to full).
        val phaseStep = if (inputAudioFormat.sampleRate > 0)
            2.0 * PI * hz.toDouble() / inputAudioFormat.sampleRate.toDouble() else 0.0

        if (is16Bit) {
            var i = 0
            while (i < frameCount) {
                val left = inputBuffer.short
                val right = if (isStereo) inputBuffer.short else left
                val balance = sin(phase).toFloat() * effectiveWidth
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
                val balance = sin(phase).toFloat() * effectiveWidth
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
