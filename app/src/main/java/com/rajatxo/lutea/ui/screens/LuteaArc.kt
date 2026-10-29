package com.rajatxo.lutea.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * LuteaArc — the playlist wheel arc.
 *
 * Inspired by the Lutea-Inspiration image + Coral's PlaylistsScreen arc technique.
 *
 * The arc is:
 *  - A partial circular curve (100° sweep)
 *  - White, ~25% opacity at center (fullAlpha = 0.25f)
 *  - 2px stroke (thin, elegant hairline)
 *  - Fades to transparent over the last 20% of each end
 *  - Positioned in the lower half of the screen (pivot at 50% height)
 *  - Radius = 65% of screen width (large imaginary circle — center is off-screen)
 *
 * The fade is achieved by drawing the arc in 40 segments, each with a
 * different alpha based on its distance from the endpoints. Segments near
 * the center are full alpha; segments near the endpoints fade linearly to 0.
 * This produces a smooth, continuous dissolve — no hard edges.
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
            // Less off-screen than before — smaller circle, more curve visible.
            val pivotX = w * -0.20f
            val pivotY = h * 0.50f

            // Radius — SMALLER circle so we see 50% of it (half circle).
            val arcRadius = w * 0.42f

            // Arc spans 180° — half the circle visible.
            val arcSweepDeg = 180f
            val arcStartDeg = -arcSweepDeg / 2f

            // Stroke — THICKER (was 2dp, now 3.5dp).
            val strokePx = with(density) { 3.5.dp.toPx() }

            // Fade — last 20% of each end fades to transparent.
            val fullAlpha = 0.25f
            val fadeRange = 0.20f

            // Draw arc in 40 segments with per-segment alpha for smooth fade.
            val arcSegments = 40
            for (i in 0 until arcSegments) {
                val segStart = i / arcSegments.toFloat()
                val segEnd = (i + 1) / arcSegments.toFloat()

                // Distance from nearest endpoint (0 at ends, 0.5 at center).
                val distFromEndpoint = minOf(segStart, 1f - segStart)

                // Alpha: full in the middle, fades to 0 near the ends.
                val segAlpha = if (distFromEndpoint > fadeRange) {
                    fullAlpha
                } else {
                    fullAlpha * (distFromEndpoint / fadeRange)
                }

                if (segAlpha <= 0.005f) continue

                drawArc(
                    color = Color.White.copy(alpha = segAlpha),
                    startAngle = arcStartDeg + segStart * arcSweepDeg,
                    sweepAngle = (segEnd - segStart) * arcSweepDeg,
                    useCenter = false,
                    topLeft = Offset(pivotX - arcRadius, pivotY - arcRadius),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = strokePx),
                )
            }
        }
    }
}
