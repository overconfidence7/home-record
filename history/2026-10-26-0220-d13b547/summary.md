# QA suite summary
Commit `d13b547`, 2026-10-26 02:20 UTC

- Unit tests: 534 (0 failing)
- Backend tests: 77 (0 failing)
- Coverage gate: {'acs_claimed': 111, 'acs_missing': 0, 'ticked_tasks': 43}
- Device stages: 47/68 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | pass | debug apk |
| field-log | install-test:field-log | pass | androidTest apk |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | coldstart:field-log | fail | median 4431 ms over 1500 ms (AC-U11) |
| field-log | seed:field-log | pass | regression scenario seeded |
| field-log | instrumented:field-log | pass | 2 tests |
| field-log | flow:F01-welcome | pass | ok |
| field-log | flow:F02-invite-join | fail | see maestro/F02-invite-join.log |
| field-log | flow:F03-jobs | fail | see maestro/F03-jobs.log |
| field-log | flow:F04-clock-in | fail | see maestro/F04-clock-in.log |
| field-log | flow:F05-today | fail | see maestro/F05-today.log |
| field-log | flow:F06-timesheets | pass | ok |
| field-log | flow:F07-reports | pass | ok |
| field-log | flow:F08-capture | fail | see maestro/F08-capture.log |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | pass | tour ok |
| field-log | jank:field-log | fail | 100% janky frames of 3 (AC-U11) |
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | release:field-log | pass | R8 build launches (1850 ms) |
| field-log | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | install:field-log | pass | debug apk |
| handoff | install-test:field-log | pass | androidTest apk |
| handoff | install:house-manual | pass | debug apk |
| handoff | install-test:house-manual | pass | androidTest apk |
| handoff | permissions | pass | granted where declared |
| handoff | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| handoff | coldstart:field-log | fail | median 4389 ms over 1500 ms (AC-U11) |
| handoff | coldstart:house-manual | fail | median 1929 ms over 1500 ms (AC-U11) |
| handoff | seed:field-log | pass | regression scenario seeded |
| handoff | seed:house-manual | pass | regression scenario seeded |
| handoff | instrumented:field-log | pass | 2 tests |
| handoff | instrumented:house-manual | fail | 1 failing of 20 |
| handoff | flows | skip | no flows in qa/flows/handoff yet (PLAN Q004) |
| handoff | variants | skip | no _tour.yaml for handoff |
| handoff | jank:field-log | fail | 100% janky frames of 2 (AC-U11) |
| handoff | jank:house-manual | fail | 87% janky frames of 8 (AC-U11) |
| handoff | monkey:field-log | pass | 1500 events, no crash |
| handoff | monkey:house-manual | pass | 1500 events, no crash |
| handoff | release:field-log | pass | R8 build launches (3196 ms) |
| handoff | release:house-manual | pass | R8 build launches (2443 ms) |
| handoff | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| house-manual | install:house-manual | pass | debug apk |
| house-manual | install-test:house-manual | pass | androidTest apk |
| house-manual | permissions | pass | granted where declared |
| house-manual | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| house-manual | coldstart:house-manual | fail | median 3937 ms over 1500 ms (AC-U11) |
| house-manual | seed:house-manual | pass | regression scenario seeded |
| house-manual | instrumented:house-manual | fail | 1 failing of 20 |
| house-manual | flow:H01-onboarding | pass | ok |
| house-manual | flow:H02-draw-plan | pass | ok |
| house-manual | flow:H02b-shape-doors-stairs | fail | see maestro/H02b-shape-doors-stairs.log |
| house-manual | flow:H03-blueprint-room | pass | ok |
| house-manual | flow:H04-add-item | fail | see maestro/H04-add-item.log |
| house-manual | flow:H05-contacts | fail | see maestro/H05-contacts.log |
| house-manual | flow:H06-documents | pass | ok |
| house-manual | flow:H07-home | pass | ok |
| house-manual | flow:H15-sign-in | fail | see maestro/H15-sign-in.log |
| house-manual | flow:H18-paywall | pass | ok |
| house-manual | variant:dark | pass | tour ok |
| house-manual | variant:font200 | pass | tour ok |
| house-manual | variant:landscape | pass | tour ok |
| house-manual | jank:house-manual | fail | 80% janky frames of 10 (AC-U11) |
| house-manual | monkey:house-manual | pass | 1500 events, no crash |
| house-manual | release:house-manual | pass | R8 build launches (1310 ms) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Major] Flow F03-jobs fails on field-log (regression of a fixed item) [auto:flow:field-log:F03-jobs] - steps: automated, see qa-results branch latest/ for commit d13b547 - expected: walkthrough step F03 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F03-jobs.log
- [ ] [Major] Flow F04-clock-in fails on field-log (regression of a fixed item) [auto:flow:field-log:F04-clock-in] - steps: automated, see qa-results branch latest/ for commit d13b547 - expected: walkthrough step F04 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F04-clock-in.log
- [ ] [Major] Flow F05-today fails on field-log (regression of a fixed item) [auto:flow:field-log:F05-today] - steps: automated, see qa-results branch latest/ for commit d13b547 - expected: walkthrough step F05 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F05-today.log
- [ ] [Major] Flow F08-capture fails on field-log [auto:flow:field-log:F08-capture] - steps: automated, see qa-results branch latest/ for commit d13b547 - expected: walkthrough step F08 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F08-capture.log

