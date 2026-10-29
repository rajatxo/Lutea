package com.rajatxo.lutea.ui.util

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * ★ Consistent navigation bar padding — always at least 48dp (button nav height).
 * Ported from Coral X's ConsistentNavBarPadding.kt (Universal Layout Law).
 *
 * Problem: navigationBarsPadding() returns DIFFERENT values depending on the
 * navigation mode:
 *   - Gesture nav: ~16-24dp (thin gesture bar)
 *   - Button nav: ~48dp (3 buttons)
 *
 * This causes the tab capsule, miniplayer, etc. to sit at different
 * heights in different modes. In gesture nav, things sit too LOW and overlap.
 *
 * Fix: always use max(systemInset, 48dp). This makes both modes look
 * identical — as if button navigation is always active.
 */
fun Modifier.consistentNavBarPadding(): Modifier = composed {
    val density = LocalDensity.current
    val systemInset = WindowInsets.navigationBars.getBottom(density)
    val minInset = with(density) { 48.dp.toPx().toInt() }
    val effectiveInset = maxOf(systemInset, minInset)
    this.then(Modifier.padding(bottom = with(density) { effectiveInset.toDp() }))
}

/**
 * Get the consistent navigation bar bottom inset in dp.
 * Always at least 48dp (button nav height).
 */
@Composable
fun consistentNavBottomPadding(): androidx.compose.ui.unit.Dp {
    val density = LocalDensity.current
    val systemInset = WindowInsets.navigationBars.getBottom(density)
    val minInset = with(density) { 48.dp.toPx().toInt() }
    val effectiveInset = maxOf(systemInset, minInset)
    return with(density) { effectiveInset.toDp() }
}
