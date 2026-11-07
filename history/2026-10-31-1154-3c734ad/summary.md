# QA suite summary
Commit `3c734ad3`, 2026-10-31 11:54 UTC

- Unit tests: 973 (0 failing)
- Backend tests: 140 (0 failing)
- Coverage gate: {'acs_claimed': 236, 'acs_missing': 0, 'ticked_tasks': 127}
- Device stages: 52/54 passed

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
| field-log | flow:F07c-markup | fail | see maestro/F07c-markup.log |
| field-log | flow:F08-capture | pass | ok |
| field-log | flow:F08b-capture-photo | pass | ok |
| field-log | flow:F09-daily-log | pass | ok |
| field-log | flow:F15-reports | pass | ok |
| field-log | flow:x-seeded-company | pass | ok |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | pass | tour ok |
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | jank:field-log | skip | not measurable: 98 frames rendered while flinging the 200-row list (need 100) |
| field-log | release:field-log | pass | R8 build launches (1673 ms) |
| field-log | coldstart:field-log | pass | median 911 ms (release, profile compiled) |
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
| house-manual | flow:H04-add-item | pass | ok |
| house-manual | flow:H06-items | pass | ok |
| house-manual | flow:H17-sign-in | pass | ok |
| house-manual | flow:H19-paywall | pass | ok |
| house-manual | flow:x-contacts | pass | ok |
| house-manual | flow:x-documents | pass | ok |
| house-manual | variant:dark | pass | tour ok |
| house-manual | variant:font200 | pass | tour ok |
| house-manual | variant:landscape | pass | tour ok |
| house-manual | monkey:house-manual | pass | 1500 events, no crash |
| house-manual | jank:house-manual | pass | 1% of 117 frames ran the UI thread past the frame budget (software renderer, overall 80% janky of 115; 200-row list, benchmark build) |
| house-manual | release:house-manual | pass | R8 build launches (1309 ms) |
| house-manual | coldstart:house-manual | pass | median 624 ms (release, profile compiled) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Major] Flow F07c-markup fails on field-log [auto:flow:field-log:F07c-markup] - steps: automated, see qa-results branch latest/ for commit 3c734ad3 - expected: walkthrough step F07c passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F07c-markup.log - screenshot: qa-results:latest/field-log/screenshots/flow-F07c-markup-F07c-markup-drawn.png

## REVIEW.md entry

## QA auto 2026-10-31 (commit 3c734ad3)

Automated suite run at 2026-10-31 11:54 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] Flow F07c-markup fails on field-log [auto:flow:field-log:F07c-markup] - steps: automated, see qa-results branch latest/ for commit 3c734ad3 - expected: walkthrough step F07c passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F07c-markup.log - screenshot: qa-results:latest/field-log/screenshots/flow-F07c-markup-F07c-markup-drawn.png

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Pass, field-log F03 Pass, field-log F04 Pass, field-log F04b Pass, field-log F04c Pass, field-log F06 Pass, field-log F07 Pass, field-log F07b Pass, field-log F07c Fail, field-log F08 Pass, field-log F08b Pass, field-log F09 Pass, field-log F15 Pass, house-manual H01 Pass, house-manual H01b Pass, house-manual H02 Pass, house-manual H02b Pass, house-manual H03 Pass, house-manual H04 Pass, house-manual H06 Pass, house-manual H17 Pass, house-manual H19 Pass

Not built yet or skipped: handoff flows (no flows in qa/flows/handoff yet (PLAN Q004); handoff job not run); field-log jank:field-log (not measurable: 98 frames rendered while flinging the 200-row list (need 100))

Tested: 54 device stages, 973 unit tests, 140 backend tests, coverage 236/236 ACs. Passed: 52. New issues: 1. Critical: 0. Product gaps: n/a (judge entry follows).
