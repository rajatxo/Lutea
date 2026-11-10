# Lutea — UI/UX Design Direction

## Overall Aesthetic
- **Dark theme** — near-black backgrounds (`#0A0A0A` or `#000000`)
- **White text** with varying alpha for hierarchy
- **Frosted glass** — kyant0 backdrop blur on nav bar, menu pills, search capsule
- **Smooth animations** — springs with bouncy damping, smoothstep easing curves
- **Typography-driven** — large display fonts, minimal iconography

## Fonts
| Font | Usage | Import |
|---|---|---|
| BillieEilish | Display text (playlist names, titles, artist names) | `com.rajatxo.lutea.ui.components.BillieEilishFamily` |
| CalSans | UI text (labels, buttons, metadata) | `com.rajatxo.lutea.ui.components.CalSansFamily` |
| NyghtSerif | Secondary (wheel simpler mode, not primary) | Available in res/font/ |
| CalSans SemiBold | Wheel title "Search your playlist here" | Same as CalSans |

## Key UI Components

### Playlist Wheel (LuteaArc)
- Semicircle arc on the left edge, radius = 28% of screen width
- 11 playlist text items positioned along the arc at 22° spacing
- Active item: UPPERCASE, BillieEilish font, large (50-60sp)
- Inactive items: lowercase, smaller (36-49sp), decreasing opacity
- Opacity (Advanced mode): 100% → 90% → 75% → 62% → 45% → 30%
- Slider: right edge, 7.5% from right, 25% opacity track, 55% of screen height
- Thumb: 20×60dp, 3D gradient (#4A4A4A→#121212), drop shadow

### Gooey Menu (4 pills)
- 4 hollow circles (2×2 grid) at top-right, Library tab only
- On tap: circles morph into 4 white/grey gradient pills
- Gooey metaball filter: blur(12px) + alpha threshold (×18/-8)
- Two sequential phases: fast morph (150ms) + slow separation (750ms spring)
- Pills: "New Playlist", "Select Tag", "Favourite", "Settings"

### Fluid Nav Bar (3 expanding circles)
- 3 frosted glass circles: Home, Search, Library
- Active tab expands into a pill with icon + label
- Spring animation (dampingRatio=0.7, stiffness=400)
- Soft shadow (6dp), thin bright rim, glossy text reflection
- Glint sweep on tab open

### Title Animation (per-word layers)
- 5 words: "(name),", "Search", "your", "playlist", "here"
- Each word is a separate layer with animated offset + font size
- On search open: username + "here" fade out, words shrink + slide to one line
- Search capsule appears at line 2's old position, full width
- Cross icon replaces menu button (mutually exclusive)

### Playlist Detail (Mochy-style cover blend)
- Cover image at top (60% screen height, ContentScale.Crop)
- Cover blends into background via smoothstep gradient:
  - 0→56%: cover fully visible
  - 56→84%: crossfade to background color
  - 84→100%: solid background color
- Background color extracted from bottom 38% of cover (Palette + HSL darkening)
- Saturation ×0.9 (cap 0.62), Lightness ×0.5 (clamp 0.09-0.30)
- Scroll choreography: parallax (0.8×), zoom (+0.22), fade, dissolve, progressive blur
- Frosted top bar morphs in at scrollProgress > 0.55

### Mini Player
- Bar above nav bar (56dp height, #1A1A1A background)
- Album art thumbnail (40dp rounded) + title + artist + play/pause + next
- Slide-up animation when song starts, slide-down when stopped

### Full Player
- Full-screen overlay, slides up from bottom (400ms FastOutSlowInEasing)
- Large album art (85% width, square, 20dp rounded corners)
- Title (BillieEilish 28sp) + artist (CalSans 16sp) — both clickable
- Seek bar (Material3 Slider) with position/duration labels
- Controls: prev (56dp), play/pause (72dp filled white), next (56dp)
- Swipe down or tap close to collapse

### Artist Page
- Circular artist image (35% screen width) from Wikipedia REST API
- Artist name (BillieEilish 34sp, centered)
- Song count + album count
- Biography from Wikipedia (up to 6 lines)
- Albums grouped from local library, each album clickable → AlbumScreen

### Album Page
- Album art (70% width, square, 20dp rounded)
- Album name (BillieEilish 30sp) + artist + song count + duration
- Track list with track numbers + durations

## Color Palette
| Usage | Color |
|---|---|
| App background | `#0A0A0A` or `#000000` |
| Card/surface | `#1A1A1A` or `#1C1C1E` |
| Primary text | `Color.White` |
| Secondary text | `Color.White.copy(alpha = 0.55f)` |
| Tertiary text | `Color.White.copy(alpha = 0.35f)` |
| Accent (wheel simpler) | `#F4B400` (amber) |
| Pill gradient | White (0.95) → Grey (0.85) |
| Search capsule gradient | White (0.95) → #B0B0B0 (0.85) |
| Slider track | White (0.25 alpha) |
| Thumb gradient | #4A4A4A → #121212 |

## Haptics
| Component | Vibration | Method |
|---|---|---|
| Wheel (item boundary) | 10ms one-shot | `Vibrator.createOneShot(10, DEFAULT_AMPLITUDE)` |
| Nav bar (tab change) | 25ms one-shot | `Vibrator.createOneShot(25, DEFAULT_AMPLITUDE)` |
| Sound | Disabled (temporarily) | — |
