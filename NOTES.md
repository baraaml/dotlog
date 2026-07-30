# Notes — Things I Learned Building dotLog

---

## Rate Limiting

### What is it?

Servers have finite CPU, RAM, database connections, and network bandwidth. Every
request costs a slice of those resources. Rate limiting is the server telling you:
*"You're asking too fast — wait before asking again."*

**Example:** Overpass API runs on a PostgreSQL database. If 100 queries arrive
simultaneously but the pool only has 50 database connections, the excess 50 queue
up. If they queue faster than they drain → memory runs out → server crashes.
Rate limiting rejects early before the expensive database path is hit.

### How servers enforce it

- **HTTP 429 "Too Many Requests"** — the standard response. Includes a
  `Retry-After` header telling you how many seconds to wait.
- **Connection drop / timeout** — the server silently closes the connection.
- **IP block** — after repeated violations, they block your IP entirely.

### Common rate limits in the OSM ecosystem

| API | Limit | Why it matters for us |
|-----|-------|-----------------------|
| **Nominatim** (search) | 1 req/sec | Search-as-you-type needs debounce |
| **Overpass** (POI lookup) | ~1 req/sec, 10k/day | We only look up on launch + refresh + long-press |
| **MAPNIK** (tiles) | Varies, usually generous | Blocked if User-Agent is missing or generic |

### How to handle rate limiting (different approaches)

| Approach | How it works | When to use |
|----------|-------------|-------------|
| **Debounce** | Wait for the user to *stop typing* for N ms before sending | Search-as-you-type (keystroke → API) |
| **Throttle** | At most 1 request every N seconds, no matter what | Background sync, periodic polls |
| **Queue with delay** | Put requests in a queue, send them 1 second apart | Batch imports, multiple dependent API calls |
| **Backoff & retry** | On 429, wait 1s, then 2s, then 4s... (exponential) | Any critical API call |
| **Cache results** | Store API responses locally so you don't re-ask | POI lookups for the same coordinates |
| **Batched requests** | Combine multiple queries into one API call | Overpass supports complex queries in a single request |

**What we already use in dotLog:**
- POI lookups are cached per session (in-memory state, not re-queried unless user
  explicitly refreshes or long-presses a new spot)
- Only 1 Overpass call per action (not per location update)
- Tile cache is persistent (no longer wiped on launch)

**What we'll add for search:**
- **Debounce** the search input (500ms after the user stops typing)
- Space Nominatim calls at least 1 second apart

---

## MVI (Model-View-Intent)

MVI is a stricter version of MVVM where:
- **State** is a single immutable data class (one source of truth)
- **Intent/Action** is a sealed interface describing what the user wants to do
- The ViewModel never exposes mutable state — only a `StateFlow` of the single state
- UI sends actions, ViewModel produces new state

### Why MVI over MVVM in this app

MVVM often ends up with multiple `MutableStateFlow` fields (`_location`, `_visits`,
`_isLoading`, etc.). Each one can change independently, and it's easy to forget to
reset one when another changes. With a single `MainState`, you're forced to think
about the whole screen state at once. `_state.update { it.copy(…) }` makes every
transition explicit.

### The three parts

```
MainState    → data class with every UI field (nullable Location, list of visits, etc.)
MainAction   → sealed interface: OnLogClick, OnToggleHistory, OnVisitClick, …
MainEvent    → sealed interface: VisitLogged (snackbar), ExportReady (share intent)
```

Events are one-shot (Channel), not part of the persistent state.

---

## StateFlow / collectAsStateWithLifecycle

### StateFlow vs LiveData

`StateFlow` is Kotlin's reactive state holder (part of `kotlinx.coroutines.flow`).
It always has a current value (`flow.value`). It's lifecycle-aware *only* when
collected with `collectAsStateWithLifecycle()`.

**Why not LiveData?**
- LiveData is tightly coupled to Android (LifecycleOwner)
- StateFlow is pure Kotlin — testable without Android
- StateFlow works seamlessly with `combine`, `map`, `filter`, etc.

### combine operator

