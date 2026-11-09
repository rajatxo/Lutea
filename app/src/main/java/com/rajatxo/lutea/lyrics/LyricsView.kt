package com.rajatxo.lutea.lyrics

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import java.text.BreakIterator
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

// ─────────────────────────────────────────────────────────────────────────────
//  Lutea lyrics view — per-letter karaoke wave.
//  Behaviour studied from Mochy's LyricsGlowLine / LyricsScreen / LyricsStagger,
//  implementation written from scratch.
//
//  How a line is drawn:
//    Box (letter-motion drawWithContent)
//      ├─ BasicText  dim copy (unsung colour)
//      └─ BasicText  lit copy (sung colour) — offscreen layer, clipped to the sweep head,
//                    soft DstIn edge, plus Shadow glow on held words
//    The Box's drawWithContent then calls drawContent() once per letter slot inside
//    withTransform { translate; scale(baseline); clipRect(slot) { drawContent() } }.
//    The clip lives in the letter's own (unmoved) space, so only that letter's pixels travel.
// ─────────────────────────────────────────────────────────────────────────────

@Immutable
data class LyricsStyle(
    val fontSize: TextUnit = 32.sp,
    val lineHeight: TextUnit = 40.sp,
    val fontFamily: FontFamily = FontFamily.Default,
    val fontWeight: FontWeight = FontWeight.Bold,
    /** null = use [fontWeight] for inactive lines too (keeps line wrapping identical when a line activates). */
    val inactiveFontWeight: FontWeight? = null,
    val sungColor: Color = Color.White,
    val unsungColor: Color = Color.White.copy(alpha = 0.58f),
    val inactiveColor: Color = Color.White.copy(alpha = 0.36f),
    val glowColor: Color = Color.White,
    val glowRadius: Dp = 8.dp,
    val liftDistance: Dp = 3.dp,        // WordWaitDrop
    val heldZoom: Float = 0.075f,       // swell on held notes
    val heldGlowAlpha: Float = 0.5f,    // glow intensity on held notes
    val maxBlur: Dp = 12.dp,
    val focusFraction: Float = 0.055f,
    val minFocusDp: Dp = 72.dp,
    val lineGap: Dp = 12.dp,
    val horizontalPadding: Dp = 36.dp,
)

// ───────────────────────────── constants ─────────────────────────────

private val HeldLift = 2.5.dp
private val MotionBleed = 14.dp           // spare room around the lit layer (glow, rise, swell)
private val WordBoxPad = 4.dp

private const val RiseFollowSec = 0.08f   // omega 12.5
private const val SwellFollowSec = 0.2f   // omega 5.0
private const val RiseCompleteAt = 0.85f
private const val LetterRiseShare = 0.55f
private const val LetterRiseLeadChars = 0.5f
private const val LetterRiseSpanChars = 2.5f
private const val LetterSwellLeadChars = 0.35f
private const val LetterSwellSpanChars = 1.4f

private const val FastSecPerChar = 0.1f
private const val SlowSecPerChar = 0.34f
private const val GlowAttack = 0.35f
private const val GlowRelease = 0.3f
private const val SwellAttack = 0.2f
private const val SwellRelease = 0.12f
private const val NoGapSec = 0.03f
private const val SpaceTimeWeight = 0.4f
private const val MergeEpsilonPx = 0.1f
private const val NoLigatures = "liga 0, clig 0"

private const val STAGGER_LAG_PER_ROW_MS = 55f
private const val STAGGER_MAX_ROWS = 9

private val RecenterEasing = CubicBezierEasing(0.42f, 0f, 0.16f, 1f)

