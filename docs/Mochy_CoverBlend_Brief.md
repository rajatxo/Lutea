# Task: Playlist detail screen — cover-to-background color blend (Mochy-style)

> **Reference:** Study the Mochy music app at `https://github.com/realidkroo/Mochy-Music.git`
> for its playlist detail background technique. **Do NOT copy any code from Mochy.**
> Study how it works, understand the design principles, and implement your own version
> for Lutea. The user will place every button and UI element themselves — your ONLY job
> is making the playlist cover image blend seamlessly into the background color.

## Project
- **Repo**: `https://github.com/rajatxo/Lutea.git` (private)
- **Branch**: `main`
- **Clone**: `git clone https://rajatxo:<YOUR_GITHUB_TOKEN>@github.com/rajatxo/Lutea.git`
- **Package**: `com.rajatxo.lutea`
- **App**: Lutea — Android music player (Kotlin + Jetpack Compose)
- **Min SDK**: 29 · **Target SDK**: 35 · **Compile SDK**: 35

## The file to modify
`app/src/main/java/com/rajatxo/lutea/ui/screens/PlaylistDetailScreen.kt`

Also potentially relevant (already exist in Lutea):
- `app/src/main/java/com/rajatxo/lutea/ui/screens/PlaylistCoverBackground.kt` — current cover background
- `app/src/main/java/com/rajatxo/lutea/ui/util/LuteaColorExtractor.kt` — current palette extraction

Read these files first to understand what Lutea currently has, then replace the
background technique with the Mochy-style blend described below.

## What I want — the visual result

When the user opens a playlist detail page:
1. The **playlist cover image** is displayed at the top of the screen (full width,
   roughly 60% of screen height, `ContentScale.Crop`).
2. The cover **seamlessly blends** into a **background color** extracted from the
   cover itself. There is NO visible seam or hard edge where the cover ends and
   the background begins.
3. The background color is **dark** (dark enough for white text to be legible) but
   still **tinted with the cover's hue** — so the whole screen feels like it's
   "colored by the album art".
4. The content (song list, etc.) scrolls over this blended background.
5. The cover area darkens slightly as you scroll (for text legibility).

## The technique (study from Mochy, implement your own version)

### 1. Color extraction — sample the BOTTOM of the cover, not the whole image

This is the single most important trick. The color the cover blends INTO should be
extracted from the **bottom 38% of the cover image**, not the whole image. Why?
Because the cover's bottom edge IS the color it's fading toward — so a gradient from
"cover image" to "that color, darkened" never has a visible seam.

