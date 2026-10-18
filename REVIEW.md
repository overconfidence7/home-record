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