private fun smoothstep(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/** Exact critically damped spring step toward [target]. */
private fun dampedStep(values: FloatArray, vels: FloatArray, i: Int, target: Float, followSec: Float, dt: Float) {
    val omega = 1f / followSec
    val y = values[i] - target
    val v = vels[i]
    if (abs(y) < 0.0005f && abs(v) < 0.005f) {
        values[i] = target; vels[i] = 0f; return
    }
    val e = exp(-omega * dt)
    val tmp = (v + omega * y) * dt
    values[i] = target + (y + tmp) * e
    vels[i] = (v - omega * tmp) * e
}

private fun activeLine(lines: List<LyricLine>, t: Long): Int {
    var ans = -1
    for (i in lines.indices) {
        if (lines[i].startMs > t) break
        if (!lines[i].isBackground) ans = i
    }
    return ans
}

// ───────────────────────────── per-line timing model ─────────────────────────────

private class TokRange(val start: Int, val end: Int, val startMs: Long, val endMs: Long)

private class WordInfo(
    val startOff: Int, val endOff: Int,     // char offsets in line.text, trailing space excluded
    val startMs: Long, val endMs: Long,
    val hold: Float,                        // 0..1 held-note amount
)

private fun isSyllableGlyph(cp: Int): Boolean = when (Character.UnicodeScript.of(cp)) {
    Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA,
    Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL -> true
    else -> false
}

private fun sungLength(text: String, s: Int, e: Int): Int {
    var n = 0
    var i = s
    while (i < e) {
        val cp = text.codePointAt(i)
        n += if (isSyllableGlyph(cp)) 3 else if (Character.isLetterOrDigit(cp)) 1 else 0
        i += Character.charCount(cp)
    }
    return max(n, 1)
}

/** Scripts whose letters are separate shapes. Joined scripts (Arabic, Thai…) move as whole words. */
private fun splitsIntoLetters(text: String, start: Int, end: Int): Boolean {
    var i = start
    while (i < end) {
        val cp = text.codePointAt(i)
        when (Character.UnicodeScript.of(cp)) {
            Character.UnicodeScript.LATIN, Character.UnicodeScript.GREEK, Character.UnicodeScript.CYRILLIC,
            Character.UnicodeScript.ARMENIAN, Character.UnicodeScript.GEORGIAN, Character.UnicodeScript.HANGUL,
            Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HAN,
            Character.UnicodeScript.COMMON, Character.UnicodeScript.INHERITED -> Unit
            else -> return false
        }
        i += Character.charCount(cp)
    }
    return true
}

private class LineModel(line: LyricLine) {
    val text: String = line.text
    val toks: List<TokRange>
    val words: List<WordInfo>
    val cellStart: IntArray
    val cellEnd: IntArray
    val cellWord: IntArray
    val tiled: Boolean

    init {
        val tr = ArrayList<TokRange>()
        val tokWord = ArrayList<Int>()
        var cursor = 0
        for (tk in line.tokens) {
            val raw = tk.text
            val lead = raw.length - raw.trimStart().length
            val body = raw.trim()
            if (body.isNotEmpty()) {
                tr.add(TokRange(cursor + lead, cursor + lead + body.length, tk.startMs, tk.endMs))
                tokWord.add(tk.wordIndex)
            }
            cursor += raw.length
        }
        toks = tr

        val ws = ArrayList<WordInfo>()
        var a = 0
        while (a < tr.size) {
            var b = a
            while (b + 1 < tr.size && tokWord[b + 1] == tokWord[a]) b++
            val s = tr[a].start
            val e = tr[b].end
            val st = tr[a].startMs
            val en = tr[b].endMs
            val secPerChar = max(en - st, 1L) / 1000f / sungLength(text, s, e)
            val hold = smoothstep((secPerChar - FastSecPerChar) / (SlowSecPerChar - FastSecPerChar))
            ws.add(WordInfo(s, e, st, en, hold))
            a = b + 1
        }
        words = ws

        val rtl = text.any {
            val d = Character.getDirectionality(it)
            d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
        }
        tiled = !rtl
        val bi = if (!rtl && text.isNotEmpty()) BreakIterator.getCharacterInstance().also { it.setText(text) } else null
        val cs = ArrayList<Int>(); val ce = ArrayList<Int>(); val cw = ArrayList<Int>()
        for (w in ws.indices) {
            val s = ws[w].startOff
            val e = ws[w].endOff
            if (bi == null || !splitsIntoLetters(text, s, e)) {
                cs.add(s); ce.add(e); cw.add(w); continue
            }
            var p = s
            while (p < e) {
                var q = bi.following(p)
                if (q == BreakIterator.DONE || q > e) q = e
                cs.add(p); ce.add(q); cw.add(w)
                p = q
            }
        }
        cellStart = cs.toIntArray(); cellEnd = ce.toIntArray(); cellWord = cw.toIntArray()
    }

    val cellCount: Int get() = cellStart.size

    /** Sweep head as a (fractional) char offset. Seek-safe: a pure function of time. */
    fun headAt(t: Float): Float {
        var next = 0f
        for (i in toks.indices) {
            val tk = toks[i]
            if (t < tk.startMs) break
            val following = toks.getOrNull(i + 1)
            if (t >= tk.endMs) {
                next = tk.end.toFloat()
                if (following == null) continue
                val gap = (following.startMs - tk.endMs).toFloat()
                if (gap > 0f && t < following.startMs) {
                    val through = ((t - tk.endMs) / gap).coerceIn(0f, 1f)
                    next = tk.end + (following.start - tk.end) * smoothstep(through)
                }
                continue
            }
            val span = (tk.endMs - tk.startMs).toFloat()
            if (span <= 0f) break
            val within = ((t - tk.startMs) / span).coerceIn(0f, 1f)
            val len = max(tk.end - tk.start, 1).toFloat()
            val tail = if (following != null && (following.startMs - tk.endMs) / 1000f <= NoGapSec &&
                following.start > tk.end
            ) (following.start - tk.end).toFloat() else 0f
            next = if (tail > 0f) {
                val share = len / (len + tail * SpaceTimeWeight)
                if (within <= share) tk.start + len * (within / share)
                else tk.end + tail * ((within - share) / (1f - share))
            } else tk.start + len * within
            break
        }
        return next
    }
}

// ───────────────────────────── per-line spring state ─────────────────────────────

private class LineMotion(val model: LineModel) {
    val tick = mutableIntStateOf(0)           // read in draw to subscribe
    var head = 0f
    val rise = FloatArray(model.cellCount)
    val riseV = FloatArray(model.cellCount)
    val swell = FloatArray(model.cellCount)
    val swellV = FloatArray(model.cellCount)
    val wordGlow = FloatArray(model.words.size)      // 0..1 envelope (hold * attack * release)
    private val wordSwellEnv = FloatArray(model.words.size)

    fun reset() {
        head = 0f
        rise.fill(0f); riseV.fill(0f); swell.fill(0f); swellV.fill(0f); wordGlow.fill(0f); wordSwellEnv.fill(0f)
        tick.intValue++
    }

    fun step(tMs: Float, dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.064f)
        val h = model.headAt(tMs)
        head = h

        for (w in model.words.indices) {
            val wd = model.words[w]
            var glowT = 0f
            var swellT = 0f
            if (tMs >= wd.startMs && tMs <= wd.endMs && wd.hold > 0f) {
                val wp = ((tMs - wd.startMs) / max(wd.endMs - wd.startMs, 1L)).coerceIn(0f, 1f)
                glowT = wd.hold * smoothstep(wp / GlowAttack) * smoothstep((1f - wp) / GlowRelease)
                swellT = wd.hold * smoothstep(wp / SwellAttack) * smoothstep((1f - wp) / SwellRelease)
            }
            val g = wordGlow[w]
            wordGlow[w] = if (glowT >= g) glowT else g + (glowT - g) * (1f - exp(-dt * 8f))
            if (wordGlow[w] < 0.002f) wordGlow[w] = 0f
            wordSwellEnv[w] = swellT
        }

        for (c in 0 until model.cellCount) {
            val w = model.cellWord[c]
            val wd = model.words[w]
            val letterStart = model.cellStart[c].toFloat()

            val through = (h - wd.startOff) / max(wd.endOff - wd.startOff, 1)
            val wordRise = smoothstep(through / RiseCompleteAt)
            val letterRise = if (h >= wd.endOff) 1f
            else smoothstep((h - letterStart + LetterRiseLeadChars) / LetterRiseSpanChars)
            val riseTarget = wordRise + (letterRise - wordRise) * LetterRiseShare

            val swellTarget = if (h >= wd.startOff && h <= wd.endOff)
                wordSwellEnv[w] * smoothstep((h - letterStart + LetterSwellLeadChars) / LetterSwellSpanChars)
            else 0f

            dampedStep(rise, riseV, c, riseTarget, RiseFollowSec, dt)
            dampedStep(swell, swellV, c, swellTarget, SwellFollowSec, dt)
        }
        tick.intValue++
    }
}

