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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.rajatxo.lutea.ui.util.LuteaColorExtractor

// ------------------------------------------------------------------ tunables

// Blend gradient: clear until 0.56, 78% background at 0.84, solid background at 1.0
private const val BLEND_CLEAR_UNTIL = 0.56f
private const val BLEND_MID_AT = 0.84f
private const val BLEND_MID_ALPHA = 0.78f
private const val BLEND_JOIN_SLOPE = 1.5f // slope at the 0.84 join, keeps the ramp smooth (C1)
private const val BLEND_STEPS = 8

// Top scrim for status-bar legibility: black 34% at the top, gone by 0.24
private const val SCRIM_ALPHA = 0.34f
private const val SCRIM_END = 0.24f

// Scroll choreography — this is Mochy's ScrollAwayCover, exactly.
// The cover is the backdrop pinned behind the list, so it has to travel most of the way up on
// its own (follow = -0.8). It zooms a little (Ken Burns), dims a little, dissolves from the
// bottom up, fades out, and softens from sharp to a 30px blur. These are Mochy's constants:
//   CoverFollow   = 0.8    CoverZoom      = 0.22   CoverFadeFrom = 0.2
//   CoverFadeTo   = 0.85   CoverDim       = 0.22   CoverSoftRadius = 30f   CoverSoftBy = 0.55
private const val COVER_FOLLOW = 0.8f        // travels up this share of its height on scroll
private const val COVER_ZOOM = 0.22f         // Ken Burns zoom
private const val COVER_FADE_FROM = 0.2f     // alpha fade starts here
private const val COVER_FADE_TO = 0.85f      // alpha fade ends here (cover gone)
private const val COVER_DIM = 0.22f          // black overlay, grows with scroll
private const val DISSOLVE_RAMP_TO = 0.8f    // bottom dissolve reaches full strength here
private const val BLUR_MAX_RADIUS_PX = 30f   // actual pixels (Mochy CoverSoftRadius) — NOT dp
private const val BLUR_IN_FROM = 0f          // blur starts immediately
private const val BLUR_IN_TO = 0.55f         // blur reaches full strength here
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
 * This mirrors Mochy's [ScrollAwayCover] for the playlist backdrop: the cover rides up, zooms,
 * dims, dissolves from the bottom, fades out, and softens from sharp to a 30px blur as the list
 * scrolls over it. Constants match Mochy exactly (CoverFollow/CoverZoom/CoverFadeFrom/CoverFadeTo/
 * CoverDim/CoverSoftRadius/CoverSoftBy) so the behaviour is the one the user already likes.
 *
 * There is NO second blur layer on top of this — the kyant top blur that used to sit above the
 * list is gone. The cover's own RenderEffect blur is the only blur.
 *
 * Layers, bottom to top:
 *  1. placeholder gradient (shown when there is no cover, or while it loads)
 *  2. cover (Coil AsyncImage, Crop) whose RenderEffect blur radius grows with scroll (API 31+;
 *     below S the image simply stays sharp)
 *  3. blend gradient: cover -> [backgroundColor] — seamless fade into the page colour
 *  4. top scrim
 *
 * The container's graphicsLayer applies the scroll-away transform (translate / zoom / alpha) and
 * the drawWithContent below paints the dim + bottom dissolve on top of everything inside.
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
            .height(coverHeight)
            .graphicsLayer {
                val p = scrollProgress().coerceIn(0f, 1f)
                translationY = p * size.height * -COVER_FOLLOW
                val zoom = 1f + p * COVER_ZOOM
                scaleX = zoom
                scaleY = zoom
                transformOrigin = ZoomOrigin
                alpha = 1f - luteaSmoothstep(COVER_FADE_FROM, COVER_FADE_TO, p)
                // the bottom dissolve cuts into its own buffer; at rest there's nothing to cut
                compositingStrategy =
                    if (p > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
            .drawWithContent {
                drawContent()
                val p = scrollProgress().coerceIn(0f, 1f)
                if (p > 0f) {
                    // dim
                    drawRect(Color.Black.copy(alpha = COVER_DIM * p))
                    // bottom dissolve — DstIn with a soft gradient cuts the lower band away
                    val clear = 0.1f + 0.45f * luteaSmoothstep(0f, DISSOLVE_RAMP_TO, p)
                    val top = size.height * (1f - clear)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            1f to Color.Transparent,
                            startY = top,
                            endY = size.height,
                        ),
                        topLeft = Offset(0f, top),
                        size = Size(size.width, size.height - top),
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
            // The cover. Its RenderEffect blur radius grows with scroll progress
            // (0 -> 30px over progress 0 -> 0.55), Mochy's CoverSoftRadius / CoverSoftBy.
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
