package com.rajatxo.lutea.lyrics

import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import kotlin.math.max
import kotlin.math.min

// ─────────────────────────────────────────────────────────────────────────────
//  Lutea lyrics — data model + parsers.  100% original code, no third-party deps
//  (uses only android.util.Xml and org.json that ship with Android).
// ─────────────────────────────────────────────────────────────────────────────

/** Best → worst. WORD = per-word/syllable timing, LINE = per-line timing, NONE = plain text. */
enum class SyncType { WORD, LINE, NONE }

/** One timed piece of a line (a word, or one syllable of a word). */
class LyricToken(
    val text: String,      // exact visible text; non-final words end with a space
    val startMs: Long,
    val endMs: Long,
    val wordIndex: Int,    // tokens sharing a wordIndex are one word (they lift + glow together)
)

class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val tokens: List<LyricToken>,   // never empty
    val isBackground: Boolean = false,
    val alignEnd: Boolean = false,  // duets: second singer is drawn on the right
) {
    val text: String = tokens.joinToString("") { it.text }
}

class Lyrics(
    val lines: List<LyricLine>,
    val sync: SyncType,
    val source: String,
    val isInstrumental: Boolean = false,
)

internal fun JSONObject.str(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

/** Drops blank tokens, trims the last token, and numbers words by whitespace boundaries. */
internal fun finishTokens(raw: List<LyricToken>): List<LyricToken> {
    val toks = raw.filter { it.text.isNotBlank() }
    if (toks.isEmpty()) return emptyList()
    val out = ArrayList<LyricToken>(toks.size)
    var word = 0
    for (i in toks.indices) {
        val t = toks[i]
        val text = if (i == toks.lastIndex) t.text.trimEnd() else t.text
        out.add(LyricToken(text, t.startMs, max(t.endMs, t.startMs), word))
        val endsSpace = text.isNotEmpty() && text.last().isWhitespace()
        val nextStartsSpace = i + 1 < toks.size && toks[i + 1].text.first().isWhitespace()
        if (endsSpace || nextStartsSpace) word++
    }
    return out
}

private fun LyricToken.withText(t: String) = LyricToken(t, startMs, endMs, wordIndex)

/**
 * Some APIs give syllables/words without spaces. Walk the full line text to decide where the
 * real word boundaries are, so "Hel"+"lo" stays one word and "Hello"+"world" becomes two.
 */
internal fun alignTokens(raw: List<LyricToken>, lineText: String): List<LyricToken> {
    var pos = 0
    val out = ArrayList<LyricToken>()
    for (tk in raw) {
        val t = tk.text.trim()
        if (t.isEmpty()) continue
        val idx = lineText.indexOf(t, pos)
        val matched = idx >= 0 && lineText.substring(pos, idx).isBlank()
        if ((matched && idx > pos) || !matched) {
            if (out.isNotEmpty()) out[out.lastIndex] = out.last().withText(out.last().text.trimEnd() + " ")
        }
        out.add(LyricToken(t, tk.startMs, tk.endMs, 0))
        if (matched) pos = idx + t.length
    }
    return finishTokens(out)
}

/** Invents believable per-word timing for lines that only have a start/end (LRC, most APIs). */
internal object LyricsTiming {
    private const val PUNCT = ",;:.!?…"

    fun estimate(text: String, startMs: Long, endMs: Long): List<LyricToken> {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val letters = text.count { it.isLetterOrDigit() }
        val avail = max(endMs - startMs, 1L).toDouble()
        val tail = min(250.0, avail * 0.1)
        var sung = min(avail - tail, max(1000.0, letters * 260.0))   // don't stretch a short line over a long gap
        sung = max(sung, min(avail, 400.0))
        val weights = words.map { w ->
            w.count { it.isLetterOrDigit() } + 0.6 + (if (w.last() in PUNCT) 0.8 else 0.0)
        }
        val total = weights.sum()
        var cur = startMs.toDouble()
        val out = ArrayList<LyricToken>(words.size)
        for (i in words.indices) {
            val d = sung * weights[i] / total
            out.add(LyricToken(if (i < words.lastIndex) words[i] + " " else words[i], Math.round(cur), Math.round(cur + d), i))
            cur += d
        }
        return out
    }
}

object PlainParser {
    fun parse(raw: String, source: String): Lyrics? {
        val lines = raw.replace("\r", "").split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            .map { LyricLine(0L, 0L, listOf(LyricToken(it, 0L, 0L, 0))) }
        return if (lines.isEmpty()) null else Lyrics(lines, SyncType.NONE, source)
    }
}

// ───────────────────────────── LRC (+ enhanced word tags) ─────────────────────────────
object LrcParser {
    private val lineTs = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordTs = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val offsetTag = Regex("""^\[offset:\s*([+-]?\d+)]""", RegexOption.IGNORE_CASE)

    private fun ms(m: MatchResult): Long {
        val (mm, ss, fr) = m.destructured
        var f = 0L
        if (fr.isNotEmpty()) {
            val v = fr.toLong()
            f = when (fr.length) { 1 -> v * 100; 2 -> v * 10; else -> v }
        }
        return (mm.toLong() * 60 + ss.toLong()) * 1000 + f
    }

    fun parse(raw: String, source: String): Lyrics? {
        var offset = 0L
        val flat = ArrayList<Pair<Long, String>>()
        for (rawLine in raw.replace("\r", "").split("\n")) {
            val ln = rawLine.trim()
            if (ln.isEmpty()) continue
            val om = offsetTag.find(ln)
            if (om != null) { offset = om.groupValues[1].toLong(); continue }
            val stamps = ArrayList<Long>()
            var pos = 0
            while (true) {
                val m = lineTs.find(ln, pos) ?: break
                if (m.range.first != pos) break
                stamps.add(ms(m))
                pos = m.range.last + 1
            }
            if (stamps.isEmpty()) continue
            val text = ln.substring(pos)
            for (s in stamps) flat.add(max(s - offset, 0L) to text)
        }
        if (flat.isEmpty()) return null
        flat.sortBy { it.first }

        val lines = ArrayList<LyricLine>()
        var anyWord = false
        for (i in flat.indices) {
            val start = flat[i].first
            val text = flat[i].second
            val next = if (i + 1 < flat.size) flat[i + 1].first else start + 5000L
            if (text.isBlank()) continue   // an empty stamp just marks where the previous line ends
            val tokens = if (wordTs.containsMatchIn(text)) {
                anyWord = true
                enhancedTokens(text, start, next)
            } else {
                LyricsTiming.estimate(text, start, next)
            }
            val fin = finishTokens(tokens)
            if (fin.isNotEmpty()) lines.add(LyricLine(start, next, fin))
        }
        if (lines.isEmpty()) return null
        return Lyrics(lines, if (anyWord) SyncType.WORD else SyncType.LINE, source)
    }

    private fun enhancedTokens(text: String, lineStart: Long, lineEnd: Long): List<LyricToken> {
        val tags = wordTs.findAll(text).toList()
        val out = ArrayList<LyricToken>()
        if (tags.isNotEmpty() && tags[0].range.first > 0) {
            val pre = text.substring(0, tags[0].range.first)
            if (pre.isNotBlank()) out.add(LyricToken(pre, lineStart, ms(tags[0]), 0))
        }
        for (i in tags.indices) {
            val segStart = tags[i].range.last + 1
            val segEnd = if (i + 1 < tags.size) tags[i + 1].range.first else text.length
            val seg = text.substring(segStart, segEnd)
            if (seg.isBlank()) continue
            val st = ms(tags[i])
            val en = if (i + 1 < tags.size) ms(tags[i + 1]) else lineEnd
            out.add(LyricToken(seg, st, max(en, st), 0))
        }
        return out
    }
}

// ───────────────────────────── TTML (Apple-style, word/syllable timing) ─────────────────────────────
private class XNode(val name: String, val attrs: Map<String, String>) {
    val kids = ArrayList<Any>()   // XNode or String
}

object TtmlParser {
    private fun dom(xml: String): XNode? {
        return try {
            val p = Xml.newPullParser()
            p.setInput(StringReader(xml))
            val stack = ArrayList<XNode>()
            var root: XNode? = null
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> {
                        val attrs = HashMap<String, String>()
                        for (i in 0 until p.attributeCount) attrs[p.getAttributeName(i)] = p.getAttributeValue(i)
                        val n = XNode(p.name.substringAfter(':'), attrs)
                        if (stack.isNotEmpty()) stack.last().kids.add(n)
                        if (root == null) root = n
                        stack.add(n)
                    }
                    XmlPullParser.TEXT -> if (stack.isNotEmpty()) stack.last().kids.add(p.text)
                    XmlPullParser.END_TAG -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                }
                ev = p.next()
            }
            root
        } catch (e: Exception) {
            null
        }
    }

    /** "12.345", "1:12.345", "01:02:03.5", "12.5s", "250ms" → milliseconds. */
    internal fun time(raw: String): Long {
        var s = raw.trim()
        if (s.endsWith("ms")) return s.dropLast(2).toDouble().toLong()
        if (s.endsWith("s")) s = s.dropLast(1)
        var secs = 0.0
        for (part in s.split(":")) secs = secs * 60 + part.toDouble()
        return Math.round(secs * 1000)
    }

    private class Raw(var text: String, val start: Long?, val end: Long?, val bg: Boolean)

    private fun hasTimedDesc(n: XNode): Boolean =
        n.kids.any { it is XNode && it.name == "span" && (it.attrs.containsKey("begin") || hasTimedDesc(it)) }

    private fun textOf(n: XNode): String {
        val sb = StringBuilder()
        for (k in n.kids) {
            if (k is String) sb.append(k) else if (k is XNode) sb.append(textOf(k))
        }
        return sb.toString()
    }

    private fun collect(node: XNode, bg: Boolean, out: ArrayList<Raw>) {
        for (k in node.kids) {
            if (k is String) {
                if (k.isBlank()) {
                    if (out.isNotEmpty()) out.last().text = out.last().text.trimEnd() + " "
                } else {
                    out.add(Raw(k, null, null, bg))
                }
            } else if (k is XNode && k.name == "span") {
                val isBg = bg || k.attrs["ttm:role"] == "x-bg"
                val b = k.attrs["begin"]
                val e = k.attrs["end"]
                if (hasTimedDesc(k)) collect(k, isBg, out)
                else if (b != null && e != null) out.add(Raw(textOf(k), time(b), time(e), isBg))
                else collect(k, isBg, out)
            }
        }
    }

    fun parse(xml: String, source: String): Lyrics? {
        val root = dom(xml) ?: return null
        val lines = ArrayList<LyricLine>()
        var firstAgent: String? = null
        var anyWord = false

        fun handleP(p: XNode) {
            val pStart = p.attrs["begin"]?.let { time(it) }
            val pEnd = p.attrs["end"]?.let { time(it) }
            val agent = p.attrs["ttm:agent"]
            if (agent != null && firstAgent == null) firstAgent = agent
            val raw = ArrayList<Raw>()
            collect(p, false, raw)
            val timed = raw.filter { it.start != null && it.end != null }
            var main: List<LyricToken> = timed.filter { !it.bg }.map { LyricToken(it.text, it.start!!, it.end!!, 0) }
            val bgTokens = timed.filter { it.bg }.map { LyricToken(it.text, it.start!!, it.end!!, 0) }
            if (timed.isEmpty()) {
                val txt = textOf(p).trim()
                if (txt.isNotEmpty() && pStart != null) main = LyricsTiming.estimate(txt, pStart, pEnd ?: (pStart + 4000L))
            } else {
                anyWord = true
            }
            val m = finishTokens(main)
            if (m.isNotEmpty()) {
                val second = agent != null && firstAgent != null && agent != firstAgent
                lines.add(LyricLine(m.first().startMs, m.last().endMs, m, false, second))
            }
            val b = finishTokens(bgTokens)
            if (b.isNotEmpty()) lines.add(LyricLine(b.first().startMs, b.last().endMs, b, true))
        }

        fun visit(n: XNode) {
            for (k in n.kids) {
                if (k !is XNode) continue
                if (k.name == "p") handleP(k) else visit(k)
            }
        }
        visit(root)
        if (lines.isEmpty()) return null
        lines.sortBy { it.startMs }
        return Lyrics(lines, if (anyWord) SyncType.WORD else SyncType.LINE, source)
    }
}

