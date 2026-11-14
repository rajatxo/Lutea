package com.rajatxo.lutea.ui.components

import android.os.Build
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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Shimmer speed multiplier (the component's `raysSpeed`). */
private const val LIGHT_RAYS_SPEED = 0.45f
/** Overall brightness (the component's `opacity`). */
private const val LIGHT_RAYS_BRIGHTNESS = 0.42f
/**
 * Where the light sits, as a fraction of the height ABOVE the top edge.
 * -0.55 puts the source well outside the frame, so the rays fade in from the
 * edge instead of reading as a lamp parked on it.
 */
private const val LIGHT_RAYS_SOURCE_Y = -0.55f
/** Ray length, multiplied by the larger side. Longer = lazier, softer falloff. */
private const val LIGHT_RAYS_REACH = 1.6f

/**
 * Light rays background — the react-bits "LightRays" effect.
 *
 * On Android 13+ (API 33) this runs the *original fragment shader* from the
 * react-bits component, translated 1:1 to AGSL and evaluated on the GPU through
 * `RuntimeShader`, so the result is the exact same effect (same rays, same
 * bands, same haze, same intro reveal).
 *
 * On older versions there is no runtime-shader support, so a lightweight
 * Canvas approximation is used instead.
 */
@Composable
fun LightRaysBackground(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    intensity: Float = 1f,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LightRaysShaderBackground(
                modifier = Modifier.fillMaxSize(),
                color = color,
                intensity = intensity,
            )
        } else {
            LightRaysCanvasBackground(
                modifier = Modifier.fillMaxSize(),
                color = color,
                intensity = intensity,
            )
        }
        // Black fade: knocks the whole backdrop back a stop so the rays read as
        // dim light instead of a bright wash.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.28f),
                        0.55f to Color.Black.copy(alpha = 0.16f),
                        1f to Color.Black.copy(alpha = 0.06f),
                    )
                    onDrawBehind { drawRect(brush) }
                },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// API 33+  —  faithful AGSL port of the react-bits shader
// ─────────────────────────────────────────────────────────────────────────────

@androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LightRaysShaderBackground(
    modifier: Modifier,
    color: Color,
    intensity: Float,
) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (true) {
            time.floatValue = (System.nanoTime() - start) / 1_000_000_000f
            // 30 fps: the shader animates slowly, and this halves the GPU cost.
            delay(33L)
        }
    }

    val paint = remember { android.graphics.Paint().apply { isAntiAlias = false } }
    val shader = remember {
        android.graphics.RuntimeShader(LIGHT_RAYS_AGSL).also { paint.shader = it }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val t = time.floatValue

        // Intro reveal: 0 → 1 over 1.8 s, exactly like the component.
        val opening = (t / 1.8f).coerceIn(0f, 1f)
        val eased = 1f - (1f - opening) * (1f - opening) * (1f - opening)
        val reveal = smoother(opening / 0.5f)

        val w = size.width
        val h = size.height
        shader.setFloatUniform("uSize", w, h)
        shader.setFloatUniform("uTime", t * LIGHT_RAYS_SPEED)
        // Origin: top-centre, above the page (the component's default).
        shader.setFloatUniform("uSource", w * 0.5f, LIGHT_RAYS_SOURCE_Y * h)
        shader.setFloatUniform("uAim", 0f, 1f)
        shader.setFloatUniform("uReach", maxOf(w, h) * LIGHT_RAYS_REACH)
        shader.setFloatUniform(
            "uColor",
            (color.red * intensity).coerceIn(0f, 1f),
            (color.green * intensity).coerceIn(0f, 1f),
            (color.blue * intensity).coerceIn(0f, 1f),
        )
        shader.setFloatUniform("uIntro", eased * 2.6f, reveal)
        shader.setFloatUniform("uOpacity", LIGHT_RAYS_BRIGHTNESS)

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, w, h, paint)
        }
    }
}

