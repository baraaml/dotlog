# Location Tracker — Design Spec

**Date:** 2026-07-29
**Type:** Practice project (mentor-assigned, treated as a client engagement)
**Platform:** Android native (Kotlin, Jetpack Compose), no iOS

## Overview

A single-screen Android app that shows the user's live location on a map, resolves
the nearest named point of interest for that location, and logs "visits" (places
the user was when the app stopped/was killed) that can be reviewed and optionally
shown as markers on the map. English-only for MVP.

## Requirements (source of truth)

- Instantly locate the user on open, then reposition live as location changes
- Show past visited locations (list + optional map markers, user-toggleable)
- Movement (not raw polling) triggers location re-checks, for battery efficiency
- Survives backgrounding (app switch) while tracking
- English only for MVP (Arabic support deferred)

## 1. Architecture & Tech Stack

- **Language/UI:** Kotlin, Jetpack Compose, single-screen app (no nav graph needed)
- **Architecture:** MVVM — one `ViewModel` backing the screen, exposing UI state via `StateFlow`
- **Local storage:** Room (single `visits` table)
- **Location:** `FusedLocationProviderClient` (Google Play Services) for GPS fixes
- **Movement trigger:** `ActivityRecognitionClient` — listens for `STILL` ↔ `WALKING`/`IN_VEHICLE`
  transitions; a fresh GPS fix is requested only on a "moving" transition, not on a timer
- **Background survival:** Foreground `Service` with a persistent notification, started when
  the app is backgrounded while tracking is active, stopped when tracking is explicitly ended
- **Map:** osmdroid (OpenStreetMap tiles), Compose-wrapped via `AndroidView`
- **POI lookup:** Overpass API — queries named nodes/ways within a radius, widening
  200m → 500m → 1km, falling back to `"Unnamed area"` if nothing is found
- **Networking:** Retrofit/OkHttp for Overpass API calls
- **minSdk:** 29 (Android 10) — required as the floor since `ACCESS_BACKGROUND_LOCATION`
  and `ACTIVITY_RECOGNITION` only exist as runtime permissions from API 29 onward

## 2. Data Model

Single Room entity, `Visit`:

| Field | Type | Notes |
|---|---|---|
| `id` | Long (PK, autogen) | |
| `latitude` | Double | |
| `longitude` | Double | |
| `placeName` | String | Resolved via Overpass, or `"Unnamed area"` |
| `timestamp` | Long (epoch millis) | Updated in place if this visit is a duplicate |

**Duplicate check:** before inserting, query the most recent `Visit` row. If its coordinates
are within 100m (Haversine distance) of the new location, **update** that row's `timestamp`
and `placeName` instead of inserting a new row. Otherwise, insert a new row.

**Write trigger:** the visit is captured and written in `onStop()` of the screen's lifecycle
owner — this fires when the app is backgrounded or killed, matching the requirement exactly.

## 3. Screen Layout & State

Single Composable screen, two stacked sections:

**Top — Map section** (`osmdroid` `MapView` wrapped in `AndroidView`)
- Blue dot marker at current location, recentered live as new fixes arrive
- When "show history" is toggled on: secondary-colored markers for all saved `Visit` rows
- Tapping a "Last visits" list item animates the map camera to that visit's coordinates

**Bottom — Info panel** (Compose `Column` / `LazyColumn`)
- Current location card: live `placeName` + current time/date (reflects *now*, not a saved visit)
- "Last visits" header row, with a pin-icon toggle button on the right (controls map markers)
- `LazyColumn` of saved visits (place name + date), newest first, tap-to-zoom

**State (`ViewModel` → `StateFlow`):**

```kotlin
data class ScreenState(
    val currentLocation: LatLng?,
    val currentPlaceName: String,   // live POI lookup, not persisted until onStop()
    val visits: List<Visit>,        // from Room, newest first
    val showHistoryOnMap: Boolean   // off by default
)
```

The `ViewModel` observes location updates (via the service/repository) and the Room
`visits` table (via `Flow`), merging both into this single state object for the UI.

## 4. Permissions & Error Handling

**Runtime permissions:**
- `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` — GPS fixes
- `ACCESS_BACKGROUND_LOCATION` — separate prompt on API 29+, required for the foreground
  service to keep locating while backgrounded
- `ACTIVITY_RECOGNITION` — movement-triggered updates, API 29+
- `POST_NOTIFICATIONS` — only requested when `Build.VERSION.SDK_INT >= 33` (API 33+);
  below that, the foreground service notification shows without a runtime prompt

All requested on first launch, each with a brief rationale shown before the system prompt.

**Failure/edge cases:**
- **Permission denied:** persistent inline message on the main screen (not a blocking dialog)
  explaining tracking won't work until granted, with a button to open app settings
- **GPS fix unavailable** (indoors, no signal): keep last known location on the map, show a
  subtle "last updated Xm ago" indicator
- **Overpass API failure/timeout:** fall back to `"Unnamed area"` for that lookup; no retry
  loop needed since lookups are tied to infrequent movement events, not a tight loop
- **No network:** map tiles may fail to load (osmdroid caches previously-seen tiles); location
  tracking and visit logging still work fully offline via local Room storage — only POI name
  resolution needs connectivity

## 5. Testing Strategy

- **Unit tests:** duplicate-visit detection (Haversine distance), POI lookup radius-widening
  fallback chain (200m→500m→1km→"Unnamed area"), `ScreenState` merging logic in the ViewModel
- **Manual/device testing:** real GPS behavior, Activity Recognition transitions, foreground
  service surviving backgrounding/app-switch, permission flows across API 29 vs 33+
- **Fakes/mocks:** fake `LocationRepository` and `VisitDao` so ViewModel logic can be tested
  without real GPS or a real database

Kept lightweight given this is a practice project: solid unit coverage on pure logic
(distance math, fallback chain), manual testing on-device for anything sensor/service-based.

## Deferred / Out of Scope for MVP

- Arabic language support (structure should stay open to adding it later, but no i18n work now)
- Google Maps/Places as an alternative map+POI stack (OSM/Overpass chosen for MVP)
- Configurable duplicate-visit radius or dwell-time-based visit detection
