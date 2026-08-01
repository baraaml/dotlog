# Implementation Plan - Fix App Startup Crash (MainViewModel Constructor)

The app is crashing at startup with a `NoSuchMethodException` when trying to instantiate `MainViewModel`. This is because `AndroidViewModelFactory` expects a constructor that takes a single `Application` parameter, but since `MainViewModel` uses Kotlin default parameters without `@JvmOverloads`, it only exposes a 6-argument constructor to the JVM reflection used by the factory.

## Proposed Changes

### UI Layer

#### [MODIFY] [MainViewModel.kt](file:///home/baraa/AndroidStudioProjects/dotlog/app/src/main/java/com/example/dotlog/ui/MainViewModel.kt)
- Add `@JvmOverloads` annotation to the `MainViewModel` constructor.
- This will generate the necessary overloaded constructors, including the one taking only `Application`, allowing `ViewModelProvider` to successfully instantiate it.

## Verification Plan

### Automated Tests
- Run unit tests to ensure no regressions in ViewModel logic:
  - `./gradlew testDebugUnitTest`

### Manual Verification
- Rebuild the app and ensure it no longer crashes on startup.
- Note: This fix addresses the specific `NoSuchMethodException` reported in the logs.
