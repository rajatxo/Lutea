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
9. **Metadata:** MusicBrainz (free, no key) + Wikipedia REST API for artist bios/images + Cover Art Archive for album art
10. **Architecture:** MusicSource interface as extension point for future streaming sources
11. **Play Store:** Core app = local only; streaming modules distributed via GitHub separately

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
