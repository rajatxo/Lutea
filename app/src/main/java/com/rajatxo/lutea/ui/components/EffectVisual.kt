package com.rajatxo.lutea.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rajatxo.lutea.R
import com.rajatxo.lutea.ui.foundation.LocalLuteaWindowInsets
import kotlin.math.min

private val GinkgoGold = Color(0xFFD4A03A)

/**
 * Effect tab visual — image-based bust with breathing ginkgo-gold backlight.
 *
 * Universal Layout Law #7: sizes are fractions of BoxWithConstraints
 * maxHeight/maxWidth — works on every screen size, multi-window, foldables.
 * No SDF, no Filament, no ray marching. Just image + glow + fade-in.
 */
@Composable
fun EffectVisual(
    modifier: Modifier = Modifier,
    headRes: Int = R.drawable.effect_head,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(LocalLuteaWindowInsets.current),
    ) {
        // Law 7: fractional sizing, never screenHeightDp.
        val headHeight: Dp = maxHeight * 0.28f
        val bottomMargin: Dp = maxHeight * 0.05f
        // Head shouldn't overflow narrow widths — cap by 90% of width.
        val headSize: Dp = min(headHeight.value, maxWidth.value * 0.9f).dp

        // Fade-in on first composition.
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(900, easing = FastOutSlowInEasing),
            label = "effectFadeIn",
        )

        // Breathing glow — subtle radial pulsation behind the head.
        val breath by rememberInfiniteTransition(label = "breath").animateFloat(
            initialValue = 0.85f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breathScale",
        )
        val breathAlpha by rememberInfiniteTransition(label = "breathA").animateFloat(
            initialValue = 0.30f,
            targetValue = 0.60f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "breathAlpha",
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = bottomMargin)
                    .size(headSize),
                contentAlignment = Alignment.Center,
            ) {
                // Glow behind the head — drawn first so the image sits on top.
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = breath
                            scaleY = breath
                        },
                ) {
                    val canvasSize = this.size
                    val radius = canvasSize.minDimension * 0.7f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GinkgoGold.copy(alpha = breathAlpha),
                                GinkgoGold.copy(alpha = breathAlpha * 0.3f),
                                Color.Transparent,
                            ),
                            center = Offset(canvasSize.width / 2f, canvasSize.height * 0.42f),
                            radius = radius,
                        ),
                    )
                }

                Image(
                    painter = painterResource(headRes),
                    contentDescription = "Spatial audio head",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
