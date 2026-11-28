# QA suite summary
Commit `5cb6009c`, 2026-11-25 03:43 UTC

- Unit tests: 2328 (0 failing)
- Backend tests: 222 (0 failing)
- Coverage gate: {'acs_claimed': 495, 'acs_missing': 0, 'ticked_tasks': 299}
- Device stages: 98/102 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | pass | debug apk |
| field-log | install-test:field-log | pass | androidTest apk |
| field-log | backend:field-log | pass | GET /health answered |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | seed:field-log | pass | regression scenario seeded |
| field-log | instrumented:field-log | pass | 27 tests |
| field-log | flow:F01-welcome | pass | ok |
| field-log | flow:F02-invite-join | pass | ok |
| field-log | flow:F03-jobs | pass | ok |
| field-log | flow:F04-clock-in | pass | ok |
| field-log | flow:F04b-location-allow | pass | ok |
| field-log | flow:F04c-today-clock-in | pass | ok |
| field-log | flow:F06-timesheets | pass | ok |
| field-log | flow:F07-files | pass | ok |
| field-log | flow:F07b-storage | pass | ok |
| field-log | flow:F07c-markup | pass | ok |
| field-log | flow:F08-capture | pass | ok |
| field-log | flow:F08b-capture-photo | pass | ok |
| field-log | flow:F09-daily-log | pass | ok |
| field-log | flow:F09b-log-send | fail | see maestro/F09b-log-send.log |
| field-log | flow:F10-house-3d | pass | ok |
| field-log | flow:F10b-behind-wall | pass | ok |
| field-log | flow:F10c-installed-item | pass | ok |
| field-log | flow:F10d-handoff-export | pass | ok |
| field-log | flow:F10e-handoff-send | pass | ok |
| field-log | flow:F11-tasks | pass | ok |
| field-log | flow:F11b-task-pin | pass | ok |
| field-log | flow:F11c-file-pin | pass | ok |
| field-log | flow:F12-change-order | pass | ok |
| field-log | flow:F13-schedule | pass | ok |
| field-log | flow:F14-materials | pass | ok |
| field-log | flow:F15-reports | pass | ok |
| field-log | flow:F16-offline | pass | ok |
| field-log | flow:F17-trial-expiry | pass | ok |
| field-log | flow:F18-more | pass | ok |
| field-log | flow:x-seeded-company | pass | ok |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | pass | tour ok, shots landscape |
| field-log | monkey:field-log | pass | 1500 events, no crash; foreground after: com.google.android.googlequicksearchbox |
| field-log | jank:field-log | fail | 15% of 120 frames ran the UI thread past the frame budget (AC-U11; software renderer, overall 90% janky of 151; 200-row list, benchmark build) |
| field-log | release:field-log | pass | R8 build launches (1601 ms) |
| field-log | coldstart:field-log | pass | median 810 ms (release, profile compiled) |
| field-log | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | install:field-log | pass | debug apk |
| handoff | install-test:field-log | pass | androidTest apk |
| handoff | install:house-manual | pass | debug apk |
| handoff | install-test:house-manual | pass | androidTest apk |
| handoff | backend:handoff | pass | GET /health answered |
| handoff | permissions | pass | granted where declared |
| handoff | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| handoff | seed:field-log | pass | regression scenario seeded |
| handoff | seed:house-manual | pass | regression scenario seeded |
| handoff | flow:X02-claim-link | pass | ok |
| handoff | variants | skip | no _tour.yaml for handoff |
| handoff | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| house-manual | install:house-manual | pass | debug apk |
| house-manual | install-test:house-manual | pass | androidTest apk |
| house-manual | backend:house-manual | pass | GET /health answered |
| house-manual | permissions | pass | granted where declared |
| house-manual | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| house-manual | seed:house-manual | pass | regression scenario seeded |
| house-manual | instrumented:house-manual | pass | 22 tests |
| house-manual | flow:H01-onboarding | pass | ok |
| house-manual | flow:H01b-home | pass | ok |
| house-manual | flow:H02-draw-plan | pass | ok |
| house-manual | flow:H02b-shape-doors-stairs | pass | ok |
| house-manual | flow:H03-blueprint-room | pass | ok |
| house-manual | flow:H04-add-item | pass | ok |
| house-manual | flow:H05-add-items | pass | ok |
| house-manual | flow:H06-items | pass | ok |
| house-manual | flow:H07-item-page | pass | ok |
| house-manual | flow:H08-offline-item | pass | ok |
| house-manual | flow:H09-wall-photo | pass | ok |
| house-manual | flow:H10-3d | fail | see maestro/H10-3d.log |
| house-manual | flow:H11-overlay-pipe | pass | ok |
| house-manual | flow:H12-breaker-panel | pass | ok |
| house-manual | flow:H13-emergency | pass | ok |
| house-manual | flow:H14-reminders | pass | ok |
| house-manual | flow:H15-home-report | pass | ok |
| house-manual | flow:H16-backup-restore | pass | ok |
| house-manual | flow:H17-sign-in | pass | ok |
| house-manual | flow:H17b-second-phone | pass | ok |
| house-manual | flow:H18-import-handoff | pass | ok |
| house-manual | flow:H18b-claim-link | pass | ok |
| house-manual | flow:H19-paywall | pass | ok |
| house-manual | flow:x-contacts | pass | ok |
| house-manual | flow:x-documents | pass | ok |
| house-manual | flow:x-more | pass | ok |
| house-manual | flow:x-share-viewer | pass | ok |
| house-manual | flow:x-share | pass | ok |
| house-manual | 3d:house-manual | pass | 3d/orbit.mp4; first frame 7789 ms; 4.9 fps; GLES: Google (Google Inc.), Android Emulator OpenGL ES Translator (Google SwiftShader) |
| house-manual | variant:dark | pass | tour ok |
| house-manual | variant:font200 | pass | tour ok |
| house-manual | variant:landscape | pass | tour ok, shots landscape |
| house-manual | monkey:house-manual | pass | 1500 events, no crash; foreground after: com.google.android.apps.nexuslauncher |
| house-manual | jank:house-manual | pass | 2% of 120 frames ran the UI thread past the frame budget (software renderer, overall 72% janky of 187; 200-row list, benchmark build) |
| house-manual | release:house-manual | pass | R8 build launches (978 ms) |
| house-manual | coldstart:house-manual | pass | median 596 ms (release, profile compiled) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Major] Flow F09b-log-send fails on field-log (regression of a fixed item) [auto:flow:field-log:F09b-log-send] - steps: automated, see qa-results branch latest/ for commit 5cb6009c - expected: walkthrough step F09b passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F09b-log-send.log - screenshot: qa-results:latest/field-log/screenshots/flow-F09b-log-send-F09b-log-link-sheet.png
- [ ] [Major] field-log scroll jank (regression of a fixed item) [auto:jank:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit 5cb6009c - expected: under 10% janky frames (AC-U11) - actual: 15% of 120 frames ran the UI thread past the frame budget (AC-U11; software renderer, overall 90% janky of 151; 200-row list, benchmark build)
- [ ] [Major] Flow H10-3d fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H10-3d] - steps: automated, see qa-results branch latest/ for commit 5cb6009c - expected: walkthrough step H10 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H10-3d.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H10-3d-H10-kitchen-3d.png

