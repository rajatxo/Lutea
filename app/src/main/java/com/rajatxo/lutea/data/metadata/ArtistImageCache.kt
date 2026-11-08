package com.rajatxo.lutea.data.metadata

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * ArtistImageCache — permanently stores artist images on disk.
 *
 * First time an artist page opens: fetch image from API → download → save to
 * filesDir/artist_images/{sanitizedName}.jpg
 *
 * Every subsequent time: load directly from the file → instant, no network.
 *
 * Images are ~50-100KB each. 1000 artists = ~50-100MB (fine for local storage).
 */
object ArtistImageCache {

    private const val TAG = "ArtistImageCache"
    private const val DIR_NAME = "artist_images"

    /**
     * Get the cached image file for an artist, or null if not cached.
     */
    fun getCachedFile(context: Context, artistName: String): File? {
        val file = getFile(context, artistName)
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Download an image from a URL and save it permanently for this artist.
     * Returns the saved file, or null on failure.
     */
    fun downloadAndCache(context: Context, artistName: String, imageUrl: String): File? {
        return try {
            val file = getFile(context, artistName)
            file.parentFile?.mkdirs()

            val conn = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Lutea/1.0")
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = true
            }

            try {
                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Cached image for '$artistName': ${file.length()} bytes")
                    file
                } else {
                    Log.w(TAG, "Download failed for '$artistName': HTTP ${conn.responseCode}")
                    null
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "downloadAndCache failed for '$artistName'", e)
            null
        }
    }

    /**
     * Delete the cached image for an artist (e.g. when user sets a custom image).
     */
    fun clear(context: Context, artistName: String) {
        try {
            getFile(context, artistName).delete()
        } catch (e: Exception) {
            Log.w(TAG, "clear failed", e)
        }
    }

    private fun getFile(context: Context, artistName: String): File {
        val sanitizedName = sanitizeFileName(artistName)
        return File(context.filesDir, "$DIR_NAME/$sanitizedName.jpg")
    }

    /**
     * Sanitize artist name for use as a filename.
     * Removes characters that are invalid in filenames.
     */
    private fun sanitizeFileName(name: String): String {
        return name.lowercase()
            .replace(" ", "_")
            .replace(Regex("[^a-z0-9_]"), "")
            .take(50) // limit length
            .ifBlank { "unknown" }
    }

    /**
     * Sanitize artist name for API searches.
     * Strips feat., ft., &, commas, parentheses, etc.
     */
    fun sanitizeForSearch(name: String): String {
        return name
            .replace(Regex("(?i)\\s*(feat|ft|featuring)\\..*", ""), "") // remove feat. X
            .replace(Regex("(?i)\\s*&\\s*.*"), "") // remove & X
            .replace(Regex("[,()\\[\\]{}]"), "") // remove punctuation
            .replace(Regex("\\s*-\\s*.*"), "") // remove - X
            .trim()
            .ifBlank { name.trim() }
    }
}
