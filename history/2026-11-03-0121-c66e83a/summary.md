# QA suite summary
Commit `c66e83a2`, 2026-11-03 01:21 UTC

- Unit tests: 1454 (0 failing)
- Backend tests: 161 (0 failing)
- Coverage gate: {'acs_claimed': 320, 'acs_missing': 0, 'ticked_tasks': 179}
- Device stages: 51/57 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | pass | debug apk |
| field-log | install-test:field-log | pass | androidTest apk |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | seed:field-log | pass | regression scenario seeded |
| field-log | instrumented:field-log | pass | 26 tests |
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
| field-log | flow:F10-house-3d | fail | see maestro/F10-house-3d.log |
| field-log | flow:F11-tasks | fail | see maestro/F11-tasks.log |
| field-log | flow:F11b-task-pin | pass | ok |
| field-log | flow:F11c-file-pin | pass | ok |
| field-log | flow:F15-reports | pass | ok |
| field-log | flow:F16-offline | pass | ok |
| field-log | flow:x-seeded-company | pass | ok |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | pass | tour ok |
| house-manual | install:house-manual | pass | debug apk |
| house-manual | install-test:house-manual | pass | androidTest apk |
| house-manual | permissions | pass | granted where declared |
| house-manual | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| house-manual | seed:house-manual | pass | regression scenario seeded |
| house-manual | instrumented:house-manual | fail | 1 failing of 20 |
| house-manual | flow:H01-onboarding | pass | ok |
| house-manual | flow:H01b-home | pass | ok |
| house-manual | flow:H02-draw-plan | pass | ok |
| house-manual | flow:H02b-shape-doors-stairs | pass | ok |
| house-manual | flow:H03-blueprint-room | pass | ok |
| house-manual | flow:H04-add-item | pass | ok |
| house-manual | flow:H06-items | pass | ok |
| house-manual | flow:H09-wall-photo | pass | ok |
| house-manual | flow:H11-overlay-pipe | fail | see maestro/H11-overlay-pipe.log |
| house-manual | flow:H17-sign-in | pass | ok |
| house-manual | flow:H19-paywall | pass | ok |
| house-manual | flow:x-contacts | pass | ok |
| house-manual | flow:x-documents | pass | ok |
| house-manual | variant:dark | pass | tour ok |
| house-manual | variant:font200 | pass | tour ok |
| house-manual | variant:landscape | pass | tour ok |
| house-manual | monkey:house-manual | pass | 1500 events, no crash |
| house-manual | jank:house-manual | skip | not measurable: 97 frames rendered while flinging the 200-row list (need 100) |
| house-manual | release:house-manual | pass | R8 build launches (1469 ms) |
| house-manual | coldstart:house-manual | pass | median 879 ms (release, profile compiled) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Major] Flow F09b-log-send fails on field-log (regression of a fixed item) [auto:flow:field-log:F09b-log-send] - steps: automated, see qa-results branch latest/ for commit c66e83a2 - expected: walkthrough step F09b passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F09b-log-send.log - screenshot: qa-results:latest/field-log/screenshots/flow-F09b-log-send-F09b-log-pdf.png
- [ ] [Major] Flow F10-house-3d fails on field-log (regression of a fixed item) [auto:flow:field-log:F10-house-3d] - steps: automated, see qa-results branch latest/ for commit c66e83a2 - expected: walkthrough step F10 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F10-house-3d.log - screenshot: qa-results:latest/field-log/screenshots/flow-F10-house-3d-F10-3d-room.png
- [ ] [Major] Flow F11-tasks fails on field-log (regression of a fixed item) [auto:flow:field-log:F11-tasks] - steps: automated, see qa-results branch latest/ for commit c66e83a2 - expected: walkthrough step F11 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F11-tasks.log - screenshot: qa-results:latest/field-log/screenshots/flow-F11-tasks-step-109-assertCondition-Everything_is_synced.png
- [ ] [Major] Instrumented tests fail on house-manual (regression of a fixed item) [auto:instrumented:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit c66e83a2 - expected: all acceptance tests and the screen crawler pass - actual: 1 failing of 20 (qa-results latest/house-manual/instrumented/house-manual.txt)
- [ ] [Major] Flow H11-overlay-pipe fails on house-manual [auto:flow:house-manual:H11-overlay-pipe] - steps: automated, see qa-results branch latest/ for commit c66e83a2 - expected: walkthrough step H11 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H11-overlay-pipe.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H11-overlay-pipe-H01-home-after-onboarding.png

## REVIEW.md entry

## QA auto 2026-11-03 (commit c66e83a2)

Automated suite run at 2026-11-03 01:21 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] Flow F09b-log-send fails on field-log (regression of a fixed item) [auto:flow:field-log:F09b-log-send] - steps: automated, see qa-results branch latest/ for commit c66e83a2 - expected: walkthrough step F09b passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F09b-log-send.log - screenshot: qa-results:latest/field-log/screenshots/flow-F09b-log-send-F09b-log-pdf.png
- [ ] [Major] Flow F10-house-3d fails on field-log (regression of a fixed item) [auto:flow:field-log:F10-house-3d] - steps: automated, see qa-results branch latest/ for commit c66e83a2 - expected: walkthrough step F10 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F10-house-3d.log - screenshot: qa-results:latest/field-log/screenshots/flow-F10-house-3d-F10-3d-room.png
- [ ] [Major] Flow F11-tasks fails on field-log (regression of a fixed item) [auto:flow:field-log:F11-tasks] - steps: automated, see qa-results branch latest/ for commit c66e83a2 - expected: walkthrough step F11 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F11-tasks.log - screenshot: qa-results:latest/field-log/screenshots/flow-F11-tasks-step-109-assertCondition-Everything_is_synced.png
- [ ] [Major] Instrumented tests fail on house-manual (regression of a fixed item) [auto:instrumented:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit c66e83a2 - expected: all acceptance tests and the screen crawler pass - actual: 1 failing of 20 (qa-results latest/house-manual/instrumented/house-manual.txt)
- [ ] [Major] Flow H11-overlay-pipe fails on house-manual [auto:flow:house-manual:H11-overlay-pipe] - steps: automated, see qa-results branch latest/ for commit c66e83a2 - expected: walkthrough step H11 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H11-overlay-pipe.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H11-overlay-pipe-H01-home-after-onboarding.png

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Pass, field-log F03 Pass, field-log F04 Pass, field-log F04b Pass, field-log F04c Pass, field-log F06 Pass, field-log F07 Pass, field-log F07b Pass, field-log F07c Pass, field-log F08 Pass, field-log F08b Pass, field-log F09 Pass, field-log F09b Fail, field-log F10 Fail, field-log F11 Fail, field-log F11b Pass, field-log F11c Pass, field-log F15 Pass, field-log F16 Pass, house-manual H01 Pass, house-manual H01b Pass, house-manual H02 Pass, house-manual H02b Pass, house-manual H03 Pass, house-manual H04 Pass, house-manual H06 Pass, house-manual H09 Pass, house-manual H11 Fail, house-manual H17 Pass, house-manual H19 Pass

Not built yet or skipped: handoff flows (no flows in qa/flows/handoff yet (PLAN Q004); handoff job not run); house-manual jank:house-manual (not measurable: 97 frames rendered while flinging the 200-row list (need 100))

Tested: 57 device stages, 1454 unit tests, 161 backend tests, coverage 320/320 ACs. Passed: 51. New issues: 5. Critical: 0. Product gaps: n/a (judge entry follows).