```kotlin
val state = combine(_state, visitRepository.allVisits) { local, visits ->
    local.copy(visits = filtered)
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainState())
```

This merges two flows:
1. Local MVI state (isDarkMode, currentLocation, searchQuery, …)
2. Room database flow (all visits)

Every time *either* changes, `combine` re-runs the lambda. The result is a single
`StateFlow<MainState>` that the UI observes.

### WhileSubscribed(5000)

The upstream flows remain active for 5 seconds after the last subscriber goes away
(e.g. screen rotated). If the user comes back within 5s, no re-query needed.
This avoids wasting GPS resources when the app is truly in the background.

---

## Room Database

### What Room does for you

Room is an ORM (Object-Relational Mapper) that generates SQLite code from Kotlin
annotations. You write:

```kotlin
@Entity
data class Visit(val id: Long, val placeName: String, …)

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits")
    fun getAll(): Flow<List<Visit>>
}
```

Room generates the SQL `CREATE TABLE`, `INSERT`, `SELECT` statements at compile time.

### Flow return type from DAO

When a DAO method returns `Flow<List<Visit>>`, Room watches the underlying table
and emits a new list whenever *any* row changes. This is how the UI stays in sync
without manual refresh — when `addVisit()` inserts a row, the Flow automatically
re-emits the updated list.

### The singleton pattern with double-checked locking

```kotlin
companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(…).build()
            INSTANCE = instance
            instance
        }
    }
}
```

- `@Volatile` — ensures all threads see the latest value of INSTANCE
- `synchronized` — prevents two threads from building the database at the same time
- The `?:` (elvis) means most calls skip the synchronized block entirely (fast path)

### fallbackToDestructiveMigration()

If your database schema changes (e.g. you add a column to `Visit`), Room needs to
migrate the old table to the new one. Without migration code OR this flag, the app
crashes on launch. `fallbackToDestructiveMigration()` means "just delete the old
data and recreate the table" — safe for MVP, but you lose user data on each schema
change.

---

## Retrofit + OkHttp Interceptors

### What Retrofit does

Retrofit turns a Kotlin interface into HTTP calls:

```kotlin
interface OverpassApi {
    @GET("api/interpreter")
    suspend fun query(@Query("data") data: String): OverpassResponse
}
```

Calling `api.query("[out:json];…")` sends a GET request to
`https://overpass-api.de/api/interpreter?data=[out:json];…` and deserializes the
JSON response into `OverpassResponse`.

### OkHttp Interceptors

An interceptor runs on every request before it's sent:

```kotlin
.addInterceptor { chain ->
    val request = chain.request().newBuilder()
        .header("User-Agent", USER_AGENT)
        .build()
    chain.proceed(request)
}
```

This ensures the `User-Agent` header is set on every request without repeating it
in every function. You can also use interceptors for logging, retry logic, or
adding auth tokens.

---

## Haversine Formula

### What it calculates

The straight-line distance between two points on a sphere (Earth), accounting for
curvature. Not driving distance — "as the crow flies."

### Why not the Pythagorean theorem?

At Earth's scale, latitude and longitude are *not* on a flat grid:
- 1 degree of latitude ≈ 111km everywhere
- 1 degree of longitude ≈ 111km at the equator, **0km at the poles**
Using `sqrt(dlat² + dlon²)` would give nonsense near the poles.

### The formula (simplified)

```
a = sin²(Δlat/2) + cos(lat1)·cos(lat2)·sin²(Δlon/2)
c = 2 · atan2(√a, √(1-a))
distance = R · c    (R = 6371km)
```

We use this to check if a new visit is within 100m of the last one (duplicate
detection).

---

## Haptic Feedback

### What it is

Vibration patterns on touch. In Compose:

```kotlin
val haptic = LocalHapticFeedback.current
haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
```

### Types

| Type | Feel |
|------|------|
| `VirtualKey` | Short click (like a keyboard key) |
| `LongPress` | Deeper buzz |
| `TextHandleMove` | Subtle tick (text selection handle) |

We use `VirtualKey` on the Log button — gives immediate tactile confirmation
that the button was pressed, before the UI updates.

