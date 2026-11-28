# QA suite summary
Commit `86d14c0b`, 2026-11-07 23:03 UTC

- Unit tests: 2261 (0 failing)
- Backend tests: 222 (0 failing)
- Coverage gate: {'acs_claimed': 495, 'acs_missing': 0, 'ticked_tasks': 299}
- Device stages: 91/102 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | pass | debug apk |
| field-log | install-test:field-log | pass | androidTest apk |
| field-log | backend:field-log | pass | GET /health answered |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | seed:field-log | pass | regression scenario seeded |
| field-log | instrumented:field-log | fail | 1 failing of 27 |
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
| field-log | flow:F09b-log-send | pass | ok |
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
| field-log | flow:F18-more | fail | see maestro/F18-more.log |
| field-log | flow:x-seeded-company | pass | ok |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | pass | tour ok, shots landscape |
| field-log | monkey:field-log | pass | 1500 events, no crash; foreground after: com.google.android.apps.nexuslauncher |
| field-log | jank:field-log | pass | 8% of 107 frames ran the UI thread past the frame budget (software renderer, overall 92% janky of 105; 200-row list, benchmark build) |
| field-log | release:field-log | pass | R8 build launches (1893 ms) |
| field-log | coldstart:field-log | pass | median 837 ms (release, profile compiled) |
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
| house-manual | instrumented:house-manual | fail | 1 failing of 22 |
| house-manual | flow:H01-onboarding | pass | ok |
| house-manual | flow:H01b-home | pass | ok |
| house-manual | flow:H02-draw-plan | pass | ok |
| house-manual | flow:H02b-shape-doors-stairs | pass | ok |
| house-manual | flow:H03-blueprint-room | pass | ok |
| house-manual | flow:H04-add-item | pass | ok |
| house-manual | flow:H05-add-items | pass | ok |
| house-manual | flow:H06-items | pass | ok |
| house-manual | flow:H07-item-page | fail | see maestro/H07-item-page.log |
| house-manual | flow:H08-offline-item | pass | ok |
| house-manual | flow:H09-wall-photo | pass | ok |
| house-manual | flow:H10-3d | pass | ok |
| house-manual | flow:H11-overlay-pipe | pass | ok |
| house-manual | flow:H12-breaker-panel | fail | see maestro/H12-breaker-panel.log |
| house-manual | flow:H13-emergency | pass | ok |
| house-manual | flow:H14-reminders | pass | ok |
| house-manual | flow:H15-home-report | pass | ok |
| house-manual | flow:H16-backup-restore | pass | ok |
| house-manual | flow:H17-sign-in | pass | ok |
| house-manual | flow:H17b-second-phone | fail | see maestro/H17b-second-phone.log |
| house-manual | flow:H18-import-handoff | pass | ok |
| house-manual | flow:H18b-claim-link | fail | see maestro/H18b-claim-link.log |
| house-manual | flow:H19-paywall | pass | ok |
| house-manual | flow:x-contacts | pass | ok |
| house-manual | flow:x-documents | pass | ok |
| house-manual | flow:x-more | fail | see maestro/x-more.log |
| house-manual | flow:x-share-viewer | fail | see maestro/x-share-viewer.log |
| house-manual | flow:x-share | fail | see maestro/x-share.log |
| house-manual | 3d:house-manual | pass | 3d/orbit.mp4; first frame 7803 ms; 2.8 fps; GLES: Google (Google Inc.), Android Emulator OpenGL ES Translator (Google SwiftShader) |
| house-manual | variant:dark | pass | tour ok |
| house-manual | variant:font200 | pass | tour ok |
| house-manual | variant:landscape | pass | tour ok, shots landscape |
| house-manual | monkey:house-manual | pass | 1500 events, no crash; foreground after: com.google.android.googlequicksearchbox |
| house-manual | jank:house-manual | pass | 6% of 103 frames ran the UI thread past the frame budget (software renderer, overall 91% janky of 102; 200-row list, benchmark build) |
| house-manual | release:house-manual | pass | R8 build launches (1545 ms) |
| house-manual | coldstart:house-manual | pass | median 710 ms (release, profile compiled) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Major] Instrumented tests fail on field-log (regression of a fixed item) [auto:instrumented:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit 86d14c0b - expected: all acceptance tests and the screen crawler pass - actual: 1 failing of 27 (qa-results latest/field-log/instrumented/field-log.txt)
- [ ] [Major] Instrumented tests fail on house-manual (regression of a fixed item) [auto:instrumented:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit 86d14c0b - expected: all acceptance tests and the screen crawler pass - actual: 1 failing of 22 (qa-results latest/house-manual/instrumented/house-manual.txt)

## REVIEW.md entry

## QA auto 2026-11-07 (commit 86d14c0b)

Automated suite run at 2026-11-07 23:03 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] Instrumented tests fail on field-log (regression of a fixed item) [auto:instrumented:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit 86d14c0b - expected: all acceptance tests and the screen crawler pass - actual: 1 failing of 27 (qa-results latest/field-log/instrumented/field-log.txt)
- [ ] [Major] Instrumented tests fail on house-manual (regression of a fixed item) [auto:instrumented:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit 86d14c0b - expected: all acceptance tests and the screen crawler pass - actual: 1 failing of 22 (qa-results latest/house-manual/instrumented/house-manual.txt)

Still open from earlier runs: [auto:flow:field-log:F18-more], [auto:flow:house-manual:H07-item-page], [auto:flow:house-manual:H12-breaker-panel], [auto:flow:house-manual:H17b-second-phone], [auto:flow:house-manual:H18b-claim-link], [auto:flow:house-manual:x-more], [auto:flow:house-manual:x-share-viewer], [auto:flow:house-manual:x-share]

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Pass, field-log F03 Pass, field-log F04 Pass, field-log F04b Pass, field-log F04c Pass, field-log F06 Pass, field-log F07 Pass, field-log F07b Pass, field-log F07c Pass, field-log F08 Pass, field-log F08b Pass, field-log F09 Pass, field-log F09b Pass, field-log F10 Pass, field-log F10b Pass, field-log F10c Pass, field-log F10d Pass, field-log F10e Pass, field-log F11 Pass, field-log F11b Pass, field-log F11c Pass, field-log F12 Pass, field-log F13 Pass, field-log F14 Pass, field-log F15 Pass, field-log F16 Pass, field-log F17 Pass, field-log F18 Fail, house-manual H01 Pass, house-manual H01b Pass, house-manual H02 Pass, house-manual H02b Pass, house-manual H03 Pass, house-manual H04 Pass, house-manual H05 Pass, house-manual H06 Pass, house-manual H07 Fail, house-manual H08 Pass, house-manual H09 Pass, house-manual H10 Pass, house-manual H11 Pass, house-manual H12 Fail, house-manual H13 Pass, house-manual H14 Pass, house-manual H15 Pass, house-manual H16 Pass, house-manual H17 Pass, house-manual H17b Fail, house-manual H18 Pass, house-manual H18b Fail, house-manual H19 Pass

Not built yet or skipped: handoff variants (no _tour.yaml for handoff)

Tested: 102 device stages, 2261 unit tests, 222 backend tests, coverage 495/495 ACs. Passed: 91. New issues: 2. Critical: 0. Product gaps: n/a (judge entry follows).