private fun smoother(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

/**
 * The react-bits fragment shader (Backgrounds/LightRays), translated to AGSL.
 *
 * Differences from the WebGL source, none of which change the look:
 *  • `fwidth` is unavailable, so the band width term is dropped — with the
 *    default sharpness (0.35) it was always the constant 0.353 anyway.
 *  • cursor shadow, click pulses and grain are removed (they need a pointer,
 *    which a phone background does not have).
 *  • props are baked in at their documented defaults.
 */
private const val LIGHT_RAYS_AGSL = """
uniform float2 uSize;
uniform float uTime;
uniform float2 uSource;
uniform float2 uAim;
uniform float uReach;
uniform float3 uColor;
uniform float2 uIntro;
uniform float uOpacity;

float hash21(float2 p) {
  p = fract(p * float2(123.34, 456.21));
  p += dot(p, p + float2(45.32));
  return fract(p.x * p.y);
}

float valueNoise(float2 p) {
  float2 i = floor(p);
  float2 f = p - i;
  float2 w = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
  return mix(
    mix(hash21(i), hash21(i + float2(1.0, 0.0)), w.x),
    mix(hash21(i + float2(0.0, 1.0)), hash21(i + float2(1.0, 1.0)), w.x),
    w.y
  );
}

float rayShape(float strength) {
  float edge = 0.46;
  return smoothstep(0.62 - edge, 0.62 + edge, strength);
}

half4 main(float2 fragCoord) {
  float2 delta = fragCoord - uSource;
  float dist = length(delta);
  float2 dir = delta / max(dist, 0.0001);
  float cosAngle = dot(dir, uAim);
  float depth = dist / uReach;

  float src = exp(-depth * depth * 9.0) * 0.9 + exp(-depth * 3.2) * 0.25;
  float glow = 0.6 * src * smoothstep(-0.2, 0.6, cosAngle);
  float spread = pow(max(cosAngle, 0.0), 1.9);
  float lenFall = clamp(1.0 - dist / (uReach * 3.0), 0.0, 1.0);
  float fadeFall = clamp(1.0 - dist / (uReach * 1.0), 0.5, 1.0);
  float envelope = spread * lenFall * fadeFall;
  float reveal = uIntro.y * (1.0 - smoothstep(uIntro.x - 0.35, uIntro.x, depth));

  float phaseA = cosAngle * 36.2214 + uTime * 1.5;
  float phaseB = -cosAngle * 21.11349 + uTime * 1.5;
  float phaseC = cosAngle * 22.3991 + uTime * 1.1;
  float phaseD = -cosAngle * 18.0234 + uTime * 1.1;
  float strengthA = clamp(0.75 + 0.15 * sin(phaseA) + 0.2 * cos(phaseB), 0.0, 1.0);
  float strengthB = clamp(0.75 + 0.15 * sin(phaseC) + 0.2 * cos(phaseD), 0.0, 1.0);
  float light = envelope * (0.5 * rayShape(strengthA) + 0.4 * rayShape(strengthB));

  float around = atan(uAim.x * dir.y - uAim.y * dir.x, cosAngle);
  float2 drift = float2(around * 2.2 + uTime * 0.035, depth * 2.6 - uTime * 0.11);
  float fog = 0.65 * valueNoise(drift * float2(3.0, 1.0))
            + 0.35 * valueNoise(drift * float2(7.0, 2.3) + float2(4.1));
  light *= mix(1.0, 0.25 + 1.2 * fog, 0.32);

  light = (light + glow) * reveal;

  float near = clamp(1.2 - dist / uSize.y, 0.0, 1.0);
  float3 tint = float3(0.1, 0.3, 0.5) + near * float3(0.8, 0.6, 0.5);
  float3 hue = tint * uColor;

  float level = light < 0.75 ? light : 0.75 + 0.25 * (1.0 - exp(-(light - 0.75) / 0.25));
  float top = max(hue.r, max(hue.g, hue.b));
  hue = mix(hue, float3(top, top, top), smoothstep(0.8, 1.0, level) * 0.45);
  float3 shown = clamp(hue * level, 0.0, 1.0) * level;
  return half4(clamp(shown, 0.0, level) * uOpacity, level * uOpacity);
}
"""

// ─────────────────────────────────────────────────────────────────────────────
// API 29–32  —  Canvas approximation (no runtime shaders available)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LightRaysCanvasBackground(
    modifier: Modifier,
    color: Color,
    intensity: Float,
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
                    onDrawBehind {
                        drawRays(
                            rays = rays,
                            t = time.floatValue * LIGHT_RAYS_SPEED,
                            color = color,
                            intensity = intensity * LIGHT_RAYS_BRIGHTNESS,
                        )
                    }
                },
        )
        // Distance fade: the rays dissolve toward the bottom of the page.
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

    val srcX = w * 0.5f
    val srcY = LIGHT_RAYS_SOURCE_Y * h

    var minA = Float.MAX_VALUE
    var maxA = -Float.MAX_VALUE
    for (cx in floatArrayOf(0f, w)) {
        for (cy in floatArrayOf(0f, h)) {
            val a = atan2(cy - srcY, cx - srcX)
            if (a < minA) minA = a
            if (a > maxA) maxA = a
        }
    }
    minA -= 0.05f
    maxA += 0.05f

    val count = 46
    val reach = sqrt(w * w + h * h) * 1.55f
    val out = ArrayList<RayShape>(count)
    for (i in 0 until count) {
        val u = (i + 0.5f) / count
        val ang = minA + (maxA - minA) * u
        val dx = cos(ang)
        val dy = sin(ang)
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
    val src = Offset(size.width * 0.5f, LIGHT_RAYS_SOURCE_Y * size.height)

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
