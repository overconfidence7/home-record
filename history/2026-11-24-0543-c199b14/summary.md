# QA suite summary
Commit `c199b144`, 2026-11-24 05:43 UTC

- Unit tests: 2271 (0 failing)
- Backend tests: 222 (0 failing)
- Coverage gate: {'acs_claimed': 495, 'acs_missing': 0, 'ticked_tasks': 299}
- Device stages: 94/102 passed

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
| field-log | monkey:field-log | pass | 1500 events, no crash; foreground after: com.kurtz.homerecord.fieldlog |
| field-log | jank:field-log | skip | not measurable: 96 frames rendered while flinging the 200-row list (need 100) |
| field-log | release:field-log | pass | R8 build launches (1861 ms) |
| field-log | coldstart:field-log | pass | median 872 ms (release, profile compiled) |
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
| house-manual | flow:H18b-claim-link | pass | ok |
| house-manual | flow:H19-paywall | pass | ok |
| house-manual | flow:x-contacts | pass | ok |
| house-manual | flow:x-documents | pass | ok |
| house-manual | flow:x-more | fail | see maestro/x-more.log |
| house-manual | flow:x-share-viewer | fail | see maestro/x-share-viewer.log |
| house-manual | flow:x-share | fail | see maestro/x-share.log |
| house-manual | 3d:house-manual | pass | 3d/orbit.mp4; first frame 14470 ms; 3.1 fps; GLES: Google (Google Inc.), Android Emulator OpenGL ES Translator (Google SwiftShader) |
| house-manual | variant:dark | pass | tour ok |
| house-manual | variant:font200 | pass | tour ok |
| house-manual | variant:landscape | pass | tour ok, shots landscape |
| house-manual | monkey:house-manual | pass | 1500 events, no crash; foreground after: com.google.android.apps.nexuslauncher |
| house-manual | jank:house-manual | pass | 10% of 110 frames ran the UI thread past the frame budget (software renderer, overall 88% janky of 109; 200-row list, benchmark build) |
| house-manual | release:house-manual | pass | R8 build launches (1824 ms) |
| house-manual | coldstart:house-manual | pass | median 743 ms (release, profile compiled) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Major] Screenshot validation failed: committed reference PNGs differ from the current previews [auto:screenshots:validate] - steps: automated, see qa-results branch latest/ for commit c199b144 - expected: updateDebugScreenshotTest run and PNGs inspected and committed after every UI change (CLAUDE.md) - actual: validateDebugScreenshotTest FAILED; see qa-results jvm/gradle.log
- [ ] [Major] Flow F18-more fails on field-log (regression of a fixed item) [auto:flow:field-log:F18-more] - steps: automated, see qa-results branch latest/ for commit c199b144 - expected: walkthrough step F18 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F18-more.log - screenshot: qa-results:latest/field-log/screenshots/flow-F18-more-F18-clients.png
- [ ] [Major] Flow H12-breaker-panel fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H12-breaker-panel] - steps: automated, see qa-results branch latest/ for commit c199b144 - expected: walkthrough step H12 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H12-breaker-panel.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H12-breaker-panel-H12-breaker-labeled.png
- [ ] [Major] Flow H17b-second-phone fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H17b-second-phone] - steps: automated, see qa-results branch latest/ for commit c199b144 - expected: walkthrough step H17b passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H17b-second-phone.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H17b-second-phone-H17b-home-back.png
- [ ] [Major] Flow x-more fails on house-manual (regression of a fixed item) [auto:flow:house-manual:x-more] - steps: automated, see qa-results branch latest/ for commit c199b144 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/house-manual/maestro/x-more.log - screenshot: qa-results:latest/house-manual/screenshots/flow-x-more-step-027-assertCondition-Cold.png
- [ ] [Major] Flow x-share-viewer fails on house-manual (regression of a fixed item) [auto:flow:house-manual:x-share-viewer] - steps: automated, see qa-results branch latest/ for commit c199b144 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/house-manual/maestro/x-share-viewer.log - screenshot: qa-results:latest/house-manual/screenshots/flow-x-share-viewer-step-067-assertCondition-Furnace.png
- [ ] [Major] Flow x-share fails on house-manual (regression of a fixed item) [auto:flow:house-manual:x-share] - steps: automated, see qa-results branch latest/ for commit c199b144 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/house-manual/maestro/x-share.log - screenshot: qa-results:latest/house-manual/screenshots/flow-x-share-step-071-assertCondition-Furnace.png

