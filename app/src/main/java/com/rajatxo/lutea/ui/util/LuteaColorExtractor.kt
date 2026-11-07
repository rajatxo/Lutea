package com.rajatxo.lutea.ui.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlin.math.roundToInt

/**
 * LuteaColorExtractor — extracts a dominant color from album art for the
 * playlist detail background. Built from scratch (Mochy-inspired technique).
 *
 * The pipeline:
 *  1. Run Palette on the album art bitmap (max 8 colors, downscaled to 100×100)
 *  2. Take the dominant swatch (most pixels)
 *  3. Darken it for dark theme — saturation halved, brightness clamped to 0.12–0.45
 *     so the background never blows out white
 *  4. Animated color transition handled by the caller (animateColorAsState)
 */
object LuteaColorExtractor {

    /** Default fallback if no album art or extraction fails. */
    val Default = Color(0xFF1A1A2E)

    /**
     * Extract a darkened dominant color from [bitmap], suitable as a background
     * tint behind white text. Returns null if Palette finds nothing.
     */
    fun extractDominant(bitmap: Bitmap): Color? {
        val palette = Palette.from(bitmap)
            .maximumColorCount(8)
            .resizeBitmapArea(100 * 100)
            .generate()
        val rgb = palette.dominantSwatch?.rgb ?: return null
        return darkenForDarkTheme(Color(rgb))
    }

    /**
     * Darken + desaturate so the color sits behind text without washing out.
     * - Saturation halved (muted feel)
     * - Brightness clamped to [0.12, 0.45] (always dark enough for white text)
     */
    private fun darkenForDarkTheme(color: Color): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (color.red * 255f).roundToInt().coerceIn(0, 255),
            (color.green * 255f).roundToInt().coerceIn(0, 255),
            (color.blue * 255f).roundToInt().coerceIn(0, 255),
            hsv,
        )
        hsv[1] = (hsv[1] * 0.5f).coerceIn(0.04f, 0.42f)
        hsv[2] = hsv[2].coerceIn(0.12f, 0.45f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}
