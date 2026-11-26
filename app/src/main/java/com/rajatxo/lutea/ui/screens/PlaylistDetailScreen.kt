package com.rajatxo.lutea.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.rajatxo.lutea.data.model.Playlist
import com.rajatxo.lutea.data.store.FavouriteStore
import com.rajatxo.lutea.data.store.PlaylistStore
import com.rajatxo.lutea.domain.model.Song
import com.rajatxo.lutea.playback.PlaybackConnection
import com.rajatxo.lutea.ui.components.CalSansFamily
import com.rajatxo.lutea.ui.foundation.LuteaTabCapsule
import com.rajatxo.lutea.ui.foundation.fluidNavMetrics
import com.rajatxo.lutea.ui.foundation.navBarWidthFor
import com.rajatxo.lutea.ui.player.PlayerSheet
import com.rajatxo.lutea.ui.util.LuteaTab
import com.rajatxo.lutea.ui.util.rememberCoverThemeColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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

private const val COVER_HEIGHT_FRACTION = 0.60f   // square cover side, capped at 60% of height
// Remembered song order (shared across playlists and app restarts).
private const val KEY_SORT_MODE = "playlist_sort_mode"
private const val KEY_SORT_DESC = "playlist_sort_desc"
// The header used to start 14% of screen-height INSIDE the cover's fade zone, so the
// playlist name overlapped the cover art. Now the header starts flush at the cover's
// bottom edge (0 overlap) plus a small breathing gap below the cover.
private const val TITLE_OVERLAP_FRACTION = 0.10f  // title starts a little inside the cover's fade zone
private val TopBarContentHeight = 56.dp
private val ActionButtonSize = 44.dp
private val BackButtonSize = 44.dp   // matches the menu + search circles so all three read equal
private val PlaylistStatsHeight = 56.dp
private val CustomOrderHeight = 32.dp                 // thinner custom-order capsule
private val PlayCapsulePadding = 58.dp                // wider play/pause capsule
private val TitleToStatsGapDp = 0.dp                  // title sits flush against the stats row
private val StatsToActionsGapDp = 6.dp                // tighter gap between stats and the 3 buttons
private val ActionsToCustomOrderGapDp = 10.dp          // gap between buttons and custom order
private val TitleFontSize = 32.sp                     // slightly smaller playlist name
private val TitleLineHeight = 36.sp
// Top-blur mask: full strength down to this fraction of the band, then a
// smoothstep fade to zero. Many stops keep the fade free of visible steps.
private const val BLUR_MASK_START = 0.28f
private const val BLUR_MASK_STEPS = 24

