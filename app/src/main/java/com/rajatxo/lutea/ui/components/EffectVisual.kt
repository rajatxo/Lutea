package com.rajatxo.lutea.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.lutea.R
import com.rajatxo.lutea.ui.foundation.LocalLuteaWindowInsets
import com.rajatxo.lutea.ui.screens.blendStops
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val Cream = Color(0xFFF5F0E6)
private val GinkgoGold = Color(0xFFD4A03A)

/**
 * Effect tab — full Behance "3D Audio Controller" look.
 *
 * Layers, top to bottom:
 *  1. Title bar: "3D Audio Controller" + × close button
 *  2. Dot sphere with Top/Bottom/Left/Right arrow labels around it
 *  3. Two toggle pills: Auto Spatial Rotate / Lock Position
 *  4. Coordinate row: X 1.50  Y 1.05  Z 0.05
 *  5. Head image at bottom + glowing gold orb + HRTF label
 *
 * Universal Layout Law #7: BoxWithConstraints at root, fractional sizing.
 */
@Composable
fun EffectVisual(
    modifier: Modifier = Modifier,
    headRes: Int = R.drawable.effect_head,
    onClose: () -> Unit = {},
) {
    val density = LocalDensity.current
    var autoRotate by remember { mutableStateOf(true) }
    var lockPos by remember { mutableStateOf(true) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(LocalLuteaWindowInsets.current),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val minDimPx = min(widthPx, heightPx)

        // ── 1. Title bar ──
        val titleFontSize = with(density) { (minDimPx * 0.048f).toSp() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "3D Audio Controller",
                color = Cream,
                fontSize = titleFontSize,
                fontFamily = CalSansFamily,
                fontWeight = FontWeight.SemiBold,
            )
            CloseButton(onClick = onClose, size = with(density) { (minDimPx * 0.05f).toDp() })
        }

        // ── 2. Dot sphere with arrows + directional labels ──
        val sphereSizeDp = with(density) { (minDimPx * 0.52f).toDp() }
        val sphereTopPad = with(density) { (heightPx * 0.085f).toDp() }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = sphereTopPad)
                .size(sphereSizeDp + with(density) { (minDimPx * 0.10f).toDp() }),
            contentAlignment = Alignment.Center,
        ) {
            DotSphere(modifier = Modifier.size(sphereSizeDp, sphereSizeDp))
            DirectionalLabels(
                containerSize = sphereSizeDp + with(density) { (minDimPx * 0.10f).toDp() },
                labelSize = with(density) { (minDimPx * 0.025f).toSp() },
            )
        }

        // ── 3. Two toggle pills below sphere ──
        val togglesTopPad = with(density) { (heightPx * 0.06f).toDp() }
        val toggleFontSize = with(density) { (minDimPx * 0.028f).toSp() }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = togglesTopPad + sphereSizeDp + with(density) { (minDimPx * 0.14f).toDp() }),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TogglePill(
                label = "Auto Spatial Rotate",
                checked = autoRotate,
                onToggle = { autoRotate = it },
                fontSize = toggleFontSize,
            )
            TogglePill(
                label = "Lock Position",
                checked = lockPos,
                onToggle = { lockPos = it },
                fontSize = toggleFontSize,
            )
        }

        // ── 4. Coordinate row ──
        val coordsBottomPad = with(density) { (heightPx * 0.30f).toDp() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = coordsBottomPad),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoordItem("X", "1.50", with(density) { (minDimPx * 0.026f).toSp() })
            Box(modifier = Modifier.size(16.dp))
            CoordItem("Y", "1.05", with(density) { (minDimPx * 0.026f).toSp() })
            Box(modifier = Modifier.size(16.dp))
            CoordItem("Z", "0.05", with(density) { (minDimPx * 0.026f).toSp() })
        }

        // ── 5. Head image at bottom + glowing orb + HRTF label ──
        val imgSizeDp = with(density) {
            val sideGapPx = widthPx * 0.075f
            min(widthPx - sideGapPx * 2, heightPx * 0.42f).toDp()
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

        // ── 6. Glowing orb beside the head + HRTF label ──
        val orbSize = with(density) { (minDimPx * 0.05f).toDp() }
        val hrtfFontSize = with(density) { (minDimPx * 0.024f).toSp() }
        val hrtfBottomPad = with(density) { (heightPx * 0.18f).toDp() }
        val hrtfEndPad = with(density) { (widthPx * 0.10f).toDp() }
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = hrtfEndPad, bottom = hrtfBottomPad),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GlowOrb(modifier = Modifier.size(orbSize, orbSize))
            Text(
                text = "HRTF",
                color = GinkgoGold,
                fontSize = hrtfFontSize,
                fontFamily = CalSansFamily,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "+3dB / 2kHz",
                color = Cream.copy(alpha = 0.65f),
                fontSize = with(density) { (minDimPx * 0.020f).toSp() },
                fontFamily = CalSansFamily,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CloseButton(onClick: () -> Unit, size: androidx.compose.ui.unit.Dp) {
    val stroke = with(LocalDensity.current) { (size.toPx() * 0.06f).toDp() }
    Box(
        modifier = Modifier
            .size(size, size)
            .clip(RoundedCornerShape(50))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = minOf(this.size.width, this.size.height)
            val inset = s * 0.28f
            drawLine(
                color = Cream.copy(alpha = 0.85f),
                start = Offset(inset, inset),
                end = Offset(s - inset, s - inset),
                strokeWidth = stroke.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Cream.copy(alpha = 0.85f),
                start = Offset(s - inset, inset),
                end = Offset(inset, s - inset),
                strokeWidth = stroke.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun TogglePill(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    val bg = if (checked) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.04f)
    val border = if (checked) GinkgoGold else Color.White.copy(alpha = 0.10f)
    val fg = if (checked) GinkgoGold else Cream.copy(alpha = 0.55f)
    val knobColor = if (checked) GinkgoGold else Color.White.copy(alpha = 0.4f)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable { onToggle(!checked) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Toggle knob
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(50))
                .background(knobColor),
        )
        Text(
            text = label,
            color = fg,
            fontSize = fontSize,
            fontFamily = CalSansFamily,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CoordItem(label: String, value: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = label,
            color = GinkgoGold,
            fontSize = fontSize,
            fontFamily = CalSansFamily,
            fontWeight = FontWeight.Bold,
        )
        Box(modifier = Modifier.size(4.dp))
        Text(
            text = value,
            color = Cream.copy(alpha = 0.85f),
            fontSize = fontSize,
            fontFamily = CalSansFamily,
        )
    }
}

@Composable
private fun GlowOrb(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "orb")
    val pulse by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbPulse",
    )
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        // Outer halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    GinkgoGold.copy(alpha = 0.85f * pulse),
                    GinkgoGold.copy(alpha = 0.25f * pulse),
                    Color.Transparent,
                ),
                center = center,
                radius = r * 1.4f,
            ),
            radius = r * 1.4f,
            center = center,
        )
        // Solid core
        drawCircle(
            color = GinkgoGold.copy(alpha = 0.95f),
            radius = r * 0.5f,
            center = center,
        )
        // Highlight
        drawCircle(
            color = Color.White.copy(alpha = 0.35f * pulse),
            radius = r * 0.18f,
            center = Offset(center.x - r * 0.15f, center.y - r * 0.15f),
        )
    }
}

