package com.rajatxo.lutea.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * ClothPullRefresh — a pull-to-refresh where the top of the screen behaves like
 * stretched silk and a soft vinyl hand reaches down from behind the status bar to
 * pinch it.
 *
 * Visual layers, bottom to top:
 *   1. your content (nudged down slightly while pulling, like it is attached to the sheet)
 *   2. the fabric panel: a dark silk sheet with radial folds converging on the grip point
 *   3. the hand: forearm + palm + four curled fingers + thumb, in cream vinyl
 *
 * Gesture: pure nested-scroll. When the child list is at the top and the user drags
 * down, the delta is consumed here before the list (or its overscroll glow) sees it.
 *
 * Everything is sized from the canvas width, so it scales on every phone.
 */
@Composable
fun ClothPullRefresh(
    modifier: Modifier = Modifier,
    canPull: () -> Boolean,
    onRefresh: suspend () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val threshold = remember(density) { with(density) { 86.dp.toPx() } }
    val maxPull = remember(threshold) { threshold * 1.35f }

    var dragPull by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val settle = remember { Animatable(0f) }
    val pull = if (dragging) dragPull else settle.value

    val connection = remember(threshold, maxPull) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag) {
                    // Downward drag at the top of the list → feed the cloth.
                    if (available.y > 0f && !refreshing && canPull()) {
                        dragging = true
                        dragPull = (dragPull + available.y * 0.55f).coerceAtMost(maxPull)
                        return Offset(0f, available.y)
                    }
                    // Upward drag while the cloth is open → close it first.
                    if (available.y < 0f && dragPull > 0f) {
                        val consume = available.y.coerceAtLeast(-dragPull)
                        dragPull = (dragPull + consume).coerceAtLeast(0f)
                        if (dragPull == 0f) dragging = false
                        return Offset(0f, consume)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragPull <= 0f) return Velocity.Zero
                val start = dragPull
                dragging = false
                dragPull = 0f
                settle.snapTo(start)
                if (start >= threshold && !refreshing) {
                    refreshing = true
                    settle.animateTo(threshold * 1.12f, tween(130))
                    val t0 = System.currentTimeMillis()
                    try {
                        onRefresh()
                    } finally {
                        val elapsed = System.currentTimeMillis() - t0
                        if (elapsed < 520L) delay(520L - elapsed)
                        settle.animateTo(
                            0f,
                            spring(dampingRatio = 0.72f, stiffness = 180f),
                        )
                        refreshing = false
                    }
                } else {
                    settle.animateTo(
                        0f,
                        spring(dampingRatio = 0.62f, stiffness = 260f),
                    )
                }
                // Swallow the fling so the list does not jump after the sheet closes.
                return Velocity(0f, available.y)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(connection),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = pull * 0.30f },
        ) {
            content()
        }

        ClothSheet(pullPx = pull, refreshing = refreshing)
    }
}

private const val TAU = 6.2831855f

