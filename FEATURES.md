# dotLog — Feature Documentation

**Version:** 1.0
**Package:** `com.example.dotlog`
**Platform:** Android 10+ (minSdk 29)
**Architecture:** MVI (Model-View-Intent)

---

## 1. Architecture Overview

dotLog uses a single-screen MVI pattern with three core layers:

```
data/              → Room entity/DAO, repositories, Overpass API
ui/                → Compose UI (MainScreen, MapView, VisitsList)
MainViewModel.kt   → State / Action / Event definitions + ViewModel
```

### State Flow

```
User Action → MainAction → ViewModel.onAction() → _state.update { copy(...) }
                                                      ↓
Room DB → visitRepository.allVisits → combine(_state, ...) → state: StateFlow
                                                      ↓
                                              collectAsStateWithLifecycle() → UI
```

One-time effects (snackbar, share intents) use a separate `Channel<MainEvent>` collected via `collectLatest`.

### Key files

| File | Role |
|------|------|
| `MainViewModel.kt` | State data class, Action/Event sealed interfaces, all business logic |
| `MainScreen.kt` | `MainScreenRoot` (permissions, theme, snackbar) + `MainScreen` (layout) |
| `MapViewCompose.kt` | osmdroid `MapView` wrapped in `AndroidView` with markers, long-press overlay |
| `VisitsList.kt` | Animated bottom panel with search, visit items, edit/delete dialogs |
| `VisitRepository.kt` | Duplicate-aware `addVisit()` using Haversine (100m radius) |
| `LocationRepository.kt` | GPS via `FusedLocationProviderClient`, fresh-fix fallback |
| `PoiRepository.kt` | Overpass API POI lookup with widening radii (200m → 500m → 1km) |

---

## 2. Features

### 2.1 GPS Location & Map

**Startup sequence:**
1. Request `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` (if not granted → permission screen)
2. `viewModelScope.launch` in `init` block:
   - Try `getLocationUpdates(1000)` with freshness filter (age < 30s, accuracy < 100m) — **10s timeout**
   - If timeout → fallback to `requestSingleFreshLocation()` (uses `CancellationTokenSource` so GPS gets time to lock)
3. Once location is obtained → resolve POI name via Overpass → update `currentLocation` + `currentPlaceName` in state
4. Map centers on the location

**Map controls:**
- Pinch-to-zoom, pan
- Zoom buttons hidden
- "You are here" marker at `currentLocation`
- Long-press anywhere to log a visit at that coordinates

### 2.2 Log Visit (current location)

