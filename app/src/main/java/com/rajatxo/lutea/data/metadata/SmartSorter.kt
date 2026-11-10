package com.rajatxo.lutea.data.metadata

import com.rajatxo.lutea.domain.model.Song

/**
 * SmartSorter — sorts the user's LOCAL songs using Last.fm's top tracks data.
 *
 * How it works:
 * 1. Fetch the artist's top tracks from Last.fm (ranked by real listener data)
 * 2. Match each top track against the user's local songs (by normalized title)
 * 3. Sort local songs by their Last.fm rank — if "Starboy" is #1 on Last.fm
 *    and you have it, it shows first
 * 4. Songs not in Last.fm's top tracks go at the bottom (sorted alphabetically)
 * 5. Songs you DON'T have are never shown
 */
object SmartSorter {

    /**
     * Sort a list of local songs by Last.fm top tracks ranking.
     *
     * @param localSongs The user's songs by this artist (from MediaStore)
     * @param topTracks Last.fm's top tracks for this artist (ranked by popularity)
     * @return Songs sorted by popularity — most popular first, unknown songs last
     */
    fun sortByPopularity(localSongs: List<Song>, topTracks: List<LastFmClient.TopTrack>): List<Song> {
        if (topTracks.isEmpty()) return localSongs

        // Build a map: normalized title → rank (lower = more popular)
        val rankMap = HashMap<String, Int>()
        for (track in topTracks) {
            val normalized = normalizeTitle(track.title)
            if (!rankMap.containsKey(normalized) || track.rank < rankMap[normalized]!!) {
                rankMap[normalized] = track.rank
            }
        }

        // Partition songs into "ranked" (found in Last.fm) and "unranked" (not found)
        val ranked = mutableListOf<Pair<Song, Int>>()
        val unranked = mutableListOf<Song>()

        for (song in localSongs) {
            val normalized = normalizeTitle(song.title)
            val rank = rankMap[normalized]
            if (rank != null) {
                ranked.add(song to rank)
            } else {
                unranked.add(song)
            }
        }

        // Sort ranked by rank (ascending = most popular first)
        ranked.sortBy { it.second }

        // Sort unranked alphabetically
        unranked.sortBy { it.title.lowercase() }

        // Ranked first, then unranked
        return ranked.map { it.first } + unranked
    }

    /**
     * Get a popularity rank for a song (1 = most popular, Int.MAX_VALUE = not in top tracks).
     */
    fun getRank(song: Song, topTracks: List<LastFmClient.TopTrack>): Int {
        val normalized = normalizeTitle(song.title)
        return topTracks.firstOrNull { normalizeTitle(it.title) == normalized }?.rank
            ?: Int.MAX_VALUE
    }

    /**
     * Find similar artists that the user actually has on their phone.
     *
     * @param similarFromApi Similar artists from Last.fm
     * @param localArtists All artist names the user has songs from
     * @return Similar artists that the user has locally (name + hasLocalSongs = true)
     */
    fun filterSimilarToLocal(
        similarFromApi: List<LastFmClient.SimilarArtist>,
        localArtists: Set<String>,
    ): List<LastFmClient.SimilarArtist> {
        val normalizedLocal = localArtists.map { normalizeArtist(it) }.toSet()
        return similarFromApi.filter { normalizeArtist(it.name) in normalizedLocal }
    }

    /**
     * Normalize a song title for matching.
     * Strips: feat./ft., remix/live/acoustic suffixes, punctuation, makes lowercase.
     */
    private fun normalizeTitle(title: String): String {
        var result = title.lowercase().trim()
        // Remove feat./ft.
        result = Regex("(?i)\\s*\\(?\\s*(feat|ft)\\.?\\s.*?\\)?\\s*$").replace(result, "")
        // Remove remix/live/acoustic/etc in parentheses
        result = Regex("\\([^)]*\\)").replace(result, "")
        // Remove - remix / - live / - acoustic
        result = Regex("\\s*-\\s*(remix|live|acoustic|radio edit|extended|instrumental).*").replace(result, "")
        // Remove punctuation
        result = result.replace(Regex("[^a-z0-9\\s]"), "")
        // Collapse whitespace
        result = result.replace(Regex("\\s+"), " ").trim()
        return result
    }

    /**
     * Normalize an artist name for matching.
     */
    private fun normalizeArtist(name: String): String {
        return ArtistImageCache.sanitizeForSearch(name).lowercase().trim()
    }
}
