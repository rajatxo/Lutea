package com.rajatxo.lutea.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rajatxo.lutea.data.prefs.PlaylistStyle
import com.rajatxo.lutea.data.prefs.PlaylistStyleManager
import com.rajatxo.lutea.data.scanner.MusicScanner
import com.rajatxo.lutea.data.store.PlaylistStore
import com.rajatxo.lutea.domain.model.Song
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.foundation.LuteaScaffold
import com.rajatxo.lutea.ui.util.LuteaTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * QuickPicksScreen — the first page of Lutea after onboarding.
 *
 * Now wrapped in LuteaScaffold (ported from Coral X's CoralXScaffold):
 *  - Header blur (120dp) with profile + title + settings icons
 *  - Bottom: tab capsule + search FAB (both kyant frosted glass)
 *  - Content: placeholder song rows
 */
@Composable
fun QuickPicksScreen(
    modifier: Modifier = Modifier,
) {
    var activeTab by remember { mutableStateOf(LuteaTab.Home) }
    var selectedPlaylist by remember { mutableStateOf<com.rajatxo.lutea.data.model.Playlist?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showStylePicker by remember { mutableStateOf(false) }
    val playlistStyle by PlaylistStyleManager.state

    if (showSettings) {
        SettingsScreen(onBackClick = { showSettings = false })
        return
    }

    selectedPlaylist?.let { playlist ->
        PlaylistDetailScreen(
            playlist = playlist,
            onBackClick = { selectedPlaylist = null },
        )
        return
    }

    // ★ Create Playlist dialog (triggered from the 3-dot menu)
    if (showCreateDialog) {
        var playlistName by remember { mutableStateOf("") }
        val MAX_LETTERS = 10
        fun letterCount(s: String): Int = s.count { it.isLetter() }
        val remainingLetters = MAX_LETTERS - letterCount(playlistName)
        val isOverLimit = remainingLetters < 0

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text("New Playlist", color = Color.White, fontFamily = CalSansFamily, fontWeight = FontWeight.SemiBold)
            },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { newValue ->
                        val filtered = newValue.filter { it.isLetter() || it.isWhitespace() }
                        if (filtered.count { it.isLetter() } <= MAX_LETTERS) {
                            playlistName = filtered
                        }
                    },
                    placeholder = { Text("Playlist name (max 10 letters)") },
                    singleLine = true,
                    isError = isOverLimit,
                    supportingText = {
                        Text(
                            "$remainingLetters letters left",
                            color = if (remainingLetters <= 2) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (playlistName.isNotBlank() && !isOverLimit) {
                            PlaylistStore.createPlaylist(playlistName.trim())
                        }
                        showCreateDialog = false
                    },
                    enabled = playlistName.isNotBlank() && !isOverLimit,
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } },
        )
    }

    LuteaScaffold(
        title = when (activeTab) {
            LuteaTab.Home -> ""
            LuteaTab.Search -> ""
            LuteaTab.Library -> ""
        },
        activeTab = activeTab,
        onTabSelected = { activeTab = it },
        onSettingsClick = { showSettings = true },
        onNewPlaylistClick = { showCreateDialog = true },
        onPlaylistStyleClick = { showStylePicker = true },
    ) {
        when (activeTab) {
            LuteaTab.Home -> QuickPicksContent()
            LuteaTab.Search -> SearchContent()
            LuteaTab.Library -> PlaylistsContent(
                onOpenPlaylist = { selectedPlaylist = it },
                playlistStyle = playlistStyle,
            )
        }
    }

    if (showStylePicker) {
        PlaylistStylePickerDialog(onDismiss = { showStylePicker = false })
    }
}

// ── Search content — basic placeholder, will be polished later ──
@Composable
private fun SearchContent() {
    var query by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
            .padding(horizontal = 20.dp)
            .padding(top = 120.dp, bottom = 120.dp),
    ) {
        Text(
            text = "Search",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = CalSansFamily,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Songs, artists, playlists...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Type to search your music",
            color = Color(0xFF8E8E93),
            fontSize = 14.sp,
        )
    }
}

// ── Quick Picks content ──
@Composable
private fun QuickPicksContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
            .padding(horizontal = 20.dp)
            .padding(top = 120.dp, bottom = 120.dp),
    ) {
            // Section: Recently Played
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recently Played",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CalSansFamily,
                )
                Text(
                    text = "See all",
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Horizontal row of circular thumbnails
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                repeat(4) {
                    CircularThumbPlaceholder()
                }
            }

            Spacer(Modifier.height(28.dp))

            // Section: Made for You
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Made for You",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = CalSansFamily,
                )
                Text(
                    text = "See all",
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                repeat(4) {
                    CircularThumbPlaceholder()
                }
            }

            Spacer(Modifier.height(28.dp))

            // Section: Recently Added
            Text(
                text = "Recently Added",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily,
            )

            Spacer(Modifier.height(16.dp))

            repeat(3) {
                SongRowPlaceholder()
                Spacer(Modifier.height(12.dp))
            }
    }
}

@Composable
private fun CircularThumbPlaceholder() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A)),
                    ),
                ),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.12f)),
        )
    }
}

@Composable
private fun SongRowPlaceholder() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A)),
                    ),
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.15f)),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
            )
        }
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        )
    }
}

// ── Songs content — scans device music + lists it + plays on tap ──
@Composable
private fun SongsContent() {
    val context = LocalContext.current
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        songs = withContext(Dispatchers.IO) { MusicScanner.scanMusic(context) }
        scanning = false
    }

    if (scanning) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1E)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Scanning music...",
                color = Color(0xFF8E8E93),
                fontSize = 14.sp,
            )
        }
        return
    }

    if (songs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1E)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No music found",
                color = Color(0xFF8E8E93),
                fontSize = 14.sp,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1C1E))
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 120.dp, bottom = 120.dp),
    ) {
        item {
            Text(
                text = "All Songs (${songs.size})",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = CalSansFamily,
            )
            Spacer(Modifier.height(16.dp))
        }
        items(songs) { song ->
            SongItem(
                song = song,
                onClick = {
                    PlaybackConnection.playSong(song, songs)
                },
            )
        }
    }
}

@Composable
private fun SongItem(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Album art thumbnail
        AsyncImage(
            model = song.albumArtUri,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
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
                color = Color(0xFF8E8E93),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatDuration(song.duration),
            color = Color(0xFF8E8E93),
            fontSize = 13.sp,
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

// ── Playlists content — the wheel — tap main text opens the playlist ──
@Composable
private fun PlaylistsContent(
    onOpenPlaylist: (com.rajatxo.lutea.data.model.Playlist) -> Unit,
    playlistStyle: PlaylistStyle,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // The arc — playlist wheel indicator
        // Wheel Advanced = 42sp title, Wheel Simpler = 28sp title
        LuteaArc(
            modifier = Modifier.fillMaxSize(),
            onOpenPlaylist = onOpenPlaylist,
            titleFontSize = when (playlistStyle) {
                PlaylistStyle.WHEEL_ADVANCED -> 42
                PlaylistStyle.WHEEL_SIMPLER -> 28
            },
            playlistStyle = playlistStyle,
        )
    }
}
