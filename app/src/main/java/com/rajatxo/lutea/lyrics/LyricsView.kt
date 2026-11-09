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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// ─────────────────────────────────────────────────────────────────────────────
//  LyricsView — per-letter karaoke lyrics (original implementation).
// ─────────────────────────────────────────────────────────────────────────────

data class LyricsStyle(
    val fontSize: TextUnit = 32.sp,
    val lineHeight: TextUnit = 40.sp,
    val fontFamily: FontFamily = FontFamily.Default,
    val fontWeight: FontWeight = FontWeight.Bold,
    val inactiveFontWeight: FontWeight? = null,
    val sungColor: Color = Color.White,
    val unsungColor: Color = Color.White.copy(alpha = 0.58f),
    val glowColor: Color = Color.White,
    val glowRadius: Dp = 8.dp,
    val liftDistance: Dp = 3.dp,
    val heldZoom: Float = 0.075f,
    val heldGlowAlpha: Float = 0.5f,
    val maxBlur: Dp = 12.dp,
    val focusFraction: Float = 0.055f,
    val minFocusDp: Dp = 72.dp,
    val lineGap: Dp = 12.dp,
    val horizontalPadding: Dp = 36.dp,
)

// ── constants ──
private const val RiseFollowSec = 0.08f
private const val SwellFollowSec = 0.2f
private const val LetterRiseShare = 0.55f
private val HeldLift = 2.5.dp
private const val CascadeRows = 9
private const val CascadeSecPerRow = 0.055f
private const val ScrollMs = 950
private const val UserPauseMs = 3500L
private val ScrollEase = CubicBezierEasing(0.42f, 0f, 0.16f, 1f)

private fun smoothstep(x: Float): Float {
    val c = if (x < 0f) 0f else if (x > 1f) 1f else x
    return c * c * (3f - 2f * c)
}

/** Exact critically-damped spring step toward [target]. Returns true while still moving. */
private fun dampedStep(
    value: FloatArray, vel: FloatArray, i: Int,
    target: Float, followSec: Float, dt: Float,
): Boolean {
    val omega = 1f / followSec
    val y = value[i] - target
    val v = vel[i]
    if (abs(y) < 0.0005f && abs(v) < 0.005f) {
        value[i] = target; vel[i] = 0f
        return false
    }
    val e = exp(-omega * dt)
    val tmp = (v + omega * y) * dt
    value[i] = target + (y + tmp) * e
    vel[i] = (v - omega * tmp) * e
    return true
}

// ─────────────────────────────── line model ───────────────────────────────

private class WordInfo(
    val cs: Int, val ce: Int,
    val startMs: Float, val endMs: Float,
    val hold: Float,
    val firstCh: Int, val lastCh: Int,
)

/** Caches a clip path covering everything the sweep head has passed. */
private class SweepClip(private val inflate: Float) {
    val path = Path()
    private var lastHead = Float.NaN
    private var lastLayout: TextLayoutResult? = null

    fun get(lay: TextLayoutResult, head: Float): Path {
        if (head == lastHead && lay === lastLayout) return path
        lastHead = head; lastLayout = lay
        path.rewind()
        for (l in 0 until lay.lineCount) {
            val ls = lay.getLineStart(l)
            val le = lay.getLineEnd(l)
            if (head <= ls) break
            val left = lay.getLineLeft(l) - inflate
            val top = lay.getLineTop(l) - inflate
            val bottom = lay.getLineBottom(l) + inflate
            val right = if (head >= le) {
                lay.getLineRight(l)
            } else {
                val c = floor(head).toInt().coerceIn(ls, max(ls, le - 1))
                val frac = (head - c).coerceIn(0f, 1f)
                val b = lay.getBoundingBox(c)
                b.left + b.width * frac
            }
            if (right > left) path.addRect(Rect(left, top, right, bottom))
        }
        return path
    }
}

