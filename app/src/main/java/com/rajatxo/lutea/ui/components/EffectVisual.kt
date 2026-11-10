package com.rajatxo.lutea.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
//  EffectVisual — a real, lit 3D mannequin head with over-ear headphones.
//
//  Why this isn't a pile of Paths: gradients on a silhouette can never produce
//  believable form. Instead the head is *sculpted* from signed-distance shapes
//  (ellipsoids, capsules, smooth unions) and ray-marched once on a background
//  thread into a 360x450 bitmap with real lighting: key + fill light, soft
//  shadows, ambient occlusion, a studio-reflection sheen and a rim.
//  Pure Kotlin math — no OpenGL, no Filament, no assets, no dependencies.
//  The bitmap is cached per accent colour, so it renders only once per run.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EffectVisual(
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFFD4A03A),
    showEarcupRing: Boolean = true,
) {
    val key = accentColor.toArgb() * 31 + if (showEarcupRing) 1 else 0
    var image by remember(key) { mutableStateOf(HeadCache.get(key)) }

    LaunchedEffect(key) {
        if (image == null) {
            val rendered = withContext(Dispatchers.Default) {
                renderHead(accentColor, showEarcupRing)
            }
            HeadCache.put(key, rendered)
            image = rendered
        }
    }

    val reveal by animateFloatAsState(
        targetValue = if (image != null) 1f else 0f,
        animationSpec = tween(800),
        label = "headReveal",
    )
    val infinite = rememberInfiniteTransition(label = "headIdle")
    // Slow breathing of the accent backlight.
    val pulse by infinite.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    // The *light* drifts across the head; the geometry never rotates.
    val drift by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )

    Box(modifier = modifier.background(Color.Black)) {
        // Layer 1: ambient accent glow behind the head.
        Canvas(Modifier.fillMaxSize()) {
            val layout = headLayout(size.width, size.height)
            val c = Offset(layout.centerX, layout.headCenterY)
            val r = layout.w * 0.62f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accentColor.copy(alpha = pulse * reveal), Color.Transparent),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )
        }

        // Layer 2: the rendered head. Offscreen so the sheen only lands on head pixels.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = reveal
                },
        ) {
            val img = image ?: return@Canvas
            val layout = headLayout(size.width, size.height)
            drawImage(
                image = img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(layout.left.toInt(), layout.top.toInt()),
                dstSize = IntSize(layout.w.toInt(), layout.h.toInt()),
                filterQuality = FilterQuality.High,
            )
            // Moving soft highlight (SrcAtop = only brightens pixels that already exist).
            val sc = Offset(layout.centerX + drift * layout.w * 0.14f, layout.top + layout.h * 0.30f)
            val sr = layout.w * 0.30f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.13f), Color.Transparent),
                    center = sc,
                    radius = sr,
                ),
                radius = sr,
                center = sc,
                blendMode = BlendMode.SrcAtop,
            )
        }
    }
}

private class HeadLayout(val left: Float, val top: Float, val w: Float, val h: Float) {
    val centerX get() = left + w / 2f
    val headCenterY get() = top + h * 0.35f
}

private fun headLayout(cw: Float, ch: Float): HeadLayout {
    val h = min(ch * 0.80f, cw * 1.45f / 0.8f)
    val w = h * 0.8f
    return HeadLayout((cw - w) / 2f, (ch - h) * 0.45f, w, h)
}

private object HeadCache {
    private var key = 0
    private var image: ImageBitmap? = null
    fun get(k: Int): ImageBitmap? = if (image != null && key == k) image else null
    fun put(k: Int, img: ImageBitmap) { key = k; image = img }
}

// ─────────────────────────────── signed-distance toolkit ───────────────────────────────

private fun len2(a: Float, b: Float) = sqrt(a * a + b * b)
private fun len3(a: Float, b: Float, c: Float) = sqrt(a * a + b * b + c * c)

private fun smin(a: Float, b: Float, k: Float): Float {
    val h = max(k - abs(a - b), 0f) / k
    return min(a, b) - h * h * k * 0.25f
}

private fun smax(a: Float, b: Float, k: Float) = -smin(-a, -b, k)

