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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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

// Scroll choreography
private const val SCROLL_TRAVEL = 0.8f   // cover rides up at 80% of scroll speed
private const val ZOOM_ON_SCROLL = 0.22f // Ken Burns zoom
private const val DIM_ON_SCROLL = 0.22f  // black overlay
private const val FADE_FROM = 0.20f
private const val FADE_TO = 0.85f
private const val DISSOLVE_TO = 0.90f
private const val DISSOLVE_BAND = 0.35f  // softness of the bottom-up dissolve edge
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
 * Layers, bottom to top (all inside one box so they move/fade/dissolve together):
 *  1. placeholder gradient (shown when there is no cover, or while it loads)
 *  2. sharp cover (Coil AsyncImage, Crop)
 *  3. blurred copy of the cover, crossfaded in as you scroll (API 31+ only)
 *  4. dim overlay, black x 0.22 x progress
 *  5. blend gradient: cover -> [backgroundColor], bottom edge is exactly the background (no seam)
 *  6. top scrim
 *
 * The whole box is then transformed: translation, zoom, fade, and a DstIn mask that eats the
 * cover from the bottom up.
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
            // 1) transform + fade (layer phase, zero recomposition)
            .graphicsLayer {
                val p = scrollProgress().coerceIn(0f, 1f)
                translationY = p * size.height * -SCROLL_TRAVEL
                val s = 1f + p * ZOOM_ON_SCROLL
                scaleX = s
                scaleY = s
                transformOrigin = ZoomOrigin
                alpha = 1f - luteaSmoothstep(FADE_FROM, FADE_TO, p)
                // needed so the DstIn mask below only eats THIS layer, not what is behind it
                compositingStrategy = CompositingStrategy.Offscreen
            }
            // 2) dissolve from the bottom up: DstIn gradient mask that grows as you scroll
            .drawWithContent {
                drawContent()
                val dissolve = luteaSmoothstep(0f, DISSOLVE_TO, scrollProgress().coerceIn(0f, 1f))
                if (dissolve > 0f) {
                    val edge = (1f + DISSOLVE_BAND) * (1f - dissolve) // where alpha reaches 0
                    val start = (edge - DISSOLVE_BAND).coerceIn(0f, 1f)
                    val end = edge.coerceIn(0f, 1f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            start to Color.Black,
                            end to Color.Transparent,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
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
            // sharp cover
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Progressive blur: a second copy of the cover whose RenderEffect blur
            // RADIUS grows with scroll progress (0 → 32dp over the first 75% of scroll).
            // The previous version used a fixed Modifier.blur(28.dp) — the blur amount
            // never changed, only the alpha faded in. Now the cover actually gets more
            // blurry the further you scroll, matching Mochy's ScrollAwayCover behaviour.
            // API 31+ only (RenderEffect); below that we rely on the fade + dim alone.
            if (canBlur) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = scrollProgress().coerceIn(0f, 1f)
                            val blurAmount = luteaSmoothstep(BLUR_IN_FROM, BLUR_IN_TO, p)
                            alpha = blurAmount
                            if (blurAmount > 0.01f) {
                                val r = maxBlurPx * blurAmount
                                renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                    r, r, android.graphics.Shader.TileMode.CLAMP,
                                ).asComposeRenderEffect()
                            } else {
                                renderEffect = null
                            }
                        },
                )
            }
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