private class LineModel(
    val text: String,
    tokens: List<LyricToken>,
    val heldZoom: Float,
    val glowMax: Float,
    val measurer: TextMeasurer,
    val glyphStyle: TextStyle,
    glowInflate: Float,
) {
    val n = text.length
    private val tokS: FloatArray
    private val tokE: FloatArray
    private val tokA: FloatArray   // first visible char
    private val tokB: FloatArray   // one past the last visible char
    val words: List<WordInfo>
    val wordOf = IntArray(n) { -1 }
    val letters: IntArray

    val rise = FloatArray(n)
    val riseV = FloatArray(n)
    val swell = FloatArray(n)
    val swellV = FloatArray(n)
    val glow = FloatArray(n)
    val meanSwell: FloatArray

    private val swT: FloatArray
    private val glT: FloatArray
    private val sumSw: FloatArray
    private val cntSw: IntArray

    val dxs: FloatArray
    val dys: FloatArray
    val scs: FloatArray

    var head = -0.5f
    var layout: TextLayoutResult? = null
    val litClip = SweepClip(0f)
    val glowClip = SweepClip(glowInflate)
    private val glyphs = HashMap<Char, TextLayoutResult>()

    init {
        require(tokens.isNotEmpty() && n > 0)
        val k = tokens.size
        tokS = FloatArray(k); tokE = FloatArray(k); tokA = FloatArray(k); tokB = FloatArray(k)
        var off = 0
        for (i in 0 until k) {
            val t = tokens[i]
            val a = off + (t.text.length - t.text.trimStart().length)
            val b = off + t.text.trimEnd().length
            tokS[i] = t.startMs.toFloat(); tokE[i] = max(t.endMs, t.startMs).toFloat()
            tokA[i] = a.toFloat(); tokB[i] = max(a, b).toFloat()
            off += t.text.length
        }
        val ws = ArrayList<WordInfo>()
        var i = 0
        while (i < k) {
            var j = i
            while (j + 1 < k && tokens[j + 1].wordIndex == tokens[i].wordIndex) j++
            val cs = tokA[i].toInt(); val ce = tokB[j].toInt()
            if (ce > cs) {
                var first = -1; var last = -1; var count = 0
                for (c in cs until min(ce, n)) if (!text[c].isWhitespace()) {
                    if (first < 0) first = c
                    last = c; count++
                }
                if (count > 0) {
                    val sMs = tokS[i]; val eMs = tokE[j]
                    val secPerChar = (eMs - sMs) / 1000f / count
                    val hold = smoothstep((secPerChar - 0.1f) / (0.34f - 0.1f))
                    ws.add(WordInfo(cs, min(ce, n), sMs, eMs, hold, first, last))
                }
            }
            i = j + 1
        }
        require(ws.isNotEmpty())
        words = ws
        for (w in 0 until ws.size) {
            for (c in ws[w].cs until ws[w].ce) if (!text[c].isWhitespace()) wordOf[c] = w
        }
        var lastW = 0
        for (c in 0 until n) {
            if (text[c].isWhitespace()) continue
            if (wordOf[c] < 0) wordOf[c] = lastW else lastW = wordOf[c]
        }
        letters = (0 until n).filter { !text[it].isWhitespace() }.toIntArray()
        meanSwell = FloatArray(ws.size)
        swT = FloatArray(ws.size); glT = FloatArray(ws.size)
        sumSw = FloatArray(ws.size); cntSw = IntArray(ws.size)
        dxs = FloatArray(letters.size); dys = FloatArray(letters.size); scs = FloatArray(letters.size)
    }

    /** Fractional character offset the sweep has reached at time [t]. */
    fun headAt(t: Float): Float {
        val k = tokS.size
        if (t < tokS[0]) return tokA[0] - 0.5f
        for (i in 0 until k) {
            if (t < tokS[i]) {
                val gap = tokS[i] - tokE[i - 1]
                val win = min(gap, 250f)
                if (win <= 0f) return tokA[i]
                val u = ((t - (tokS[i] - win)) / win).coerceIn(0f, 1f)
                val s = smoothstep(u)
                return tokB[i - 1] + (tokA[i] - tokB[i - 1]) * s
            }
            if (t <= tokE[i]) {
                val d = tokE[i] - tokS[i]
                val f = if (d <= 0f) 1f else (t - tokS[i]) / d
                return tokA[i] + (tokB[i] - tokA[i]) * f
            }
        }
        return tokB[k - 1].coerceAtLeast(n.toFloat())
    }

    /** Advances all springs. With [snap] the springs jump straight to their targets. */
    fun update(t: Float, dt: Float, snap: Boolean): Boolean {
        head = headAt(t)
        var moving = false
        for (wi in words.indices) {
            val w = words[wi]
            val span = w.endMs - w.startMs
            val wp = if (span <= 0f) (if (t >= w.startMs) 1f else 0f)
            else ((t - w.startMs) / span).coerceIn(0f, 1f)
            swT[wi] = w.hold * smoothstep(wp / 0.2f) * smoothstep((1f - wp) / 0.12f)
            glT[wi] = w.hold * smoothstep(wp / 0.35f) * smoothstep((1f - wp) / 0.3f) * glowMax
            sumSw[wi] = 0f; cntSw[wi] = 0
        }
        for (i in letters) {
            val wi = wordOf[i]
            val w = words[wi]
            val len = max(1, w.ce - w.cs).toFloat()
            val through = (head - w.cs) / len
            val wordRise = smoothstep(through / 0.85f)
            val letterRise = if (head >= w.ce) 1f else smoothstep((head - i + 0.5f) / 2.5f)
            val rT = wordRise + (letterRise - wordRise) * LetterRiseShare
            if (snap) {
                rise[i] = rT; riseV[i] = 0f; swell[i] = swT[wi]; swellV[i] = 0f
            } else {
                val a = dampedStep(rise, riseV, i, rT, RiseFollowSec, dt)
                val b = dampedStep(swell, swellV, i, swT[wi], SwellFollowSec, dt)
                if (a || b) moving = true
            }
            glow[i] = glT[wi]
            sumSw[wi] += swell[i]; cntSw[wi]++
        }
        for (wi in words.indices) meanSwell[wi] = if (cntSw[wi] > 0) sumSw[wi] / cntSw[wi] else 0f
        return moving
    }

    fun glyph(ch: Char): TextLayoutResult =
        glyphs.getOrPut(ch) { measurer.measure(ch.toString(), glyphStyle) }
}