---

## osmdroid User-Agent Override

### Why this exists

OpenStreetMap tile servers block HTTP requests with generic or missing
User-Agent headers. MAPNIK tile source has `FLAG_USER_AGENT_NORMALIZED`, meaning
it ignores `setUserAgentValue()` and uses `getNormalizedUserAgent()` instead
(package name + version code).

### The fix

```kotlin
osmConfig.additionalHttpRequestProperties["User-Agent"] = customUA
```

This map is iterated *after* the normalized UA is set in the request headers.
`HttpURLConnection.setRequestProperty()` overwrites whatever was there before.
So the custom UA replaces the normalized one.

---

## CancellationTokenSource

### What it solves

When calling `getCurrentLocation()`, GPS might take several seconds to get a fix.
If the user leaves the screen or the coroutine is cancelled, we need to tell
Google Play Services "never mind, stop trying."

```kotlin
val source = CancellationTokenSource()
try {
    client.getCurrentLocation(priority, source.token).await()
} finally {
    source.cancel()  // Tell Play Services to abort if still waiting
}
```

Without this, the callback would fire after the coroutine is cancelled, leading
to wasted battery and potential crashes from updating detached state.

---

## CopyOnWriteArrayList

### Why osmdroid uses it

osmdroid's `MapView.overlays` is a `CopyOnWriteArrayList`. This is a thread-safe
list where every mutation (add, remove) creates a *copy* of the entire array.
Iteration is safe without locking.

### The trap

`CopyOnWriteArrayList`'s iterator does **not** support `remove()`:

```kotlin
// CRASHES:
for (overlay in mapView.overlays) {
    if (overlay !is CopyrightOverlay) mapView.overlays.remove(overlay)
}
```

Instead:

```kotlin
val toRemove = mapView.overlays.filter { … }
mapView.overlays.removeAll(toRemove)
```

The `filter` iterates (safe), then `removeAll` does a single bulk mutation.

---

## CSV Quoting (RFC 4180)

### The problem

A CSV row looks like `30.0,31.0,place name,1700000000000`. But what if the place
name contains a comma? E.g. `"Cairo, Egypt"` would split into two fields.

### The standard

- If a field contains a comma, double-quote, or newline, wrap it in double quotes:
  `"Cairo, Egypt"`
- If the field contains a double quote, escape it with another double quote:
  `8" pizza` → `"8"" pizza"`

### Our implementation

```kotlin
// Export quoting
val name = if (placeName.contains(',') || placeName.contains('"') || placeName.contains('\n')) {
    "\"${placeName.replace("\"", "\"\"")}\""
} else placeName

// Import parsing — state machine tracking in/out of quotes
fun parseCsvLine(line: String): List<String> {
    // When we see a ", flip inQuotes. Comma only splits when !inQuotes.
}
```

---

## callbackFlow

### What it is

A `Flow` builder that uses a callback-based API and converts it to a cold stream:

```kotlin
fun getLocationUpdates(interval: Long): Flow<Location> = callbackFlow {
    val callback = LocationCallback {
        result.lastLocation?.let { trySend(it) }
    }
    client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    awaitClose { client.removeLocationUpdates(callback) }
}
```

- `trySend()` emits a value into the flow (non-blocking)
- `awaitClose { … }` runs when the flow collector cancels — this is where you
  clean up the callback so you don't leak the listener

---

## rememberUpdatedState

### The problem

osmdroid's `MapEventsOverlay` is created once inside `remember`. But the callback
it uses (`onMapLongPress`) might change on recomposition (new lambda).

If the old lambda is captured inside the `remember` block, the overlay will call
the *old* function forever.

### The fix

```kotlin
val currentOnLongPress by rememberUpdatedState(onMapLongPress)

remember {
    MapEventsOverlay(object : MapEventsReceiver {
        override fun longPressHelper(p: GeoPoint): Boolean {
            currentOnLongPress(p.latitude, p.longitude)  // always the latest
            return true
        }
    })
}
```

