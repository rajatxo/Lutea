package com.rajatxo.lutea.ui.screens

import android.graphics.Bitmap
import android.graphics.Shader
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
 * PlaylistCoverBackground — Mochy-inspired seamless blurred cover background.
 *
 * What it does:
 *  1. Loads the first song's album art at TINY size (96×96) — cheap
 *  2. Extracts the dominant color via Palette (on Dispatchers.Default)
 *  3. Animates the extracted color over 600ms when it changes
 *  4. Draws the cover image (blurred via Modifier.blur) filling the band
 *  5. Overlays a vertical gradient: tinted color @ 18% → 40% → 92% → solid black
 *     — this is the seamless blend from cover into the page color
 *  6. As the user scrolls up, the page color washes over the backdrop (scroll fade)
 *
 * No paid APIs, no API keys — all local image processing.
 *
 * @param albumArtUri    URI of the first song's album art (or null = fallback color)
 * @param bandHeight     How tall the backdrop band is (e.g. 280.dp)
 * @param scrollProgress 0f = top of list, 1f = fully scrolled away
 */
@Composable
fun PlaylistCoverBackground(
    albumArtUri: android.net.Uri?,
    bandHeight: Dp,
    scrollProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var extractedColor by remember { mutableStateOf<Color?>(null) }

    // ★ Load cover at tiny size + extract color on background threads.
    LaunchedEffect(albumArtUri) {
        if (albumArtUri == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .size(96)  // tiny — keeps blur cheap
                    .allowHardware(false)  // need bitmap for Palette
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
        animationSpec = tween(durationMillis = 600),
        label = "PlaylistBgColor",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bandHeight)
            // ★ Parallax: slide up at 50% of scroll + zoom 22% as you scroll away
            .graphicsLayer {
                val p = scrollProgress()
                translationY = -p * size.height * 0.5f
                val zoom = 1f + p * 0.22f
                scaleX = zoom
                scaleY = zoom
                transformOrigin = TransformOrigin(0.5f, 0.35f)
            }
            // ★ Scroll fade: page color washes over the backdrop as you scroll up.
            .drawWithContent {
                drawContent()
                val fade = smoothstep(0.2f, 0.85f, scrollProgress())
                if (fade > 0f) {
                    drawRect(Color.Black.copy(alpha = fade))
                }
            }
    ) {
        // Layer 1: blurred album art (fill the band)
        if (albumArtUri != null) {
            coil3.compose.AsyncImage(
                model = albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Heavy blur (rendered on Android 12+ via RenderEffect;
                        // falls back to no-op on older Android — still shows the cover)
                        this.renderEffect = if (
                            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                        ) {
                            val radiusPx = 32f  // dp-equivalent heavy blur
                            android.graphics.RenderEffect.createBlurEffect(
                                radiusPx, radiusPx,
                                Shader.TileMode.DECAL
                            ).asComposeRenderEffect()
                        } else null
                        this.compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    }
            )
        }

        // Layer 2: bottom gradient — tinted color → solid black (seamless blend)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val bg = animatedColor
                    val bottom = Brush.verticalGradient(
                        0f to bg.copy(alpha = 0.18f),     // top: tint faint (cover visible)
                        0.5f to bg.copy(alpha = 0.4f),    // mid: tint stronger
                        0.85f to bg.copy(alpha = 0.92f),  // almost solid tint
                        1f to Color.Black,                // bottom: solid black (page bg)
                    )
                    val topShadow = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.3f),
                        1f to Color.Transparent,
                        endY = size.height * 0.25f,
                    )
                    onDrawBehind {
                        drawRect(bottom)
                        drawRect(topShadow, size = Size(size.width, size.height * 0.25f))
                    }
                }
        )
    }
}

/** Smoothstep — ease-in-out interpolation between [edge0] and [edge1]. */
private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
