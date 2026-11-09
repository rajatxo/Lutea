package com.rajatxo.lutea.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

// ═══════════════════════════════════════════════════════════════════════════════
// Data model
// ═══════════════════════════════════════════════════════════════════════════════

data class WordTimestamp(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

data class LyricsLine(
    val timeMs: Long,
    val text: String,
    val words: List<WordTimestamp>? = null,
)

sealed interface LyricsState {
    data object Loading : LyricsState
    data class Success(val lines: List<LyricsLine>, val isSynced: Boolean) : LyricsState
    data object NotFound : LyricsState
    data object Error : LyricsState
}

// ═══════════════════════════════════════════════════════════════════════════════
// Shared HTTP client + JSON
// ═══════════════════════════════════════════════════════════════════════════════

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

private val lyricsCache = mutableMapOf<Long, LyricsState>()

private val USER_AGENT = "Lutea (+https://github.com/rajatxo/Lutea)"

// ═══════════════════════════════════════════════════════════════════════════════
// Orchestrator — fetches from all providers in parallel, picks the best result
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Fetches lyrics from all providers in parallel (LRCLIB, YouLyPlus, BetterLyrics,
 * Paxsenix) and picks the best result — preferring word-level sync over line-level,
 * and synced over plain text. Caches per song ID.
 */
suspend fun fetchLyrics(
    songId: Long,
    title: String,
    artist: String,
    durationMs: Long,
    album: String? = null,
): LyricsState {
    lyricsCache[songId]?.let { return it }

    val state = try {
        coroutineScope {
            val deferred = listOf(
                async { fetchFromLrclib(title, artist, durationMs) },
                async { fetchFromYoulyplus(title, artist, durationMs, album) },
                async { fetchFromBetterLyrics(title, artist, durationMs, album) },
                async { fetchFromPaxsenix(title, artist, durationMs) },
            )
            val rawLyrics = deferred.awaitAll().filterNotNull()

            if (rawLyrics.isEmpty()) {
                LyricsState.NotFound
            } else {
                // Parse all and pick the best: word-sync > line-sync > plain
                val parsed = rawLyrics.map { parseLrc(it) }
                val best = parsed.maxByOrNull { (lines, isSynced) ->
                    val hasWords = lines.any { it.words != null }
                    when {
                        isSynced && hasWords -> 3
                        isSynced -> 2
                        else -> 1
                    }
                }
                if (best == null || best.first.isEmpty()) LyricsState.NotFound
                else LyricsState.Success(best.first, best.second)
            }
        }
    } catch (e: Exception) {
        LyricsState.Error
    }

    lyricsCache[songId] = state
    return state
}

// ═══════════════════════════════════════════════════════════════════════════════
// LRC parser — handles standard LRC + inline <mm:ss.xxx>word tags
// ═══════════════════════════════════════════════════════════════════════════════

private val LINE_TS_REGEX = Regex("""\[(\d+):(\d+)[.:](\d{1,3})\]""")
private val WORD_TS_REGEX = Regex("""<(\d+):(\d+)[.:](\d{1,3})>""")
private val METADATA_REGEX = Regex("""^\[[a-zA-Z]+:.*]$""")

fun parseLrc(raw: String): Pair<List<LyricsLine>, Boolean> {
    val lines = ArrayList<LyricsLine>()
    var hasAnyTimestamp = false

    for (rawLine in raw.lineSequence()) {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) continue
        if (METADATA_REGEX.matches(trimmed)) continue

        val lineTimestamps = LINE_TS_REGEX.findAll(trimmed).toList()
        if (lineTimestamps.isEmpty()) continue
        hasAnyTimestamp = true

        val lastMatch = lineTimestamps.last()
        val content = trimmed.substring(lastMatch.range.last + 1).trim()
            // Strip duet/background markers like {agent:v1} {bg}
            .replace(Regex("""\{[^}]*}"""), "")

        val words = parseWordTimestamps(content)
        val plainText = if (words != null) words.joinToString("") { it.text } else content

        if (plainText.isBlank() && words == null) continue

        for (match in lineTimestamps) {
            val timeMs = parseTimestampMs(
                match.groupValues[1], match.groupValues[2], match.groupValues[3]
            )
            lines.add(LyricsLine(timeMs, plainText, words))
        }
    }

    if (!hasAnyTimestamp) {
        val plainLines = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !METADATA_REGEX.matches(it) }
            .map { LyricsLine(0L, it, null) }
            .toList()
        return plainLines to false
    }

    lines.sortBy { it.timeMs }
    return lines to true
}