/**
 * Playlist detail: cover art that rides up, zooms, dims, dissolves and blurs as the list
 * scrolls over it (Mochy's ScrollAwayCover, exactly), dissolving into a dark hue-tinted
 * background colour taken from the cover, with a song list riding over it. Once the header
 * is mostly gone, a progressive backdrop blur fades in below the status bar and eases back
 * to zero 96dp down — Mochy's top-bar glass, without a hard edge.
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
    onCustomOrder: () -> Unit = {},
    /**
     * Add a SPECIFIC song (from its three-dot menu) to one or more OTHER
     * existing playlists. Distinct from [onAddSongs] (which picks songs to
     * add to THIS playlist from the top menu).
     */
    onAddSongToPlaylists: (Song) -> Unit = {},
    songArt: (Song) -> Any? = { null },
    insets: WindowInsets = WindowInsets.systemBars,
    activeTab: LuteaTab = LuteaTab.Library,
    onTabSelected: (LuteaTab) -> Unit = {},
) {
    DetailLightSystemBarIcons()
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var playerVisible by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    val playerPageProgress = if (showLyrics || showQueue) 1f else 0f
    var menuOpen by remember { mutableStateOf(false) }
    // Custom-order (sort) menu + its current mode.
    var customOrderOpen by remember { mutableStateOf(false) }
    // The chosen sort order is remembered app-wide (not per playlist), so it
    // survives leaving the screen and restarting the app. Default: most played
    // first (listening time, descending).
    val context = LocalContext.current
    val sortPrefs = remember {
        context.getSharedPreferences("lutea_prefs", Context.MODE_PRIVATE)
    }
    var sortMode by remember {
        mutableStateOf(
            sortPrefs.getString(KEY_SORT_MODE, null)
                ?.let { name -> PlaylistSortMode.entries.firstOrNull { it.name == name } }
                ?: PlaylistSortMode.LISTENING_TIME
        )
    }
    // Ascending/descending toggle for the current sort. Most-played-first means
    // descending for listening-time; A→Z means ascending for title. The toggle
    // just flips whichever order the active mode produced.
    var sortDescending by remember { mutableStateOf(sortPrefs.getBoolean(KEY_SORT_DESC, false)) }
    LaunchedEffect(sortMode) { sortPrefs.edit().putString(KEY_SORT_MODE, sortMode.name).apply() }
    LaunchedEffect(sortDescending) {
        sortPrefs.edit().putBoolean(KEY_SORT_DESC, sortDescending).apply()
    }
    // Lock on the custom-order capsule: locked (default) = sort menu only;
    // unlocked = manual drag-reorder is enabled. The lock circle on the right
    // of the capsule toggles it.
    var manualOrderUnlocked by remember(playlist.id) { mutableStateOf(false) }
    // Manual-order working copy: while unlocked, the list shows this order and
    // drag-reorder mutates it directly. Re-locking (or leaving) keeps whatever
    // was last dropped — each drop already persists to the store.
    var manualOrder by remember(playlist.id) { mutableStateOf<List<Song>?>(null) }
    // Per-song three-dot menu + which song it targets.
    var songMenuSong by remember { mutableStateOf<Song?>(null) }
    // Per-song editor (opened from the song menu's "Edit" option).
    var editingSong by remember { mutableStateOf<Song?>(null) }
    val favourites by FavouriteStore.favourites.collectAsState()

    // Glass-capsule notification for remove-from-playlist / favourite, with an
    // undo action. `undoableMessage` is what the capsule shows; `undoAction`
    // runs when Undo is tapped (restores the removed song or unfavourites).
    var undoableMessage by remember { mutableStateOf<String?>(null) }
    var undoAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Auto-dismiss the capsule after a short delay.
    LaunchedEffect(undoableMessage) {
        if (undoableMessage != null) {
            kotlinx.coroutines.delay(3200)
            undoableMessage = null
            undoAction = null
        }
    }
    // Back closes the topmost layer first: lyrics, player, editor, song menu,
    // custom order, playlist menu, search, then the page.
    BackHandler(enabled = !playerVisible && !showLyrics && !showQueue && !searchOpen && !menuOpen && !customOrderOpen && songMenuSong == null && editingSong == null) { onBack() }
    BackHandler(enabled = searchOpen) { searchOpen = false }
    BackHandler(enabled = menuOpen) { menuOpen = false }
    BackHandler(enabled = customOrderOpen) { customOrderOpen = false }
    BackHandler(enabled = songMenuSong != null) { songMenuSong = null }
    BackHandler(enabled = editingSong != null) { editingSong = null }
    BackHandler(enabled = showQueue) { showQueue = false }
    BackHandler(enabled = showLyrics && !showQueue) { showLyrics = false }
    BackHandler(enabled = playerVisible && !showLyrics && !showQueue) { playerVisible = false }
    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            menuOpen = false
            customOrderOpen = false
            songMenuSong = null
            editingSong = null
        } else {
            searchQuery = ""
        }
    }

    // Only this screen's own play/shuffle/song taps count as this playlist playing.
    // Playback started elsewhere (mini player, Quick Picks) must leave the capsule on Play.
    val playing by PlaybackConnection.isPlaying.collectAsState()
    var startedHere by remember(playlist.id) { mutableStateOf(false) }
    val showPause = startedHere && playing
    val onPlayPause = {
        if (startedHere && PlaybackConnection.currentSong.value != null) {
            PlaybackConnection.playPause()
        } else {
            startedHere = true
            onPlayAll()
        }
    }
    val onShuffleHere = { startedHere = true; onShuffle() }
    val onSongHere: (Int, Song) -> Unit = { i, song -> startedHere = true; onSongClick(i, song) }
    // Custom order opens the sort menu (the param default {} is overridden here
    // so the screen owns the menu state). Selecting a mode just re-sorts the
    // display; POSITION keeps the stored order.
    val onCustomOrderHere = { customOrderOpen = true }

    val themeState = rememberCoverThemeColor(playlist.coverUri, fallbackSeed = playlist.id.toString())
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Manual drag-reorder controller. While the lock is unlocked, long-press
    // any song row to lift it and drag; the others slide aside live. Each drop
    // persists the new order to the playlist store.
    val reorderState = rememberDragDropListState(
        listState = listState,
        scope = scope,
        songIds = { (manualOrder ?: songs).map { it.id } },
        onReorder = { from, to ->
            val base = manualOrder ?: songs
            val mut = base.toMutableList().apply {
                val item = removeAt(from)
                add(to.coerceIn(0, size), item)
            }
            manualOrder = mut
        },
        onDragEnd = { orderedIds ->
            PlaylistStore.reorderPlaylistSongs(playlist.id, orderedIds)
        },
    )
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        val screenW = maxWidth
        val screenH = maxHeight
        // Playlist covers are always shown 1:1. Use the full width on a phone,
        // but never taller than 60% of the screen (landscape / large displays).
        val coverHeight = minOf(screenW, screenH * COVER_HEIGHT_FRACTION)
        val coverHeightPx = with(density) { coverHeight.toPx() }.coerceAtLeast(1f)
        val titleOverlap = screenH * TITLE_OVERLAP_FRACTION
        val titleOverlapPx = with(density) { titleOverlap.toPx() }
        // contentPadding.top in pixels = coverHeight - titleOverlap (the space above the header).
        val contentTopPx = coverHeightPx - titleOverlapPx
        val sidePad = screenW * 0.06f
        val thumbSize = screenW * 0.13f
        val topInset = with(density) { insets.getTop(this).toDp() }
        val topInsetPx = with(density) { topInset.toPx() }
        val bottomInset = with(density) { insets.getBottom(this).toDp() }
        // The caller may pad `insets.bottom` with extra room so the song list
        // can scroll clear of the nav + mini player. But the NAV itself must
        // sit at the real system bottom (same as the main app), not 96dp up.
        // So we re-derive the true navigation-bar inset from the system bars
        // here, and use [bottomInset] only for the LazyColumn's content padding.
        val systemNavBottom = with(density) {
            WindowInsets.navigationBars.getBottom(this).toDp()
        }
        val navBottom = maxOf(systemNavBottom, 48.dp)

        // 0f..1f as the FIRST SONG ROW reaches the top of the screen.
        //
        // We read the header item's actual offset from layoutInfo and derive progress from THAT.
        // LazyListItemInfo.offset is 0 at rest (the header at the content-area top) and goes
        // negative as the header scrolls up. So scrolledFromRest = -header.offset — NOT
        // contentTopPx - header.offset (that assumed viewport coords and gave progress ≈ 0.74 at
        // rest, which made the cover's alpha ~0.07 and the cover was invisible at rest).
        //
        // The denominator is contentTopPx + headerHeight — the total scroll needed to bring the
        // first song row to the top of the viewport. This makes the cover's full parallax unfold
        // over exactly the scroll that brings the songs up, so cover and list move together with
        // no gap. When the header leaves visibleItemsInfo, we return 1f if it scrolled off, or 0f
        // if it just hasn't been laid out yet (first frame) — so the cover is visible from frame
        // one instead of flashing invisible.
        val progress = remember(listState, contentTopPx) {
            derivedStateOf {
                val header = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == "header" }
                if (header == null) {
                    // header not in visibleItemsInfo: either not laid out yet (index 0, at rest)
                    // or scrolled fully off (index > 0). Assume rest if index 0, done if index > 0.
                    if (listState.firstVisibleItemIndex == 0) 0f else 1f
                } else {
                    val headerH = header.size.toFloat()
                    val scrolledFromRest = -header.offset.toFloat()
                    val denom = (contentTopPx + headerH).coerceAtLeast(1f)
                    (scrolledFromRest / denom).coerceIn(0f, 1f)
                }
            }
        }

        // Title and shuffle stay in the header and scroll with it. Only search detaches
        // and animates into the top bar, so no title docking/scale/fade state is needed.
        val topBarContentPx = with(density) { TopBarContentHeight.toPx() }
        val defaultTitleHeightPx = with(density) { 40.dp.toPx() }
        val titleToStatsGapPx = with(density) { TitleToStatsGapDp.toPx() }
        val statsHeightPx = with(density) { PlaylistStatsHeight.toPx() }
        val actionsGapPx = with(density) { StatsToActionsGapDp.toPx() }

        var titleSize by remember { mutableStateOf(IntSize.Zero) }
        val titleHeightPx = if (titleSize.height > 0) {
            titleSize.height.toFloat()
        } else {
            defaultTitleHeightPx
        }

        val screenHPx = with(density) { screenH.toPx() }
        val rawTitleTop = remember(listState, contentTopPx, screenHPx) {
            derivedStateOf {
                val header = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == "header" }
                // Once the header leaves the viewport, keep the overlay fully off-screen.
                // Using -contentTopPx pinned the title at y=0 under the top bar.
                val headerOffset = header?.offset?.toFloat()
                    ?: if (listState.firstVisibleItemIndex == 0) 0f else -screenHPx
                contentTopPx + headerOffset
            }
        }
        val headerTop: () -> Float = { rawTitleTop.value }
        val statsTop: () -> Float = { headerTop() + titleHeightPx + titleToStatsGapPx }
        val actionsTop: () -> Float = {
            statsTop() + statsHeightPx + actionsGapPx
        }

        // ★ Scroll-driven search docking (Mochy pattern).
        // As the header rises, the search button's natural rest position rises
        // with it. Once that rest position meets the top-bar dock row, the
        // button morphs into the docked capsule shape — purely from scroll, no
        // tap. Songs then scroll BEHIND the docked capsule.
        //
        // scrollDockProgress in [0,1]:
        //   0 = header at rest (search button still in its actions row)
        //   1 = search rest position has risen to the dock row
        // The window over which this happens is [actionsTop at rest, dockY].
        val searchDockY = topInsetPx +
            (topBarContentPx - with(density) { ActionButtonSize.toPx() }) / 2f
        // At-rest Y positions (header top = contentTopPx, before any scroll):
        //   statsTop_rest  = contentTopPx + titleHeightPx + titleToStatsGapPx
        //   actionsTop_rest = statsTop_rest + statsHeightPx + actionsGapPx
        val actionsTopAtRestPx = contentTopPx + titleHeightPx + titleToStatsGapPx +
            statsHeightPx + actionsGapPx
        val scrollDockSpanPx = (actionsTopAtRestPx - searchDockY).coerceAtLeast(1f)
        val scrollDockProgress by remember(progress, actionsTopAtRestPx) {
            derivedStateOf {
                // How far the header's actions row has RISEN from rest = the
                // difference between at-rest and current actionsTop().
                val rise = (actionsTopAtRestPx - actionsTop()).coerceAtLeast(0f)
                (rise / scrollDockSpanPx).coerceIn(0f, 1f)
            }
        }
        // ── Scroll capsule reveal ─────────────────────────────────────────────
        // There is NO circle→capsule morph anymore. The header search circle
        // just scrolls away with the rest of the header. As the header hides
        // (name / numbers / three buttons / custom-order all leaving), a separate
        // docked capsule SMOOTHLY slides down from the status bar and fades in,
        // settling at the middle of the top-bar row. Tapping it opens the search
        // page. The reveal is timed to the header's exit, not a hard threshold.
        //
        // scrollDockProgress is the header's rise (0 = at rest, 1 = actions row at
        // the baseline). The capsule enters as the LAST of the header leaves, so
        // we map a late slice of the rise to a 0→1 reveal.
        val scrollCapsuleReveal by remember(progress) {
            derivedStateOf {
                // Header fully gone by rise ≈ 0.78; capsule appears across 0.62→0.86.
                ((scrollDockProgress - 0.62f) / 0.24f).coerceIn(0f, 1f)
            }
        }
        // Eased for a soft fade-in; read in the layout/draw phase. A longer
        // spring-free tween gives a buttery slide-down AND slide-up (the same
        // spec drives both directions since the target is the derived reveal).
        val scrollCapsuleAlpha by animateFloatAsState(
            targetValue = scrollCapsuleReveal,
            animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
            label = "playlistScrollCapsule",
        )

        val searchCapsuleWidth =
            (screenW - sidePad * 2f - 104.dp).coerceAtLeast(120.dp)

        val totalMs = remember(songs) { songs.sumOf { detailDurationMs(it.duration) } }
        val statsSongCount = songs.size.toString()
        val statsDuration = remember(totalMs) { detailStatsDuration(totalMs) }
        val statsCreated = remember(playlist.createdAtMs) { detailCreatedDate(playlist.createdAtMs) }
        // The main list shows every song, ordered by the current sort mode.
        // POSITION keeps the playlist's stored order; the rest re-derive a
        // display-only order (the underlying playlist order is untouched unless
        // the user picks POSITION after a manual reorder, which persists).
        val stats by com.rajatxo.lutea.data.store.PlaybackStatsStore.stats.collectAsState()
        // While the lock is UNLOCKED the list shows the manual working order
        // (drag-reorder mutates it live); while locked it shows the sorted view.
        val visibleSongs = if (manualOrderUnlocked && manualOrder != null) {
            manualOrder!!
        } else {
            remember(songs, sortMode, sortDescending, stats) {
                val ordered: List<Song> = when (sortMode) {
                    PlaylistSortMode.TITLE -> songs.sortedBy { it.title.lowercase() }
                    PlaylistSortMode.ARTIST -> songs.sortedBy { it.artist.lowercase() }
                    PlaylistSortMode.ALBUM -> songs.sortedBy { it.album.lowercase() }
                    PlaylistSortMode.DATE_ADDED -> songs.sortedByDescending { it.dateAdded }
                    PlaylistSortMode.DURATION -> songs.sortedBy { detailDurationMs(it.duration) }
                    // Listening time = real accumulated play time per song.
                    PlaylistSortMode.LISTENING_TIME ->
                        songs.sortedByDescending { stats[it.id] ?: 0L }
                }
                if (sortDescending) ordered.reversed() else ordered
            }
        }
        // Seeding / reconciling the manual working order:
        // - Unlock or a new sort selection: seed from the displayed (sorted)
        //   order so dragging starts from what the user sees.
        // - Songs added/removed while unlocked: keep the manual order for the
        //   songs still present, append new ones at the end.
        var lastSortKey by remember(playlist.id) { mutableStateOf<Any?>(null) }
        val sortKey = Triple(sortMode, sortDescending, songs)
        LaunchedEffect(manualOrderUnlocked, sortKey) {
            if (!manualOrderUnlocked) return@LaunchedEffect
            val sortChanged = lastSortKey != null && lastSortKey != sortKey
            lastSortKey = sortKey
            val prev = manualOrder
            manualOrder = when {
                prev == null || sortChanged -> visibleSongs
                else -> {
                    val byId = songs.associateBy { it.id }
                    val prevIds = prev.map { it.id }.toHashSet()
                    prev.mapNotNull { byId[it.id] } + songs.filter { it.id !in prevIds }
                }
            }
        }

        // The cover + song list are captured together so the top glass and the
        // progressive blur sample what is actually scrolling underneath, not
        // just the cover art.
        val coverScroll = remember(progress) { { progress.value } }
        val coverColor = remember(themeState) { { themeState.value } }
        val pageGraphicsLayer = rememberGraphicsLayer()
        val pageBackdrop = rememberLayerBackdrop(graphicsLayer = pageGraphicsLayer) {
            drawContent()
        }

        // The top-bar frost used to snap on at a hard threshold (progress > 0.55)
        // which made the playlist name "suddenly blur" the moment you crossed it.
        // Instead the frost is ALWAYS present and eases in continuously across the
        // whole scroll: 0 at rest, full by progress 0.55. So the title passes
        // THROUGH an already-existing gradient instead of hitting a wall. Disabled
        // (fully transparent) while search is open so the docked field stays sharp.
        val blurVisible by remember(progress, searchOpen) {
            derivedStateOf { progress.value > 0.001f && !searchOpen }
        }
        // Continuous eased morph in [0,1] — no spring, no threshold. A fast-out
        // curve makes the frost grow with the scroll, not pop.
        val blurMorph by animateFloatAsState(
            targetValue = if (!searchOpen) {
                ((progress.value - 0.001f) / 0.55f).coerceIn(0f, 1f)
            } else 0f,
            animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
            label = "playlistTopBlur",
        )
        // The top frost is kept ALWAYS composed (its alpha is 0 at rest) so the
        // backdrop capture texture + blur shader warm up on the first frame —
        // otherwise the first scroll pays the compile/allocation cost and feels
        // heavy. The blur draw itself is skipped when morph is ~0 (no visible
        // blur), so there is no steady-state cost.
        val blurActive by remember {
            derivedStateOf { blurVisible }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Capture layer: the real page behind the top bar. It stays outside
            // the LazyColumn, per Kyant's scrollable rule.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(pageBackdrop),
            ) {
                PlaylistPageBackground(
                    coverUri = playlist.coverUri,
                    backgroundColor = coverColor,
                    coverHeight = coverHeight,
                    scrollProgress = coverScroll,
                    placeholderSeed = playlist.id.toString(),
                    enableBlur = true,
                )

                // Top padding puts the header at the bottom of the cover's fade zone,
                // so the list "rides over" the blended background.
                // Bottom padding clears the nav capsule (56dp) + mini player
                // (56dp) + a little breathing room so the last song scrolls clear.
                // While the custom-order lock is UNLOCKED, long-press starts a
                // drag-reorder (RiMusic style: the row lifts as an elevated
                // card and the others slide aside).
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (manualOrderUnlocked) {
                                Modifier.pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            if (!reorderState.onDragStart(offset)) {
                                                // Not on a song row — ignore.
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            reorderState.onDrag(dragAmount)
                                        },
                                        onDragEnd = { reorderState.onDrop() },
                                        onDragCancel = { reorderState.onDragCancel() },
                                    )
                                }
                            } else {
                                Modifier
                            },
                        ),
                    contentPadding = PaddingValues(
                        top = coverHeight - titleOverlap,
                        bottom = navBottom + 112.dp + screenH * 0.04f,
                    ),
                ) {
                    item(key = "header") {
                        PlaylistHeaderSpacer(
                            name = playlist.name,
                            sidePad = sidePad,
                        )
                    }
                    if (visibleSongs.isEmpty()) {
                        item(key = "empty") {
                            DetailEmptyHint(
                                sidePad = sidePad,
                                searching = false,
                            )
                        }
                    } else {
                        itemsIndexed(visibleSongs, key = { _, song -> song.id }) { i, song ->
                            val isDragged = reorderState.draggedItemId == song.id
                            Column(
                                modifier = Modifier
                                    // Smooth glide when the list re-sorts (e.g. the
                                    // listening-time mode bumps a song up the list
                                    // as it plays) — disabled mid-drag so the held
                                    // row follows the finger with no animation lag.
                                    .then(if (!isDragged) Modifier.animateItem() else Modifier)
                                    .zIndex(if (isDragged) 1f else 0f)
                                    .graphicsLayer {
                                        translationY = if (isDragged) reorderState.dragOffset else 0f
                                        val scale = if (isDragged) 1.04f else 1f
                                        scaleX = scale
                                        scaleY = scale
                                        shadowElevation = if (isDragged) 24.dp.toPx() else 0f
                                        shape = RoundedCornerShape(14.dp)
                                        clip = false
                                    },
                            ) {
                                PlaylistSongRow(
                                    song = song,
                                    art = songArt(song),
                                    thumbSize = thumbSize,
                                    sidePad = sidePad,
                                    showDragHandle = manualOrderUnlocked,
                                    onClick = { onSongHere(i, song) },
                                    onMore = { songMenuSong = song },
                                )
                                if (i < visibleSongs.lastIndex) {
                                    DetailRowDivider(sidePad = sidePad, thumbSize = thumbSize)
                                }
                            }
                        }
                    }
                }

                // Title and stats are part of the captured page, so they soften
                // under the glass just like the rows do.
                PlaylistTitleOverlay(
                    name = playlist.name,
                    sidePad = sidePad,
                    headerTop = headerTop,
                    onSize = { titleSize = it },
                )
                PlaylistStatsOverlay(
                    songCount = statsSongCount,
                    duration = statsDuration,
                    dateDay = statsCreated.first,
                    dateMonth = statsCreated.second,
                    sidePad = sidePad,
                    statsTop = statsTop,
                )
                // Header actions (shuffle / play / custom-order) live INSIDE the
                // captured page so the top frost blurs them as they rise. Custom
                // order is hidden once search is open (everything above the song
                // list is gone on the search page). The search capsule is NOT here
                // — it has its own top-layer dock above the frost.
                PlaylistHeaderActionsOverlay(
                    actionsTop = actionsTop,
                    isPlaying = showPause,
                    onPlayPause = onPlayPause,
                    onShuffle = onShuffleHere,
                    onCustomOrder = onCustomOrderHere,
                    onOpenSearch = { searchOpen = true },
                    locked = !manualOrderUnlocked,
                    onToggleLock = { manualOrderUnlocked = !manualOrderUnlocked },
                )
            }

            // Real backdrop blur, full at the status bar and eased to zero
            // 96dp below it. Drawn above the captured page (so it frosts the
            // shuffle/play/search/custom-order + title + stats + songs), below
            // the chrome. The search page is a separate full overlay, so this
            // frost is just the scroll frost.
            if (blurActive) {
                PlaylistTopBlur(
                    backdrop = pageBackdrop,
                    morph = { blurMorph },
                    height = topInset + 96.dp,
                    tint = coverColor,
                )
            }

            // ★ Scroll-reveal search capsule — TOP LAYER. Slides down from the
            //    status bar and fades in as the header leaves (name / numbers /
            //    three buttons / custom-order all exiting). Settles centered in
            //    the top-bar row. Tapping opens the search page. Hidden while the
            //    search page is open (the page has its own field).
            if (!searchOpen) {
                val screenWPx = with(density) { screenW.toPx() }
                PlaylistScrollSearchCapsule(
                    reveal = scrollCapsuleAlpha,
                    searchCapsuleWidth = searchCapsuleWidth,
                    onOpenSearch = { searchOpen = true },
                    dockCenterX = { screenWPx / 2f },
                    dockY = searchDockY,
                    backdrop = pageBackdrop,
                )
            }

            // Back + menu. Transparent bar, so songs pass behind the buttons.
            PlaylistTopBar(
                searchOpen = searchOpen,
                onCloseSearch = { searchOpen = false },
                onMenuToggle = { menuOpen = true },
                topInset = topInset,
                sidePad = sidePad,
                onBack = onBack,
                backdrop = pageBackdrop,
                modifier = Modifier.align(Alignment.TopStart),
            )

            // ★ Search page — a separate full-screen overlay with its own field
            //    and a filtered song list, themed to the cover color. Slides up
            //    over the playlist page when the search button is tapped.
            //    Drawn BEFORE the bottom chrome and the sheet, so the nav tabs,
            //    the mini player and the song menu all stay above it.
            if (searchOpen) {
                PlaylistSearchPage(
                    playlistName = playlist.name,
                    songs = songs,
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = { searchOpen = false },
                    onSongClick = onSongHere,
                    onMore = { songMenuSong = it },
                    songArt = songArt,
                    backgroundColor = coverColor,
                    topInset = topInset,
                    sidePad = sidePad,
                    thumbSize = thumbSize,
                    navBottom = navBottom,
                )
            }

            // Bottom chrome, same as the main app: nav capsule + mini player.
            // The caller adds matching bottom insets so songs can scroll clear.
            val navMetrics = fluidNavMetrics(
                maxWidth = screenW,
                maxHeight = screenH,
                tabCount = LuteaTab.entries.size,
                fontScale = density.fontScale,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(navBottom + 112.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Black.copy(alpha = 0.35f),
                            1f to Color.Black,
                        )
                    ),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navBottom),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LuteaTabCapsule(
                    tabs = LuteaTab.entries.toList(),
                    activeTab = activeTab,
                    onTabSelected = onTabSelected,
                    backdrop = pageBackdrop,
                )
            }
            // Glass playlist menu, drawn over the page so it can sample the cover.
            if (menuOpen) {
                PlaylistMenuOverlay(
                    top = topInset + TopBarContentHeight + 4.dp,
                    endPad = sidePad,
                    backdrop = pageBackdrop,
                    onDismiss = { menuOpen = false },
                    onRename = onRename,
                    onChangeCover = onChangeCover,
                    onAddSongs = onAddSongs,
                    onDelete = onDelete,
                )
            }
            // Custom-order (sort) menu — RiPlay's full sort set + a leading Pin.
            if (customOrderOpen) {
                PlaylistCustomOrderMenu(
                    anchorY = topInset + TopBarContentHeight + 4.dp,
                    endPad = sidePad,
                    current = sortMode,
                    descending = sortDescending,
                    backdrop = pageBackdrop,
                    onDismiss = { customOrderOpen = false },
                    onSelect = { mode ->
                        sortMode = mode
                    },
                    onToggleDirection = { sortDescending = !sortDescending },
                )
            }
            // Per-song three-dot menu — Mochy-style action set.
            songMenuSong?.let { song ->
                PlaylistSongMenu(
                    song = song,
                    isFavourite = song.id in favourites,
                    anchorY = topInset + TopBarContentHeight + 4.dp,
                    endPad = sidePad,
                    backdrop = pageBackdrop,
                    onDismiss = { songMenuSong = null },
                    onFavourite = {
                        val wasFav = song.id in favourites
                        FavouriteStore.toggle(song.id)
                        undoableMessage = if (wasFav) "Removed from favourites" else "Added to favourites"
                        undoAction = { FavouriteStore.toggle(song.id) }
                    },
                    onAddToPlaylist = { onAddSongToPlaylists(song) },  // pick OTHER playlists for THIS song
                    onViewAlbum = {},                      // album page: later pass
                    onViewArtist = {},                     // artist page: later pass
                    onEdit = { editingSong = song },       // opens the song editor
                    onAddToQueue = { PlaybackConnection.addToQueue(song) },
                    onPinToHome = {},                      // pin to home: later pass
                    onRemoveFromPlaylist = {
                        PlaylistStore.removeSongFromPlaylist(playlist.id, song.id)
                        undoableMessage = "Removed from playlist"
                        undoAction = { PlaylistStore.addSongToPlaylist(playlist.id, song.id) }
                    },
                    onDetails = {},                        // details sheet: later pass
                )
            }
            // Per-song editor (full-screen) — opened from the song menu's Edit.
            editingSong?.let { song ->
                PlaylistSongEditSheet(
                    song = song,
                    onDismiss = { editingSong = null },
                )
            }
            PlayerSheet(
                modifier = Modifier.fillMaxSize(),
                expanded = playerVisible,
                onExpandedChange = { playerVisible = it },
                onLyricsClick = { showLyrics = true },
                onQueueOpen = { showQueue = true },
                pageProgress = playerPageProgress,
                backdrop = pageBackdrop,
                navMatchedWidth = navMetrics.navBarWidthFor(LuteaTab.entries.size),
                miniBottomPadding = navBottom + 56.dp,
            )
            if (showLyrics) {
                LyricsScreen(onClose = { showLyrics = false })
            }
            if (showQueue) {
                QueueScreen(onClose = { showQueue = false })
            }
            // Persistent control bar — seek + transport stay over the player,
            // lyrics and queue pages (Mochy Flagship).
            if (playerVisible) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = playerVisible,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = androidx.compose.animation.core.tween(300)
                    ),
                    exit = androidx.compose.animation.fadeOut(
                        animationSpec = androidx.compose.animation.core.tween(200)
                    ),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        com.rajatxo.lutea.ui.player.PlayerControlBar(
                            onClose = { playerVisible = false; showLyrics = false; showQueue = false },
                            onQueueClick = { showQueue = true },
                            onLyricsClick = { showLyrics = true },
                            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                        )
                    }
                }
            }
            // Glass-morphism notification capsule (remove-from-playlist /
            // favourite) with an Undo button. Placed exactly like the search
            // tab's hold-to-delete capsule: centred, clear of the nav (48dp)
            // and lifted above the mini player (+56dp) so nothing overlaps.
            undoableMessage?.let { msg ->
                DetailUndoCapsule(
                    message = msg,
                    onUndo = {
                        undoAction?.invoke()
                        undoableMessage = null
                        undoAction = null
                    },
                    backdrop = pageBackdrop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navBottom + 120.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Manual drag-to-reorder (RiMusic/RiPlay style)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tracks a long-press drag-reorder of the song list, Mochi/Calvin-style:
 * the held row stays GLUED to the finger (its offset = raw drag delta),
 * and other rows only slide aside when the held row's centre crosses a
 * neighbour's midpoint — each swap is a single step, so nothing jumps
 * erratically. On drop the final order is reported for persistence.
 *
 * Only active when the custom-order lock is unlocked.
 */
class DragDropListState(
    private val listState: LazyListState,
    private val onReorder: (Int, Int) -> Unit,
    private val onDragEnd: (List<Long>) -> Unit = {},
    private val scope: CoroutineScope,
    private val songIdsProvider: () -> List<Long>,
) {
    var dragging by mutableStateOf(false)
        private set
    var draggedItemId by mutableStateOf<Long?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    private var draggedLazyIndex = -1   // LazyColumn item index (header = 0)

    fun onDragStart(offset: Offset): Boolean {
        // Only song rows participate (their key is the song id, a Long; the
        // header/empty items use string keys and are filtered out).
        val info = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { offset.y >= it.offset && offset.y <= it.offset + it.size } ?: return false
        val itemId = info.key as? Long ?: return false
        draggedLazyIndex = info.index
        draggedItemId = itemId
        dragging = true
        return true
    }

    fun onDrag(offset: Offset) {
        dragOffset += offset.y
        val info = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == draggedItemId } ?: return
        // The held row's visual centre, in viewport coordinates.
        val draggedCentre = info.offset + info.size / 2f + dragOffset
        val draggedSongIndex = draggedLazyIndex - 1
        // Walk the visible rows top→bottom; swap exactly one neighbour per
        // crossing so nothing jumps.
        for (other in listState.layoutInfo.visibleItemsInfo.sortedBy { it.offset }) {
            if (other.key == draggedItemId) continue
            val otherCentre = other.offset + other.size / 2f
            val otherSongIndex = other.index - 1
            if (otherSongIndex < 0) continue  // header
            if (otherSongIndex > draggedSongIndex && draggedCentre > otherCentre) {
                // Dragged moved BELOW this neighbour — swap down one.
                onReorder(draggedSongIndex, otherSongIndex)
                draggedLazyIndex = other.index
                // Keep the held item visually under the finger: compensate for
                // the slot displacement the swap caused.
                dragOffset += (info.offset - other.offset)
                break
            }
            if (otherSongIndex < draggedSongIndex && draggedCentre < otherCentre) {
                // Dragged moved ABOVE this neighbour — swap up one.
                onReorder(draggedSongIndex, otherSongIndex)
                draggedLazyIndex = other.index
                dragOffset += (info.offset - other.offset)
                break
            }
        }
        // Edge auto-scroll keeps the dragged item reachable at the list ends.
        val bottom = listState.layoutInfo.viewportEndOffset
        val top = listState.layoutInfo.viewportStartOffset
        val draggedTop = info.offset + dragOffset
        val draggedBottom = draggedTop + info.size
        if (bottom - draggedBottom < 80f) {
            scope.launch { listState.scrollBy(14f) }
        } else if (draggedTop - top < 80f) {
            scope.launch { listState.scrollBy(-14f) }
        }
    }

    fun onDragCancel() {
        dragging = false
        draggedItemId = null
        draggedLazyIndex = -1
        dragOffset = 0f
    }

    fun onDrop() {
        if (dragging) {
            onDragEnd(songIdsProvider())
        }
        onDragCancel()
    }
}

@Composable
private fun rememberDragDropListState(
    listState: LazyListState,
    scope: CoroutineScope,
    songIds: () -> List<Long>,
    onReorder: (Int, Int) -> Unit,
    onDragEnd: (List<Long>) -> Unit = {},
): DragDropListState {
    // rememberUpdatedState so the callbacks always see the latest `songs` /
    // `manualOrder` even though the state object itself is remembered once.
    val currentSongIds by rememberUpdatedState(songIds)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    return remember {
        DragDropListState(
            listState = listState,
            onReorder = { from, to -> currentOnReorder(from, to) },
            onDragEnd = { ids -> currentOnDragEnd(ids) },
            scope = scope,
            songIdsProvider = { currentSongIds() },
        )
    }
}

@Composable
private fun PlaylistPageBackground(
    coverUri: String?,
    backgroundColor: () -> Color,
    coverHeight: Dp,
    scrollProgress: () -> Float,
    placeholderSeed: String,
    enableBlur: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(backgroundColor()) },
    ) {
        PlaylistCoverBackground(
            coverUri = coverUri,
            backgroundColor = backgroundColor,
            coverHeight = coverHeight,
            scrollProgress = scrollProgress,
            modifier = Modifier.align(Alignment.TopCenter),
            placeholderSeed = placeholderSeed,
            enableBlur = enableBlur,
        )
    }
}