`rememberUpdatedState` returns a `State` reference that always points to the
latest value of `onMapLongPress`. The `by` delegate reads the current value each
time the lambda runs. No need to recreate the overlay.

---

## Content Provider (OpenDocument)

### How import works

```kotlin
val importLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
) { uri ->
    uri?.let {
        val inputStream = context.contentResolver.openInputStream(it)
        val csvContent = inputStream?.bufferedReader()?.readText() ?: ""
        inputStream?.close()
        viewModel.onAction(MainAction.OnImportVisits(csvContent))
    }
}
```

- `OpenDocument` launches the system file picker
- The user picks a file → we get a `content://` URI (not a file path)
- `contentResolver.openInputStream(uri)` reads the content through Android's
  content provider system — works across apps without needing file permissions
- This is why we pass `arrayOf("text/*", "*/*")` as the MIME types

---

## Permission Handling Pattern

```kotlin
var permissionsGranted by remember { mutableStateOf(checkPermissions(context)) }

val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
) { _ -> permissionsGranted = checkPermissions(context) }

LaunchedEffect(Unit) {
    if (!permissionsGranted) {
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, …))
    }
}
```

- `permissionsGranted` is `remember` state in the Root composable (not ViewModel)
- `LaunchedEffect(Unit)` fires once on first composition
- After user responds, the callback re-checks and updates state
- `checkPermissions()` does `ContextCompat.checkSelfPermission()` — synchronous,
  safe to call on main thread

We keep this out of the ViewModel because permission state is inherently a UI
concern (it determines which screen to show).

---

## StateFlow combine + stateIn Pattern

```kotlin
val state: StateFlow<MainState> = combine(
    _state,                       // local MVI state
    visitRepository.allVisits     // Room Flow
) { local, visits ->
    val filtered = if (local.searchQuery.isBlank()) visits
    else visits.filter { it.placeName.contains(local.searchQuery, ignoreCase = true) }
    local.copy(visits = filtered)
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainState())
```

`stateIn` converts the cold `combine` flow into a hot `StateFlow`. The initial
value is `MainState()` (empty). `WhileSubscribed(5000)` keeps the upstream
(Room database, GPS) alive for 5s after the UI disconnects.

The filtering happens here — the `visits` list in state is always filtered by
`searchQuery`. When the user types in the search bar, `OnSearchQueryChange`
updates `_state.searchQuery`, which triggers `combine` to re-run, which filters
again. The Room database is only queried once — filtering is done in memory.

---

## @SuppressLint("MissingPermission")

### Why it's everywhere in LocationRepository

Google Play Services APIs require the calling code to prove permissions are
granted at compile time (via `@RequiresPermission`). But we check permissions
at runtime in the UI layer. The compiler doesn't know that.

```kotlin
@SuppressLint("MissingPermission")
suspend fun getCurrentLocation(): Location? { … }
```

**This is safe** because:
1. The user will see the permission screen if not granted
2. The ViewModel won't call `logCurrentVisit()` if `currentLocation` is null
3. The map won't show the "You are here" marker if `currentLocation` is null

---

## withTimeoutOrNull + elvis operator (?:)

```kotlin
val initialLocation = withTimeoutOrNull(10_000) {
    locationRepository.getLocationUpdates(1000)
        .filter { it.time > System.currentTimeMillis() - 30_000 && (it.accuracy ?: 999f) < 100f }
        .first()
} ?: locationRepository.requestSingleFreshLocation()
```

This is a pattern I haven't seen before:

1. Try to get a fresh GPS update within 10 seconds
2. While waiting, filter out stale locations (older than 30s) and inaccurate ones
   (accuracy >= 100m — the `?: 999f` handles null accuracy)
3. If `first()` emits within 10s → cool, use it
4. If timeout → `withTimeoutOrNull` returns null → elvis fires → fallback to
   `requestSingleFreshLocation()` which uses `CancellationTokenSource`

The `filter` on `getLocationUpdates` is why stale `lastLocation` (which can be
off by continents) is never consumed.

---

## Sealed Interfaces for Actions/Events