private fun ell(
    x: Float, y: Float, z: Float,
    cx: Float, cy: Float, cz: Float,
    rx: Float, ry: Float, rz: Float,
): Float {
    val px = (x - cx) / rx; val py = (y - cy) / ry; val pz = (z - cz) / rz
    val k0 = len3(px, py, pz)
    val k1 = len3(px / rx, py / ry, pz / rz) + 1e-9f
    return k0 * (k0 - 1f) / k1
}

private fun sph(x: Float, y: Float, z: Float, cx: Float, cy: Float, cz: Float, r: Float) =
    len3(x - cx, y - cy, z - cz) - r

private fun cap(
    x: Float, y: Float, z: Float,
    ax: Float, ay: Float, az: Float,
    bx: Float, by: Float, bz: Float,
    r: Float,
): Float {
    val pax = x - ax; val pay = y - ay; val paz = z - az
    val bax = bx - ax; val bay = by - ay; val baz = bz - az
    val h = ((pax * bax + pay * bay + paz * baz) / (bax * bax + bay * bay + baz * baz)).coerceIn(0f, 1f)
    return len3(pax - bax * h, pay - bay * h, paz - baz * h) - r
}

/** The sculpted mannequin: cranium, jaw, brow, eyelids, nose, lips, neck, shoulders. */
private fun headSdf(x: Float, y: Float, z: Float): Float {
    val ax = abs(x)
    var d = ell(x, y, z, 0f, 0.16f, -0.08f, 0.58f, 0.62f, 0.62f)
    d = smin(d, ell(x, y, z, 0f, -0.30f, 0.14f, 0.43f, 0.40f, 0.46f), 0.30f)      // jaw
    d = smin(d, ell(x, y, z, 0f, -0.68f, 0.32f, 0.17f, 0.12f, 0.14f), 0.18f)      // chin
    d = smin(d, ell(ax, y, z, 0.28f, -0.12f, 0.36f, 0.17f, 0.12f, 0.14f), 0.15f)  // cheekbones
    d = smin(d, cap(ax, y, z, 0f, 0.12f, 0.50f, 0.32f, 0.13f, 0.44f, 0.075f), 0.10f) // brow ridge
    d = smax(d, -ell(ax, y, z, 0.24f, 0.06f, 0.62f, 0.14f, 0.085f, 0.10f), 0.08f) // eye sockets
    d = smin(d, sph(ax, y, z, 0.24f, 0.045f, 0.465f, 0.095f), 0.05f)             // closed lids
    d = smin(d, cap(x, y, z, 0f, 0.04f, 0.52f, 0f, -0.22f, 0.66f, 0.065f), 0.08f) // nose bridge
    d = smin(d, sph(x, y, z, 0f, -0.27f, 0.70f, 0.095f), 0.07f)                   // nose tip
    d = smin(d, ell(ax, y, z, 0.09f, -0.27f, 0.62f, 0.06f, 0.055f, 0.06f), 0.05f) // nostril wings
    d = smin(d, ell(x, y, z, 0f, -0.47f, 0.57f, 0.12f, 0.035f, 0.06f), 0.04f)     // upper lip
    d = smin(d, ell(x, y, z, 0f, -0.55f, 0.56f, 0.10f, 0.035f, 0.055f), 0.04f)    // lower lip
    d = smax(d, -ell(x, y, z, 0f, -0.51f, 0.61f, 0.15f, 0.008f, 0.07f), 0.02f)    // mouth line
    d = smin(d, cap(x * 0.85f, y, z, 0f, -0.70f, -0.02f, 0f, -2.0f, -0.02f, 0.30f), 0.14f) // neck
    d = smin(d, ell(x, y, z, 0f, -2.0f, -0.08f, 1.5f, 0.5f, 0.52f), 0.65f)        // shoulders
    return d
}

/** Over-ear headphones: rounded earcups, soft cushions, headband, slider arms. */
private fun phonesSdf(x: Float, y: Float, z: Float): Float {
    val ax = abs(x)
    // earcup
    val r2 = len2((y + 0.02f) / 1.10f, z * 1.15f) - 0.30f
    val wx = abs(ax - 0.66f) - 0.12f
    val cup = min(max(wx, r2), 0f) + len2(max(wx, 0f), max(r2, 0f)) - 0.10f
    // cushion
    val r3 = len2(y / 1.08f, z * 1.15f) - 0.26f
    val wx2 = abs(ax - 0.55f) - 0.05f
    val cush = min(max(wx2, r3), 0f) + len2(max(wx2, 0f), max(r3, 0f)) - 0.06f
    var d = smin(cup, cush, 0.05f)
    // headband: scaled torus section so it hugs the skull
    val rr = len2(x / 0.68f * 0.8f, y / 0.90f * 0.8f)
    var ring = len2(rr - 0.8f, z * 0.45f) - 0.085f
    ring *= 0.78f
    ring = max(ring, -(y - 0.30f))
    d = smin(d, ring, 0.04f)
    // slider arm
    d = smin(d, cap(ax, y, z, 0.64f, 0.40f, 0f, 0.66f, 0.18f, 0f, 0.06f), 0.04f)
    return d
}

