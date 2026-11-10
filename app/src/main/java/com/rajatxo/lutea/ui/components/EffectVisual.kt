package com.rajatxo.lutea.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Effect tab visual — 3D bust image, bottom-anchored, sized to leave the same
 * 7.5% side gap the playlist wheel slider uses (so it lines up with the rest
 * of the UI). Lower half blends into pure black via the same Hermite-smoothstep
 * gradient as the playlist cover ([blendStops]).
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
        // Match the wheel slider's 7.5% side gap on BOTH edges — see
        // LuteaArc.kt: trackPaddingEnd = maxWidth * 0.075f.
        val imgSizeDp = with(androidx.compose.ui.platform.LocalDensity.current) {
            val widthPx = maxWidth.toPx()
            val heightPx = maxHeight.toPx()
            val sideGapPx = widthPx * 0.075f
            val sizePx = min(widthPx - sideGapPx * 2, heightPx * 0.5f)
            sizePx.toDp()
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(modifier = Modifier.size(imgSizeDp, imgSizeDp)) {
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
