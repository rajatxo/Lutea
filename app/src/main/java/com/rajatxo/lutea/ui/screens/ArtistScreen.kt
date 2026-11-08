package com.rajatxo.lutea.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.lutea.R
import com.rajatxo.lutea.domain.model.Song
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.data.metadata.MusicBrainzClient
import com.rajatxo.lutea.ui.components.BillieEilishFamily
import com.rajatxo.lutea.ui.components.CalSansFamily

/**
 * ArtistScreen — full-screen artist page.
 *
 * Shows the artist name at the top, a song count, then a list of albums by
 * the artist (grouped from the songs). Each album section is collapsible in
 * spirit — here just rendered as a header + the songs in that album.
 * Tapping a song plays it. Tapping an album header opens [AlbumScreen].
 *
 * Universal Layout Laws followed:
 *  - Law 7: everything screen-relative is a fraction of BoxWithConstraints.maxWidth/maxHeight.
 *  - Status bar + navigation bar padding applied.
 *
 * @param artistName    Name of the artist.
 * @param songs         All songs by this artist (will be grouped by album).
 * @param onBack        Called when the back button is tapped.
 * @param onAlbumClick  Called with (albumName, songsInAlbum) when an album header is tapped.
 */
@Composable
fun ArtistScreen(
    artistName: String,
    songs: List<Song>,
    onBack: () -> Unit,
    onAlbumClick: (albumName: String, songs: List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
    ) {
        val screenW = maxWidth
        val horizontalPadding = screenW * 0.06f

        // ★ YouTube Data API v3 — gives the SAME artist image as YouTube Music.
        //  Falls back to Wikipedia for bio, TheAudioDB for extra.
        var artistImageUrl by remember { mutableStateOf<String?>(null) }
        var artistBio by remember { mutableStateOf<String?>(null) }
        var loadingMetadata by remember { mutableStateOf(true) }
        LaunchedEffect(artistName) {
            loadingMetadata = true
            // 1. YouTube — same image as YTM (channel thumbnail)
            artistImageUrl = com.rajatxo.lutea.data.metadata.YouTubeClient.getArtistImage(artistName)
            // 2. Wikipedia — for bio (and image if YouTube failed)
            if (artistImageUrl == null || artistBio == null) {
                try {
                    val wiki = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val title = artistName.replace(" ", "_")
                        val url = "https://en.wikipedia.org/api/rest_v1/page/summary/" +
                            java.net.URLEncoder.encode(title, "UTF-8").replace("+", "%20")
                        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                            setRequestProperty("User-Agent", "Lutea/1.0 ( rajat@lutea.local )")
                            connectTimeout = 8000; readTimeout = 8000; instanceFollowRedirects = true
                        }
                        try {
                            if (conn.responseCode in 200..299) {
                                val json = org.json.JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                                val img = json.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.isNotBlank() }
                                val bio = json.optString("extract")?.takeIf { it.isNotBlank() }
                                Pair(img, bio)
                            } else null
                        } finally { conn.disconnect() }
                    }
                    if (wiki != null) {
                        if (artistImageUrl == null) artistImageUrl = wiki.first
                        if (artistBio == null) artistBio = wiki.second
                    }
                } catch (e: Exception) { android.util.Log.w("ArtistScreen", "Wikipedia failed", e) }
            }
            // 3. TheAudioDB fallback for bio
            if (artistBio == null) {
                val adb = com.rajatxo.lutea.data.metadata.TheAudioDBClient.searchArtist(artistName)
                if (artistImageUrl == null) artistImageUrl = adb?.thumbnailUrl ?: adb?.bannerUrl
                if (artistBio == null) artistBio = adb?.biography
            }
            loadingMetadata = false
        }

        // Group songs by album, preserving first-seen order.
        val albums: List<Pair<String, List<Song>>> = remember(songs) {
            val order = mutableListOf<String>()
            val map = linkedMapOf<String, MutableList<Song>>()
            for (s in songs) {
                val a = s.album.ifBlank { "Unknown Album" }
                if (!map.containsKey(a)) {
                    order.add(a)
                    map[a] = mutableListOf()
                }
                map[a]!!.add(s)
            }
            order.map { it to map[it]!! }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            // ── Top bar: back button ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onBack,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_left),
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            // ── Artist header ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding)
                        .padding(top = 8.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // ★ Artist image from MusicBrainz/Cover Art Archive (or placeholder)
                    Box(
                        modifier = Modifier
                            .size(screenW * 0.35f)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A1A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (artistImageUrl != null) {
                            AsyncImage(
                                model = artistImageUrl,
                                contentDescription = artistName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (!loadingMetadata) {
                            // No image found — show first letter
                            Text(
                                text = artistName.firstOrNull()?.uppercase() ?: "?",
                                color = Color.White.copy(alpha = 0.3f),
                                fontSize = 48.sp,
                                fontFamily = BillieEilishFamily,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = artistName,
                        color = Color.White,
                        fontSize = 34.sp,
                        fontFamily = BillieEilishFamily,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "${songs.size} songs  •  ${albums.size} album${if (albums.size != 1) "s" else ""}",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                        fontFamily = CalSansFamily,
                    )
                    // ★ Biography from Wikipedia or TheAudioDB
                    val bio = artistBio
                    if (bio != null && bio.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = bio,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            fontFamily = CalSansFamily,
                            lineHeight = 20.sp,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else if (loadingMetadata) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Fetching artist info...",
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 12.sp,
                            fontFamily = CalSansFamily,
                        )
                    }
                }
            }

            // ── Album sections ──
            for ((albumName, albumSongs) in albums) {
                item(key = "header-$albumName") {
                    ArtistAlbumHeader(
                        albumName = albumName,
                        songCount = albumSongs.size,
                        horizontalPadding = horizontalPadding,
                        onClick = { onAlbumClick(albumName, albumSongs) },
                    )
                }
                itemsIndexed(albumSongs, key = { idx, s -> "song-$albumName-$idx-${s.id}" }) { _, song ->
                    ArtistSongRow(
                        title = song.title,
                        durationMs = song.duration,
                        artUri = song.albumArtUri,
                        horizontalPadding = horizontalPadding,
                        onClick = { PlaybackConnection.playSong(song, songs) },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ArtistAlbumHeader(
    albumName: String,
    songCount: Int,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = albumName,
            color = Color.White,
            fontSize = 20.sp,
            fontFamily = CalSansFamily,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$songCount song${if (songCount != 1) "s" else ""}",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 13.sp,
            fontFamily = CalSansFamily,
        )
    }
}

@Composable
private fun ArtistSongRow(
    title: String,
    durationMs: Long,
    artUri: android.net.Uri?,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center,
        ) {
            artUri?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 15.sp,
            fontFamily = CalSansFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatArtistDuration(durationMs),
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp,
            fontFamily = CalSansFamily,
        )
    }
}

private fun formatArtistDuration(ms: Long): String {
    val totalSeconds = ms / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