// ───────────────────────────── letter slots (geometry from the TextLayoutResult) ─────────────────────────────

private class MotionLayout(
    val count: Int,
    val tiled: Boolean,
    val cell: IntArray,
    val word: IntArray,
    val line: IntArray,
    val coreLeft: FloatArray,
    val coreRight: FloatArray,
    val clipLeft: FloatArray,
    val clipTop: FloatArray,
    val clipRight: FloatArray,
    val clipBottom: FloatArray,
    val baseline: FloatArray,
    /** per word: left, top, right, bottom (left > right when the word has no ink). */
    val wordRect: FloatArray,
) {
    val dx = FloatArray(count)
    val dy = FloatArray(count)
    val scale = FloatArray(count)
}

private class MotionCache {
    private var layout: TextLayoutResult? = null
    private var built: MotionLayout? = null
    fun layoutFor(l: TextLayoutResult, model: LineModel, edgePx: Float, wordPadPx: Float): MotionLayout {
        val b = built
        if (b != null && layout === l) return b
        return buildMotionLayout(l, model, edgePx, wordPadPx).also { layout = l; built = it }
    }
}

private fun buildMotionLayout(layout: TextLayoutResult, model: LineModel, edgePx: Float, wordPadPx: Float): MotionLayout {
    val text = model.text
    val nWords = model.words.size
    val wordRect = FloatArray(nWords * 4) { if (it % 4 < 2) Float.POSITIVE_INFINITY else Float.NEGATIVE_INFINITY }
    // wordRect layout: [left=+inf, top=+inf, right=-inf, bottom=-inf]
    val capacity = model.cellCount + layout.lineCount + 1
    val cells = IntArray(capacity); val lines = IntArray(capacity)
    val segStarts = IntArray(capacity); val segEnds = IntArray(capacity)
    val lefts = FloatArray(capacity); val rights = FloatArray(capacity)
    var n = 0
    if (text.isNotEmpty() && layout.lineCount > 0) {
        val lastLine = layout.lineCount - 1
        for (c in 0 until model.cellCount) {
            val start = model.cellStart[c].coerceIn(0, text.length)
            val end = model.cellEnd[c].coerceIn(start, text.length)
            if (end <= start) continue
            val l0 = layout.getLineForOffset(start).coerceIn(0, lastLine)
            val l1 = layout.getLineForOffset(end - 1).coerceIn(l0, lastLine)
            for (line in l0..l1) {
                val segStart = max(start, layout.getLineStart(line))
                val segEnd = min(end, layout.getLineEnd(line))
                if (segEnd <= segStart) continue
                var left = Float.POSITIVE_INFINITY
                var right = Float.NEGATIVE_INFINITY
                for (i in segStart until segEnd) {
                    if (text[i].isWhitespace()) continue
                    val b = layout.getBoundingBox(i)
                    left = min(left, b.left); right = max(right, b.right)
                }
                if (left > right || n >= capacity) continue
                cells[n] = c; lines[n] = line; segStarts[n] = segStart; segEnds[n] = segEnd
                lefts[n] = left; rights[n] = right
                n++
            }
        }
    }

    val lastLine = layout.lineCount - 1
    val clipLeft = FloatArray(n); val clipRight = FloatArray(n)
    val clipTop = FloatArray(n); val clipBottom = FloatArray(n)
    val baseline = FloatArray(n)
    for (k in 0 until n) {
        val line = lines[k]
        clipTop[k] = layout.getLineTop(line) - if (line == 0) edgePx else 0f
        clipBottom[k] = layout.getLineBottom(line) + if (line == lastLine) edgePx else 0f
        baseline[k] = layout.getLineBaseline(line)
    }

    if (model.tiled) {
        // Slots cover each line edge to edge: every pixel of ink lands in exactly one slot.
        var a = 0
        while (a < n) {
            var b = a
            while (b + 1 < n && lines[b + 1] == lines[a]) b++
            val line = lines[a]
            clipLeft[a] = min(lefts[a], layout.getLineLeft(line)) - edgePx
            clipRight[b] = max(rights[b], layout.getLineRight(line)) + edgePx
            for (k in a until b) {
                val meet = (rights[k] + lefts[k + 1]) / 2f
                clipRight[k] = meet; clipLeft[k + 1] = meet
            }
            a = b + 1
        }
    } else {
        for (k in 0 until n) {
            val s = segStarts[k]; val e = segEnds[k]
            val padL = if (s == 0 || text[s - 1].isWhitespace()) wordPadPx else 0f
            val padR = if (e >= text.length || text[e].isWhitespace()) wordPadPx else 0f
            clipLeft[k] = lefts[k] - padL
            clipRight[k] = rights[k] + padR
        }
    }

    val wordOf = IntArray(n) { model.cellWord[cells[it]] }
    for (k in 0 until n) {
        val w = wordOf[k] * 4
        wordRect[w] = min(wordRect[w], lefts[k])
        wordRect[w + 1] = min(wordRect[w + 1], layout.getLineTop(lines[k]))
        wordRect[w + 2] = max(wordRect[w + 2], rights[k])
        wordRect[w + 3] = max(wordRect[w + 3], layout.getLineBottom(lines[k]))
    }

    return MotionLayout(
        count = n, tiled = model.tiled,
        cell = cells.copyOf(n), word = wordOf, line = lines.copyOf(n),
        coreLeft = lefts.copyOf(n), coreRight = rights.copyOf(n),
        clipLeft = clipLeft, clipTop = clipTop, clipRight = clipRight, clipBottom = clipBottom,
        baseline = baseline, wordRect = wordRect,
    )
}