```kotlin
sealed interface MainAction {
    data object OnLogClick : MainAction
    data class OnVisitClick(val lat: Double, val lon: Double) : MainAction
}
```

- `sealed` means all implementations are in the same file — the `when` in
  `onAction()` is exhaustive (compiler checks you handle every case)
- `data object` for actions with no parameters (singleton)
- `data class` for actions with parameters (immutable, destructuring)
- No `else` branch needed in the `when` — adding a new action gives a compile
  error until you handle it

---

## How to Build a New Feature (Step-by-Step)

This is the process I follow for every feature. The search feature we just built
is used as the example.

### Step 1 — Define the data model

Before writing any code, decide: *what data does this feature produce/consume?*

**Ask yourself:**
- What fields does a result have?
- What types? (String, Double, Long, custom class?)
- Is it persisted (Room) or ephemeral (in-memory)?

**Example — Search feature:**
```kotlin
data class SearchResult(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val type: String
)
```

### Step 2 — Build the API / data layer (new file)

Create the Retrofit interface + a repository class.

**Pattern:**
```
data/YourApi.kt       → Retrofit interface (what endpoints, what response format)
data/YourRepository.kt → Business logic (transform API response → your data model)
```

**Add the repository to `DotlogApplication.kt`:**
```kotlin
val yourRepository by lazy { YourRepository.create() }
```

**Example files:** `GeocodingApi.kt`, `SearchRepository.kt`

### Step 3 — Add to state + actions (MainViewModel.kt)

**State:** Every new feature needs fields in `MainState`:
```kotlin
data class MainState(
    // … existing fields …
    val locationSearchQuery: String = "",
    val locationSearchResults: List<SearchResult> = emptyList(),
    val isLocationSearching: Boolean = false
)
```

**Actions:** Every user interaction needs a `MainAction`:
```kotlin
sealed interface MainAction {
    // … existing actions …
    data class OnLocationSearchQueryChange(val query: String) : MainAction
    data object OnClearLocationSearch : MainAction
    data class OnLocationSearchResultClick(val result: SearchResult) : MainAction
}
```

### Step 4 — Wire ViewModel logic

**For simple actions** (toggle, update state): one-liner in `onAction()`:
```kotlin
is MainAction.OnClearLocationSearch -> {
    _state.update { it.copy(locationSearchQuery = "", locationSearchResults = emptyList()) }
}
```

**For async actions** (API calls): launch coroutine:
```kotlin
is MainAction.OnLocationSearchResultClick -> {
    val loc = Location("search").apply { … }
    _state.update { it.copy(currentLocation = loc, zoomTarget = loc, …) }
}
```

**For input with debounce** (search-as-you-type): use a `MutableSharedFlow`:
```kotlin
private val locationSearchQueryFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

// In init block:
viewModelScope.launch {
    locationSearchQueryFlow
        .debounce(500)           // wait for user to stop typing
        .filter { it.length >= 2 }  // don't search single chars
        .distinctUntilChanged()  // don't re-search same query
        .collectLatest { query -> searchRepository.search(query) }
}

// In onAction():
is MainAction.OnLocationSearchQueryChange -> {
    _state.update { it.copy(locationSearchQuery = action.query) }
    if (action.query.length >= 2) locationSearchQueryFlow.tryEmit(action.query)
    else _state.update { it.copy(locationSearchResults = emptyList()) }
}
```

### Step 5 — Build the UI (MainScreen.kt)

Add the composable elements inside the `Box` layout.

**Positioning with `Modifier.align()`:**
- `TopStart`, `TopCenter`, `TopEnd` — headers, search bars
- `BottomCenter` — bottom sheets, buttons
- `Center` — loading states

**Common Compose patterns used:**
- `OutlinedTextField` with `leadingIcon`/`trailingIcon` for search bars
- `Card` with `clickable` for result items
- `LinearProgressIndicator` for loading state
- `AnimatedVisibility` + `slideInVertically` for panels (history)

### Step 6 — Verify

```bash
./gradlew assembleDebug        # compiles?
./gradlew testDebugUnitTest    # existing tests still pass?
```

