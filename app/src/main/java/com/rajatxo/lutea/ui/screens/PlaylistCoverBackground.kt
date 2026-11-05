package com.rajatxo.lutea.ui.screens

import android.graphics.Shader
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.rajatxo.lutea.ui.util.LuteaColorExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PlaylistCoverBackground — Mochy-inspired FULL-SCREEN seamless background.
 *
 * The key insight from Mochy: the background fills the ENTIRE screen, not just
 * a band. The extracted color tints everything, fading from full color at the
 * top → near-black at the bottom. The blurred cover sits in the upper portion.
 *
 * Layers (back to front):
 *  1. Full-screen vertical gradient: extracted color → black
 *  2. Blurred cover image (top ~50% of screen, parallax + zoom on scroll)
 *  3. Gradient overlay on the cover: color → transparent (top) + color → black (bottom)
 *     so the cover blends seamlessly into the full-screen gradient
 */
@Composable
fun PlaylistCoverBackground(
    albumArtUri: android.net.Uri?,
    scrollProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var extractedColor by remember { mutableStateOf<Color?>(null) }

    // ★ Load cover at tiny size + extract color on background threads.
    LaunchedEffect(albumArtUri) {
        if (albumArtUri == null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .size(96)
                    .allowHardware(false)
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
        modifier = modifier.fillMaxSize()
    ) {
        // ── Layer 1: FULL-SCREEN gradient — extracted color → black ──
        // This tints the entire screen, not just a band. The color fades from
        // full at top → 55% at 35% → 18% at 65% → page black at bottom.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val gradient = Brush.verticalGradient(
                        0f to animatedColor,
                        0.35f to animatedColor.copy(alpha = 0.55f),
                        0.65f to animatedColor.copy(alpha = 0.18f),
                        1f to Color.Black,
                    )
                    onDrawBehind { drawRect(gradient) }
                }
        )

        // ── Layer 2: blurred cover image (top ~50% of screen) ──
        // Parallax: slides up + zooms as you scroll. Fades into the background
        // color via a gradient overlay so there's no hard edge.
        if (albumArtUri != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = scrollProgress()
                        translationY = -p * size.height * 0.3f
                        val zoom = 1f + p * 0.15f
                        scaleX = zoom
                        scaleY = zoom
                        transformOrigin = TransformOrigin(0.5f, 0.35f)
                        // Fade the cover out as you scroll.
                        this.alpha = (1f - p * 0.8f).coerceIn(0f, 1f)
                    }
            ) {
                coil3.compose.AsyncImage(
                    model = albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            this.renderEffect = if (
                                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                            ) {
                                val radiusPx = with(density) { 32.dp.toPx() }
                                android.graphics.RenderEffect.createBlurEffect(
                                    radiusPx, radiusPx,
                                    Shader.TileMode.DECAL
                                ).asComposeRenderEffect()
                            } else null
                            this.compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        }
                )

                // ── Layer 3: gradient overlay on cover — blends into the background ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithCache {
                            // Top: subtle dark for status bar contrast
                            val topShadow = Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.3f),
                                1f to Color.Transparent,
                                endY = size.height * 0.15f,
                            )
                            // Bottom: blend cover into the page color
                            val bottomBlend = Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.5f to animatedColor.copy(alpha = 0.3f),
                                0.8f to animatedColor.copy(alpha = 0.7f),
                                1f to animatedColor,
                            )
                            onDrawBehind {
                                drawRect(bottomBlend)
                                drawRect(topShadow, size = Size(size.width, size.height * 0.15f))
                            }
                        }
                )
            }
        }
    }
}
