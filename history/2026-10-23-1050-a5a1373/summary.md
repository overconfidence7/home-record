# QA suite summary
Commit `a5a1373`, 2026-10-23 10:50 UTC

- Unit tests: 296 (0 failing)
- Backend tests: 56 (0 failing)
- Coverage gate: {'acs_claimed': 80, 'acs_missing': 0, 'ticked_tasks': 27}
- Device stages: 31/59 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | pass | debug apk |
| field-log | install-test:field-log | pass | androidTest apk |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | coldstart:field-log | fail | median 4730 ms over 1500 ms (AC-U11) |
| field-log | seed:field-log | pass | regression scenario seeded |
| field-log | instrumented:field-log | pass | 2 tests |
| field-log | flow:F01-welcome | fail | see maestro/F01-welcome.log |
| field-log | flow:F02-invite-join | fail | see maestro/F02-invite-join.log |
| field-log | flow:F03-jobs | fail | see maestro/F03-jobs.log |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | pass | tour ok |
| field-log | jank:field-log | fail | 83% janky frames of 6 (AC-U11) |
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | release:field-log | pass | R8 build launches (1695 ms) |
| field-log | logcat:com.kurtz.homerecord.fieldlog | warn | 0 crashes, 0 ANRs, 1 StrictMode violations |
| handoff | install:field-log | pass | debug apk |
| handoff | install-test:field-log | pass | androidTest apk |
| handoff | install:house-manual | pass | debug apk |
| handoff | install-test:house-manual | pass | androidTest apk |
| handoff | permissions | pass | granted where declared |
| handoff | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| handoff | coldstart:field-log | fail | median 6435 ms over 1500 ms (AC-U11) |
| handoff | coldstart:house-manual | fail | median 4509 ms over 1500 ms (AC-U11) |
| handoff | seed:field-log | pass | regression scenario seeded |
| handoff | seed:house-manual | pass | regression scenario seeded |
| handoff | instrumented:field-log | pass | 2 tests |
| handoff | instrumented:house-manual | fail | 1 failing of 20 |
| handoff | flows | skip | no flows in qa/flows/handoff yet (PLAN Q004) |
| handoff | variants | skip | no _tour.yaml for handoff |
| handoff | jank:field-log | fail | 100% janky frames of 4 (AC-U11) |
| handoff | jank:house-manual | fail | 100% janky frames of 4 (AC-U11) |
| handoff | monkey:field-log | pass | 1500 events, no crash |
| handoff | monkey:house-manual | pass | 1500 events, no crash |
| handoff | release:field-log | pass | R8 build launches (3154 ms) |
| handoff | release:house-manual | pass | R8 build launches (1902 ms) |
| handoff | logcat:com.kurtz.homerecord.fieldlog | warn | 0 crashes, 0 ANRs, 5 StrictMode violations |
| handoff | logcat:com.kurtz.homerecord.housemanual | warn | 0 crashes, 0 ANRs, 5 StrictMode violations |
| house-manual | install:house-manual | pass | debug apk |
| house-manual | install-test:house-manual | pass | androidTest apk |
| house-manual | permissions | pass | granted where declared |
| house-manual | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| house-manual | coldstart:house-manual | fail | median 5197 ms over 1500 ms (AC-U11) |
| house-manual | seed:house-manual | pass | regression scenario seeded |
| house-manual | instrumented:house-manual | fail | 1 failing of 20 |
| house-manual | flow:H01-onboarding | fail | see maestro/H01-onboarding.log |
| house-manual | flow:H02-draw-plan | fail | see maestro/H02-draw-plan.log |
| house-manual | flow:H02b-shape-doors-stairs | fail | see maestro/H02b-shape-doors-stairs.log |
| house-manual | flow:H03-blueprint-room | fail | see maestro/H03-blueprint-room.log |
| house-manual | flow:H04-add-item | fail | see maestro/H04-add-item.log |
| house-manual | flow:H15-sign-in | fail | see maestro/H15-sign-in.log |
| house-manual | variant:dark | fail | tour failed under dark |
| house-manual | variant:font200 | fail | tour failed under font200 |
| house-manual | variant:landscape | fail | tour failed under landscape |
| house-manual | jank:house-manual | fail | 100% janky frames of 5 (AC-U11) |
| house-manual | monkey:house-manual | pass | 1500 events, no crash |
| house-manual | release:house-manual | pass | R8 build launches (1942 ms) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | warn | 0 crashes, 0 ANRs, 5 StrictMode violations |

## Findings

