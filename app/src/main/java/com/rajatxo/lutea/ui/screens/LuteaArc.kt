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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.lutea.R
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow

/**
 * LuteaArc — the playlist wheel arc (built from scratch).
 *
 * Two concentric arcs with ultra-smooth fading + sample text on the arc.
 *
 * Inner arc (radius 0.42w):
 *  - Needle shape: thick+bright at center, thin+dim at ends
 *  - 200 segments for buttery-smooth fade (no visible bands)
 *
 * Outer arc (radius 0.52w):
 *  - Same needle style, slightly bigger gap from inner
 *
 * Text: Instrument Serif font, rotated to follow the curve.
 * Active item is large + bright white. Inactive items are smaller + dim gray.
 */
@Composable
fun LuteaArc(
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    // Instrument Serif font (OFL licensed)
    val instrumentSerif = FontFamily(
        Font(R.font.instrument_serif, FontWeight.Normal),
        Font(R.font.instrument_serif_italic, FontWeight.Normal),  // italic variant if available
    )

    // Sample playlist names for the wheel
    val playlistNames = listOf("Chill Vibes", "Workout", "Late Night", "Road Trip", "Acoustic", "Jazz", "Lo-Fi", "Classical")
    val activeIndex = 3 // "Road Trip" is the active/selected one

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
            val outerRadius = w * 0.52f  // bigger gap (was 0.48)

            val arcSweepDeg = 180f
            val arcStartDeg = -arcSweepDeg / 2f

            // ── Helper: draw a smooth fading needle arc ──
            // 200 segments = ultra-smooth, NO visible bands
            val segments = 200

            fun drawNeedleArc(radius: Float, maxStroke: Float, minStroke: Float, maxAlpha: Float) {
                val fadeRange = 0.50f

                for (i in 0 until segments) {
                    val segStart = i / segments.toFloat()
                    val segEnd = (i + 1) / segments.toFloat()

                    val distFromEndpoint = minOf(segStart, 1f - segStart)

                    // Ultra-smooth fade using pow(2.0) — 200 segments means
                    // each step is tiny, so NO visible banding
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

            // Outer arc — same brightness, bigger radius
            drawNeedleArc(
                radius = outerRadius,
                maxStroke = with(density) { 3.5.dp.toPx() },
                minStroke = with(density) { 0.5.dp.toPx() },
                maxAlpha = 0.8f,
            )

            // ── Text on the arc ──
            // Each playlist name is positioned along the outer arc's circumference.
            // Active item is large + bright. Others are smaller + dim.
            val textRadius = outerRadius + with(density) { 20.dp.toPx() }
            val visibleRange = 4 // items each side of active

            for (offset in -visibleRange..visibleRange) {
                val index = (activeIndex + offset).coerceIn(0, playlistNames.lastIndex)
                val name = playlistNames[index]

                // Angle for this item on the arc
                val itemAngle = offset * 22f // 22° per item
                val angleRad = Math.toRadians(itemAngle.toDouble())

                // Position on the arc circumference
                val textX = pivotX + textRadius * cos(angleRad).toFloat()
                val textY = pivotY + textRadius * sin(angleRad).toFloat()

                // Rotation — text baseline follows the curve (perpendicular to radius)
                val rotationDeg = itemAngle - 90f // -90 so text is upright at center

                // Size + alpha based on distance from active item
                val dist = kotlin.math.abs(offset)
                val isActive = offset == 0
                val fontSize = if (isActive) 42f else (30f - dist * 3f).coerceAtLeast(12f)
                val alpha = if (isActive) 1f else (1f - dist * 0.18f).coerceAtLeast(0.1f)
                val textColor = if (isActive) Color.White else Color.White.copy(alpha = alpha)

                // Draw text using native canvas (for rotation)
                drawContext.canvas.nativeCanvas.apply {
                    save()
                    translate(textX, textY)
                    rotate(rotationDeg)
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(
                            (alpha * 255).toInt(), 255, 255, 255
                        )
                        textSize = with(density) { fontSize.sp.toPx() }
                        typeface = android.graphics.Typeface.create(
                            android.graphics.Typeface.SERIF,
                            if (isActive) android.graphics.Typeface.NORMAL else android.graphics.Typeface.NORMAL
                        )
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawText(name, 0f, 0f, paint)
                    restore()
                }
            }
        }
    }
}
