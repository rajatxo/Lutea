# Prompt for Claude — Lutea Lyrics Animation

Copy everything below the line and send it to Claude.

---

I'm building **Lutea**, an Android local music player in Kotlin + Jetpack Compose. The repo is at `github.com/rajatxo/Lutea` (public). The app already has a working player (mini player + full-screen player), a bottom nav scaffold, and a `LyricsScreen.kt` scaffold with a back button, song title display, and an empty scrollable `LazyColumn` ready to be filled.

The playback state is exposed via:
- `PlaybackConnection.currentSong` — a `Song` with `title: String?`, `artist: String?`, `albumArtUri: String?`, `durationMs: Long`
- `PlaybackConnection.positionMs` — `Long`, current playback position in milliseconds
- `PlaybackConnection.isPlaying` — `Boolean`
- `PlaybackConnection.durationMs` — `Long`

The app uses Coil 3 for images, has two custom fonts (`BillieEilishFamily` for display, `CalSansFamily` for body), and its package is `com.rajatxo.lutea`. Dark theme throughout.

## What I need from you

**The lyrics ANIMATION only — not the fetching/provider.** I want the "normal" word-by-word synced lyrics style, like Apple Music:

- All lyrics lines visible in a scrollable list
- The **current line** (the one being sung) is larger, bright white, full opacity
- **Upcoming lines** below it: slightly dimmed (~50% opacity), normal size
- **Past lines** above it: more dimmed (~24% opacity), normal size
- When word-level timestamps are available, individual words **"rise"** as they're sung — a smooth, subtle scale-up + brighten effect per word, following each word's start/end time
- The list **auto-scrolls** to keep the current line at about 1/3 from the top of the screen
- **User scroll** temporarily pauses auto-scroll for ~3.5 seconds, then resumes
- Interlude gaps (instrumental sections between lines) show as subtle dots or just empty space

## Reference (behavior only — do NOT copy code)

**Mochy Music** (`github.com/realidkroo/Mochy-Music`) is a Compose music player with this exact lyrics style. They call it "Style 1" / `MOTION`. Look at these files in their repo to understand the behavior:

- `LyricsScreen.kt` — the full lyrics page, auto-scroll logic, user-scroll pause
- `LyricsGlowLine.kt` — the per-line renderer, line states (ACTIVE/UPCOMING/PAST/DEFAULT), word-by-word karaoke sweep
- `LyricsStagger.kt` — the cascade follow-through where rows below the active line trail slightly behind the scroll

Study the **behavior**, not the implementation. Mochy is GPLv3 — write your own original code.

## The data shape (so you know what to render)

Standard LRC format — `[mm:ss.xx]lyric text` per line. Some tracks have word-level "rich sync" timing where each word is tagged:

```
[00:12.34]<00:12.34>Hello <00:12.80>world <00:13.20>this <00:13.60>is <00:14.00>a <00:14.40>line
```

I'll handle the fetching and parsing separately. What I need from you is a composable that takes a **list of lyrics lines** and the **current playback position** and renders the animated list. Define the data model yourself — something like:

```kotlin
data class WordTimestamp(val text: String, val startMs: Long, val endMs: Long)
data class LyricsLine(
    val timeMs: Long,           // line start time
    val text: String,           // full line text
    val words: List<WordTimestamp>? = null  // word-level timing, null = no word sync
)
```

## Output

Give me a Kotlin file (or 2-3 files if you split the data model, the composable, and the auto-scroll logic) that I can drop into `app/src/main/java/com/rajatxo/lutea/ui/screens/` next to the existing `LyricsScreen.kt`. Include:

1. **The data model** (`LyricsLine`, `WordTimestamp`)
2. **The lyrics list composable** — takes `lines: List<LyricsLine>`, `positionMs: Long`, and renders the scrollable list with:
   - Line states: ACTIVE (current, full size ~22sp, white, 100% opacity), UPCOMING (~50% opacity), PAST (~24% opacity)
   - Word-by-word rise animation when `words` is non-null (each word scales up slightly + brightens as its time range is active)
   - Auto-scroll keeping the current line at ~1/3 from top
   - User scroll pauses auto-scroll for 3.5s
3. **A helper** to find the current line index from `positionMs` (binary search on `lines.timeMs`)

Do NOT include:
- Network calls / lyrics fetching (I'll handle that separately with LRCLIB)
- LRC parsing (I'll handle that separately)
- Any API keys or secrets (none needed)

Write original code. Use `CalSansFamily` for the lyrics text font. Dark background `Color(0xFF0A0A0A)`. The code should compile with the project's existing Compose dependencies (no new libraries needed — pure Compose + standard Kotlin).
