# QA suite summary
Commit `9d87512`, 2026-10-29 22:52 UTC

- Unit tests: 849 (0 failing)
- Backend tests: 129 (0 failing)
- Coverage gate: {'acs_claimed': 217, 'acs_missing': 0, 'ticked_tasks': 118}
- Device stages: 37/50 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | pass | debug apk |
| field-log | install-test:field-log | pass | androidTest apk |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | seed:field-log | pass | regression scenario seeded |
| field-log | instrumented:field-log | pass | 16 tests |
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
| field-log | flow:F08b-capture-photo | pass | ok |
| field-log | flow:F15-reports | pass | ok |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | pass | tour ok |
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | jank:field-log | skip | not measurable: long-list seed failed (Broadcasting: Intent { act=com.kurtz.homerecord.fieldlog.qa.SEED flg=0x400000 pkg=com.kurtz.homerecord.fieldlog (has extras) }
Broadcast completed: result=0) |
| field-log | release:field-log | pass | R8 build launches (1285 ms) |
| field-log | coldstart:field-log | pass | median 804 ms (release, profile compiled) |
| field-log | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
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
| house-manual | flow:H17-sign-in | fail | see maestro/H17-sign-in.log |
| house-manual | flow:H19-paywall | fail | see maestro/H19-paywall.log |
| house-manual | flow:x-contacts | fail | see maestro/x-contacts.log |
| house-manual | flow:x-documents | fail | see maestro/x-documents.log |
| house-manual | variant:dark | fail | tour failed under dark |
| house-manual | variant:font200 | fail | tour failed under font200 |
| house-manual | variant:landscape | fail | tour failed under landscape |
| house-manual | monkey:house-manual | pass | 1500 events, no crash |
| house-manual | jank:house-manual | skip | not measurable: long-list seed failed (Broadcasting: Intent { act=com.kurtz.homerecord.housemanual.qa.SEED flg=0x400000 pkg=com.kurtz.homerecord.housemanual (has extras) }
Broadcast completed: result=0) |
| house-manual | release:house-manual | pass | R8 build launches (889 ms) |
| house-manual | coldstart:house-manual | pass | median 650 ms (release, profile compiled) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Major] Flow H02-draw-plan fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H02-draw-plan] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: walkthrough step H02 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02-draw-plan.log
- [ ] [Major] Flow H02b-shape-doors-stairs fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H02b-shape-doors-stairs] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: walkthrough step H02b passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02b-shape-doors-stairs.log
- [ ] [Major] Flow H03-blueprint-room fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H03-blueprint-room] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: walkthrough step H03 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H03-blueprint-room.log
- [ ] [Major] Flow H04-add-item fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H04-add-item] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: walkthrough step H04 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H04-add-item.log
- [ ] [Major] Flow H17-sign-in fails on house-manual [auto:flow:house-manual:H17-sign-in] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: walkthrough step H17 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H17-sign-in.log
- [ ] [Major] Flow H19-paywall fails on house-manual [auto:flow:house-manual:H19-paywall] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: walkthrough step H19 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H19-paywall.log
- [ ] [Major] Flow x-contacts fails on house-manual [auto:flow:house-manual:x-contacts] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/house-manual/maestro/x-contacts.log
- [ ] [Major] Flow x-documents fails on house-manual [auto:flow:house-manual:x-documents] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/house-manual/maestro/x-documents.log
- [ ] [Major] Tour fails under dark on house-manual (regression of a fixed item) [auto:variant:house-manual:dark] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under dark
- [ ] [Major] Tour fails under font200 on house-manual (regression of a fixed item) [auto:variant:house-manual:font200] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under font200
- [ ] [Major] Tour fails under landscape on house-manual (regression of a fixed item) [auto:variant:house-manual:landscape] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under landscape

## REVIEW.md entry

## QA auto 2026-10-29 (commit 9d87512)

Automated suite run at 2026-10-29 22:52 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] Flow H02-draw-plan fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H02-draw-plan] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: walkthrough step H02 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02-draw-plan.log
- [ ] [Major] Flow H02b-shape-doors-stairs fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H02b-shape-doors-stairs] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: walkthrough step H02b passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02b-shape-doors-stairs.log
- [ ] [Major] Flow H03-blueprint-room fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H03-blueprint-room] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: walkthrough step H03 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H03-blueprint-room.log
- [ ] [Major] Flow H04-add-item fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H04-add-item] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: walkthrough step H04 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H04-add-item.log
- [ ] [Major] Flow H17-sign-in fails on house-manual [auto:flow:house-manual:H17-sign-in] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: walkthrough step H17 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H17-sign-in.log
- [ ] [Major] Flow H19-paywall fails on house-manual [auto:flow:house-manual:H19-paywall] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: walkthrough step H19 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H19-paywall.log
- [ ] [Major] Flow x-contacts fails on house-manual [auto:flow:house-manual:x-contacts] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/house-manual/maestro/x-contacts.log
- [ ] [Major] Flow x-documents fails on house-manual [auto:flow:house-manual:x-documents] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/house-manual/maestro/x-documents.log
- [ ] [Major] Tour fails under dark on house-manual (regression of a fixed item) [auto:variant:house-manual:dark] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under dark
- [ ] [Major] Tour fails under font200 on house-manual (regression of a fixed item) [auto:variant:house-manual:font200] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under font200
- [ ] [Major] Tour fails under landscape on house-manual (regression of a fixed item) [auto:variant:house-manual:landscape] - steps: automated, see qa-results branch latest/ for commit 9d87512 - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under landscape

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Pass, field-log F03 Pass, field-log F04 Pass, field-log F04b Pass, field-log F04c Pass, field-log F06 Pass, field-log F07 Pass, field-log F07b Pass, field-log F08 Pass, field-log F08b Pass, field-log F15 Pass, house-manual H01 Pass, house-manual H01b Pass, house-manual H02 Fail, house-manual H02b Fail, house-manual H03 Fail, house-manual H04 Fail, house-manual H17 Fail, house-manual H19 Fail

Not built yet or skipped: handoff flows (no flows in qa/flows/handoff yet (PLAN Q004); handoff job not run); field-log jank:field-log (not measurable: long-list seed failed (Broadcasting: Intent { act=com.kurtz.homerecord.fieldlog.qa.SEED flg=0x400000 pkg=com.kurtz.homerecord.fieldlog (has extras) }
Broadcast completed: result=0)); house-manual jank:house-manual (not measurable: long-list seed failed (Broadcasting: Intent { act=com.kurtz.homerecord.housemanual.qa.SEED flg=0x400000 pkg=com.kurtz.homerecord.housemanual (has extras) }
Broadcast completed: result=0))

Tested: 50 device stages, 849 unit tests, 129 backend tests, coverage 217/217 ACs. Passed: 37. New issues: 11. Critical: 0. Product gaps: n/a (judge entry follows).
