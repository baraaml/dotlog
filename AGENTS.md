# AGENTS.md — dotLog

Persistent project memory. Update this as conventions/gotchas emerge — don't let
lessons live only in chat history.

## Project Shape

- Single-screen Android app, MVI (not MVVM), no navigation graph
- Package layout:
  - `data/` — Room entity/DAO, LocationRepository, OverpassApi (Retrofit)
  - `ui/` — MainScreen.kt (Root+Screen), MainViewModel.kt, MapViewCompose.kt, VisitsList.kt
- minSdk 29 (Android 10)
- Manual location logging only — no background tracking, no foreground service,
  no activity recognition

## MVI Architecture

- **State** — single `MainState` data class with all UI fields
- **Action** — `MainAction` sealed interface with `OnLogClick`, `OnToggleHistory`, `OnVisitClick`, `OnZoomConsumed`
- **Event** — `MainEvent` sealed interface with `VisitLogged` (one-time Channel events)
- **ViewModel** — `MainViewModel(application: Application)` holds `_state: MutableStateFlow`, processes `onAction()`, emits events via `Channel`
- **UI** — `MainScreenRoot` (holds ViewModel, permissions, events) → `MainScreen(state, onAction)` (pure composable)
- **State updates** always use `_state.update { it.copy(...) }` — never replace the entire flow
- **Events** collected via `LaunchedEffect` + `collectLatest` in Root

## Rules / Footguns

- **Duplicate visit radius is 100m (Haversine).** Don't change without updating the
  corresponding unit test.
- **POI lookup radius widens 200m → 500m → 1km, then falls back to "Unnamed area".**
  Don't silently change these thresholds — they were a deliberate product decision.
- **Log button uses `_state.value.currentLocation` + `_state.value.currentPlaceName`.**
  Both are set during the `init` block's GPS-fix wait. If GPS hasn't locked, location is null
  and `logCurrentVisit()` is a no-op.
- **Initial location waits for a fresh GPS fix (max 15s).** Uses
  `getLocationUpdates(1000).filter(age < 30s && accuracy < 100m).first()`.
  Stale `lastLocation` (off by continents) is never consumed.
- **Overpass public instance is rate-limited.** POI lookups happen on launch
  (initial GPS fix) and on map long-press only — keep it that way. Never add
  per-render or continuous POI lookups.
- **`_state` and `visitRepository.allVisits` are combined via `combine()`** into the
  single `state: StateFlow`. Don't add separate StateFlows — merge into `_state`.
- English only for MVP — don't hardcode strings in a way that blocks future Arabic
  support (still use `strings.xml`, just no translation work yet).

## Build → Verify → Fix Loop

1. `./gradlew assembleDebug` — must succeed with zero warnings treated as new
2. Manual on-device check for the step just built
3. If broken: fix, rebuild, re-verify
4. Exit condition: **max 3 fix attempts per step** before stopping to reassess the
   approach rather than continuing to patch

## Testing Commands

- Unit tests: `./gradlew testDebugUnitTest`

## Known Gotchas Log

- **osmdroid Tile Block — MAPNIK ignores `setUserAgentValue()`:** OpenStreetMap
  servers block generic or missing `User-Agent` headers. MAPNIK tile source has
  `FLAG_USER_AGENT_NORMALIZED`, so `TileDownloader` uses `getNormalizedUserAgent()`
  (packageName/versionCode) instead of `getUserAgentValue()`. The fix is
  `osmConfig.additionalHttpRequestProperties["User-Agent"] = customUA` — this map
  is iterated AFTER the UA header, so `HttpURLConnection.setRequestProperty()`
  overwrites the normalized UA with the custom one.
- **No initial location → blank map:** `currentLocation` starts `null`, so the map
  centers at (0,0) — ocean. The `init` block first tries `getLocationUpdates()` with
  freshness filter (10s timeout), then falls back to
  `requestSingleFreshLocation()`. On most devices this yields a fix within a few
  seconds.
- **`requestSingleFreshLocation()` uses `CancellationTokenSource`** so GPS gets
  time to lock instead of returning null or network-triangulated garbage. Called as
  the fallback when the freshness-filtered update flow times out.
