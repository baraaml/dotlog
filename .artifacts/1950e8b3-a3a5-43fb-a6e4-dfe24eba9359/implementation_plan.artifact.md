# Implementation Plan - OSM Tile Usage Policy Compliance

Review and update the application to strictly follow the [OpenStreetMap Tile Usage Policy](https://operations.osmfoundation.org/policies/tiles/).

## User Review Required

> [!NOTE]
> I will be updating the User-Agent string to include a placeholder contact email. You should replace `developer@example.com` with your real contact email later if you publish this app.

## Proposed Changes

### [Component] Application Configuration

#### [MODIFY] [DotlogApplication.kt](file:///home/baraa/AndroidStudioProjects/dotlog/app/src/main/java/com/example/dotlog/DotlogApplication.kt)
- Update `userAgentValue` to include a contact point as recommended by the policy.
- Ensure caching configuration remains within the internal `cacheDir`.

### [Component] UI - Map Layer

#### [MODIFY] [MapViewCompose.kt](file:///home/baraa/AndroidStudioProjects/dotlog/app/src/main/java/com/example/dotlog/ui/MapViewCompose.kt)
- Add `CopyrightOverlay` to the `MapView`.
- Modify the `AndroidView` `update` block to preserve the `CopyrightOverlay` instead of calling `view.overlays.clear()`.

### [Component] UI - Info Panel

#### [MODIFY] [VisitsList.kt](file:///home/baraa/AndroidStudioProjects/dotlog/app/src/main/java/com/example/dotlog/ui/VisitsList.kt)
- Add a "Report a map issue" link/button at the bottom of the list, pointing to `https://www.openstreetmap.org/fixthemap`.

## Verification Plan

### Automated Tests
- Run unit tests to ensure no regressions in visit logging.
- `./gradlew testDebugUnitTest`

### Manual Verification
- **User-Agent:** Verify the updated User-Agent is being sent (can be checked via Logcat or a network interceptor if available).
- **Attribution:** Verify that the "© OpenStreetMap contributors" text is clearly visible on the map.
- **Reporting Link:** Verify that the "Report map issue" link appears and opens the browser to the correct URL.
