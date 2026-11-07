# QA suite summary
Commit `0a2059ec`, 2026-11-04 01:05 UTC

- Unit tests: 1590 (0 failing)
- Backend tests: 161 (0 failing)
- Coverage gate: {'acs_claimed': 343, 'acs_missing': 0, 'ticked_tasks': 192}
- Device stages: 25/28 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| house-manual | install:house-manual | pass | debug apk |
| house-manual | install-test:house-manual | pass | androidTest apk |
| house-manual | permissions | pass | granted where declared |
| house-manual | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| house-manual | seed:house-manual | pass | regression scenario seeded |
| house-manual | instrumented:house-manual | pass | 21 tests |
| house-manual | flow:H01-onboarding | pass | ok |
| house-manual | flow:H01b-home | pass | ok |
| house-manual | flow:H02-draw-plan | pass | ok |
| house-manual | flow:H02b-shape-doors-stairs | pass | ok |
| house-manual | flow:H03-blueprint-room | pass | ok |
| house-manual | flow:H04-add-item | pass | ok |
| house-manual | flow:H06-items | fail | see maestro/H06-items.log |
| house-manual | flow:H07-item-page | fail | see maestro/H07-item-page.log |
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
| house-manual | jank:house-manual | pass | 5% of 120 frames ran the UI thread past the frame budget (software renderer, overall 75% janky of 120; 200-row list, benchmark build) |
| house-manual | release:house-manual | pass | R8 build launches (946 ms) |
| house-manual | coldstart:house-manual | pass | median 576 ms (release, profile compiled) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Critical] device suite produced no results for field-log (regression of a fixed item) [auto:device:field-log:no-results] - steps: automated, see qa-results branch latest/ for commit 0a2059ec - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run
- [ ] [Major] Flow H06-items fails on house-manual [auto:flow:house-manual:H06-items] - steps: automated, see qa-results branch latest/ for commit 0a2059ec - expected: walkthrough step H06 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H06-items.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H06-items-H06-items-by-category.png
- [ ] [Major] Flow H07-item-page fails on house-manual [auto:flow:house-manual:H07-item-page] - steps: automated, see qa-results branch latest/ for commit 0a2059ec - expected: walkthrough step H07 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H07-item-page.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H07-item-page-step-013-assertCondition-Refrigerator.png
- [ ] [Major] Flow H11-overlay-pipe fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H11-overlay-pipe] - steps: automated, see qa-results branch latest/ for commit 0a2059ec - expected: walkthrough step H11 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H11-overlay-pipe.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H11-overlay-pipe-H01-home-after-onboarding.png

## REVIEW.md entry

## QA auto 2026-11-04 (commit 0a2059ec)

Automated suite run at 2026-11-04 01:05 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Critical] device suite produced no results for field-log (regression of a fixed item) [auto:device:field-log:no-results] - steps: automated, see qa-results branch latest/ for commit 0a2059ec - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run
- [ ] [Major] Flow H06-items fails on house-manual [auto:flow:house-manual:H06-items] - steps: automated, see qa-results branch latest/ for commit 0a2059ec - expected: walkthrough step H06 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H06-items.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H06-items-H06-items-by-category.png
- [ ] [Major] Flow H07-item-page fails on house-manual [auto:flow:house-manual:H07-item-page] - steps: automated, see qa-results branch latest/ for commit 0a2059ec - expected: walkthrough step H07 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H07-item-page.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H07-item-page-step-013-assertCondition-Refrigerator.png
- [ ] [Major] Flow H11-overlay-pipe fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H11-overlay-pipe] - steps: automated, see qa-results branch latest/ for commit 0a2059ec - expected: walkthrough step H11 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H11-overlay-pipe.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H11-overlay-pipe-H01-home-after-onboarding.png

Walkthrough steps exercised by flows: house-manual H01 Pass, house-manual H01b Pass, house-manual H02 Pass, house-manual H02b Pass, house-manual H03 Pass, house-manual H04 Pass, house-manual H06 Fail, house-manual H07 Fail, house-manual H09 Pass, house-manual H11 Fail, house-manual H17 Pass, house-manual H19 Pass

Not built yet or skipped: handoff flows (no flows in qa/flows/handoff yet (PLAN Q004); handoff job not run)

Tested: 28 device stages, 1590 unit tests, 161 backend tests, coverage 343/343 ACs. Passed: 25. New issues: 4. Critical: 1. Product gaps: n/a (judge entry follows).