// ───────────────────────────── LRCLIB "lyricsfile" (small YAML subset) ─────────────────────────────
object LyricsFileParser {
    private class WB { var text = ""; var start: Long? = null; var end: Long? = null }
    private class LB { var text = ""; var start: Long? = null; var end: Long? = null; val words = ArrayList<WB>() }

    private fun unquote(v: String): String {
        if (v.length >= 2 && v.first() == '"' && v.last() == '"') {
            val b = v.substring(1, v.length - 1)
            val sb = StringBuilder()
            var i = 0
            while (i < b.length) {
                val c = b[i]
                if (c == '\\' && i + 1 < b.length) {
                    val n = b[i + 1]
                    when {
                        n == 'n' -> { sb.append('\n'); i += 2 }
                        n == 't' -> { sb.append('\t'); i += 2 }
                        n == 'u' && i + 6 <= b.length -> {
                            val code = b.substring(i + 2, i + 6).toIntOrNull(16)
                            if (code != null) sb.append(code.toChar()) else sb.append("\\u")
                            i += if (code != null) 6 else 2
                        }
                        else -> { sb.append(n); i += 2 }
                    }
                } else {
                    sb.append(c); i++
                }
            }
            return sb.toString()
        }
        if (v.length >= 2 && v.first() == '\'' && v.last() == '\'') return v.substring(1, v.length - 1).replace("''", "'")
        return v
    }

