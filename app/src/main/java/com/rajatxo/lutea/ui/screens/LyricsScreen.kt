package com.rajatxo.lutea.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.lutea.R
import com.rajatxo.lutea.lyrics.Lyrics
import com.rajatxo.lutea.lyrics.LyricsQuery
import com.rajatxo.lutea.lyrics.LyricsRepository
import com.rajatxo.lutea.lyrics.LyricsStyle
import com.rajatxo.lutea.lyrics.LyricsView
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.ui.components.BillieEilishFamily
import com.rajatxo.lutea.ui.components.CalSansFamily
import kotlinx.coroutines.delay

/**
 * The lyrics page — opens from the full player's lyrics icon.
 *
 * Uses [LyricsRepository] to fetch from all providers in parallel (LRCLIB,
 * BetterLyrics, LyricsPlus, SimpMusic) and [LyricsView] to render the
 * word-by-word fill animation with auto-scroll, glow, and progressive blur.
 */
@Composable
fun LyricsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSong by PlaybackConnection.currentSong.collectAsState()
    val positionState = PlaybackConnection.positionMs.collectAsState()
    val isPlaying by PlaybackConnection.isPlaying.collectAsState()
    val context = LocalContext.current

    val repo = remember { LyricsRepository(context) }

    var lyrics by remember { mutableStateOf<Lyrics?>(null) }
    var loading by remember { mutableStateOf(true) }

    // Fetch lyrics when the song changes
    LaunchedEffect(currentSong?.id) {
        val song = currentSong
        if (song == null) {
            lyrics = null
            loading = false
            return@LaunchedEffect
        }
        lyrics = null
        loading = true
        repo.load(
            LyricsQuery(
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.duration,
            )
        ).collect { result ->
            lyrics = result
            loading = false
        }
    }

    // Poll playback position for smooth word animation
    LaunchedEffect(Unit) {
        while (true) {
            PlaybackConnection.pollPosition()
            delay(200)
        }
    }

    val style = remember {
        LyricsStyle(
            fontFamily = CalSansFamily,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // ── Top bar: back button ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = "Back to player",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // ── Song title + artist ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = currentSong?.title ?: "Nothing playing",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontFamily = BillieEilishFamily,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = currentSong?.artist ?: "",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontFamily = CalSansFamily,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }

            // ── Lyrics content ──
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    loading -> LyricsMessage("Loading lyrics…")
                    lyrics == null -> LyricsMessage("No lyrics found for this song")
                    else -> LyricsView(
                        lyrics = lyrics,
                        positionMs = { positionState.value },
                        isPlaying = isPlaying,
                        onSeek = { timeMs -> PlaybackConnection.seekTo(timeMs) },
                        modifier = Modifier.fillMaxSize(),
                        style = style,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = message,
            style = TextStyle(
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 15.sp,
                fontFamily = CalSansFamily,
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.padding(horizontal = 48.dp),
        )
    }
}
