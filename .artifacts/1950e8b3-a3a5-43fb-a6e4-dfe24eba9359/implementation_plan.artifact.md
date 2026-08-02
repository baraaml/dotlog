# Implementation Plan - Simplified Map Interaction

This plan simplifies the UI by removing the dedicated bottom info card and instead using the existing top status card to show details when a map marker is selected. It also removes the default "grey rectangle" (osmdroid InfoWindow).

## Proposed Changes

### [Component] Map UI

#### [MODIFY] [MapViewCompose.kt](file:///home/baraa/AndroidStudioProjects/dotlog/app/src/main/java/com/example/dotlog/ui/MapViewCompose.kt)
- **Disable InfoWindows**: Explicitly disable the default osmdroid popups for all markers by returning `true` in click listeners and ensuring no default info windows are attached.
- **Marker Synchronization**: Ensure the "You are here" marker also respects the no-popup rule.

### [Component] Main Screen & Overlays

#### [MODIFY] [MainScreen.kt](file:///home/baraa/AndroidStudioProjects/dotlog/app/src/main/java/com/example/dotlog/ui/MainScreen.kt)
- **Remove `SelectedVisitCard`**: Delete the bottom-sliding card and its animation logic.
- **Enhance Top Status Card**:
    - If `selectedVisit` is null: Show "Live Tracking" and current POI (current behavior).
    - If `selectedVisit` is NOT null: Show the selected place name, the date/time of that visit, and a **"Dismiss" (X) button** to return to live mode.
- **DatePicker Optimization**: Move the `DatePickerState` initialization to be more lifecycle-aware to eliminate the animation hitch.

## Verification Plan

### Automated Tests
- Run existing unit tests: `./gradlew testDebugUnitTest`

### Manual Verification
- **Marker Click**: Tap a marker. Verify NO grey rectangle appears, and the **Top Card** updates with that location's details.
- **Deselection**: Tap the "X" on the top card. Verify it returns to showing "Live Tracking" or "Searching...".
- **Lag Check**: Tap "Change date" in the long-press dialog. Confirm it opens instantly.
