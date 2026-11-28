# QA suite summary
Commit `26da2dea`, 2026-11-02 14:58 UTC

- Unit tests: 1343 (0 failing)
- Backend tests: 161 (0 failing)
- Coverage gate: {'acs_claimed': 295, 'acs_missing': 0, 'ticked_tasks': 168}
- Device stages: 55/61 passed

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
| field-log | flow:F09b-log-send | pass | ok |
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
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | jank:field-log | pass | 8% of 106 frames ran the UI thread past the frame budget (software renderer, overall 87% janky of 102; 200-row list, benchmark build) |
| field-log | release:field-log | pass | R8 build launches (1827 ms) |
| field-log | coldstart:field-log | pass | median 755 ms (release, profile compiled) |
| field-log | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| house-manual | install:house-manual | pass | debug apk |
| house-manual | install-test:house-manual | pass | androidTest apk |
| house-manual | permissions | pass | granted where declared |
| house-manual | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| house-manual | seed:house-manual | pass | regression scenario seeded |
| house-manual | instrumented:house-manual | pass | 20 tests |
| house-manual | flow:H01-onboarding | pass | ok |
| house-manual | flow:H01b-home | pass | ok |
| house-manual | flow:H02-draw-plan | pass | ok |
| house-manual | flow:H02b-shape-doors-stairs | pass | ok |
| house-manual | flow:H03-blueprint-room | fail | see maestro/H03-blueprint-room.log |
| house-manual | flow:H04-add-item | pass | ok |
| house-manual | flow:H06-items | pass | ok |
| house-manual | flow:H09-wall-photo | pass | ok |
| house-manual | flow:H17-sign-in | pass | ok |
| house-manual | flow:H19-paywall | pass | ok |
| house-manual | flow:x-contacts | pass | ok |
| house-manual | flow:x-documents | pass | ok |
| house-manual | variant:dark | pass | tour ok |
| house-manual | variant:font200 | pass | tour ok |
| house-manual | variant:landscape | pass | tour ok |
| house-manual | monkey:house-manual | pass | 1500 events, no crash |
| house-manual | jank:house-manual | skip | not measurable: adb: device offline |
| house-manual | release:house-manual | fail | adb: device 'emulator-5554' not found |
| house-manual | coldstart:house-manual | fail | release build could not be measured (install failed) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Major] Flow F10-house-3d fails on field-log [auto:flow:field-log:F10-house-3d] - steps: automated, see qa-results branch latest/ for commit 26da2dea - expected: walkthrough step F10 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F10-house-3d.log - screenshot: qa-results:latest/field-log/screenshots/flow-F10-house-3d-step-060-assertCondition-fl_3d_scene.png
- [ ] [Major] Flow F11-tasks fails on field-log (regression of a fixed item) [auto:flow:field-log:F11-tasks] - steps: automated, see qa-results branch latest/ for commit 26da2dea - expected: walkthrough step F11 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F11-tasks.log - screenshot: qa-results:latest/field-log/screenshots/flow-F11-tasks-step-072-assertCondition-Done_Set_the_kitchen_out.png
- [ ] [Major] Flow H03-blueprint-room fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H03-blueprint-room] - steps: automated, see qa-results branch latest/ for commit 26da2dea - expected: walkthrough step H03 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H03-blueprint-room.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H03-blueprint-room-H01-home-after-onboarding.png
- [ ] [Major] house-manual release (R8) build problem [auto:release:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit 26da2dea - expected: release APK installs and launches (S8) - actual: adb: device 'emulator-5554' not found
- [ ] [Major] house-manual cold start too slow (regression of a fixed item) [auto:coldstart:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit 26da2dea - expected: under 1,500 ms (AC-U11) - actual: release build could not be measured (install failed)

## REVIEW.md entry

## QA auto 2026-11-02 (commit 26da2dea)

Automated suite run at 2026-11-02 14:58 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] Flow F10-house-3d fails on field-log [auto:flow:field-log:F10-house-3d] - steps: automated, see qa-results branch latest/ for commit 26da2dea - expected: walkthrough step F10 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F10-house-3d.log - screenshot: qa-results:latest/field-log/screenshots/flow-F10-house-3d-step-060-assertCondition-fl_3d_scene.png
- [ ] [Major] Flow F11-tasks fails on field-log (regression of a fixed item) [auto:flow:field-log:F11-tasks] - steps: automated, see qa-results branch latest/ for commit 26da2dea - expected: walkthrough step F11 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F11-tasks.log - screenshot: qa-results:latest/field-log/screenshots/flow-F11-tasks-step-072-assertCondition-Done_Set_the_kitchen_out.png
- [ ] [Major] Flow H03-blueprint-room fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H03-blueprint-room] - steps: automated, see qa-results branch latest/ for commit 26da2dea - expected: walkthrough step H03 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H03-blueprint-room.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H03-blueprint-room-H01-home-after-onboarding.png
- [ ] [Major] house-manual release (R8) build problem [auto:release:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit 26da2dea - expected: release APK installs and launches (S8) - actual: adb: device 'emulator-5554' not found
- [ ] [Major] house-manual cold start too slow (regression of a fixed item) [auto:coldstart:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit 26da2dea - expected: under 1,500 ms (AC-U11) - actual: release build could not be measured (install failed)

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Pass, field-log F03 Pass, field-log F04 Pass, field-log F04b Pass, field-log F04c Pass, field-log F06 Pass, field-log F07 Pass, field-log F07b Pass, field-log F07c Pass, field-log F08 Pass, field-log F08b Pass, field-log F09 Pass, field-log F09b Pass, field-log F10 Fail, field-log F11 Fail, field-log F11b Pass, field-log F11c Pass, field-log F15 Pass, field-log F16 Pass, house-manual H01 Pass, house-manual H01b Pass, house-manual H02 Pass, house-manual H02b Pass, house-manual H03 Fail, house-manual H04 Pass, house-manual H06 Pass, house-manual H09 Pass, house-manual H17 Pass, house-manual H19 Pass

Not built yet or skipped: handoff flows (no flows in qa/flows/handoff yet (PLAN Q004); handoff job not run); house-manual jank:house-manual (not measurable: adb: device offline)

Tested: 61 device stages, 1343 unit tests, 161 backend tests, coverage 295/295 ACs. Passed: 55. New issues: 5. Critical: 0. Product gaps: n/a (judge entry follows).
