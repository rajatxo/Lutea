# Task: Full playlist detail screen — Mochy-style structure + cover blend

> **IMPORTANT:** Study the Mochy music app at `https://github.com/realidkroo/Mochy-Music.git`
> to understand the playlist detail screen structure and background technique. **Do NOT
> copy any code from Mochy.** Study how it works, understand the design principles,
> and write your own implementation for Lutea. The user will fine-tune button positions
> after — your job is to get the full structure + background blend right.
>
> **Clone Mochy to study:** `git clone https://github.com/realidkroo/Mochy-Music.git`
> Key files to read:
> - `app/src/main/kotlin/com/mochyy/mochymusic/ui/screens/playlist/LocalPlaylistScreen.kt`
> - `app/src/main/kotlin/com/mochyy/mochymusic/ui/screens/playlist/OnlinePlaylistScreen.kt`
> - `app/src/main/kotlin/com/mochyy/mochymusic/ui/component/detail/PlaylistDetail.kt`
> - `app/src/main/kotlin/com/mochyy/mochymusic/ui/theme/CoverThemeColor.kt`
> - `app/src/main/kotlin/com/mochyy/mochymusic/ui/component/detail/ScrollAwayCover.kt`
>
> Read them to understand the TECHNIQUE. Then write your own code from scratch.

## Project
- **Repo**: `https://github.com/rajatxo/Lutea.git` (private)
- **Branch**: `main`
- **Clone**: `git clone https://rajatxo:<YOUR_GITHUB_TOKEN>@github.com/rajatxo/Lutea.git`
- **Package**: `com.rajatxo.lutea`
- **App**: Lutea — Android music player (Kotlin + Jetpack Compose)
- **Min SDK**: 29 · **Target SDK**: 35 · **Compile SDK**: 35

## Dependencies available
```kotlin
implementation("androidx.compose:compose-bom:2025.06.00")
implementation("androidx.media3:media3-exoplayer:1.5.1")
implementation("io.coil-kt.coil3:coil-compose:3.0.4")
implementation("androidx.palette:palette-ktx:1.0.0")
implementation("io.github.kyant0:backdrop:1.0.0")
implementation("com.airbnb.android:lottie-compose:6.6.2")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
```

## Files to modify/create

### Replace these existing files:
1. **`app/src/main/java/com/rajatxo/lutea/ui/screens/PlaylistDetailScreen.kt`** — the
   playlist detail screen (currently a basic placeholder)
2. **`app/src/main/java/com/rajatxo/lutea/ui/screens/PlaylistCoverBackground.kt`** — the
   cover background (currently basic)
3. **`app/src/main/java/com/rajatxo/lutea/ui/util/LuteaColorExtractor.kt`** — color
   extraction (currently basic)

### Read these for context (do NOT modify):
- **`app/src/main/java/com/rajatxo/lutea/data/model/Playlist.kt`** — data model:
  ```kotlin
  data class Playlist(
      val id: String,
      val name: String,
      val songIds: List<String> = emptyList(),
      val coverUri: String? = null
  )
  ```
- **`app/src/main/java/com/rajatxo/lutea/data/store/PlaylistStore.kt`** — playlist
  CRUD store. Has `playlists: StateFlow<List<Playlist>>`, `getSongs(playlistId)`, etc.
- **`app/src/main/java/com/rajatxo/lutea/domain/model/Song.kt`** — song model with
  `title`, `artist`, `album`, `duration`, `uri`, etc.
- **`app/src/main/java/com/rajatxo/lutea/playback/PlaybackConnection.kt`** — playback
  control: `PlaybackConnection.playSong(song, songs)`, etc.

---

## THE 10 UNIVERSAL LAYOUT LAWS (MUST follow on every screen)

These are hard-won patterns from 6+ production music player repos. EVERY screen
in Lutea MUST follow these.