private fun settledSwell(s: Float): Float = if (s < 0.002f) 0f else s

/**
 * Draws the content one letter at a time: clip to the letter's own slot, then move it.
 * dy = WordWaitDrop * (1 - rise) - HeldLift * swell ; swell scales around the BASELINE.
 */
private fun Modifier.letterMotion(
    holder: Array<TextLayoutResult?>,
    cache: MotionCache,
    model: LineModel,
    motion: LineMotion,
    style: LyricsStyle,
    amplitude: () -> Float,
): Modifier = drawWithContent {
    motion.tick.intValue // subscribe to the per-frame springs
    val layout = holder[0]
    val amp = amplitude().coerceIn(0f, 1f)
    if (layout == null || amp < 0.002f || model.cellCount == 0) {
        drawContent(); return@drawWithContent
    }
    val m = cache.layoutFor(layout, model, MotionBleed.toPx(), WordBoxPad.toPx())
    val n = m.count
    if (n == 0) { drawContent(); return@drawWithContent }

    val drop = style.liftDistance.toPx() * amp
    val lift = HeldLift.toPx()
    val zoom = style.heldZoom

    var uniform = true
    var i = 0
    while (i < n) {
        var j = i
        while (j + 1 < n && m.word[j + 1] == m.word[i] && m.line[j + 1] == m.line[i]) j++
        var swellSum = 0f
        for (k in i..j) swellSum += settledSwell(motion.swell[m.cell[k]])
        val meanSwell = swellSum / (j - i + 1)
        val pivot = (m.coreLeft[i] + m.coreRight[j]) / 2f
        for (k in i..j) {
            val c = m.cell[k]
            val sw = settledSwell(motion.swell[c])
            val dy = drop * (1f - motion.rise[c]) - lift * sw
            m.dy[k] = if (abs(dy) < 0.05f) 0f else dy
            m.dx[k] = ((m.coreLeft[k] + m.coreRight[k]) / 2f - pivot) * zoom * meanSwell
            m.scale[k] = 1f + zoom * sw
            if (m.scale[k] != 1f || m.dx[k] != 0f || abs(m.dy[k] - m.dy[0]) > MergeEpsilonPx) uniform = false
        }
        i = j + 1
    }

    if (uniform) {
        val dy = m.dy[0]
        if (dy == 0f) drawContent()
        else withTransform({ translate(0f, dy) }) { this@drawWithContent.drawContent() }
        return@drawWithContent
    }

    var k = 0
    while (k < n) {
        // Neighbouring slots that move together are drawn as one.
        if (m.tiled && m.scale[k] == 1f && m.dx[k] == 0f) {
            var e = k
            while (e + 1 < n && m.line[e + 1] == m.line[k] && m.scale[e + 1] == 1f &&
                m.dx[e + 1] == 0f && abs(m.dy[e + 1] - m.dy[k]) <= MergeEpsilonPx
            ) e++
            withTransform({ translate(0f, m.dy[k]) }) {
                clipRect(m.clipLeft[k], m.clipTop[k], m.clipRight[e], m.clipBottom[k]) {
                    this@drawWithContent.drawContent()
                }
            }
            k = e + 1
            continue
        }
        val s = m.scale[k]
        withTransform({
            translate(m.dx[k], m.dy[k])
            if (s != 1f) scale(s, s, Offset((m.coreLeft[k] + m.coreRight[k]) / 2f, m.baseline[k]))
        }) {
            clipRect(m.clipLeft[k], m.clipTop[k], m.clipRight[k], m.clipBottom[k]) {
                this@drawWithContent.drawContent()
            }
        }
        k++
    }
}

