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

            // Outer arc — the "halo" (bigger, dimmer)
            val outerRadius = w * 0.55f

            // Both arcs span 180° — half circle.
            val arcSweepDeg = 180f
            val arcStartDeg = -arcSweepDeg / 2f

            // ── INNER ARC: needle shape (tapered thickness + alpha fade) ──
            // Max thickness at center (3.5dp), tapers to 0.5dp at ends.
            // Max alpha at center (0.8 — bright), fades to 0 at ends.
            val innerMaxStroke = with(density) { 3.5.dp.toPx() }
            val innerMinStroke = with(density) { 0.5.dp.toPx() }
            val innerMaxAlpha = 0.8f  // bright at center
            val innerFadeRange = 0.45f  // last 45% of each end fades

            val segments = 60
            for (i in 0 until segments) {
                val segStart = i / segments.toFloat()
                val segEnd = (i + 1) / segments.toFloat()

                // Distance from nearest endpoint (0 at ends, 0.5 at center)
                val distFromEndpoint = minOf(segStart, 1f - segStart)

                // Alpha: bright at center, smooth fade to 0 at ends.
                // Uses a smooth curve (not linear) for organic fade.
                val alphaFactor = if (distFromEndpoint > innerFadeRange) {
                    1f
                } else {
                    // Smooth ease-out: starts fading slowly, accelerates near the end
                    (distFromEndpoint / innerFadeRange).pow(1.5f)
                }
                val segAlpha = innerMaxAlpha * alphaFactor

                if (segAlpha <= 0.005f) continue

                // Thickness: tapers from max (center) to min (ends)
                val strokeFactor = if (distFromEndpoint > innerFadeRange) {
                    1f
                } else {
                    0.3f + 0.7f * (distFromEndpoint / innerFadeRange).pow(1.5f)
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

            // ── OUTER ARC: halo (wider, dimmer, uniform thin) ──
            // Barely visible ghost ring that provides structure/depth.
            val outerStroke = with(density) { 1.dp.toPx() }
            val outerMaxAlpha = 0.08f  // very dim
            val outerFadeRange = 0.35f  // fades over last 35% of each end

            for (i in 0 until segments) {
                val segStart = i / segments.toFloat()
                val segEnd = (i + 1) / segments.toFloat()

                val distFromEndpoint = minOf(segStart, 1f - segStart)

                val segAlpha = if (distFromEndpoint > outerFadeRange) {
                    outerMaxAlpha
                } else {
                    outerMaxAlpha * (distFromEndpoint / outerFadeRange).pow(1.5f)
                }

                if (segAlpha <= 0.005f) continue

                drawArc(
                    color = Color.White.copy(alpha = segAlpha),
                    startAngle = arcStartDeg + segStart * arcSweepDeg,
                    sweepAngle = (segEnd - segStart) * arcSweepDeg,
                    useCenter = false,
                    topLeft = Offset(pivotX - outerRadius, pivotY - outerRadius),
                    size = Size(outerRadius * 2f, outerRadius * 2f),
                    style = Stroke(width = outerStroke),
                )
            }
        }
    }
}
