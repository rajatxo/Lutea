# Lutea — Tasks & Progress

## ✅ Completed
- [x] Onboarding (NameCardScreen + PermissionScreen)
- [x] Playlist wheel UI (LuteaArc) — semicircle arc, rotating text, slider
- [x] Playlist CRUD (PlaylistStore — create, rename, delete, cover photo)
- [x] Wheel simpler + advanced modes (PlaylistStyleManager)
- [x] LuteaScaffold — root scaffold with insets, blur, nav, menu
- [x] Gooey metaball menu animation (4 pills morph from hollow circles)
- [x] Fluid nav bar (3 expanding frosted-glass circles)
- [x] Title animation (per-word layers, search capsule morph)
- [x] Search history pills
- [x] Playlist detail screen (Mochy-style cover blend)
- [x] Playlist cover background (smoothstep gradient + scroll choreography)
- [x] Color extractor (bottom-38% Palette + HSL darkening)
- [x] Mini player (bar above nav)
- [x] Full player (album art, seek bar, controls, swipe-to-close)
- [x] Playback service (Coral pattern — DefaultRenderersFactory + decoder fallback)
- [x] PlaybackConnection (MediaController + await + onPlayerErrorChanged)
- [x] MusicSource interface (extension point)
- [x] LocalMusicSource (MediaStore + folder filtering)
- [x] MusicBrainzClient (artist search, album search, Cover Art Archive, Wikipedia bio)
- [x] Artist page (image + bio + albums)
- [x] Album page (art + track list)
- [x] Clickable title/artist in full player → album/artist pages
- [x] Haptic feedback (Vibrator API — wheel 10ms, nav 25ms)
- [x] Crash fixes (FOREGROUND_SERVICE permissions, decoder fallback, try-catch)
- [x] Empty playlist crash fix (hide arc/slider when no playlists)
- [x] Documentation files (prd.md, architecture.md, rules.md, design.md, tasks.md, memory.md)

## 🔄 In Progress
- [ ] MusicBrainz enrichment not showing on some artists (Wikipedia API may not have page for obscure artists)
- [ ] Folder picker UI (SAF OpenDocumentTree) — backend ready, UI not wired

## 📋 TODO (Priority Order)
- [ ] Folder picker UI — let user select which folder to scan
- [ ] Wire MusicBrainz into AlbumScreen (album art from Cover Art Archive, release year)
- [ ] Song list on Home tab — polish with album art thumbnails
- [ ] Search across full local library (not just playlist names)
- [ ] Lyrics display (LRCLIB — free, no key)
- [ ] Streaming module — YouTube Music source (GitHub distribution, not Play Store)
- [ ] Streaming module — JioSaavn source
- [ ] Audio fingerprinting (Chromaprint + AcoustID) for unknown files
- [ ] Equalizer / audio effects
- [ ] Crossfade between tracks
- [ ] Sleep timer
- [ ] Discord rich presence

## Build Info
- Current version: ~v0.1.261
- Package: com.rajatxo.lutea
- Min SDK: 29, Target SDK: 35, Compile SDK: 35
- CI: GitHub Actions (auto-bump version on every push)