// ─────────────────────────────── per-letter drawing ───────────────────────────────

private fun ContentDrawScope.drawKaraoke(
    m: LineModel,
    dropPx: Float,
    liftPx: Float,
    glowRadiusPx: Float,
    sungColor: Color,
    glowColor: Color,
) {
    val lay = m.layout
    if (lay == null) { drawContent(); return }
    val letters = m.letters
    val count = letters.size
    if (count == 0) { drawContent(); return }

    // 1. per-letter transforms
    for (li in 0 until count) {
        val i = letters[li]
        val wi = m.wordOf[i]
        val sw = m.swell[i]
        m.dys[li] = dropPx * (1f - m.rise[i]) - liftPx * sw
        m.scs[li] = 1f + m.heldZoom * sw
        val ms = m.meanSwell[wi]
        if (ms > 0.001f) {
            val w = m.words[wi]
            val wcx = (lay.getBoundingBox(w.firstCh).left + lay.getBoundingBox(w.lastCh).right) / 2f
            m.dxs[li] = (lay.getBoundingBox(i).center.x - wcx) * m.heldZoom * ms
        } else {
            m.dxs[li] = 0f
        }
    }

    // 2. uniform fast path: every letter shares one transform → draw once
    val dx0 = m.dxs[0]; val dy0 = m.dys[0]; val s0 = m.scs[0]
    var uniform = true
    for (li in 1 until count) {
        if (abs(m.dxs[li] - dx0) > 0.01f || abs(m.dys[li] - dy0) > 0.05f || abs(m.scs[li] - s0) > 0.0005f) {
            uniform = false; break
        }
    }
    if (uniform && abs(s0 - 1f) < 0.0005f) {
        withTransform({ translate(dx0, dy0) }) { this@drawKaraoke.drawContent() }
        return
    }

    // 3. per-letter slots, merging neighbours that share a plain vertical offset
    var idx = 0
    while (idx < count) {
        val i = letters[idx]
        val line = lay.getLineForOffset(i)
        val box = lay.getBoundingBox(i)
        var left = box.left
        var right = box.right
        val dx = m.dxs[idx]; val dy = m.dys[idx]; val s = m.scs[idx]
        val plain = abs(dx) < 0.01f && abs(s - 1f) < 0.0005f
        var end = idx
        if (plain) {
            while (end + 1 < count) {
                val nj = letters[end + 1]
                if (lay.getLineForOffset(nj) != line) break
                if (abs(m.dxs[end + 1]) >= 0.01f || abs(m.scs[end + 1] - 1f) >= 0.0005f) break
                if (abs(m.dys[end + 1] - dy) > 0.1f) break
                val nb = lay.getBoundingBox(nj)
                left = min(left, nb.left); right = max(right, nb.right)
                end++
            }
        }
        val top = lay.getLineTop(line)
        val bottom = lay.getLineBottom(line)
        if (plain) {
            withTransform({
                translate(0f, dy)
                clipRect(left, top, right, bottom)
            }) { this@drawKaraoke.drawContent() }
        } else {
            val pivot = Offset(box.center.x, lay.getLineBaseline(line))
            withTransform({
                translate(dx, dy)
                scale(s, s, pivot = pivot)
                clipRect(left, top, right, bottom)
            }) { this@drawKaraoke.drawContent() }
        }
        idx = end + 1
    }

    // 4. glow on held notes (halo drawn from the glyph itself, trails behind the head)
    var glowPath: Path? = null
    for (li in 0 until count) {
        val i = letters[li]
        val a = m.glow[i]
        if (a < 0.02f) continue
        if (glowPath == null) glowPath = m.glowClip.get(lay, m.head)
        val gl = m.glyph(m.text[i])
        val box = lay.getBoundingBox(i)
        val line = lay.getLineForOffset(i)
        val baseline = lay.getLineBaseline(line)
        val pivot = Offset(box.center.x, baseline)
        val blur = glowRadiusPx * (0.5f + 1.5f * a)
        val clip = glowPath
        withTransform({
            translate(m.dxs[li], m.dys[li])
            scale(m.scs[li], m.scs[li], pivot = pivot)
            clipPath(clip)
        }) {
            drawText(
                textLayoutResult = gl,
                color = sungColor,
                topLeft = Offset(box.left, baseline - gl.firstBaseline),
                shadow = Shadow(glowColor.copy(alpha = a.coerceIn(0f, 1f)), Offset.Zero, blur),
            )
        }
    }
}

