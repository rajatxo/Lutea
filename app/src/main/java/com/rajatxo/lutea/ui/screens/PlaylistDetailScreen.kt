package com.rajatxo.lutea.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.rajatxo.lutea.data.model.Playlist
import com.rajatxo.lutea.domain.model.Song
import com.rajatxo.lutea.ui.components.BillieEilishFamily
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.util.rememberCoverThemeColor

/*
 * ===================================================================================
 *  WIRING (adjust to the real PlaylistStore / PlaybackConnection API)
 * ===================================================================================
 *  This screen is pure UI: it takes the playlist, its songs and callbacks. Put the store
 *  lookup and playback calls in a thin route function, for example:
 *
 *  @Composable
 *  fun PlaylistDetailRoute(playlistId: String, onBack: () -> Unit) {
 *      val playlists by PlaylistStore.playlists.collectAsState()
 *      val playlist = playlists.firstOrNull { it.id == playlistId } ?: return
 *      val songs = remember(playlist) { PlaylistStore.getSongs(playlistId) }
 *
 *      PlaylistDetailScreen(
 *          playlist = playlist,
 *          songs = songs,
 *          onBack = onBack,
 *          onPlayAll = { songs.firstOrNull()?.let { PlaybackConnection.playSong(it, songs) } },
 *          onShuffle = { songs.shuffled().let { s -> s.firstOrNull()?.let { PlaybackConnection.playSong(it, s) } } },
 *          onSongClick = { _, song -> PlaybackConnection.playSong(song, songs) },
 *          songArt = { it.albumArtUri },            // <- whatever the Song model calls its art
 *          insets = LocalLuteaWindowInsets.current, // Law 1
 *          // onRename / onChangeCover / onDelete -> PlaylistStore calls
 *      )
 *  }
 * ===================================================================================
 */

// ------------------------------------------------------------------ tunables

private const val COVER_HEIGHT_FRACTION = 0.60f   // cover = 60% of the real container height
private const val TITLE_OVERLAP_FRACTION = 0.14f  // big title starts this much inside the cover's fade zone
private const val TOP_BAR_THRESHOLD = 0.55f       // scrollProgress where the frosted bar morphs in
private const val FROST_TINT_ALPHA = 0.78f
private const val FROST_SAMPLE_PX = 160
private val FrostBlur = 34.dp
private val TopBarContentHeight = 56.dp