/**
 * Mochy's top-bar blur, reduced to the one thing the playlist needs: a real
 * backdrop blur of the page, full strength at the status bar and melted into
 * the list below. One blur pass with a smoothstep mask: no stacked bands, so
 * the downward fade has no seam and the top stays uniform.
 */
@Composable
private fun PlaylistTopBlur(
    backdrop: LayerBackdrop,
    morph: () -> Float,
    height: Dp,
    tint: () -> Color,
) {
    val density = LocalDensity.current
    val blurPx = with(density) { 16.dp.toPx() }
    val mask = remember {
        Array(BLUR_MASK_STEPS + 1) { i ->
            val t = i / BLUR_MASK_STEPS.toFloat()
            val eased = t * t * (3f - 2f * t)
            (BLUR_MASK_START + (1f - BLUR_MASK_START) * t) to
                Color.Black.copy(alpha = 1f - eased)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                alpha = morph()
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                // Tint the glass once, then fade blur + tint together.
                drawRect(tint().copy(alpha = 0.20f))
                drawRect(
                    brush = Brush.verticalGradient(*mask),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    // A background fade must not inherit the button glass rim/shadow.
                    highlight = null,
                    shadow = null,
                    innerShadow = null,
                    effects = {
                        blur(blurPx * morph())
                    },
                ),
        )
    }
}

