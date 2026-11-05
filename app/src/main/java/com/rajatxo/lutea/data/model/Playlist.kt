package com.rajatxo.lutea.data.model

import kotlinx.serialization.Serializable

/**
 * A user-created playlist.
 *
 * @param id           Stable identifier (epoch millis at creation time).
 * @param name         Display name.
 * @param songIds      Ordered list of MediaStore song IDs in this playlist.
 * @param createdAtMs  Creation timestamp (epoch millis).
 * @param coverUri    Optional custom cover image URI. If null, the UI uses
 *                     the first song's album art as the backdrop.
 */
@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val coverUri: String? = null,
)