/**
 * Playlist detail: cover art that dissolves into a dark, hue-tinted background colour taken
 * from the cover, a song list riding over it, and a frosted top bar that morphs in on scroll.
 *
 * Law 7: everything screen-relative is a fraction of BoxWithConstraints.maxWidth/maxHeight.
 * Law 1: pass LocalLuteaWindowInsets.current as [insets].
 * Law 9: forces light status/navigation bar icons while this screen is shown (the background
 *        is always dark), and restores the previous appearance on exit.
 *
 * @param onSongClick (index in [songs], song)
 * @param songArt     model for the row thumbnail (Uri / String / null). Null shows a note glyph.
 */
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    songs: List<Song>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onSongClick: (index: Int, song: Song) -> Unit,
    modifier: Modifier = Modifier,
    onRename: () -> Unit = {},
    onChangeCover: () -> Unit = {},
    onAddSongs: () -> Unit = {},
    onDelete: () -> Unit = {},
    songArt: (Song) -> Any? = { null },
    insets: WindowInsets = WindowInsets.systemBars,
) {
    DetailLightSystemBarIcons()

    val themeState = rememberCoverThemeColor(playlist.coverUri, fallbackSeed = playlist.id.toString())
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // solid extracted colour behind everything (read in the draw phase)
            .drawBehind { drawRect(themeState.value) },
    ) {
        val screenW = maxWidth
        val screenH = maxHeight
        val coverHeight = screenH * COVER_HEIGHT_FRACTION
        val coverHeightPx = with(density) { coverHeight.toPx() }.coerceAtLeast(1f)
        val titleOverlap = screenH * TITLE_OVERLAP_FRACTION
        val sidePad = screenW * 0.06f
        val thumbSize = screenW * 0.13f
        val topInset = with(density) { insets.getTop(this).toDp() }
        val bottomInset = with(density) { insets.getBottom(this).toDp() }

        // 0f..1f over the first coverHeight pixels of scrolling
        val progress = remember(listState, coverHeightPx) {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) {
                    1f
                } else {
                    (listState.firstVisibleItemScrollOffset / coverHeightPx).coerceIn(0f, 1f)
                }
            }
        }

        // frosted bar: spring in with a little overshoot, settle out more calmly
        val barShown by remember(progress) { derivedStateOf { progress.value > TOP_BAR_THRESHOLD } }
        val morph = animateFloatAsState(
            targetValue = if (barShown) 1f else 0f,
            animationSpec = spring(
                dampingRatio = if (barShown) 0.62f else 0.9f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "topBarMorph",
        )

        val totalMs = remember(songs) { songs.sumOf { detailDurationMs(it.duration) } }
        val subtitle = remember(songs, totalMs) { detailSubtitle(songs.size, totalMs) }

        // layer 0: cover + blend + scroll choreography
        PlaylistCoverBackground(
            coverUri = playlist.coverUri,
            backgroundColor = { themeState.value },
            coverHeight = coverHeight,
            scrollProgress = { progress.value },
            modifier = Modifier.align(Alignment.TopStart),
            placeholderSeed = playlist.id.toString(),
        )

        // layer 1: content. Top padding puts the header at the bottom of the cover's fade zone,
        // so the list "rides over" the blended background.
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = coverHeight - titleOverlap,
                bottom = bottomInset + screenH * 0.04f,
            ),
        ) {
            item(key = "header") {
                PlaylistHeader(
                    name = playlist.name,
                    subtitle = subtitle,
                    sidePad = sidePad,
                    progress = { progress.value },
                    onPlayAll = onPlayAll,
                    onShuffle = onShuffle,
                    onRename = onRename,
                    onChangeCover = onChangeCover,
                    onAddSongs = onAddSongs,
                    onDelete = onDelete,
                )
            }
            if (songs.isEmpty()) {
                item(key = "empty") { DetailEmptyHint(sidePad) }
            } else {
                itemsIndexed(songs) { index, song ->
                    PlaylistSongRow(
                        song = song,
                        art = songArt(song),
                        thumbSize = thumbSize,
                        sidePad = sidePad,
                        onClick = { onSongClick(index, song) },
                    )
                }
            }
        }

        // layer 2: frosted top bar (+ the back button, which is always visible)
        PlaylistTopBar(
            title = playlist.name,
            coverUri = playlist.coverUri,
            tint = { themeState.value },
            morph = { morph.value },
            topInset = topInset,
            sidePad = sidePad,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

// ------------------------------------------------------------------ header + action buttons

@Composable
private fun PlaylistHeader(
    name: String,
    subtitle: String,
    sidePad: Dp,
    progress: () -> Float,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onRename: () -> Unit,
    onChangeCover: () -> Unit,
    onAddSongs: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sidePad),
    ) {
        // big title: collapses toward the small one in the top bar as the bar morphs in
        DetailText(
            text = name,
            size = 38.sp,
            lineHeight = 42.sp,
            family = BillieEilishFamily,
            maxLines = 3,
            modifier = Modifier.graphicsLayer {
                val t = luteaSmoothstep(0.30f, TOP_BAR_THRESHOLD, progress())
                alpha = 1f - t
                val s = 1f - 0.12f * t
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0f, 1f)
            },
        )
        Spacer(Modifier.height(6.dp))
        DetailText(
            text = subtitle,
            size = 14.sp,
            color = Color.White.copy(alpha = 0.62f),
        )
        Spacer(Modifier.height(20.dp))

        // ---- action buttons: placeholders, reposition freely ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DetailPill(label = "Play", filled = true, onClick = onPlayAll) {
                DetailPlayGlyph(Color.Black, Modifier.size(18.dp))
            }
            DetailPill(label = "Shuffle", filled = false, onClick = onShuffle) {
                DetailShuffleGlyph(Color.White, Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            DetailMoreMenu(
                onRename = onRename,
                onChangeCover = onChangeCover,
                onAddSongs = onAddSongs,
                onDelete = onDelete,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DetailPill(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val bg = if (filled) Color.White else Color.White.copy(alpha = 0.14f)
    val fg = if (filled) Color.Black else Color.White
    Row(
        modifier = Modifier
            .height(48.dp)
            .detailPressable(onClick = onClick)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        DetailText(text = label, size = 15.sp, color = fg)
    }
}

@Composable
private fun DetailMoreMenu(
    onRename: () -> Unit,
    onChangeCover: () -> Unit,
    onAddSongs: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    Box {
        Box(
            modifier = Modifier
                .size(48.dp)
                .detailPressable { open = true }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .semantics { contentDescription = "More" },
            contentAlignment = Alignment.Center,
        ) {
            DetailDotsGlyph(Color.White, Modifier.size(20.dp))
        }

        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, with(density) { 52.dp.roundToPx() }),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .width(208.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1F1F24))
                        .padding(vertical = 6.dp),
                ) {
                    DetailMenuRow("Rename") { open = false; onRename() }
                    DetailMenuRow("Change cover") { open = false; onChangeCover() }
                    DetailMenuRow("Add songs") { open = false; onAddSongs() }
                    DetailMenuRow("Delete", destructive = true) { open = false; onDelete() }
                }
            }
        }
    }
}