// ------------------------------------------------------------------ header + action buttons

@Composable
private fun PlaylistHeaderSpacer(
    name: String,
    sidePad: Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = sidePad),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DetailText(
            text = name,
            size = TitleFontSize,
            lineHeight = TitleLineHeight,
            family = CalSansFamily,
            maxLines = 3,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = 0f },
        )
        Spacer(Modifier.height(TitleToStatsGapDp))
        Spacer(Modifier.height(PlaylistStatsHeight))
        Spacer(Modifier.height(StatsToActionsGapDp))
        Spacer(Modifier.height(ActionButtonSize))
        Spacer(Modifier.height(ActionsToCustomOrderGapDp))
        Spacer(Modifier.height(CustomOrderHeight))
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PlaylistTitleOverlay(
    name: String,
    sidePad: Dp,
    headerTop: () -> Float,
    onSize: (IntSize) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, headerTop().roundToInt()) }
            .padding(horizontal = sidePad),
        contentAlignment = Alignment.TopCenter,
    ) {
        DetailText(
            text = name,
            size = TitleFontSize,
            lineHeight = TitleLineHeight,
            family = CalSansFamily,
            maxLines = 3,
            textAlign = TextAlign.Center,
            modifier = Modifier.onSizeChanged(onSize),
        )
    }
}