@Composable
private fun ClothSheet(pullPx: Float, refreshing: Boolean) {
    val transition = rememberInfiniteTransition(label = "cloth")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "clothTime",
    )

    Canvas(Modifier.fillMaxSize()) {
        if (pullPx < 0.5f) return@Canvas

        val w = size.width
        val u = w / 390f           // design unit (390dp reference width)
        val cx = w / 2f
        val prog = (pullPx / (w * 0.22f)).coerceIn(0f, 1.5f)
        val dip = pullPx * 1.05f + 8f * u          // centre of the sagging edge
        val side = pullPx * 0.30f                  // edges move less: the sheet is anchored

        val panel = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, side)
            cubicTo(w * 0.74f, side + (dip - side) * 0.15f, w * 0.62f, dip, cx, dip)
            cubicTo(w * 0.38f, dip, w * 0.26f, side + (dip - side) * 0.15f, 0f, side)
            close()
        }

        // ── silk base ──
        drawPath(
            path = panel,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF070708),
                    Color(0xFF151518),
                    Color(0xFF0A0A0C),
                ),
                endY = dip,
            ),
        )

        clipPath(panel) {
            // Concentric ripples: the fabric pulled toward the grip point.
            val rings = 9
            for (i in 1..rings) {
                val f = i / rings.toFloat()
                val r = w * (0.03f + 0.42f * f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.024f * (1f - f) + 0.006f),
                    radius = r,
                    center = Offset(cx, dip),
                    style = Stroke(width = w * 0.016f),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.05f),
                    radius = r + w * 0.008f,
                    center = Offset(cx, dip),
                    style = Stroke(width = w * 0.014f),
                )
            }

            // Radial creases fanning out from the pinch.
            for (i in 0 until 22) {
                val a = (i / 22f) * TAU
                val bow = if (i % 2 == 0) 1f else -1f
                val r0 = w * 0.05f
                val r1 = w * 0.62f
                val p0 = Offset(cx + cos(a) * r0, dip + sin(a) * r0 * 0.62f)
                val p2 = Offset(cx + cos(a) * r1, dip + sin(a) * r1 * 0.62f)
                val mx = (p0.x + p2.x) / 2f - sin(a) * bow * w * 0.035f
                val my = (p0.y + p2.y) / 2f + cos(a) * bow * w * 0.035f
                val crease = Path().apply {
                    moveTo(p0.x, p0.y)
                    quadraticBezierTo(mx, my, p2.x, p2.y)
                }
                drawPath(
                    path = crease,
                    color = Color.White.copy(alpha = 0.035f),
                    style = Stroke(width = w * 0.0055f, cap = StrokeCap.Round),
                )
            }

            // Specular pool where the hand grips.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(cx, dip),
                    radius = w * 0.30f,
                ),
                radius = w * 0.30f,
                center = Offset(cx, dip),
            )

            // Soft pulse while the refresh is running.
            if (refreshing) {
                val pulse = 0.5f + 0.5f * sin(time * TAU)
                drawCircle(
                    color = Color.White.copy(alpha = 0.045f + 0.05f * pulse),
                    radius = w * 0.34f,
                    center = Offset(cx, dip),
                    style = Stroke(width = w * 0.010f),
                )
            }
        }

        // ── lit bottom edge of the sheet ──
        val edge = Path().apply {
            moveTo(0f, side)
            cubicTo(w * 0.26f, side + (dip - side) * 0.15f, w * 0.38f, dip, cx, dip)
            cubicTo(w * 0.62f, dip, w * 0.74f, side + (dip - side) * 0.15f, w, side)
        }
        drawPath(
            path = edge,
            color = Color.White.copy(alpha = 0.10f),
            style = Stroke(width = 1.1f * u),
        )

        // ── shadow the sheet casts on the content below ──
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent),
                startY = dip - 4f * u,
                endY = dip + 48f * u,
            ),
            topLeft = Offset(0f, dip - 4f * u),
            size = Size(w, 52f * u),
        )

        // ── the hand ──
        val grip = ((prog - 0.35f) / 0.5f).coerceIn(0f, 1f)
        val handAlpha = ((prog - 0.03f) / 0.16f).coerceIn(0f, 1f)
        if (handAlpha > 0.01f) {
            drawVinylHand(
                cx = cx,
                grabY = dip,
                u = u,
                grip = grip,
                alpha = handAlpha,
                wiggle = time,
            )
        }
    }
}

/**
 * Cream vinyl hand gripping the sheet's edge. Arm comes from behind the top of the
 * screen, palm sits above the edge, fingers curl over it.
 */