**Tap the "Log Visit" button** at the bottom:
1. Plays haptic feedback (`VirtualKey`)
2. Checks `_state.value.currentLocation` — if null, no-op (GPS hasn't locked yet)
3. Calls `visitRepository.addVisit(lat, lon, placeName, timestamp)`
4. `VisitRepository.addVisit()` checks Haversine distance to latest visit:
   - ≤ 100m → **updates** the latest visit's name + timestamp (duplicate suppression)
   - > 100m → **inserts** a new row
5. Emits `VisitLogged` event → snackbar "Visit logged"

### 2.3 Log Visit (map long-press)

**Long-press anywhere on the map:**
1. `MapEventsOverlay` fires `OnMapLongClick(lat, lon)`
2. ViewModel resolves POI name via Overpass API for those coordinates
3. Sets `pendingLogLocation` + `pendingLogPlaceName` in state
4. Confirmation dialog appears showing:
   - Coordinates (read-only)
   - Place name **editable text field** (pre-filled from Overpass)
   - Date picker (default: today, opens Material3 `DatePickerDialog`)
   - Time picker (default: now, opens `TimePicker`)
5. **Confirm** → logs the visit with the chosen name, date, and time
6. **Cancel** → clears pending state, closes dialog

### 2.4 Visit History

**Toggle button (top-right)** — opens/closes an animated bottom panel showing all logged visits.

**Visit list features:**
- **Search:** `OutlinedTextField` with debounced text filter — filters by `placeName.contains(query, ignoreCase = true)`
- **Tap a visit:** zooms map to that location (zoom 18) AND keeps the history panel open
- **Long-press a visit:** options dialog with:
  - **Edit name** — text field pre-filled, saves via `updateVisit()`
  - **Delete** — removes via `deleteVisit()` (error-colored text button)
- **"Report a map issue"** link at the bottom — opens OpenStreetMap fix page

### 2.5 Settings (top-left gear icon)

Settings dialog with two sections:

**Appearance — Dark mode:**
- `Switch` toggles dark/light theme
- Preference saved via `SharedPreferences` (survives app restart)
- Uses Android 12+ dynamic color when available, else hardcoded light/dark schemes

**Data:**
- **Export visits (CSV):** Generates CSV with header `latitude,longitude,placeName,timestamp`. Place names with commas are properly quoted (RFC 4180). Shares via `ACTION_SEND` with `createChooser`.
- **Import visits (CSV):** Opens file picker (`OpenDocument`). Parses CSV with quoted field support. Skips header line. Inserts each row via `addVisit()` — duplicate radius (100m) applies.

### 2.6 Refresh Location

**Crosshair icon** on the POI card (bottom-center):
1. Calls `requestSingleFreshLocation()` (forces a new GPS fix via `getCurrentLocation` with high accuracy)
2. Re-resolves POI name from Overpass
3. Updates `currentLocation` + `currentPlaceName`

### 2.7 Theme

- **Light scheme:** White surface, `Neutral90` background, `ActionBlue` primary
- **Dark scheme:** `Neutral10` surface/background, `Neutral90` text
- Android 12+ dynamic color overrides the hardcoded palettes
- Theme is set in `MainScreenRoot` using `DotlogTheme(darkTheme = state.isDarkMode)` — wraps the entire scaffold

---

## 3. Data Layer

### 3.1 Room Database

**Table:** `visits`

| Column | Type | Notes |
|--------|------|-------|
| `id` | `Long` (PK, autoGenerate) | |
| `latitude` | `Double` | WGS-84 |
| `longitude` | `Double` | WGS-84 |
| `placeName` | `String` | From Overpass or user-entered |
| `timestamp` | `Long` | Unix millis (epoch) |

**DAO operations:** `getAllVisits()`, `getLatestVisit()`, `insert()`, `update()`, `delete()`

**Database:** `dotlog_database`, single instance (singleton via `AppDatabase.getDatabase()`). Destructive migration fallback enabled.

### 3.2 VisitRepository

```
addVisit(lat, lon, name, ts):
  latest = getLatestVisit()
  if latest && distance(latest, new) <= 100m:
    update(latest.copy(name=name, ts=ts))   // deduplicate
  else:
    insert(Visit(lat, lon, name, ts))       // new visit
```

### 3.3 LocationRepository

Wraps `FusedLocationProviderClient`:
- `getCurrentLocation()` — tries `lastLocation` then `getCurrentLocation()`
- `getLocationUpdates(interval)` — `callbackFlow` based, returns `Flow<Location>`
- `requestSingleFreshLocation()` — uses `CancellationTokenSource` for a fresh GPS fix

All methods annotated with `@SuppressLint("MissingPermission")` — permissions are checked at the UI level.

### 3.4 PoiRepository

Overpass API call with widening search radii:
1. `node(around:200,lat,lon)[name];` → 200m
2. If empty → `node(around:500,lat,lon)[name];` → 500m
3. If empty → `node(around:1000,lat,lon)[name];` → 1km
4. If still empty → `"Unnamed area"`

Uses OkHttp with custom `User-Agent` header. Retrofit base URL: `https://overpass-api.de/`.

---

## 4. Permissions

| Permission | Required for | Requested |
|------------|-------------|-----------|
| `ACCESS_FINE_LOCATION` | GPS lock | On launch |
| `ACCESS_COARSE_LOCATION` | Network fallback | On launch |
| `INTERNET` | Map tiles, Overpass API | Manifest only |

No `ACCESS_BACKGROUND_LOCATION`, `ACTIVITY_RECOGNITION`, or `POST_NOTIFICATIONS` — the app has no background tracking.

---

## 5. osmdroid Configuration

**Tile source:** MAPNIK (OpenStreetMap)

**User-Agent fix:** MAPNIK tile source uses `FLAG_USER_AGENT_NORMALIZED`, so `setUserAgentValue()` is ignored. The override is applied via:
```kotlin
osmConfig.additionalHttpRequestProperties["User-Agent"] = customUA
```
This map is iterated AFTER the UA header, so `HttpURLConnection.setRequestProperty()` overwrites the normalized UA.

**Storage:** App's internal `cacheDir/osmdroid/` — avoids scoped storage issues on Android 10+.

**Tile download threads:** 2 (set in `DotlogApplication.onCreate()`)

---

## 6. Known Behaviors & Edge Cases

| Scenario | Behavior |
|----------|----------|
| GPS not locked on launch | Map shows ocean (0,0). `currentLocation` is null. Log button is no-op. POI card shows "Searching..." |
| Duplicate visit within 100m | Updates the latest visit's name + timestamp instead of inserting |
| Place name contains comma | Properly quoted in CSV export (`"Cafe, Cairo"`); parsed correctly on import |
| CSV import header line | Skipped (detected by `startsWith("latitude,")`) |
| First-time user, no visits | Empty history panel shows "No visits yet" with icon |
| Dark mode on Android < 12 | Falls back to hardcoded dark color scheme (no dynamic color) |
| Visit click | Zooms map AND opens history panel simultaneously |
| Map long-press | Opens confirmation dialog — no zoom/animation |
| Overpass API rate limit | POI lookup only on launch + explicit refresh + long-press. Single-threaded. |
| Tile cache | Persistent across app launches (no longer wiped) |

---

## 7. UI Component Tree

```
MainScreenRoot
├── PermissionRequestScreen          (if permissions not granted)
└── Scaffold
    └── Box
        ├── MapViewCompose           (full-screen osmdroid map)
        │   ├── CopyrightOverlay
        │   ├── TilesOverlay
        │   ├── MapEventsOverlay     (long-press handler)
        │   ├── Marker (current)     ("You are here")
        │   └── FolderOverlay        (history markers, toggled by showHistory)
        ├── FilledIconButton         (top-right: history toggle)
        ├── FilledIconButton         (top-left: settings)
        ├── AlertDialog              (settings panel)
        ├── Column                   (bottom-center)
        │   ├── Card                 (POI card: icon + place name + refresh)
        │   └── Button               ("Log Visit")
        ├── AlertDialog              (long-press confirmation + date/time pickers)
        ├── DatePickerDialog         (shown on demand from long-press dialog)
        ├── AlertDialog w/ TimePicker (shown on demand from long-press dialog)
        └── VisitsList               (animated bottom panel)
            ├── OutlinedTextField    (search bar)
            ├── LazyColumn           (visit items)
            └── TextButton           ("Report a map issue")
```

---

## 8. Testing

**Unit tests:** `./gradlew testDebugUnitTest`

| Test file | Coverage |
|-----------|----------|
| `VisitRepositoryTest.kt` | Haversine distance (same point, 100m, antipodal), insert/update/delete/updateVisit |
| `PoiRepositoryTest.kt` | Radius widening (200m → 500m → 1km → fallback) |
| `CsvTest.kt` | CSV parsing (plain fields, quoted commas, embedded quotes, header, export line, empty fields) |

**No UI/instrumentation tests** currently.
