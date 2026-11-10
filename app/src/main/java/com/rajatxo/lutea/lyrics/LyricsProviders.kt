package com.rajatxo.lutea.lyrics

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max

// ─────────────────────────────────────────────────────────────────────────────
//  Providers = the open lyrics APIs Mochy lists in its README:
//    LRCLIB · Better Lyrics API · LyricsPlus · SimpMusic Lyrics
//  Only public HTTP endpoints are used (no code from those projects). Plain
//  HttpURLConnection + org.json, so nothing to add to Gradle.
// ─────────────────────────────────────────────────────────────────────────────

class LyricsQuery(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    val videoId: String? = null,   // only used by SimpMusic when you happen to have a YouTube id
)

interface LyricsProvider {
    val id: String
    val displayName: String
    suspend fun fetch(q: LyricsQuery): Lyrics?
}

internal class HttpResult(val code: Int, val body: String)

internal fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

internal object LyricsHttp {
    const val USER_AGENT = "Lutea/1.0 (Android lyrics client)"

    suspend fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 8000): HttpResult =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", "application/json")
                for ((k, v) in headers) conn.setRequestProperty(k, v)
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                HttpResult(code, body)
            } finally {
                conn.disconnect()
            }
        }
}

/** Fuzzy title/artist/duration scoring used when an API only offers search results. */
internal object LyricsMatch {
    private val noise = Regex("""\(.*?\)|\[.*?]|feat\.?.*|ft\.?.*|- .*""", RegexOption.IGNORE_CASE)
    private val punct = Regex("[^\\p{L}\\p{N} ]")
    private val spaces = Regex("\\s+")

    private fun norm(s: String): String =
        spaces.replace(punct.replace(noise.replace(s, " ").lowercase(), " "), " ").trim()

    private fun jaccard(a: String, b: String): Double {
        val x = a.split(" ").filter { it.isNotEmpty() }.toSet()
        val y = b.split(" ").filter { it.isNotEmpty() }.toSet()
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return x.intersect(y).size.toDouble() / x.union(y).size.toDouble()
    }

    fun score(qTitle: String, qArtist: String, cTitle: String, cArtist: String, qDurMs: Long, cDurMs: Long): Double {
        var s = jaccard(norm(qTitle), norm(cTitle)) * 2.0 + jaccard(norm(qArtist), norm(cArtist))
        if (qDurMs > 0 && cDurMs > 0) {
            val d = abs(qDurMs - cDurMs) / 1000.0
            s -= when {
                d <= 2 -> 0.0
                d <= 5 -> 0.4
                d <= 10 -> 1.0
                else -> 2.0
            }
        }
        return s
    }
}

// ───────────────────────────── LRCLIB ─────────────────────────────
class LrcLibProvider : LyricsProvider {
    override val id = "lrclib"
    override val displayName = "LRCLIB"
    private val base = "https://lrclib.net/api"

    override suspend fun fetch(q: LyricsQuery): Lyrics? {
        val sb = StringBuilder("$base/get?track_name=${enc(q.title)}&artist_name=${enc(q.artist)}")
        if (!q.album.isNullOrBlank()) sb.append("&album_name=").append(enc(q.album))
        if (q.durationMs > 0) sb.append("&duration=").append(q.durationMs / 1000)
        val r = LyricsHttp.get(sb.toString())
        if (r.code == 200) toLyrics(JSONObject(r.body))?.let { return it }

        val s = LyricsHttp.get("$base/search?track_name=${enc(q.title)}&artist_name=${enc(q.artist)}")
        if (s.code != 200) return null
        val arr = JSONArray(s.body)
        var best: JSONObject? = null
        var bestScore = -99.0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            var sc = LyricsMatch.score(q.title, q.artist, o.optString("trackName"), o.optString("artistName"),
                q.durationMs, (o.optDouble("duration", 0.0) * 1000).toLong())
            if (o.str("syncedLyrics") != null || o.str("lyricsfile") != null) sc += 0.3
            if (sc > bestScore) { bestScore = sc; best = o }
        }
        val b = best ?: return null
        return if (bestScore >= 1.2) toLyrics(b) else null
    }

    private fun toLyrics(o: JSONObject): Lyrics? {
        if (o.optBoolean("instrumental", false)) return Lyrics(emptyList(), SyncType.NONE, displayName, true)
        o.str("lyricsfile")?.let { y -> LyricsFileParser.parse(y, displayName)?.let { return it } }   // word timing
        o.str("syncedLyrics")?.let { s -> LrcParser.parse(s, displayName)?.let { return it } }
        o.str("plainLyrics")?.let { p -> return PlainParser.parse(p, displayName) }
        return null
    }
}

// ───────────────────────────── Better Lyrics API ─────────────────────────────
/**
 * GET /getLyrics?s=&a=&al=&d=  →  {"ttml": "..."}  (syllable-timed TTML).
 * Cache hits are keyless; a *fresh* upstream fetch needs the API owner's X-API-Key, which they do
 * not hand out to other apps — so without your own key you only get songs that are already cached.
 */
