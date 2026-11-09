package com.rajatxo.lutea.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.lutea.R
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.ui.components.BillieEilishFamily
import com.rajatxo.lutea.ui.components.CalSansFamily

/**
 * LyricsScreen — the lyrics page that opens from the full player's lyrics icon.
 *
 * This is the structural scaffold for the lyrics feature. It sets up:
 *  - A full-screen dark background matching the player.
 *  - A top bar with a back button (returns to the full player).
 *  - The current song title + artist.
 *  - A scrollable [LazyColumn] where synced lyrics lines will be rendered.
 *
 * The actual lyrics rendering (word-by-word synced, LRCLIB integration,
 * auto-scroll, current-line highlighting — the "normal" Apple-Music-style
 * the user wants) will be implemented separately and dropped into the
 * LazyColumn below.
 *
 * Style: Mochy-inspired — dark, clean, BillieEilish for the title,
 * CalSans for body text. Matches the player's visual language.
 */
@Composable
fun LyricsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSong by PlaybackConnection.currentSong.collectAsState()

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
                    .padding(horizontal = 28.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = currentSong?.title ?: "Nothing playing",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontFamily = BillieEilishFamily,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = currentSong?.artist ?: "",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    fontFamily = CalSansFamily,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }

            // ── Lyrics area (scrollable) ──
            // Placeholder — the real synced-lyrics rendering goes here.
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 24.dp,
                    bottom = 48.dp,
                ),
            ) {
                item {
                    Text(
                        text = "Lyrics will appear here.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 22.sp,
                        fontFamily = CalSansFamily,
                        lineHeight = 34.sp,
                    )
                }
                item {
                    Text(
                        text = "This is where the word-by-word synced lyrics will render — " +
                            "the normal style (like Apple Music), with all lines visible, " +
                            "the current line highlighted, and auto-scroll.",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        fontFamily = CalSansFamily,
                        lineHeight = 22.sp,
                    )
                }
            }
        }
    }
}
