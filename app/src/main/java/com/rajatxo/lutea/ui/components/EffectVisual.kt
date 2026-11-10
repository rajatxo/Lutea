package com.rajatxo.lutea.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rajatxo.lutea.R
import com.rajatxo.lutea.ui.foundation.LocalLuteaWindowInsets
import com.rajatxo.lutea.ui.screens.blendStops
import kotlin.math.min

/**
 * Effect tab visual — 3D bust image, bottom-anchored, fills screen width,
 * with its lower half fading into pure black via the same Hermite-smoothstep
 * gradient the playlist cover uses ([blendStops]).
 *
 * Universal Layout Law #7: sizes are fractions of BoxWithConstraints
 * maxWidth/maxHeight — works on every screen size, multi-window, foldables.
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
        val imgSize = min(maxWidth.value, maxHeight.value * 0.55f).dp

        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(900, easing = FastOutSlowInEasing),
            label = "effectFadeIn",
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(modifier = Modifier.size(imgSize, imgSize)) {
                Image(
                    painter = painterResource(headRes),
                    contentDescription = "Spatial audio head",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                // Bottom fade: transparent until 56%, ramps to black at 100%.
                // Same blend curve as the playlist cover — reuse, don't reinvent.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawWithCache {
                            val brush = Brush.verticalGradient(*blendStops(Color.Black))
                            onDrawBehind { drawRect(brush) }
                        },
                )
            }
        }
    }
}
