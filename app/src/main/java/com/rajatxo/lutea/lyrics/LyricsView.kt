package com.rajatxo.lutea.lyrics

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
import kotlin.math.exp
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
//    • glow: very subtle — 3dp radius, only on the sung word, trailing the sweep
//    • focus: active line at 5.5% from top (min 72dp)
//    • auto-scroll: 950ms tween, CubicBezier(0.42, 0, 0.16, 1) — eases in, no kick
//    • cascade: up to 9 rows below trail at 55ms × distance spring time constant
//    • immersive fade: top 10% + bottom 38% dissolve via DstIn
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
    val glowRadius: Dp = 8.dp,         // glow radius for held notes (wider than before)
    val liftDistance: Dp = 3.dp,       // Mochy WordWaitDrop = 3dp
    val heldZoom: Float = 0.08f,       // Mochy HeldZoom — word swells 8% on held notes
    val heldGlowAlpha: Float = 0.5f,   // glow intensity on held notes
    val maxBlur: Dp = 12.dp,
    val focusFraction: Float = 0.055f,
    val minFocusDp: Dp = 72.dp,
    val lineGap: Dp = 12.dp,
    val horizontalPadding: Dp = 36.dp,
)

private fun smooth(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
private fun easeOutCubic(x: Float): Float { val i = 1f - x; return 1f - i * i * i }

private val RecenterEasing = CubicBezierEasing(0.42f, 0f, 0.16f, 1f)  // Mochy Style 1

private const val STAGGER_LAG_PER_ROW_MS = 55f   // Mochy StaggerLagPerRowMs
private const val STAGGER_MAX_ROWS = 9            // Mochy StaggerMaxRows

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

    fun draw(scope: DrawScope, t: Long, fillAlpha: Float, p: DrawParams) {
        val l = layout ?: return
        with(scope) {
            if (!p.synced) { drawText(l, color = p.sung.copy(alpha = 0.92f)); return }
            if (t < 0L || fillAlpha <= 0.002f) { drawText(l, color = p.unsung); return }

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
                if (wp > 0f || (fallTime > 0f && fallTime < 420f)) lifted.add(gi)
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
                val wl = g.layout ?: p.measurer.measure(text = g.text, style = textStyle, overflow = TextOverflow.Visible).also { g.layout = it }
                val dur = max(g.endMs - g.startMs, 1L).toFloat()
                val wp = ((t - g.startMs).toFloat() / dur).coerceIn(0f, 1f)

                // Sweep-based rise: fully risen at 85% of the word, falls over 420ms after
                val riseProgress = (wp / 0.85f).coerceIn(0f, 1f)
                val fallProgress = if (t > g.endMs) smooth(((t - g.endMs).toFloat() / 420f).coerceIn(0f, 1f)) else 0f
                val rise = smooth(riseProgress) * (1f - fallProgress)
                val lift = p.liftPx * rise * (dur / 450f).coerceIn(0.55f, 1f)
                val origin = Offset(g.left, g.top - lift)

                // Held-note detection: secPerChar > 0.1s = some hold, > 0.34s = full hold
                val secPerChar = (dur / 1000f) / g.letterCount
                val hold = smooth(((secPerChar - 0.1f) / (0.34f - 0.1f)).coerceIn(0f, 1f))

                // Glow: attack 35%, release 30% -- grows on held notes, lingers after
                val glowAttack = smooth((wp / 0.35f).coerceIn(0f, 1f))
                val glowRelease = smooth(((1f - wp) / 0.3f).coerceIn(0f, 1f))
                val glow = hold * glowAttack * glowRelease * p.heldGlowAlpha

                // Swell: attack 20%, release 12% -- word grows on held notes
                val swellAttack = smooth((wp / 0.2f).coerceIn(0f, 1f))
                val swellRelease = smooth(((1f - wp) / 0.12f).coerceIn(0f, 1f))
                val swell = hold * swellAttack * swellRelease * p.heldZoom
                val scale = 1f + swell

                // Draw unsung base
                drawText(wl, color = p.unsung, topLeft = origin)

                // Draw sung highlight with glow + swell
                val shadow = Shadow(
                    color = p.glow.copy(alpha = glow),
                    offset = Offset.Zero,
                    blurRadius = p.glowRadiusPx * (0.5f + 1.5f * glow),
                )
                canvas2.save()
                if (scale != 1f) {
                    val baselineY = origin.y + (g.bottom - g.top)
                    val centerX = origin.x + (g.right - g.left) / 2f
                    canvas2.translate(centerX, baselineY)
                    canvas2.scale(scale, scale)
                    canvas2.translate(-centerX, -baselineY)
                }
                val k = g.visualLine
                when (modeOf(k)) {
                    2 -> drawText(wl, color = p.sung, topLeft = origin, alpha = fillAlpha, shadow = shadow)
                    1 -> drawText(wl, brush = brushFor(frontOf(k) - g.left, p), topLeft = origin, alpha = fillAlpha, shadow = shadow)
                }
                canvas2.restore()
            }        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Cascade / stagger — rows below the active line trail the scroll.
//  Each row has a critically-damped spring with time constant = 55ms × distance.
//  Updated every frame via withFrameNanos; read in graphicsLayer (no recomposition).
// ─────────────────────────────────────────────────────────────────────────────

private class StaggerState {
    val offsets = mutableStateMapOf<Int, Float>()
    private val velocities = mutableMapOf<Int, Float>()
    private var anchorRow = -1

    fun setAnchor(row: Int) { anchorRow = row }

    fun holdBack(rows: Iterable<Int>, scrollPx: Float) {
        for (row in rows) {
            if (row > anchorRow && row <= anchorRow + STAGGER_MAX_ROWS) {
                offsets[row] = (offsets[row] ?: 0f) + scrollPx
            }
        }
    }

    fun update(dt: Float) {
        if (offsets.isEmpty()) return
        val keys = offsets.keys.toList()
        for (row in keys) {
            val k = (row - anchorRow).coerceIn(1, STAGGER_MAX_ROWS)
            val omega = 1000f / (STAGGER_LAG_PER_ROW_MS * k)
            val offset = offsets[row] ?: 0f
            val v = velocities[row] ?: 0f
            val e = exp(-omega * dt)
            val tmp = (v + omega * offset) * dt
            val nextOffset = (offset + tmp) * e
            val nextV = (v - omega * tmp) * e
            if (abs(nextOffset) < 0.3f && abs(nextV) < 4f) {
                offsets.remove(row); velocities.remove(row)
            } else {
                offsets[row] = nextOffset; velocities[row] = nextV
            }
        }
    }

    val isMoving get() = offsets.isNotEmpty()
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
    clock: State<Long>,
    measurer: TextMeasurer,
    style: LyricsStyle,
    blurEffects: Array<RenderEffect?>,
    staggerOffset: () -> Float,
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
            // ★ Center by default (Mochy CENTER alignment), End for duets
            textAlign = if (line.alignEnd) TextAlign.End else TextAlign.Center,
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
                softPx = style.fontSize.toPx() * sizeScale * 0.5f,
                heldZoom = style.heldZoom,
                heldGlowAlpha = style.heldGlowAlpha,
                synced = synced, measurer = measurer,
            )
        }
    }

    // ★ Mochy transitions: 600ms tween for alpha/scale, 350ms for blur
    val focusV by animateFloatAsState(
        targetValue = if (isActive && synced) 1f else 0f,
        animationSpec = tween(600),
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
                // ★ Center transform origin for center-aligned text (Mochy CENTER)
                transformOrigin = TransformOrigin(if (line.alignEnd) 1f else 0.5f, 0.5f)
                alpha = alphaV
                val step = blurV.roundToInt().coerceIn(0, blurEffects.size - 1)
                renderEffect = blurEffects.getOrNull(step)
                // ★ Cascade: rows below the active line get a translationY offset
                translationY = staggerOffset()
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
                val tNow = if (synced && isActive && focusV > 0.002f) clock.value else -1L
                geo.draw(this, tNow, focusV, params)
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
    val clock = remember { mutableLongStateOf(0L) }
    val latestPosition by rememberUpdatedState(positionMs)
    LaunchedEffect(isPlaying, offsetMs) {
        var lastNanos = 0L
        var lastSyncWallMs = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (isPlaying && lastNanos > 0) {
                    val dtMs = (now - lastNanos) / 1_000_000
                    clock.longValue += dtMs
                }
                lastNanos = now
            }
            // Sync with actual playback position every ~500ms (corrects drift from seeking, pauses, etc.)
            val wallMs = System.currentTimeMillis()
            if (wallMs - lastSyncWallMs > 500) {
                lastSyncWallMs = wallMs
                val actual = latestPosition() + offsetMs
                if (abs(actual - clock.longValue) > 200) {
                    clock.longValue = actual
                }
            }
        }
    }

    val activeIndex by remember(lyrics) {
        derivedStateOf { if (synced) activeLine(lyrics.lines, clock.longValue) else -1 }
    }

    // ★ Mochy blur: 3 stages — null (0px), 5px, 12px — DECAL tile mode
    val blurEffects = remember(style.maxBlur, density) {
        val maxPx = with(density) { style.maxBlur.toPx() }
        val radii = floatArrayOf(0f, 5f, 12f)
        Array<RenderEffect?>(3) { i ->
            if (i == 0 || maxPx <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) null
            else BlurEffect(radii[i], radii[i], TileMode.Decal)
        }
    }

    // ★ Cascade state
    val stagger = remember { StaggerState() }
    var lastFrameNanos by remember { mutableLongStateOf(0L) }

    // Update stagger springs every frame
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { now ->
                if (lastFrameNanos > 0 && stagger.isMoving) {
                    val dt = ((now - lastFrameNanos) / 1e9f).coerceIn(0f, 0.064f)
                    stagger.update(dt)
                }
                lastFrameNanos = now
            }
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
                    stagger.setAnchor(activeIndex)
                    val visibleRows = listState.layoutInfo.visibleItemsInfo.map { it.index }
                    stagger.holdBack(visibleRows, -target)
                    listState.animateScrollBy(target, tween(950, easing = RecenterEasing))
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // ★ Mochy immersive fade: top 10% + bottom 38% dissolve via DstIn
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.10f to Color.Black,
                            0.62f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
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
                    staggerOffset = { stagger.offsets[i] ?: 0f },
                    onClick = if (synced) ({ onSeek(line.startMs) }) else null,
                )
            }
        }
    }
}