class BetterLyricsProvider(private val apiKey: String? = null) : LyricsProvider {
    override val id = "betterlyrics"
    override val displayName = "Better Lyrics"
    private val bases = listOf("https://api.betterlyrics.org", "https://lyrics-api.boidu.dev")

    override suspend fun fetch(q: LyricsQuery): Lyrics? {
        for (base in bases) {
            val sb = StringBuilder("$base/getLyrics?s=${enc(q.title)}&a=${enc(q.artist)}")
            if (!q.album.isNullOrBlank()) sb.append("&al=").append(enc(q.album))
            if (q.durationMs > 0) sb.append("&d=").append(q.durationMs / 1000)
            val headers = if (apiKey.isNullOrBlank()) emptyMap() else mapOf("X-API-Key" to apiKey)
            val r = try {
                LyricsHttp.get(sb.toString(), headers)
            } catch (e: IOException) {
                continue
            }
            if (r.code == 404 || r.code == 401 || r.code == 403 || r.code == 429) return null
            if (r.code != 200) continue
            val ttml: String? = try { JSONObject(r.body).str("ttml") } catch (e: Exception) { null }
            if (ttml == null) return null
            return TtmlParser.parse(ttml, displayName)
        }
        return null
    }
}

// ───────────────────────────── LyricsPlus (YouLy+ backend) ─────────────────────────────
/** GET /v2/lyrics/get?title=&artist=&album=&duration=  →  {type, lyrics:[{time,duration,text,syllabus:[...]}]} */
class LyricsPlusProvider : LyricsProvider {
    override val id = "lyricsplus"
    override val displayName = "LyricsPlus"
    private val bases = listOf("https://lyricsplus.prjktla.my.id", "https://lyricsplus.prjktla.workers.dev")

    override suspend fun fetch(q: LyricsQuery): Lyrics? {
        for (base in bases) {
            val sb = StringBuilder("$base/v2/lyrics/get?title=${enc(q.title)}&artist=${enc(q.artist)}")
            if (!q.album.isNullOrBlank()) sb.append("&album=").append(enc(q.album))
            if (q.durationMs > 0) sb.append("&duration=").append(q.durationMs / 1000)
            val r = try {
                LyricsHttp.get(sb.toString())
            } catch (e: IOException) {
                continue
            }
            if (r.code == 404 || r.code == 400) return null
            if (r.code != 200) continue
            return try { parse(r.body) } catch (e: Exception) { null }
        }
        return null
    }

    private fun parse(body: String): Lyrics? {
        val root = JSONObject(body)
        if (root.optString("type").equals("None", ignoreCase = true)) return null
        val arr = root.optJSONArray("lyrics") ?: return null
        val lines = ArrayList<LyricLine>()
        var anyWord = false
        var firstSinger: String? = null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val start = o.optLong("time")
            val dur = o.optLong("duration")
            val text = o.optString("text")
            val singer = o.optJSONObject("element")?.optString("singer").orEmpty()
            if (singer.isNotEmpty() && firstSinger == null) firstSinger = singer
            val second = singer.isNotEmpty() && firstSinger != null && singer != firstSinger
            val syl = o.optJSONArray("syllabus")
            if (syl != null && syl.length() > 0) {
                val main = ArrayList<LyricToken>()
                val bg = ArrayList<LyricToken>()
                for (j in 0 until syl.length()) {
                    val s = syl.optJSONObject(j) ?: continue
                    var st = s.optLong("time")
                    if (start > 0 && st < start - 1000) st += start   // tolerate line-relative times
                    val tk = LyricToken(s.optString("text"), st, st + s.optLong("duration"), 0)
                    if (s.optBoolean("isBackground", false)) bg.add(tk) else main.add(tk)
                }
                val mt = if (text.isNotBlank()) alignTokens(main, text) else finishTokens(main)
                if (mt.isNotEmpty()) {
                    anyWord = true
                    lines.add(LyricLine(mt.first().startMs, max(mt.last().endMs, start + dur), mt, false, second))
                }
                val bt = finishTokens(bg)
                if (bt.isNotEmpty()) lines.add(LyricLine(bt.first().startMs, bt.last().endMs, bt, true))
            } else if (text.isNotBlank()) {
                val toks = finishTokens(LyricsTiming.estimate(text, start, start + max(dur, 1500L)))
                if (toks.isNotEmpty()) lines.add(LyricLine(start, start + dur, toks, false, second))
            }
        }
        if (lines.isEmpty()) return null
        lines.sortBy { it.startMs }
        return Lyrics(lines, if (anyWord) SyncType.WORD else SyncType.LINE, displayName)
    }
}

// ───────────────────────────── SimpMusic Lyrics ─────────────────────────────
/**
 * GET /v1/{videoId} or /v1/search?q=  →  {"type":"success","data":[{songTitle,artistName,durationSeconds,
 * plainLyric,syncedLyrics,richSyncLyrics,...}]}.  Public GETs need no auth (HMAC is only for writes).
 * Limit: 30 requests/min/IP.  richSyncLyrics is parsed best-effort as enhanced LRC; if its format
 * differs it silently falls back to syncedLyrics.
 */
