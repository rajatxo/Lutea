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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
//  EffectVisual v2 — small, dark, glossy, featureless mannequin bust.
//
//  The bust is sculpted from signed-distance shapes and ray-marched once on a
//  background thread (pure Kotlin math, no GL / Filament / assets). Form comes
//  from reflections, not paint: the skin is near-black and a fake studio
//  environment (two tall softboxes + a top box) is reflected in it, so the
//  cranium, jaw and shoulders are carved out by highlights and rim light.
//
//  Quality fixes vs v1:
//   • banding  → triangular-PDF dither is added before 8-bit quantisation
//                (dark gradients band badly on OLED without it)
//   • jaggies  → silhouette pixels are 3x3 supersampled into real coverage
//   • size     → the bust is ~28% of the screen height, sitting low, so the
//                space above is free for UI
//   • latency → pre-render is kicked off at app startup (LuteaRoot) so the
//                bitmap is usually already cached by the time the user opens
//                the Effect tab. Race-safe via a Mutex — if the user taps
//                Effect while pre-render is still running, EffectVisual's
//                own LaunchedEffect joins the same in-flight render instead
//                of starting a second one.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Pre-renders the bust into [BustCache]. Call from app startup (e.g. LuteaRoot)
 * so the bitmap is ready by the time the user opens the Effect tab.
 *
 * Race-safe: if EffectVisual mounts while this is still running, both callers
 * share the same render job via [renderMutex] + double-checked locking. No
 * second render is started.
 */
suspend fun preloadEffectVisual(accentColor: Color = Color(0xFFD4A03A)): ImageBitmap {
    val key = accentColor.toArgb()
    BustCache.get(key)?.let { return it }
    renderMutex.withLock {
        BustCache.get(key)?.let { return it }
        val img = withContext(Dispatchers.Default) { renderBust(accentColor) }
        BustCache.put(key, img)
        return img
    }
}

private val renderMutex = Mutex()

