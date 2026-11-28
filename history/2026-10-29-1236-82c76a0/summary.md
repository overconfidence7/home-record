# QA suite summary
Commit `82c76a0`, 2026-10-29 12:36 UTC

- Unit tests: 817 (0 failing)
- Backend tests: 108 (0 failing)
- Coverage gate: {'acs_claimed': 215, 'acs_missing': 0, 'ticked_tasks': 116}
- Device stages: 50/62 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | pass | debug apk |
| field-log | install-test:field-log | pass | androidTest apk |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | seed:field-log | pass | regression scenario seeded |
| field-log | instrumented:field-log | fail | 1 failing of 16 |
| field-log | flow:F01-welcome | pass | ok |
| field-log | flow:F02-invite-join | pass | ok |
| field-log | flow:F03-jobs | pass | ok |
| field-log | flow:F04-clock-in | pass | ok |
| field-log | flow:F04b-location-allow | pass | ok |
| field-log | flow:F04c-today-clock-in | pass | ok |
| field-log | flow:F06-timesheets | pass | ok |
| field-log | flow:F07-files | pass | ok |
| field-log | flow:F07b-storage | pass | ok |
| field-log | flow:F08-capture | pass | ok |
| field-log | flow:F08b-capture-photo | fail | see maestro/F08b-capture-photo.log |
| field-log | flow:F15-reports | pass | ok |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | pass | tour ok |
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | jank:field-log | skip | not measurable: no benchmark apk |
| field-log | release:field-log | pass | R8 build launches (1611 ms) |
| field-log | coldstart:field-log | pass | median 784 ms (release, profile compiled) |
| field-log | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | install:field-log | pass | debug apk |
| handoff | install-test:field-log | pass | androidTest apk |
| handoff | install:house-manual | pass | debug apk |
| handoff | install-test:house-manual | pass | androidTest apk |
| handoff | permissions | pass | granted where declared |
| handoff | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| handoff | seed:field-log | skip | no debug seed receiver yet (PLAN Q005) |
| handoff | seed:house-manual | skip | no debug seed receiver yet (PLAN Q005) |
| handoff | flows | skip | no flows in qa/flows/handoff yet (PLAN Q004) |
| handoff | variants | skip | no _tour.yaml for handoff |
| handoff | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| house-manual | install:house-manual | pass | debug apk |
| house-manual | install-test:house-manual | pass | androidTest apk |
| house-manual | permissions | pass | granted where declared |
| house-manual | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| house-manual | seed:house-manual | pass | regression scenario seeded |
| house-manual | instrumented:house-manual | pass | 20 tests |
| house-manual | flow:H01-onboarding | pass | ok |
| house-manual | flow:H01b-home | pass | ok |
| house-manual | flow:H02-draw-plan | fail | see maestro/H02-draw-plan.log |
| house-manual | flow:H02b-shape-doors-stairs | fail | see maestro/H02b-shape-doors-stairs.log |
| house-manual | flow:H03-blueprint-room | fail | see maestro/H03-blueprint-room.log |
| house-manual | flow:H04-add-item | fail | see maestro/H04-add-item.log |
| house-manual | flow:H17-sign-in | pass | ok |
| house-manual | flow:H19-paywall | pass | ok |
| house-manual | flow:x-contacts | pass | ok |
| house-manual | flow:x-documents | pass | ok |
| house-manual | variant:dark | pass | tour ok |
| house-manual | variant:font200 | pass | tour ok |
| house-manual | variant:landscape | pass | tour ok |
| house-manual | monkey:house-manual | pass | 1500 events, no crash |
| house-manual | jank:house-manual | skip | not measurable: no benchmark apk |
| house-manual | release:house-manual | pass | R8 build launches (1242 ms) |
| house-manual | coldstart:house-manual | pass | median 749 ms (release, profile compiled) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Major] Instrumented tests fail on field-log (regression of a fixed item) [auto:instrumented:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit 82c76a0 - expected: all acceptance tests and the screen crawler pass - actual: 1 failing of 16 (qa-results latest/field-log/instrumented/field-log.txt)
- [ ] [Major] Flow F08b-capture-photo fails on field-log [auto:flow:field-log:F08b-capture-photo] - steps: automated, see qa-results branch latest/ for commit 82c76a0 - expected: walkthrough step F08b passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F08b-capture-photo.log
- [ ] [Major] Flow H02-draw-plan fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H02-draw-plan] - steps: automated, see qa-results branch latest/ for commit 82c76a0 - expected: walkthrough step H02 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02-draw-plan.log
- [ ] [Major] Flow H02b-shape-doors-stairs fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H02b-shape-doors-stairs] - steps: automated, see qa-results branch latest/ for commit 82c76a0 - expected: walkthrough step H02b passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02b-shape-doors-stairs.log
- [ ] [Major] Flow H03-blueprint-room fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H03-blueprint-room] - steps: automated, see qa-results branch latest/ for commit 82c76a0 - expected: walkthrough step H03 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H03-blueprint-room.log
- [ ] [Major] Flow H04-add-item fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H04-add-item] - steps: automated, see qa-results branch latest/ for commit 82c76a0 - expected: walkthrough step H04 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H04-add-item.log