## REVIEW.md entry

## QA auto 2026-10-26 (commit d13b547)

Automated suite run at 2026-10-26 02:20 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] Flow F03-jobs fails on field-log (regression of a fixed item) [auto:flow:field-log:F03-jobs] - steps: automated, see qa-results branch latest/ for commit d13b547 - expected: walkthrough step F03 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F03-jobs.log
- [ ] [Major] Flow F04-clock-in fails on field-log (regression of a fixed item) [auto:flow:field-log:F04-clock-in] - steps: automated, see qa-results branch latest/ for commit d13b547 - expected: walkthrough step F04 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F04-clock-in.log
- [ ] [Major] Flow F05-today fails on field-log (regression of a fixed item) [auto:flow:field-log:F05-today] - steps: automated, see qa-results branch latest/ for commit d13b547 - expected: walkthrough step F05 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F05-today.log
- [ ] [Major] Flow F08-capture fails on field-log [auto:flow:field-log:F08-capture] - steps: automated, see qa-results branch latest/ for commit d13b547 - expected: walkthrough step F08 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F08-capture.log

Still open from earlier runs: [auto:coldstart:field-log:field-log], [auto:flow:field-log:F02-invite-join], [auto:jank:field-log:field-log], [auto:coldstart:handoff:field-log], [auto:coldstart:handoff:house-manual], [auto:instrumented:handoff:house-manual], [auto:jank:handoff:field-log], [auto:jank:handoff:house-manual], [auto:coldstart:house-manual:house-manual], [auto:instrumented:house-manual:house-manual], [auto:flow:house-manual:H02b-shape-doors-stairs], [auto:flow:house-manual:H04-add-item], [auto:flow:house-manual:H05-contacts], [auto:flow:house-manual:H15-sign-in], [auto:jank:house-manual:house-manual]

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Fail, field-log F03 Fail, field-log F04 Fail, field-log F05 Fail, field-log F06 Pass, field-log F07 Pass, field-log F08 Fail, house-manual H01 Pass, house-manual H02 Pass, house-manual H02b Fail, house-manual H03 Pass, house-manual H04 Fail, house-manual H05 Fail, house-manual H06 Pass, house-manual H07 Pass, house-manual H15 Fail, house-manual H18 Pass

Not built yet or skipped: handoff flows (no flows in qa/flows/handoff yet (PLAN Q004)); handoff variants (no _tour.yaml for handoff)

Tested: 68 device stages, 534 unit tests, 77 backend tests, coverage 111/111 ACs. Passed: 47. New issues: 4. Critical: 0. Product gaps: n/a (judge entry follows).