### Law 1: ONE CompositionLocal for ALL insets
```kotlin
val LocalLuteaWindowInsets = staticCompositionLocalOf<WindowInsets> {
    error("No WindowInsets provided — wrap your screen in LuteaScaffold")
}
// Computed ONCE at root (in LuteaScaffold), consumed EVERYWHERE.
// Includes: system bars + nav bar + header height + tab capsule height.
```

### Law 2: Manual edge-to-edge (NOT enableEdgeToEdge)
```kotlin
// In MainActivity.onCreate:
WindowCompat.setDecorFitsSystemWindows(window, false)
// NOT: enableEdgeToEdge() — that applies its own bar colors we can't control
```

### Law 3: Theme XML with enforceNavigationBarContrast=false
```xml
<style name="Theme.Lutea" parent="android:Theme.Material.NoActionBar">
    <item name="android:windowBackground">@drawable/splash_background</item>
    <item name="android:statusBarColor">@android:color/transparent</item>
    <item name="android:navigationBarColor">@android:color/transparent</item>
    <item name="android:windowDrawsSystemBarBackgrounds">true</item>
    <item name="android:windowContentOverlay">@null</item>
    <item name="android:windowMinWidthMinor">100%</item>
    <item name="android:windowMinWidthMajor">100%</item>
    <item name="android:windowLayoutInDisplayCutoutMode">shortEdges</item>
    <item name="android:enforceStatusBarContrast" tools:targetApi="29">false</item>
    <item name="android:enforceNavigationBarContrast" tools:targetApi="29">false</item>
</style>
```

### Law 4: Custom BottomSheet (NOT Material3 ModalBottomSheet)
- Position via `graphicsLayer.translationY` (draw phase, zero recomposition)
- `collapsedBound = bottomInset + navBarHeight + miniPlayerHeight`
- `expandedBound = maxHeight` (from BoxWithConstraints)

### Law 5: preUpPostDownNestedScrollConnection
- Lets inner LazyColumn scroll normally
- Hands gesture to the sheet when the list reaches the top
- Pattern: `onPreScroll` + `onPostScroll` + `onPreFling` + `onPostFling`

### Law 6: System gesture exclusion zone
```kotlin
// In MainActivity, after setContent:
private fun setSystemGestureExclusion() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.decorView.post {
            val width = window.decorView.width
            val height = window.decorView.height
            val exclusionWidth = (width * 0.35f).toInt()
            window.setSystemGestureExclusionRects(
                listOf(android.graphics.Rect(0, 0, exclusionWidth, height))
            )
        }
    }
}
```

### Law 7: BoxWithConstraints at root (NEVER screenHeightDp)
```kotlin
BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val containerHeight = this.maxHeight  // actual measured height
    val containerWidth = this.maxWidth    // actual measured width
    // Works in multi-window, split-screen, floating windows, foldables.
    // NEVER use LocalConfiguration.current.screenHeightDp — breaks in multi-window.
    // NEVER use hardcoded dp values for screen-relative sizing — use percentages.
}
```

### Law 8: Manifest flags
```xml
<application
    android:enableOnBackInvokedCallback="true"
    android:windowSoftInputMode="adjustResize"
    android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|keyboard|smallestScreenSize|uiMode|locale|layoutDirection|fontScale|density"
    android:launchMode="singleTask">
```

### Law 9: Status bar icon appearance flips per screen state
```kotlin
WindowCompat.getInsetsController(window, decorView).apply {
    isAppearanceLightStatusBars = !isDark
    isAppearanceLightNavigationBars = !isDark
}
```

### Law 10: BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE for immersive mode
```kotlin
WindowInsetsControllerCompat(window, window.decorView).apply {
    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}
```

### Universal Fitting Rule Summary
- Use `% of maxWidth / maxHeight` for ALL screen-relative sizing (not hardcoded dp)
- Examples: `maxWidth * 0.08f` for slider gap, `maxHeight * 0.55f` for slider height
- The ONLY hardcoded dp values should be for UI element sizes (button width, text size,
  stroke thickness) — NOT for positioning relative to screen edges
