package com.rajatxo.lutea.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.lutea.ui.foundation.LocalLuteaWindowInsets
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Effect tab — Behance-style metamorphic dot sphere.
 *
 * One single screen with 5 capsules (8D / Reverb / Spatial / Stereo / Speed).
 * Each capsule swaps the dot sphere's pulsation pattern + rotation speed.
 * The dots' sizes morph smoothly between capsules via animateFloatAsState.
 *
 * Drag the sphere area to rotate it manually.
 *
 * Universal Layout Law #7: BoxWithConstraints at root, fractional sizing.
 */
@Composable
fun EffectVisual(
    modifier: Modifier = Modifier,
) {
    var activeMode by remember { mutableStateOf(EffectMode.EIGHT_D) }
    var dragY by remember { mutableStateOf(0f) }
    var dragX by remember { mutableStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(LocalLuteaWindowInsets.current),
    ) {
        Column(Modifier.fillMaxSize()) {
            EffectCapsuleRow(
                activeMode = activeMode,
                onModeSelected = { activeMode = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            DotSphere(
                activeMode = activeMode,
                dragX = dragX,
                dragY = dragY,
                onDragY = { dragY += it },
                onDragX = { dragX += it },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

enum class EffectMode(val label: String) {
    EIGHT_D("8D"),
    REVERB("Reverb"),
    SPATIAL("Spatial"),
    STEREO("Stereo"),
    SPEED("Speed")
}

private val Cream = Color(0xFFF5F0E6)
private val GinkgoGold = Color(0xFFD4A03A)

@Composable
private fun EffectCapsuleRow(
    activeMode: EffectMode,
    onModeSelected: (EffectMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        EffectMode.entries.forEach { mode ->
            val isActive = mode == activeMode
            val bg by animateColorAsState(
                if (isActive) GinkgoGold else Color.White.copy(alpha = 0.06f),
                animationSpec = tween(300),
                label = "bg_${mode.name}",
            )
            val fg by animateColorAsState(
                if (isActive) Color.Black else Color.White.copy(alpha = 0.7f),
                animationSpec = tween(300),
                label = "fg_${mode.name}",
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = mode.label,
                    color = fg,
                    fontSize = 12.sp,
                    fontFamily = CalSansFamily,
                )
            }
        }
    }
}

private data class SpherePoint(val x: Float, val y: Float, val z: Float)

/** Even points on a unit sphere via the Fibonacci spiral. */
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
private fun DotSphere(
    activeMode: EffectMode,
    dragX: Float,
    dragY: Float,
    onDragY: (Float) -> Unit,
    onDragX: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Per-mode tunables. animateFloatAsState gives smooth morph between capsules.
    val targetPulseAmp = when (activeMode) {
        EffectMode.EIGHT_D -> 0.10f
        EffectMode.REVERB -> 0.35f
        EffectMode.SPATIAL -> 0.22f
        EffectMode.STEREO -> 0.08f
        EffectMode.SPEED -> 0.18f
    }
    val targetRotSpeed = when (activeMode) {
        EffectMode.EIGHT_D -> 0.5f
        EffectMode.REVERB -> 0.15f
        EffectMode.SPATIAL -> 0.3f
        EffectMode.STEREO -> 0.7f
        EffectMode.SPEED -> 1.5f
    }
    val pulseAmp by animateFloatAsState(targetPulseAmp, tween(700), label = "pulseAmp")
    val rotSpeed by animateFloatAsState(targetRotSpeed, tween(700), label = "rotSpeed")

    // Continuous time drivers — never restart, params above modulate them.
    val infinite = rememberInfiniteTransition(label = "sphere")
    val baseRotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label = "rot",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )

    val points = remember { fibonacciSphere(180) }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, delta ->
                        change.consume()
                        onDragY(delta.x * 0.01f)
                        onDragX(delta.y * 0.01f)
                    },
                )
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = min(size.width, size.height) * 0.36f
        val baseDot = size.minDimension * 0.0075f

        val angY = Math.toRadians((baseRotation * rotSpeed + dragY).toDouble()).toFloat()
        val angX = dragX
        val cosY = cos(angY); val sinY = sin(angY)
        val cosX = cos(angX); val sinX = sin(angX)

        // Rotate all points, sort back-to-front so closer dots draw over farther ones.
        val projected = ArrayList<SpherePoint>(points.size)
        for (p in points) {
            val x1 = p.x * cosY + p.z * sinY
            val z1 = -p.x * sinY + p.z * cosY
            val y2 = p.y * cosX - z1 * sinX
            val z2 = p.y * sinX + z1 * cosX
            projected.add(SpherePoint(x1, y2, z2))
        }
        projected.sortBy { it.z }

        for (v in projected) {
            val depth = (v.z + 1f) * 0.5f  // 0=back, 1=front
            // Different wave per capsule — sizes pulse along a different axis.
            val wave = when (activeMode) {
                EffectMode.SPATIAL -> sin(pulse + v.y * 3f)
                EffectMode.STEREO -> sin(pulse + v.x * 3f)
                else -> sin(pulse + depth * 3f)
            }
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
            radius = baseDot * 1.4f,
            center = Offset(size.width * 0.12f, size.height * 0.18f),
        )
    }
}
