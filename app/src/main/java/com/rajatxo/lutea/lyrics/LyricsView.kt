package com.rajatxo.lutea.lyrics

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
//  Lutea lyrics view — line-by-line focus + word-by-word fill, with a soft glow and a
//  little "pull" (lift) on the word being sung.  Original implementation:
//    • one text layout per line, measured once (text never re-composes while playing)
//    • the fill is a soft-edged gradient swept across the word, redrawn per frame
//    • sung word lifts + glows (text shadow, no shaders) and settles back after
//    • the line itself is pulled to the focus point by a spring
//    • scale/blur/alpha are graphicsLayer transforms → no layout shift
// ─────────────────────────────────────────────────────────────────────────────

@Immutable
data class LyricsStyle(
    val fontSize: TextUnit = 30.sp,
    val lineHeight: TextUnit = 38.sp,
    val fontFamily: FontFamily = FontFamily.Default,     // pass your CalSansFamily here
    val fontWeight: FontWeight = FontWeight.Bold,
    val sungColor: Color = Color.White,
    val unsungColor: Color = Color.White.copy(alpha = 0.36f),
    val glowColor: Color = Color.White,
    val glowRadius: Dp = 12.dp,
    val liftDistance: Dp = 3.dp,       // how far the sung word rises
    val maxBlur: Dp = 3.5.dp,          // blur on far lines (API 31+); 0.dp = off
    val focusFraction: Float = 0.30f,  // where the active line sits (0 = top, 1 = bottom)
    val lineGap: Dp = 18.dp,
    val horizontalPadding: Dp = 24.dp,
)

private fun easeOutCubic(x: Float): Float { val i = 1f - x; return 1f - i * i * i }
private fun smooth(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

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
    val synced: Boolean,
    val measurer: TextMeasurer,
)