- **Visit click also opens history panel** — `OnVisitClick` sets both
  `zoomTarget` and `showHistoryOnMap = true`, so the visits list slides up and the
  map zooms to the selected location.
- **CopyOnWriteArrayList iterator doesn't support `remove()`.** Use `filter()` +
  `removeAll()` instead of iterating with `iter.remove()`.
- **osmdroid Storage on Android 10+:** Use the app's internal cache directory
  (`context.cacheDir`) for `osmdroidBasePath` and `osmdroidTileCache` to avoid
  scoped storage permission issues.
- **Library compileSdk Requirements:** Modern `androidx` libraries (like
  `lifecycle-viewmodel-compose:2.11.0`) require `compileSdk 37`. If build fails
  with AAR metadata errors, bump `compileSdk` in `app/build.gradle.kts`.
- **KSP Kotlin Source Sets:** Built-in Kotlin in AGP 9.0+ may conflict with KSP
  source set injection. If build fails with `Using kotlin.sourceSets DSL to add
  Kotlin sources is not allowed`, set `android.disallowKotlinSourceSets=false` in
  `gradle.properties`.
- **Permissions stored in Root composable state:** `permissionsGranted` is held
  in `remember { mutableStateOf(...) }` in `MainScreenRoot`, not in the ViewModel.
  The permission launcher is also in Root, not in `PermissionRequestScreen`.
- **`PermissionRequestScreen` is a simple callback screen** — it receives
  `onRequestPermissions: () -> Unit` and calls it when the user taps "Grant
  Permission". The actual `rememberLauncherForActivityResult` lives in
  `MainScreenRoot`.
- **Map long-press overlay must be preserved in update block**: `MapEventsOverlay`
  is added once in `remember` but gets filtered out in the `update` block's overlay
  removal logic unless `it !is MapEventsOverlay` is included in the filter predicate.
- **`rememberUpdatedState` for map callbacks**: The long-press callback lambda
  inside `MapEventsOverlay` uses `rememberUpdatedState(onMapLongPress)` so that
  recomposition with a new callback doesn't require recreating the overlay.
- **Map long-press workflow**: Dispatches `OnMapLongClick` → ViewModel sets
  `pendingLogLocation` + `pendingLogPlaceName = "Resolving..."` **immediately**
  (dialog appears instantly, never blocked on the slow rate-limited Overpass
  lookup) → POI name resolves in the background and is patched in via a
  `longPressToken` guard → `MainScreen` shows `AlertDialog` with coordinates +
  editable name → `OnConfirmLogLocation(name)` logs visit. No zoom/animation on
  long-press.
- **Long-press resolution guard uses a token, NOT `Location` equality.**
  `longPressToken` increments on every long-press; a slow lookup only patches the
  name if `token == longPressToken` AND `pendingLogLocation != null` (dialog not
  dismissed/replaced). Don't switch to comparing `Location` instances — it breaks
  under unit tests (mockable android.jar stubs `Location.equals()` to always
  return `false`).
- **`MainViewModel` is constructor-injectable.** Params
  (`visitRepository`, `poiRepository`, `searchRepository`, `locationRepository:
  LocationProvider`, `prefs`) have defaults wired to `DotlogApplication`, so
  production still uses `viewModel()` untouched but tests inject fakes.
  `LocationProvider` interface exists so tests never construct the real
  play-services `LocationRepository` (its constructor calls
  `LocationServices.getFusedLocationProviderClient` and throws in JVM tests).
- **Unit tests need `isReturnDefaultValues = true`** (set in `app/build.gradle.kts`
  testOptions). The mockable android.jar makes Android methods throw by default;
  this makes them return default values (0/null/false) so `Location("...")`
  construction and setters work in JVM tests.
- **ViewModel tests use `runTest(dispatcher)` + `Dispatchers.setMain(dispatcher)`.**
  See `MainViewModelTest`. The fake location provider's `getLocationUpdates`
  returns `flow { awaitCancellation() }` (never emits/completes) — do NOT use
  `emptyFlow()`, because `Flow.first()` on a completed empty flow throws
  `NoSuchElementException`, which crashes the VM init coroutine and surfaces as a
  test failure during `advanceUntilIdle()`.
