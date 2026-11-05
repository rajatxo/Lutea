package com.rajatxo.lutea.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import coil3.transform.Transformation
import coil3.size.Precision
import com.rajatxo.lutea.ui.util.LuteaColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Light box blur baked into the bitmap at load time (Mochy technique).
 * Applied to a 48×48 bitmap, then stretched full-screen — the pixelation +
 * light blur = soft dreamy backdrop. No RenderEffect needed (works on ALL
 * Android versions, not just 12+).
 */
class BoxBlurTransformation(private val radiusPx: Int) : Transformation() {
    override val cacheKey: String = "boxblur-$radiusPx"
    override suspend fun transform(input: Bitmap, size: coil3.size.Size): Bitmap {
        if (radiusPx <= 0) return input
        val w = input.width; val h = input.height
        if (w == 0 || h == 0) return input
        val pixels = IntArray(w * h)
        input.getPixels(pixels, 0, w, 0, 0, w, h)
        repeat(3) {
            boxBlurH(pixels, w, h, radiusPx)
            boxBlurV(pixels, w, h, radiusPx)
        }
        val out = Bitmap.createBitmap(w, h, input.config ?: Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
    private fun boxBlurH(p: IntArray, w: Int, h: Int, r: Int) {
        val row = IntArray(w)
        for (y in 0 until h) {
            val s = y * w; for (x in 0 until w) row[x] = p[s + x]
            for (x in 0 until w) {
                var a=0;var rd=0;var g=0;var b=0;var c=0
                for (i in (x-r).coerceAtLeast(0)..(x+r).coerceAtMost(w-1)) {
                    val v=row[i];a+=v ushr 24;rd+=v ushr 16 and 0xFF;g+=v ushr 8 and 0xFF;b+=v and 0xFF;c++
                }
                p[s+x]=((a/c)shl 24)or((rd/c)shl 16)or((g/c)shl 8)or(b/c)
            }
        }
    }
    private fun boxBlurV(p: IntArray, w: Int, h: Int, r: Int) {
        val col = IntArray(h)
        for (x in 0 until w) {
            for (y in 0 until h) col[y] = p[y*w+x]
            for (y in 0 until h) {
                var a=0;var rd=0;var g=0;var b=0;var c=0
                for (i in (y-r).coerceAtLeast(0)..(y+r).coerceAtMost(h-1)) {
                    val v=col[i];a+=v ushr 24;rd+=v ushr 16 and 0xFF;g+=v ushr 8 and 0xFF;b+=v and 0xFF;c++
                }
                p[y*w+x]=((a/c)shl 24)or((rd/c)shl 16)or((g/c)shl 8)or(b/c)
            }
        }
    }
}

/**
 * PlaylistCoverBackground — Mochy-inspired FULL-SCREEN seamless background.
 *
 * Technique (exactly like Mochy):
 *  1. Load cover at TINY size (48×48) with BoxBlurTransformation(radius=6) baked in
 *  2. Stretch the tiny blurred bitmap full-screen via ContentScale.Crop
 *     → the pixelation + light blur = soft dreamy backdrop (NOT heavy RenderEffect)
 *  3. Extract dominant color via Palette for the gradient tint
 *  4. Full-screen gradient: extracted color → black (seamless blend)
 *  5. Subtle dark shadow at very top for status bar contrast
 *
 * NO RenderEffect — works on ALL Android versions, not just 12+.
 */
@Composable
fun PlaylistCoverBackground(
    albumArtUri: android.net.Uri?,
    scrollProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var extractedColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(albumArtUri) {
        if (albumArtUri == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .size(48)
                    .allowHardware(false)
                    .transformations(BoxBlurTransformation(radiusPx = 6))
                    .build()
                val bitmap = context.imageLoader.execute(request).image?.toBitmap()
                if (bitmap != null) {
                    extractedColor = LuteaColorExtractor.extractDominant(bitmap)
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
        // ── Layer 1: blurred tiny cover stretched full-screen ──
        // (Only the upper portion is visible — the gradient below covers the rest)
        if (albumArtUri != null) {
            coil3.compose.AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .size(48)
                    .transformations(BoxBlurTransformation(radiusPx = 6))
                    .allowHardware(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = scrollProgress()
                        translationY = -p * size.height * 0.2f
                        val zoom = 1f + p * 0.12f
                        scaleX = zoom
                        scaleY = zoom
                        transformOrigin = TransformOrigin(0.5f, 0.35f)
                        this.alpha = (1f - p * 0.7f).coerceIn(0.2f, 1f)
                    }
            )
        }

        // ── Layer 2: FULL-SCREEN gradient — color → black (seamless blend) ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val gradient = Brush.verticalGradient(
                        0f to animatedColor.copy(alpha = 0.75f),
                        0.25f to animatedColor.copy(alpha = 0.55f),
                        0.55f to animatedColor.copy(alpha = 0.25f),
                        0.8f to animatedColor.copy(alpha = 0.08f),
                        1f to Color.Black,
                    )
                    onDrawBehind { drawRect(gradient) }
                }
        )

        // ── Layer 3: subtle top shadow for status bar contrast ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawWithCache {
                    val topShadow = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f),
                        1f to Color.Transparent,
                        endY = size.height * 0.1f,
                    )
                    onDrawBehind {
                        drawRect(topShadow, size = Size(size.width, size.height * 0.1f))
                    }
                }
        )
    }
}
