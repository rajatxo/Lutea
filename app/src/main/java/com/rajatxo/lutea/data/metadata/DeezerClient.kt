package com.rajatxo.lutea.data.metadata

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * DeezerClient — free music metadata API (no key, no registration).
 *
 * Deezer provides artist photos (picture_xl = 1000x1000), top tracks,
 * albums, similar artists, and fan counts — all free, no API key.
 * Rate limit: 50 requests per 5 seconds (very generous).
 *
 * Endpoints:
 *  - Search artist:   https://api.deezer.com/search/artist?q=NAME
 *  - Top tracks:      https://api.deezer.com/artist/{id}/top?limit=15
 *  - Albums:          https://api.deezer.com/artist/{id}/albums
 *  - Similar artists:  https://api.deezer.com/artist/{id}/related
 *
 * All methods suspend, Dispatchers.IO, never crash.
 */
object DeezerClient {

    private const val TAG = "DeezerClient"
    private const val BASE = "https://api.deezer.com"
    private const val TIMEOUT = 8000

    data class ArtistData(
        val name: String,
        val imageUrl: String?,       // picture_xl (1000x1000)
        val fanCount: Int?,
        val albumCount: Int?,
        val deezerId: Long?,
        val genre: String?,
    )

    data class TopTrack(
        val title: String,
        val duration: Int,           // seconds
        val albumName: String,
        val albumArtUrl: String?,
    )

    data class AlbumData(
        val title: String,
        val artUrl: String?,         // cover_xl
        val year: String?,
        val recordType: String?,     // "album", "single", "ep"
    )

    data class SimilarArtist(
        val name: String,
        val imageUrl: String?,
    )

    private val cache = HashMap<String, Any?>()

    /**
     * Search for an artist by name. Returns ArtistData with a high-res
     * image URL (picture_xl) or null if not found.
     */
    suspend fun searchArtist(name: String): ArtistData? = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext null
        val cacheKey = "deezer:artist:$name"
        if (cache.containsKey(cacheKey)) {
            @Suppress("UNCHECKED_CAST")
            return@withContext cache[cacheKey] as ArtistData?
        }
        try {
            val url = "$BASE/search/artist?q=" + encode(name)
            val json = httpGetJson(url) ?: return@withContext null
            val data = json.optJSONArray("data") ?: return@withContext null
            if (data.length() == 0) return@withContext null
            val a = data.optJSONObject(0) ?: return@withContext null

            val result = ArtistData(
                name = a.optString("name").ifBlank { name },
                imageUrl = a.optString("picture_xl").takeIf { it.isNotBlank() && it != "null" }
                    ?: a.optString("picture_big").takeIf { it.isNotBlank() && it != "null" }
                    ?: a.optString("picture_medium").takeIf { it.isNotBlank() && it != "null" },
                fanCount = a.optInt("nb_fan", 0).takeIf { it > 0 },
                albumCount = a.optInt("nb_album", 0).takeIf { it > 0 },
                deezerId = a.optLong("id", 0).takeIf { it > 0 },
                genre = null,
            )
            cache[cacheKey] = result
            Log.d(TAG, "Found artist: ${result.name}, image: ${result.imageUrl != null}")
            result
        } catch (e: Exception) {
            Log.w(TAG, "searchArtist failed for '$name'", e)
            cache[cacheKey] = null
            null
        }
    }

    /**
     * Get top tracks for an artist (by Deezer artist ID).
     */
    suspend fun getTopTracks(artistId: Long): List<TopTrack>? = withContext(Dispatchers.IO) {
        if (artistId <= 0) return@withContext null
        val cacheKey = "deezer:top:$artistId"
        if (cache.containsKey(cacheKey)) {
            @Suppress("UNCHECKED_CAST")
            return@withContext cache[cacheKey] as List<TopTrack>?
        }
        try {
            val url = "$BASE/artist/$artistId/top?limit=15"
            val json = httpGetJson(url) ?: return@withContext null
            val data = json.optJSONArray("data") ?: return@withContext null
            val list = (0 until data.length()).mapNotNull { i ->
                val t = data.optJSONObject(i) ?: return@mapNotNull null
                val album = t.optJSONObject("album")
                TopTrack(
                    title = t.optString("title").ifBlank { "Unknown" },
                    duration = t.optInt("duration", 0),
                    albumName = album?.optString("title") ?: "",
                    albumArtUrl = album?.optString("cover_xl")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: album?.optString("cover_medium")?.takeIf { it.isNotBlank() },
                )
            }
            cache[cacheKey] = list
            list
        } catch (e: Exception) {
            Log.w(TAG, "getTopTracks failed for $artistId", e)
            null
        }
    }

    /**
     * Get albums for an artist (by Deezer artist ID).
     */
    suspend fun getAlbums(artistId: Long): List<AlbumData>? = withContext(Dispatchers.IO) {
        if (artistId <= 0) return@withContext null
        val cacheKey = "deezer:albums:$artistId"
        if (cache.containsKey(cacheKey)) {
            @Suppress("UNCHECKED_CAST")
            return@withContext cache[cacheKey] as List<AlbumData>?
        }
        try {
            val url = "$BASE/artist/$artistId/albums"
            val json = httpGetJson(url) ?: return@withContext null
            val data = json.optJSONArray("data") ?: return@withContext null
            val list = (0 until data.length()).mapNotNull { i ->
                val al = data.optJSONObject(i) ?: return@mapNotNull null
                AlbumData(
                    title = al.optString("title").ifBlank { "Unknown Album" },
                    artUrl = al.optString("cover_xl").takeIf { it.isNotBlank() && it != "null" }
                        ?: al.optString("cover_medium").takeIf { it.isNotBlank() },
                    year = al.optString("release_date").takeIf { it.isNotBlank() && it != "null" }?.substringBefore("-"),
                    recordType = al.optString("record_type").takeIf { it.isNotBlank() && it != "null" },
                )
            }
            cache[cacheKey] = list
            list
        } catch (e: Exception) {
            Log.w(TAG, "getAlbums failed for $artistId", e)
            null
        }
    }

    /**
     * Get similar artists (by Deezer artist ID).
     */
    suspend fun getSimilarArtists(artistId: Long): List<SimilarArtist>? = withContext(Dispatchers.IO) {
        if (artistId <= 0) return@withContext null
        val cacheKey = "deezer:similar:$artistId"
        if (cache.containsKey(cacheKey)) {
            @Suppress("UNCHECKED_CAST")
            return@withContext cache[cacheKey] as List<SimilarArtist>?
        }
        try {
            val url = "$BASE/artist/$artistId/related?limit=10"
            val json = httpGetJson(url) ?: return@withContext null
            val data = json.optJSONArray("data") ?: return@withContext null
            val list = (0 until data.length()).mapNotNull { i ->
                val a = data.optJSONObject(i) ?: return@mapNotNull null
                SimilarArtist(
                    name = a.optString("name").ifBlank { "Unknown" },
                    imageUrl = a.optString("picture_medium").takeIf { it.isNotBlank() && it != "null" },
                )
            }
            cache[cacheKey] = list
            list
        } catch (e: Exception) {
            Log.w(TAG, "getSimilarArtists failed for $artistId", e)
            null
        }
    }

    // ── internals ──

    private fun httpGetJson(urlString: String): JSONObject? {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Lutea/1.0 ( rajat@lutea.local )")
            setRequestProperty("Accept", "application/json")
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
        } catch (e: Exception) {
            Log.w(TAG, "Network error for $urlString", e)
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
