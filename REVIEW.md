# QA review log

QA findings from emulator or device testing, grouped by test session. Each session is a heading `## QA <YYYY-MM-DD> (commit <sha>)`. Each finding is one bullet:

```
- [ ] [Critical|Major|Minor] <problem> - steps: <how to reproduce> - expected: <what SPEC.md says> / actual: <what happened> - screenshot: qa/screenshots/<file>.png
```

Severity: Critical = crash, data loss or an acceptance criterion that fails outright; Major = feature works but wrong or unusable; Minor = cosmetic or wording.

The builder fixes findings (Critical and Major before starting new tasks) and marks them:

```
- [x] [Major] ... - fixed in <sha>
```

Findings that are really spec changes go to BLOCKERS.md under "Decisions to confirm" instead.

---

## QA 2026-10-15 (commit 3c9f751)

Tested: AC-1, AC-2, AC-3, AC-4, rotation, back button, dark mode, long text input. Passed: 4. New issues: 0.

---

## QA 2026-10-16 (commit 0a2226a)

Tested: AC-1 through AC-50 (House Manual M1 complete milestone test pass: Onboarding AC-1..AC-4, Panel & Breakers AC-5..AC-12, Room Confidence & Breaker-Room Linking AC-13..AC-16 & AC-20..AC-21, Unified Search AC-17..AC-19, Shareable Legend PDF AC-22..AC-24, Emergency Shutoffs & Offline Photo Preview AC-25..AC-31, Quick Settings Emergency Tile AC-32..AC-34, Mapping Mode AC-35..AC-41, Equipment Registry & Offline ML Kit OCR AC-42..AC-50), dark mode, landscape rotation, back navigation, unit test suite (`./gradlew testDebugUnitTest`). Passed: 50. New issues: 2.

- [x] [Major] (severity raised by Nate 2026-10-16: a white window in dark mode is a release blocker) Screens lack background Surface causing visible text bleedthrough across navigation transitions and white window background in dark mode - steps: 1. Launch app and navigate between screens (e.g., Onboarding to Panel Setup, or Search result to Panel). 2. Switch system UI to dark mode (`cmd uimode night yes`). - expected: Root composable or Window content wrapped in `Surface(color = MaterialTheme.colorScheme.background)` so screens are opaque and dark mode uses dark background / actual: Root layouts are transparent Columns, causing previous screen text to bleed through during transitions and dark mode to render dark cards on a bright white window background - screenshot: qa/screenshots/ac5_panel_setup.png - fixed in 2d39697
- [x] [Major] (severity raised by Nate 2026-10-16; AC-9 was rewritten the same day — build to the new text: ONE cell spanning both rows, showing both slot numbers "2 · 4" and a "2-pole · 240 V" marker, one label, one amps, tapping anywhere opens the breaker) Two-pole breaker grid cell rendered as two separate cards instead of one tall cell - steps: 1. In Panel, tap Slot 2. 2. Set Poles to 2 (occupying slots 2 and 4), label "Range", 50A, and save. 3. Inspect panel grid. - expected: SPEC.md AC-9: "A two-pole breaker occupies slots n and n+2; the grid draws it as one tall cell" / actual: Rendered as two separate slot cards (slot 2: "2 · Range · 50A", slot 4: "4 · Two-pole (with slot 2) · Range") - screenshot: qa/screenshots/ac9_twopole_grid.png - fixed in c652a9e

---

## QA 2026-10-16 (commit 9015c42)

Tested: AC-51..AC-70 (Field Log M2 milestone pass: Jobs list & archive toggle AC-51..AC-54, CameraX preview & shutter UI AC-55, Photo capture & volume keys AC-56..AC-57, Notes AC-58, Voice memos AC-59, Permission explanations AC-60, Job day grouping & entry editing/playback AC-61, Process kill persistence AC-62, Behind-the-wall rooms & custom walls AC-63..AC-65, Daily report draft & delay picker AC-67..AC-68, Report PDF generation & overwrite AC-69..AC-70, Company details profile), House Manual dark mode fix re-check (`2d39697`), full unit test suite (`./gradlew testDebugUnitTest` — 242/242 passed). Passed: 19. New issues: 1.