- `BoxWithConstraints` is the root of every screen — never assume screen dimensions

---

## WHAT TO BUILD — the playlist detail screen

### Overall structure
A full-screen playlist detail page with:

1. **Cover image at the top** — full width, ~60% of screen height, `ContentScale.Crop`
2. **Cover blends seamlessly into background color** — no visible seam
3. **Background color** — extracted from the cover, darkened, hue-tinted
4. **Scrollable content below** — song list, playlist info, action buttons
5. **The cover scrolls away** as you scroll down (parallax + zoom + fade)
6. **A frosted top bar morphs in** when you scroll past ~55% (for back button + title)

### The cover-to-background blend (THE KEY TECHNIQUE)

This is the most important part. Study Mochy's `CoverThemeColor.kt` and
`ScrollAwayCover.kt` to understand it. Here's the technique:

#### 1. Color extraction — sample the BOTTOM of the cover, not the whole image

The color the cover blends INTO is extracted from the **bottom 38% of the cover
image**, not the whole image. The cover's bottom edge IS the color it's fading
toward — so a gradient from "cover image" to "that color, darkened" never has a
visible seam.

- Load the cover as a small bitmap (~112px, software bitmap for Palette)
- Use `Palette.from(bitmap)` with `.setRegion(0, height*0.62, width, height)` —
  samples only the bottom 38%
- Pick the swatch with the highest `population` (not `dominantSwatch`)
- `maximumColorCount = 12`

#### 2. Darken the extracted color using HSL

Convert the extracted color to HSL and apply:
- **Saturation:** × 0.9, capped at 0.62 (prevents neon)
- **Lightness:** × 0.5, clamped to [0.09, 0.30] (dark enough for white text,
  never pure black)
- **Hue:** preserved exactly

#### 3. The blend gradient — smoothstep easing

Cover stays fully visible through the top ~56%. Between 56% and 84%, it
crossfades to the background color. Full background at the bottom edge.

Gradient stops (vertical, top to bottom of the cover area):
```
0.00 → transparent (cover fully visible)
0.56 → transparent (cover still fully visible)
0.84 → background color at 78% alpha
1.00 → background color at 100% alpha
```

Top scrim for status bar legibility:
```
0.00 → Black at 34% alpha
0.24 → transparent
```

#### 4. Smoothstep function
```kotlin
fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)   // Hermite smoothstep, C¹-continuous
}
```
Use for ALL transitions (color crossfade, alpha fades, scroll ramps).

#### 5. Color crossfade animation
- Start with neutral background (black)
- Crossfade to extracted color over 600ms via `animateColorAsState(tween(600))`
- Cache extracted color (LruCache, ~96 entries keyed by cover URI) for instant
  subsequent opens

#### 6. Fill below the cover with the solid extracted background color

### Scroll choreography (study Mochy's ScrollAwayCover)

As you scroll the content:
1. **Cover rides up** — `translationY = scrollProgress × coverHeight × -0.8` (moves
   up at 80% of scroll speed)
2. **Cover zooms slightly** — `scale = 1 + scrollProgress × 0.22` (Ken Burns feel)
3. **Cover fades out** — `alpha = 1 - smoothstep(0.2, 0.85, scrollProgress)`
4. **Cover dims** — black overlay at `0.22 × scrollProgress` alpha
5. **Cover dissolves from bottom** — DstIn gradient mask that grows from bottom up
6. **Progressive blur** (API 31+) — cover blurs as it scrolls away (crossfade sharp→blurred)

Transform origin for zoom: `(0.5, 0.35)` — horizontal center, 35% down from top.

### Frosted top bar (morphs in on scroll)
- Appears when `scrollProgress > 0.55`
- Frosted glass background (blur + tint with the extracted color)
- Contains: back button + playlist name (collapses from large title to small)
- Spring animation for morph (dampingRatio ~0.62 going in, ~0.9 going out)

