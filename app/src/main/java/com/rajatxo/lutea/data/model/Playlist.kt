package com.rajatxo.lutea.data.model

import kotlinx.serialization.Serializable

/**
 * A user-created playlist.
 * Ported from Coral's Playlist.kt (simplified — no tags, no coverUri).
 *
 * @param id    Stable identifier (epoch millis at creation time).
 * @param name  Display name.
 * @param songIds  Ordered list of MediaStore song IDs in this playlist.
 */
@Serializable
data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis()
)
