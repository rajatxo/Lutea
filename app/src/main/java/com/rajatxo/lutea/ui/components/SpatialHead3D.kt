package com.rajatxo.lutea.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 3D metallic head with headphones — drawn entirely with Canvas (no images).
 * Creates a skeuomorphic 3D look using radial/vertical gradients on a Path silhouette.
 *
 * @param accentColor The glow color for earcup rings and ambient light.
 */
@Composable
fun SpatialHead3D(
    modifier: Modifier = Modifier.size(220.dp),
    accentColor: Color = Color(0xFFFF8C24),
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        // 1. Ambient backlight (depth against dark UI)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accentColor.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(cx, h * 0.45f),
                radius = w * 0.55f,
            ),
            radius = w * 0.55f,
            center = Offset(cx, h * 0.45f),
        )

        // 2. Shoulders & neck
        val shouldersPath = Path().apply {
            moveTo(cx - w * 0.11f, h * 0.62f)
            lineTo(cx - w * 0.13f, h * 0.74f)
            quadraticBezierTo(cx - w * 0.34f, h * 0.78f, cx - w * 0.42f, h * 0.96f)
            lineTo(cx + w * 0.42f, h * 0.96f)
            quadraticBezierTo(cx + w * 0.34f, h * 0.78f, cx + w * 0.13f, h * 0.74f)
            lineTo(cx + w * 0.11f, h * 0.62f)
            close()
        }
        drawPath(
            path = shouldersPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF1A1A1D), Color(0xFF2B2B30), Color(0xFF0E0E10)),
                startY = h * 0.62f, endY = h * 0.96f,
            ),
        )
        drawPath(
            path = shouldersPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                startY = h * 0.72f, endY = h * 0.92f,
            ),
            style = Stroke(width = 1.5.dp.toPx()),
        )

        // 3. Head silhouette
        val headPath = Path().apply {
            moveTo(cx, h * 0.18f)
            cubicTo(cx + w * 0.22f, h * 0.18f, cx + w * 0.23f, h * 0.42f, cx + w * 0.19f, h * 0.54f)
            cubicTo(cx + w * 0.16f, h * 0.64f, cx + w * 0.06f, h * 0.71f, cx, h * 0.71f)
            cubicTo(cx - w * 0.06f, h * 0.71f, cx - w * 0.16f, h * 0.64f, cx - w * 0.19f, h * 0.54f)
            cubicTo(cx - w * 0.23f, h * 0.42f, cx - w * 0.22f, h * 0.18f, cx, h * 0.18f)
            close()
        }

        // 3D spherical shading
        drawPath(
            path = headPath,
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF5A5A62), Color(0xFF26262B), Color(0xFF0D0D0F)),
                center = Offset(cx, h * 0.36f),
                radius = w * 0.32f,
            ),
        )

        // 4. Eye socket shadows
        listOf(-1f, 1f).forEach { dir ->
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF070708), Color.Transparent),
                    center = Offset(cx + dir * w * 0.075f, h * 0.43f),
                    radius = w * 0.06f,
                ),
                topLeft = Offset(cx + dir * w * 0.075f - w * 0.055f, h * 0.40f),
                size = Size(w * 0.11f, h * 0.065f),
            )
        }

        // Nose bridge highlight
        drawLine(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.28f),
                    Color.White.copy(alpha = 0.12f),
                    Color.Transparent,
                ),
                startY = h * 0.40f, endY = h * 0.56f,
            ),
            start = Offset(cx, h * 0.40f),
            end = Offset(cx, h * 0.56f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Head rim light (studio-lit 3D edge)
        drawPath(
            path = headPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.45f),
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent,
                ),
                startY = h * 0.18f, endY = h * 0.70f,
            ),
            style = Stroke(width = 1.5.dp.toPx()),
        )

        // 5. Headphones — headband arc
        drawArc(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF1E1E22), Color(0xFF4A4A52), Color(0xFF1E1E22)),
            ),
            startAngle = 185f,
            sweepAngle = 170f,
            useCenter = false,
            topLeft = Offset(cx - w * 0.23f, h * 0.13f),
            size = Size(w * 0.46f, h * 0.44f),
            style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round),
        )

        // Earcups
        listOf(-1f, 1f).forEach { dir ->
            val cupWidth = w * 0.075f
            val cupHeight = h * 0.18f
            val cupLeft = if (dir < 0) cx - w * 0.245f else cx + w * 0.245f - cupWidth
            val cupTop = h * 0.36f

            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF3A3A40), Color(0xFF121215)),
                    start = Offset(cupLeft, cupTop),
                    end = Offset(cupLeft + cupWidth, cupTop + cupHeight),
                ),
                topLeft = Offset(cupLeft, cupTop),
                size = Size(cupWidth, cupHeight),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
            )

            drawRoundRect(
                color = accentColor.copy(alpha = 0.55f),
                topLeft = Offset(cupLeft + 2.dp.toPx(), cupTop + 4.dp.toPx()),
                size = Size(cupWidth - 4.dp.toPx(), cupHeight - 8.dp.toPx()),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx()),
            )
        }
    }
}
