# Implementation Plan - Fix OSM Tile Block

The user is still seeing "Access Blocked" on the map. This is a strict enforcement of the OpenStreetMap Tile Usage Policy. We need to ensure the `User-Agent` is set correctly and early, and that we are following all technical requirements.

## User Review Required

> [!IMPORTANT]
> I am moving the `osmdroid` configuration to the `Application` class to ensure it's applied before any UI component (like `MapView`) is initialized.

## Proposed Changes

### Configuration

#### [MODIFY] [DotlogApplication.kt](file:///home/baraa/AndroidStudioProjects/dotlog/app/src/main/java/com/example/dotlog/DotlogApplication.kt)
- Initialize `osmdroid` configuration here.
- Set a unique and descriptive `User-Agent`.
- Configure internal cache paths.

#### [MODIFY] [MainActivity.kt](file:///home/baraa/AndroidStudioProjects/dotlog/app/src/main/java/com/example/dotlog/MainActivity.kt)
- Remove the configuration logic from `onCreate` (it's now in the Application class).

### UI

#### [MODIFY] [MapViewCompose.kt](file:///home/baraa/AndroidStudioProjects/dotlog/app/src/main/java/com/example/dotlog/ui/MapViewCompose.kt)
- Ensure the `MapView` uses the global configuration.

## Verification Plan

### Manual Verification
- Clear App Cache and Data.
- Restart the app.
- Check if map tiles load without the "Access Blocked" overlay.
