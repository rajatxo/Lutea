package com.rajatxo.lutea.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * Dark, 3D, skeuomorphic slider thumb (pill with three grooves + a recessed LED).
 *
 *  - active = false : LED is a white, meshed metal capsule
 *  - active = true  : LED lights up lime/yellow with a real glow that also bleeds onto the body
 *
 * The change is animated (fast on, slower off), and the animation is read in the draw phase,
 * so it never recomposes.
 *
 * Give it a pill size, roughly 2.35 : 1, e.g.  Modifier.height(48.dp).aspectRatio(2.35f)
 * The drawing keeps a small margin inside its bounds for the drop shadow, so don't clip it.
 * It is drawn horizontally; wrap with Modifier.rotate(90f) if your slider is vertical.
 *
 * @param active    true while the wheel is being rotated / the thumb is being dragged
 * @param activeColor LED colour when lit. Default matches the reference photo (yellow-green);
 *                    use Color(0xFFFFE600) for a pure yellow.
 */
@Composable
fun Lutea3DThumb(
    active: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFFB8FF1A),
) {
    val glowState = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = if (active) 140 else 280),
        label = "thumbGlow",
    )
    val shadowPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val glowPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }

    Canvas(modifier = modifier) {
        val glow = glowState.value

        // ---- geometry (everything is a fraction of the canvas height) ----
        val bodyH = size.height * 0.84f
        val bodyW = size.width - size.height * 0.16f
        val bodyL = size.height * 0.08f
        val bodyT = size.height * 0.04f
        val r = bodyH / 2f

        val ledH = bodyH * 0.46f
        val ledW = bodyH * 0.13f
        val ledCx = bodyL + bodyW * 0.80f
        val ledL = ledCx - ledW / 2f
        val ledT = bodyT + (bodyH - ledH) / 2f

        val wellW = ledW * 1.75f
        val wellH = ledH * 1.17f
        val wellL = ledCx - wellW / 2f
        val wellT = bodyT + (bodyH - wellH) / 2f

        val hairline = 1.dp.toPx()

        // ---- 1. drop shadow under the whole thumb ----
        shadowPaint.color = 0xFF222226.toInt()
        shadowPaint.setShadowLayer(size.height * 0.13f, 0f, size.height * 0.07f, 0xB8000000.toInt())
        drawIntoCanvas {
            it.nativeCanvas.drawRoundRect(
                bodyL, bodyT, bodyL + bodyW, bodyT + bodyH, r, r, shadowPaint,
            )
        }

        // ---- 2. convex body ----
        pill(
            Brush.verticalGradient(listOf(Color(0xFF3F3F44), Color(0xFF2A2A2E), Color(0xFF1C1C20))),
            bodyL, bodyT, bodyW, bodyH, r,
        )

        // ---- 3. bevel: light rim on top, dark rim on the bottom ----
        val sw = 1.5.dp.toPx()
        translate(bodyL + sw / 2f, bodyT + sw / 2f) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.03f), Color.Black.copy(alpha = 0.5f)),
                ),
                size = Size(bodyW - sw, bodyH - sw),
                cornerRadius = CornerRadius(r - sw / 2f),
                style = Stroke(width = sw),
            )
        }

        // ---- 4. soft top sheen (gives the "plastic" curvature) ----
        pill(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)),
            bodyL + bodyH * 0.10f, bodyT + bodyH * 0.05f, bodyW - bodyH * 0.20f, bodyH * 0.42f, bodyH * 0.21f,
        )

        // ---- 5. LED light spilling onto the body (clipped to the body) ----
        if (glow > 0.01f) {
            val bodyPath = Path().apply {
                addRoundRect(RoundRect(bodyL, bodyT, bodyL + bodyW, bodyT + bodyH, CornerRadius(r)))
            }
            clipPath(bodyPath) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(activeColor.copy(alpha = 0.26f * glow), Color.Transparent),
                        center = Offset(ledCx, bodyT + bodyH / 2f),
                        radius = bodyH * 1.05f,
                    ),
                    radius = bodyH * 1.05f,
                    center = Offset(ledCx, bodyT + bodyH / 2f),
                )
            }
        }

        // ---- 6. three engraved grip grooves ----
        val gH = bodyH * 0.46f
        val gW = bodyH * 0.085f
        val gY = bodyT + (bodyH - gH) / 2f
        val gX0 = bodyL + bodyH * 0.62f
        val gStep = bodyH * 0.21f
        for (i in 0..2) {
            val gx = gX0 + gStep * i
            // light lip below/right of the cut, then the dark cut itself
            pill(SolidColor(Color.White.copy(alpha = 0.11f)), gx + hairline * 0.5f, gY + hairline * 1.3f, gW, gH)
            pill(
                Brush.horizontalGradient(listOf(Color(0xFF0B0B0D), Color(0xFF2B2B30))),
                gx, gY, gW, gH,
            )
        }

        // ---- 7. recessed well for the LED ----
        pill(SolidColor(Color.White.copy(alpha = 0.09f)), wellL, wellT + hairline * 1.4f, wellW, wellH)
        pill(
            Brush.verticalGradient(listOf(Color(0xFF060607), Color(0xFF18181B))),
            wellL, wellT, wellW, wellH,
        )

        // ---- 8. LED glow (two blurred passes: tight + wide) ----
        if (glow > 0.01f) {
            glowPaint.color = activeColor.copy(alpha = glow).toArgb()
            glowPaint.setShadowLayer(bodyH * 0.30f, 0f, 0f, activeColor.copy(alpha = 0.95f * glow).toArgb())
            drawIntoCanvas {
                it.nativeCanvas.drawRoundRect(ledL, ledT, ledL + ledW, ledT + ledH, ledW / 2f, ledW / 2f, glowPaint)
            }
            glowPaint.setShadowLayer(bodyH * 0.62f, 0f, 0f, activeColor.copy(alpha = 0.55f * glow).toArgb())
            drawIntoCanvas {
                it.nativeCanvas.drawRoundRect(ledL, ledT, ledL + ledW, ledT + ledH, ledW / 2f, ledW / 2f, glowPaint)
            }
        }

        // ---- 9. LED body: white mesh metal -> hot lime tube ----
        val litTop = lerp(activeColor, Color.White, 0.55f)
        val top = lerp(Color(0xFFF6F6F8), litTop, glow)
        val bottom = lerp(Color(0xFF93939A), activeColor, glow)
        pill(Brush.verticalGradient(listOf(top, bottom)), ledL, ledT, ledW, ledH)

        // tube sheen
        pill(
            Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.22f + 0.28f * glow), Color.Transparent),
            ),
            ledL, ledT, ledW, ledH,
        )

        // mesh texture (fades out as the LED lights up)
        if (glow < 0.99f) {
            val ledPath = Path().apply {
                addRoundRect(RoundRect(ledL, ledT, ledL + ledW, ledT + ledH, CornerRadius(ledW / 2f)))
            }
            val step = 2.3.dp.toPx()
            val dot = 0.65.dp.toPx()
            val meshColor = Color(0xFF3A3A40).copy(alpha = 0.5f * (1f - glow))
            clipPath(ledPath) {
                var y = ledT + step / 2f
                var row = 0
                while (y < ledT + ledH) {
                    var x = ledL + (if (row % 2 == 0) step / 2f else step)
                    while (x < ledL + ledW) {
                        drawCircle(meshColor, radius = dot, center = Offset(x, y))
                        x += step
                    }
                    y += step / 2f
                    row++
                }
            }
        }

        // thin dark outline so the unlit LED sits in the well
        translate(ledL, ledT) {
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.35f * (1f - glow)),
                size = Size(ledW, ledH),
                cornerRadius = CornerRadius(ledW / 2f),
                style = Stroke(width = hairline),
            )
        }
    }
}

/** Rounded rect drawn at (x, y). Translates first so gradients span THIS shape, not the canvas. */
private fun DrawScope.pill(
    brush: Brush,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    radius: Float = minOf(w, h) / 2f,
) {
    translate(x, y) {
        drawRoundRect(brush = brush, size = Size(w, h), cornerRadius = CornerRadius(radius))
    }
}