private class WordGroup(
    val firstToken: Int,
    val lastToken: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
) {
    var left = 0f
    var top = 0f
    var right = 0f
    var bottom = 0f
    var visualLine = 0
    var layout: TextLayoutResult? = null
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

/** Measured text + per-token / per-word geometry for one lyric line. */
private class LineGeometry(val line: LyricLine, val textStyle: TextStyle) {
    var layout: TextLayoutResult? = null
    private var width = -1
    private var rtl = false
    private val n = line.tokens.size
    private val tLeft = FloatArray(n)
    private val tRight = FloatArray(n)
    private val tLine = IntArray(n)
    private val groups: List<WordGroup> = buildGroups(line)

    fun ensureLayout(measurer: TextMeasurer, maxWidth: Int) {
        if (layout != null && width == maxWidth) return
        width = maxWidth
        if (line.text.isEmpty()) return
        val l = measurer.measure(
            text = line.text,
            style = textStyle,
            overflow = TextOverflow.Visible,
            constraints = Constraints(maxWidth = maxWidth),
        )
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
        }
        for (g in groups) {
            var lo = Float.MAX_VALUE
            var hi = -Float.MAX_VALUE
            for (i in g.firstToken..g.lastToken) { lo = min(lo, tLeft[i]); hi = max(hi, tRight[i]) }
            g.left = lo
            g.right = hi
            g.visualLine = tLine[g.firstToken]
            g.top = l.getLineTop(g.visualLine)
            g.bottom = l.getLineBottom(g.visualLine)
            g.layout = null
        }
    }

    private fun brushFor(front: Float, p: DrawParams): Brush {
        val clear = p.sung.copy(alpha = 0f)
        return if (!rtl) {
            Brush.horizontalGradient(0f to p.sung, 1f to clear, startX = front - p.softPx * 0.15f, endX = front + p.softPx * 0.85f)
        } else {
            Brush.horizontalGradient(0f to clear, 1f to p.sung, startX = front - p.softPx * 0.85f, endX = front + p.softPx * 0.15f)
        }
    }

    /** t < 0 means "not playing this line": draw it dim. fillAlpha fades the highlight in/out. */
    fun draw(scope: DrawScope, t: Long, fillAlpha: Float, p: DrawParams) {
        val l = layout ?: return
        with(scope) {
            if (!p.synced) {
                drawText(l, color = p.sung.copy(alpha = 0.92f))
                return
            }
            if (t < 0L || fillAlpha <= 0.002f) {
                drawText(l, color = p.unsung)
                return
            }

            // 1 ── how far has the fill travelled on each visual line?
            val lc = l.lineCount
            val hasTok = BooleanArray(lc)
            val anyFilled = BooleanArray(lc)
            val allFilled = BooleanArray(lc) { true }
            val partialFront = FloatArray(lc) { Float.NaN }
            val lastEdge = FloatArray(lc) { Float.NaN }
            for (i in 0 until n) {
                val k = tLine[i]
                hasTok[k] = true
                val tk = line.tokens[i]
                val dur = max(tk.endMs - tk.startMs, 1L)
                val pr = ((t - tk.startMs).toFloat() / dur).coerceIn(0f, 1f)
                if (pr >= 1f) {
                    anyFilled[k] = true
                    lastEdge[k] = if (rtl) tLeft[i] else tRight[i]
                } else if (pr <= 0f) {
                    allFilled[k] = false
                } else {
                    anyFilled[k] = true
                    allFilled[k] = false
                    val w = tRight[i] - tLeft[i]
                    partialFront[k] = if (rtl) tRight[i] - pr * w else tLeft[i] + pr * w
                }
            }
            // mode: 0 nothing, 1 partial, 2 full
            fun modeOf(k: Int): Int = if (!hasTok[k] || !anyFilled[k]) 0 else if (allFilled[k]) 2 else 1
            fun frontOf(k: Int): Float = if (!partialFront[k].isNaN()) partialFront[k] else lastEdge[k]

            // 2 ── which words are currently pulled up / glowing?
            val env = FloatArray(groups.size)
            val lifted = ArrayList<Int>()
            for (gi in groups.indices) {
                val e = wordEnvelope(groups[gi].startMs, groups[gi].endMs, t)
                env[gi] = e
                if (e > 0.004f) lifted.add(gi)
            }

            // 3 ── everything except the lifted words: dim base + highlight sweep
            val canvas = drawContext.canvas
            canvas.save()
            for (gi in lifted) {
                val g = groups[gi]
                canvas.clipRect(g.left - 2f, g.top - 1f, g.right + 2f, g.bottom + 1f, ClipOp.Difference)
            }
            drawText(l, color = p.unsung)
            for (k in 0 until lc) {
                val m = modeOf(k)
                if (m == 0) continue
                canvas.save()
                canvas.clipRect(0f, l.getLineTop(k), size.width, l.getLineBottom(k))
                if (m == 2) drawText(l, color = p.sung, alpha = fillAlpha)
                else drawText(l, brush = brushFor(frontOf(k), p), alpha = fillAlpha)
                canvas.restore()
            }
            canvas.restore()

            // 4 ── the lifted words, drawn on their own so they can rise and glow
            for (gi in lifted) {
                val g = groups[gi]
                val e = env[gi]
                val wl = g.layout ?: p.measurer.measure(
                    text = g.text,
                    style = textStyle,
                    overflow = TextOverflow.Visible,
                ).also { g.layout = it }
                val dur = max(g.endMs - g.startMs, 1L).toFloat()
                val lift = p.liftPx * e * (dur / 450f).coerceIn(0.55f, 1f)
                val origin = Offset(g.left, g.top - lift)
                drawText(wl, color = p.unsung, topLeft = origin)
                val gp = ((t - g.startMs).toFloat() / dur).coerceIn(0f, 1f)
                val glowAlpha = e * smooth(gp / 0.3f) * 0.8f
                val shadow = Shadow(
                    color = p.glow.copy(alpha = glowAlpha),
                    offset = Offset.Zero,
                    blurRadius = p.glowRadiusPx * (0.5f + 0.5f * e),
                )
                val k = g.visualLine
                when (modeOf(k)) {
                    2 -> drawText(wl, color = p.sung, topLeft = origin, alpha = fillAlpha, shadow = shadow)
                    1 -> drawText(wl, brush = brushFor(frontOf(k) - g.left, p), topLeft = origin, alpha = fillAlpha, shadow = shadow)
                }
            }
        }
    }
}

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
            textAlign = if (line.alignEnd) TextAlign.End else TextAlign.Start,
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
                synced = synced,
                measurer = measurer,
            )
        }
    }

    val focusV by animateFloatAsState(
        targetValue = if (isActive && synced) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
        label = "lyricFocus",
    )
    val alphaTarget = when {
        !synced -> 0.95f
        isActive -> 1f
        distance == 1 -> 0.92f
        distance == 2 -> 0.82f
        else -> 0.70f
    }
    val alphaV by animateFloatAsState(alphaTarget, tween(320), label = "lyricAlpha")
    val blurV by animateFloatAsState(
        targetValue = if (synced && !isActive) min(distance, 4) / 4f else 0f,
        animationSpec = tween(320),
        label = "lyricBlur",
    )
    val interaction = remember { MutableInteractionSource() }

    Spacer(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .padding(vertical = style.lineGap / 2)
            .graphicsLayer {
                val s = if (synced) 0.93f + 0.07f * focusV else 1f
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(if (line.alignEnd) 1f else 0f, 0.5f)
                alpha = alphaV
                val step = (blurV * (blurEffects.size - 1)).roundToInt()
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
                val tNow = if (synced && focusV > 0.002f) clock.value else -1L
                geo.draw(this, tNow, focusV, params)
            }
    )
}

