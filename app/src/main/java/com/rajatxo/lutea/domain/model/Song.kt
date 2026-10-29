package com.rajatxo.lutea.domain.model

import android.net.Uri

/**
 * Represents a single audio track on the device.
 * Ported from Coral X's Song.kt.
 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val albumArtUri: Uri?,
    val dateAdded: Long = 0L
)