@Composable
private fun PlaylistStatsOverlay(
    songCount: String,
    duration: String,
    dateDay: String,
    dateMonth: String,
    sidePad: Dp,
    statsTop: () -> Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, statsTop().roundToInt()) }
            .padding(horizontal = sidePad)
            .height(PlaylistStatsHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetailStat(value = songCount, label = "Songs", modifier = Modifier.weight(1f))
        DetailStatDivider()
        DetailStat(value = duration, label = "Duration", modifier = Modifier.weight(1f))
        DetailStatDivider()
        DetailStat(value = dateDay, label = dateMonth, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DetailStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DetailText(text = value, size = 22.sp, color = Color.White)
        Spacer(Modifier.height(4.dp))
        DetailText(
            text = label,
            size = 11.sp,
            color = Color.White.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun DetailStatDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(28.dp)
            .background(Color.White.copy(alpha = 0.16f)),
    )
}

// ── Header actions: shuffle / play / custom-order. ──────────────────────────
// Lives INSIDE the captured page so the top frost blurs these buttons as they
// rise with the header. The search circle is part of this row and scrolls away
// with it — tapping it opens the separate search page (no in-place morph). A
// different, top-layer capsule slides in on scroll (PlaylistScrollSearchCapsule).

/**
 * Glass-morphism notification capsule for transient actions (removed from
 * playlist, added/removed from favourites) with an Undo button. REAL kyant
 * glass like the menu cards (backdrop blur + vibrancy) with a hairline
 * border, centred above the mini player.
 */
@Composable
private fun DetailUndoCapsule(
    message: String,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
) {
    val shape = CircleShape
    Row(
        modifier = modifier
            .clip(shape)
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            colorControls(brightness = 0.05f, contrast = 1f, saturation = 1.2f)
                            blur(20.dp.toPx())
                        },
                        onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.42f)) },
                    )
                } else {
                    Modifier.background(Color.Black.copy(alpha = 0.55f))
                },
            )
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DetailText(
            text = message,
            size = 14.sp,
            color = Color.White,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onUndo,
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            DetailText(
                text = "Undo",
                size = 14.sp,
                color = Color.White,
                weight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PlaylistHeaderActionsOverlay(
    actionsTop: () -> Float,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onShuffle: () -> Unit,
    onCustomOrder: () -> Unit,
    onOpenSearch: () -> Unit,
    locked: Boolean = true,
    onToggleLock: () -> Unit = {},
) {
    val density = LocalDensity.current
    val gapPx = with(density) { 12.dp.roundToPx() }
    val customGapPx = with(density) { ActionsToCustomOrderGapDp.roundToPx() }
    val restSizePx = with(density) { ActionButtonSize.roundToPx() }

    Layout(
        content = {
            DetailGlassCircleButton(
                desc = "Shuffle playlist",
                onClick = onShuffle,
                white = true,
            ) {
                DetailShuffleGlyph(Color.Black, Modifier.size(18.dp))
            }
            DetailPill(
                label = if (isPlaying) "Pause" else "Play",
                filled = true,
                onClick = onPlayPause,
                height = ActionButtonSize,
                horizontalPadding = PlayCapsulePadding,
                reserveLabel = "Pause",
            ) {
                if (isPlaying) {
                    DetailPauseGlyph(Color.Black, Modifier.size(22.dp))
                } else {
                    DetailPlayGlyph(Color.Black, Modifier.size(22.dp))
                }
            }
            // The real search circle — rides the header, scrolls away with it.
            // Tapping opens the search page.
            DetailGlassCircleButton(
                desc = "Search playlist",
                onClick = onOpenSearch,
                white = true,
            ) {
                DetailSearchGlyph(Color.Black, Modifier.size(18.dp))
            }
            DetailPill(
                label = "Custom order",
                filled = false,
                onClick = onCustomOrder,
                height = CustomOrderHeight,
                horizontalPadding = 18.dp,
                trailing = {
                    // Small glass circle on the right holding the lock/unlock
                    // glyph. Locked = sort menu only; unlocked = manual reorder.
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onToggleLock,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (locked) {
                            DetailLockGlyph(Color.White, Modifier.size(12.dp))
                        } else {
                            DetailUnlockGlyph(Color.White, Modifier.size(12.dp))
                        }
                    }
                },
            ) {
                DetailReorderGlyph(Color.White, Modifier.size(16.dp))
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val shuffle = measurables[0].measure(Constraints())
        val play = measurables[1].measure(Constraints())
        val search = measurables[2].measure(Constraints())
        val customOrder = measurables[3].measure(Constraints())
        val total = shuffle.width + gapPx + play.width + gapPx + restSizePx
        val startX = ((constraints.maxWidth - total) / 2).coerceAtLeast(0)
        val shuffleRestX = startX
        val playRestX = startX + shuffle.width + gapPx
        val searchRestX = playRestX + play.width + gapPx

        layout(constraints.maxWidth, constraints.maxHeight) {
            val restY = actionsTop()
            val shuffleY = restY + (restSizePx - shuffle.height) / 2f
            val playY = restY + (restSizePx - play.height) / 2f
            val searchY = restY + (restSizePx - search.height) / 2f
            shuffle.placeWithLayer(shuffleRestX, shuffleY.roundToInt())
            play.placeWithLayer(playRestX, playY.roundToInt())
            search.placeWithLayer(searchRestX, searchY.roundToInt())

            // Custom order sits directly under the play capsule (screen-centred).
            customOrder.placeWithLayer(
                x = ((constraints.maxWidth - customOrder.width) / 2f).roundToInt(),
                y = (restY + restSizePx + customGapPx).roundToInt(),
            )
        }
    }
}
// ── Scroll-reveal search capsule — TOP LAYER. ───────────────────────────────
// No circle→capsule morph. The header search circle scrolls away with the rest
// of the header. As the header leaves, this SEPARATE capsule slides DOWN from
// the status bar and fades in (smooth), settling centered in the top-bar row.
// Tapping it opens the full search page. It is drawn above the frost so it
// stays sharp, and only exists while the playlist page is visible (hidden once
// the search page is open — the page has its own field).
@Composable
private fun PlaylistScrollSearchCapsule(
    reveal: Float,            // 0 = hidden, 1 = fully settled (eased)
    searchCapsuleWidth: Dp,
    onOpenSearch: () -> Unit,
    dockCenterX: () -> Float,  // screen-centred X for the capsule
    dockY: Float,              // settled Y (top-bar row, below status bar)
    backdrop: LayerBackdrop?,
) {
    if (reveal <= 0.005f) return  // not laid out while fully hidden
    val density = LocalDensity.current
    // The capsule starts just above the status bar (translationY = -dockedHeight)
    // and slides down to its settled Y as reveal → 1.
    val heightPx = with(density) { ActionButtonSize.toPx() }
    val slideFromY = -heightPx * 1.2f
    val slideTranslationY = slideFromY * (1f - reveal)

    Layout(
        content = {
            DetailSearchCapsule(
                expanded = false,           // never expanded here — the page owns the field
                progress = 0f,
                width = searchCapsuleWidth,
                query = "",
                onQueryChange = {},
                onOpen = onOpenSearch,
                // Always the glass surface (this is a scroll-docked hint, not a tap-open).
                whiteAmount = { 0f },
                // Always full capsule width once it exists.
                dockProgress = { 1f },
                showPlaceholder = true,
                backdrop = backdrop,
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val search = measurables[0].measure(Constraints())
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = (dockCenterX() - search.width / 2f).roundToInt()
            // Layer-level: alpha + slide. Y is the settled dock row; the slide is
            // applied as a translationY inside the layer block.
            search.placeWithLayer(
                x = x,
                y = dockY.roundToInt(),
                layerBlock = {
                    alpha = reveal
                    translationY = slideTranslationY
                },
            )
        }
    }
}

// ── Search page — full-screen overlay. ───────────────────────────────────────
// Tapping the header search circle (or the scroll-reveal capsule) opens this.
// Its own field + filtered song list, themed to the cover color. Slides up over
// the playlist page. Back / the close disc / clearing focus closes it.
@Composable
private fun PlaylistSearchPage(
    playlistName: String,
    songs: List<Song>,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onSongClick: (Int, Song) -> Unit,
    onMore: (Song) -> Unit,
    songArt: (Song) -> Any?,
    backgroundColor: () -> Color,
    topInset: Dp,
    sidePad: Dp,
    thumbSize: Dp,
    navBottom: Dp,
) {
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Entrance: slide up + fade in. The composable mounts at 0 and animates to 1.
    val enterProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "searchPageEnter",
    )
    LaunchedEffect(Unit) {
        // Let the slide begin, then focus the field so the IME rises with it.
        runCatching { focusRequester.requestFocus() }
    }
    val screenHPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val slidePx = screenHPx * 0.06f * (1f - enterProgress)

    val q = query.trim()
    val filtered = remember(songs, q) {
        if (q.isEmpty()) songs.withIndex().toList()
        else songs.withIndex().filter { (_, s) ->
            s.title.contains(q, ignoreCase = true) ||
                s.artist.contains(q, ignoreCase = true)
        }
    }
    val listState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(backgroundColor()) }
            .graphicsLayer {
                alpha = enterProgress
                translationY = slidePx
            },
    ) {
        // Filtered song list — laid out FIRST so the top row (drawn after it)
        // sits above it and always receives touches. The list's content padding
        // keeps its first item below the top row.
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topInset + ActionButtonSize + 20.dp,
                bottom = navBottom + 112.dp,
            ),
        ) {
            if (filtered.isEmpty()) {
                item(key = "empty") {
                    DetailEmptyHint(sidePad = sidePad, searching = q.isNotEmpty())
                }
            } else {
                itemsIndexed(filtered) { i, indexed ->
                    val song = indexed.value
                    Column {
                        PlaylistSongRow(
                            song = song,
                            art = songArt(song),
                            thumbSize = thumbSize,
                            sidePad = sidePad,
                            onClick = {
                                focusManager.clearFocus()
                                onSongClick(indexed.index, song)
                                onClose()
                            },
                            onMore = { onMore(song) },
                        )
                        if (i < filtered.lastIndex) {
                            DetailRowDivider(sidePad = sidePad, thumbSize = thumbSize)
                        }
                    }
                }
            }
        }

        // Top row: close disc + search field capsule. Drawn AFTER the list so it
        // is on top of the touch stack — the close button and clear-text button
        // are always tappable.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topInset + 6.dp, start = sidePad, end = sidePad, bottom = 8.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailGlassCircleButton(
                desc = "Close search",
                onClick = {
                    focusManager.clearFocus()
                    onClose()
                },
                white = true,
            ) {
                DetailCloseGlyph(Color.Black, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            // The search field — a white capsule with the search glyph + text.
            Box(
                modifier = Modifier
                    .height(ActionButtonSize)
                    .weight(1f)
                    .clip(CircleShape)
                    .background(Color.White, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { focusRequester.requestFocus() },
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(start = 14.dp)
                            .size(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        DetailSearchGlyph(Color.Black.copy(alpha = 0.85f), Modifier.size(18.dp))
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontFamily = CalSansFamily,
                        ),
                        cursorBrush = SolidColor(Color.Black),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .focusRequester(focusRequester),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (query.isEmpty()) {
                                    DetailText(
                                        text = "Search $playlistName",
                                        size = 14.sp,
                                        color = Color.Black.copy(alpha = 0.45f),
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    if (query.isNotEmpty()) {
                        // Clear-text button: clickable comes AFTER clip/background so
                        // the hit area is the full circle and is never blocked.
                        Box(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.12f), CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { onQueryChange("") },
                            contentAlignment = Alignment.Center,
                        ) {
                            DetailCloseGlyph(Color.Black.copy(alpha = 0.7f), Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailGlassCircleButton(
    desc: String,
    onClick: () -> Unit,
    size: Dp = ActionButtonSize,
    white: Boolean = false,
    backdrop: LayerBackdrop? = null,
    icon: @Composable () -> Unit,
) {
    val surface = if (white) {
        Modifier.background(Color.White, CircleShape)
    } else {
        Modifier.detailGlass(CircleShape, backdrop = backdrop)
    }
    Box(
        modifier = Modifier
            .size(size)
            .detailPressable(onClick = onClick)
            .then(surface)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
private fun DetailSearchCapsule(
    expanded: Boolean,
    progress: Float,
    width: Dp,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: () -> Unit,
    // 1 = solid white (click-opened), 0 = glass morphism (scroll-docked). Crossfaded.
    whiteAmount: () -> Float,
    // Combined dock amount (max of click-open and scroll-dock) — drives width.
    dockProgress: () -> Float,
    // Show the "Search songs" placeholder as soon as the capsule shape appears
    // (docked), not only once fully expanded.
    showPlaceholder: Boolean,
    backdrop: LayerBackdrop?,
) {
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    // Width follows the COMBINED dock so a scroll-docked circle grows into the
    // capsule shape even without a tap.
    val dockP = dockProgress().coerceIn(0f, 1f)
    val currentWidth = ActionButtonSize + (width - ActionButtonSize) * dockP
    val iconShiftDp = ActionButtonSize * 0.5f - currentWidth * 0.5f
    val pressModifier = if (expanded) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = { focusRequester.requestFocus() },
        )
    } else {
        Modifier.detailPressable(onClick = onOpen)
    }
    val wa = whiteAmount().coerceIn(0f, 1f)
    // Ink: black on white, white on glass — crossfade between the two.
    val ink = lerp(Color.White, Color.Black, wa)

    LaunchedEffect(expanded) {
        if (expanded) {
            // Wait for the circle-to-capsule motion before the IME can resize anything.
            delay(420)
            runCatching { focusRequester.requestFocus() }
        } else {
            focusManager.clearFocus()
        }
    }

    Box(
        modifier = Modifier
            .width(currentWidth)
            .height(ActionButtonSize)
            .then(pressModifier)
            .clip(CircleShape)
            .semantics { contentDescription = "Search playlist" },
        contentAlignment = Alignment.Center,
    ) {
        // Two stacked surfaces, alpha-crossfaded by [wa]:
        //  - glass morphism (backdrop blur) when scroll-docked  [alpha = 1 - wa]
        //  - solid white when click-opened                      [alpha = wa]
        // Stacking two boxes lets each surface own its draw phase cleanly.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = 1f - wa }
                .detailGlass(CircleShape, backdrop = backdrop)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = wa }
                .background(Color.White, CircleShape)
        )
        DetailSearchGlyph(
            color = ink.copy(alpha = 0.9f),
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    translationX = with(density) { iconShiftDp.toPx() }
                },
        )
        if (expanded) {
            // Fully opened by a tap: the real text field with focus + cursor.
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = ink,
                    fontSize = 14.sp,
                    fontFamily = CalSansFamily,
                ),
                cursorBrush = SolidColor(ink),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 40.dp, end = 16.dp)
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            DetailText(
                                text = "Search songs",
                                size = 14.sp,
                                color = ink.copy(alpha = 0.75f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        } else if (showPlaceholder) {
            // Docked but not opened by a tap (scroll-docked glass capsule): show
            // the "Search songs" hint as a plain label. Tapping opens it.
            DetailText(
                text = "Search songs",
                size = 14.sp,
                color = ink.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 40.dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun DetailPill(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    height: Dp = ActionButtonSize,
    horizontalPadding: Dp = 26.dp,
    // Widest label this pill can show, so Play <-> Pause keeps one width.
    reserveLabel: String? = null,
    backdrop: LayerBackdrop? = null,
    // Optional trailing slot (e.g. the lock circle on the custom-order pill).
    // Must come before `icon` so the trailing-lambda call syntax still binds to
    // `icon` (the required, last parameter).
    trailing: (@Composable () -> Unit)? = null,
    icon: @Composable () -> Unit,
) {
    val fg = if (filled) Color.Black else Color.White
    val surface = if (filled) {
        Modifier.background(Color.White)
    } else {
        Modifier.detailGlass(CircleShape, backdrop = backdrop)
    }
    Row(
        modifier = Modifier
            .height(height)
            .detailPressable(onClick = onClick, enabled = enabled)
            .clip(CircleShape)
            .then(surface)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        Box {
            if (reserveLabel != null) {
                DetailText(text = reserveLabel, size = 15.sp, color = Color.Transparent)
            }
            DetailText(text = label, size = 15.sp, color = fg)
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun Modifier.detailGlass(
    shape: Shape,
    backdrop: LayerBackdrop? = null,
    baseAlpha: Float = 0.35f,
): Modifier {
    val surface = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                colorControls(brightness = 0.05f, contrast = 1f, saturation = 1.4f)
                blur(20f.dp.toPx())
            },
            onDrawSurface = {
                drawRect(Color.Black.copy(alpha = baseAlpha))
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.10f),
                        0.45f to Color.White.copy(alpha = 0.02f),
                        1f to Color.Transparent,
                    ),
                )
            },
        )
    } else {
        Modifier
            .background(Color.Black.copy(alpha = baseAlpha), shape)
            .background(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.14f),
                    0.45f to Color.White.copy(alpha = 0.03f),
                    1f to Color.Transparent,
                ),
                shape = shape,
            )
    }
    return this
        .clip(shape)
        .then(surface)
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.52f),
                0.5f to Color.White.copy(alpha = 0.16f),
                1f to Color.White.copy(alpha = 0.08f),
            ),
            shape = shape,
        )
}

@Composable
private fun DetailMoreMenu(
    searchOpen: Boolean,
    onCloseSearch: () -> Unit,
    onToggle: () -> Unit,
    backdrop: LayerBackdrop?,
) {
    val dotsAlpha by animateFloatAsState(
        targetValue = if (searchOpen) 0f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "playlistMenuDots",
    )
    val crossAlpha by animateFloatAsState(
        targetValue = if (searchOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "playlistMenuCross",
    )

    Box(
        modifier = Modifier
            .size(ActionButtonSize)
            .detailPressable {
                if (searchOpen) onCloseSearch() else onToggle()
            }
            .detailGlass(CircleShape, backdrop = backdrop)
            .semantics {
                contentDescription = if (searchOpen) "Close search" else "More"
            },
        contentAlignment = Alignment.Center,
    ) {
        DetailDotsGlyph(
            color = Color.White,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    alpha = dotsAlpha
                    rotationZ = 90f * (1f - dotsAlpha)
                },
        )
        DetailCloseGlyph(
            color = Color.White,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    alpha = crossAlpha
                    rotationZ = -90f * (1f - crossAlpha)
                },
        )
    }
}