/**
 * @param positionMs  current playback position in ms (read every frame, e.g. { player.currentPosition })
 * @param onSeek      called with the tapped line's start time
 * @param offsetMs    user lyrics delay (+ shows lyrics later, − earlier)
 */
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
            BasicText(
                "Instrumental",
                style = TextStyle(
                    color = style.unsungColor.copy(alpha = 0.7f),
                    fontSize = 20.sp,
                    fontFamily = style.fontFamily,
                ),
            )
        }
        return
    }

    val synced = lyrics.sync != SyncType.NONE
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val listState = rememberLazyListState()

    // one clock for the whole view; only lines that read it (the active one) redraw per frame
    val clock = remember { mutableLongStateOf(0L) }
    val latestPosition by rememberUpdatedState(positionMs)
    LaunchedEffect(isPlaying, offsetMs, lyrics) {
        while (isActive) {
            clock.longValue = latestPosition() + offsetMs
            if (isPlaying) withFrameNanos { } else delay(120)
        }
    }

    val activeIndex by remember(lyrics) {
        derivedStateOf { if (synced) activeLine(lyrics.lines, clock.longValue) else -1 }
    }

    val blurEffects = remember(style.maxBlur, density) {
        val maxPx = with(density) { style.maxBlur.toPx() }
        Array<RenderEffect?>(5) { i ->
            if (i == 0 || maxPx <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) null
            else BlurEffect(maxPx * i / 4f, maxPx * i / 4f, TileMode.Decal)
        }
    }

    // pause auto-follow while the user drags; resume 2.5 s after they let go
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var followPaused by remember { mutableStateOf(false) }
    LaunchedEffect(dragged) {
        if (dragged) followPaused = true
        else if (followPaused) {
            delay(2500)
            followPaused = false
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportPx = with(density) { maxHeight.toPx() }
        val focusPx = viewportPx * style.focusFraction

        // the "pull": a slightly under-damped spring brings the active line to the focus point
        LaunchedEffect(activeIndex, followPaused, lyrics) {
            if (!synced || followPaused || activeIndex < 0) return@LaunchedEffect
            var info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
            if (info == null) {
                listState.scrollToItem(activeIndex)
                info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
            }
            if (info != null) {
                listState.animateScrollBy(info.offset - focusPx, spring(dampingRatio = 0.8f, stiffness = 110f))
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.07f to Color.Black,
                            0.90f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentPadding = PaddingValues(
                top = with(density) { focusPx.toDp() },
                bottom = with(density) { (viewportPx * (1f - style.focusFraction)).toDp() },
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
                    distance = if (activeIndex < 0) i + 1 else kotlin.math.abs(i - activeIndex),
                    clock = clock,
                    measurer = measurer,
                    style = style,
                    blurEffects = blurEffects,
                    onClick = if (synced) ({ onSeek(line.startMs) }) else null,
                )
            }
        }
    }
}
