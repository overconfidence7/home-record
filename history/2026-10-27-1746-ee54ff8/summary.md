# QA suite summary
Commit `ee54ff8`, 2026-10-27 17:46 UTC

- Unit tests: 670 (1 failing)
- Backend tests: 95 (0 failing)
- Coverage gate: {'acs_claimed': 117, 'acs_missing': 0, 'ticked_tasks': 45}
- Device stages: 58/71 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | pass | debug apk |
| field-log | install-test:field-log | pass | androidTest apk |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | coldstart:field-log | fail | median 3149 ms over 1500 ms (AC-U11) |
| field-log | seed:field-log | pass | regression scenario seeded |
| field-log | instrumented:field-log | pass | 2 tests |
| field-log | flow:F01-welcome | pass | ok |
| field-log | flow:F02-invite-join | pass | ok |
| field-log | flow:F03-jobs | pass | ok |
| field-log | flow:F04-clock-in | pass | ok |
| field-log | flow:F04b-location-allow | pass | ok |
| field-log | flow:F05-today | pass | ok |
| field-log | flow:F06-timesheets | pass | ok |
| field-log | flow:F07-reports | pass | ok |
| field-log | flow:F08-capture | pass | ok |
| field-log | flow:F09-files | pass | ok |
| field-log | flow:F09b-storage | pass | ok |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | pass | tour ok |
| field-log | jank:field-log | skip | gfxinfo reported no frames |
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | release:field-log | pass | R8 build launches (2291 ms) |
| field-log | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | install:field-log | pass | debug apk |
| handoff | install-test:field-log | pass | androidTest apk |
| handoff | install:house-manual | pass | debug apk |
| handoff | install-test:house-manual | pass | androidTest apk |
| handoff | permissions | pass | granted where declared |
| handoff | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| handoff | coldstart:field-log | fail | median 3719 ms over 1500 ms (AC-U11) |
| handoff | coldstart:house-manual | fail | median 2007 ms over 1500 ms (AC-U11) |
| handoff | seed:field-log | pass | regression scenario seeded |
| handoff | seed:house-manual | pass | regression scenario seeded |
| handoff | instrumented:field-log | pass | 2 tests |
| handoff | instrumented:house-manual | pass | 20 tests |
| handoff | flows | skip | no flows in qa/flows/handoff yet (PLAN Q004) |
| handoff | variants | skip | no _tour.yaml for handoff |
| handoff | jank:field-log | fail | 100% janky frames of 5 (AC-U11) |
| handoff | jank:house-manual | fail | 87% janky frames of 8 (AC-U11) |
| handoff | monkey:field-log | pass | 1500 events, no crash |
| handoff | monkey:house-manual | pass | 1500 events, no crash |
| handoff | release:field-log | pass | R8 build launches (2801 ms) |
| handoff | release:house-manual | pass | R8 build launches (2062 ms) |
| handoff | logcat:com.kurtz.homerecord.fieldlog | fail | 1 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | logcat:com.kurtz.homerecord.housemanual | fail | 1 crashes, 0 ANRs, 0 StrictMode violations |
| house-manual | install:house-manual | pass | debug apk |
| house-manual | install-test:house-manual | pass | androidTest apk |
| house-manual | permissions | pass | granted where declared |
| house-manual | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| house-manual | coldstart:house-manual | fail | median 3968 ms over 1500 ms (AC-U11) |
| house-manual | seed:house-manual | pass | regression scenario seeded |
| house-manual | instrumented:house-manual | pass | 20 tests |
| house-manual | flow:H01-onboarding | pass | ok |
| house-manual | flow:H02-draw-plan | pass | ok |
| house-manual | flow:H02b-shape-doors-stairs | pass | ok |
| house-manual | flow:H03-blueprint-room | pass | ok |
| house-manual | flow:H04-add-item | fail | see maestro/H04-add-item.log |
| house-manual | flow:H05-contacts | pass | ok |
| house-manual | flow:H06-documents | pass | ok |
| house-manual | flow:H07-home | pass | ok |
| house-manual | flow:H15-sign-in | pass | ok |
| house-manual | flow:H18-paywall | pass | ok |
| house-manual | variant:dark | pass | tour ok |
| house-manual | variant:font200 | pass | tour ok |
| house-manual | variant:landscape | pass | tour ok |
| house-manual | jank:house-manual | fail | 100% janky frames of 8 (AC-U11) |
| house-manual | monkey:house-manual | pass | 1500 events, no crash |
| house-manual | release:house-manual | pass | R8 build launches (1918 ms) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Major] Unit test com.kurtz.homerecord.housemanual.PlanEditorViewModelTest.tapAWallThenAddDoorSelectsItAndSaves fails [auto:unit:com.kurtz.homerecord.housemanual.PlanEditorViewModelTest.tapAWallThenAddDoorSelectsItAndSaves] - steps: automated, see qa-results branch latest/ for commit ee54ff8 - expected: test passes (CLAUDE.md: never weaken tests) - actual: java.lang.NullPointerException
- [ ] [Major] Flow H04-add-item fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H04-add-item] - steps: automated, see qa-results branch latest/ for commit ee54ff8 - expected: walkthrough step H04 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H04-add-item.log

## REVIEW.md entry

## QA auto 2026-10-27 (commit ee54ff8)

Automated suite run at 2026-10-27 17:46 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] Unit test com.kurtz.homerecord.housemanual.PlanEditorViewModelTest.tapAWallThenAddDoorSelectsItAndSaves fails [auto:unit:com.kurtz.homerecord.housemanual.PlanEditorViewModelTest.tapAWallThenAddDoorSelectsItAndSaves] - steps: automated, see qa-results branch latest/ for commit ee54ff8 - expected: test passes (CLAUDE.md: never weaken tests) - actual: java.lang.NullPointerException
- [ ] [Major] Flow H04-add-item fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H04-add-item] - steps: automated, see qa-results branch latest/ for commit ee54ff8 - expected: walkthrough step H04 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H04-add-item.log

Still open from earlier runs: [auto:coldstart:field-log:field-log], [auto:coldstart:handoff:field-log], [auto:coldstart:handoff:house-manual], [auto:jank:handoff:field-log], [auto:jank:handoff:house-manual], [auto:logcat:handoff:com.kurtz.homerecord.fieldlog], [auto:logcat:handoff:com.kurtz.homerecord.housemanual], [auto:coldstart:house-manual:house-manual], [auto:jank:house-manual:house-manual]

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Pass, field-log F03 Pass, field-log F04 Pass, field-log F04b Pass, field-log F05 Pass, field-log F06 Pass, field-log F07 Pass, field-log F08 Pass, field-log F09 Pass, field-log F09b Pass, house-manual H01 Pass, house-manual H02 Pass, house-manual H02b Pass, house-manual H03 Pass, house-manual H04 Fail, house-manual H05 Pass, house-manual H06 Pass, house-manual H07 Pass, house-manual H15 Pass, house-manual H18 Pass

Not built yet or skipped: field-log jank:field-log (gfxinfo reported no frames); handoff flows (no flows in qa/flows/handoff yet (PLAN Q004)); handoff variants (no _tour.yaml for handoff)

Tested: 71 device stages, 670 unit tests, 95 backend tests, coverage 117/117 ACs. Passed: 58. New issues: 2. Critical: 0. Product gaps: n/a (judge entry follows).
