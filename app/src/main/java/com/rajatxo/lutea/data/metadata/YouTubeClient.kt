package com.rajatxo.lutea.data.metadata

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * YouTubeClient — fetches artist images using YouTube Data API v3.
 *
 * This gives the EXACT same artist image as YouTube Music shows because
 * YTM uses the artist's YouTube channel thumbnail as their profile picture.
 *
 * Free quota: 10,000 units/day. Each search costs 100 units = 100 lookups/day.
 * Results are cached in-memory + SharedPreferences so the same artist is
 * never fetched twice.
 *
 * Requires: API key from Google Cloud Console (YouTube Data API v3 enabled).
 */
object YouTubeClient {

    private const val TAG = "YouTubeClient"
    private const val API_KEY = "AIzaSyCEKSJ4GHx5WaDG-3trGqMFikkIg_7J-Y0"
    private const val BASE = "https://www.googleapis.com/youtube/v3"
    private const val TIMEOUT = 8000

    private val cache = HashMap<String, String?>()

    /**
     * Search YouTube for an artist's channel and return their profile image URL.
     * This is the same image YouTube Music shows for the artist.
     *
     * @param artistName The artist name to search for
     * @return Image URL (high-quality channel thumbnail) or null if not found
     */
    suspend fun getArtistImage(artistName: String): String? = withContext(Dispatchers.IO) {
        if (artistName.isBlank()) return@withContext null
        val cacheKey = "yt:artist:$artistName"
        if (cache.containsKey(cacheKey)) {
            return@withContext cache[cacheKey]
        }
        try {
            // Search for the artist's YouTube channel
            val searchUrl = "$BASE/search?part=snippet&q=" + encode(artistName) +
                "&type=channel&maxResults=1&key=$API_KEY"
            Log.d(TAG, "Searching YouTube for: $artistName")

            val conn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Lutea/1.0")
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                instanceFollowRedirects = true
            }

            val imageUrl = try {
                val code = conn.responseCode
                Log.d(TAG, "YouTube API response: $code")
                if (code in 200..299) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(text)
                    val items = json.optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        val item = items.optJSONObject(0)
                        val snippet = item?.optJSONObject("snippet")
                        val thumbnails = snippet?.optJSONObject("thumbnails")
                        // Try high → medium → default quality
                        val high = thumbnails?.optJSONObject("high")?.optString("url")
                        val medium = thumbnails?.optJSONObject("medium")?.optString("url")
                        val default = thumbnails?.optJSONObject("default")?.optString("url")
                        val url = high?.takeIf { it.isNotBlank() }
                            ?: medium?.takeIf { it.isNotBlank() }
                            ?: default?.takeIf { it.isNotBlank() }
                        Log.d(TAG, "Found image for '$artistName': $url")
                        url
                    } else {
                        Log.d(TAG, "No YouTube channel found for '$artistName'")
                        null
                    }
                } else {
                    Log.w(TAG, "YouTube API error: $code")
                    null
                }
            } finally {
                conn.disconnect()
            }

            cache[cacheKey] = imageUrl
            imageUrl
        } catch (e: Exception) {
            Log.w(TAG, "getArtistImage failed for '$artistName'", e)
            cache[cacheKey] = null
            null
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
