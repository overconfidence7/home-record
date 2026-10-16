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

- [x] [Major] (severity raised by Nate 2026-10-16: a white window in dark mode is a release blocker) Screens lack background Surface causing visible text bleedthrough across navigation transitions and white window background in dark mode - steps: 1. Launch app and navigate between screens (e.g., Onboarding to Panel Setup, or Search result to Panel). 2. Switch system UI to dark mode (`cmd uimode night yes`). - expected: Root composable or Window content wrapped in `Surface(color = MaterialTheme.colorScheme.background)` so screens are opaque and dark mode uses dark background / actual: Root layouts are transparent Columns, causing previous screen text to bleed through during transitions and dark mode to render dark cards on a bright white window background - screenshot: qa/screenshots/ac5_panel_setup.png - fixed in THIS
- [ ] [Major] (severity raised by Nate 2026-10-16; AC-9 was rewritten the same day — build to the new text: ONE cell spanning both rows, showing both slot numbers "2 · 4" and a "2-pole · 240 V" marker, one label, one amps, tapping anywhere opens the breaker) Two-pole breaker grid cell rendered as two separate cards instead of one tall cell - steps: 1. In Panel, tap Slot 2. 2. Set Poles to 2 (occupying slots 2 and 4), label "Range", 50A, and save. 3. Inspect panel grid. - expected: SPEC.md AC-9: "A two-pole breaker occupies slots n and n+2; the grid draws it as one tall cell" / actual: Rendered as two separate slot cards (slot 2: "2 · Range · 50A", slot 4: "4 · Two-pole (with slot 2) · Range") - screenshot: qa/screenshots/ac9_twopole_grid.png

