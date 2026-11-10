package com.rajatxo.lutea.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SpatialAudioControllerCard(
    modifier: Modifier = Modifier,
    xVal: String = "1.50",
    yVal: String = "1.05",
    zVal: String = "0.05",
    accentColor: Color = Color(0xFFFF7A00) // Vibrant spatial orange
) {
    // Floating animation for the spatial sound source orb
    val infiniteTransition = rememberInfiniteTransition(label = "spatial_anim")
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_float"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF121215)) // Deep dark card background matching reference
            .padding(16.dp)
    ) {
        // --- 1. LEFT SIDE: X, Y, Z Coordinate Readouts ---
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp, start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CoordinateLabel(axis = "X", value = xVal)
            CoordinateLabel(axis = "Y", value = yVal)
            CoordinateLabel(axis = "Z", value = zVal)
        }

        // --- 2. RIGHT SIDE: HRTF Info & Rotation Icon ---
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 4.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF222226)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u21BB",
                    color = accentColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "HRTF",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "+3dB",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "/ 2kHz",
                color = Color.Gray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // --- 3. CENTER & BACKGROUND: Canvas for Dot Mesh, Head & Orb ---
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 20.dp)
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val headCenterY = h * 0.58f

            // A. Dot-Matrix / Halftone Sound Field Mesh (Behind Head)
            val ringCount = 5
            val dotCountPerRing = 24
            for (i in 1..ringCount) {
                val radius = w * 0.12f * i
                for (j in 0 until dotCountPerRing) {
                    val angle = (j * (360f / dotCountPerRing)) * (Math.PI / 180f)
                    // Only draw dots behind the head area to give depth
                    if (Math.sin(angle) > -0.6f) {
                        val dotX = cx + (radius * cos(angle)).toFloat()
                        val dotY = headCenterY - (radius * 0.7f * sin(angle)).toFloat() - 20.dp.toPx()

                        drawCircle(
                            color = Color.White.copy(alpha = 0.12f + (i * 0.03f)),
                            radius = 1.2.dp.toPx(),
                            center = Offset(dotX, dotY)
                        )
                    }
                }
            }

            // B. Shoulders / Torso Base
            val torsoPath = Path().apply {
                moveTo(cx - w * 0.14f, h * 0.72f)
                lineTo(cx - w * 0.18f, h * 0.85f)
                quadraticBezierTo(cx - w * 0.40f, h * 0.90f, cx - w * 0.50f, h * 1.05f)
                lineTo(cx + w * 0.50f, h * 1.05f)
                quadraticBezierTo(cx + w * 0.40f, h * 0.90f, cx + w * 0.18f, h * 0.85f)
                lineTo(cx + w * 0.14f, h * 0.72f)
                close()
            }
            drawPath(
                path = torsoPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A1A1E), Color(0xFF2C2C32), Color(0xFF0B0B0D)),
                    startY = h * 0.72f,
                    endY = h * 1.05f
                )
            )
            // Torso Rim Light
            drawPath(
                path = torsoPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
                    startY = h * 0.78f,
                    endY = h * 0.98f
                ),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // C. Sleek Mannequin Head Shape
            val headPath = Path().apply {
                moveTo(cx, h * 0.22f) // Cranium top
                cubicTo(cx + w * 0.20f, h * 0.22f, cx + w * 0.22f, h * 0.44f, cx + w * 0.17f, h * 0.58f)
                cubicTo(cx + w * 0.13f, h * 0.67f, cx + w * 0.05f, h * 0.72f, cx, h * 0.72f)
                cubicTo(cx - w * 0.05f, h * 0.72f, cx - w * 0.13f, h * 0.67f, cx - w * 0.17f, h * 0.58f)
                cubicTo(cx - w * 0.22f, h * 0.44f, cx - w * 0.20f, h * 0.22f, cx, h * 0.22f)
                close()
            }

            // Volumetric Metallic Head Shading
            drawPath(
                path = headPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF757582), // Specular forehead highlight
                        Color(0xFF313138), // Smooth midtone
                        Color(0xFF0F0F12)  // Deep edge shadow
                    ),
                    center = Offset(cx, h * 0.40f),
                    radius = w * 0.28f
                )
            )

            // Subtle Facial Plane Highlights & Shadows
            // Brow ridge shadow
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1B1B20), Color.Transparent),
                    center = Offset(cx, h * 0.44f),
                    radius = w * 0.10f
                ),
                topLeft = Offset(cx - w * 0.10f, h * 0.40f),
                size = Size(w * 0.20f, h * 0.07f)
            )
            // Nose bridge highlight
            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.25f), Color.Transparent),
                    startY = h * 0.45f,
                    endY = h * 0.58f
                ),
                start = Offset(cx, h * 0.45f),
                end = Offset(cx, h * 0.58f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Head Rim Light (Studio edge separation)
            drawPath(
                path = headPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f), Color.Transparent),
                    startY = h * 0.22f,
                    endY = h * 0.70f
                ),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // D. Over-Ear Headphones
            // Headband Arc
            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1E1E22), Color(0xFF6B6B78), Color(0xFF1E1E22))
                ),
                startAngle = 184f,
                sweepAngle = 172f,
                useCenter = false,
                topLeft = Offset(cx - w * 0.22f, h * 0.17f),
                size = Size(w * 0.44f, h * 0.42f),
                style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
            )

            // Earcups (Left & Right)
            listOf(-1f, 1f).forEach { dir ->
                val cupW = w * 0.075f
                val cupH = h * 0.18f
                val cupLeft = if (dir < 0) cx - w * 0.235f else cx + w * 0.235f - cupW
                val cupTop = h * 0.41f

                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF4A4A52), Color(0xFF141416)),
                        start = Offset(cupLeft, cupTop),
                        end = Offset(cupLeft + cupW, cupTop + cupH)
                    ),
                    topLeft = Offset(cupLeft, cupTop),
                    size = Size(cupW, cupH),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                )
                // Accent ring on earcup
                drawRoundRect(
                    color = accentColor.copy(alpha = 0.7f),
                    topLeft = Offset(cupLeft + 2.dp.toPx(), cupTop + 4.dp.toPx()),
                    size = Size(cupW - 4.dp.toPx(), cupH - 8.dp.toPx()),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // E. Floating Spatial Audio Orb (Sound Source Indicator)
            val orbCenter = Offset(cx - w * 0.24f, h * 0.38f + floatAnim)
            val orbRadius = 14.dp.toPx()

            // Outer soft glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accentColor.copy(alpha = 0.55f), Color.Transparent),
                    center = orbCenter,
                    radius = orbRadius * 2.5f
                ),
                radius = orbRadius * 2.5f,
                center = orbCenter
            )
            // Solid glowing orb core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, accentColor, Color(0xFF521C00)),
                    center = Offset(orbCenter.x - 3f, orbCenter.y - 3f),
                    radius = orbRadius
                ),
                radius = orbRadius,
                center = orbCenter
            )
        }
    }
}

@Composable
private fun CoordinateLabel(axis: String, value: String) {
    Column {
        Text(
            text = axis,
            color = Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}
