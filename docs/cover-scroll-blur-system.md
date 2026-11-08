# Lutea — Cover Scroll & Blur System

> A reference for reusing the playlist detail's cover behaviour on the **album** and **artist** detail pages.

---

## What it does

The playlist detail screen has a full-bleed cover image at the top that **rides up, zooms, dims, dissolves from the bottom, fades out, and blurs** as you scroll the song list. This is Mochy's `ScrollAwayCover` behaviour, ported with Mochy's own constants. The cover and the list move in sync: the cover's full parallax unfolds over exactly the scroll that brings the first song row to the top of the screen — no gap, no sudden vanish, no extra blur layer.

**At rest:** the cover is sharp and fully visible at the top 60% of the screen. The big title and action buttons overlap its lower portion.

**Mid-scroll:** the cover translates up (80% of its height), zooms slightly (22%), blurs progressively (0 to 30px), dims (22% black overlay), and dissolves from the bottom. The song list rides up over it.

**Fully scrolled:** the cover has faded out (alpha 0), the first song row is at the top of the screen, and the small title has morphed into the top bar. No gap, no flat-colour dead zone.

---

## Architecture

Two files own the whole system:

| File | Role |
|------|------|
| `PlaylistCoverBackground.kt` | The cover component — a self-contained `@Composable` that draws the cover and all its scroll-driven effects. **Generic and reusable as-is for album/artist.** |
| `PlaylistDetailScreen.kt` | The screen that hosts the cover + `LazyColumn` + top bar. Computes the scroll `progress` and passes it to the cover. |

---

## Part 1 — The cover component

**File:** `app/src/main/java/com/rajatxo/lutea/ui/screens/PlaylistCoverBackground.kt`

### Signature

```kotlin
@Composable
fun PlaylistCoverBackground(
    coverUri: String?,
    backgroundColor: () -> Color,
    coverHeight: Dp,
    scrollProgress: () -> Float,
    modifier: Modifier = Modifier,
    placeholderSeed: String = "",
)
```

`scrollProgress` and `backgroundColor` are **lambdas** — read only in the `graphicsLayer` / draw phase, so scrolling and the colour fade never trigger recomposition. This is critical for performance.

### Scroll choreography constants

These match Mochy's `ScrollAwayCover.kt` exactly:

| Constant | Value | Mochy name | Effect |
|----------|-------|------------|--------|
| `COVER_FOLLOW` | `0.8` | `CoverFollow` | Cover translates up 80% of its height |
| `COVER_ZOOM` | `0.22` | `CoverZoom` | Ken Burns zoom (scale grows with scroll) |
| `COVER_FADE_FROM` | `0.2` | `CoverFadeFrom` | Alpha fade starts at 20% scroll |
| `COVER_FADE_TO` | `0.85` | `CoverFadeTo` | Alpha fade ends at 85% scroll (cover gone) |
| `COVER_DIM` | `0.22` | `CoverDim` | Black overlay, 22% opacity at full scroll |
| `DISSOLVE_RAMP_TO` | `0.8` | — | Bottom dissolve reaches full strength at 80% scroll |
| `BLUR_MAX_RADIUS_PX` | `30f` | `CoverSoftRadius` | Max blur radius in **actual pixels** (NOT dp) |
| `BLUR_IN_FROM` | `0f` | `softFrom` | Blur starts immediately |
| `BLUR_IN_TO` | `0.55` | `CoverSoftBy` | Blur reaches full strength at 55% scroll |

### Layers (bottom → top)

1. **Placeholder gradient** — a hue-tinted vertical gradient, shown while the cover loads or if it fails.
2. **Cover image** — Coil `AsyncImage` with `ContentScale.Crop`. Its `RenderEffect` blur radius grows `0 → 30px` over progress `0 → 0.55`. API 31+ only; below S the image stays sharp.
3. **Blend gradient** — cover → background colour, seamless fade. Stops: `0.56` clear → `0.84` background@78% → `1.0` solid background. **Static** — never driven by scroll.
4. **Top scrim** — black 34% at the very top, gone by 24% of cover height. Static, for status-bar icon legibility.

### The container's transform + draw