**How Mochy does it (study, don't copy):**
- Load the cover as a small bitmap (~112px)
- Use `Palette.from(bitmap)` with `.setRegion(0, height*0.62, width, height)` —
  samples only the bottom 38%
- Pick the swatch with the highest `population` (not `dominantSwatch`)
- `maximumColorCount = 12`

### 2. Darken the extracted color using HSL, not RGB multiplication

Convert the extracted color to HSL and apply:
- **Saturation:** × 0.9, capped at 0.62 (prevents neon-looking colors)
- **Lightness:** × 0.5, clamped to [0.09, 0.30] (guarantees dark enough for white
  text, never pure black, never too light)
- **Hue:** preserved exactly (so the color still "reads" as the cover's color)

This ensures:
- A near-white cover → L ≈ 0.30 (dark enough for white text)
- A flat black cover → L ≈ 0.09 (never pure black, doesn't vanish)
- A bright neon cover → saturation capped at 0.62 (not eye-searing)

### 3. The blend gradient — NOT linear, uses smoothstep easing

The cover stays **fully visible** through the top ~56% of its height. Then between
**56% and 84%**, it crossfades to the background color, reaching 78% of the
background color at 84%, and **100% background at the bottom edge**.

Gradient stops (vertical, top to bottom of the cover area):
```
0.00 → transparent (cover fully visible)
0.56 → transparent (cover still fully visible)
0.84 → background color at 78% alpha
1.00 → background color at 100% alpha (fully blended)
```

Also add a **top scrim** for status bar legibility:
```
0.00 → Black at 34% alpha
0.24 → transparent
```
This covers the top 24% of the cover area only.

### 4. Smoothstep easing — `3t² − 2t³`

Use the Hermite smoothstep curve `t * t * (3 - 2 * t)` for ALL transitions (color
crossfade, alpha fades, blur ramps). This gives a soft S-curve that looks cinematic
— no linear "ramp" feel, no harsh cubic-in/out.

```kotlin
fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
```

### 5. Color crossfade animation

When the playlist detail page opens:
- Start with a neutral background color (black or theme background)
- Once the Palette extraction completes (~tens of ms after cover loads), **crossfade
  to the extracted color over 600ms** using `animateColorAsState(tween(600))`
- This prevents a jarring color flash on first load
- Cache the extracted color (LruCache, ~96 entries keyed by cover URL/URI) so
  subsequent opens are instant

### 6. The cover image

- Full width, height = ~60% of screen height
- `ContentScale.Crop`
- Coil/AsyncImage for loading
- No blur on the cover itself at rest (it's sharp). Blur and dim ramps in on scroll
  (optional — the user said they'll handle the rest of the UI, so just the at-rest
  blend is the priority)

## What already exists in Lutea (read these before implementing)

1. **`PlaylistCoverBackground.kt`** — Lutea's current cover background. Read it to
   see what's already there and what needs replacing.
2. **`LuteaColorExtractor.kt`** — Lutea's current palette color extraction. Read it
   to see the current extraction approach.
3. **`PlaylistDetailScreen.kt`** — the playlist detail screen. Read it to understand
   the current layout and where the cover + background should go.
4. **`Playlist.kt`** (data model) — has `coverUri` field for the cover image URI.

## Constraints

1. **Do NOT copy Mochy's code.** Study the technique, understand why it works,
   write your own implementation.
2. **The user will place every button and UI element themselves.** Your job is ONLY
   the cover image + background color blend. Don't add play buttons, song lists,
   menus, or any other UI elements.
3. **Min SDK 29.** Palette API works on all API levels. `RenderEffect` (blur)
   requires API 31+ — if you use blur, add a fallback for API 29-30.
4. **Universal Layout Laws** (from the project handover):
   - Use `BoxWithConstraints` at root — `maxWidth` / `maxHeight`, never
     `screenHeightDp`
   - Use `% of maxWidth / maxHeight` for all screen-relative sizing
   - Manual edge-to-edge (already handled by MainActivity)
5. **Coil 3** is the image loading library (`io.coil-kt.coil3:coil-compose:3.0.4`).
   Use `AsyncImage` for loading cover images.
6. **Palette** is available (`androidx.palette:palette-ktx:1.0.0`).

## What to deliver

The complete replacement for `PlaylistCoverBackground.kt` (and optionally
`LuteaColorExtractor.kt` if you want to improve the color extraction). The code
should:

1. Display the cover image at the top (full width, ~60% screen height, Crop)
2. Extract the background color from the bottom 38% of the cover (Palette + HSL
   darkening)
3. Draw a smoothstep-eased vertical gradient that blends the cover into the
   extracted background color (cover visible top 56%, crossfade 56%→84%, full
   background at bottom)
4. Top scrim (black 34% alpha over top 24%) for status bar legibility
5. Animate the background color in over 600ms on first load
6. Cache the extracted color for instant subsequent loads
7. Fill the rest of the screen with the extracted background color (solid, below
   the cover area)

Output as a single Kotlin file (or two — the background composable + the color
extractor) ready to paste into the Lutea project.

## Goal

A playlist detail screen where the cover image is at the top and seamlessly
dissolves into a dark, hue-tinted background color — no visible seam, no hard
edge, the whole screen feels "colored by the album art". The blend uses
smoothstep easing for a cinematic feel, and the color is extracted from the
cover's bottom edge (darkened via HSL) so the transition is invisible.
