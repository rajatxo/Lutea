package com.rajatxo.lutea.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rajatxo.lutea.ui.util.LuteaColorExtractor

// ------------------------------------------------------------------ tunables (brief's cheat sheet)

// Blend gradient: clear until 0.56, 78% background at 0.84, solid background at 1.0
private const val BLEND_CLEAR_UNTIL = 0.56f
private const val BLEND_MID_AT = 0.84f
private const val BLEND_MID_ALPHA = 0.78f
private const val BLEND_JOIN_SLOPE = 1.5f // slope at the 0.84 join, keeps the ramp smooth (C1)
private const val BLEND_STEPS = 8

// Top scrim for status-bar legibility: black 34% at the top, gone by 0.24
private const val SCRIM_ALPHA = 0.34f
private const val SCRIM_END = 0.24f

// Scroll choreography — the cover is PINNED. The one and only thing scroll changes is the
// blur radius: 0 -> 30 px over progress 0 -> 0.55. Same numbers as Mochy's ScrollAwayCover
// (CoverSoftRadius = 30f, CoverSoftBy = 0.55f). No parallax, no zoom, no dim, no fade —
// nothing that could read as the artwork moving, growing or vanishing into the background.
private const val BLUR_MAX_RADIUS_PX = 30f // actual pixels, exactly Mochy (NOT dp — dp would be ~3x stronger)
private const val BLUR_IN_FROM = 0f
private const val BLUR_IN_TO = 0.55f

private val TopScrimBrush: Brush = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = SCRIM_ALPHA),
    SCRIM_END to Color.Black.copy(alpha = 0f),
    1f to Color.Black.copy(alpha = 0f),
)

/** Hermite smoothstep (C1). The one easing curve used for every ramp on the detail screen. */
internal fun luteaSmoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * The cover image at the top of the playlist screen.
 *
 * What the user asked for, and all this does: when the list scrolls up, the cover blurs —
 * that's it. The box never moves (no parallax), never scales (no zoom), never dims, never
 * fades, never dissolves. The artwork is blurred in place with a RenderEffect whose radius
 * grows 0 -> 30 px as progress goes 0 -> 0.55 (Mochy's ScrollAwayCover numbers, so the blur
 * is clearly visible early but the artwork stays recognisable instead of smearing into a
 * flat puddle of the background colour).
 *
 * Layers, bottom to top:
 *  1. placeholder gradient (shown when there is no cover, or while it loads)
 *  2. cover (Coil AsyncImage, Crop) whose RenderEffect blur radius grows with scroll (API 31+;
 *     below S the image simply stays sharp)
 *  3. blend gradient: cover -> [backgroundColor] — STATIC, never driven by scroll (this is the
 *     resting seamless fade into the page colour; it does not expand or move while scrolling)
 *  4. top scrim
 *
 * [scrollProgress] and [backgroundColor] are lambdas on purpose: they are only read in the
 * graphicsLayer / draw phase, so scrolling and the 600 ms colour fade never recompose this.
 *
 * @param scrollProgress 0f..1f = how far the list has scrolled, relative to the cover height.
 */
@Composable
fun PlaylistCoverBackground(
    coverUri: String?,
    backgroundColor: () -> Color,
    coverHeight: Dp,
    scrollProgress: () -> Float,
    modifier: Modifier = Modifier,
    placeholderSeed: String = "",
) {
    val context = LocalContext.current
    val request = remember(coverUri) {
        if (coverUri.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context).data(coverUri).crossfade(true).build()
        }
    }
    val hue = remember(placeholderSeed) { LuteaColorExtractor.hueFor(placeholderSeed) }
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(coverHeight),
    ) {
        // placeholder (also what you see if the image fails to load)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.hsl(hue, 0.55f, 0.42f),
                            Color.hsl(hue, 0.50f, 0.20f),
                        )
                    )
                ),
        )

        if (request != null) {
            // The ONE cover image. Its RenderEffect blur radius grows with scroll progress
            // (0 -> 30px over progress 0 -> 0.55), so the artwork visibly turns from sharp
            // to blurred as the list rides over it — and it stays fully visible the whole way.
            // This is the ONLY scroll-driven effect on the cover.
            // API 31+ only (RenderEffect); below S the image simply stays sharp.
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (canBlur) {
                            val p = scrollProgress().coerceIn(0f, 1f)
                            val blurAmount = luteaSmoothstep(BLUR_IN_FROM, BLUR_IN_TO, p)
                            if (blurAmount > 0.01f) {
                                val r = BLUR_MAX_RADIUS_PX * blurAmount
                                renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                    r, r, android.graphics.Shader.TileMode.CLAMP,
                                ).asComposeRenderEffect()
                            } else {
                                renderEffect = null
                            }
                        }
                    },
            )
        }

        // blend: cover -> background colour. STATIC — the stops never depend on scroll
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val brush = Brush.verticalGradient(*blendStops(backgroundColor()))
                    onDrawBehind { drawRect(brush) }
                },
        )

        // top scrim (status bar legibility)
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind { drawRect(TopScrimBrush) },
        )
    }
}

/**
 * Vertical stops for the cover -> background blend.
 *
 *   0.00 -> clear
 *   0.56 -> clear
 *   0.84 -> background @ 78%
 *   1.00 -> background @ 100%
 *
 * Same stops as the brief, but the ramp between them is a smooth (C1) curve instead of two
 * straight lines, so there is no faint "edge" where the fade starts. The clear stops use the
 * background RGB at alpha 0 (not transparent black) so the gradient never passes through grey.
 */
private fun blendStops(bg: Color): Array<Pair<Float, Color>> {
    val clear = bg.copy(alpha = 0f)
    val stops = ArrayList<Pair<Float, Color>>(2 + BLEND_STEPS * 2)
    stops.add(0f to clear)
    stops.add(BLEND_CLEAR_UNTIL to clear)

    val firstLen = BLEND_MID_AT - BLEND_CLEAR_UNTIL
    for (i in 1..BLEND_STEPS) {
        val t = i / BLEND_STEPS.toFloat()
        val a = hermite(t, 0f, 0f, BLEND_MID_ALPHA, BLEND_JOIN_SLOPE * firstLen)
        stops.add((BLEND_CLEAR_UNTIL + firstLen * t) to bg.copy(alpha = a.coerceIn(0f, 1f)))
    }

    val secondLen = 1f - BLEND_MID_AT
    for (i in 1..BLEND_STEPS) {
        val t = i / BLEND_STEPS.toFloat()
        val a = hermite(t, BLEND_MID_ALPHA, BLEND_JOIN_SLOPE * secondLen, 1f, 0f)
        stops.add((BLEND_MID_AT + secondLen * t) to bg.copy(alpha = a.coerceIn(0f, 1f)))
    }
    return stops.toTypedArray()
}

/** Cubic Hermite. m0 / m1 are slopes already multiplied by the segment length. */
private fun hermite(t: Float, p0: Float, m0: Float, p1: Float, m1: Float): Float {
    val t2 = t * t
    val t3 = t2 * t
    return (2f * t3 - 3f * t2 + 1f) * p0 +
        (t3 - 2f * t2 + t) * m0 +
        (-2f * t3 + 3f * t2) * p1 +
        (t3 - t2) * m1
}