class SimpMusicProvider : LyricsProvider {
    override val id = "simpmusic"
    override val displayName = "SimpMusic"
    private val base = "https://api-lyrics.simpmusic.org/v1"

    override suspend fun fetch(q: LyricsQuery): Lyrics? {
        val byId = !q.videoId.isNullOrBlank()
        val url = if (byId) "$base/${enc(q.videoId!!)}" else "$base/search?q=${enc(q.title + " " + q.artist)}&limit=5"
        val r = LyricsHttp.get(url)
        if (r.code != 200) return null
        val data = JSONObject(r.body).optJSONArray("data") ?: return null
        var best: JSONObject? = null
        var bestScore = -99.0
        for (i in 0 until data.length()) {
            val o = data.optJSONObject(i) ?: continue
            val sc = if (byId) 10.0 else LyricsMatch.score(q.title, q.artist, o.optString("songTitle"),
                o.optString("artistName"), q.durationMs, o.optLong("durationSeconds") * 1000)
            if (sc > bestScore) { bestScore = sc; best = o }
        }
        val o = best ?: return null
        if (!byId && bestScore < 1.2) return null
        o.str("richSyncLyrics")?.let { rs ->
            LrcParser.parse(rs, displayName)?.takeIf { it.sync == SyncType.WORD }?.let { return it }
        }
        o.str("syncedLyrics")?.let { s -> LrcParser.parse(s, displayName)?.let { return it } }
        o.str("plainLyric")?.let { p -> return PlainParser.parse(p, displayName) }
        return null
    }
}

// ───────────────────────────── repository ─────────────────────────────
/**
 * Asks every provider at once. Emits the first usable result immediately (so lyrics show fast) and
 * upgrades it if a better one (word-synced) arrives; stops waiting as soon as a WORD result lands.
 * Results are cached in memory and on disk (7 days), so each song hits the network at most once.
 *
 *   val repo = LyricsRepository(context)
 *   repo.load(LyricsQuery(title, artist, album, durationMs)).collect { lyrics = it }
 */
class LyricsRepository(
    context: Context,
    private val providers: List<LyricsProvider> = defaultProviders(),
    private val timeoutMs: Long = 7000L,
) {
    companion object {
        fun defaultProviders(betterLyricsApiKey: String? = null): List<LyricsProvider> =
            listOf(LyricsPlusProvider(), BetterLyricsProvider(betterLyricsApiKey), LrcLibProvider(), SimpMusicProvider())
    }

    private val dir = File(context.applicationContext.cacheDir, "lutea_lyrics").apply { mkdirs() }
    private val memory = LruCache<String, Lyrics>(64)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun rank(l: Lyrics?): Int = when {
        l == null -> 0
        l.sync == SyncType.WORD -> 3
        l.sync == SyncType.LINE -> 2
        else -> 1
    }

    private fun keyOf(q: LyricsQuery): String =
        listOf(q.title.trim().lowercase(), q.artist.trim().lowercase(), (q.durationMs / 2000).toString()).joinToString("|")

    private fun fileFor(key: String): File {
        val h = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$h.json")
    }

    private fun readDisk(key: String): Lyrics? {
        val f = fileFor(key)
        if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 7L * 24 * 3600 * 1000) return null
        return try { LyricsCodec.decode(f.readText()) } catch (e: Exception) { null }
    }

    private fun writeDisk(key: String, l: Lyrics) {
        try { fileFor(key).writeText(LyricsCodec.encode(l)) } catch (e: Exception) { /* cache is best-effort */ }
    }

    private suspend fun runProvider(p: LyricsProvider, q: LyricsQuery): Lyrics? =
        try {
            withTimeoutOrNull(timeoutMs) { p.fetch(q) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    fun load(q: LyricsQuery, forceRefresh: Boolean = false): Flow<Lyrics?> = flow {
        val key = keyOf(q)
        if (!forceRefresh) {
            val cached = memory.get(key) ?: readDisk(key)
            if (cached != null) {
                memory.put(key, cached)
                emit(cached)
                return@flow
            }
        }
        val channel = Channel<Pair<Int, Lyrics?>>(Channel.UNLIMITED)
        val jobs = providers.mapIndexed { i, p -> scope.launch { channel.trySend(i to runProvider(p, q)) } }
        var best: Lyrics? = null
        try {
            for (n in providers.indices) {
                val r = channel.receive().second
                if (r != null && rank(r) > rank(best)) {
                    best = r
                    emit(r)
                    if (r.sync == SyncType.WORD) break
                }
            }
        } finally {
            jobs.forEach { it.cancel() }
        }
        val b = best
        if (b != null) {
            memory.put(key, b)
            writeDisk(key, b)
        } else {
            emit(null)
        }
    }
}