/**
 * Lit copy: shows only what the sweep head has passed, with a soft DstIn edge, and a Shadow glow
 * behind held words. Must sit inside an Offscreen layer (the caller does that).
 */
private fun Modifier.sweepMask(
    holder: Array<TextLayoutResult?>,
    cache: MotionCache,
    model: LineModel,
    motion: LineMotion,
    style: LyricsStyle,
    path: Path,
): Modifier = drawWithContent {
    motion.tick.intValue
    val layout = holder[0] ?: return@drawWithContent
    val len = model.text.length
    val h = motion.head
    if (h <= 0f || len == 0 || layout.lineCount == 0) return@drawWithContent

    val reach = MotionBleed.toPx()
    val m = cache.layoutFor(layout, model, reach, WordBoxPad.toPx())
    val lastLine = layout.lineCount - 1
    fun topOf(line: Int) = layout.getLineTop(line) - if (line == 0) reach else 0f
    fun bottomOf(line: Int) = layout.getLineBottom(line) + if (line == lastLine) reach else 0f

    val done = h >= len
    val idx = h.toInt().coerceIn(0, len - 1)
    val headLine = if (done) lastLine else layout.getLineForOffset(idx).coerceIn(0, lastLine)
    val rtl = layout.getParagraphDirection(idx) == ResolvedTextDirection.Rtl
    val flow = if (rtl) -1f else 1f
    val headX = if (done) {
        if (rtl) layout.getLineLeft(lastLine) else layout.getLineRight(lastLine)
    } else {
        val b = layout.getBoundingBox(idx)
        val f = (h - idx).coerceIn(0f, 1f)
        if (rtl) b.right - f * (b.right - b.left) else b.left + f * (b.right - b.left)
    }
    val soft = layout.layoutInput.style.fontSize.toPx() * 0.5f
    val fromX = headX - flow * soft * 0.6f
    val toX = headX + flow * soft * 0.4f

    path.rewind()
    for (line in 0..headLine) {
        var left = layout.getLineLeft(line) - reach
        var right = layout.getLineRight(line) + reach
        if (line == headLine && !done) {
            if (rtl) left = max(toX, left) else right = min(toX, right)
        }
        if (right > left) path.addRect(Rect(left, topOf(line), right, bottomOf(line)))
    }

    clipPath(path) {
        this@drawWithContent.drawContent()
        // Glow on held words: Shadow-blurred redraw of the lit text, limited to that word's box.
        val glowRadiusPx = style.glowRadius.toPx()
        for (w in model.words.indices) {
            val g = motion.wordGlow[w] * style.heldGlowAlpha
            if (g < 0.01f) continue
            val l = m.wordRect[w * 4]; val t = m.wordRect[w * 4 + 1]
            val r = m.wordRect[w * 4 + 2]; val b = m.wordRect[w * 4 + 3]
            if (l > r) continue
            val glowReach = glowRadiusPx * 2f
            clipRect(l - glowReach, t - glowReach, r + glowReach, b + glowReach) {
                drawText(
                    layout,
                    color = style.sungColor,
                    shadow = Shadow(
                        color = style.glowColor.copy(alpha = g),
                        offset = Offset.Zero,
                        blurRadius = glowRadiusPx * (0.5f + 1.5f * g),
                    ),
                )
            }
        }
    }

    if (!done) {
        val x0 = min(fromX, toX)
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black, 1f to Color.Transparent,
                startX = fromX, endX = toX,
            ),
            topLeft = Offset(x0, topOf(headLine)),
            size = androidx.compose.ui.geometry.Size(abs(toX - fromX), bottomOf(headLine) - topOf(headLine)),
            blendMode = BlendMode.DstIn,
        )
    }
}