// ─────────────────────────────── one karaoke line ───────────────────────────────

@Composable
private fun KaraokeLine(
    line: LyricLine,
    style: LyricsStyle,
    weight: FontWeight,
    sizeScale: Float,
    align: TextAlign,
    clock: () -> Float,
) {
    val measurer = rememberTextMeasurer()
    val sungStyle = remember(style, weight, sizeScale, align) {
        TextStyle(
            color = style.sungColor,
            fontSize = style.fontSize * sizeScale,
            lineHeight = style.lineHeight * sizeScale,
            fontFamily = style.fontFamily,
            fontWeight = weight,
            fontFeatureSettings = "liga 0, clig 0",
            textAlign = align,
        )
    }
    val unsungStyle = remember(sungStyle, style) { sungStyle.copy(color = style.unsungColor) }
    val density = LocalDensity.current

    val model = remember(line, sungStyle, style.heldZoom, style.heldGlowAlpha) {
        try {
            LineModel(
                text = line.text,
                tokens = line.tokens,
                heldZoom = style.heldZoom,
                glowMax = style.heldGlowAlpha,
                measurer = measurer,
                glyphStyle = sungStyle.copy(textAlign = TextAlign.Unspecified),
                glowInflate = with(density) { style.glowRadius.toPx() * 2f },
            ).also { it.update(Snapshot.withoutReadObservation { clock() }, 0f, true) }
        } catch (e: Exception) {
            null
        }
    }

    val lineEnd = remember(line) { max(line.endMs, line.tokens.last().endMs).toFloat() }
    val phase by remember(line) {
        derivedStateOf {
            val c = clock()
            when {
                c < line.startMs - 1200f -> 0
                c > lineEnd + 2500f -> 2
                else -> 1
            }
        }
    }
    val frame = remember { mutableIntStateOf(0) }

    LaunchedEffect(model, phase) {
        val m = model ?: return@LaunchedEffect
        val live = phase == 1
        var last = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.064f)
            last = now
            val moving = m.update(clock(), dt, false)
            frame.intValue = frame.intValue + 1
            if (!live && !moving) break
        }
    }

    val m = model
    if (m == null) {
        BasicText(
            text = line.text,
            style = unsungStyle,
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                try {
                    frame.intValue // redraw whenever the springs step
                    drawKaraoke(
                        m = m,
                        dropPx = style.liftDistance.toPx(),
                        liftPx = HeldLift.toPx(),
                        glowRadiusPx = style.glowRadius.toPx(),
                        sungColor = style.sungColor,
                        glowColor = style.glowColor,
                    )
                } catch (e: Exception) {
                    this@drawKaraoke.drawContent()
                }
            },
    ) {
        // dim copy — always visible
        BasicText(
            text = line.text,
            style = unsungStyle,
            onTextLayout = { m.layout = it },
            modifier = Modifier.fillMaxWidth(),
        )
        // lit copy — clipped to everything the sweep has passed
        BasicText(
            text = line.text,
            style = sungStyle,
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    try {
                        val lay = m.layout
                        if (lay == null) {
                            drawContent()
                        } else {
                            val p = m.litClip.get(lay, m.head)
                            clipPath(p) { this@drawWithContent.drawContent() }
                        }
                    } catch (e: Exception) {
                        drawContent()
                    }
                },
        )
    }
}