- [ ] [Major] Flow F03-jobs fails on field-log [auto:flow:field-log:F03-jobs] - steps: automated, see qa-results branch latest/ for commit a5a1373 - expected: walkthrough step F03 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F03-jobs.log
- [ ] [Major] Flow H01-onboarding fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H01-onboarding] - steps: automated, see qa-results branch latest/ for commit a5a1373 - expected: walkthrough step H01 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H01-onboarding.log
- [ ] [Major] Flow H03-blueprint-room fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H03-blueprint-room] - steps: automated, see qa-results branch latest/ for commit a5a1373 - expected: walkthrough step H03 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H03-blueprint-room.log
- [ ] [Major] Flow H04-add-item fails on house-manual [auto:flow:house-manual:H04-add-item] - steps: automated, see qa-results branch latest/ for commit a5a1373 - expected: walkthrough step H04 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H04-add-item.log
- [ ] [Major] Tour fails under dark on house-manual (regression of a fixed item) [auto:variant:house-manual:dark] - steps: automated, see qa-results branch latest/ for commit a5a1373 - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under dark
- [ ] [Major] Tour fails under font200 on house-manual (regression of a fixed item) [auto:variant:house-manual:font200] - steps: automated, see qa-results branch latest/ for commit a5a1373 - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under font200
- [ ] [Major] Tour fails under landscape on house-manual (regression of a fixed item) [auto:variant:house-manual:landscape] - steps: automated, see qa-results branch latest/ for commit a5a1373 - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under landscape

## REVIEW.md entry

## QA auto 2026-10-23 (commit a5a1373)

Automated suite run at 2026-10-23 10:50 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] Flow F03-jobs fails on field-log [auto:flow:field-log:F03-jobs] - steps: automated, see qa-results branch latest/ for commit a5a1373 - expected: walkthrough step F03 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F03-jobs.log
- [ ] [Major] Flow H01-onboarding fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H01-onboarding] - steps: automated, see qa-results branch latest/ for commit a5a1373 - expected: walkthrough step H01 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H01-onboarding.log
- [ ] [Major] Flow H03-blueprint-room fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H03-blueprint-room] - steps: automated, see qa-results branch latest/ for commit a5a1373 - expected: walkthrough step H03 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H03-blueprint-room.log
- [ ] [Major] Flow H04-add-item fails on house-manual [auto:flow:house-manual:H04-add-item] - steps: automated, see qa-results branch latest/ for commit a5a1373 - expected: walkthrough step H04 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H04-add-item.log
- [ ] [Major] Tour fails under dark on house-manual (regression of a fixed item) [auto:variant:house-manual:dark] - steps: automated, see qa-results branch latest/ for commit a5a1373 - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under dark
- [ ] [Major] Tour fails under font200 on house-manual (regression of a fixed item) [auto:variant:house-manual:font200] - steps: automated, see qa-results branch latest/ for commit a5a1373 - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under font200
- [ ] [Major] Tour fails under landscape on house-manual (regression of a fixed item) [auto:variant:house-manual:landscape] - steps: automated, see qa-results branch latest/ for commit a5a1373 - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under landscape

Still open from earlier runs: [auto:coldstart:field-log:field-log], [auto:flow:field-log:F01-welcome], [auto:flow:field-log:F02-invite-join], [auto:jank:field-log:field-log], [auto:logcat:field-log:com.kurtz.homerecord.fieldlog], [auto:coldstart:handoff:field-log], [auto:coldstart:handoff:house-manual], [auto:instrumented:handoff:house-manual], [auto:jank:handoff:field-log], [auto:jank:handoff:house-manual], [auto:logcat:handoff:com.kurtz.homerecord.fieldlog], [auto:logcat:handoff:com.kurtz.homerecord.housemanual], [auto:coldstart:house-manual:house-manual], [auto:instrumented:house-manual:house-manual], [auto:flow:house-manual:H02-draw-plan], [auto:flow:house-manual:H02b-shape-doors-stairs], [auto:flow:house-manual:H15-sign-in], [auto:jank:house-manual:house-manual], [auto:logcat:house-manual:com.kurtz.homerecord.housemanual]

Walkthrough steps exercised by flows: field-log F01 Fail, field-log F02 Fail, field-log F03 Fail, house-manual H01 Fail, house-manual H02 Fail, house-manual H02b Fail, house-manual H03 Fail, house-manual H04 Fail, house-manual H15 Fail

Not built yet or skipped: handoff flows (no flows in qa/flows/handoff yet (PLAN Q004)); handoff variants (no _tour.yaml for handoff)

Tested: 59 device stages, 296 unit tests, 56 backend tests, coverage 80/80 ACs. Passed: 31. New issues: 7. Critical: 0. Product gaps: n/a (judge entry follows).
