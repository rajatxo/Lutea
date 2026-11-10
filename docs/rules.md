# Lutea — Coding Rules

## The 10 Universal Layout Laws (MUST follow on every screen)

### Law 1: ONE CompositionLocal for ALL insets
```kotlin
val LocalLuteaWindowInsets = staticCompositionLocalOf<WindowInsets> {
    error("No WindowInsets provided — wrap your screen in LuteaScaffold")
}
```
Computed ONCE at root (in LuteaScaffold), consumed EVERYWHERE.

### Law 2: Manual edge-to-edge (NOT enableEdgeToEdge)
```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)
```
NOT `enableEdgeToEdge()` — that applies its own bar colors we can't control.

### Law 3: Theme XML with enforceNavigationBarContrast=false
Both status bar and navigation bar must be transparent with no system scrim.

### Law 4: Custom BottomSheet (NOT Material3 ModalBottomSheet)
Position via `graphicsLayer.translationY` (draw phase, zero recomposition).

### Law 5: preUpPostDownNestedScrollConnection
Lets inner LazyColumn scroll normally, hands gesture to the sheet when list reaches top.

### Law 6: System gesture exclusion zone
Exclude left 35% of screen for wheel drag gesture.

### Law 7: BoxWithConstraints at root (NEVER screenHeightDp)
```kotlin
BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val containerHeight = this.maxHeight  // actual measured height
    val containerWidth = this.maxWidth    // actual measured width
}
```
NEVER use `LocalConfiguration.current.screenHeightDp` — breaks in multi-window.
NEVER use hardcoded dp for screen-relative sizing — use `% of maxWidth/maxHeight`.
The ONLY hardcoded dp: UI element sizes (button width, text size, stroke thickness).

### Law 8: Manifest flags
```xml
android:enableOnBackInvokedCallback="true"
android:windowSoftInputMode="adjustResize"
android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|..."
android:launchMode="singleTask"
```

### Law 9: Status bar icon appearance flips per screen
Light icons on dark backgrounds, dark icons on light backgrounds.

### Law 10: BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE for immersive mode

## Code Style
- **Package:** `com.rajatxo.lutea`
- **Language:** Kotlin
- **UI:** Jetpack Compose only (no XML layouts)
- **Fonts:** BillieEilishFamily (display), CalSansFamily (UI text) — from `com.rajatxo.lutea.ui.components`
- **Colors:** Dark theme. Background `Color(0xFF0A0A0A)` or `Color.Black`. Text `Color.White` with varying alpha.
- **Animations:** Use `spring()` with `DampingRatioMediumBouncy` for bouncy effects, `tween(300, easing = FastOutSlowInEasing)` for smooth transitions.
- **Haptics:** Use `Vibrator` API directly with `createOneShot(ms, DEFAULT_AMPLITUDE)`. Wheel: 10ms. Nav bar: 25ms.
- **Playback:** All `PlaybackConnection` methods are wrapped in try-catch. Never crashes on playback errors.
- **Clickable elements:** Always gate `.clickable()` on visibility via `derivedStateOf`. `graphicsLayer { alpha = 0f }` does NOT disable pointer input in Compose.

## Build & Push Workflow
```bash
cd /home/z/my-project/lutea
git fetch origin main
git add <files>
git commit -m "<message>"
git stash && git pull --rebase origin main && git stash pop
git push origin main
```
Always wait for CI build to pass before declaring done. If build fails, fix and re-push.

## Anti-Patterns (NEVER do)
- ❌ NEVER use `screenHeightDp` or `screenWidthDp` for sizing
- ❌ NEVER use `enableEdgeToEdge()` — use `WindowCompat.setDecorFitsSystemWindows(window, false)`
- ❌ NEVER put `.clickable()` on an invisible element (alpha=0) without gating it on visibility
- ❌ NEVER use `Material3 ModalBottomSheet` — use custom `graphicsLayer.translationY`
- ❌ NEVER store secrets/tokens/passwords in code or documentation
- ❌ NEVER use `RenderEffect.createBlurEffect` with ×72/-36 alpha threshold on Android (jagged edges) — use ×18/-8 instead
- ❌ NEVER call `performHapticFeedback()` without `FLAG_IGNORE_VIEW_SETTING | FLAG_IGNORE_GLOBAL_SETTING` — may silently fail
- ❌ NEVER declare `foregroundServiceType` in manifest without the corresponding `FOREGROUND_SERVICE_*` permission
