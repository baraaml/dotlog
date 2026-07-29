# PLAN.md — Google Maps Migration (Execution Plan)

Replaces osmdroid with Google Maps Compose. Disposable — delete once merged.
Lasting conventions belong in `AGENTS.md`.

---

## Step 1 — Add Google Maps dependencies + API key

- Add `play-services-maps` and `maps-compose` to `app/build.gradle.kts`
- Remove `osmdroid` dependency and its TOML entry
- Generate a Maps SDK API key (Google Cloud Console → Credentials → API Key)
- Add `<meta-data android:name="com.google.android.geo.API_KEY" ...>` to `AndroidManifest.xml`
- Enable "Maps SDK for Android" in Cloud Console

**Verify:** `./gradlew assembleDebug` succeeds, no manifest merger errors, no osmdroid references remain.
**Model:** safe for a weaker model — mechanical dependency swap.

---

## Step 2 — Replace `MapViewCompose.kt`

- Swap `AndroidView` wrapping `MapView` for `GoogleMap` composable from `maps-compose`
- Replace `Marker` + `FolderOverlay` with `Marker` composables
- Replace manual `controller.animateTo()` with `CameraPositionState.animate()`
- Use `MapProperties(isMyLocationEnabled = ...)` for the blue dot (remove custom current-location marker)
- Keep history markers as `Marker` composables gated by `showHistory`
- Keep `zoomTarget` LaunchedEffect for tap-to-zoom from list

**Verify:** app launches, map renders without "Access denied", blue dot appears when location is available.
**Model:** safe for a weaker model for boilerplate Composables; review camera-state logic yourself (LaunchedEffect + `animate()` timing is subtle).

---

## Step 3 — Clean up osmdroid initialization

- Remove `Configuration.getInstance()` setup from `DotlogApplication.onCreate()`
- Remove `osmdroidBasePath` / `osmdroidTileCache` / `userAgentValue` code
- Delete osmdroid SharedPreferences if any (not strictly required, but clean)
- Verify no `org.osmdroid` imports remain anywhere in the project

**Verify:** global text search for "osmdroid" returns zero results; `./gradlew assembleDebug` still clean.
**Model:** safe for a weaker model — pure deletion.

---

## Step 4 — Test map interactions

- Confirm live tracking pans map to current location
- Confirm history toggle shows/hides past visit markers
- Confirm tapping a visit in the list zooms camera to 18f
- Confirm map gestures (pinch zoom, pan) work smoothly
- Confirm no memory leak on configuration change (screen rotation)

**Verify:** manual UI walkthrough on a real device or emulator with Play Services.
**Model:** needs your attention — camera-state lifecycle across rotation is the main gotcha.
**Exit condition:** max 3 fix attempts on any camera/jank issue before stepping back to check `rememberCameraPositionState` usage.

---

## Step 5 — Full regression pass

- Fresh install → permissions → start tracking → background → foreground → stop tracking
- Confirm `onStop()` visit write still fires (not affected by map swap, but verify)
- Confirm foreground service notification still works
- Confirm POI lookup still populates the card
- Test with no network (map tiles cache vs blank, app doesn't crash)

**Verify:** end-to-end manual walkthrough identical to original Step 7.
**Model:** you, fully — this is the integration review stage.

---

## Notes

- Google Maps free tier: ~28,500 map loads/month. Fine for personal use.
- If you later want to avoid Google entirely, MapLibre is the open-source escape hatch — but that's a separate plan.
- The `MapView` → `GoogleMap` swap is intentionally narrow: don't touch ViewModel, repository, service, or DAO logic unless the compiler forces you to.

## Exit Conditions Summary

- Any step: max 3 fix attempts before reassessing approach (per `AGENTS.md`)
- Step 4: if camera state bugs appear, review `rememberCameraPositionState` docs rather than patching LaunchedEffect timing blindly
