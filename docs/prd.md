# Lutea — Product Requirements Document (PRD)

## Product Overview
Lutea is a music player for Android that plays local audio files with a focus on
beautiful UI and smooth animations. It will eventually support streaming (YouTube
Music, JioSaavn) via an extension/module system, but the core app on Play Store
is local-only.

## Target Audience
- Users who have local music files on their Android device
- Users who want a beautiful, animated music player (not a generic list app)
- Users who want playlist management with a unique wheel UI

## Core Features (Implemented)
- ✅ Local audio playback (MediaStore + ExoPlayer)
- ✅ Playlist wheel UI (semicircle arc with rotating text)
- ✅ Playlist creation, rename, delete, cover photo
- ✅ Playlist detail screen (Mochy-style cover-to-background blend)
- ✅ Mini player + full player (expandable)
- ✅ Gooey metaball menu animation (4 pills morph from hollow circles)
- ✅ Fluid navigation bar (3 expanding circles)
- ✅ Title animation (per-word layers, search capsule morph)
- ✅ Artist page (MusicBrainz + Wikipedia enrichment)
- ✅ Album page (album art + track list)
- ✅ MusicBrainz integration (artist bios, album info, Cover Art Archive)
- ✅ Wheel search (filter playlists by name, rotate-to-match animation)
- ✅ Search history pills
- ✅ Haptic feedback (wheel + nav bar)
- ✅ Dark theme with frosted glass effects

## Future Features
- Folder-based scanning (SAF folder picker)
- Streaming modules (YouTube Music, JioSaavn) — distributed via GitHub, not Play Store
- Lyrics display (LRCLIB)
- MusicBrainz audio fingerprinting (Chromaprint) for unknown files
- Equalizer / audio effects
- Crossfade between tracks
- Sleep timer
- Discord rich presence

## Technical Requirements
- **Min SDK:** 29 (Android 10)
- **Target SDK:** 35 (Android 15)
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Playback:** Media3 (ExoPlayer + MediaSessionService)
- **Image Loading:** Coil 3
- **Color Extraction:** androidx.palette
- **Background Blur:** kyant0 backdrop library
- **Min Android version:** 10 (API 29)
- **Target devices:** Phones (no tablet optimization yet)

## App Flow
1. **First launch:** NameCardScreen (onboarding) → user enters name → PermissionScreen → grant storage + notification permissions → QuickPicksScreen
2. **Subsequent launches:** QuickPicksScreen directly (onboarding complete flag persisted)
3. **QuickPicksScreen** → LuteaScaffold wraps content with:
   - Title at top (user name + "Search your playlist here")
   - Menu button (top-right, Library tab only)
   - Fluid nav bar (bottom: Home, Search, Library)
   - Mini player (above nav bar when playing)
   - Full player (overlay when expanded)
4. **Home tab:** Quick picks song list (from MediaStore)
5. **Search tab:** Search interface (placeholder)
6. **Library tab:** Playlist wheel (LuteaArc) — rotate to select playlist
7. **Playlist detail:** Cover image blends into background, song list scrolls
8. **Artist page:** Click artist name in full player → artist info (MusicBrainz)
9. **Album page:** Click song title in full player → album track list

## Play Store Strategy
- Core app on Play Store = local playback only (safe, no ToS violations)
- Streaming modules distributed via GitHub / F-Droid (not Play Store)
- Extension-based architecture (MusicSource interface) allows adding sources later