- [x] [Major] CameraX hardcodes `DEFAULT_BACK_CAMERA`, failing on emulators/devices with only front camera and blocking in-app photo capture - steps: 1. Launch Field Log on an emulator (or device with only one camera). 2. Open any job and tap "Capture" (or "Behind the wall" -> select room/wall). 3. Tap shutter button or press volume up/down. - expected: CameraX selects available camera (falling back to front camera or available camera if back camera is missing) and captures photo into app storage / actual: CameraX throws `IllegalArgumentException: No available camera can be found` during back camera validation; `imageCapture` is null; shutter taps show "Photo not saved. Try again." - screenshot: qa/screenshots/ac56_shutter_tap.png - fixed in 2936c5c

---

## QA 2026-10-18 (commit ad9498a)

Tested: AC-1..AC-100 regression pass across both apps on emulator (House Manual: AC-9 two-pole spanning tall cell fix verified in c652a9e, Panel Insights & Load Estimate T30b/c, Reminders & seasonal maintenance checklists T31/T32, Multiple Panels & switcher T37, Consumables & equipment specs T38, Encrypted backup export/import T34, Entitlements limit gate & paywall T35/T36; Field Log: Daily report PDF export T25, Handoff .homerecord and PDF export T27, CameraX capture re-tested and still reproduces), `./gradlew testDebugUnitTest` (414/414 passed), `./gradlew lintDebug` (0 errors). Passed: 38. Existing open issues: 1 (CameraX DEFAULT_BACK_CAMERA from 9015c42). New issues: 1.

- [x] [Major] Edge-to-edge status bar insets padding missing across screens on targetSdk 37, causing top headers to overlap with system status bar icons - steps: 1. Launch House Manual or Field Log on Android 15+ (targetSdk 37). 2. Navigate to any screen (Home, Panel, Reminders, Settings, Paywall, Field Log Jobs, Job Details, Handoff). - expected: Top headers and action items render below system status bar insets (e.g. using `Modifier.statusBarsPadding()` or Scaffold inner padding) / actual: Top headers draw at y=42 directly overlapping with system clock, Wi-Fi, battery, and notification icons - screenshot: qa/screenshots/edge_to_edge_status_bar_overlap.png - fixed in fd86092

---

## QA 2026-10-18 (commit 6746054)

Tested: Re-test of builder fixes on emulator (fd86092 edge-to-edge insets, 2936c5c CameraX back-camera hardcode fallback), AC-1..AC-100 regression check across House Manual and Field Log screens (House Manual Home, Panel, Emergency, Reminders, Settings; Field Log Jobs, Job Details, Behind the Wall, Daily Report, Handoff, CameraX capture), unit test suite (`./gradlew testDebugUnitTest` — 417/417 passed), `./gradlew lintDebug` (0 errors). Passed: 38. Fixes verified: 1 (edge-to-edge status bar insets fd86092). Existing open issues: 1 (CameraX capture failure on single-camera devices). New issues: 0.

- [x] [Major] CameraX initialization fails on single-camera devices/emulators (CameraValidator$CameraIdListIncorrectException: Expected camera missing from device), releasing camera and blocking in-app capture - steps: 1. Launch Field Log on an emulator or device with only one camera (e.g. qa_pixel where front camera is absent). 2. Open any job, tap "Capture" (or "Behind the wall"). 3. Tap shutter button. - expected: CameraX initializes with available camera and successfully captures and saves photo / actual: CameraX logs CameraValidator$CameraIdListIncorrectException (LENS_FACING_FRONT verification failed: "No available camera can be found"), releases camera internal state (RELEASING -> RELEASED), and shutter tap shows "Photo not saved. Try again." - screenshot: qa/screenshots/verify_fl_photo_captured.png - fixed in c9220d5

---

## QA 2026-10-19 (commit a86884c)

Tested: Re-test of CameraX fix (89e432a/409a9b1) on single-camera emulator (`qa_pixel` API 37), verification of T40b language pass (~55 updated strings per docs/voice.md across both apps), House Manual feature verification (AC-9 two-pole spanning tall cell, Panel insights T30b, Load estimate sheet T30c, Emergency shutoffs, Reminders & maintenance checklists, Settings & encrypted backup export/import T34/T41), Field Log feature verification (Jobs list, job creation, Behind the Wall empty state and room navigation, Daily report PDF export, Handoff export), full unit test suite (`./gradlew testDebugUnitTest` — 420/420 passed). Passed: 39. Fixes verified: 0 (CameraX fix does not resolve capture on single-camera devices). Open issues: 2 (1 Major re-opened with new root cause, 1 Minor new).