@Composable
private fun DirectionalLabels(
    containerSize: androidx.compose.ui.unit.Dp,
    labelSize: androidx.compose.ui.unit.TextUnit,
) {
    Box(modifier = Modifier.size(containerSize, containerSize)) {
        val arrowColor = GinkgoGold.copy(alpha = 0.85f)
        val textColor = Cream.copy(alpha = 0.7f)

        // Top
        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "Top", color = textColor, fontSize = labelSize, fontFamily = CalSansFamily)
            ArrowDown(color = arrowColor, size = 8.dp)
        }
        // Bottom
        Column(
            modifier = Modifier.align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArrowUp(color = arrowColor, size = 8.dp)
            Text(text = "Bottom", color = textColor, fontSize = labelSize, fontFamily = CalSansFamily)
        }
        // Left
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArrowRight(color = arrowColor, size = 8.dp)
            Text(text = " Left", color = textColor, fontSize = labelSize, fontFamily = CalSansFamily)
        }
        // Right
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Right ", color = textColor, fontSize = labelSize, fontFamily = CalSansFamily)
            ArrowLeft(color = arrowColor, size = 8.dp)
        }
    }
}

@Composable
private fun ArrowDown(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size, size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.10f
        drawLine(color, Offset(w * 0.5f, 0f), Offset(w * 0.5f, h * 0.85f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.85f), Offset(w * 0.20f, h * 0.55f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.85f), Offset(w * 0.80f, h * 0.55f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ArrowUp(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size, size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.10f
        drawLine(color, Offset(w * 0.5f, h), Offset(w * 0.5f, h * 0.15f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.15f), Offset(w * 0.20f, h * 0.45f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.15f), Offset(w * 0.80f, h * 0.45f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ArrowLeft(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size, size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.10f
        drawLine(color, Offset(w, h * 0.5f), Offset(w * 0.15f, h * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.15f, h * 0.5f), Offset(w * 0.45f, h * 0.20f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.15f, h * 0.5f), Offset(w * 0.45f, h * 0.80f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ArrowRight(color: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size, size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.10f
        drawLine(color, Offset(0f, h * 0.5f), Offset(w * 0.85f, h * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.85f, h * 0.5f), Offset(w * 0.55f, h * 0.20f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.85f, h * 0.5f), Offset(w * 0.55f, h * 0.80f), stroke, StrokeCap.Round)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dot sphere (Fibonacci-spiral point distribution, depth-shaded dots)
// ─────────────────────────────────────────────────────────────────────────────

private data class SpherePoint(val x: Float, val y: Float, val z: Float)

private fun fibonacciSphere(n: Int): List<SpherePoint> {
    val out = ArrayList<SpherePoint>(n)
    val phi = (Math.PI * (3.0 - Math.sqrt(5.0))).toFloat()
    for (i in 0 until n) {
        val y = 1f - (i.toFloat() / (n - 1)) * 2f
        val r = sqrt(1f - y * y)
        val theta = phi * i
        out.add(SpherePoint(cos(theta) * r, y, sin(theta) * r))
    }
    return out
}

@Composable
private fun DotSphere(modifier: Modifier = Modifier) {
    val points = remember { fibonacciSphere(180) }
    val infinite = rememberInfiniteTransition(label = "sphere")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rot",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = size.minDimension * 0.42f
        val baseDot = size.minDimension * 0.009f

        val ang = Math.toRadians(rotation.toDouble()).toFloat()
        val cosA = cos(ang); val sinA = sin(ang)

        val projected = ArrayList<SpherePoint>(points.size)
        for (p in points) {
            val x1 = p.x * cosA + p.z * sinA
            val z1 = -p.x * sinA + p.z * cosA
            projected.add(SpherePoint(x1, p.y, z1))
        }
        projected.sortBy { it.z }

        val pulseAmp = 0.10f
        for (v in projected) {
            val depth = (v.z + 1f) * 0.5f
            val wave = sin(pulse + depth * 3f)
            val scale = 1f + pulseAmp * wave
            val dotR = baseDot * (0.4f + depth * 0.9f) * scale
            val px = center.x + v.x * baseRadius
            val py = center.y - v.y * baseRadius
            val alpha = 0.18f + depth * 0.82f
            drawCircle(
                color = Cream.copy(alpha = alpha),
                radius = dotR.coerceAtLeast(0.3f),
                center = Offset(px, py),
            )
        }

        // One accent dot upper-left (Behance red-dot reference, gold for Lutea).
        drawCircle(
            color = GinkgoGold,
            radius = baseDot * 1.6f,
            center = Offset(size.width * 0.12f, size.height * 0.18f),
        )
    }
}
