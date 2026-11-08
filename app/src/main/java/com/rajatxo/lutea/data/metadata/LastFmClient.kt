package com.rajatxo.lutea.data.metadata

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * LastFmClient — free music metadata API.
 *
 * Provides:
 *  - Top tracks (ranked by real listener data from millions of users)
 *  - Artist images (real photos in multiple sizes)
 *  - Artist biographies (written by Last.fm editors)
 *  - Similar artists (based on real listening patterns)
 *  - Listener/play counts (social proof)
 *
 * Free quota: 5 requests/second (no daily limit).
 * API key required (free, instant from last.fm/api/account/create).
 * Play Store safe: 100% free for commercial use.
 */
object LastFmClient {

    private const val TAG = "LastFmClient"
    private const val API_KEY = "8cc44114b56203033a82db9a73d039f3"
    private const val BASE = "http://ws.audioscrobbler.com/2.0"
    private const val TIMEOUT = 8000

    data class ArtistInfo(
        val name: String,
        val imageUrl: String?,       // mega/extralarge image
        val bio: String?,            // biography (Last.fm editor-written)
        val listeners: Long?,        // e.g. "1234567" listeners
        val playCount: Long?,
        val genre: String?,          // top tag
    )

    data class TopTrack(
        val rank: Int,               // 1 = most popular
        val title: String,
        val playCount: Long?,
    )

    data class SimilarArtist(
        val name: String,
        val imageUrl: String?,
    )

    private val cache = HashMap<String, Any?>()

    /**
     * Get artist info — bio, image, listener count.
     */
    suspend fun getArtistInfo(artistName: String): ArtistInfo? = withContext(Dispatchers.IO) {
        val cleanName = ArtistImageCache.sanitizeForSearch(artistName)
        if (cleanName.isBlank()) return@withContext null
        val cacheKey = "lfm:info:$cleanName"
        if (cache.containsKey(cacheKey)) {
            @Suppress("UNCHECKED_CAST")
            return@withContext cache[cacheKey] as ArtistInfo?
        }
        try {
            val url = "$BASE/?method=artist.getinfo&artist=" + encode(cleanName) +
                "&api_key=$API_KEY&format=json"
            Log.d(TAG, "getArtistInfo: $cleanName")
            val json = httpGetJson(url) ?: return@withContext null

            // Check for error
            if (json.has("error")) {
                Log.w(TAG, "Last.fm error: ${json.optString("message")}")
                cache[cacheKey] = null
                return@withContext null
            }

            val artist = json.optJSONObject("artist") ?: return@withContext null
            val bio = artist.optJSONObject("bio")
            val bioText = bio?.optString("content")?.takeIf { it.isNotBlank() }
                ?: bio?.optString("summary")?.takeIf { it.isNotBlank() }

            // Get the largest image available
            val images = artist.optJSONArray("image")
            var imageUrl: String? = null
            if (images != null) {
                for (i in 0 until images.length()) {
                    val img = images.optJSONObject(i)
                    val size = img?.optString("size")
                    val src = img?.optString("#text")?.takeIf { it.isNotBlank() }
                    if (src != null) {
                        // Prefer mega > extralarge > large > medium
                        if (size == "mega" || size == "extralarge" || size == "large" || imageUrl == null) {
                            imageUrl = src
                        }
                    }
                }
            }

            val stats = artist.optJSONObject("stats")
            val listeners = stats?.optString("listeners")?.replace(",", "")?.toLongOrNull()
            val playCount = stats?.optString("playcount")?.replace(",", "")?.toLongOrNull()

            // Top tag as genre
            val tags = artist.optJSONObject("tags")
            val genre = tags?.optJSONArray("tag")?.optJSONObject(0)?.optString("name")

            val info = ArtistInfo(
                name = artist.optString("name").ifBlank { cleanName },
                imageUrl = imageUrl,
                bio = bioText,
                listeners = listeners,
                playCount = playCount,
                genre = genre,
            )
            cache[cacheKey] = info
            Log.d(TAG, "Found: ${info.name}, image=${info.imageUrl != null}, bio=${info.bio?.length ?: 0} chars")
            info
        } catch (e: Exception) {
            Log.w(TAG, "getArtistInfo failed for '$cleanName'", e)
            cache[cacheKey] = null
            null
        }
    }