### Content (song list)
- LazyColumn that scrolls over the blended background
- Each song row: title, artist, duration, album art thumbnail
- Content padding: top = cover height (so songs start below the cover), bottom = nav bar
- The song list background is the solid extracted color (below the cover area)

### Action buttons (placeholder — user will reposition)
- Play button
- Shuffle button
- 3-dot menu (rename / change cover / delete)
- These can be simple placeholders — the user will reposition them later

---

## Key parameters cheat sheet

| Parameter | Value |
|---|---|
| Cover height | 0.6 × screen height |
| Cover image size | full width, ContentScale.Crop |
| Color sample region | bottom 38% (y from 0.62×h to h) |
| Color sample bitmap | ~112px |
| Palette maximumColorCount | 12 |
| Darken saturation | × 0.9, cap 0.62 |
| Darken lightness | × 0.5, clamp [0.09, 0.30] |
| Color cache | LruCache(96) |
| Color crossfade | 600ms tween |
| Blend gradient stops | 0→transparent, 0.56→transparent, 0.84→bg@78%, 1.0→bg@100% |
| Top scrim | 0→Black@34%, 0.24→transparent |
| Smoothstep | `t² × (3 − 2t)` |
| Cover scroll travel | -0.8 × height |
| Cover zoom on scroll | +0.22 × progress |
| Cover transform origin | (0.5, 0.35) |
| Cover alpha fade | `1 - smoothstep(0.2, 0.85, progress)` |
| Cover dim overlay | Black @ 0.22 × progress |
| Top bar morph threshold | scrollProgress > 0.55 |
| Top bar blur | max ~34 × morph progress |

---

## Fonts available
```kotlin
// In app/src/main/res/font/:
BillieEilishFamily  // billie_eilish.ttf — main display font
CalSansFamily       // cal_sans_semibold.ttf — UI font
// Also: nyght_serif_*.ttf, instrument_serif.ttf, picaflor.otf, jun_regular.otf,
// poppins_*.ttf
```

Import: `import com.rajatxo.lutea.ui.components.CalSansFamily`
       `import com.rajatxo.lutea.ui.components.BillieEilishFamily`

## What to deliver

Three complete Kotlin files, ready to paste into the Lutea project:

1. **`PlaylistCoverBackground.kt`** — the cover image + blend gradient + scroll
   choreography (parallax, zoom, fade, dissolve, blur). This is the main visual
   composable.

2. **`LuteaColorExtractor.kt`** — Palette color extraction from the bottom 38% of
   the cover, HSL darkening, LruCache, `rememberCoverThemeColor(coverUri)`
   composable function that returns a `State<Color>` (animated crossfade).

3. **`PlaylistDetailScreen.kt`** — the full screen that uses the cover background +
   color extractor + scrollable song list + frosted top bar + action buttons
   (placeholder positions).

All three must:
- Follow the 10 Universal Layout Laws (especially Law 7: BoxWithConstraints, never
  screenHeightDp)
- Use `% of maxWidth / maxHeight` for screen-relative sizing
- Work on Min SDK 29 (RenderEffect blur needs API 31+ fallback)
- Use Coil 3 `AsyncImage` for cover loading
- Use `androidx.palette` for color extraction
- NOT copy any code from Mochy — study the technique, write your own

## Goal

A playlist detail screen where:
1. The cover image is at the top and seamlessly dissolves into a dark, hue-tinted
   background color — no visible seam, no hard edge
2. The whole screen feels "colored by the album art"
3. Scrolling content rides over the blended background
4. The cover scrolls away with parallax + zoom + fade + dissolve + blur
5. A frosted top bar morphs in for navigation
6. The song list sits on the solid extracted color below the cover

The user will fine-tune button positions, sizes, and spacing after implementation.
Your job is to get the full structure + background blend + scroll choreography right.