/** Same layout as the content, but the layer that follows gets [bleed] of extra room on every side. */
private fun Modifier.layerBleed(bleed: Dp): Modifier = layout { measurable, constraints ->
    val px = bleed.roundToPx()
    val placeable = measurable.measure(
        androidx.compose.ui.unit.Constraints(
            minWidth = (constraints.minWidth - px * 2).coerceAtLeast(0),
            maxWidth = (constraints.maxWidth - px * 2).coerceAtLeast(0),
            minHeight = (constraints.minHeight - px * 2).coerceAtLeast(0),
            maxHeight = (constraints.maxHeight - px * 2).coerceAtLeast(0),
        )
    )
    layout(
        (placeable.width - px * 2).coerceIn(constraints.minWidth, constraints.maxWidth),
        (placeable.height - px * 2).coerceIn(constraints.minHeight, constraints.maxHeight),
    ) { placeable.place(-px, -px) }
}

// ───────────────────────────── cascade / stagger ─────────────────────────────

private class StaggerState {
    val offsets = mutableStateMapOf<Int, Float>()
    private val velocities = HashMap<Int, Float>()
    var anchorRow = -1

    /** The list just scrolled by [px]; the rows trailing the active line hold back by that much. */
    fun holdBack(px: Float, rowCount: Int) {
        if (anchorRow < 0) return
        val last = min(anchorRow + STAGGER_MAX_ROWS, rowCount - 1)
        for (row in (anchorRow + 1)..last) offsets[row] = (offsets[row] ?: 0f) + px
    }

    /** Each row chases its place on an exactly-solved critically damped spring (tau = 55ms × distance). */
    fun update(dt: Float) {
        if (offsets.isEmpty()) return
        for (row in offsets.keys.toList()) {
            val k = (row - anchorRow).coerceIn(1, STAGGER_MAX_ROWS)
            val omega = 1000f / (STAGGER_LAG_PER_ROW_MS * k)
            val lag = offsets[row] ?: 0f
            val v = velocities[row] ?: 0f
            val e = exp(-omega * dt)
            val tmp = (v + omega * lag) * dt
            val nextLag = (lag + tmp) * e
            val nextV = (v - omega * tmp) * e
            if (abs(nextLag) < 0.3f && abs(nextV) < 4f) {
                offsets.remove(row); velocities.remove(row)
            } else {
                offsets[row] = nextLag; velocities[row] = nextV
            }
        }
    }