/**
 * Coral's glass playlist menu: a real backdrop blur of the cover behind the
 * card. Drawn inline (not in a Popup) so it can sample the page backdrop.
 */
@Composable
private fun PlaylistMenuOverlay(
    top: Dp,
    endPad: Dp,
    backdrop: LayerBackdrop?,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onChangeCover: () -> Unit,
    onAddSongs: () -> Unit,
    onDelete: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        val cardShape = RoundedCornerShape(18.dp)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = top, end = endPad)
                .width(208.dp)
                .clip(cardShape)
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { cardShape },
                            effects = {
                                vibrancy()
                                colorControls(brightness = 0.05f, contrast = 1f, saturation = 1.2f)
                                blur(20.dp.toPx())
                            },
                            onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.42f)) },
                        )
                    } else {
                        Modifier.background(Color.Black.copy(alpha = 0.55f))
                    }
                )
                .border(1.dp, Color.White.copy(alpha = 0.15f), cardShape)
                .padding(vertical = 6.dp),
        ) {
            DetailMenuRow("Rename") { onDismiss(); onRename() }
            DetailMenuRow("Change cover") { onDismiss(); onChangeCover() }
            DetailMenuRow("Add songs") { onDismiss(); onAddSongs() }
            DetailMenuRow("Delete", destructive = true) { onDismiss(); onDelete() }
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
    showDragHandle: Boolean = false,
    onClick: () -> Unit,
    onMore: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .detailPressable(pressedScale = 0.985f, onClick = onClick)
            .padding(horizontal = sidePad, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetailSongThumb(art = art, size = thumbSize, songId = song.id)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            DetailText(text = song.title.orEmpty(), size = 16.sp)
            Spacer(Modifier.height(2.dp))
            // Artist and duration now sit on the same line — duration as a muted
            // suffix after the artist, separated by a small dot. The right-side
            // slot that duration used to occupy now holds the per-song menu.
            Row(verticalAlignment = Alignment.CenterVertically) {
                DetailText(
                    text = song.artist.orEmpty(),
                    size = 13.sp,
                    color = Color.White.copy(alpha = 0.58f),
                    maxLines = 1,
                )
                Spacer(Modifier.width(6.dp))
                DetailText(
                    text = "•",
                    size = 13.sp,
                    color = Color.White.copy(alpha = 0.30f),
                )
                Spacer(Modifier.width(6.dp))
                DetailText(
                    text = detailClock(detailDurationMs(song.duration)),
                    size = 13.sp,
                    color = Color.White.copy(alpha = 0.45f),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // Right-side action: while the custom-order lock is unlocked this is the
        // drag handle (≡) like RiPlay's manual-order mode; otherwise it's the
        // per-song three-dot menu.
        Box(
            modifier = Modifier
                .size(32.dp)
                .detailPressable(
                    pressedScale = 0.90f,
                    onClick = onMore,
                    enabled = !showDragHandle,
                )
                .semantics {
                    contentDescription = if (showDragHandle) {
                        "Drag to reorder ${song.title}"
                    } else {
                        "More options for ${song.title}"
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (showDragHandle) {
                DetailReorderGlyph(
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            } else {
                DetailDotsGlyph(
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailSongThumb(art: Any?, size: Dp, songId: Long? = null) {
    // Per-song listening-time badge (RiPlay style): actual played time
    // accumulated across sessions, as bold white text at the BOTTOM-LEFT of
    // the thumbnail — "0m", "3m", "1h", "12h", "1d". Always shown, exactly
    // like RiPlay, even before the song has a full minute.
    val stats by com.rajatxo.lutea.data.store.PlaybackStatsStore.stats.collectAsState()
    val playedMs = songId?.let { stats[it] } ?: 0L
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
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
        // Listening-time text — centred over the artwork, bold white on a light
        // scrim so it stays readable on any cover.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.30f)),
            contentAlignment = Alignment.Center,
        ) {
            DetailText(
                text = com.rajatxo.lutea.data.store.formatPlayedMs(playedMs),
                size = 10.sp,
                color = Color.White,
                weight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DetailRowDivider(sidePad: Dp, thumbSize: Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            // Start after the thumbnail; run all the way to the side margin so
            // the line passes under the three-dot menu icon too — a single
            // continuous divider per row pair.
            .padding(start = sidePad + thumbSize + 14.dp, end = sidePad)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.07f)),
    )
}

@Composable
private fun DetailEmptyHint(sidePad: Dp, searching: Boolean) {
    DetailText(
        text = if (searching) {
            "No songs found."
        } else {
            "No songs yet. Add some from your library."
        },
        size = 14.sp,
        color = Color.White.copy(alpha = 0.55f),
        maxLines = 3,
        modifier = Modifier.padding(horizontal = sidePad, vertical = 24.dp),
    )
}

// ------------------------------------------------------------------ top bar row

/**
 * Back + menu only. The bar stays transparent so the title/shuffle scroll behind it.
 * While the search capsule is open the menu disc becomes a cross that closes it.
 */
@Composable
private fun PlaylistTopBar(
    searchOpen: Boolean,
    onCloseSearch: () -> Unit,
    onMenuToggle: () -> Unit,
    topInset: Dp,
    sidePad: Dp,
    onBack: () -> Unit,
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topInset + TopBarContentHeight),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(TopBarContentHeight)
                .padding(horizontal = sidePad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailBackButton(onBack = onBack, backdrop = backdrop)
            Spacer(Modifier.weight(1f))
            DetailMoreMenu(
                searchOpen = searchOpen,
                onCloseSearch = onCloseSearch,
                onToggle = onMenuToggle,
                backdrop = backdrop,
            )
        }
    }
}

@Composable
private fun DetailBackButton(
    onBack: () -> Unit,
    backdrop: LayerBackdrop?,
) {
    Box(
        modifier = Modifier
            .size(BackButtonSize)
            .detailPressable(onClick = onBack)
            .detailGlass(CircleShape, backdrop = backdrop)
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
    textAlign: TextAlign = TextAlign.Unspecified,
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
            textAlign = textAlign,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Press-scale + click without a ripple (no Material dependency). */
@Composable
private fun Modifier.detailPressable(
    pressedScale: Float = 0.94f,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    if (!enabled) return this
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

private fun detailStatsDuration(totalMs: Long): String {
    val totalMin = (totalMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMin / 60L
    val mins = totalMin % 60L
    return if (hours > 0L) "${hours}h ${mins}m" else "${mins}m"
}

private fun detailCreatedDate(ms: Long): Pair<String, String> {
    if (ms <= 0L) return "\u2014" to "Created"
    val date = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val month = date.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
    return date.dayOfMonth.toString() to month
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
private fun DetailPauseGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val barW = size.width * 0.22f
        val barH = size.height * 0.72f
        val y = (size.height - barH) / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.22f, y),
            size = Size(barW, barH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW * 0.35f, barW * 0.35f),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.56f, y),
            size = Size(barW, barH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW * 0.35f, barW * 0.35f),
        )
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
private fun DetailReorderGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension * 0.07f
        val xs = floatArrayOf(size.width * 0.34f, size.width * 0.66f)
        val ys = floatArrayOf(size.height * 0.24f, size.height * 0.50f, size.height * 0.76f)
        for (x in xs) {
            for (y in ys) {
                drawCircle(color, radius = r, center = Offset(x, y))
            }
        }
    }
}

/** Closed padlock — manual reorder is disabled. The user's exact SVG path (20x20). */
@Composable
private fun DetailLockGlyph(color: Color, modifier: Modifier = Modifier) {
    val path = remember {
        androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M10 1a4 4 0 0 1 4 4v2.05a2.5 2.5 0 0 1 2 2.45v6a2.5 2.5 0 0 1-2.5 2.5h-7A2.5 2.5 0 0 1 4 15.5v-6a2.5 2.5 0 0 1 2-2.45V5a4 4 0 0 1 4-4m0 10.5a1 1 0 1 0 0 2a1 1 0 0 0 0-2M10 2a3 3 0 0 0-3 3v2h6V5a3 3 0 0 0-3-3"
        ).toPath()
    }
    Canvas(modifier) {
        // The SVG is authored on a 20x20 grid; scale to whatever size the
        // canvas was given.
        withTransform({ scale(size.width / 20f, size.height / 20f, pivot = Offset.Zero) }) {
            drawPath(path, color)
        }
    }
}

/** Open padlock — manual reorder is enabled. The user's exact SVG path (20x20). */
@Composable
private fun DetailUnlockGlyph(color: Color, modifier: Modifier = Modifier) {
    val path = remember {
        androidx.compose.ui.graphics.vector.PathParser().parsePathString(
            "M15 1a4 4 0 0 1 4 4v.5a.5.5 0 0 1-1 0V5a3 3 0 1 0-6 0v2h1.5A2.5 2.5 0 0 1 16 9.5v6a2.5 2.5 0 0 1-2.5 2.5h-7A2.5 2.5 0 0 1 4 15.5v-6A2.5 2.5 0 0 1 6.5 7H11V5a4 4 0 0 1 4-4m-5 10.5a1 1 0 1 0 0 2a1 1 0 0 0 0-2"
        ).toPath()
    }
    Canvas(modifier) {
        withTransform({ scale(size.width / 20f, size.height / 20f, pivot = Offset.Zero) }) {
            drawPath(path, color)
        }
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
private fun DetailCloseGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.09f
        drawLine(
            color = color,
            start = Offset(w * 0.22f, h * 0.22f),
            end = Offset(w * 0.78f, h * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.78f, h * 0.22f),
            end = Offset(w * 0.22f, h * 0.78f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun DetailSearchGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = color,
            radius = w * 0.27f,
            center = Offset(w * 0.42f, h * 0.42f),
            style = Stroke(width = w * 0.09f),
        )
        drawLine(
            color = color,
            start = Offset(w * 0.62f, h * 0.62f),
            end = Offset(w * 0.86f, h * 0.86f),
            strokeWidth = w * 0.09f,
            cap = StrokeCap.Round,
        )
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
