# dotLog

Manual location-logging Android app. Log where you've been with coordinates, place name, and timestamp.

**Architecture:** MVI (not MVVM) — single `MainState` data class, `MainAction` sealed interface, `MainEvent` Channel for one-shot effects. `combine()` merges local state with Room flow.

**Stack:** Jetpack Compose, Room, Retrofit (Overpass + Nominatim), osmdroid, Haversine dedup (100m radius).

## Key features

- **Log visit** — taps a button to save current GPS location + resolved place name
- **Long-press on map** — logs a custom location with editable name + date/time picker
- **History** — list of all visits with search filter, tap-to-zoom, long-press to edit/delete
- **Location search** — Nominatim-based search bar with debounce, recent searches, scrim dismiss
- **Export/Import** — CSV with proper RFC 4180 quoting, share via `ACTION_SEND`
- **Dark mode** — persisted in SharedPreferences
- **Haptic feedback** — on log button

## Data layer

- `Visit` — Room entity (lat, lon, placeName, timestamp)
- `VisitRepository` — insert with 100m Haversine dedup against latest visit
- `LocationRepository` — `callbackFlow` for GPS, `CancellationTokenSource` fallback
- `PoiRepository` — Overpass API resolves place names from coordinates
- `SearchRepository` — Nominatim search with 1 req/sec rate limiting
- `CsvParser` — RFC 4180 `parseCsvLine()` with escaped double-quote support

## Permissions

Only `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`. No background tracking, no foreground service, no activity recognition.

## Build

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