// ─────────────────────────────── renderer ───────────────────────────────

private const val TILT = 0.08f // chin-down tilt: reads friendlier than a dead-flat frontal bust
private val COS_T = cos(TILT.toDouble()).toFloat()
private val SIN_T = sin(TILT.toDouble()).toFloat()

private const val IMG_W = 360
private const val IMG_H = 450
private const val VIEW_H = 3.7f
private const val CAM_Y = -0.40f

private fun sceneSdf(x: Float, y: Float, z: Float): Float {
    val qy = COS_T * y - SIN_T * z
    val qz = SIN_T * y + COS_T * z
    return min(headSdf(x, qy, qz), phonesSdf(x, qy, qz))
}

private fun norm3(x: Float, y: Float, z: Float): FloatArray {
    val l = len3(x, y, z)
    return floatArrayOf(x / l, y / l, z / l)
}

private fun smooth01(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

private suspend fun renderHead(accent: Color, ring: Boolean): ImageBitmap = coroutineScope {
    val pixels = IntArray(IMG_W * IMG_H)
    val workers = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
    (0 until workers).map { w ->
        async(Dispatchers.Default) {
            var j = w
            while (j < IMG_H) {
                shadeRow(j, pixels, accent, ring)
                j += workers // interleaved rows balance the load across threads
            }
        }
    }.awaitAll()
    val bmp = Bitmap.createBitmap(IMG_W, IMG_H, Bitmap.Config.ARGB_8888)
    bmp.setPixels(pixels, 0, IMG_W, 0, 0, IMG_W, IMG_H)
    bmp.asImageBitmap()
}

private fun shadeRow(j: Int, out: IntArray, accent: Color, ring: Boolean) {
    val asp = IMG_W.toFloat() / IMG_H
    val pw = VIEW_H / IMG_H
    val l1 = norm3(-0.45f, 0.75f, 0.65f)
    val l2 = norm3(0.7f, 0.2f, 0.4f)
    val hv = norm3(l1[0], l1[1], l1[2] + 1f)
    val accR = accent.red; val accG = accent.green; val accB = accent.blue

    val v = (0.5f - (j + 0.5f) / IMG_H) * VIEW_H + CAM_Y
    // fade the cut-off bust into black at the bottom
    val fadeY = 1f - smooth01(((j.toFloat() / IMG_H) - 0.70f) / 0.26f)

    for (i in 0 until IMG_W) {
        val u = ((i + 0.5f) / IMG_W - 0.5f) * VIEW_H * asp
        val fadeX = smooth01(min(i, IMG_W - 1 - i) / (0.10f * IMG_W))

        // ── ray march (orthographic, looking down -Z) ──
        var t = 6.3f
        var hit = false
        var dmin = 1e9f
        while (t < 10f) {
            val d = sceneSdf(u, v, 8f - t)
            if (d < dmin) dmin = d
            if (d < 0.0015f) { hit = true; break }
            t += d * 0.9f
        }

        if (!hit) {
            val edge = (1f - dmin / pw).coerceIn(0f, 1f) * 0.6f
            val a = (edge * fadeX * fadeY * 255f).toInt()
            out[j * IMG_W + i] = if (a <= 0) 0 else (a shl 24) or 0x595959
            continue
        }

        val px = u; val py = v; val pz = 8f - t

        // normal (tetrahedron sampling)
        val h = 0.0015f
        val n0 = sceneSdf(px + h, py - h, pz - h)
        val n1 = sceneSdf(px - h, py - h, pz + h)
        val n2 = sceneSdf(px - h, py + h, pz - h)
        val n3 = sceneSdf(px + h, py + h, pz + h)
        var nx = n0 - n1 - n2 + n3
        var ny = -n0 - n1 + n2 + n3
        var nz = -n0 + n1 - n2 + n3
        val nl = len3(nx, ny, nz) + 1e-9f
        nx /= nl; ny /= nl; nz /= nl

        // lighting frame (slightly tilted, matches the look tuned in the prototype)
        val nvx = nx
        val nvy = COS_T * ny + SIN_T * nz
        val nvz = -SIN_T * ny + COS_T * nz

        // which material did we hit?
        val qy = COS_T * py - SIN_T * pz
        val qz = SIN_T * py + COS_T * pz
        val isPhones = phonesSdf(px, qy, qz) < headSdf(px, qy, qz)

        val ndl1 = (nvx * l1[0] + nvy * l1[1] + nvz * l1[2]).coerceIn(0f, 1f)
        val ndl2 = (nvx * l2[0] + nvy * l2[1] + nvz * l2[2]).coerceIn(0f, 1f)

        // soft shadow toward the key light
        var sh = 1f
        var tt = 0.05f
        for (s in 0 until 24) {
            val dd = sceneSdf(px + l1[0] * tt, py + l1[1] * tt, pz + l1[2] * tt)
            sh = min(sh, (7f * dd / tt).coerceIn(0f, 1f))
            tt += dd.coerceIn(0.03f, 0.25f)
        }
        sh = smooth01(sh)

        // ambient occlusion
        var ao = 0f
        for (k in 1..5) {
            val hh = 0.05f * k
            val dd = sceneSdf(px + nx * hh, py + ny * hh, pz + nz * hh)
            ao += (hh - dd) / (1 shl k).toFloat()
        }
        ao = (1f - 1.6f * ao).coerceIn(0f, 1f)

        val ndv = nvz.coerceIn(0f, 1f)
        val omn = 1f - ndv
        val fres = omn * omn * omn
        val rx = 2f * ndv * nvx
        val ry = 2f * ndv * nvy
        // fake studio environment: gradient + two softboxes
        val up = (ry * 0.5f + 0.5f).coerceIn(0f, 1f)
        val env = 0.10f + 0.55f * up * up +
            0.9f * exp(-((rx + 0.5f) * (rx + 0.5f) * 6f + (ry - 0.5f) * (ry - 0.5f) * 9f)) +
            0.5f * exp(-((rx - 0.8f) * (rx - 0.8f) * 8f + (ry - 0.1f) * (ry - 0.1f) * 20f))

        val ndh = (nvx * hv[0] + nvy * hv[1] + nvz * hv[2]).coerceIn(0f, 1f)
        val specH = ndh.pow(40f)
        val specP = ndh.pow(90f)

        var col: Float
        if (isPhones) {
            val base = 0.16f * ndl1 * sh + 0.04f * ndl2 + 0.03f
            col = base * ao + 0.7f * specP * sh + 0.30f * env * (0.2f + 0.8f * fres) * ao
        } else {
            val base = 0.50f * ndl1 * sh + 0.12f * ndl2 + 0.10f * (0.5f + 0.5f * nvy)
            col = (base * ao + 0.30f * specH * sh + 0.22f * env * fres * ao + 0.05f * env * ao).pow(0.95f)
        }
        col += 0.25f * fres * (0.6f + 0.4f * nvy).coerceIn(0f, 1f) * ao

        var r = col * 0.98f
        var g = col
        var b = col * 1.03f

        // thin glowing accent ring on each earcup's outer face
        if (ring && isPhones && abs(px) > 0.74f) {
            val re = len2((qy + 0.02f) / 1.10f, qz * 1.15f)
            val dr = (re - 0.16f)
            val e = (exp(-(dr / 0.010f) * (dr / 0.010f)) + 0.18f * exp(-(dr / 0.05f) * (dr / 0.05f))) *
                ((abs(nx) - 0.6f) / 0.3f).coerceIn(0f, 1f)
            r += accR * e * 0.9f; g += accG * e * 0.9f; b += accB * e * 0.9f
        }

        val a = (fadeX * fadeY * 255f).toInt().coerceIn(0, 255)
        val ri = (r.coerceIn(0f, 1f) * 255f).toInt()
        val gi = (g.coerceIn(0f, 1f) * 255f).toInt()
        val bi = (b.coerceIn(0f, 1f) * 255f).toInt()
        out[j * IMG_W + i] = (a shl 24) or (ri shl 16) or (gi shl 8) or bi
    }
}