## REVIEW.md entry

## QA auto 2026-10-29 (commit 82c76a0)

Automated suite run at 2026-10-29 12:36 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] Instrumented tests fail on field-log (regression of a fixed item) [auto:instrumented:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit 82c76a0 - expected: all acceptance tests and the screen crawler pass - actual: 1 failing of 16 (qa-results latest/field-log/instrumented/field-log.txt)
- [ ] [Major] Flow F08b-capture-photo fails on field-log [auto:flow:field-log:F08b-capture-photo] - steps: automated, see qa-results branch latest/ for commit 82c76a0 - expected: walkthrough step F08b passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F08b-capture-photo.log
- [ ] [Major] Flow H02-draw-plan fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H02-draw-plan] - steps: automated, see qa-results branch latest/ for commit 82c76a0 - expected: walkthrough step H02 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02-draw-plan.log
- [ ] [Major] Flow H02b-shape-doors-stairs fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H02b-shape-doors-stairs] - steps: automated, see qa-results branch latest/ for commit 82c76a0 - expected: walkthrough step H02b passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02b-shape-doors-stairs.log
- [ ] [Major] Flow H03-blueprint-room fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H03-blueprint-room] - steps: automated, see qa-results branch latest/ for commit 82c76a0 - expected: walkthrough step H03 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H03-blueprint-room.log
- [ ] [Major] Flow H04-add-item fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H04-add-item] - steps: automated, see qa-results branch latest/ for commit 82c76a0 - expected: walkthrough step H04 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H04-add-item.log

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Pass, field-log F03 Pass, field-log F04 Pass, field-log F04b Pass, field-log F04c Pass, field-log F06 Pass, field-log F07 Pass, field-log F07b Pass, field-log F08 Pass, field-log F08b Fail, field-log F15 Pass, house-manual H01 Pass, house-manual H01b Pass, house-manual H02 Fail, house-manual H02b Fail, house-manual H03 Fail, house-manual H04 Fail, house-manual H17 Pass, house-manual H19 Pass

Not built yet or skipped: field-log jank:field-log (not measurable: no benchmark apk); handoff seed:field-log (no debug seed receiver yet (PLAN Q005)); handoff seed:house-manual (no debug seed receiver yet (PLAN Q005)); handoff flows (no flows in qa/flows/handoff yet (PLAN Q004)); handoff variants (no _tour.yaml for handoff); house-manual jank:house-manual (not measurable: no benchmark apk)

Tested: 62 device stages, 817 unit tests, 108 backend tests, coverage 215/215 ACs. Passed: 50. New issues: 6. Critical: 0. Product gaps: n/a (judge entry follows).