## REVIEW.md entry

## QA auto 2026-11-24 (commit c199b144)

Automated suite run at 2026-11-24 05:43 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] Screenshot validation failed: committed reference PNGs differ from the current previews [auto:screenshots:validate] - steps: automated, see qa-results branch latest/ for commit c199b144 - expected: updateDebugScreenshotTest run and PNGs inspected and committed after every UI change (CLAUDE.md) - actual: validateDebugScreenshotTest FAILED; see qa-results jvm/gradle.log
- [ ] [Major] Flow F18-more fails on field-log (regression of a fixed item) [auto:flow:field-log:F18-more] - steps: automated, see qa-results branch latest/ for commit c199b144 - expected: walkthrough step F18 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F18-more.log - screenshot: qa-results:latest/field-log/screenshots/flow-F18-more-F18-clients.png
- [ ] [Major] Flow H12-breaker-panel fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H12-breaker-panel] - steps: automated, see qa-results branch latest/ for commit c199b144 - expected: walkthrough step H12 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H12-breaker-panel.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H12-breaker-panel-H12-breaker-labeled.png
- [ ] [Major] Flow H17b-second-phone fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H17b-second-phone] - steps: automated, see qa-results branch latest/ for commit c199b144 - expected: walkthrough step H17b passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H17b-second-phone.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H17b-second-phone-H17b-home-back.png
- [ ] [Major] Flow x-more fails on house-manual (regression of a fixed item) [auto:flow:house-manual:x-more] - steps: automated, see qa-results branch latest/ for commit c199b144 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/house-manual/maestro/x-more.log - screenshot: qa-results:latest/house-manual/screenshots/flow-x-more-step-027-assertCondition-Cold.png
- [ ] [Major] Flow x-share-viewer fails on house-manual (regression of a fixed item) [auto:flow:house-manual:x-share-viewer] - steps: automated, see qa-results branch latest/ for commit c199b144 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/house-manual/maestro/x-share-viewer.log - screenshot: qa-results:latest/house-manual/screenshots/flow-x-share-viewer-step-067-assertCondition-Furnace.png
- [ ] [Major] Flow x-share fails on house-manual (regression of a fixed item) [auto:flow:house-manual:x-share] - steps: automated, see qa-results branch latest/ for commit c199b144 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/house-manual/maestro/x-share.log - screenshot: qa-results:latest/house-manual/screenshots/flow-x-share-step-071-assertCondition-Furnace.png

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Pass, field-log F03 Pass, field-log F04 Pass, field-log F04b Pass, field-log F04c Pass, field-log F06 Pass, field-log F07 Pass, field-log F07b Pass, field-log F07c Pass, field-log F08 Pass, field-log F08b Pass, field-log F09 Pass, field-log F09b Pass, field-log F10 Pass, field-log F10b Pass, field-log F10c Pass, field-log F10d Pass, field-log F10e Pass, field-log F11 Pass, field-log F11b Pass, field-log F11c Pass, field-log F12 Pass, field-log F13 Pass, field-log F14 Pass, field-log F15 Pass, field-log F16 Pass, field-log F17 Pass, field-log F18 Fail, house-manual H01 Pass, house-manual H01b Pass, house-manual H02 Pass, house-manual H02b Pass, house-manual H03 Pass, house-manual H04 Pass, house-manual H05 Pass, house-manual H06 Pass, house-manual H07 Pass, house-manual H08 Pass, house-manual H09 Pass, house-manual H10 Pass, house-manual H11 Pass, house-manual H12 Fail, house-manual H13 Pass, house-manual H14 Pass, house-manual H15 Pass, house-manual H16 Pass, house-manual H17 Pass, house-manual H17b Fail, house-manual H18 Pass, house-manual H18b Pass, house-manual H19 Pass

Not built yet or skipped: field-log jank:field-log (not measurable: 96 frames rendered while flinging the 200-row list (need 100)); handoff variants (no _tour.yaml for handoff)

Tested: 102 device stages, 2271 unit tests, 222 backend tests, coverage 495/495 ACs. Passed: 94. New issues: 7. Critical: 0. Product gaps: n/a (judge entry follows).
