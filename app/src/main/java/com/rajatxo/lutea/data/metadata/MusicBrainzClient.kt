package com.rajatxo.lutea.data.metadata

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * MusicBrainzClient — a minimal MusicBrainz + Cover Art Archive client.
 *
 * Uses only [HttpURLConnection] (no external HTTP/JSON libraries — [JSONObject]
 * ships with Android). All public methods are [suspend] and run on
 * [Dispatchers.IO]. Every call returns `null` on any failure so callers
 * never have to catch — this thing is designed to be best-effort metadata
 * enrichment on top of local MediaStore tags.
 *
 * Endpoints used:
 *  - Artist search:       GET https://musicbrainz.org/ws/2/artist/?query=artist:NAME&fmt=json&limit=1
 *  - Artist url-rels:     GET https://musicbrainz.org/ws/2/artist/MBID?inc=url-rels&fmt=json
 *  - Release search:      GET https://musicbrainz.org/ws/2/release/?query=release:ALBUM AND artist:ARTIST&fmt=json&limit=1
 *  - Cover Art Archive:   GET https://coverartarchive.org/release/MBID/front (302 → image URL)
 *
 * Per MusicBrainz etiquette, every request carries a custom User-Agent that
 * identifies the app + a contact address.
 *
 * A simple in-memory cache ([HashMap] guarded by a [Mutex]) keeps repeated
 * lookups cheap — MusicBrainz is rate-limited to ~1 req/sec.
 */
object MusicBrainzClient {

    private const val TAG = "MusicBrainzClient"
    private const val USER_AGENT = "Lutea/1.0 ( rajat@lutea.local )"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000

    // ───────────────────────────── public data classes ─────────────────────────────

    /**
     * Info about an artist. [biography] is the Wikipedia summary extract when
     * one is reachable (otherwise the raw Wikipedia URL, otherwise null).
     * [imageUrl] is reserved for future MB image lookups — currently null.
     */
    data class ArtistInfo(
        val name: String,
        val mbid: String,
        val biography: String?,
        val imageUrl: String?,
    )

    /**
     * Info about a release / album. [artUrl] is the Cover Art Archive "front"
     * image URL when one is published, otherwise null. [year] is the
     * first-release year if MusicBrainz returns one, otherwise null.
     */
    data class AlbumInfo(
        val title: String,
        val mbid: String,
        val artUrl: String?,
        val year: Int?,
    )

    // ───────────────────────────── in-memory cache ─────────────────────────────

    private val cache = HashMap<String, Any?>()
    private val cacheMutex = Mutex()

    private suspend fun <T> cached(key: String, loader: suspend () -> T?): T? {
        cacheMutex.withLock {
            @Suppress("UNCHECKED_CAST")
            if (cache.containsKey(key)) return cache[key] as T?
        }
        val value = loader()
        cacheMutex.withLock { cache[key] = value }
        return value
    }

    // ───────────────────────────── public API ─────────────────────────────