private fun parseWordTimestamps(content: String): List<WordTimestamp>? {
    val matches = WORD_TS_REGEX.findAll(content).toList()
    if (matches.isEmpty()) return null

    val words = ArrayList<WordTimestamp>(matches.size)
    for (i in matches.indices) {
        val match = matches[i]
        val startMs = parseTimestampMs(
            match.groupValues[1], match.groupValues[2], match.groupValues[3]
        )
        val textStart = match.range.last + 1
        val textEnd = if (i + 1 < matches.size) matches[i + 1].range.first else content.length
        val wordText = content.substring(textStart, textEnd)
        val endMs = if (i + 1 < matches.size) {
            val next = matches[i + 1]
            parseTimestampMs(next.groupValues[1], next.groupValues[2], next.groupValues[3])
        } else {
            startMs + 500L
        }
        if (wordText.isNotBlank()) {
            words.add(WordTimestamp(wordText, startMs, endMs))
        }
    }
    return words.ifEmpty { null }
}

private fun parseTimestampMs(minStr: String, secStr: String, fracStr: String): Long {
    val minutes = minStr.toLong()
    val seconds = secStr.toLong()
    val fracMs = when (fracStr.length) {
        1 -> fracStr.toLong() * 100
        2 -> fracStr.toLong() * 10
        3 -> fracStr.toLong()
        else -> fracStr.take(3).toLong()
    }
    return minutes * 60_000L + seconds * 1000L + fracMs
}

fun findActiveLineIndex(lines: List<LyricsLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return 0
    var lo = 0
    var hi = lines.lastIndex
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (lines[mid].timeMs <= positionMs) lo = mid else hi = mid - 1
    }
    return lo
}

// ═══════════════════════════════════════════════════════════════════════════════
// Timestamp formatting helpers
// ═══════════════════════════════════════════════════════════════════════════════

private fun formatLrcTs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val millis = ms % 1000
    return String.format(java.util.Locale.US, "[%02d:%02d.%03d]", min, sec, millis)
}

private fun formatWordTs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val millis = ms % 1000
    return String.format(java.util.Locale.US, "<%02d:%02d.%03d>", min, sec, millis)
}

// ═══════════════════════════════════════════════════════════════════════════════
// Provider 1: LRCLIB (free, keyless, MIT-licensed)
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
private data class LrclibResult(
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
)

private suspend fun fetchFromLrclib(
    title: String, artist: String, durationMs: Long
): String? = withContext(Dispatchers.IO) {
    try {
        val durationSec = (durationMs / 1000).toInt()
        val url = buildString {
            append("https://lrclib.net/api/search")
            append("?track_name=").append(URLEncoder.encode(title, "UTF-8"))
            append("&artist_name=").append(URLEncoder.encode(artist, "UTF-8"))
            if (durationSec > 0) append("&duration=").append(durationSec)
        }
        val req = okhttp3.Request.Builder().url(url)
            .header("User-Agent", USER_AGENT).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string() ?: return@use null
            val results = json.decodeFromString<List<LrclibResult>>(body)
            results.firstOrNull { !it.syncedLyrics.isNullOrBlank() }?.syncedLyrics
                ?: results.firstOrNull { !it.plainLyrics.isNullOrBlank() }?.plainLyrics
        }
    } catch (e: Exception) { null }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Provider 2: YouLyPlus (syllable-level JSON → inline LRC with word tags)
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
private data class YoulyplusResponse(
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
    val lyrics: List<YoulyplusItem>? = null,
)

@Serializable
private data class YoulyplusItem(
    val text: String? = null,
    val time: Long? = null,
    val syllabus: List<YoulyplusSyllable>? = null,
)

@Serializable
private data class YoulyplusSyllable(
    val text: String? = null,
    val time: Long? = null,
)

private suspend fun fetchFromYoulyplus(
    title: String, artist: String, durationMs: Long, album: String?
): String? = withContext(Dispatchers.IO) {
    try {
        val durationSec = (durationMs / 1000).toInt()
        val sb = StringBuilder("https://lyricsplus.prjktla.my.id/v2/lyrics/get")
            .append("?title=").append(URLEncoder.encode(title, "UTF-8"))
            .append("&artist=").append(URLEncoder.encode(artist, "UTF-8"))
            .append("&duration=").append(durationSec)
        if (!album.isNullOrBlank()) sb.append("&album=").append(URLEncoder.encode(album, "UTF-8"))

        val req = okhttp3.Request.Builder().url(sb.toString())
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string() ?: return@use null
            val parsed = json.decodeFromString<YoulyplusResponse>(body)
            when {
                !parsed.syncedLyrics.isNullOrBlank() -> parsed.syncedLyrics
                !parsed.lyrics.isNullOrEmpty() -> convertYoulyplusToLrc(parsed.lyrics)
                else -> parsed.plainLyrics
            }
        }
    } catch (e: Exception) { null }
}

