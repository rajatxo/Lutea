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
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * LuteaArc — the playlist wheel arc (built from scratch).
 *
 * Two concentric smooth arcs + balls on the arc (one per playlist item).
 *
 * The balls:
 *  - Positioned ON the inner arc (on the curve)
 *  - Active ball (center) is LARGER (6dp) + bright white
 *  - Inactive balls are SMALLER (4dp) + dim, getting dimmer further away
 *  - Generous spacing between balls (breathable, not cluttered like Coral)
 *
 * Arcs:
 *  - 200 segments for ultra-smooth fade (no visible bands)
 *  - Needle shape: thick+bright at center, thin+dim at ends
 */
@Composable
fun LuteaArc(
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    // Sample playlist count
    val playlistCount = 8
    val activeIndex = 3

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val pivotX = w * -0.20f
            val pivotY = h * 0.50f

            val innerRadius = w * 0.42f
            val outerRadius = w * 0.52f

            val arcSweepDeg = 180f
            val arcStartDeg = -arcSweepDeg / 2f

            // ── Helper: draw smooth fading needle arc ──
            val segments = 200

            fun drawNeedleArc(radius: Float, maxStroke: Float, minStroke: Float, maxAlpha: Float) {
                val fadeRange = 0.50f

                for (i in 0 until segments) {
                    val segStart = i / segments.toFloat()
                    val segEnd = (i + 1) / segments.toFloat()

                    val distFromEndpoint = minOf(segStart, 1f - segStart)

                    val alphaFactor = if (distFromEndpoint > fadeRange) {
                        1f
                    } else {
                        (distFromEndpoint / fadeRange).pow(2.0f)
                    }
                    val segAlpha = maxAlpha * alphaFactor

                    if (segAlpha <= 0.001f) continue

                    val strokeFactor = if (distFromEndpoint > fadeRange) {
                        1f
                    } else {
                        0.15f + 0.85f * (distFromEndpoint / fadeRange).pow(2.0f)
                    }
                    val segStroke = minStroke + (maxStroke - minStroke) * strokeFactor

                    drawArc(
                        color = Color.White.copy(alpha = segAlpha),
                        startAngle = arcStartDeg + segStart * arcSweepDeg,
                        sweepAngle = (segEnd - segStart) * arcSweepDeg,
                        useCenter = false,
                        topLeft = Offset(pivotX - radius, pivotY - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = Stroke(width = segStroke),
                    )
                }
            }

            // Inner arc
            drawNeedleArc(
                radius = innerRadius,
                maxStroke = with(density) { 3.5.dp.toPx() },
                minStroke = with(density) { 0.5.dp.toPx() },
                maxAlpha = 0.8f,
            )

            // Outer arc
            drawNeedleArc(
                radius = outerRadius,
                maxStroke = with(density) { 3.5.dp.toPx() },
                minStroke = with(density) { 0.5.dp.toPx() },
                maxAlpha = 0.8f,
            )

            // ── BALLS on the inner arc ──
            // One ball per playlist item, positioned ON the arc curve.
            // Generous spacing — 22° per item (breathable, not cluttered).
            val ballSpacingDeg = 22f
            val ballFadeRange = 4 // items visible each side of active

            for (offset in -ballFadeRange..ballFadeRange) {
                val index = (activeIndex + offset)
                if (index < 0 || index >= playlistCount) continue

                // Angle on the arc for this ball
                val ballAngleDeg = offset * ballSpacingDeg
                val ballAngleRad = Math.toRadians(ballAngleDeg.toDouble())

                // Position ON the inner arc
                val ballX = pivotX + innerRadius * cos(ballAngleRad).toFloat()
                val ballY = pivotY + innerRadius * sin(ballAngleRad).toFloat()

                // Size + alpha based on distance from active
                val dist = kotlin.math.abs(offset)
                val isActive = offset == 0

                val ballRadius = if (isActive) {
                    with(density) { 6.dp.toPx() }  // active = bigger
                } else {
                    with(density) { (4f - dist * 0.5f).coerceAtLeast(1.5f).dp.toPx() }
                }

                // Alpha: bright at center, fades smoothly to 0
                val ballAlpha = if (isActive) {
                    1f
                } else {
                    (1f - dist * 0.2f).coerceAtLeast(0f)
                }

                if (ballAlpha <= 0.01f) continue

                drawCircle(
                    color = Color.White.copy(alpha = ballAlpha),
                    radius = ballRadius,
                    center = Offset(ballX, ballY),
                )
            }
        }
    }
}
