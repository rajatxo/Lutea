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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rajatxo.lutea.R
import com.rajatxo.lutea.ui.foundation.LocalLuteaWindowInsets
import kotlin.math.min

/**
 * Effect tab visual — 3D bust image, bottom-anchored, fills screen width.
 *
 * Universal Layout Law #7: sizes are fractions of BoxWithConstraints
 * maxWidth/maxHeight — works on every screen size, multi-window, foldables.
 * No glow, no canvas tricks. Just the image, full-width, bottom-anchored.
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
        // Source PNG is ~1:1, so a square sized by width fills the bottom
        // ~45% of a typical phone screen. Cap by 55% of height so tablets
        // don't drown the screen.
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
            Image(
                painter = painterResource(headRes),
                contentDescription = "Spatial audio head",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(imgSize, imgSize),
            )
        }
    }
}