    val isMoving: Boolean get() = offsets.isNotEmpty()
}

// ───────────────────────────── one lyric line ─────────────────────────────

@Composable
private fun LyricLineItem(
    line: LyricLine,
    synced: Boolean,
    isActive: Boolean,
    relation: Int,                       // index - activeIndex (negative = already sung)
    clock: State<Float>,
    style: LyricsStyle,
    blurEffects: Array<RenderEffect?>,
    staggerOffset: () -> Float,
    onClick: (() -> Unit)?,
) {
    val sizeScale = if (line.isBackground) 0.72f else 1f
    val weight = when {
        line.isBackground -> FontWeight.SemiBold
        isActive -> style.fontWeight
        else -> style.inactiveFontWeight ?: style.fontWeight
    }
    val align = if (line.alignEnd) TextAlign.End else TextAlign.Center
    val dimStyle = remember(style, weight, align, sizeScale) {
        TextStyle(
            fontFamily = style.fontFamily,
            fontWeight = weight,
            fontSize = style.fontSize * sizeScale,
            lineHeight = style.lineHeight * sizeScale,
            textAlign = align,
            color = if (synced) style.unsungColor else style.sungColor.copy(alpha = 0.92f),
            fontFeatureSettings = NoLigatures,   // a ligature is one glyph over two letters; can't be torn apart
        )
    }
    val litStyle = remember(dimStyle, style) { dimStyle.copy(color = style.sungColor) }

    // ACTIVE alpha 1.0 / scale 1.06 · UPCOMING 0.5 / 0.98 · DEFAULT 0.38 / 0.95 · PAST 0.24 / 0.93
    val alphaTarget = when {
        !synced -> 0.95f
        isActive -> 1f
        relation == 1 -> 0.5f
        relation >= 2 -> 0.38f
        else -> 0.24f
    }
    val scaleTarget = when {
        !synced -> 1f
        isActive -> 1.06f
        relation == 1 -> 0.98f
        relation >= 2 -> 0.95f
        else -> 0.93f
    }
    val blurStep = when {
        !synced || isActive -> 0
        abs(relation) <= 1 -> 1
        else -> 2
    }
    val focus = animateFloatAsState(if (isActive && synced) 1f else 0f, tween(600), label = "lyricFocus")
    val alpha = animateFloatAsState(alphaTarget, tween(600), label = "lyricAlpha")
    val scale = animateFloatAsState(scaleTarget, tween(600), label = "lyricScale")
    val blur = animateFloatAsState(blurStep.toFloat(), tween(350), label = "lyricBlur")
    val interaction = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .padding(vertical = style.lineGap)
            .graphicsLayer {
                val s = scale.value
                scaleX = s; scaleY = s
                transformOrigin = TransformOrigin(if (line.alignEnd) 1f else 0.5f, 0.5f)
                this.alpha = alpha.value
                val step = (blur.value + 0.5f).toInt().coerceIn(0, blurEffects.size - 1)
                renderEffect = blurEffects[step]
                translationY = staggerOffset()   // cascade
            },
    ) {
        if (!synced) {
            BasicText(line.text, modifier = Modifier.fillMaxWidth(), style = dimStyle)
            return@Box
        }

        val model = remember(line) { LineModel(line) }
        val motion = remember(model) { LineMotion(model) }
        val holder = remember(model) { arrayOfNulls<TextLayoutResult>(1) }
        val cache = remember(model) { MotionCache() }
        val path = remember(model) { Path() }

        // Drive this line's springs while it's active (and while it fades out).
        LaunchedEffect(model, isActive) {
            var last = -1L
            fun dtOf(now: Long): Float = (if (last < 0) 0.016f else (now - last) / 1e9f).also { last = now }
            if (isActive) {
                while (isActive) withFrameNanos { now -> motion.step(clock.value, dtOf(now)) }
            } else {
                while (focus.value > 0.002f) withFrameNanos { now -> motion.step(clock.value, dtOf(now)) }
                motion.reset()
            }
        }

        Box(Modifier.fillMaxWidth().letterMotion(holder, cache, model, motion, style) { focus.value }) {
            // dim copy
            BasicText(
                text = line.text,
                modifier = Modifier.fillMaxWidth(),
                style = dimStyle,
                onTextLayout = { holder[0] = it },
            )
            // lit copy — its own offscreen layer so the DstIn edge only touches this copy
            BasicText(
                text = line.text,
                modifier = Modifier
                    .layerBleed(MotionBleed)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        this.alpha = focus.value
                    }
                    .padding(MotionBleed)
                    .fillMaxWidth()
                    .sweepMask(holder, cache, model, motion, style, path),
                style = litStyle,
            )
        }
    }
}