```kotlin
Box(
    modifier = modifier
        .fillMaxWidth()
        .height(coverHeight)
        .graphicsLayer {
            val p = scrollProgress().coerceIn(0f, 1f)
            translationY = p * size.height * -COVER_FOLLOW
            val zoom = 1f + p * COVER_ZOOM
            scaleX = zoom
            scaleY = zoom
            transformOrigin = TransformOrigin(0.5f, 0.35f)
            alpha = 1f - luteaSmoothstep(COVER_FADE_FROM, COVER_FADE_TO, p)
            compositingStrategy = if (p > 0f) Offscreen else Auto
        }
        .drawWithContent {
            drawContent()
            val p = scrollProgress().coerceIn(0f, 1f)
            if (p > 0f) {
                // dim
                drawRect(Color.Black.copy(alpha = COVER_DIM * p))
                // bottom dissolve — DstIn cuts the lower band away
                val clear = 0.1f + 0.45f * luteaSmoothstep(0f, DISSOLVE_RAMP_TO, p)
                val top = size.height * (1f - clear)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black,
                        1f to Color.Transparent,
                        startY = top, endY = size.height,
                    ),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, size.height - top),
                    blendMode = BlendMode.DstIn,
                )
            }
        },
)
```

### The easing function

Every ramp uses the same Hermite smoothstep (C¹ continuous, no hard edges):

```kotlin
internal fun luteaSmoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
```

---

## Part 2 — The progress calculation

**File:** `app/src/main/java/com/rajatxo/lutea/ui/screens/PlaylistDetailScreen.kt`

This is the part that broke three times before it worked. The `progress` value (`0f..1f`) drives every scroll-driven effect on the cover. It must:

- Be **0 at rest** (cover fully visible).
- Reach **1 when the first song row reaches the top** of the screen (cover and list in sync — no gap).
- Be **continuous** across the moment the header item scrolls off (no jump → no sudden vanish).

### The formula

```kotlin
val contentTopPx = coverHeightPx - titleOverlapPx   // contentPadding.top in pixels

val progress = remember(listState, contentTopPx) {
    derivedStateOf {
        val header = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == "header" }
        if (header == null) {
            // header not laid out yet (first frame) → assume rest
            // header scrolled off → fully done
            if (listState.firstVisibleItemIndex == 0) 0f else 1f
        } else {
            val headerH = header.size.toFloat()
            val scrolledFromRest = -header.offset.toFloat()
            val denom = (contentTopPx + headerH).coerceAtLeast(1f)
            (scrolledFromRest / denom).coerceIn(0f, 1f)
        }
    }
}
```

### Why this works

- **`LazyListItemInfo.offset` is `0` at rest** (content-area coordinates) and goes **negative** as the header scrolls up. So `scrolledFromRest = -header.offset` is `0` at rest and grows positive — exactly the scroll amount.
- **The denominator** is `contentTopPx + headerHeight` — the total scroll needed to bring the first song row from its rest position to the top of the viewport.
- **At the flip** (header scrolls off `visibleItemsInfo`), `header.offset` was already ≈ `-(contentTopPx + headerH)`, so progress was ≈ 1. Returning `1f` when the header is gone is continuous — no jump, no sudden vanish.
- **First frame**: the header hasn't been laid out yet, so it's absent from `visibleItemsInfo`. Returning `0f` (rest) instead of `1f` prevents the cover from flashing invisible before the first layout pass.

### The header item MUST have `key = "header"`

```kotlin
item(key = "header") {
    PlaylistHeader(...)
}
```

The progress calculation looks up the header by its key in `visibleItemsInfo`. Without the key, the lookup fails and progress stays at 0 (cover never animates).

---

## Part 3 — The screen layout

```
BoxWithConstraints (solid background colour)
├── Box (fillMaxSize)
│   ├── PlaylistCoverBackground        ← layer 0: the cover
│   └── LazyColumn (fillMaxSize)
│       ├── contentPadding.top = coverHeight - titleOverlap
│       ├── item(key = "header")       ← big title, subtitle, action buttons
│       └── itemsIndexed(songs)        ← song rows
└── PlaylistTopBar                     ← layer 2: back button + small title (morphs in)
```

### Layout constants

| Constant | Value | Purpose |
|----------|-------|---------|
| `COVER_HEIGHT_FRACTION` | `0.60` | Cover = 60% of screen height |
| `TITLE_OVERLAP_FRACTION` | `0.14` | Header starts 14% inside the cover's bottom |
| `TOP_BAR_THRESHOLD` | `0.55` | Scroll progress where the small title morphs in |

