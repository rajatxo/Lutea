package com.rajatxo.lutea.data.metadata

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * TheAudioDBClient — free music metadata API (no key needed).
 *
 * Provides artist images (thumbnails + banners), biographies, and album
 * discographies — the data that MusicBrainz lacks (MusicBrainz only has
 * album art via Cover Art Archive, not artist photos).
 *
 * Uses the public test key "123" — no registration required.
 * Rate limit: ~30 requests/minute (generous for a mobile app with caching).
 *
 * Endpoints:
 *  - Artist search:  https://www.theaudiodb.com/api/v1/json/123/search.php?s=NAME
 *  - Albums by artist: https://www.theaudiodb.com/api/v1/json/123/album.php?i=ARTIST_ID
 *
 * All methods are suspend, run on Dispatchers.IO, return null on failure.
 * Never crashes.
 */
object TheAudioDBClient {

    private const val TAG = "TheAudioDBClient"
    private const val BASE = "https://www.theaudiodb.com/api/v1/json/123"
    private const val TIMEOUT = 10_000

    data class ArtistData(
        val name: String,
        val thumbnailUrl: String?,    // artist photo (square-ish)
        val bannerUrl: String?,       // wide banner image
        val biography: String?,
        val genre: String?,
        val country: String?,
        val artistId: String?,
    )

    data class AlbumData(
        val title: String,
        val artUrl: String?,          // album cover thumbnail
        val year: Int?,
    )

    private val cache = HashMap<String, Any?>()

    /**
     * Search for an artist by name. Returns ArtistData with image URLs + bio,
     * or null if not found / error.
     */
    suspend fun searchArtist(name: String): ArtistData? = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext null
        val cacheKey = "audiodb:artist:$name"
        if (cache.containsKey(cacheKey)) {
            @Suppress("UNCHECKED_CAST")
            return@withContext cache[cacheKey] as ArtistData?
        }
        try {
            // Try v1 API first (free, key "123")
            val url = "$BASE/search.php?s=" + encode(name)
            val json = httpGetJson(url)
            val artists = json?.optJSONArray("artists")
            val a = artists?.optJSONObject(0)

            if (a != null) {
                val data = ArtistData(
                    name = a.optString("strArtist").ifBlank { name },
                    thumbnailUrl = a.optString("strArtistThumb").takeIf { it.isNotBlank() && it != "null" },
                    bannerUrl = a.optString("strArtistFanart").takeIf { it.isNotBlank() && it != "null" },
                    biography = a.optString("strBiographyEN").takeIf { it.isNotBlank() && it != "null" },
                    genre = a.optString("strGenre").takeIf { it.isNotBlank() && it != "null" },
                    country = a.optString("strCountry").takeIf { it.isNotBlank() && it != "null" },
                    artistId = a.optString("idArtist").takeIf { it.isNotBlank() },
                )
                cache[cacheKey] = data
                return@withContext data
            }

            // TheAudioDB v1 didn't have the artist — try iTunes Search API (free, no key)
            val itunesUrl = "https://itunes.apple.com/search?term=" + encode(name) + "&entity=musicArtist&limit=1"
            val itunesJson = httpGetJson(itunesUrl)
            val results = itunesJson?.optJSONArray("results")
            val first = results?.optJSONObject(0)

            if (first != null) {
                // iTunes doesn't give a direct artist image URL, but we can
                // get the artist's content and use album art as a fallback image.
                val artistId = first.optLong("artistId", 0)
                val imageUrl = if (artistId > 0) {
                    // Lookup the artist's songs to get album art as their image
                    val lookupUrl = "https://itunes.apple.com/lookup?id=$artistId&entity=song&limit=1"
                    val lookupJson = httpGetJson(lookupUrl)
                    val lookupResults = lookupJson?.optJSONArray("results")
                    val song = lookupResults?.optJSONObject(1) // index 0 is the artist, 1 is first song
                    song?.optString("artworkUrl100")?.replace("100x100", "600x600")
                        ?.takeIf { it.isNotBlank() }
                } else null

                val data = ArtistData(
                    name = first.optString("artistName").ifBlank { name },
                    thumbnailUrl = imageUrl,
                    bannerUrl = null,
                    biography = null,
                    genre = first.optString("primaryGenreName").takeIf { it.isNotBlank() },
                    country = null,
                    artistId = artistId.toString().takeIf { it != "0" },
                )
                cache[cacheKey] = data
                return@withContext data
            }

            cache[cacheKey] = null
            null
        } catch (e: Exception) {
            Log.w(TAG, "searchArtist failed for '$name'", e)
            cache[cacheKey] = null
            null
        }
    }

    /**
     * Get all albums by an artist (using their TheAudioDB artist ID).
     * Returns a list of AlbumData with cover art URLs + release years.
     */
    suspend fun getArtistAlbums(artistId: String): List<AlbumData>? = withContext(Dispatchers.IO) {
        if (artistId.isBlank()) return@withContext null
        val cacheKey = "audiodb:albums:$artistId"
        if (cache.containsKey(cacheKey)) {
            @Suppress("UNCHECKED_CAST")
            return@withContext cache[cacheKey] as List<AlbumData>?
        }
        try {
            val url = "$BASE/album.php?i=$artistId"
            val json = httpGetJson(url) ?: return@withContext null
            val albums = json.optJSONArray("album") ?: return@withContext null
            val list = (0 until albums.length()).mapNotNull { i ->
                val al = albums.optJSONObject(i) ?: return@mapNotNull null
                AlbumData(
                    title = al.optString("strAlbum").ifBlank { "Unknown Album" },
                    artUrl = al.optString("strAlbumThumb").takeIf { it.isNotBlank() && it != "null" },
                    year = al.optString("intYearReleased").takeIf { it.isNotBlank() }?.toIntOrNull(),
                )
            }
            cache[cacheKey] = list
            list
        } catch (e: Exception) {
            Log.w(TAG, "getArtistAlbums failed for '$artistId'", e)
            null
        }
    }

    // ── internals ──

    private fun httpGetJson(urlString: String): JSONObject? {
        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Lutea/1.0 ( rajat@lutea.local )")
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