## REVIEW.md entry

## QA auto 2026-11-25 (commit 5cb6009c)

Automated suite run at 2026-11-25 03:43 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] Flow F09b-log-send fails on field-log (regression of a fixed item) [auto:flow:field-log:F09b-log-send] - steps: automated, see qa-results branch latest/ for commit 5cb6009c - expected: walkthrough step F09b passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F09b-log-send.log - screenshot: qa-results:latest/field-log/screenshots/flow-F09b-log-send-F09b-log-link-sheet.png
- [ ] [Major] field-log scroll jank (regression of a fixed item) [auto:jank:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit 5cb6009c - expected: under 10% janky frames (AC-U11) - actual: 15% of 120 frames ran the UI thread past the frame budget (AC-U11; software renderer, overall 90% janky of 151; 200-row list, benchmark build)
- [ ] [Major] Flow H10-3d fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H10-3d] - steps: automated, see qa-results branch latest/ for commit 5cb6009c - expected: walkthrough step H10 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H10-3d.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H10-3d-H10-kitchen-3d.png

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Pass, field-log F03 Pass, field-log F04 Pass, field-log F04b Pass, field-log F04c Pass, field-log F06 Pass, field-log F07 Pass, field-log F07b Pass, field-log F07c Pass, field-log F08 Pass, field-log F08b Pass, field-log F09 Pass, field-log F09b Fail, field-log F10 Pass, field-log F10b Pass, field-log F10c Pass, field-log F10d Pass, field-log F10e Pass, field-log F11 Pass, field-log F11b Pass, field-log F11c Pass, field-log F12 Pass, field-log F13 Pass, field-log F14 Pass, field-log F15 Pass, field-log F16 Pass, field-log F17 Pass, field-log F18 Pass, house-manual H01 Pass, house-manual H01b Pass, house-manual H02 Pass, house-manual H02b Pass, house-manual H03 Pass, house-manual H04 Pass, house-manual H05 Pass, house-manual H06 Pass, house-manual H07 Pass, house-manual H08 Pass, house-manual H09 Pass, house-manual H10 Fail, house-manual H11 Pass, house-manual H12 Pass, house-manual H13 Pass, house-manual H14 Pass, house-manual H15 Pass, house-manual H16 Pass, house-manual H17 Pass, house-manual H17b Pass, house-manual H18 Pass, house-manual H18b Pass, house-manual H19 Pass

Not built yet or skipped: handoff variants (no _tour.yaml for handoff)

Tested: 102 device stages, 2328 unit tests, 222 backend tests, coverage 495/495 ACs. Passed: 98. New issues: 3. Critical: 0. Product gaps: n/a (judge entry follows).
