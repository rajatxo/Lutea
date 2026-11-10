# Lutea — Project Architecture

## Package Structure
```
com.rajatxo.lutea/
├── MainActivity.kt              — edge-to-edge, gesture exclusion, playback connect
├── LuteaApplication.kt          — init PlaylistStore + PlaylistStyleManager + UserNameManager
├── data/
│   ├── model/
│   │   └── Playlist.kt          — id: Long, name, songIds: List<Long>, coverUri: String?
│   ├── store/
│   │   └── PlaylistStore.kt     — CRUD playlists (SharedPreferences JSON persistence)
│   ├── scanner/
│   │   └── MusicScanner.kt      — MediaStore query, folder filtering, returns List<Song>
│   ├── source/
│   │   ├── MusicSource.kt       — interface (the extension point)
│   │   └── LocalMusicSource.kt  — local implementation (MediaStore + folder path)
│   ├── prefs/
│   │   ├── PlaylistStyleManager.kt — wheel simpler/advanced persistence
│   │   └── UserNameManager.kt   — user display name persistence
│   └── metadata/
│       └── MusicBrainzClient.kt — MusicBrainz + Wikipedia + Cover Art Archive
├── domain/
│   └── model/
│       └── Song.kt              — id: Long, title, artist, album, duration, uri, albumArtUri
├── playback/
│   └── PlaybackConnection.kt    — singleton, MediaController, StateFlows for UI
├── service/
│   └── LuteaPlaybackService.kt  — MediaSessionService, ExoPlayer with decoder fallback
└── ui/
    ├── theme/                   — Typography, Theme, Color extractors
    ├── components/              — CalSansFamily, BillieEilishFamily, BlurRevealText
    ├── icons/                   — LuteaIcons (Home, Search, Library, etc.)
    ├── foundation/
    │   ├── LuteaScaffold.kt     — root scaffold (insets, blur, menu, nav, mini player, full player)
    │   ├── LuteaTabCapsule.kt   — fluid 3-circle expanding nav bar
    │   ├── LuteaBackground.kt   — app background
    │   └── LocalLuteaWindowInsets.kt — CompositionLocal for insets
    ├── screens/
    │   ├── LuteaArc.kt          — playlist wheel (semicircle arc + rotating text)
    │   ├── QuickPicksScreen.kt  — home/search/library tabs + playlist detail routing
    │   ├── PlaylistDetailScreen.kt — Mochy-style cover blend + song list + frosted top bar
    │   ├── PlaylistCoverBackground.kt — cover image + smoothstep gradient + scroll choreography
    │   ├── AlbumScreen.kt       — album page (art + track list)
    │   ├── ArtistScreen.kt      — artist page (MusicBrainz image + bio + albums)
    │   ├── SettingsScreen.kt    — dev info, Telegram, support group
    │   ├── NameCardScreen.kt    — onboarding (name entry)
    │   └── PermissionScreen.kt  — permission request
    └── player/
        ├── MiniPlayer.kt        — compact bar above nav (art + title + play/pause + next)
        └── FullPlayer.kt        — full-screen player (art + seek bar + controls)
```

## Playback Architecture (Coral pattern)
```
MainActivity.onCreate()
  └─ LaunchedEffect { PlaybackConnection.connect(context) }
       └─ MediaController.Builder(context, SessionToken(LuteaPlaybackService))
            .buildAsync()
            .await()  ← kotlinx-coroutines-guava
            └─ MediaController (stored in PlaybackConnection singleton)
                 └─ Player.Listener (onIsPlayingChanged, onMediaItemTransition, onPlayerErrorChanged)

PlaybackConnection (singleton object)
  ├─ controller: MediaController?
  ├─ isPlaying: StateFlow<Boolean>
  ├─ currentSong: StateFlow<Song?>
  ├─ positionMs: StateFlow<Long>
  ├─ durationMs: StateFlow<Long>
  ├─ playSong(song, allSongs)  → setMediaItems + prepare + play
  ├─ playPause() / next() / prev() / seekTo()
  └─ All methods wrapped in try-catch (never crashes)

LuteaPlaybackService (MediaSessionService)
  ├─ DefaultRenderersFactory (setEnableDecoderFallback=true, EXTENSION_RENDERER_MODE_ON)
  ├─ ExoPlayer (REPEAT_MODE_ALL, audio focus, handleAudioBecomingNoisy)
  ├─ MediaSession (session activity = MainActivity)
  └─ No manual startForeground (Media3 handles it automatically)
```

## Extension Architecture
```
MusicSource (interface)
  ├─ name: String
  ├─ isStreaming: Boolean
  ├─ getAllSongs(): List<Song>
  ├─ search(query): List<Song>
  ├─ getArtists(): Map<String, List<Song>>
  ├─ getAlbums(): Map<String, List<Song>>
  ├─ getPlaybackUri(song): String
  └─ getAlbumArtUri(song): String?

LocalMusicSource (implements MusicSource)
  └─ Uses MusicScanner.scanMusic(context, folderPath)

Future: YouTubeMusicSource, JioSaavnSource (same interface, different backend)
```

## Key Dependencies
```kotlin
// Media3 — playback
implementation("androidx.media3:media3-exoplayer:1.5.1")
implementation("androidx.media3:media3-session:1.5.1")
implementation("androidx.media3:media3-ui:1.5.1")
implementation("androidx.media3:media3-decoder:1.5.1")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0") // .await() on ListenableFuture

// Images
implementation("io.coil-kt.coil3:coil-compose:3.0.4")

// Color extraction
implementation("androidx.palette:palette-ktx:1.0.0")

// Frosted glass blur
implementation("io.github.kyant0:backdrop:1.0.0")

// Animation
implementation("com.airbnb.android:lottie-compose:6.6.2")

// Serialization
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
```

## Manifest Permissions
```xml
READ_MEDIA_AUDIO              — access audio files (Android 13+)
READ_EXTERNAL_STORAGE          — access audio files (Android 10-12, maxSdkVersion=32)
POST_NOTIFICATIONS             — show playback notification
VIBRATE                        — haptic feedback
FOREGROUND_SERVICE             — run playback service in foreground
FOREGROUND_SERVICE_MEDIA_PLAYBACK — media playback foreground service type
WAKE_LOCK                      — keep CPU awake during playback
```