    /**
     * Get top tracks for an artist — ranked by real listener data.
     * Returns list of (rank, title, playCount) in popularity order.
     */
    suspend fun getTopTracks(artistName: String, limit: Int = 50): List<TopTrack>? = withContext(Dispatchers.IO) {
        val cleanName = ArtistImageCache.sanitizeForSearch(artistName)
        if (cleanName.isBlank()) return@withContext null
        val cacheKey = "lfm:top:$cleanName"
        if (cache.containsKey(cacheKey)) {
            @Suppress("UNCHECKED_CAST")
            return@withContext cache[cacheKey] as List<TopTrack>?
        }
        try {
            val url = "$BASE/?method=artist.gettoptracks&artist=" + encode(cleanName) +
                "&api_key=$API_KEY&format=json&limit=$limit"
            Log.d(TAG, "getTopTracks: $cleanName (limit $limit)")
            val json = httpGetJson(url) ?: return@withContext null

            if (json.has("error")) {
                Log.w(TAG, "Last.fm error: ${json.optString("message")}")
                cache[cacheKey] = null
                return@withContext null
            }

            val topTracks = json.optJSONObject("toptracks")
            val trackArray = topTracks?.optJSONArray("track") ?: return@withContext null

            val list = (0 until trackArray.length()).mapNotNull { i ->
                val track = trackArray.optJSONObject(i) ?: return@mapNotNull null
                val title = track.optString("name").ifBlank { return@mapNotNull null }
                val playCount = track.optLong("playcount", 0).takeIf { it > 0 }
                TopTrack(rank = i + 1, title = title, playCount = playCount)
            }
            cache[cacheKey] = list
            Log.d(TAG, "Found ${list.size} top tracks for '$cleanName'")
            list
        } catch (e: Exception) {
            Log.w(TAG, "getTopTracks failed for '$cleanName'", e)
            cache[cacheKey] = null
            null
        }
    }

    /**
     * Get similar artists — based on real listening patterns.
     */
    suspend fun getSimilarArtists(artistName: String, limit: Int = 10): List<SimilarArtist>? = withContext(Dispatchers.IO) {
        val cleanName = ArtistImageCache.sanitizeForSearch(artistName)
        if (cleanName.isBlank()) return@withContext null
        val cacheKey = "lfm:similar:$cleanName"
        if (cache.containsKey(cacheKey)) {
            @Suppress("UNCHECKED_CAST")
            return@withContext cache[cacheKey] as List<SimilarArtist>?
        }
        try {
            val url = "$BASE/?method=artist.getsimilar&artist=" + encode(cleanName) +
                "&api_key=$API_KEY&format=json&limit=$limit"
            Log.d(TAG, "getSimilarArtists: $cleanName")
            val json = httpGetJson(url) ?: return@withContext null

            if (json.has("error")) {
                cache[cacheKey] = null
                return@withContext null
            }

            val similar = json.optJSONObject("similarartists")
            val artistArray = similar?.optJSONArray("artist") ?: return@withContext null

            val list = (0 until artistArray.length()).mapNotNull { i ->
                val a = artistArray.optJSONObject(i) ?: return@mapNotNull null
                val name = a.optString("name").ifBlank { return@mapNotNull null }
                val images = a.optJSONArray("image")
                var img: String? = null
                if (images != null) {
                    for (j in 0 until images.length()) {
                        val im = images.optJSONObject(j)
                        val src = im?.optString("#text")?.takeIf { it.isNotBlank() }
                        if (src != null) img = src
                    }
                }
                SimilarArtist(name = name, imageUrl = img)
            }
            cache[cacheKey] = list
            Log.d(TAG, "Found ${list.size} similar artists for '$cleanName'")
            list
        } catch (e: Exception) {
            Log.w(TAG, "getSimilarArtists failed for '$cleanName'", e)
            cache[cacheKey] = null
            null
        }
    }

    // ── internals ──

    private fun httpGetJson(urlString: String): JSONObject? {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Coral/1.0 ( rajat@lutea.local )")
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            instanceFollowRedirects = true
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

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
