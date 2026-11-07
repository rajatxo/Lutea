package com.rajatxo.lutea.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Cover art -> screen background colour.
 *
 * The cover fades INTO this colour along its bottom edge, so the colour is sampled from the
 * bottom 38% of the cover only (not the whole image). That is what removes the visible seam.
 *
 *  1. Load a ~112 px software bitmap through Coil 3.
 *  2. Palette over y in [0.62h, h], 12 colours, take the swatch with the highest population.
 *  3. HSL darken: saturation x 0.9 (cap 0.62), lightness x 0.5 (clamp 0.09..0.30), hue untouched.
 *  4. Cache the result per cover URI (LruCache, 96 entries) so re-opening a playlist is instant.
 */
object LuteaColorExtractor {

    private const val SAMPLE_PX = 112
    private const val SAMPLE_TOP_FRACTION = 0.62f // bottom 38% => y from 0.62h to h
    private const val MAX_COLORS = 12
    private const val CACHE_ENTRIES = 96

    private const val SAT_SCALE = 0.9f
    private const val SAT_CAP = 0.62f
    private const val LIGHT_SCALE = 0.5f
    private const val LIGHT_MIN = 0.09f
    private const val LIGHT_MAX = 0.30f

    /** What the screen shows before a colour is known. */
    val Neutral: Color = Color.Black

    private val cache = LruCache<String, Int>(CACHE_ENTRIES)

    /** Instant lookup, no work. Null when this cover has not been extracted yet. */
    fun cached(coverUri: String?): Color? {
        if (coverUri.isNullOrEmpty()) return null
        val argb = cache.get(coverUri) ?: return null
        return Color(argb)
    }

    fun clearCache() {
        cache.evictAll()
    }

    /** Extracts (or returns the cached) background colour. Null if the cover can't be loaded. */
    suspend fun extract(context: Context, coverUri: String): Color? {
        cached(coverUri)?.let { return it }
        val bitmap = loadSample(context, coverUri) ?: return null
        val argb = withContext(Dispatchers.Default) { backdropArgb(bitmap) } ?: return null
        cache.put(coverUri, argb)
        return Color(argb)
    }

    /** Saturation x 0.9 (cap 0.62), lightness x 0.5 (clamp 0.09..0.30), hue preserved. */
    fun darken(argb: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        hsl[1] = min(hsl[1] * SAT_SCALE, SAT_CAP)
        hsl[2] = (hsl[2] * LIGHT_SCALE).coerceIn(LIGHT_MIN, LIGHT_MAX)
        return ColorUtils.HSLToColor(hsl)
    }

    /** Stable hue (0..359) from any string. Used for covers that don't exist (yet). */
    fun hueFor(seed: String): Float =
        ((seed.hashCode().toLong() and 0x7FFFFFFFL) % 360L).toFloat()

    /** Background for a playlist with no cover (or one that failed to load). */
    fun fallbackFor(seed: String?): Color {
        if (seed.isNullOrEmpty()) return Neutral
        val base = ColorUtils.HSLToColor(floatArrayOf(hueFor(seed), 0.55f, 0.45f))
        return Color(darken(base))
    }

    // ---------------------------------------------------------------- internals

    private suspend fun loadSample(context: Context, coverUri: String): Bitmap? {
        val appContext = context.applicationContext
        val request = ImageRequest.Builder(appContext)
            .data(coverUri)
            .size(SAMPLE_PX, SAMPLE_PX)
            .allowHardware(false) // Palette needs a readable (software) bitmap
            .build()
        val result = SingletonImageLoader.get(appContext).execute(request)
        val image = (result as? SuccessResult)?.image ?: return null
        return runCatching { image.toBitmap() }.getOrNull()
    }

    private fun backdropArgb(bitmap: Bitmap): Int? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 2 || h < 2) return null
        val top = (h * SAMPLE_TOP_FRACTION).toInt().coerceIn(0, h - 1)

        val palette = runCatching {
            Palette.from(bitmap)
                .maximumColorCount(MAX_COLORS)
                .setRegion(0, top, w, h)
                .generate()
        }.getOrNull()

        // Most populous swatch, NOT palette.dominantSwatch.
        val picked = palette?.swatches?.maxByOrNull { it.population }?.rgb
            // Palette filters out near-black / near-white; for those covers fall back to a plain average.
            ?: averageArgb(bitmap, top)

        return darken(picked)
    }

    private fun averageArgb(bitmap: Bitmap, top: Int): Int {
        val w = bitmap.width
        val rows = (bitmap.height - top).coerceAtLeast(1)
        val pixels = IntArray(w * rows)
        bitmap.getPixels(pixels, 0, w, 0, top, w, rows)
        var r = 0L
        var g = 0L
        var b = 0L
        for (p in pixels) {
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
        }
        val n = pixels.size.coerceAtLeast(1)
        return (0xFF shl 24) or
            ((r / n).toInt() shl 16) or
            ((g / n).toInt() shl 8) or
            (b / n).toInt()
    }
}

/**
 * Animated background colour for a cover.
 *
 * - Cached cover: the very first frame already has the final colour (no animation).
 * - Not cached: starts neutral black, then crossfades to the extracted colour over 600 ms.
 * - No cover / failed load: uses a colour derived from [fallbackSeed] (e.g. the playlist id).
 *
 * Read the returned State inside draw/graphicsLayer lambdas when you can, so the 600 ms fade
 * doesn't recompose your screen.
 */
@Composable
fun rememberCoverThemeColor(
    coverUri: String?,
    fallbackSeed: String? = null,
): State<Color> {
    val appContext = LocalContext.current.applicationContext

    var target by remember(coverUri, fallbackSeed) {
        mutableStateOf(
            LuteaColorExtractor.cached(coverUri)
                ?: if (coverUri.isNullOrBlank()) {
                    LuteaColorExtractor.fallbackFor(fallbackSeed)
                } else {
                    LuteaColorExtractor.Neutral
                }
        )
    }

    LaunchedEffect(coverUri, fallbackSeed) {
        if (!coverUri.isNullOrBlank()) {
            val extracted = LuteaColorExtractor.extract(appContext, coverUri)
            target = extracted ?: LuteaColorExtractor.fallbackFor(fallbackSeed)
        }
    }

    return animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 600),
        label = "coverThemeColor",
    )
}
