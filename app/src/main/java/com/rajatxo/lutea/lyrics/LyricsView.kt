package com.rajatxo.lutea.lyrics

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
//  Lutea lyrics view — studied from Mochy's LyricsGlowLine + LyricsScreen +
//  LyricsStagger, then written from scratch with original code.
//
//  Visual constants match Mochy exactly:
//    • active line: alpha 1.0, scale 1.06, white, Bold, 32sp / 40sp line-height
//    • upcoming:    alpha 0.5,  scale 0.98
//    • default:     alpha 0.38, scale 0.95
//    • past:        alpha 0.24, scale 0.93
//    • blur: 0px (active), 5px (adjacent), 12px (far) — DECAL tile mode
//    • glow: subtle halo on held words only, as a separate unclipped two-pass pass
//    • focus: active line at 5.5% from top (min 72dp)
//    • auto-scroll: 950ms tween, CubicBezier(0.42, 0, 0.16, 1) — eases in, no kick
//    • alignment: main vocal left, secondary/background centred, duet partner right
//    • immersive fade: top 10% + bottom 38% dissolve into the page colour
//    • user-scroll pause: 3.5s
//    • word rise: 3dp lift on the sung word, easing in/out
// ─────────────────────────────────────────────────────────────────────────────

@Immutable
data class LyricsStyle(
    val fontSize: TextUnit = 32.sp,
    val lineHeight: TextUnit = 40.sp,
    val fontFamily: FontFamily = FontFamily.Default,
    val fontWeight: FontWeight = FontWeight.Bold,
    val sungColor: Color = Color.White,
    val unsungColor: Color = Color.White.copy(alpha = 0.58f),
    val inactiveColor: Color = Color.White.copy(alpha = 0.36f),
    val glowColor: Color = Color.White,
    val glowRadius: Dp = 12.dp,         // glow radius for held notes (wider than before)
    val liftDistance: Dp = 1.5.dp,      // reduced from 3dp — smoother, less jumpy
    val heldZoom: Float = 0.04f,       // reduced from 0.08 — subtle swell, not jumpy
    val heldGlowAlpha: Float = 0.32f,  // glow intensity on held notes (was 0.5 — too hot on holds)
    val maxBlur: Dp = 12.dp,
    val focusFraction: Float = 0.055f,
    val minFocusDp: Dp = 72.dp,
    val lineGap: Dp = 12.dp,
    val horizontalPadding: Dp = 36.dp,
    val fadeColor: Color = Color(0xFF0A0A0A),   // page background, for the top/bottom fade
)

