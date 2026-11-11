package com.rajatxo.lutea.playback

import java.util.Locale

/**
 * FFmpeg command builder for 8D audio processing.
 *
 * Adapted from SpatialFlow's FFmpegCommandBuilder (github.com/MythicalSHUB/SpatialFlow).
 * Builds the FFmpeg command that produces the 8D rotation effect via:
 *   - apulsator: stereo pan LFO at [rotationSpeed] Hz (the actual 8D motion)
 *   - aecho: subtle echo depth scaled by [spatialAmbience] (0 = none, 1 = full)
 *   - stereotools: tiny balance offset to break perfect symmetry
 *   - alimiter: clean output ceiling so the filter chain never clips
 *
 * Output is AAC 192kbps stereo 44.1kHz (matches SpatialFlow's high-fidelity preset).
 */
object FFmpegCommandBuilder {

    fun build8D(
        inputPath: String,
        outputPath: String,
        rotationSpeed: Float,
        spatialAmbience: Float,
    ): String {
        // apulsator hz is bounded to a sane range — too slow = no effect, too fast = seasick
        val speed = rotationSpeed.coerceIn(0.03f, 0.5f)
        // aecho intensity scales with the ambience slider. SpatialFlow uses fixed
        // 0.6/0.4 in_gain/out_gain; we keep those but scale the decay gain with ambience.
        // At ambience=0, decay gain = 0 (no echo). At ambience=1, decay gain = 0.35.
        val ambience = spatialAmbience.coerceIn(0f, 1f)
        val echoDecayGain = 0.35f * ambience

        return buildString {
            append("-y ")
            append("-hide_banner ")
            append("-loglevel error ")
            append("-threads 0 ")

            append("-i \"$inputPath\" ")
            append("-vn ")
            append("-map 0:a:0 ")

            append("-af \"")

            // Main motion: stereo pan LFO (the actual 8D rotation)
            append("apulsator=hz=")
            append(String.format(Locale.US, "%.3f", speed))
            append(":width=0.75:mode=sine")

            // Spatial ambience: subtle echo depth — controlled by slider
            if (echoDecayGain > 0.001f) {
                append(",aecho=0.6:0.4:30|60:")
                append(String.format(Locale.US, "%.2f", echoDecayGain))
                append("|")
                append(String.format(Locale.US, "%.2f", echoDecayGain * 0.75f))
            }

            // Subtle stereo variation — breaks perfect symmetry
            append(",stereotools=balance_in=0.02")

            // Clean output ceiling
            append(",alimiter=limit=0.97")

            append("\" ")

            append("-c:a aac ")
            append("-b:a 192k ")
            append("-ar 44100 ")
            append("-ac 2 ")

            append("-movflags +faststart ")
            append("-map_metadata 0 ")

            append("\"$outputPath\"")
        }
    }
}
