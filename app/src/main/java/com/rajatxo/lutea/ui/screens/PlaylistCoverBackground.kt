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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.rajatxo.lutea.ui.util.LuteaColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Light box blur on a bitmap (Mochy technique — 3 passes of H+V box blur).
 * Applied to a tiny bitmap (48×48), then stretched full-screen.
 */
private fun boxBlur(input: Bitmap, radius: Int): Bitmap {
    if (radius <= 0) return input
    val w = input.width; val h = input.height
    if (w == 0 || h == 0) return input
    val pixels = IntArray(w * h)
    input.getPixels(pixels, 0, w, 0, 0, w, h)
    repeat(3) {
        // Horizontal
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
        // Vertical
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

/**
 * PlaylistCoverBackground — Mochy-inspired FULL-SCREEN seamless background.
 *
 * Technique (exactly like Mochy):
 *  1. Load cover at TINY size (48×48)
 *  2. Apply box blur (radius 6, 3 passes) to the bitmap manually
 *  3. Extract dominant color via Palette
 *  4. Display the pre-blurred tiny bitmap stretched full-screen
 *     → pixelation + light blur = soft dreamy backdrop (NOT heavy RenderEffect)
 *  5. Full-screen gradient: extracted color → black (seamless blend)
 *
 * NO RenderEffect — works on ALL Android versions.
 */
@Composable
fun PlaylistCoverBackground(
    albumArtUri: android.net.Uri?,
    scrollProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var blurredBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var extractedColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(albumArtUri) {
        if (albumArtUri == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .size(48)
                    .allowHardware(false)
                    .build()
                val raw = context.imageLoader.execute(request).image?.toBitmap()
                if (raw != null) {
                    blurredBitmap = boxBlur(raw, 6)
                    extractedColor = LuteaColorExtractor.extractDominant(raw)
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
        // ── Layer 1: pre-blurred tiny bitmap stretched full-screen ──
        blurredBitmap?.let { bmp ->
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
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

        // ── Layer 2: FULL-SCREEN gradient — color → black ──
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

        // ── Layer 3: subtle top shadow ──
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
