package com.rajatxo.lutea.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// ──────────────────────────────────────────────────────────────────────────────
// Data model
// ──────────────────────────────────────────────────────────────────────────────

/** A single word with timing (milliseconds). */
data class WordTimestamp(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

/** A single lyrics line. [words] is non-null only for rich-sync (word-timed) LRC. */
data class LyricsLine(
    val timeMs: Long,
    val text: String,
    val words: List<WordTimestamp>? = null,
)

/** The state of the lyrics fetch + parse pipeline. */
sealed interface LyricsState {
    data object Loading : LyricsState
    data class Success(val lines: List<LyricsLine>, val isSynced: Boolean) : LyricsState
    data object NotFound : LyricsState
    data object Error : LyricsState
}

// ──────────────────────────────────────────────────────────────────────────────
// LRC parser
// ──────────────────────────────────────────────────────────────────────────────

private val LINE_TIMESTAMP_REGEX = Regex("""\[(\d+):(\d+)[.:](\d{1,3})\]""")
private val WORD_TIMESTAMP_REGEX = Regex("""<(\d+):(\d+)[.:](\d{1,3})>""")
private val METADATA_REGEX = Regex("""^\[[a-zA-Z]+:.*]$""")

/** Parses a raw LRC string into a list of [LyricsLine] + whether it's time-synced. */
fun parseLrc(raw: String): Pair<List<LyricsLine>, Boolean> {
    val lines = ArrayList<LyricsLine>()
    var hasAnyTimestamp = false

    for (rawLine in raw.lineSequence()) {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) continue

        // Skip metadata tags like [ti:...], [ar:...], [al:...], [by:...], [offset:...]
        if (METADATA_REGEX.matches(trimmed)) continue

        // Find all leading line timestamps: [mm:ss.xx][mm:ss.xx]...text
        val lineTimestamps = LINE_TIMESTAMP_REGEX.findAll(trimmed).toList()
        if (lineTimestamps.isEmpty()) {
            // No timestamp on this line — skip it (it's not synced content)
            continue
        }
        hasAnyTimestamp = true

        // The content is everything after the last line timestamp
        val lastMatch = lineTimestamps.last()
        val content = trimmed.substring(lastMatch.range.last + 1).trim()

        // Parse word-level timestamps from the content: <mm:ss.xx>word1 <mm:ss.xx>word2
        val words = parseWordTimestamps(content)
        val plainText = if (words != null) words.joinToString("") { it.text } else content

        if (plainText.isBlank() && words == null) continue

        // Each line timestamp creates a separate LyricsLine (LRC allows multiple timestamps
        // for the same text — the line appears at multiple points in the song)
        for (match in lineTimestamps) {
            val timeMs = parseTimestampMs(
                match.groupValues[1],
                match.groupValues[2],
                match.groupValues[3],
            )
            lines.add(LyricsLine(timeMs, plainText, words))
        }
    }

    // If no timestamps were found at all, treat as unsynced plain text
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

/** Parses `<mm:ss.xx>word` tags from rich-sync content. Returns null if no word tags. */
private fun parseWordTimestamps(content: String): List<WordTimestamp>? {
    val matches = WORD_TIMESTAMP_REGEX.findAll(content).toList()
    if (matches.isEmpty()) return null

    val words = ArrayList<WordTimestamp>(matches.size)
    for (i in matches.indices) {
        val match = matches[i]
        val startMs = parseTimestampMs(
            match.groupValues[1],
            match.groupValues[2],
            match.groupValues[3],
        )
        // The word text is everything between this tag's end and the next tag's start (or end of string)
        val textStart = match.range.last + 1
        val textEnd = if (i + 1 < matches.size) matches[i + 1].range.first else content.length
        val wordText = content.substring(textStart, textEnd)
        // The word's end time is the next word's start (or start + 500ms if it's the last)
        val endMs = if (i + 1 < matches.size) {
            val nextMatch = matches[i + 1]
            parseTimestampMs(nextMatch.groupValues[1], nextMatch.groupValues[2], nextMatch.groupValues[3])
        } else {
            startMs + 500L
        }
        if (wordText.isNotBlank()) {
            words.add(WordTimestamp(wordText, startMs, endMs))
        }
    }
    return words.ifEmpty { null }
}

/** Converts captured regex groups (minutes, seconds, fractional) to milliseconds. */
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

/** Binary-searches for the last line with [LyricsLine.timeMs] <= [positionMs]. */
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

// ──────────────────────────────────────────────────────────────────────────────
// LRCLIB client (free, keyless, MIT-licensed — safe for commercial use)
// ──────────────────────────────────────────────────────────────────────────────

@Serializable
private data class LrclibResult(
    val syncedLyrics: String? = null,
    val plainLyrics: String? = null,
)

private val json = Json { ignoreUnknownKeys = true }

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

/** In-memory cache so switching back to a song doesn't re-fetch. */
private val lyricsCache = mutableMapOf<Long, LyricsState>()

/**
 * Fetches lyrics from LRCLIB (https://lrclib.net) — free, no API key, MIT-licensed.
 * Prefers synced lyrics (with timestamps) over plain text. Caches the result per song ID.
 */
suspend fun fetchLyrics(songId: Long, title: String, artist: String, durationMs: Long): LyricsState {
    lyricsCache[songId]?.let { return it }

    val state = try {
        withContext(Dispatchers.IO) {
            val durationSec = (durationMs / 1000).toInt()
            val url = buildString {
                append("https://lrclib.net/api/search")
                append("?track_name=").append(URLEncoder.encode(title, "UTF-8"))
                append("&artist_name=").append(URLEncoder.encode(artist, "UTF-8"))
                if (durationSec > 0) append("&duration=").append(durationSec)
            }

            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Lutea (+https://github.com/rajatxo/Lutea)")
                .build()

            val response = httpClient.newCall(request).execute()
            response.use {
                if (!it.isSuccessful) return@use LyricsState.Error
                val body = it.body?.string() ?: return@use LyricsState.Error

                val results = json.decodeFromString<List<LrclibResult>>(body)
                // Prefer the first result with synced lyrics, then fall back to plain
                val best = results.firstOrNull { r -> !r.syncedLyrics.isNullOrBlank() }
                    ?: results.firstOrNull { r -> !r.plainLyrics.isNullOrBlank() }
                    ?: return@use LyricsState.NotFound

                val raw = best.syncedLyrics ?: best.plainLyrics!!
                val (lines, isSynced) = parseLrc(raw)
                if (lines.isEmpty()) LyricsState.NotFound
                else LyricsState.Success(lines, isSynced)
            }
        }
    } catch (e: Exception) {
        LyricsState.Error
    }

    lyricsCache[songId] = state
    return state
}