    fun parse(yaml: String, source: String): Lyrics? {
        val lines = ArrayList<LB>()
        var cur: LB? = null
        var word: WB? = null
        var linesIndent = -1
        var wordIndent = -1
        var inLines = false
        for (raw in yaml.replace("\r", "").split("\n")) {
            if (raw.isBlank() || raw.trimStart().startsWith("#")) continue
            val trimmed = raw.trimStart()
            val indent = raw.length - trimmed.length
            if (!inLines) {
                if (trimmed.startsWith("lines:")) inLines = true
                continue
            }
            if (indent == 0) break
            val isItem = trimmed.startsWith("- ")
            val body = if (isItem) trimmed.substring(2) else trimmed
            var target: Any? = null
            if (isItem) {
                if (linesIndent < 0) linesIndent = indent
                if (indent == linesIndent) {
                    val l = LB(); lines.add(l); cur = l; word = null; target = l
                } else {
                    val w = WB(); cur?.words?.add(w); word = w; wordIndent = indent; target = w
                }
            } else {
                target = if (word != null && indent > wordIndent) word else cur
            }
            val colon = body.indexOf(':')
            if (colon < 0) continue
            val key = body.substring(0, colon).trim()
            val value = body.substring(colon + 1).trim()
            when (val t = target) {
                is LB -> when (key) {
                    "text" -> t.text = unquote(value)
                    "start_ms" -> t.start = value.toDoubleOrNull()?.toLong()
                    "end_ms" -> t.end = value.toDoubleOrNull()?.toLong()
                }
                is WB -> when (key) {
                    "text" -> t.text = unquote(value)
                    "start_ms" -> t.start = value.toDoubleOrNull()?.toLong()
                    "end_ms" -> t.end = value.toDoubleOrNull()?.toLong()
                }
            }
        }
        val out = ArrayList<LyricLine>()
        var anyWord = false
        for (l in lines) {
            val st = l.start ?: continue
            val raw = l.words.mapNotNull { w ->
                val ws = w.start ?: return@mapNotNull null
                LyricToken(w.text, ws, w.end ?: ws, 0)
            }
            val toks: List<LyricToken> = if (raw.isNotEmpty()) {
                anyWord = true
                if (l.text.isNotBlank()) alignTokens(raw, l.text) else finishTokens(raw)
            } else {
                finishTokens(LyricsTiming.estimate(l.text, st, l.end ?: (st + 4000L)))
            }
            if (toks.isNotEmpty()) out.add(LyricLine(st, l.end ?: toks.last().endMs, toks))
        }
        if (out.isEmpty()) return null
        return Lyrics(out, if (anyWord) SyncType.WORD else SyncType.LINE, source)
    }
}