// ─────────────────────────────── helpers ───────────────────────────────

private fun activeLine(lines: List<LyricLine>, c: Float): Int {
    var j = -1
    for (i in lines.indices) {
        if (lines[i].startMs <= c) j = i else break
    }
    if (j < 0) return -1
    // a background line that overlaps its main line shouldn't steal the scroll anchor
    var a = j
    while (a > 0 && lines[a].isBackground && lines[a - 1].endMs > lines[a].startMs) a--
    return a
}

// ─────────────────────────────── public composable ───────────────────────────────

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
    if (lyrics == null || lyrics.lines.isEmpty()) {
        Box(modifier)
        return
    }
    val lines = lyrics.lines
    val synced = lyrics.sync != SyncType.NONE
    val posNow by rememberUpdatedState(positionMs)
    val offNow by rememberUpdatedState(offsetMs)
    val seekNow by rememberUpdatedState(onSeek)

    // ── smooth clock ──
    val clockState = remember {
        mutableFloatStateOf(Snapshot.withoutReadObservation { (positionMs() + offsetMs).toFloat() })
    }
    val clock: () -> Float = remember { { clockState.floatValue } }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var last = withFrameNanos { it }
            var sinceSync = 0f
            while (isActive) {
                val now = withFrameNanos { it }
                val dtMs = ((now - last) / 1_000_000f).coerceAtMost(100f)
                last = now
                clockState.floatValue += dtMs
                sinceSync += dtMs
                val real = (posNow() + offNow).toFloat()
                val drift = abs(real - clockState.floatValue)
                if (drift > 1500f || sinceSync >= 500f) {
                    sinceSync = 0f
                    if (drift > 200f) clockState.floatValue = real
                }
            }
        } else {
            snapshotFlow { posNow() + offNow }.collect { clockState.floatValue = it.toFloat() }
        }
    }

    val activeIndex by remember(lines, synced) {
        derivedStateOf { if (!synced) -1 else activeLine(lines, clockState.floatValue) }
    }

    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var followPaused by remember { mutableStateOf(false) }
    LaunchedEffect(dragged) {
        if (dragged) {
            followPaused = true
        } else if (followPaused) {
            delay(UserPauseMs)
            followPaused = false
        }
    }

    // ── cascade state ──
    val lags = remember(lines) { FloatArray(lines.size) }
    val lagV = remember(lines) { FloatArray(lines.size) }
    val lagTick = remember { mutableIntStateOf(0) }

    // ── auto-scroll (keyed on activeIndex + followPaused only) ──
    LaunchedEffect(activeIndex, followPaused) {
        if (followPaused || activeIndex < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == activeIndex }
        if (item == null) {
            listState.animateScrollToItem(activeIndex)
        } else {
            val contentStart = info.viewportStartOffset + info.beforeContentPadding
            val delta = (item.offset - contentStart).toFloat()
            if (abs(delta) > 1f) listState.animateScrollBy(delta, tween(ScrollMs, easing = ScrollEase))
        }
    }

    // ── cascade driver: each scrolled pixel is held back on the trailing rows ──
    LaunchedEffect(lines) {
        snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
            fun anyLag(): Boolean {
                for (v in lags) if (v != 0f) return true
                return false
            }
            if (!scrolling && !anyLag()) return@collectLatest
            var last = withFrameNanos { it }
            var anchorIdx = -1
            var anchorOff = 0
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.064f)
                last = now

                val infos = listState.layoutInfo.visibleItemsInfo
                var delta = 0f
                if (anchorIdx >= 0) {
                    val cur = infos.firstOrNull { it.index == anchorIdx }
                    if (cur != null) delta = (anchorOff - cur.offset).toFloat()
                }
                if (infos.isNotEmpty()) {
                    val a = infos[infos.size / 2]
                    anchorIdx = a.index; anchorOff = a.offset
                } else anchorIdx = -1

                val act = activeIndex
                if (abs(delta) > 0.01f && !followPaused && synced) {
                    for (k in 1..CascadeRows) {
                        val row = act + k
                        if (row in lags.indices) lags[row] = (lags[row] + delta).coerceIn(-480f, 480f)
                    }
                }
                var moving = false
                for (row in lags.indices) {
                    if (lags[row] == 0f && lagV[row] == 0f) continue
                    val k = (row - act).coerceIn(1, CascadeRows)
                    dampedStep(lags, lagV, row, 0f, CascadeSecPerRow * k, dt)
                    if (abs(lags[row]) < 0.3f && abs(lagV[row]) < 2f) {
                        lags[row] = 0f; lagV[row] = 0f
                    } else moving = true
                }
                lagTick.intValue = lagTick.intValue + 1
                if (!scrolling && !moving) break
                if (followPaused && !moving) break
            }
        }
    }

    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val vhPx = constraints.maxHeight.toFloat()
        val focusPx = max(vhPx * style.focusFraction, with(density) { style.minFocusDp.toPx() })
        val topPad = with(density) { focusPx.toDp() }
        val bottomPad = with(density) { (vhPx * 0.55f).toDp() }
        val maxBlurPx = with(density) { style.maxBlur.toPx() }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
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
        ) {
            itemsIndexed(lines, key = { i, _ -> i }) { index, line ->
                val active = activeIndex
                val paused = followPaused
                LyricRow(
                    index = index,
                    line = line,
                    active = active,
                    synced = synced,
                    paused = paused,
                    style = style,
                    maxBlurPx = maxBlurPx,
                    clock = clock,
                    lags = lags,
                    lagTick = lagTick,
                    onClick = { seekNow((line.startMs - offNow).coerceAtLeast(0L)) },
                )
            }
        }
    }
}

