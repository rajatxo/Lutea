package com.rajatxo.lutea.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajatxo.lutea.R
import com.rajatxo.lutea.lyrics.LyricsLine
import com.rajatxo.lutea.lyrics.LyricsState
import com.rajatxo.lutea.lyrics.fetchLyrics
import com.rajatxo.lutea.lyrics.findActiveLineIndex
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.ui.components.BillieEilishFamily
import com.rajatxo.lutea.ui.components.CalSansFamily
import kotlinx.coroutines.delay

// ──────────────────────────────────────────────────────────────────────────────
// Line state
// ──────────────────────────────────────────────────────────────────────────────

private enum class LineState { ACTIVE, UPCOMING, PAST }

// ──────────────────────────────────────────────────────────────────────────────
// LyricsScreen — the full lyrics page
// ──────────────────────────────────────────────────────────────────────────────

/**
 * The lyrics page that opens from the full player's lyrics icon.
 *
 * Fetches synced lyrics from LRCLIB (free, keyless, MIT-licensed) on song change,
 * polls playback position every 200ms, and renders the lyrics with:
 *  - The current line bright white, larger (scaled up), at ~1/3 from the top
 *  - Past lines dimmed (~22% opacity)
 *  - Upcoming lines slightly dimmed (~45% opacity)
 *  - Word-by-word brightness change when word timestamps are available (rich-sync LRC)
 *  - Auto-scroll that follows playback, pausing 3.5s when the user scrolls manually
 *  - Loading / not-found / error states
 */
@Composable
fun LyricsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSong by PlaybackConnection.currentSong.collectAsState()
    val positionState = PlaybackConnection.positionMs.collectAsState()
    val durationState = PlaybackConnection.durationMs.collectAsState()

    var lyricsState by remember { mutableStateOf<LyricsState>(LyricsState.Loading) }

    // Fetch lyrics when the song changes
    LaunchedEffect(currentSong?.id) {
        val song = currentSong
        if (song == null) {
            lyricsState = LyricsState.Loading
            return@LaunchedEffect
        }
        lyricsState = LyricsState.Loading
        lyricsState = fetchLyrics(song.id, song.title, song.artist, durationState.value)
    }

    // Poll playback position every 200ms for smooth word animation
    LaunchedEffect(Unit) {
        while (true) {
            PlaybackConnection.pollPosition()
            delay(200)
        }
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
                    .padding(horizontal = 28.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = currentSong?.title ?: "Nothing playing",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontFamily = BillieEilishFamily,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
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
                when (val state = lyricsState) {
                    is LyricsState.Loading -> LyricsMessage("Loading lyrics…")
                    is LyricsState.NotFound -> LyricsMessage("No lyrics found for this song")
                    is LyricsState.Error -> LyricsMessage("Couldn't load lyrics.\nCheck your connection.")
                    is LyricsState.Success -> LyricsList(
                        lines = state.lines,
                        isSynced = state.isSynced,
                        positionProvider = { positionState.value },
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Lyrics message (loading / error / not found)
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun LyricsMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 15.sp,
            fontFamily = CalSansFamily,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 48.dp),
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Lyrics list — the animated scrollable lyrics
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun LyricsList(
    lines: List<LyricsLine>,
    isSynced: Boolean,
    positionProvider: () -> Long,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportH = maxHeight
        // Top padding = 1/3 of viewport so the active line rests at 1/3 from the top
        // when animateScrollToItem puts it at the content-area top.
        val topPad = viewportH / 3
        val bottomPad = viewportH / 2

        val listState = rememberLazyListState()
        var lastUserScrollMs by remember { mutableLongStateOf(0L) }

        // Active line index — only recomputes when the active line actually changes
        val activeLineIndex by remember(lines) {
            derivedStateOf {
                if (!isSynced) 0
                else findActiveLineIndex(lines, positionProvider())
            }
        }

        // Track user scroll — record the time when the user starts scrolling
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }
                .collect { scrolling ->
                    if (scrolling) lastUserScrollMs = System.currentTimeMillis()
                }
        }

        // Auto-scroll to the active line (only for synced lyrics)
        LaunchedEffect(activeLineIndex, isSynced) {
            if (!isSynced) return@LaunchedEffect
            if (lines.isEmpty()) return@LaunchedEffect
            // Small delay to let layout settle after active index change
            delay(60)
            // Skip if user scrolled within the last 3.5 seconds
            if (System.currentTimeMillis() - lastUserScrollMs < 3500) return@LaunchedEffect
            // Skip if currently scrolling (user or programmatic)
            if (listState.isScrollInProgress) return@LaunchedEffect
            listState.animateScrollToItem(activeLineIndex)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(lines, key = { i, _ -> i }) { i, line ->
                val state = when {
                    !isSynced -> LineState.ACTIVE  // unsynced: all lines same style
                    i == activeLineIndex -> LineState.ACTIVE
                    i < activeLineIndex -> LineState.PAST
                    else -> LineState.UPCOMING
                }
                LyricsLineItem(
                    line = line,
                    state = state,
                    positionProvider = positionProvider,
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Lyrics line item — the per-line renderer
// ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun LyricsLineItem(
    line: LyricsLine,
    state: LineState,
    positionProvider: () -> Long,
) {
    // Scale animation: active line is slightly larger
    val scale by animateFloatAsState(
        targetValue = if (state == LineState.ACTIVE) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "lineScale",
    )

    // Line-level opacity: active = full, upcoming = dimmed, past = more dimmed
    val lineAlpha = when (state) {
        LineState.ACTIVE -> 1f
        LineState.UPCOMING -> 0.42f
        LineState.PAST -> 0.2f
    }

    // For the active line with word timestamps: build an annotated string with
    // per-word brightness (dim → bright as each word is sung).
    // Only the active line reads positionMs — non-active lines don't recompose on position change.
    val text = if (state == LineState.ACTIVE && line.words != null) {
        buildWordAnnotatedString(line.words, positionProvider())
    } else {
        AnnotatedString(line.text)
    }

    Text(
        text = text,
        color = Color.White,
        fontSize = 20.sp,
        fontFamily = CalSansFamily,
        fontWeight = if (state == LineState.ACTIVE) FontWeight.SemiBold else FontWeight.Normal,
        lineHeight = 32.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = lineAlpha
            }
            .padding(horizontal = 4.dp),
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Word-by-word annotated string builder
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Builds an [AnnotatedString] where each word's brightness depends on the playback
 * position. Words that haven't been sung yet are dimmed (~30% alpha); the currently
 * singing word transitions from dim to bright; words that are done are full bright.
 */
private fun buildWordAnnotatedString(
    words: List<com.rajatxo.lutea.lyrics.WordTimestamp>,
    positionMs: Long,
): AnnotatedString = buildAnnotatedString {
    for (word in words) {
        val progress = when {
            positionMs < word.startMs -> 0f          // upcoming
            positionMs > word.endMs -> 1f            // sung
            word.endMs > word.startMs -> {
                ((positionMs - word.startMs).toFloat() /
                    (word.endMs - word.startMs).toFloat()).coerceIn(0f, 1f)
            }
            else -> 1f
        }
        // 0.3 (dimmed) → 1.0 (bright) as the word becomes active
        val alpha = 0.3f + 0.7f * progress
        withStyle(SpanStyle(color = Color.White.copy(alpha = alpha))) {
            append(word.text)
        }
    }
}
