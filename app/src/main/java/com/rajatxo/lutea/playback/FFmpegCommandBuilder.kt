package com.rajatxo.lutea.playback

import java.util.Locale

/**
 * Port of SpatialFlow's FFmpegCommandBuilder (Apache-2.0, used with the author's permission).
 *
 * Filter chain is SpatialFlow's, unchanged:
 *   apulsator -> aecho -> stereotools -> alimiter, encoded as AAC 192k / 44.1 kHz stereo.
 *
 * Only addition: [spatialAmbience] (Lutea's 2nd slider, 0..1) scales the aecho decays.
 * At the default 0.5 the decays are exactly SpatialFlow's original 0.2|0.15.
 *
 * Place this file in the SAME package as PlaybackConnection (com.rajatxo.lutea.playback)
 * so no import is needed. Do NOT keep SpatialFlow's copy (com.codetrio...) in the project.
 */
object FFmpegCommandBuilder {

    @JvmStatic
    fun build8D(
        inputPath: String,
        outputPath: String,
        rotationSpeed: Float,
        spatialAmbience: Float = 0.5f,
    ): String {
        // SpatialFlow clamps to 0.03..0.25 Hz. Lutea's slider goes up to 1.5, so widen the top.
        val speed = rotationSpeed.coerceIn(0.03f, 1.5f)
        val ambience = spatialAmbience.coerceIn(0f, 1f)

        // 0.5 -> 0.20 | 0.15 (SpatialFlow's exact values). aecho decays must be > 0.
        val decay1 = (0.4f * ambience).coerceAtLeast(0.01f)
        val decay2 = (0.3f * ambience).coerceAtLeast(0.01f)

        return buildString {
            append("-y ")
            append("-hide_banner ")
            append("-loglevel error ")
            append("-threads 0 ")

            append("-i \"$inputPath\" ")
            append("-vn ")
            append("-map 0:a:0 ")

            append("-af \"")
            append("apulsator=hz=")
            append(String.format(Locale.US, "%.3f", speed))
            append(":width=0.75:mode=sine")
            append(",aecho=0.6:0.4:30|60:")
            append(String.format(Locale.US, "%.3f|%.3f", decay1, decay2))
            append(",stereotools=balance_in=0.02")
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
