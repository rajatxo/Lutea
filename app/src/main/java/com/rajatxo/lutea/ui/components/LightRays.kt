package com.rajatxo.lutea.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Light rays background.
 *
 * A native Compose port of the react-bits "LightRays" backdrop (top-centre
 * origin, additive white rays, slowly shimmering) — no WebGL, no React, so it
 * runs on every Android version the app supports.
 *
 * Ray outlines never change, so they are built once per size and cached;
 * only the per-ray brightness is recomputed on each (throttled) tick.
 */
@Composable
fun LightRaysBackground(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    intensity: Float = 1f,
) {
    // 25 fps is plenty for a slow shimmer and keeps the GPU cheap.
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) {
            time.floatValue = (System.nanoTime() - start) / 1_000_000_000f
            delay(40L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val rays = buildRays(size.width, size.height)
                    onDrawBehind { drawRays(rays, time.floatValue, color, intensity) }
                },
        )
        // Distance fade: the rays dissolve toward the bottom of the page, so
        // they never fight with the wheel labels down there.
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White,
                    0.45f to Color.White.copy(alpha = 0.75f),
                    0.85f to Color.Transparent,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}

/** One cached ray: soft outline + the two phases that drive its shimmer. */
private class RayShape(
    val core: Path,
    val halo: Path,
    val phaseA: Float,
    val phaseB: Float,
    val base: Float,
)

private fun frac(v: Float): Float = v - floor(v)

private fun buildRays(w: Float, h: Float): List<RayShape> {
    if (w <= 0f || h <= 0f) return emptyList()

    // Source sits above the page, like the web default ("top-center").
    val srcX = w * 0.5f
    val srcY = -h * 0.18f

    // Angular window that covers the whole page as seen from the source.
    var minA = Float.MAX_VALUE
    var maxA = -Float.MAX_VALUE
    val xs = floatArrayOf(0f, w)
    val ys = floatArrayOf(0f, h)
    for (cx in xs) {
        for (cy in ys) {
            val a = atan2(cy - srcY, cx - srcX)
            if (a < minA) minA = a
            if (a > maxA) maxA = a
        }
    }
    minA -= 0.05f
    maxA += 0.05f

    val count = 46
    val reach = sqrt(w * w + h * h) * 1.12f
    val out = ArrayList<RayShape>(count)
    for (i in 0 until count) {
        val u = (i + 0.5f) / count
        val ang = minA + (maxA - minA) * u
        val dx = cos(ang)
        val dy = sin(ang)
        // Deterministic per-ray jitter.
        val h1 = frac(sin(i * 12.9898f) * 43758.5453f)
        val h2 = frac(sin(i * 78.233f) * 12345.678f)
        val h3 = frac(sin(i * 37.719f) * 5283.153f)
        val len = reach * (0.80f + 0.25f * h1)
        val halfW = len * tan(0.005f + 0.011f * h2)
        fun wedge(scale: Float): Path {
            val px = -dy * halfW * scale
            val py = dx * halfW * scale
            return Path().apply {
                moveTo(srcX, srcY)
                lineTo(srcX + dx * len + px, srcY + dy * len + py)
                lineTo(srcX + dx * len - px, srcY + dy * len - py)
                close()
            }
        }
        out.add(
            RayShape(
                core = wedge(1f),
                halo = wedge(2.6f),
                phaseA = ang * 24f + h3 * 6.2831855f,
                phaseB = -ang * 15f + h1 * 6.2831855f,
                base = 0.45f + 0.55f * h2,
            )
        )
    }
    return out
}

private fun DrawScope.drawRays(
    rays: List<RayShape>,
    t: Float,
    color: Color,
    intensity: Float,
) {
    if (rays.isEmpty()) return
    val src = Offset(size.width * 0.5f, -size.height * 0.18f)

    // Soft glow where all the rays converge.
    val glowR = size.width * 0.62f
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = 0.10f * intensity),
            0.45f to color.copy(alpha = 0.04f * intensity),
            1f to Color.Transparent,
            center = src,
            radius = glowR,
        ),
        radius = glowR,
        center = src,
        blendMode = BlendMode.Plus,
    )

    for (r in rays) {
        val s = 0.62f +
            0.22f * sin(r.phaseA + t * 1.15f) +
            0.16f * cos(r.phaseB + t * 0.85f)
        val a = (0.15f * s * r.base * intensity).coerceIn(0f, 0.5f)
        if (a < 0.003f) continue
        drawPath(r.halo, color = color.copy(alpha = a * 0.32f), blendMode = BlendMode.Plus)
        drawPath(r.core, color = color.copy(alpha = a), blendMode = BlendMode.Plus)
    }
}