private fun DrawScope.drawVinylHand(
    cx: Float,
    grabY: Float,
    u: Float,
    grip: Float,
    alpha: Float,
    wiggle: Float,
) {
    val light = Color(0xFFF9ECDE).copy(alpha = alpha)
    val mid = Color(0xFFE4C7AF).copy(alpha = alpha)
    val deep = Color(0xFFB98E71).copy(alpha = alpha)
    val brush = Brush.linearGradient(
        colors = listOf(light, mid, deep),
        start = Offset(cx - 55f * u, grabY - 150f * u),
        end = Offset(cx + 65f * u, grabY + 30f * u),
    )
    val rim = Color.White.copy(alpha = 0.20f * alpha)
    val shade = Color.Black.copy(alpha = 0.16f * alpha)

    // Shadow the hand throws on the fabric.
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = 0.35f * alpha), Color.Transparent),
            center = Offset(cx, grabY),
            radius = 78f * u,
        ),
        topLeft = Offset(cx - 78f * u, grabY - 46f * u),
        size = Size(156f * u, 92f * u),
    )

    // Forearm — starts above the screen so it reads as reaching in.
    val armW = 74f * u
    drawRoundRect(
        brush = brush,
        topLeft = Offset(cx - armW / 2f, -80f * u),
        size = Size(armW, (grabY - 46f * u) + 80f * u),
        cornerRadius = CornerRadius(armW / 2f),
    )

    // Palm.
    val palmW = 104f * u
    val palmH = 88f * u
    val palmTop = grabY - 98f * u
    drawRoundRect(
        brush = brush,
        topLeft = Offset(cx - palmW / 2f, palmTop),
        size = Size(palmW, palmH),
        cornerRadius = CornerRadius(38f * u, 34f * u),
    )
    drawRoundRect(
        color = rim,
        topLeft = Offset(cx - palmW / 2f + 4f * u, palmTop + 4f * u),
        size = Size(palmW - 8f * u, palmH - 8f * u),
        cornerRadius = CornerRadius(34f * u, 30f * u),
        style = Stroke(width = 1.3f * u),
    )

    // Four fingers curling over the edge.
    for (i in 0 until 4) {
        val knuckleX = cx - 33f * u + i * 22f * u
        val knuckleY = grabY - 30f * u
        // Fingers wiggle a little while the hand is just holding on.
        val sway = if (grip < 0.25f) sin(wiggle * TAU + i * 0.9f) * 2.4f * u else 0f
        val tipX = knuckleX + (cx - knuckleX) * 0.10f + sway
        val tipY = grabY + (4f + 15f * grip) * u
        val ctrlX = knuckleX + (cx - knuckleX) * 0.04f
        val ctrlY = knuckleY + 46f * u * (0.55f + 0.45f * grip)
        val finger = Path().apply {
            moveTo(knuckleX, knuckleY)
            quadraticBezierTo(ctrlX, ctrlY, tipX, tipY)
        }
        drawPath(
            path = finger,
            color = shade,
            style = Stroke(width = 18.5f * u, cap = StrokeCap.Round),
        )
        drawPath(
            path = finger,
            brush = brush,
            style = Stroke(width = 17f * u, cap = StrokeCap.Round),
        )
        drawCircle(
            color = ColourShine.copy(alpha = 0.18f * alpha),
            radius = 4.6f * u,
            center = Offset(tipX, tipY - 1.5f * u),
        )
    }

    // Thumb across the sheet.
    val tStart = Offset(cx - 46f * u, grabY - 72f * u)
    val tCtrl = Offset(cx - 56f * u, grabY - 26f * u)
    val tEnd = Offset(cx - 10f * u, grabY - 2f * u)
    val thumb = Path().apply {
        moveTo(tStart.x, tStart.y)
        quadraticBezierTo(tCtrl.x, tCtrl.y, tEnd.x, tEnd.y)
    }
    drawPath(
        path = thumb,
        color = shade,
        style = Stroke(width = 25.5f * u, cap = StrokeCap.Round),
    )
    drawPath(
        path = thumb,
        brush = brush,
        style = Stroke(width = 24f * u, cap = StrokeCap.Round),
    )

    // Wrist crease.
    drawLine(
        color = Color.Black.copy(alpha = 0.10f * alpha),
        start = Offset(cx - 26f * u, grabY - 104f * u),
        end = Offset(cx + 26f * u, grabY - 104f * u),
        strokeWidth = 1.2f * u,
        cap = StrokeCap.Round,
    )
}

private val ColourShine = Color.White