### Content padding

```kotlin
contentPadding = PaddingValues(
    top = coverHeight - titleOverlap,    // header starts here (46% of screen)
    bottom = bottomInset + screenH * 0.04f,
)
```

The `top` padding puts the header at 46% of the screen (60% cover − 14% overlap), so the big title overlaps the cover's lower portion at rest.

### The top bar

A back button (always visible) + small title that morphs in via a spring when `progress > TOP_BAR_THRESHOLD`:

```kotlin
val morph = animateFloatAsState(
    targetValue = if (progress.value > TOP_BAR_THRESHOLD) 1f else 0f,
    animationSpec = spring(
        dampingRatio = if (barShown) 0.62f else 0.9f,  // overshoot on show, calm on hide
        stiffness = Spring.StiffnessMediumLow,
    ),
)
```

The big title scrolls naturally with the list (no separate fade/scale) — Mochy-style. The small title in the top bar takes over once the cover has scrolled away.

---

## Part 4 — Reusing for album and artist pages

The cover component (`PlaylistCoverBackground`) is **generic** — it takes a `coverUri`, `backgroundColor`, `coverHeight`, and `scrollProgress`. You can use it as-is for album and artist pages. **Copy the file unchanged.**

### What stays identical

- `PlaylistCoverBackground.kt` — the cover component, all constants, the blur/dim/dissolve/fade choreography.
- The progress calculation formula.
- The screen layout structure (BoxWithConstraints → Box → cover + LazyColumn + top bar).
- All layout constants.

### What changes per page

