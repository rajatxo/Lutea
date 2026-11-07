package com.rajatxo.lutea.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.rajatxo.lutea.ui.util.LuteaColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Light box blur on a bitmap (3 passes of H+V box blur) — used for the soft copy.
 */
private fun boxBlur(input: Bitmap, radius: Int): Bitmap {
    if (radius <= 0) return input
    val w = input.width; val h = input.height
    if (w == 0 || h == 0) return input
    val pixels = IntArray(w * h)
    input.getPixels(pixels, 0, w, 0, 0, w, h)
    repeat(3) {
        // Horizontal pass
        val row = IntArray(w)
        for (y in 0 until h) {
            val s = y * w; for (x in 0 until w) row[x] = pixels[s + x]
            for (x in 0 until w) {
                var a=0;var r=0;var g=0;var b=0;var c=0
                for (i in (x-radius).coerceAtLeast(0)..(x+radius).coerceAtMost(w-1)) {
                    val v=row[i];a+=v ushr 24;r+=v ushr 16 and 0xFF;g+=v ushr 8 and 0xFF;b+=v and 0xFF;c++
                }
                pixels[s+x]=((a/c)shl 24)or((r/c)shl 16)or((g/c)shl 8)or(b/c)
            }
        }
        // Vertical pass
        val col = IntArray(h)
        for (x in 0 until w) {
            for (y in 0 until h) col[y] = pixels[y*w+x]
            for (y in 0 until h) {
                var a=0;var r=0;var g=0;var b=0;var c=0
                for (i in (y-radius).coerceAtLeast(0)..(y+radius).coerceAtMost(h-1)) {
                    val v=col[i];a+=v ushr 24;r+=v ushr 16 and 0xFF;g+=v ushr 8 and 0xFF;b+=v and 0xFF;c++
                }
                pixels[y*w+x]=((a/c)shl 24)or((r/c)shl 16)or((g/c)shl 8)or(b/c)
            }
        }
    }
    val out = Bitmap.createBitmap(w, h, input.config ?: Bitmap.Config.ARGB_8888)
    out.setPixels(pixels, 0, w, 0, 0, w, h)
    return out
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * PlaylistCoverBackground — Mochy-inspired. SHARP cover on top, crossfades to
 * a SOFT (blurred) copy as you scroll. Color gradient blends into the page.
 *
 * Technique (exactly like Mochy's PlaylistCoverBackdrop):
 *  1. SHARP cover (full size) — alpha = 1 - smoothstep(0, 0.55, scroll)
 *     → fades out as you scroll
 *  2. SOFT cover (192px + box blur) — alpha = 0 → 1 as you scroll
 *     → fades in underneath
 *  3. Color gradient: cover color → transparent (top) + transparent → bg (bottom)
 *     so cover blends into the page
 *  4. Parallax: cover slides up at 0.8× scroll rate
 *
 * NO heavy blur on the stationary image — you see the SHARP cover at rest,
 * and only when you scroll does it crossfade to the blurred version.
 */
@Composable
fun PlaylistCoverBackground(
    albumArtUri: android.net.Uri?,
    scrollProgress: () -> Float,
    modifier: Modifier = Modifier,
    coverHeight: Dp = 320.dp,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var sharpBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var softBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var extractedColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(albumArtUri) {
        if (albumArtUri == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                // SHARP copy — load at larger size (192px) for crisp display
                val sharpReq = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .size(192)
                    .allowHardware(false)
                    .build()
                val sharp = context.imageLoader.execute(sharpReq).image?.toBitmap()

                // SOFT copy — load at 192px and blur it (for the scroll crossfade)
                val softReq = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .size(192)
                    .allowHardware(false)
                    .build()
                val softRaw = context.imageLoader.execute(softReq).image?.toBitmap()
                val soft = softRaw?.let { boxBlur(it, 8) }

                sharpBitmap = sharp
                softBitmap = soft
                if (sharp != null) {
                    extractedColor = LuteaColorExtractor.extractDominant(sharp)
                }
            } catch (_: Exception) { }
        }
    }

    val targetColor = extractedColor ?: LuteaColorExtractor.Default
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(600),
        label = "PlaylistBgColor",
    )

    Box(modifier = modifier.fillMaxSize()) {
        // ── Backdrop box — pinned behind the list, slides up on scroll (parallax) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(coverHeight)
                .graphicsLayer {
                    val p = scrollProgress()
                    // Parallax: cover follows scroll at 80% rate (slides up as you scroll)
                    translationY = -p * size.height * 0.8f
                }
        ) {
            // ── Layer 1: SOFT cover (fades IN as you scroll) ──
            softBitmap?.let { bmp ->
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Alpha goes 0 → 1 as scroll progresses
                            this.alpha = if (scrollProgress() > 0f) 1f else 0f
                        }
                )
            }

            // ── Layer 2: SHARP cover (fades OUT as you scroll) ──
            sharpBitmap?.let { bmp ->
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Crossfade: sharp fades out as soft fades in
                            this.alpha = 1f - smoothstep(0f, 0.55f, scrollProgress())
                            this.compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                        }
                )
            }

            // ── Layer 3: gradient overlay — blends cover into page color ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val bg = animatedColor
                        // Top: subtle dark for status bar contrast
                        val top = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.34f),
                            1f to Color.Transparent,
                            endY = size.height * 0.24f,
                        )
                        // Bottom: transparent → color (seamless blend into page)
                        val bottom = Brush.verticalGradient(
                            0.56f to Color.Transparent,
                            0.84f to bg.copy(alpha = 0.78f),
                            1f to bg,
                        )
                        onDrawBehind {
                            drawRect(top, size = Size(size.width, size.height * 0.24f))
                            drawRect(bottom)
                        }
                    }
            )
        }

        // ── Full-screen color tint below the cover (page background) ──
        // Fades from the extracted color at the cover's bottom edge to black.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val gradient = Brush.verticalGradient(
                        0f to Color.Transparent,           // cover area is untouched
                        0.30f to Color.Transparent,        // still cover
                        0.45f to animatedColor.copy(alpha = 0.5f),  // color starts
                        0.70f to animatedColor.copy(alpha = 0.15f),
                        1f to Color.Black,                 // solid black at bottom
                    )
                    onDrawBehind { drawRect(gradient) }
                }
        )
    }
}