@Composable
private fun DetailMenuRow(
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    DetailText(
        text = label,
        size = 15.sp,
        color = if (destructive) Color(0xFFFF6B6B) else Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .detailPressable(pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
    )
}

// ------------------------------------------------------------------ song rows

@Composable
private fun PlaylistSongRow(
    song: Song,
    art: Any?,
    thumbSize: Dp,
    sidePad: Dp,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .detailPressable(pressedScale = 0.985f, onClick = onClick)
            .padding(horizontal = sidePad, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetailSongThumb(art = art, size = thumbSize)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            DetailText(text = song.title.orEmpty(), size = 16.sp)
            Spacer(Modifier.height(2.dp))
            DetailText(
                text = song.artist.orEmpty(),
                size = 13.sp,
                color = Color.White.copy(alpha = 0.58f),
            )
        }
        Spacer(Modifier.width(12.dp))
        DetailText(
            text = detailClock(detailDurationMs(song.duration)),
            size = 12.sp,
            color = Color.White.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun DetailSongThumb(art: Any?, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        DetailNoteGlyph(Color.White.copy(alpha = 0.35f), Modifier.size(size * 0.5f))
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DetailEmptyHint(sidePad: Dp) {
    DetailText(
        text = "No songs yet. Add some from your library.",
        size = 14.sp,
        color = Color.White.copy(alpha = 0.55f),
        maxLines = 3,
        modifier = Modifier.padding(horizontal = sidePad, vertical = 24.dp),
    )
}

// ------------------------------------------------------------------ frosted top bar

/**
 * Back button (always visible) + frosted bar and small title that morph in after
 * scrollProgress > 0.55.
 *
 * Frost here = a heavily blurred copy of the cover + the extracted colour at 78%.
 * TODO: for a REAL backdrop blur of the song rows scrolling under the bar, replace the frost
 *       layer with kyant0's drawBackdrop (the same one the nav bar uses): wrap the
 *       LazyColumn in layerBackdrop(...) and drive blur strength with FrostBlur * morph.
 */
@Composable
private fun PlaylistTopBar(
    title: String,
    coverUri: String?,
    tint: () -> Color,
    morph: () -> Float,
    topInset: Dp,
    sidePad: Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val frostRequest = remember(coverUri) {
        if (coverUri.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(coverUri)
                .size(FROST_SAMPLE_PX, FROST_SAMPLE_PX)
                .build()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topInset + TopBarContentHeight),
    ) {
        // frost layer: fades in and slides down into place (the spring overshoot lives here)
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val m = morph()
                    alpha = m.coerceIn(0f, 1f)
                    translationY = -(1f - m) * size.height * 0.35f
                },
        ) {
            if (frostRequest != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AsyncImage(
                    model = frostRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = 0.6f }
                        .blur(FrostBlur),
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawRect(tint().copy(alpha = FROST_TINT_ALPHA))
                        drawRect(
                            color = Color.White.copy(alpha = 0.07f),
                            topLeft = Offset(0f, size.height - 1.dp.toPx()),
                            size = Size(size.width, 1.dp.toPx()),
                        )
                    },
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(TopBarContentHeight)
                .padding(horizontal = sidePad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailBackButton(morph = morph, onBack = onBack)
            Spacer(Modifier.width(12.dp))
            // small title: grows in from a larger size as the bar morphs
            DetailText(
                text = title,
                size = 17.sp,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        val m = morph().coerceIn(0f, 1f)
                        alpha = m
                        translationY = (1f - m) * 10.dp.toPx()
                        val s = 1.18f - 0.18f * m
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
            )
        }
    }
}

@Composable
private fun DetailBackButton(morph: () -> Float, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .detailPressable(onClick = onBack)
            .clip(CircleShape)
            // dark disc over the cover for legibility; fades out once the bar's own tint takes over
            .drawBehind {
                val m = morph().coerceIn(0f, 1f)
                drawRect(Color.Black.copy(alpha = 0.30f * (1f - m)))
            }
            .semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) {
        DetailBackGlyph(Color.White, Modifier.size(22.dp))
    }
}

// ------------------------------------------------------------------ small building blocks

/** BasicText wrapper so this file doesn't depend on Material. */
@Composable
private fun DetailText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    color: Color = Color.White,
    family: FontFamily = CalSansFamily,
    weight: FontWeight? = null,
    maxLines: Int = 1,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontFamily = family,
            fontWeight = weight,
            lineHeight = lineHeight,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Press-scale + click without a ripple (no Material dependency). */
@Composable
private fun Modifier.detailPressable(
    pressedScale: Float = 0.94f,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** Law 9: light status/nav bar icons while this (always dark) screen is up. */
@Composable
private fun DetailLightSystemBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.detailFindActivity()?.window
        if (window == null) {
            return@DisposableEffect onDispose { }
        }
        val controller = WindowCompat.getInsetsController(window, view)
        val previousStatus = controller.isAppearanceLightStatusBars
        val previousNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = previousStatus
            controller.isAppearanceLightNavigationBars = previousNav
        }
    }
}

