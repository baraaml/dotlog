# PLAN.md — Location Tracker (Execution Plan)

Derived from `2026-07-29-location-tracker-design.md`. Disposable — safe to delete
once this feature set is fully shipped. Lasting conventions belong in `AGENTS.md`.

Each step lists: what to build, how to verify it, and whether it's safe to delegate
to a cheaper/weaker model or needs full attention.

---

## Step 1 — Project scaffold + permissions manifest

- New Compose project, `minSdk = 29`
- Declare all manifest permissions (fine/coarse/background location, activity
  recognition, notifications-conditional)
- Add dependencies: Room, Retrofit/OkHttp, osmdroid, Play Services Location,
  Play Services Activity Recognition

**Verify:** project builds and installs on an emulator with no manifest merger errors.
**Model:** safe for a weaker/cheap model — mechanical setup, low judgment required.

---

## Step 2 — Data layer: `Visit` entity + DAO + repository

- Room entity/DAO exactly as specced (id, latitude, longitude, placeName, timestamp)
- `VisitRepository` wrapping the DAO, exposing `Flow<List<Visit>>`
- Haversine distance helper function
- Duplicate-check logic: given a new lat/lng, query most recent visit, compare
  distance, decide insert vs update

**Verify:** unit tests for Haversine distance + duplicate-check logic (in-range,
out-of-range, exactly-at-boundary cases). This is pure logic — no device needed.
**Model:** safe for a weaker model, but review the boundary-condition test cases
yourself — this is exactly the kind of subtle logic that "AI reviewing its own
work in isolation won't catch" per your notes' Stage 4.

---

## Step 3 — Location layer: `LocationRepository` + `ActivityRecognitionClient`

- `LocationRepository` wrapping `FusedLocationProviderClient`, exposing current
  location as a `Flow`/callback
- Activity Recognition transition receiver: on `WALKING`/`IN_VEHICLE` transition,
  trigger a fresh location fetch; on `STILL`, do nothing further
- Runtime permission request flow (fine/coarse/background/activity recognition),
  with rationale UI before each system prompt

**Verify:** manual, on a real device — emulator location/activity-recognition
simulation is unreliable for this. Walk around with the device, confirm location
updates fire on movement and stop firing when stationary.
**Model:** needs your full attention — permission flows and real sensor behavior
are exactly the "device/manual-testing territory" this doc flags as fiddly.
**Exit condition:** max 3 fix attempts on any single permission-flow bug before
stepping back to re-check the permission declarations/manifest rather than
continuing to patch the request code.

---

## Step 4 — Foreground service

- Foreground `Service` with persistent notification, started when app backgrounds
  while tracking is active
- Wires into the location + activity recognition layers from Step 3
- Stops cleanly when tracking is explicitly ended

**Verify:** manual — start tracking, background the app (home button, switch apps),
confirm notification persists and location/visit logging continues; confirm
`onStop()` visit-write still fires correctly when the app is killed outright.
**Model:** needs your full attention — service lifecycle bugs are easy to miss
and hard to unit test.

---

## Step 5 — POI lookup: Overpass integration

- Retrofit interface for Overpass API
- Radius-widening query logic (200m → 500m → 1km → "Unnamed area")
- Wire into `ScreenState.currentPlaceName` (live, not persisted until `onStop()`)

**Verify:** unit test the radius-widening fallback chain with mocked API responses
(empty at 200m/500m, found at 1km; empty at all three). Manual spot-check with
real coordinates for at least one dense-POI area and one sparse area.
**Model:** safe for a weaker model for the Retrofit plumbing; review the fallback
chain logic yourself.

---

## Step 6 — UI: map + info panel + ViewModel wiring

- osmdroid `MapView` in `AndroidView`, blue dot for current location
- History markers (secondary color) when `showHistoryOnMap` is true
- Info panel: current location card, "Last visits" list with pin-icon toggle
- Tap-to-zoom on list items
- `ViewModel` merging location + visits Flow into `ScreenState`

**Verify:** manual UI walkthrough against the original prototype — confirm layout
matches, toggle shows/hides markers, tapping a visit zooms the map correctly.
**Model:** safe for a weaker model for boilerplate Composables; review the
ViewModel state-merging logic yourself (subtle Flow-combination bugs are common).

---

## Step 7 — Full integration pass

- Fresh install, walk through: first launch → permission prompts → live tracking →
  background app → force-stop → reopen → confirm a visit was logged → toggle
  history markers → tap a past visit to zoom
- Test with no network (tiles cached vs not, POI falls back to "Unnamed area")
- Test with permissions denied (inline message + settings link works)

**Verify:** this step *is* the verification — full manual walkthrough end to end.
**Model:** you, fully — this is the review stage from your notes' §4, done by hand
before calling the feature done.

---

## Notes on parallelization

Given this is a single-developer practice project (not a multi-agent orchestration
job), the orchestrator-worker/git-worktree patterns from your notes are probably
overkill here — steps 2, 3, and 5 are independent enough that you *could* run them
in parallel worktrees if you wanted the practice, but there's no real time pressure
forcing that. Sequential is simpler and fine for this scope.

## Exit Conditions Summary

- Any step: max 3 fix attempts before reassessing approach (per `AGENTS.md`)
- Step 3 & 4 (permissions/service): stop and re-verify manifest/permission
  declarations rather than continuing to patch request code past 3 attempts