// ───────────────────────────── disk cache codec ─────────────────────────────
object LyricsCodec {
    fun encode(l: Lyrics): String {
        val root = JSONObject()
        root.put("v", 1)
        root.put("source", l.source)
        root.put("sync", l.sync.name)
        root.put("instr", l.isInstrumental)
        val arr = JSONArray()
        for (line in l.lines) {
            val o = JSONObject()
            o.put("s", line.startMs)
            o.put("e", line.endMs)
            o.put("bg", line.isBackground)
            o.put("end", line.alignEnd)
            val t = JSONArray()
            for (tk in line.tokens) {
                val a = JSONArray()
                a.put(tk.text); a.put(tk.startMs); a.put(tk.endMs); a.put(tk.wordIndex)
                t.put(a)
            }
            o.put("t", t)
            arr.put(o)
        }
        root.put("lines", arr)
        return root.toString()
    }

    fun decode(s: String): Lyrics? {
        return try {
            val root = JSONObject(s)
            val arr = root.getJSONArray("lines")
            val lines = ArrayList<LyricLine>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val t = o.getJSONArray("t")
                val toks = ArrayList<LyricToken>(t.length())
                for (j in 0 until t.length()) {
                    val a = t.getJSONArray(j)
                    toks.add(LyricToken(a.getString(0), a.getLong(1), a.getLong(2), a.getInt(3)))
                }
                lines.add(LyricLine(o.getLong("s"), o.getLong("e"), toks, o.optBoolean("bg"), o.optBoolean("end")))
            }
            Lyrics(lines, SyncType.valueOf(root.getString("sync")), root.optString("source"), root.optBoolean("instr"))
        } catch (e: Exception) {
            null
        }
    }
}