// ───────────────────────────── public API ─────────────────────────────

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
                style = TextStyle(color = style.unsungColor.copy(alpha = 0.7f), fontSize = 20.sp, fontFamily = style.fontFamily),
            )
        }
        return
    }

    val synced = lyrics.sync != SyncType.NONE
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    // Smooth clock (ms, float so sub-ms frame deltas don't drift): advance by frame delta while
    // playing; every 500ms re-sync with the real position and correct if it drifted > 200ms.
    val clock = remember { mutableFloatStateOf(0f) }
    val latestPosition by rememberUpdatedState(positionMs)
    LaunchedEffect(isPlaying, offsetMs) {
        clock.floatValue = (latestPosition() + offsetMs).toFloat()
        var lastNanos = 0L
        var lastSyncWallMs = System.currentTimeMillis()
        while (isActive) {
            withFrameNanos { now ->
                if (isPlaying && lastNanos > 0L) {
                    clock.floatValue += ((now - lastNanos) / 1_000_000f).coerceAtMost(100f)
                }
                lastNanos = now
            }
            val wallMs = System.currentTimeMillis()
            if (wallMs - lastSyncWallMs > 500) {
                lastSyncWallMs = wallMs
                val actual = (latestPosition() + offsetMs).toFloat()
                if (abs(actual - clock.floatValue) > 200f) clock.floatValue = actual
            }
        }
    }

    val activeIndex by remember(lyrics) {
        derivedStateOf { if (synced) activeLine(lyrics.lines, clock.floatValue.toLong()) else -1 }
    }

    // 3-stage blur: none, 5px, 12px — Decal tile mode, API 31+.
    val blurEffects = remember(style.maxBlur) {
        val radii = floatArrayOf(0f, 5f, 12f)
        Array<RenderEffect?>(3) { i ->
            if (i == 0 || style.maxBlur.value <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) null
            else BlurEffect(radii[i], radii[i], TileMode.Decal)
        }
    }

    // Cascade springs: asleep until a scroll adds lag, then one frame loop until everything settles.
    val stagger = remember { StaggerState() }
    LaunchedEffect(stagger) {
        while (isActive) {
            snapshotFlow { stagger.isMoving }.first { it }
            var last = -1L
            while (stagger.isMoving) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.064f)
                    last = now
                    stagger.update(dt)
                }
            }
        }
    }

    // Pause auto-follow while the user drags; resume 3.5s after they let go.
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var followPaused by remember { mutableStateOf(false) }
    LaunchedEffect(dragged) {
        if (dragged) followPaused = true
        else if (followPaused) { delay(3500); followPaused = false }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportPx = with(density) { maxHeight.toPx() }
        // Focus point: max(5.5% of the viewport, 72dp) from the top.
        val focusPx = maxOf(viewportPx * style.focusFraction, with(density) { style.minFocusDp.toPx() })

        // Auto-scroll: 950ms tween, CubicBezier(0.42, 0, 0.16, 1). Keyed on activeIndex/followPaused only.
        LaunchedEffect(activeIndex, followPaused) {
            if (!synced || followPaused || activeIndex < 0) return@LaunchedEffect
            var info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
            if (info == null) {
                listState.animateScrollToItem(activeIndex, 0)
                info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
            }
            if (info != null) {
                // item offset is relative to the viewport start, which sits above the content padding
                val screenY = info.offset - listState.layoutInfo.viewportStartOffset
                val target = screenY - focusPx
                if (abs(target) > 0.5f) {
                    stagger.anchorRow = activeIndex
                    var last = 0f
                    listState.scroll {
                        animate(0f, target, animationSpec = tween(950, easing = RecenterEasing)) { v, _ ->
                            val d = v - last
                            last = v
                            scrollBy(d)
                            stagger.holdBack(d, lyrics.lines.size)   // rows below trail the scroll
                        }
                    }
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
                    // Immersive fade: top 10% + bottom 38% dissolve.
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
                bottom = with(density) { (viewportPx * 0.55f).toDp() },
                start = style.horizontalPadding,
                end = style.horizontalPadding,
            ),
        ) {
            itemsIndexed(lyrics.lines, key = { i, _ -> i }) { i, line ->
                val active = synced && (i == activeIndex || (line.isBackground && i - 1 == activeIndex))
                LyricLineItem(
                    line = line,
                    synced = synced,
                    isActive = active,
                    relation = if (activeIndex < 0) i + 1 else i - activeIndex,
                    clock = clock,
                    style = style,
                    blurEffects = blurEffects,
                    staggerOffset = { stagger.offsets[i] ?: 0f },
                    onClick = if (synced) ({ onSeek(line.startMs) }) else null,
                )
            }
        }
    }
}
