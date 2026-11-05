package com.rajatxo.lutea.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.rajatxo.lutea.ui.components.BillieEilishFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PlaylistDetailScreen — Mochy-inspired full-screen seamless background +
 * Coral-style 3-dot menu + Play/Shuffle buttons.
 *
 * Layers (back to front):
 *  1. PlaylistCoverBackground — FULL SCREEN color gradient + blurred cover
 *  2. Top bar: back + 3-dot menu
 *  3. Playlist title + song count + Play/Shuffle buttons
 *  4. Song list (LazyColumn scrolls over the tinted background)
 */
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val playlists by PlaylistStore.playlists.collectAsState()

    val livePlaylist = playlists.firstOrNull { it.id == playlist.id }
    if (livePlaylist == null) {
        LaunchedEffect(Unit) { onBackClick() }
        return
    }

    var allSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        allSongs = withContext(Dispatchers.IO) { MusicScanner.scanMusic(context) }
        loaded = true
    }

    val playlistSongs = allSongs.filter { it.id in livePlaylist.songIds }
    val coverUri: Uri? = livePlaylist.coverUri?.let { Uri.parse(it) }
        ?: playlistSongs.firstOrNull()?.albumArtUri

    val listState = rememberLazyListState()
    val scrollProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 600f).coerceIn(0f, 1f)
        }
    }

    // Menu + dialog state
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) { }
            PlaylistStore.setPlaylistCover(livePlaylist.id, uri.toString())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ── Layer 1: FULL-SCREEN seamless background ──
        PlaylistCoverBackground(
            albumArtUri = coverUri,
            scrollProgress = { scrollProgress },
        )

        // ── Layer 2: content ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top bar: back + 3-dot menu
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
                    Text("←", color = Color.White, fontSize = 20.sp)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showMenu = true },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⋮", color = Color.White, fontSize = 22.sp)
                }
            }

            if (!loaded) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Loading...", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                // ── Header: title + stats + play/shuffle ──
                item {
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
                            fontFamily = BillieEilishFamily,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Playlist · ${playlistSongs.size} songs",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontFamily = CalSansFamily,
                        )
                        Spacer(Modifier.height(20.dp))

                        // ── Play + Shuffle buttons (Mochy style) ──
                        if (playlistSongs.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Play pill
                                Box(
                                    modifier = Modifier
                                        .height(40.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                PlaybackConnection.playSong(
                                                    playlistSongs.first(),
                                                    playlistSongs,
                                                )
                                            },
                                        )
                                        .padding(horizontal = 28.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Play",
                                        color = Color.Black,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = CalSansFamily,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                // Shuffle circle
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.15f))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {
                                                if (playlistSongs.isNotEmpty()) {
                                                    val shuffled = playlistSongs.shuffled()
                                                    PlaybackConnection.playSong(
                                                        shuffled.first(),
                                                        shuffled,
                                                    )
                                                }
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "⇄",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
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
                                    "No songs yet",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontFamily = CalSansFamily,
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    "Add songs from the Songs tab",
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

        // ── 3-dot menu ──
        if (showMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showMenu = false },
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 60.dp, end = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1A1A1A).copy(alpha = 0.95f)),
            ) {
                MenuOption("Rename") {
                    showMenu = false
                    showRenameDialog = true
                }
                MenuOption("Change cover photo") {
                    showMenu = false
                    coverPickerLauncher.launch(arrayOf("image/*"))
                }
                MenuOption("Remove cover photo", enabled = livePlaylist.coverUri != null) {
                    showMenu = false
                    PlaylistStore.setPlaylistCover(livePlaylist.id, null)
                }
                MenuOption("Delete playlist", isDanger = true) {
                    showMenu = false
                    showDeleteConfirm = true
                }
            }
        }

        // ── Rename dialog ──
        if (showRenameDialog) {
            var renameText by remember { mutableStateOf(livePlaylist.name) }
            val MAX_LETTERS = 10
            val letterCount = renameText.count { it.isLetter() }
            val isOverLimit = letterCount > MAX_LETTERS
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename playlist", color = Color.White, fontFamily = CalSansFamily, fontWeight = FontWeight.SemiBold) },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { newValue ->
                            val filtered = newValue.filter { it.isLetter() || it.isWhitespace() }
                            if (filtered.count { it.isLetter() } <= MAX_LETTERS) {
                                renameText = filtered
                            }
                        },
                        placeholder = { Text("Playlist name (max 10 letters)") },
                        singleLine = true,
                        isError = isOverLimit,
                        supportingText = {
                            Text(
                                "${MAX_LETTERS - letterCount} letters left",
                                color = if (letterCount >= MAX_LETTERS - 2) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                            )
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (renameText.isNotBlank() && !isOverLimit) {
                                PlaylistStore.renamePlaylist(livePlaylist.id, renameText.trim())
                            }
                            showRenameDialog = false
                        },
                        enabled = renameText.isNotBlank() && !isOverLimit,
                    ) { Text("Save") }
                },
                dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } },
            )
        }

        // ── Delete confirmation ──
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete playlist?", color = Color.White, fontFamily = CalSansFamily, fontWeight = FontWeight.SemiBold) },
                text = { Text("\"${livePlaylist.name}\" will be permanently deleted.", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp) },
                confirmButton = {
                    TextButton(onClick = {
                        PlaylistStore.deletePlaylist(livePlaylist.id)
                        showDeleteConfirm = false
                        onBackClick()
                    }) { Text("Delete", color = Color(0xFFFF6B6B)) }
                },
                dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun MenuOption(label: String, isDanger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val color = when {
        !enabled -> Color.White.copy(alpha = 0.3f)
        isDanger -> Color(0xFFFF6B6B)
        else -> Color.White
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(label, color = color, fontSize = 15.sp, fontFamily = CalSansFamily, fontWeight = FontWeight.Medium)
    }
}