@Composable
fun EffectVisual(
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFFD4A03A),
) {
    val key = accentColor.toArgb()
    var image by remember(key) { mutableStateOf(BustCache.get(key)) }

    LaunchedEffect(key) {
        if (image == null) {
            image = preloadEffectVisual(accentColor)
        }
    }

    val reveal by animateFloatAsState(
        targetValue = if (image != null) 1f else 0f,
        animationSpec = tween(900),
        label = "bustReveal",
    )
    val infinite = rememberInfiniteTransition(label = "bustIdle")
    val pulse by infinite.animateFloat(
        initialValue = 0.07f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    // The light drifts; the geometry never moves.
    val drift by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )

    Box(modifier = modifier.background(Color.Black)) {
        // Layer 1: faint accent backlight behind the head.
        Canvas(Modifier.fillMaxSize()) {
            val l = bustLayout(size.width, size.height)
            val c = Offset(l.centerX, l.headCenterY)
            val r = l.size * 0.55f
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

        // Layer 2: the rendered bust (offscreen so the sheen only touches bust pixels).
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = reveal
                },
        ) {
            val img = image ?: return@Canvas
            val l = bustLayout(size.width, size.height)
            drawImage(
                image = img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(l.left.toInt(), l.top.toInt()),
                dstSize = IntSize(l.size.toInt(), l.size.toInt()),
                filterQuality = FilterQuality.High,
            )
            val sc = Offset(l.centerX + drift * l.size * 0.10f, l.top + l.size * 0.20f)
            val sr = l.size * 0.22f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
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

private class BustLayout(val left: Float, val top: Float, val size: Float) {
    val centerX get() = left + size / 2f
    val headCenterY get() = top + size * 0.33f
}

/**
 * The bitmap is square; the visible bust (head + neck + shoulders) fills ~80% of
 * its height. Scaling the bitmap to 35% of the screen height makes the visible
 * bust ~28% of the screen height — small, bottom-anchored, lots of space above.
 */
private fun bustLayout(cw: Float, ch: Float): BustLayout {
    val s = ch * 0.35f
    return BustLayout((cw - s) / 2f, ch * 0.95f - s, s)
}

private object BustCache {
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

/** Blank sculptor's bust: cranium, soft face plane, jaw, chin, ears, neck, shoulders. No facial features. */
private fun bustSdf(x: Float, y: Float, z: Float): Float {
    val ax = abs(x)
    var d = ell(x, y, z, 0f, 0.16f, -0.08f, 0.56f, 0.62f, 0.62f)                    // cranium
    d = smin(d, ell(x, y, z, 0f, -0.10f, 0.16f, 0.42f, 0.55f, 0.46f), 0.30f)         // face plane
    d = smin(d, ell(x, y, z, 0f, -0.30f, 0.14f, 0.45f, 0.38f, 0.46f), 0.30f)         // jaw
    d = smin(d, ell(x, y, z, 0f, -0.64f, 0.30f, 0.20f, 0.12f, 0.15f), 0.20f)         // chin
    d = smin(d, ell(ax, y, z, 0.30f, -0.18f, 0.30f, 0.16f, 0.14f, 0.16f), 0.18f)     // jaw angle
    d = smin(d, ell(ax, y, z, 0.56f, -0.02f, -0.04f, 0.05f, 0.15f, 0.09f), 0.05f)    // ears
    d = smin(d, cap(x * 0.85f, y, z, 0f, -0.70f, -0.02f, 0f, -2.0f, -0.02f, 0.28f), 0.16f) // neck
    d = smin(d, ell(x, y, z, 0f, -1.95f, -0.08f, 1.35f, 0.48f, 0.50f), 0.60f)         // shoulders
    return d
}

// ─────────────────────────────── renderer ───────────────────────────────

private const val TILT = 0.05f
private val COS_T = cos(TILT.toDouble()).toFloat()
private val SIN_T = sin(TILT.toDouble()).toFloat()

private const val IMG = 640
private const val VIEW_H = 3.0f
private const val CAM_Y = -0.5f

private fun sceneSdf(x: Float, y: Float, z: Float): Float =
    bustSdf(x, COS_T * y - SIN_T * z, SIN_T * y + COS_T * z)

/** Orthographic ray down -Z. Returns hit distance t, or -1 on a miss. */
private fun marchT(u: Float, v: Float): Float {
    var t = 6.5f
    while (t < 9.6f) {
        val d = sceneSdf(u, v, 8f - t)
        if (d < 0.0012f) return t
        t += d * 0.9f
    }
    return -1f
}

private fun smooth01(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/** Cheap integer hash → [0,1). */
private fun hash01(i: Int, j: Int, seed: Int): Float {
    var h = i * 374761393 + j * 668265263 + seed * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777216f
}

private fun pixelU(i: Float) = (i / IMG - 0.5f) * VIEW_H
private fun pixelV(j: Float) = (0.5f - j / IMG) * VIEW_H + CAM_Y

private suspend fun renderBust(accent: Color): ImageBitmap = coroutineScope {
    val hitT = FloatArray(IMG * IMG)
    val pixels = IntArray(IMG * IMG)
    val workers = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)

    // Phase 1: primary rays.
    (0 until workers).map { w ->
        async(Dispatchers.Default) {
            var j = w
            while (j < IMG) {
                for (i in 0 until IMG) hitT[j * IMG + i] = marchT(pixelU(i + 0.5f), pixelV(j + 0.5f))
                j += workers
            }
        }
    }.awaitAll()

    // Phase 2: shading + edge supersampling (needs neighbours, hence the barrier).
    (0 until workers).map { w ->
        async(Dispatchers.Default) {
            val rgb = FloatArray(3)
            var j = w
            while (j < IMG) {
                shadeRow(j, hitT, pixels, accent, rgb)
                j += workers
            }
        }
    }.awaitAll()

    val bmp = Bitmap.createBitmap(IMG, IMG, Bitmap.Config.ARGB_8888)
    bmp.setPixels(pixels, 0, IMG, 0, 0, IMG, IMG)
    bmp.asImageBitmap()
}

private fun shadeRow(j: Int, hitT: FloatArray, out: IntArray, accent: Color, rgb: FloatArray) {
    val fadeY = 1f - smooth01((j.toFloat() / IMG - 0.74f) / 0.24f) // dissolve the cut-off bust into black

    for (i in 0 until IMG) {
        val idx = j * IMG + i
        val tc = hitT[idx]
        val centreHit = tc >= 0f

        // Is any neighbour different? Then this pixel straddles the silhouette.
        var edge = false
        loop@ for (dj in -1..1) for (di in -1..1) {
            val ni = i + di; val nj = j + dj
            if (ni < 0 || nj < 0 || ni >= IMG || nj >= IMG) continue
            if ((hitT[nj * IMG + ni] >= 0f) != centreHit) { edge = true; break@loop }
        }
        if (!centreHit && !edge) { out[idx] = 0; continue }

        var coverage = 1f
        var su = pixelU(i + 0.5f)
        var sv = pixelV(j + 0.5f)
        var st = tc
        if (edge) {
            var hits = 0
            var found = centreHit
            for (sj in 0 until 3) for (si in 0 until 3) {
                val u = pixelU(i + (si + 0.5f) / 3f)
                val v = pixelV(j + (sj + 0.5f) / 3f)
                val t = marchT(u, v)
                if (t >= 0f) {
                    hits++
                    if (!found) { found = true; su = u; sv = v; st = t }
                }
            }
            if (hits == 0) { out[idx] = 0; continue }
            coverage = hits / 9f
        }

        shade(su, sv, st, accent, rgb)

        // Triangular dither (±1 LSB) so dark gradients don't band.
        val n = hash01(i, j, 1) + hash01(i, j, 2) - 1f
        val fadeX = smooth01(min(i, IMG - 1 - i) / (0.05f * IMG))
        val a = (coverage * fadeY * fadeX * 255f + n + 0.5f).toInt().coerceIn(0, 255)
        if (a == 0) { out[idx] = 0; continue }
        val ri = (rgb[0] * 255f + n + 0.5f).toInt().coerceIn(0, 255)
        val gi = (rgb[1] * 255f + n + 0.5f).toInt().coerceIn(0, 255)
        val bi = (rgb[2] * 255f + n + 0.5f).toInt().coerceIn(0, 255)
        out[idx] = (a shl 24) or (ri shl 16) or (gi shl 8) or bi
    }
}

private val L1 = normalise(-0.45f, 0.75f, 0.65f)
private val L2 = normalise(0.7f, 0.2f, 0.4f)
private val HV = normalise(L1[0], L1[1], L1[2] + 1f)

private fun normalise(x: Float, y: Float, z: Float): FloatArray {
    val l = len3(x, y, z)
    return floatArrayOf(x / l, y / l, z / l)
}

/** Lit colour at the surface point found by marching ray (u,v) to distance t. Writes linear 0..1 RGB. */
private fun shade(u: Float, v: Float, t: Float, accent: Color, rgb: FloatArray) {
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

    val nvx = nx
    val nvy = COS_T * ny + SIN_T * nz
    val nvz = -SIN_T * ny + COS_T * nz

    val ndl1 = (nvx * L1[0] + nvy * L1[1] + nvz * L1[2]).coerceIn(0f, 1f)
    val ndl2 = (nvx * L2[0] + nvy * L2[1] + nvz * L2[2]).coerceIn(0f, 1f)

    // soft shadow toward the key light (chin onto neck)
    var sh = 1f
    var tt = 0.05f
    for (s in 0 until 16) {
        val dd = sceneSdf(px + L1[0] * tt, py + L1[1] * tt, pz + L1[2] * tt)
        sh = min(sh, (7f * dd / tt).coerceIn(0f, 1f))
        tt += dd.coerceIn(0.03f, 0.25f)
    }
    sh = smooth01(sh)

    // ambient occlusion
    var ao = 0f
    for (k in 1..4) {
        val hh = 0.06f * k
        val dd = sceneSdf(px + nx * hh, py + ny * hh, pz + nz * hh)
        ao += (hh - dd) / (1 shl k).toFloat()
    }
    ao = (1f - 1.6f * ao).coerceIn(0f, 1f)

    val ndv = nvz.coerceIn(0f, 1f)
    val om = 1f - ndv
    val fres = om * om * om
    val rx = 2f * ndv * nvx
    val ry = 2f * ndv * nvy

    // Fake studio: two tall softboxes left/right + a top box. This is what carves the form.
    val win = smooth01((ry + 0.5f) / 0.6f) * smooth01((0.9f - ry) / 0.4f)
    val up = (ry * 0.5f + 0.5f).coerceIn(0f, 1f)
    val env = 0.07f + 0.40f * up * up * up +
        1.3f * exp(-(rx + 0.62f) * (rx + 0.62f) * 14f) * win +
        0.85f * exp(-(rx - 0.70f) * (rx - 0.70f) * 16f) * win +
        0.9f * exp(-((ry - 0.78f) * (ry - 0.78f) * 40f + rx * rx * 3f))

    val ndh = (nvx * HV[0] + nvy * HV[1] + nvz * HV[2]).coerceIn(0f, 1f)

    var col = 0.028f + 0.12f * ndl1 * sh * ao + 0.04f * ndl2 * ao +
        0.65f * env * (0.10f + 0.85f * fres) * ao +
        0.9f * ndh.pow(140f) * sh
    col += 0.20f * fres * (0.6f + 0.4f * nvy).coerceIn(0f, 1f) * ao

    // faint accent kicker on the right edge so the glow and the bust feel connected
    val kick = 0.22f * fres * (nvx * 0.8f + nvy * 0.2f).coerceIn(0f, 1f) * ao

    rgb[0] = (col * 0.96f + accent.red * kick).coerceIn(0f, 1f)
    rgb[1] = (col * 0.99f + accent.green * kick).coerceIn(0f, 1f)
    rgb[2] = (col * 1.06f + accent.blue * kick).coerceIn(0f, 1f)
}