- The **header content** (title, subtitle, action buttons, extra metadata for artist pages).
- The **data source** (album's songs vs artist's songs vs playlist's songs).
- The `placeholderSeed` (use the album/artist ID).

### Template for AlbumDetailScreen / ArtistDetailScreen

```kotlin
@Composable
fun AlbumDetailScreen(
    album: Album,
    songs: List<Song>,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onSongClick: (Int, Song) -> Unit,
    modifier: Modifier = Modifier,
    insets: WindowInsets = WindowInsets.systemBars,
) {
    DetailLightSystemBarIcons()

    val themeState = rememberCoverThemeColor(album.coverUri, fallbackSeed = album.id.toString())
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(themeState.value) },
    ) {
        val screenW = maxWidth
        val screenH = maxHeight
        val coverHeight = screenH * 0.60f          // COVER_HEIGHT_FRACTION
        val coverHeightPx = with(density) { coverHeight.toPx() }.coerceAtLeast(1f)
        val titleOverlap = screenH * 0.14f          // TITLE_OVERLAP_FRACTION
        val titleOverlapPx = with(density) { titleOverlap.toPx() }
        val contentTopPx = coverHeightPx - titleOverlapPx
        val sidePad = screenW * 0.06f
        val thumbSize = screenW * 0.13f
        val topInset = with(density) { insets.getTop(this).toDp() }
        val bottomInset = with(density) { insets.getBottom(this).toDp() }

        // ── progress: identical to PlaylistDetailScreen ──
        val progress = remember(listState, contentTopPx) {
            derivedStateOf {
                val header = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.key == "header" }
                if (header == null) {
                    if (listState.firstVisibleItemIndex == 0) 0f else 1f
                } else {
                    val headerH = header.size.toFloat()
                    val scrolledFromRest = -header.offset.toFloat()
                    val denom = (contentTopPx + headerH).coerceAtLeast(1f)
                    (scrolledFromRest / denom).coerceIn(0f, 1f)
                }
            }
        }

        val barShown by remember { derivedStateOf { progress.value > 0.55f } }
        val morph = animateFloatAsState(
            targetValue = if (barShown) 1f else 0f,
            animationSpec = spring(
                dampingRatio = if (barShown) 0.62f else 0.9f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "topBarMorph",
        )

        Box(modifier = Modifier.fillMaxSize()) {
            // layer 0: the cover (reused as-is)
            PlaylistCoverBackground(
                coverUri = album.coverUri,
                backgroundColor = { themeState.value },
                coverHeight = coverHeight,
                scrollProgress = { progress.value },
                modifier = Modifier.align(Alignment.TopStart),
                placeholderSeed = album.id.toString(),
            )

            // layer 1: content
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = coverHeight - titleOverlap,
                    bottom = bottomInset + screenH * 0.04f,
                ),
            ) {
                item(key = "header") {
                    AlbumHeader(album = album, sidePad = sidePad, ...)
                }
                itemsIndexed(songs) { index, song ->
                    AlbumSongRow(song = song, ..., sidePad = sidePad, ...)
                }
            }
        }

        // layer 2: top bar (reused as-is or extracted to a shared composable)
        PlaylistTopBar(
            title = album.title,
            morph = { morph.value },
            topInset = topInset,
            sidePad = sidePad,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}
```

### Suggested refactor

If you have three detail screens (playlist, album, artist), extract the shared scaffolding into a composable:

```kotlin
@Composable
fun DetailScaffold(
    coverUri: String?,
    title: String,
    placeholderSeed: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    insets: WindowInsets = WindowInsets.systemBars,
    header: @Composable () -> Unit,
    content: LazyListScope.() -> Unit,
) { ... }
```

Then each screen becomes:

```kotlin
DetailScaffold(
    coverUri = album.coverUri,
    title = album.title,
    placeholderSeed = album.id.toString(),
    onBack = onBack,
    header = { AlbumHeader(album) },
    content = { itemsIndexed(songs) { ... } },
)
```

---

## Part 5 — Gotchas & lessons learned

1. **Blur radius is in actual pixels, NOT dp.** Mochy uses `CoverSoftRadius = 30f` (30px). Using `32.dp` (~88px on a 2.75× density phone) is 3× stronger and makes the cover smear into a flat puddle of the background colour — it looks like it vanished.

2. **`LazyListItemInfo.offset` is `0` at rest** (content-area coordinates), NOT `contentPadding.top` (viewport coordinates). Using `contentTopPx - header.offset` gives `contentTopPx` at rest → progress ≈ 0.74 → cover alpha ≈ 0.07 → invisible. The correct formula is `-header.offset`.

3. **The header item MUST have `key = "header"`** — the progress calculation looks it up by key in `visibleItemsInfo`.

4. **`compositingStrategy = Offscreen`** is required when `p > 0f` for the `DstIn` dissolve to work. Without it, the dissolve gradient does nothing.

5. **`RenderEffect` requires API 31+** (Android 12). Below S, the cover stays sharp (no blur). Gate it: `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)`.

6. **`scrollProgress` and `backgroundColor` are lambdas** — read only in the `graphicsLayer` / draw phase. This is critical for performance: scrolling never triggers recomposition of the cover. If you pass a plain `Float` or `Color`, every scroll frame recomposes the entire cover tree.

7. **The alpha fade** (`COVER_FADE_FROM` / `COVER_FADE_TO`) makes the cover transparent as it scrolls away. This is Mochy's behaviour. Without it, the cover's sliver stays at the top after it scrolls up — may look odd.

8. **The blend gradient is static** — its stops never depend on scroll. Don't make it scroll-driven or it'll "expand" and look like a growing rectangle.

9. **No second blur layer.** The cover's own `RenderEffect` blur is the only blur. Don't add a kyant top-blur overlay on top of it — that creates a visible "double blur" band and a weird animation in the centre.

10. **The big title scrolls naturally** with the list (no separate fade/scale animation). Only the small title in the top bar animates (spring morph). Adding a scroll-driven fade/scale to the big title causes a weird "centre opening animation" effect.

---

## Reference: Mochy's source

The behaviour was ported from Mochy's codebase:

- **`ScrollAwayCover.kt`** — the cover's translate / zoom / fade / dim / dissolve / blur choreography. Constants: `CoverFollow`, `CoverZoom`, `CoverFadeFrom`, `CoverFadeTo`, `CoverDim`, `CoverSoftRadius`, `CoverSoftBy`.
- **`PlaylistDetail.kt` → `PlaylistCoverBackdrop`** — calls `ScrollAwayCover` with `follow = -0.8` for the backdrop.
- **`PlaylistDetail.kt` → `PlaylistDetailScaffold`** — the screen layout.

All constants match Mochy exactly. The progress calculation differs (Lutea uses `header.offset` from `layoutInfo`; Mochy uses `firstVisibleItemScrollOffset / collapseHeightPx`) because Lutea's header is shorter than the cover, which would cause a gap with Mochy's approach.