private fun convertYoulyplusToLrc(items: List<YoulyplusItem>): String = buildString {
    for (item in items) {
        val lineTime = item.time ?: 0L
        val syllabus = item.syllabus
        if (!syllabus.isNullOrEmpty()) {
            append(formatLrcTs(lineTime))
            for (syl in syllabus) {
                append(formatWordTs(syl.time ?: 0L))
                append(syl.text ?: "")
            }
            append("\n")
        } else if (!item.text.isNullOrBlank()) {
            append(formatLrcTs(lineTime))
            append(item.text)
            append("\n")
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Provider 3: BetterLyrics (TTML → inline LRC with word tags)
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
private data class BetterLyricsResponse(val ttml: String? = null)

private suspend fun fetchFromBetterLyrics(
    title: String, artist: String, durationMs: Long, album: String?
): String? = withContext(Dispatchers.IO) {
    try {
        val durationSec = (durationMs / 1000).toInt()
        val sb = StringBuilder("https://lyrics-api.boidu.dev/getLyrics")
            .append("?s=").append(URLEncoder.encode(title, "UTF-8"))
            .append("&a=").append(URLEncoder.encode(artist, "UTF-8"))
        if (durationSec > 0) sb.append("&d=").append(durationSec)
        if (!album.isNullOrBlank()) sb.append("&al=").append(URLEncoder.encode(album, "UTF-8"))

        val req = okhttp3.Request.Builder().url(sb.toString())
            .header("User-Agent", USER_AGENT).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string() ?: return@use null
            val parsed = json.decodeFromString<BetterLyricsResponse>(body)
            parsed.ttml?.let { ttmlToLrc(it) }
        }
    } catch (e: Exception) { null }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Provider 4: Paxsenix (search → fetch, TTML or structured content → inline LRC)
// ═══════════════════════════════════════════════════════════════════════════════

@Serializable
private data class PaxsenixSearchResult(
    val id: String = "",
    val songName: String? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val duration: Int? = null, // milliseconds
)

@Serializable
private data class PaxsenixLyricsResponse(
    val type: String? = null,
    val ttmlContent: String? = null,
    val elrc: String? = null,
    val plain: String? = null,
    val content: List<PaxsenixContent>? = null,
)

@Serializable
private data class PaxsenixContent(
    val timestamp: Long = 0,
    val endtime: Long = 0,
    val text: List<PaxsenixText>? = null,
)

@Serializable
private data class PaxsenixText(
    val text: String = "",
    val timestamp: Long = 0,
    val endtime: Long = 0,
)

private suspend fun fetchFromPaxsenix(
    title: String, artist: String, durationMs: Long
): String? = withContext(Dispatchers.IO) {
    try {
        // Step 1: search
        val query = URLEncoder.encode("$title $artist", "UTF-8")
        val searchReq = okhttp3.Request.Builder()
            .url("https://lyrics.paxsenix.org/apple-music/search?q=$query")
            .header("User-Agent", USER_AGENT).build()

        val searchBody = httpClient.newCall(searchReq).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            resp.body?.string()
        } ?: return@withContext null

        val results = json.decodeFromString<List<PaxsenixSearchResult>>(searchBody)
        if (results.isEmpty()) return@withContext null

        // Pick best match by duration proximity
        val durationSec = durationMs / 1000
        val best = results
            .filter { it.id.isNotEmpty() }
            .minByOrNull { r ->
                val rSec = (r.duration ?: 0) / 1000
                Math.abs(rSec - durationSec)
            } ?: return@withContext null

        // Step 2: fetch lyrics for the best match
        val lyricsReq = okhttp3.Request.Builder()
            .url("https://lyrics.paxsenix.org/apple-music/lyrics?id=${best.id}")
            .header("User-Agent", USER_AGENT).build()

        val lyricsBody = httpClient.newCall(lyricsReq).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            resp.body?.string()
        } ?: return@withContext null

        val parsed = json.decodeFromString<PaxsenixLyricsResponse>(lyricsBody)
        // Priority: TTML > elrc > content[] > plain
        parsed.ttmlContent?.let { return@withContext ttmlToLrc(it) }
        parsed.elrc?.let { if (it.isNotBlank()) return@withContext it }
        parsed.content?.let { return@withContext convertPaxsenixContentToLrc(it) }
        parsed.plain
    } catch (e: Exception) { null }
}

private fun convertPaxsenixContentToLrc(content: List<PaxsenixContent>): String = buildString {
    for (line in content) {
        val texts = line.text
        if (!texts.isNullOrEmpty()) {
            append(formatLrcTs(line.timestamp))
            for (t in texts) {
                append(formatWordTs(t.timestamp))
                append(t.text)
            }
            append("\n")
        } else {
            val lineText = texts?.joinToString("") { it.text } ?: ""
            if (lineText.isNotBlank()) {
                append(formatLrcTs(line.timestamp))
                append(lineText)
                append("\n")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TTML parser — parses Apple Music TTML XML into inline LRC with word tags
// ═══════════════════════════════════════════════════════════════════════════════

private fun ttmlToLrc(ttml: String): String? {
    return try {
        val lines = parseTtmlDocument(ttml)
        if (lines.isEmpty()) null
        else lines.joinToString("\n") { line ->
            val sb = StringBuilder(formatLrcTs(line.timeMs))
            val words = line.words
            if (words != null) {
                for (word in words) {
                    sb.append(formatWordTs(word.startMs))
                    sb.append(word.text)
                }
            } else {
                sb.append(line.text)
            }
            sb.toString()
        }
    } catch (e: Exception) { null }
}

private fun parseTtmlDocument(ttml: String): List<LyricsLine> {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isValidating = false
    }
    val doc = factory.newDocumentBuilder().parse(ttml.byteInputStream())
    // Use getElementsByTagNameNS with "*" to match any namespace
    val pElements = doc.getElementsByTagNameNS("*", "p")
    val lines = ArrayList<LyricsLine>()

    for (i in 0 until pElements.length) {
        val p = pElements.item(i) as Element
        val beginAttr = p.getAttribute("begin")
        if (beginAttr.isNullOrBlank()) continue
        val lineStartMs = parseTtmlTime(beginAttr)

        val words = ArrayList<WordTimestamp>()
        val textBuilder = StringBuilder()
        val children = p.childNodes

        for (j in 0 until children.length) {
            val child = children.item(j)
            if (child.nodeType == Node.ELEMENT_NODE &&
                child.localName == "span") {
                val span = child as Element
                val spanText = span.textContent ?: ""
                if (spanText.isBlank()) continue
                val spanBegin = span.getAttribute("begin")
                val spanEnd = span.getAttribute("end")
                if (!spanBegin.isNullOrBlank() && !spanEnd.isNullOrBlank()) {
                    val wordStartMs = parseTtmlTime(spanBegin)
                    val wordEndMs = parseTtmlTime(spanEnd)
                    words.add(WordTimestamp(spanText.trim(), wordStartMs, wordEndMs))
                }
                textBuilder.append(spanText)
            }
        }

        val lineText = textBuilder.toString().replace(Regex("\\s+"), " ").trim()
        if (lineText.isNotBlank()) {
            lines.add(LyricsLine(lineStartMs, lineText, words.ifEmpty { null }))
        }
    }

    return lines.sortedBy { it.timeMs }
}

/** Parses a TTML time string: "1.234s", "01:23.456", "00:01:23.456", "1.234" */
private fun parseTtmlTime(timeStr: String): Long {
    val cleaned = timeStr.trim().removeSuffix("s").removeSuffix("t")
    val totalSec = if (cleaned.contains(":")) {
        val parts = cleaned.split(":")
        when (parts.size) {
            2 -> {
                val min = parts[0].toDoubleOrNull() ?: 0.0
                val sec = parts[1].toDoubleOrNull() ?: 0.0
                min * 60 + sec
            }
            3 -> {
                val h = parts[0].toDoubleOrNull() ?: 0.0
                val m = parts[1].toDoubleOrNull() ?: 0.0
                val s = parts[2].toDoubleOrNull() ?: 0.0
                h * 3600 + m * 60 + s
            }
            else -> cleaned.toDoubleOrNull() ?: 0.0
        }
    } else {
        cleaned.toDoubleOrNull() ?: 0.0
    }
    return (totalSec * 1000).toLong()
}
