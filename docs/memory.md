# Lutea — Project Context & Memory

## Developer Info
- **Name:** Rajat
- **GitHub:** rajatxo
- **Telegram:** https://t.me/rajat_xo
- **Support group:** https://t.me/coralmusicsupport
- **App name:** Lutea (will rename to Coral later — do NOT use "Coral" in code/comments for now)

## Communication Rules
- Be direct, no fluff
- Push after EVERY change, wait for green build before declaring done
- Don't ask questions — make decisions and go
- "Check Coral" = github.com/rajatxo/Coral.git (the user owns this app, can copy code)
- "Check Mochy" = github.com/realidkroo/Mochy-Music.git (study technique, do NOT copy code)
- Universal Layout Laws MUST be followed on every screen
- Do NOT create files in download/ or share files in chat — upload to repo only
- Use text responses, not file uploads in IM
- If build fails, fix it and re-push — never leave a red build

## Key Decisions Made
1. **Playback pattern:** Copied from Coral (MediaController + await + MediaSessionService, NOT Mochy's bindService pattern)
2. **Renderers:** DefaultRenderersFactory with setEnableDecoderFallback(true) + EXTENSION_RENDERER_MODE_ON
3. **Gooey filter:** blur(12px) + alpha threshold ×18/-8 (NOT ×72/-36 which causes jagged edges on Android)
4. **Haptics:** Vibrator API directly (createOneShot), NOT performHapticFeedback (unreliable on some devices)
5. **Title:** Per-word layers with TextMeasurer for position interpolation
6. **Search:** Username + "here" fade out when search opens; remaining words shrink + slide to one line
7. **Nav bar:** No sound effects; medium vibration (25ms) on tab change
8. **Menu:** Library tab only (not on Home/Search tabs)
9. **Metadata:** Last.fm is the PRIMARY source (top tracks for smart sorting, artist images, bios, similar artists, listener counts). Fallbacks in order: YouTube Data API v3 (image, same channel thumbnail as YouTube Music) → Wikipedia REST API (bio) → TheAudioDB (bio). MusicBrainz/Cover Art Archive stays for album art.
10. **Architecture:** MusicSource interface as extension point for future streaming sources
11. **Play Store:** Core app = local only; streaming modules distributed via GitHub separately
12. **Smart sorting:** Last.fm top tracks are matched against the user's local songs by normalized title. Sort by rank (most popular first). Songs the user doesn't have are NEVER shown. Unknown local songs go to the bottom alphabetically.
13. **Ponytail:** `AGENTS.md` at repo root holds the lazy-senior-dev ruleset + project-specific overrides. Every AI session (Claude Code, Codex, Cursor, Gemini) auto-loads it. Follow it.
14. **CI:** GitHub Actions builds `assembleRelease`. The gradle wrapper download (services.gradle.org) sometimes 504s — `gradle-wrapper.properties` is set to `networkTimeout=120000` + `retries=3` so it survives transient outages. Don't reduce these.

## Inspiration Repos
- **Coral:** github.com/rajatxo/Coral.git — user owns this, can copy code directly
- **Mochy-Music:** github.com/realidkroo/Mochy-Music.git — study technique, do NOT copy code
- **Coral-X:** github.com/rajatxo/Coral-X.git — Universal Layout Laws reference

## Common Crash Causes (Fixed)
1. Missing FOREGROUND_SERVICE + FOREGROUND_SERVICE_MEDIA_PLAYBACK permissions → service can't start
2. Missing DefaultRenderersFactory.setEnableDecoderFallback(true) → decoder errors crash
3. Invisible .clickable() elements (alpha=0 doesn't disable pointer input) → gate on visibility via derivedStateOf
4. % playlists.size with empty list → division by zero → use .coerceAtLeast(1)
5. LaunchedEffect keyed on scrollOffset.value → 60fps SharedPreferences thrashing → key on centerIndex only
6. CI build "failed" with no code error → gradle wrapper download 504 from services.gradle.org. Fixed by bumping `networkTimeout` to 120000 + `retries` to 3 in `gradle/wrapper/gradle-wrapper.properties`. Don't push "retry build" commits — fix the wrapper config instead.
7. **Filament Engine leak → crash on Effect tab after a few opens.** `FilamentViewport`'s `DisposableEffect` had an empty `onDispose {}` — every open created a new Engine, Renderer, Scene, View, Camera, two LightManager entities, AssetLoader, UbershaderProvider, ResourceLoader, the GLB asset, and a self-reposting Choreographer callback, NONE of which were freed. After ~7 tab switches GPU native handles exhausted → crash on next `Engine.create()`. Fix: full teardown in `onDispose` in reverse order (choreographer → swapchain → asset → assetLoader → materialProvider → lights → camera → view → scene → renderer → engine), `Utils.init()` BEFORE `Engine.create()`, GLB byte-read on `Dispatchers.IO`, 0×0 viewport guard, `catch` blocks log to logcat instead of swallowing silently. Filament is NOT garbage-collected — every `create*` must have a matching `destroy*`.

## File Locations (Quick Reference)
- Wheel: `app/src/main/java/com/rajatxo/lutea/ui/screens/LuteaArc.kt`
- Scaffold: `app/src/main/java/com/rajatxo/lutea/ui/foundation/LuteaScaffold.kt`
- Nav bar: `app/src/main/java/com/rajatxo/lutea/ui/foundation/LuteaTabCapsule.kt`
- Playback: `app/src/main/java/com/rajatxo/lutea/playback/PlaybackConnection.kt`
- Service: `app/src/main/java/com/rajatxo/lutea/service/LuteaPlaybackService.kt`
- Scanner: `app/src/main/java/com/rajatxo/lutea/data/scanner/MusicScanner.kt`
- MusicBrainz: `app/src/main/java/com/rajatxo/lutea/data/metadata/MusicBrainzClient.kt`
- Playlist detail: `app/src/main/java/com/rajatxo/lutea/ui/screens/PlaylistDetailScreen.kt`
- Cover background: `app/src/main/java/com/rajatxo/lutea/ui/screens/PlaylistCoverBackground.kt`
- Color extractor: `app/src/main/java/com/rajatxo/lutea/ui/util/LuteaColorExtractor.kt`
- Mini player: `app/src/main/java/com/rajatxo/lutea/ui/player/MiniPlayer.kt`
- Full player: `app/src/main/java/com/rajatxo/lutea/ui/player/FullPlayer.kt`
- Album page: `app/src/main/java/com/rajatxo/lutea/ui/screens/AlbumScreen.kt`
- Artist page: `app/src/main/java/com/rajatxo/lutea/ui/screens/ArtistScreen.kt`
- Icons: `app/src/main/java/com/rajatxo/lutea/ui/icons/LuteaIcons.kt`
- Fonts: `app/src/main/java/com/rajatxo/lutea/ui/components/BlurRevealText.kt`
- Song model: `app/src/main/java/com/rajatxo/lutea/domain/model/Song.kt`
- Playlist model: `app/src/main/java/com/rajatxo/lutea/data/model/Playlist.kt`
- MusicSource interface: `app/src/main/java/com/rajatxo/lutea/data/source/MusicSource.kt`
- LocalMusicSource: `app/src/main/java/com/rajatxo/lutea/data/source/LocalMusicSource.kt`

## Signing
- Keystore is stored as a GitHub Secret on CI (not in the repo)
- See GitHub repo Settings → Secrets and variables → Actions for:
  CORAL_X_KEYSTORE_BASE64, CORAL_X_STORE_PASSWORD, CORAL_X_KEY_ALIAS, CORAL_X_KEY_PASSWORD
- Do NOT put any signing info in code or documentation files