private fun Context.detailFindActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

// ---- formatting. Assumes Song.duration is milliseconds (Long/Int/String all accepted). ----

private fun detailDurationMs(raw: Any?): Long = when (raw) {
    is Number -> raw.toLong()
    is String -> raw.toLongOrNull() ?: 0L
    else -> 0L
}

private fun detailClock(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSec / 60L
    val seconds = (totalSec % 60L).toString().padStart(2, '0')
    return if (minutes >= 60L) {
        "${minutes / 60L}:${(minutes % 60L).toString().padStart(2, '0')}:$seconds"
    } else {
        "$minutes:$seconds"
    }
}

private fun detailSubtitle(count: Int, totalMs: Long): String {
    if (count == 0) return "No songs yet"
    val totalMin = totalMs / 60_000L
    val hours = totalMin / 60L
    val mins = totalMin % 60L
    val length = if (hours > 0L) "$hours hr $mins min" else "$mins min"
    val noun = if (count == 1) "song" else "songs"
    return "$count $noun \u00B7 $length"
}

// ---- glyphs, drawn with Canvas so no icon library is needed ----

@Composable
private fun DetailPlayGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.24f, h * 0.14f)
            lineTo(w * 0.86f, h * 0.50f)
            lineTo(w * 0.24f, h * 0.86f)
            close()
        }
        drawPath(path, color)
        // round the corners a little
        drawPath(path, color, style = Stroke(width = w * 0.12f, join = StrokeJoin.Round))
    }
}

@Composable
private fun DetailShuffleGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            // line A: top-left -> bottom-right, arrow head at the end
            moveTo(w * 0.08f, h * 0.28f)
            lineTo(w * 0.30f, h * 0.28f)
            lineTo(w * 0.62f, h * 0.72f)
            lineTo(w * 0.86f, h * 0.72f)
            moveTo(w * 0.76f, h * 0.62f)
            lineTo(w * 0.86f, h * 0.72f)
            lineTo(w * 0.76f, h * 0.82f)
            // line B: bottom-left -> top-right, with a gap where it passes under line A
            moveTo(w * 0.08f, h * 0.72f)
            lineTo(w * 0.30f, h * 0.72f)
            lineTo(w * 0.40f, h * 0.58f)
            moveTo(w * 0.52f, h * 0.42f)
            lineTo(w * 0.62f, h * 0.28f)
            lineTo(w * 0.86f, h * 0.28f)
            moveTo(w * 0.76f, h * 0.18f)
            lineTo(w * 0.86f, h * 0.28f)
            lineTo(w * 0.76f, h * 0.38f)
        }
        drawPath(
            path,
            color,
            style = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun DetailBackGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.64f, h * 0.18f)
            lineTo(w * 0.32f, h * 0.50f)
            lineTo(w * 0.64f, h * 0.82f)
        }
        drawPath(
            path,
            color,
            style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun DetailDotsGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.width * 0.09f
        val cy = size.height / 2f
        for (i in 0..2) {
            drawCircle(color, radius = r, center = Offset(size.width * (0.2f + 0.3f * i), cy))
        }
    }
}

@Composable
private fun DetailNoteGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color, radius = w * 0.13f, center = Offset(w * 0.36f, h * 0.70f))
        val stem = Path().apply {
            moveTo(w * 0.48f, h * 0.70f)
            lineTo(w * 0.48f, h * 0.24f)
            lineTo(w * 0.74f, h * 0.38f)
        }
        drawPath(
            stem,
            color,
            style = Stroke(width = w * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
