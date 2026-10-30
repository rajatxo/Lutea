package com.rajatxo.lutea.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/**
 * LuteaArc — the playlist wheel arc (built from scratch, not copied).
 *
 * Two concentric arcs:
 *
 * 1. INNER ARC (the "needle"):
 *    - Bright at center, fades to 0 at both ends
 *    - Thickness tapers: thick in middle → thin at ends (like a needle)
 *    - 180° sweep, radius = 42% screen width
 *    - White, max alpha 0.8 at center (bright), 0 at ends (completely faded)
 *
 * 2. OUTER ARC (the "halo"):
 *    - Wider, dimmer, sits OUTSIDE the inner arc
 *    - Same 180° sweep but larger radius
 *    - White, low alpha 0.08 — barely visible ghost ring
 *    - Provides depth/structure (like Coral's second arc concept)
 *
 * The "needle" effect: stroke width varies per segment.
 * Center segments are thickest (3.5dp), end segments taper to 0.5dp.
 * Combined with the alpha fade, the arc looks like a glowing needle
 * that's brightest and thickest at its apex, thinning and dimming
 * toward both ends until it vanishes.
 */
@Composable
fun LuteaArc(
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Pivot — center of the imaginary circle.
            val pivotX = w * -0.20f
            val pivotY = h * 0.50f

            // Inner arc — the "needle"
            val innerRadius = w * 0.42f

            // Outer arc — the "halo" (bigger, same brightness as inner)
            val outerRadius = w * 0.48f  // was 0.55 — closer to inner (less gap)

            // Both arcs span 180° — half circle.
            val arcSweepDeg = 180f
            val arcStartDeg = -arcSweepDeg / 2f

            // ── INNER ARC: needle shape (tapered thickness + alpha fade) ──
            // Max thickness at center (3.5dp), tapers to 0.5dp at ends.
            // Max alpha at center (0.8 — bright), COMPLETELY fades to 0 at ends.
            val innerMaxStroke = with(density) { 3.5.dp.toPx() }
            val innerMinStroke = with(density) { 0.5.dp.toPx() }
            val innerMaxAlpha = 0.8f  // bright at center
            val innerFadeRange = 0.50f  // last 50% of each end fades — MORE fade

            val segments = 60
            for (i in 0 until segments) {
                val segStart = i / segments.toFloat()
                val segEnd = (i + 1) / segments.toFloat()

                // Distance from nearest endpoint (0 at ends, 0.5 at center)
                val distFromEndpoint = minOf(segStart, 1f - segStart)

                // Alpha: bright at center, SMOOTH complete fade to 0 at ends.
                // Uses pow(2.0) for a steeper curve — ends fade MORE aggressively.
                val alphaFactor = if (distFromEndpoint > innerFadeRange) {
                    1f
                } else {
                    (distFromEndpoint / innerFadeRange).pow(2.0f)
                }
                val segAlpha = innerMaxAlpha * alphaFactor

                if (segAlpha <= 0.001f) continue  // skip nearly-invisible segments

                // Thickness: tapers from max (center) to min (ends)
                val strokeFactor = if (distFromEndpoint > innerFadeRange) {
                    1f
                } else {
                    0.15f + 0.85f * (distFromEndpoint / innerFadeRange).pow(2.0f)
                }
                val segStroke = innerMinStroke + (innerMaxStroke - innerMinStroke) * strokeFactor

                drawArc(
                    color = Color.White.copy(alpha = segAlpha),
                    startAngle = arcStartDeg + segStart * arcSweepDeg,
                    sweepAngle = (segEnd - segStart) * arcSweepDeg,
                    useCenter = false,
                    topLeft = Offset(pivotX - innerRadius, pivotY - innerRadius),
                    size = Size(innerRadius * 2f, innerRadius * 2f),
                    style = Stroke(width = segStroke),
                )
            }

            // ── OUTER ARC: SAME needle style as inner, just bigger + dimmer ──
            // Same tapered thickness, same fade curve — just a larger radius
            // and lower max alpha so it's a subtle halo around the inner arc.
            val outerMaxStroke = with(density) { 3.5.dp.toPx() }  // same as inner
            val outerMinStroke = with(density) { 0.5.dp.toPx() }  // same as inner
            val outerMaxAlpha = 0.8f  // same brightness as inner (was 0.2)
            val outerFadeRange = 0.50f  // same fade range as inner

            for (i in 0 until segments) {
                val segStart = i / segments.toFloat()
                val segEnd = (i + 1) / segments.toFloat()

                val distFromEndpoint = minOf(segStart, 1f - segStart)

                // Same fade curve as inner arc
                val alphaFactor = if (distFromEndpoint > outerFadeRange) {
                    1f
                } else {
                    (distFromEndpoint / outerFadeRange).pow(2.0f)
                }
                val segAlpha = outerMaxAlpha * alphaFactor

                if (segAlpha <= 0.001f) continue

                // Same tapered thickness as inner arc
                val strokeFactor = if (distFromEndpoint > outerFadeRange) {
                    1f
                } else {
                    0.15f + 0.85f * (distFromEndpoint / outerFadeRange).pow(2.0f)
                }
                val segStroke = outerMinStroke + (outerMaxStroke - outerMinStroke) * strokeFactor

                drawArc(
                    color = Color.White.copy(alpha = segAlpha),
                    startAngle = arcStartDeg + segStart * arcSweepDeg,
                    sweepAngle = (segEnd - segStart) * arcSweepDeg,
                    useCenter = false,
                    topLeft = Offset(pivotX - outerRadius, pivotY - outerRadius),
                    size = Size(outerRadius * 2f, outerRadius * 2f),
                    style = Stroke(width = segStroke),
                )
            }
        }
    }
}