- [x] [Major] CameraX viewfinder is black and photo capture fails on single-camera devices due to uncaught IllegalArgumentException in provider.hasCamera(DEFAULT_FRONT_CAMERA) - steps: 1. Launch Field Log on an emulator or device with only a back camera (e.g. qa_pixel). 2. Open any job and tap "Capture" (or tap "Behind the wall" -> select room/wall). 3. View the preview and tap the shutter button. - expected: CameraX selects the available back camera via CameraChoice.pick, binds preview to the viewfinder, and captures photo to storage when shutter is tapped / actual: Viewfinder is black; tapping shutter shows toast "Couldn't save the photo. Check free space and try again." In CaptureScreen.kt (lines 132-137) and BehindWallScreen.kt, runCatching wraps CameraChoice.pick(provider.hasCamera(DEFAULT_BACK_CAMERA), provider.hasCamera(DEFAULT_FRONT_CAMERA)). Under FieldLogApp's setAvailableCamerasLimiter(DEFAULT_BACK_CAMERA), calling provider.hasCamera(DEFAULT_FRONT_CAMERA) throws IllegalArgumentException ("No available camera can be found"). runCatching catches this exception and returns early before provider.bindToLifecycle and capturer.imageCapture = imageCapture execute, leaving imageCapture null and capturer.capture returning false - screenshot: qa/screenshots/verify_fl_photo_taken.png
- [x] [Minor] Behind the Wall empty state copy directs user to "Tap Edit job to add some" rooms, but Edit Job does not allow adding rooms - steps: 1. In Field Log, create a job without rooms. 2. Open the job and tap "Behind the wall". 3. Observe empty state text, then tap "Edit job". - expected: Copy in wall_no_rooms directs user to an action that can actually add rooms, or JobEditScreen supports adding/editing rooms / actual: wall_no_rooms displays "This job has no rooms yet. Tap Edit job to add some.", but JobEditScreen.kt (line 81) gates rooms behind `if (!state.isEdit)`, meaning rooms can only be specified during initial job creation and cannot be added from Edit Job - screenshot: qa/screenshots/verify_fl_btw_failed.png - fixed in this commit (copy now says rooms are set at job creation)

---

## Lint warnings accepted (S1, 2026-10-17)

`lintDebug` has 0 errors. Fixed: ComposableNaming, 2 UnusedResources, AutoboxingStateCreation. Remaining warnings, each justified:

- GradleDependency / NewerVersionAvailable / AndroidGradlePluginVersion (core): versions are pinned to the approved set in CLAUDE.md; upgrading is Nate's call.
- PluralsCandidate (13, English-only v1): strings like "%d slots" are only shown where the count is formatted by surrounding logic or is always plural/large; converting all risks regressions in tested strings. Revisit if localization is added.
- DataExtractionRules / allowBackup: both apps set `allowBackup="false"`, so no data is extracted; the rules file adds nothing.
- ObsoleteSdkInt (`mipmap-anydpi-v26`): renaming to `mipmap-anydpi` makes AAPT2 fail to find `ic_launcher`; keep the v26 folder.
- InlinedApi `RECEIVER_NOT_EXPORTED`: call is gated by an SDK_INT check; the constant is inlined.
- UnusedAttribute `targetCellWidth/Height` (widget info): intentional for API 31+; ignored on older versions.
- IntentFilterUniqueDataAttributes: `.homerecord` filter needs scheme/mimeType/pathPattern together to match senders (see BLOCKERS T29).
- TypographyDashes in `equipment_date_error`: false positive, "yyyy-MM-dd" is a literal date format.
- UseKtx `String.toUri`: core-ktx is not on the approved list.

## Accepted limits (S5)
- Process death restores input only on the equipment form; breaker/room/shutoff/job forms are short and restore from rotation (retained ViewModel) but would restart empty after the OS kills the process. Acceptable for v1.

## Accepted fixed colors (S6)
- Camera/viewfinder surfaces (Field Log capture, behind-wall header, HM-Emergency photo zoom) stay black with white text on purpose: they sit over a live preview or full-screen photo in either theme.
- Widget (red background, white text) and launcher icons are fixed brand colors by design.
- Confidence dots use green/amber/grey per AC-15 with lighter tones in dark mode (ConfidenceColors.kt); alarm panel uses colorScheme.error/onError. Everything else comes from MaterialTheme (dynamic color on API 31+, static light/dark fallback).

