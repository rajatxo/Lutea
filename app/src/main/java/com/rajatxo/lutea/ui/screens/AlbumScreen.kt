package com.rajatxo.lutea.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.remember
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
import com.rajatxo.lutea.ui.components.BillieEilishFamily
import com.rajatxo.lutea.ui.components.CalSansFamily

/**
 * AlbumScreen — full-screen album page.
 *
 * Shows the album art at the top, album + artist names, the song count +
 * total duration, then a track list. Tapping a track plays the whole album
 * starting from that song.
 *
 * Universal Layout Laws followed:
 *  - Law 7: everything screen-relative is a fraction of BoxWithConstraints.maxWidth/maxHeight.
 *  - Status bar + navigation bar padding applied so nothing hides under system bars.
 *
 * @param albumName Title of the album.
 * @param songs     All songs in this album (order is preserved for the track list).
 * @param onBack    Called when the back button is tapped.
 */
@Composable
fun AlbumScreen(
    albumName: String,
    songs: List<Song>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
    ) {
        val screenW = maxWidth
        // Album art is square, sized relative to the screen width.
        val artSize = screenW * 0.70f
        val horizontalPadding = screenW * 0.06f

        val firstSong = songs.firstOrNull()
        val albumArtUri = firstSong?.albumArtUri
        val artistName = firstSong?.artist ?: "Unknown Artist"
        val totalDurationMs = songs.sumOf { it.duration }

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

            // ── Album art ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(artSize)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1A1A1A)),
                        contentAlignment = Alignment.Center,
                    ) {
                        albumArtUri?.let { artUri ->
                            AsyncImage(
                                model = artUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }

            // ── Album name + artist + meta ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding)
                        .padding(top = 24.dp),
                ) {
                    Text(
                        text = albumName,
                        color = Color.White,
                        fontSize = 30.sp,
                        fontFamily = BillieEilishFamily,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = artistName,
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 16.sp,
                        fontFamily = CalSansFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${songs.size} songs  •  ${formatAlbumDuration(totalDurationMs)}",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                        fontFamily = CalSansFamily,
                    )
                }
            }

            item { Spacer(Modifier.height(20.dp).fillMaxWidth()) }

            // ── Track list ──
            itemsIndexed(songs) { index, song ->
                AlbumTrackRow(
                    trackNumber = index + 1,
                    title = song.title,
                    durationMs = song.duration,
                    onClick = { PlaybackConnection.playSong(song, songs) },
                    horizontalPadding = horizontalPadding,
                )
            }
        }
    }
}

@Composable
private fun AlbumTrackRow(
    trackNumber: Int,
    title: String,
    durationMs: Long,
    onClick: () -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = trackNumber.toString(),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 14.sp,
            fontFamily = CalSansFamily,
            modifier = Modifier.width(32.dp),
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontFamily = CalSansFamily,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatAlbumDuration(durationMs),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 13.sp,
            fontFamily = CalSansFamily,
        )
    }
}

private fun formatAlbumDuration(ms: Long): String {
    val totalSeconds = ms / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