private fun smooth(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
private fun easeOutCubic(x: Float): Float { val i = 1f - x; return 1f - i * i * i }

private val RecenterEasing = CubicBezierEasing(0.42f, 0f, 0.16f, 1f)  // Mochy Style 1

/** 0..1 "this word is being pulled up" envelope, purely a function of time (seek-safe). */
private fun wordEnvelope(startMs: Long, endMs: Long, t: Long): Float {
    val dur = max(endMs - startMs, 1L).toFloat()
    val ramp = min(280f, dur * 0.6f).coerceAtLeast(90f)
    val begin = startMs - 60L
    if (t < begin) return 0f
    val up = easeOutCubic(((t - begin) / ramp).coerceIn(0f, 1f))
    val down = if (t <= endMs) 0f else smooth((t - endMs) / 420f)
    return up * (1f - down)
}

private fun activeLine(lines: List<LyricLine>, t: Long): Int {
    var ans = -1
    for (i in lines.indices) {
        if (lines[i].startMs > t) break
        if (!lines[i].isBackground) ans = i
    }
    return ans
}

private class DrawParams(
    val sung: Color,
    val unsung: Color,
    val glow: Color,
    val glowRadiusPx: Float,
    val liftPx: Float,
    val softPx: Float,
    val heldZoom: Float,
    val heldGlowAlpha: Float,
    val synced: Boolean,
    val measurer: TextMeasurer,
)

private class WordGroup(
    val firstToken: Int, val lastToken: Int,
    val startMs: Long, val endMs: Long, val text: String,
) {
    var left = 0f; var top = 0f; var right = 0f; var bottom = 0f
    var visualLine = 0; var layout: TextLayoutResult? = null
    val letterCount: Int = text.count { it.isLetterOrDigit() }.coerceAtLeast(1)
}

private fun buildGroups(line: LyricLine): List<WordGroup> {
    val out = ArrayList<WordGroup>()
    var i = 0
    while (i < line.tokens.size) {
        var j = i
        while (j + 1 < line.tokens.size && line.tokens[j + 1].wordIndex == line.tokens[i].wordIndex) j++
        val txt = (i..j).joinToString("") { line.tokens[it].text }.trimEnd()
        out.add(WordGroup(i, j, line.tokens[i].startMs, line.tokens[j].endMs, txt))
        i = j + 1
    }
    return out
}

private class LineGeometry(val line: LyricLine, val textStyle: TextStyle) {
    var layout: TextLayoutResult? = null
    private var width = -1
    private var rtl = false
    private val n = line.tokens.size
    private val tLeft = FloatArray(n)
    private val tRight = FloatArray(n)
    private val tLine = IntArray(n)
    private val tTop = FloatArray(n)
    private val tBottom = FloatArray(n)
    private val tStartOffset = IntArray(n)
    private val groups: List<WordGroup> = buildGroups(line)

    fun ensureLayout(measurer: TextMeasurer, maxWidth: Int) {
        if (layout != null && width == maxWidth) return
        width = maxWidth
        if (line.text.isEmpty()) return
        val l = measurer.measure(text = line.text, style = textStyle, overflow = TextOverflow.Visible, constraints = Constraints(maxWidth = maxWidth))
        layout = l
        rtl = l.getParagraphDirection(0) == ResolvedTextDirection.Rtl
        var cursor = 0
        val len = line.text.length
        for (i in 0 until n) {
            val s = min(cursor, len - 1).coerceAtLeast(0)
            val e = min(cursor + line.tokens[i].text.length, len)
            cursor += line.tokens[i].text.length
            val b0 = l.getBoundingBox(s)
            val b1 = l.getBoundingBox((e - 1).coerceAtLeast(s))
            tLeft[i] = min(b0.left, b1.left)
            tRight[i] = max(b0.right, b1.right)
            tLine[i] = l.getLineForOffset(s)
            tTop[i] = l.getLineTop(tLine[i])
            tBottom[i] = l.getLineBottom(tLine[i])
            tStartOffset[i] = cursor
        }
        for (g in groups) {
            var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
            for (i in g.firstToken..g.lastToken) { lo = min(lo, tLeft[i]); hi = max(hi, tRight[i]) }
            g.left = lo; g.right = hi; g.visualLine = tLine[g.firstToken]
            g.top = l.getLineTop(g.visualLine); g.bottom = l.getLineBottom(g.visualLine); g.layout = null
        }
    }

    private fun brushFor(front: Float, p: DrawParams): Brush {
        val clear = p.sung.copy(alpha = 0f)
        return if (!rtl)
            Brush.horizontalGradient(0f to p.sung, 1f to clear, startX = front - p.softPx * 0.15f, endX = front + p.softPx * 0.85f)
        else
            Brush.horizontalGradient(0f to clear, 1f to p.sung, startX = front - p.softPx * 0.85f, endX = front + p.softPx * 0.15f)
    }

    fun draw(scope: DrawScope, t: Float, fillAlpha: Float, p: DrawParams) {
        val l = layout ?: return
        with(scope) {
            if (!p.synced) { drawText(l, color = p.sung.copy(alpha = 0.92f)); return }
            if (t < 0f || fillAlpha <= 0.002f) { drawText(l, color = p.unsung); return }

            val lc = l.lineCount
            val hasTok = BooleanArray(lc); val anyFilled = BooleanArray(lc); val allFilled = BooleanArray(lc) { true }
            val partialFront = FloatArray(lc) { Float.NaN }; val lastEdge = FloatArray(lc) { Float.NaN }
            for (i in 0 until n) {
                val k = tLine[i]; hasTok[k] = true
                val tk = line.tokens[i]
                val dur = max(tk.endMs - tk.startMs, 1L)
                val pr = ((t - tk.startMs).toFloat() / dur).coerceIn(0f, 1f)
                if (pr >= 1f) { anyFilled[k] = true; lastEdge[k] = if (rtl) tLeft[i] else tRight[i] }
                else if (pr <= 0f) { allFilled[k] = false }
                else { anyFilled[k] = true; allFilled[k] = false; val w = tRight[i] - tLeft[i]; partialFront[k] = if (rtl) tRight[i] - pr * w else tLeft[i] + pr * w }
            }
            fun modeOf(k: Int) = if (!hasTok[k] || !anyFilled[k]) 0 else if (allFilled[k]) 2 else 1
            fun frontOf(k: Int) = if (!partialFront[k].isNaN()) partialFront[k] else lastEdge[k]

            val lifted = ArrayList<Int>()
            for (gi in groups.indices) {
                val g = groups[gi]
                val dur = max(g.endMs - g.startMs, 1L).toFloat()
                val wp = ((t - g.startMs).toFloat() / dur).coerceIn(0f, 1f)
                val fallTime = (t - g.endMs).toFloat()
                if (t >= g.startMs && fallTime < 420f) lifted.add(gi)
            }

            val canvas = drawContext.canvas
            canvas.save()
            for (gi in lifted) { val g = groups[gi]; canvas.clipRect(g.left - 2f, g.top - 1f, g.right + 2f, g.bottom + 1f, ClipOp.Difference) }
            drawText(l, color = p.unsung)
            for (k in 0 until lc) {
                val m = modeOf(k); if (m == 0) continue
                canvas.save(); canvas.clipRect(0f, l.getLineTop(k), size.width, l.getLineBottom(k))
                if (m == 2) drawText(l, color = p.sung, alpha = fillAlpha)
                else drawText(l, brush = brushFor(frontOf(k), p), alpha = fillAlpha)
                canvas.restore()
            }
            canvas.restore()
            val canvas2 = drawContext.canvas
            for (gi in lifted) {
                val g = groups[gi]
                val dur = max(g.endMs - g.startMs, 1L).toFloat()
                val wp = ((t - g.startMs) / dur).coerceIn(0f, 1f)

                // Sweep-based rise: fully risen at 85% of the word, falls over 420ms after
                val riseProgress = (wp / 0.85f).coerceIn(0f, 1f)
                val fallProgress = if (t > g.endMs) smooth(((t - g.endMs) / 420f).coerceIn(0f, 1f)) else 0f
                val rise = smooth(riseProgress) * (1f - fallProgress)
                val lift = p.liftPx * rise * (dur / 600f).coerceIn(0.4f, 0.8f)

                // Held-note detection: secPerChar > 0.1s = some hold, > 0.34s = full hold
                val secPerChar = (dur / 1000f) / g.letterCount
                val hold = smooth(((secPerChar - 0.1f) / (0.34f - 0.1f)).coerceIn(0f, 1f))

                // Glow: attack 35%, release 30%
                val glowAttack = smooth((wp / 0.35f).coerceIn(0f, 1f))
                val glowRelease = smooth(((1f - wp) / 0.3f).coerceIn(0f, 1f))
                val holdBoost = 1f + 0.3f * smooth(((secPerChar - 0.34f) / 0.5f).coerceIn(0f, 1f))
                val glow = hold * holdBoost * glowAttack * glowRelease * p.heldGlowAlpha

                // Swell: attack 20%, release 12%
                val swellAttack = smooth((wp / 0.2f).coerceIn(0f, 1f))
                val swellRelease = smooth(((1f - wp) / 0.12f).coerceIn(0f, 1f))
                val scale = 1f + hold * swellAttack * swellRelease * p.heldZoom

                val k = g.visualLine
                val mode = modeOf(k)
                val baselineY = l.getLineBaseline(k)          // swell grows upward from the baseline
                val centerX = (g.left + g.right) / 2f
                // ★ The word is NOT re-laid-out any more. We draw the very same line layout,
                //   clipped to this word's box and moved. Same glyphs, same baseline, same
                //   kerning — a single-word layout trims its line height differently from a
                //   word sitting on the 2nd/3rd row, which made those words jump up and down.
                // 1) unsung base of the word
                canvas2.save()
                canvas2.translate(0f, -lift)
                if (scale != 1f) {
                    canvas2.translate(centerX, baselineY); canvas2.scale(scale, scale); canvas2.translate(-centerX, -baselineY)
                }
                canvas2.clipRect(g.left - 2f, g.top - 1f, g.right + 2f, g.bottom + 1f)
                drawText(l, color = p.unsung)
                canvas2.restore()

                // 2) glow — a soft halo shaped like the letters themselves, never clipped to a box.
                //    A one-word layout is used ONLY for this halo, and it is aligned to the real
                //    line layout (same left edge, same baseline) and given the same lift/swell, so
                //    its glyphs sit exactly under the real ones and only the blur shows.
                if (mode != 0 && glow > 0.01f && tLine[g.lastToken] == k) {
                    val wl = g.layout ?: p.measurer.measure(
                        text = g.text, style = textStyle, overflow = TextOverflow.Visible,
                    ).also { g.layout = it }
                    val ox = g.left - wl.getBoundingBox(0).left
                    val oy = baselineY - wl.getLineBaseline(0)
                    val outerA = (glow * 1.35f).coerceAtMost(0.62f)
                    val radius = p.glowRadiusPx * (0.6f + 1.2f * glow.coerceAtMost(1f))
                    canvas2.save()
                    canvas2.translate(0f, -lift)
                    if (scale != 1f) {
                        canvas2.translate(centerX, baselineY); canvas2.scale(scale, scale); canvas2.translate(-centerX, -baselineY)
                    }
                    // wide soft bloom, then a tighter brighter core
                    for (pass in 0..1) {
                        val shadow = Shadow(
                            color = p.glow.copy(alpha = if (pass == 0) outerA else outerA * 0.7f),
                            offset = Offset.Zero,
                            blurRadius = if (pass == 0) radius * 1.6f else radius * 0.55f,
                        )
                        if (mode == 2) {
                            drawText(wl, color = p.sung, topLeft = Offset(ox, oy), alpha = fillAlpha, shadow = shadow)
                        } else {
                            drawText(wl, brush = brushFor(frontOf(k) - ox, p), topLeft = Offset(ox, oy), alpha = fillAlpha, shadow = shadow)
                        }
                    }
                    canvas2.restore()
                }

                // 3) sung fill of the word (exact line-layout glyphs, narrow clip — no glow here)
                if (mode != 0) {
                    canvas2.save()
                    canvas2.translate(0f, -lift)
                    if (scale != 1f) {
                        canvas2.translate(centerX, baselineY); canvas2.scale(scale, scale); canvas2.translate(-centerX, -baselineY)
                    }
                    canvas2.clipRect(g.left - 2f, g.top - 1f, g.right + 2f, g.bottom + 1f)
                    if (mode == 2) drawText(l, color = p.sung, alpha = fillAlpha)
                    else drawText(l, brush = brushFor(frontOf(k), p), alpha = fillAlpha)
                    canvas2.restore()
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Per-line item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LyricLineItem(
    line: LyricLine,
    synced: Boolean,
    isActive: Boolean,
    distance: Int,
    clock: State<Float>,
    measurer: TextMeasurer,
    style: LyricsStyle,
    blurEffects: Array<RenderEffect?>,
    onClick: (() -> Unit)?,
) {
    val density = LocalDensity.current
    val sizeScale = if (line.isBackground) 0.72f else 1f
    val textStyle = remember(style, line.isBackground, line.alignEnd) {
        TextStyle(
            fontFamily = style.fontFamily,
            fontWeight = if (line.isBackground) FontWeight.SemiBold else style.fontWeight,
            fontSize = style.fontSize * sizeScale,
            lineHeight = style.lineHeight * sizeScale,
            // ★ Per-singer alignment: main vocal left, secondary/background centred,
            //   duet partner (TTML agent change) right.
            textAlign = when {
                line.alignEnd -> TextAlign.End
                line.isBackground -> TextAlign.Center
                else -> TextAlign.Start
            },
            color = Color.White,
        )
    }
    val geo = remember(line, textStyle) { LineGeometry(line, textStyle) }
    val params = remember(style, density, synced, measurer, sizeScale) {
        with(density) {
            DrawParams(
                sung = style.sungColor,
                unsung = style.unsungColor,
                glow = style.glowColor,
                glowRadiusPx = style.glowRadius.toPx(),
                liftPx = style.liftDistance.toPx(),
                softPx = style.fontSize.toPx() * sizeScale * 0.8f,
                heldZoom = style.heldZoom,
                heldGlowAlpha = style.heldGlowAlpha,
                synced = synced, measurer = measurer,
            )
        }
    }

    // ★ Mochy transitions: 600ms tween for alpha/scale, 350ms for blur.
    //   Secondary/background vocals get a light spring instead, so the centred
    //   line pops in with a soft overshoot when its turn comes.
    val focusV by animateFloatAsState(
        targetValue = if (isActive && synced) 1f else 0f,
        animationSpec = if (line.isBackground) {
            spring<Float>(dampingRatio = 0.55f, stiffness = 380f)
        } else {
            tween(600)
        },
        label = "lyricFocus",
    )
    // ★ Mochy resting alpha: ACTIVE 1.0, UPCOMING 0.5, DEFAULT 0.38, PAST 0.24
    val alphaTarget = when {
        !synced -> 0.95f
        isActive -> 1f
        distance == 1 -> 0.5f
        distance == 2 -> 0.38f
        else -> 0.24f
    }
    val alphaV by animateFloatAsState(alphaTarget, tween(600), label = "lyricAlpha")

    // ★ Mochy blur: 0px (active/adjacent-inactive-0), 5px (distance 1), 12px (distance 2+)
    //   3-stage array: [null, 5px, 12px]
    val blurStep = when {
        !synced || isActive -> 0
        distance == 1 -> 1
        else -> 2
    }
    val blurV by animateFloatAsState(
        targetValue = blurStep.toFloat(),
        animationSpec = tween(350),
        label = "lyricBlur",
    )
    val interaction = remember { MutableInteractionSource() }

    Spacer(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .padding(vertical = style.lineGap)
            .graphicsLayer {
                // ★ Mochy resting scale: ACTIVE 1.06, UPCOMING 0.98, DEFAULT 0.95, PAST 0.93
                val scaleTarget = when {
                    !synced -> 1f
                    isActive -> 1.06f
                    distance == 1 -> 0.98f
                    distance == 2 -> 0.95f
                    else -> 0.93f
                }
                val s = if (synced) {
                    // interpolate from the "inactive" scale to the "active" scale via focusV
                    val inactiveScale = when {
                        distance <= 1 -> 0.98f
                        distance <= 2 -> 0.95f
                        else -> 0.93f
                    }
                    inactiveScale + (1.06f - inactiveScale) * focusV
                } else 1f
                scaleX = s; scaleY = s
                // ★ Scale from the edge the line is aligned to
                transformOrigin = TransformOrigin(
                    when {
                        line.alignEnd -> 1f
                        line.isBackground -> 0.5f
                        else -> 0f
                    },
                    0.5f,
                )
                alpha = alphaV
                val step = blurV.roundToInt().coerceIn(0, blurEffects.size - 1)
                renderEffect = blurEffects.getOrNull(step)
            }
            .layout { measurable, constraints ->
                val w = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
                geo.ensureLayout(measurer, w)
                val h = geo.layout?.size?.height ?: 0
                val placeable = measurable.measure(Constraints.fixed(w, h))
                layout(w, h) { placeable.place(0, 0) }
            }
            .drawBehind {
                // ★ Only draw the fill on the ACTIVE line (not during the focus-out transition)
                val tNow = if (synced && isActive && focusV > 0.002f) clock.value else -1f
                // fill reaches full strength in ~200ms (focusV itself takes 600ms), so the first
                // words of a line are lit the moment they are sung
                geo.draw(this, tNow, smooth(focusV * 3f), params)
            }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Public API
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LyricsView(
    lyrics: Lyrics?,
    positionMs: () -> Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    style: LyricsStyle = LyricsStyle(),
    offsetMs: Long = 0L,
) {
    if (lyrics == null) return
    if (lyrics.isInstrumental || lyrics.lines.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BasicText("Instrumental", style = TextStyle(color = style.unsungColor.copy(alpha = 0.7f), fontSize = 20.sp, fontFamily = style.fontFamily))
        }
        return
    }

    val synced = lyrics.sync != SyncType.NONE
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val listState = rememberLazyListState()

    // ★ Smooth clock: interpolate every frame, sync with actual position every 500ms.
    //    This is the key fix — reading positionMs() every frame returned the same value
    //    between polls (200ms), causing the word fill to stall then jump. Now the clock
    //    advances smoothly every frame and syncs periodically.
    val clock = remember { mutableFloatStateOf(0f) }      // ms, float: no per-frame rounding loss
    val latestPosition by rememberUpdatedState(positionMs)
    LaunchedEffect(isPlaying, offsetMs) {
        clock.floatValue = (latestPosition() + offsetMs).toFloat()   // correct from the very first frame
        var lastNanos = 0L
        var lastRaw = latestPosition()
        while (isActive) {
            withFrameNanos { now ->
                // ★ Old code did integer (now - last) / 1_000_000: at 60Hz that throws away ~0.67ms
                //   every frame (≈4% slow), and only re-synced when 200ms off — so lyrics ran late.
                val dtMs = if (lastNanos > 0L) ((now - lastNanos) / 1_000_000f).coerceIn(0f, 100f) else 0f
                lastNanos = now
                val raw = latestPosition()
                if (isPlaying) {
                    clock.floatValue += dtMs
                    // A changed value is a FRESH sample from the player (a repeated value is just
                    // stale). Pull the clock toward it gently; snap only on a real jump (seek).
                    if (raw != lastRaw) {
                        val err = (raw + offsetMs) - clock.floatValue
                        clock.floatValue += if (abs(err) > 250f) err else err * 0.2f
                    }
                } else {
                    clock.floatValue = (raw + offsetMs).toFloat()
                }
                lastRaw = raw
            }
        }
    }

    val activeIndex by remember(lyrics) {
        derivedStateOf { if (synced) activeLine(lyrics.lines, clock.floatValue.toLong()) else -1 }
    }

    // ★ Instrumental: nothing is being sung right now — either before the first line,
    //   or inside a real gap between two lines (a line whose words ended well before the
    //   next one starts). The typing dots stand in for the lyrics there.
    val waitingForLyrics by remember(lyrics, synced) {
        derivedStateOf {
            if (!synced) return@derivedStateOf false
            val lines = lyrics.lines
            if (lines.isEmpty()) return@derivedStateOf false
            val t = clock.floatValue
            val i = activeLine(lines, t.toLong())
            if (i < 0) {
                t < (lines.first().startMs - 900L).toFloat()
            } else {
                val gapStart = lines[i].endMs
                val gapEnd = lines.getOrNull(i + 1)?.startMs ?: return@derivedStateOf false
                gapEnd - gapStart > 3200L && t > gapStart + 1000f && t < gapEnd - 1000f
            }
        }
    }

    // ★ Mochy blur: 3 stages — null (0px), 5px, 12px — DECAL tile mode
    val blurEffects = remember(style.maxBlur, density) {
        val maxPx = with(density) { style.maxBlur.toPx() }
        val radii = floatArrayOf(0f, 3f, 8f)
        Array<RenderEffect?>(3) { i ->
            if (i == 0 || maxPx <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) null
            else BlurEffect(radii[i], radii[i], TileMode.Decal)
        }
    }

    // pause auto-follow while the user drags; resume 3.5s after they let go (Mochy)
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var followPaused by remember { mutableStateOf(false) }
    LaunchedEffect(dragged) {
        if (dragged) followPaused = true
        else if (followPaused) { delay(3500); followPaused = false }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportPx = with(density) { maxHeight.toPx() }
        // ★ Mochy focus: max(viewportH * 0.055, 72dp) from the top
        val focusPx = maxOf(viewportPx * style.focusFraction, with(density) { style.minFocusDp.toPx() })

        // ★ Mochy auto-scroll: 950ms tween, CubicBezier(0.42, 0, 0.16, 1) — eases in
        //    Keyed on activeIndex + followPaused only (NOT lyrics — upgrading lyrics
        //    would cancel the scroll mid-animation and cause a snap).
        LaunchedEffect(activeIndex, followPaused) {
            if (!synced || followPaused || activeIndex < 0) return@LaunchedEffect
            var info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
            if (info == null) {
                // ★ Use animateScrollToItem (animated) instead of scrollToItem (instant snap)
                listState.animateScrollToItem(activeIndex, 0)
                info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
            }
            if (info != null) {
                val target = info.offset - focusPx
                if (abs(target) > 0.5f) {
                    listState.animateScrollBy(target, tween(950, easing = RecenterEasing))
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = with(density) { focusPx.toDp() },
                bottom = with(density) { (viewportPx * 0.55f).toDp() },  // Mochy: 55% bottom padding
                start = style.horizontalPadding,
                end = style.horizontalPadding,
            ),
        ) {
            itemsIndexed(lyrics.lines, key = { i, _ -> i }) { i, line ->
                val active = i == activeIndex || (line.isBackground && i - 1 == activeIndex)
                LyricLineItem(
                    line = line,
                    synced = synced,
                    isActive = active,
                    distance = if (activeIndex < 0) i + 1 else abs(i - activeIndex),
                    clock = clock,
                    measurer = measurer,
                    style = style,
                    blurEffects = blurEffects,
                    onClick = if (synced) ({ onSeek(line.startMs) }) else null,
                )
            }
        }

        // ★ Instrumental / intro: typing dots while no lyric line is being sung.
        AnimatedVisibility(
            visible = waitingForLyrics,
            enter = fadeIn(tween(280)),
            exit = fadeOut(tween(280)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            TypingDots(color = style.unsungColor)
        }

        // ★ Immersive fade: top 10% and bottom 38% dissolve into the page background.
        //   Painted as a plain overlay in the screen's own colour instead of a DstIn
        //   pass, so the list no longer renders into an offscreen layer every frame —
        //   that layer (plus the per-line blur) was the cost that made auto-scroll
        //   stutter. Visually identical over the solid background.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to style.fadeColor,
                            0.10f to Color.Transparent,
                            0.62f to Color.Transparent,
                            1f to style.fadeColor,
                        )
                    )
                }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Typing dots — the "instrumental / waiting for lyrics" indicator.
//  Three dots, 1s loop, 160ms stagger: opacity 0.5 → 1 and a half-dot lift.
//  The loop phase is read inside graphicsLayer, so it redraws without recomposing.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TypingDots(
    color: Color,
    modifier: Modifier = Modifier,
    dotSize: Dp = 9.dp,
    gap: Dp = 7.dp,
) {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "typingPhase",
    )
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        val p = (phase + i * 0.16f) % 1f
                        // 0 → 1 → 0 over one loop, so the dot rises to half its size and back
                        val lift = if (p <= 0.5f) p * 2f else (1f - p) * 2f
                        translationY = -lift * size.height * 0.5f
                        alpha = 0.5f + 0.5f * lift
                    }
                    .background(color, CircleShape)
            )
        }
    }
}
