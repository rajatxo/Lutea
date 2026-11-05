package com.rajatxo.lutea.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.lutea.data.model.Playlist
import com.rajatxo.lutea.data.scanner.MusicScanner
import com.rajatxo.lutea.data.store.PlaylistStore
import com.rajatxo.lutea.domain.model.Song
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.ui.components.CalSansFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PlaylistDetailScreen — Mochy-inspired seamless cover background.
 *
 * Layers (back to front):
 *  1. PlaylistCoverBackground — blurred album art + color blend into black
 *  2. Top bar with back button (status-bar padded)
 *  3. Playlist title (large, white)
 *  4. Song count subtitle
 *  5. Song list (LazyColumn, scroll-fades the backdrop)
 *
 * Universal Layout Laws:
 *  - Law 1: statusBarsPadding() on top bar
 *  - Law 2/3: edge-to-edge (already set in MainActivity + theme)
 *  - Law 7: fillMaxSize + BoxWithConstraints-free (LazyColumn handles scrolling)
 */
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val playlists by PlaylistStore.playlists.collectAsState()

    // Crash protection: if playlist was deleted, dismiss immediately.
    val livePlaylist = playlists.firstOrNull { it.id == playlist.id }
    if (livePlaylist == null) {
        LaunchedEffect(Unit) { onBackClick() }
        return
    }

    // Load songs once
    var allSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        allSongs = withContext(Dispatchers.IO) { MusicScanner.scanMusic(context) }
        loaded = true
    }

    val playlistSongs = allSongs.filter { it.id in livePlaylist.songIds }
    val firstSongArt = playlistSongs.firstOrNull()?.albumArtUri

    // Scroll state — drives the backdrop parallax + fade.
    val listState = rememberLazyListState()
    val scrollProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 600f).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ── Layer 1: seamless blurred cover background ──
        if (firstSongArt != null) {
            PlaylistCoverBackground(
                albumArtUri = firstSongArt,
                bandHeight = 360.dp,
                scrollProgress = { scrollProgress },
            )
        }

        // ── Layer 2: content (top bar + scrollable song list) ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top bar: back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBackClick,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "←",
                        color = Color.White,
                        fontSize = 20.sp,
                    )
                }
            }

            // Header: playlist name + song count (sits over the cover background)
            if (!loaded) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Loading...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                    )
                }
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item {
                    // Playlist title header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = livePlaylist.name,
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = CalSansFamily,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Playlist · ${playlistSongs.size} songs",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }

                if (playlistSongs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No songs yet",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontFamily = CalSansFamily,
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = "Add songs from the Songs tab",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                } else {
                    items(playlistSongs) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    PlaybackConnection.playSong(song, playlistSongs)
                                }
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = song.title,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = song.artist,
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