// ─────────────────────────────── one row ───────────────────────────────

@Composable
private fun LyricRow(
    index: Int,
    line: LyricLine,
    active: Int,
    synced: Boolean,
    paused: Boolean,
    style: LyricsStyle,
    maxBlurPx: Float,
    clock: () -> Float,
    lags: FloatArray,
    lagTick: androidx.compose.runtime.MutableIntState,
    onClick: () -> Unit,
) {
    val lineEnd = remember(line) { max(line.endMs, line.tokens.last().endMs).toFloat() }
    val sounding by remember(line, synced) {
        derivedStateOf {
            if (!synced) false else {
                val c = clock()
                c >= line.startMs && c <= lineEnd
            }
        }
    }
    val dist = index - active
    val isActive = synced && (dist == 0 || sounding)

    val tAlpha: Float
    val tScale: Float
    val tBlur: Float
    when {
        !synced -> { tAlpha = 0.8f; tScale = 1f; tBlur = 0f }
        isActive -> { tAlpha = 1f; tScale = 1.06f; tBlur = 0f }
        dist == 1 -> { tAlpha = 0.5f; tScale = 0.98f; tBlur = maxBlurPx * 5f / 12f }
        dist > 1 -> { tAlpha = 0.38f; tScale = 0.95f; tBlur = maxBlurPx }
        else -> {
            tAlpha = 0.24f; tScale = 0.93f
            tBlur = if (dist == -1) maxBlurPx * 5f / 12f else maxBlurPx
        }
    }
    val alphaS = animateFloatAsState(tAlpha, tween(600), label = "lyricAlpha")
    val scaleS = animateFloatAsState(tScale, tween(600), label = "lyricScale")
    val blurS = animateFloatAsState(if (paused) 0f else tBlur, tween(350), label = "lyricBlur")

    val weight = if (isActive || !synced) style.fontWeight else (style.inactiveFontWeight ?: style.fontWeight)
    val align = if (line.alignEnd) TextAlign.End else TextAlign.Center
    val sizeScale = if (line.isBackground) 0.75f else 1f
    val origin = TransformOrigin(if (line.alignEnd) 1f else 0.5f, 0.5f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (synced) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ) else Modifier
            )
            .graphicsLayer {
                alpha = alphaS.value
                scaleX = scaleS.value
                scaleY = scaleS.value
                transformOrigin = origin
                lagTick.intValue
                translationY = if (index in lags.indices) lags[index] else 0f
                val b = blurS.value
                if (b > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    renderEffect = BlurEffect(b, b, TileMode.Decal)
                }
            }
            .padding(horizontal = style.horizontalPadding, vertical = style.lineGap / 2),
    ) {
        if (synced) {
            KaraokeLine(
                line = line,
                style = style,
                weight = weight,
                sizeScale = sizeScale,
                align = align,
                clock = clock,
            )
        } else {
            BasicText(
                text = line.text,
                style = TextStyle(
                    color = style.sungColor,
                    fontSize = style.fontSize,
                    lineHeight = style.lineHeight,
                    fontFamily = style.fontFamily,
                    fontWeight = style.fontWeight,
                    fontFeatureSettings = "liga 0, clig 0",
                    textAlign = align,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
