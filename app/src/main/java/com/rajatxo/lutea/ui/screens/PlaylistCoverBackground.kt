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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

// Scroll choreography — the cover NEVER fades, never dissolves, never leaves the screen.
// It parallaxes up a little and gets progressively more blurred as the list scrolls over it.
private const val SCROLL_TRAVEL = 0.12f  // gentle parallax (was 0.8 + fade + dissolve -> cover vanished)
private const val ZOOM_ON_SCROLL = 0.22f // Ken Burns zoom
private const val DIM_ON_SCROLL = 0.22f  // black overlay, keeps white text readable over blurred art
private const val BLUR_IN_FROM = 0.08f
private const val BLUR_IN_TO = 0.75f
private val ZoomOrigin = TransformOrigin(0.5f, 0.35f)

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
 * The cover NEVER disappears. The old choreography translated it 80% off-screen, faded it
 * out (alpha 1 -> 0 over progress 0.20..0.85) and dissolved it bottom-up with a DstIn mask —
 * so by mid-scroll the artwork was simply gone and the blur never got a chance to show.
 * Now: the box stays pinned at the top (gentle parallax), and the artwork itself is blurred
 * in place, radius 0 -> 32dp as progress goes 0.08 -> 0.75.
 *
 * Layers, bottom to top:
 *  1. placeholder gradient (shown when there is no cover, or while it loads)
 *  2. cover (Coil AsyncImage, Crop) whose RenderEffect blur radius grows with scroll (API 31+;
 *     below S the image simply stays sharp)
 *  3. dim overlay, black x 0.22 x progress (text legibility over the blurred art)
 *  4. blend gradient: cover -> [backgroundColor], bottom edge is exactly the background (no seam)
 *  5. top scrim
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
    val density = androidx.compose.ui.platform.LocalDensity.current
    val request = remember(coverUri) {
        if (coverUri.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context).data(coverUri).crossfade(true).build()
        }
    }
    val hue = remember(placeholderSeed) { LuteaColorExtractor.hueFor(placeholderSeed) }
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Max blur radius in pixels — the radius scales from 0 to this as you scroll.
    // Mochy uses ~30px; we match. Computed once outside graphicsLayer so the lambda
    // doesn't allocate on every frame.
    val maxBlurPx = with(density) { 32.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(coverHeight)
            // transform only — no alpha fade, no dissolve mask. Layer phase, zero recomposition.
            .graphicsLayer {
                val p = scrollProgress().coerceIn(0f, 1f)
                translationY = p * size.height * -SCROLL_TRAVEL
                val s = 1f + p * ZOOM_ON_SCROLL
                scaleX = s
                scaleY = s
                transformOrigin = ZoomOrigin
            },
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
            // (0 -> 32dp over progress 0.08 -> 0.75), so the artwork visibly turns from sharp
            // to blurred as the list rides over it — and it stays fully visible the whole way.
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
                                val r = maxBlurPx * blurAmount
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

        // dim: black x 0.22 x progress (under the blend so the background colour stays pure)
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val p = scrollProgress().coerceIn(0f, 1f)
                    if (p > 0f) drawRect(Color.Black, alpha = DIM_ON_SCROLL * p)
                },
        )

        // blend: cover -> background colour. Cached; only rebuilt while the colour animates.
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
