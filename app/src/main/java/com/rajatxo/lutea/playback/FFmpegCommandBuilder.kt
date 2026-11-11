package com.rajatxo.lutea.playback

import java.util.Locale

/**
 * FFmpeg command builder for 8D audio processing.
 *
 * VERBATIM copy of SpatialFlow's FFmpegCommandBuilder (github.com/MythicalSHUB/SpatialFlow).
 * Same filter chain, same encoding params, same fixed aecho values.
 *
 * The only addition is the spatialAmbience parameter — but it scales the apulsator
 * width (depth of the rotation) instead of the aecho, so the audio character stays
 * identical to SpatialFlow's signature sound.
 */
object FFmpegCommandBuilder {

    /**
     * Builds the 8D audio processing command.
     *
     * SpatialFlow's exact filter chain:
     *   - apulsator: stereo pan LFO at [rotationSpeed] Hz (the actual 8D rotation)
     *   - aecho: FIXED 0.6/0.4 in/out, 30|60 ms delays, 0.2/0.15 decay (the space)
     *   - stereotools: balance_in=0.02 (subtle symmetry break)
     *   - alimiter: limit=0.97 (clean output ceiling)
     *
     * Output: AAC 192kbps stereo 44.1kHz, faststart for streaming.
     */
    fun build8D(
        inputPath: String,
        outputPath: String,
        rotationSpeed: Float,
        spatialAmbience: Float = 0.5f,
    ): String {
        // SpatialFlow clamps to 0.03..0.25 — copy that
        val speed = rotationSpeed.coerceIn(0.03f, 0.25f)

        return buildString {
            append("-y ")
            append("-hide_banner ")
            append("-loglevel error ")
            append("-threads 0 ")

            append("-i \"$inputPath\" ")
            append("-vn ")
            append("-map 0:a:0 ")

            append("-af \"")

            // MAIN MOTION — apulsator with spatial ambience controlling width
            append("apulsator=hz=")
            append(String.format(Locale.US, "%.3f", speed))
            // Width scales with ambience: 0.5 (default) = 0.75 like SpatialFlow,
            // 0.0 = 0.50 (subtle), 1.0 = 1.0 (full rotation)
            val width = 0.50f + 0.50f * spatialAmbience.coerceIn(0f, 1f)
            append(":width=")
            append(String.format(Locale.US, "%.2f", width))
            append(":mode=sine")

            // FIXED ECHO — matches SpatialFlow exactly (their signature space sound)
            append(",aecho=0.6:0.4:30|60:0.2|0.15")

            // SUBTLE STEREO VARIATION
            append(",stereotools=balance_in=0.02")

            // CLEAN LIMIT
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
