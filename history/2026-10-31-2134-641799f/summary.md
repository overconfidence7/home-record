# QA suite summary
Commit `641799f9`, 2026-10-31 21:34 UTC

- Unit tests: 1051 (0 failing)
- Backend tests: 140 (0 failing)
- Coverage gate: {'acs_claimed': 251, 'acs_missing': 0, 'ticked_tasks': 136}
- Device stages: 53/55 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | pass | debug apk |
| field-log | install-test:field-log | pass | androidTest apk |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | seed:field-log | pass | regression scenario seeded |
| field-log | instrumented:field-log | pass | 23 tests |
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
| field-log | flow:F11-tasks | fail | see maestro/F11-tasks.log |
| field-log | flow:F15-reports | pass | ok |
| field-log | flow:x-seeded-company | pass | ok |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | pass | tour ok |
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | jank:field-log | pass | 3% of 113 frames ran the UI thread past the frame budget (software renderer, overall 89% janky of 110; 200-row list, benchmark build) |
| field-log | release:field-log | pass | R8 build launches (1617 ms) |
| field-log | coldstart:field-log | pass | median 831 ms (release, profile compiled) |
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
| house-manual | flow:H03-blueprint-room | pass | ok |
| house-manual | flow:H04-add-item | fail | see maestro/H04-add-item.log |
| house-manual | flow:H06-items | pass | ok |
| house-manual | flow:H17-sign-in | pass | ok |
| house-manual | flow:H19-paywall | pass | ok |
| house-manual | flow:x-contacts | pass | ok |
| house-manual | flow:x-documents | pass | ok |
| house-manual | variant:dark | pass | tour ok |
| house-manual | variant:font200 | pass | tour ok |
| house-manual | variant:landscape | pass | tour ok |
| house-manual | monkey:house-manual | pass | 1500 events, no crash |
| house-manual | jank:house-manual | pass | 9% of 104 frames ran the UI thread past the frame budget (software renderer, overall 89% janky of 104; 200-row list, benchmark build) |
| house-manual | release:house-manual | pass | R8 build launches (1350 ms) |
| house-manual | coldstart:house-manual | pass | median 809 ms (release, profile compiled) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Major] Flow F11-tasks fails on field-log [auto:flow:field-log:F11-tasks] - steps: automated, see qa-results branch latest/ for commit 641799f9 - expected: walkthrough step F11 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F11-tasks.log - screenshot: qa-results:latest/field-log/screenshots/flow-F11-tasks-step-071-assertCondition-Done_Set_the_kitchen_out.png
- [ ] [Major] Flow H04-add-item fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H04-add-item] - steps: automated, see qa-results branch latest/ for commit 641799f9 - expected: walkthrough step H04 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H04-add-item.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H04-add-item-H01-home-after-onboarding.png

## REVIEW.md entry

## QA auto 2026-10-31 (commit 641799f9)

Automated suite run at 2026-10-31 21:34 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] Flow F11-tasks fails on field-log [auto:flow:field-log:F11-tasks] - steps: automated, see qa-results branch latest/ for commit 641799f9 - expected: walkthrough step F11 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F11-tasks.log - screenshot: qa-results:latest/field-log/screenshots/flow-F11-tasks-step-071-assertCondition-Done_Set_the_kitchen_out.png
- [ ] [Major] Flow H04-add-item fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H04-add-item] - steps: automated, see qa-results branch latest/ for commit 641799f9 - expected: walkthrough step H04 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H04-add-item.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H04-add-item-H01-home-after-onboarding.png

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Pass, field-log F03 Pass, field-log F04 Pass, field-log F04b Pass, field-log F04c Pass, field-log F06 Pass, field-log F07 Pass, field-log F07b Pass, field-log F07c Pass, field-log F08 Pass, field-log F08b Pass, field-log F09 Pass, field-log F11 Fail, field-log F15 Pass, house-manual H01 Pass, house-manual H01b Pass, house-manual H02 Pass, house-manual H02b Pass, house-manual H03 Pass, house-manual H04 Fail, house-manual H06 Pass, house-manual H17 Pass, house-manual H19 Pass

Not built yet or skipped: handoff flows (no flows in qa/flows/handoff yet (PLAN Q004); handoff job not run)

Tested: 55 device stages, 1051 unit tests, 140 backend tests, coverage 251/251 ACs. Passed: 53. New issues: 2. Critical: 0. Product gaps: n/a (judge entry follows).