    /**
     * Search MusicBrainz for an artist by name. Returns a minimal
     * [ArtistInfo] (no biography) on hit, or null on miss / error.
     */
    suspend fun searchArtist(name: String): ArtistInfo? = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext null
        cached("artist:search:$name") {
            runCatching {
                val url = "https://musicbrainz.org/ws/2/artist/?query=artist:" +
                    encode(name) + "&fmt=json&limit=1"
                val json = httpGetJson(url) ?: return@runCatching null
                val artists = json.optJSONArray("artists") ?: return@runCatching null
                if (artists.length() == 0) return@runCatching null
                val first = artists.optJSONObject(0) ?: return@runCatching null
                ArtistInfo(
                    name = first.optString("name").ifBlank { name },
                    mbid = first.optString("id"),
                    biography = null,
                    imageUrl = null,
                )
            }.getOrElse {
                Log.w(TAG, "searchArtist failed for '$name'", it); null
            }
        }
    }

    /**
     * Fetch full artist info including the Wikipedia biography. Resolves the
     * Wikipedia URL via `inc=url-rels`, then pulls the page summary extract
     * via the Wikipedia REST API. Returns null if the MBID can't be found
     * or any step fails.
     */
    suspend fun getArtistInfo(mbid: String): ArtistInfo? = withContext(Dispatchers.IO) {
        if (mbid.isBlank()) return@withContext null
        cached("artist:info:$mbid") {
            runCatching {
                val url = "https://musicbrainz.org/ws/2/artist/" +
                    mbid + "?inc=url-rels&fmt=json"
                val json = httpGetJson(url) ?: return@runCatching null
                val name = json.optString("name").ifBlank { "Unknown Artist" }

                // Walk the url-rels for the Wikipedia relation.
                val relations = json.optJSONArray("relations") ?: org.json.JSONArray()
                var wikiUrl: String? = null
                for (i in 0 until relations.length()) {
                    val rel = relations.optJSONObject(i) ?: continue
                    if (rel.optString("type") == "wikipedia") {
                        val target = rel.optJSONObject("url") ?: continue
                        wikiUrl = target.optString("resource").takeIf { it.isNotBlank() }
                        if (wikiUrl != null) break
                    }
                }

                val bio = wikiUrl?.let { fetchWikipediaExtract(it) } ?: wikiUrl
                ArtistInfo(
                    name = name,
                    mbid = mbid,
                    biography = bio,
                    imageUrl = null,
                )
            }.getOrElse {
                Log.w(TAG, "getArtistInfo failed for '$mbid'", it); null
            }
        }
    }

    /**
     * Convenience — search by name then fetch full info for the first hit.
     * Also tries to fetch an artist image from Wikipedia.
     * Returns null if either step fails.
     */
    suspend fun lookupArtist(name: String): ArtistInfo? {
        val hit = searchArtist(name) ?: return null
        val info = getArtistInfo(hit.mbid) ?: hit
        // Try to get artist image from Wikipedia
        val imageUrl = info.biography?.let { fetchArtistImage(name) }
        return info.copy(imageUrl = imageUrl)
    }

    /**
     * Search MusicBrainz for a release (album) by title + artist. Returns
     * an [AlbumInfo] (without art) on hit, null on miss / error.
     */
    suspend fun searchAlbum(albumName: String, artistName: String): AlbumInfo? = withContext(Dispatchers.IO) {
        if (albumName.isBlank()) return@withContext null
        cached("album:search:$albumName:$artistName") {
            runCatching {
                val query = "release:" + encode(albumName) +
                    if (artistName.isNotBlank()) " AND artist:" + encode(artistName) else ""
                val url = "https://musicbrainz.org/ws/2/release/?query=" +
                    query + "&fmt=json&limit=1"
                val json = httpGetJson(url) ?: return@runCatching null
                val releases = json.optJSONArray("releases") ?: return@runCatching null
                if (releases.length() == 0) return@runCatching null
                val first = releases.optJSONObject(0) ?: return@runCatching null
                AlbumInfo(
                    title = first.optString("title").ifBlank { albumName },
                    mbid = first.optString("id"),
                    artUrl = null,
                    year = parseReleaseYear(first),
                )
            }.getOrElse {
                Log.w(TAG, "searchAlbum failed for '$albumName' / '$artistName'", it); null
            }
        }
    }

    /**
     * Fetch the Cover Art Archive "front" image URL for a release MBID.
     * The Cover Art Archive responds with a 302 redirect to the actual image
     * URL — we follow it and return the final resolved URL as a String.
     * Returns null if no cover art is published or the request fails.
     */
    suspend fun getAlbumArt(releaseMbid: String): String? = withContext(Dispatchers.IO) {
        if (releaseMbid.isBlank()) return@withContext null
        cached("album:art:$releaseMbid") {
            runCatching {
                val url = "https://coverartarchive.org/release/$releaseMbid/front"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", USER_AGENT)
                    instanceFollowRedirects = true
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                }
                try {
                    val code = conn.responseCode
                    if (code in 200..299) {
                        // The final URL after redirects is the actual image URL.
                        conn.url?.toString()
                    } else {
                        Log.w(TAG, "getAlbumArt: HTTP $code for $releaseMbid")
                        null
                    }
                } finally {
                    conn.disconnect()
                }
            }.getOrElse {
                Log.w(TAG, "getAlbumArt failed for '$releaseMbid'", it); null
            }
        }
    }

    /**
     * Convenience — search for an album, then resolve its Cover Art Archive
     * URL. Returns the [AlbumInfo] with [AlbumInfo.artUrl] populated when
     * cover art exists.
     */
    suspend fun lookupAlbum(albumName: String, artistName: String): AlbumInfo? {
        val hit = searchAlbum(albumName, artistName) ?: return null
        val art = getAlbumArt(hit.mbid)
        return hit.copy(artUrl = art)
    }

    // ───────────────────────────── internals ─────────────────────────────

    private fun httpGetJson(urlString: String): JSONObject? {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }
        return try {
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "HTTP $code for $urlString")
                return null
            }
            conn.inputStream.bufferedReader().use { it.readText() }
                .takeIf { it.isNotBlank() }
                ?.let { JSONObject(it) }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Pull the plain-text summary extract for a Wikipedia page URL.
     * Uses the Wikipedia REST API:
     *   https://en.wikipedia.org/api/rest_v1/page/summary/<title>
     * The title is derived from the URL's last path segment.
     */
    private fun fetchWikipediaExtract(wikipediaUrl: String): String? {
        val title = wikipediaUrl.substringAfterLast("/").substringBefore("#").substringBefore("?")
        if (title.isBlank()) return null
        val apiUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/" + encode(title)
        return runCatching {
            httpGetJson(apiUrl)?.optString("extract")?.takeIf { it.isNotBlank() }
        }.getOrElse {
            Log.w(TAG, "fetchWikipediaExtract failed for '$wikipediaUrl'", it); null
        }
    }

    /**
     * Fetch artist image URL from Wikipedia REST API.
     * The page summary endpoint returns a "thumbnail" object with a "source" URL.
     */
    private fun fetchArtistImage(artistName: String): String? {
        val title = artistName.replace(" ", "_")
        val apiUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/" + encode(title)
        return runCatching {
            val json = httpGetJson(apiUrl) ?: return null
            val thumbnail = json.optJSONObject("thumbnail") ?: return null
            val source = thumbnail.optString("source")
            // Get the full-size version by removing the thumbnail size suffix
            source.takeIf { it.isNotBlank() }?.replace("/thumb/", "/")
                ?.replace("/\\d+px-".toRegex(), "/")
                ?.replace("^https?://upload\\.wikimedia\\.org/wikipedia/commons/thumb/".toRegex(),
                    "https://upload.wikimedia.org/wikipedia/commons/")
        }.getOrElse {
            Log.w(TAG, "fetchArtistImage failed for '$artistName'", it); null
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun parseReleaseYear(json: JSONObject): Int? {
        // MusicBrainz release-search returns a "date" string like "2023-08-11"
        // or "2023" or "". We take the first 4 chars as the year when numeric.
        val date = json.optString("date").trim()
        if (date.length >= 4) {
            return date.substring(0, 4).toIntOrNull()
        }
        // Some responses use a "first-release-date" key.
        val firstRelease = json.optString("first-release-date").trim()
        if (firstRelease.length >= 4) {
            return firstRelease.substring(0, 4).toIntOrNull()
        }
        return null
    }
}
