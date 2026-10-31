# QA suite summary
Commit `695d291f`, 2026-10-31 03:28 UTC

- Unit tests: 919 (0 failing)
- Backend tests: 133 (0 failing)
- Coverage gate: {'acs_claimed': 223, 'acs_missing': 0, 'ticked_tasks': 119}
- Device stages: 50/52 passed

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
| field-log | flow:F02-invite-join | fail | see maestro/F02-invite-join.log |
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
| field-log | flow:x-seeded-company | pass | ok |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | pass | tour ok |
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | jank:field-log | fail | 13% of 111 frames ran the UI thread past the frame budget (AC-U11; software renderer, overall 88% janky of 109; 200-row list, benchmark build) |
| field-log | release:field-log | pass | R8 build launches (1889 ms) |
| field-log | coldstart:field-log | pass | median 878 ms (release, profile compiled) |
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
| house-manual | jank:house-manual | pass | 6% of 114 frames ran the UI thread past the frame budget (software renderer, overall 87% janky of 114; 200-row list, benchmark build) |
| house-manual | release:house-manual | pass | R8 build launches (1292 ms) |
| house-manual | coldstart:house-manual | pass | median 551 ms (release, profile compiled) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Major] field-log scroll jank (regression of a fixed item) [auto:jank:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit 695d291f - expected: under 10% janky frames (AC-U11) - actual: 13% of 111 frames ran the UI thread past the frame budget (AC-U11; software renderer, overall 88% janky of 109; 200-row list, benchmark build)

## REVIEW.md entry

## QA auto 2026-10-31 (commit 695d291f)

Automated suite run at 2026-10-31 03:28 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] field-log scroll jank (regression of a fixed item) [auto:jank:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit 695d291f - expected: under 10% janky frames (AC-U11) - actual: 13% of 111 frames ran the UI thread past the frame budget (AC-U11; software renderer, overall 88% janky of 109; 200-row list, benchmark build)

Still open from earlier runs: [auto:flow:field-log:F02-invite-join]

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Fail, field-log F03 Pass, field-log F04 Pass, field-log F04b Pass, field-log F04c Pass, field-log F06 Pass, field-log F07 Pass, field-log F07b Pass, field-log F08 Pass, field-log F08b Pass, field-log F15 Pass, house-manual H01 Pass, house-manual H01b Pass, house-manual H02 Pass, house-manual H02b Pass, house-manual H03 Pass, house-manual H04 Pass, house-manual H06 Pass, house-manual H17 Pass, house-manual H19 Pass

Not built yet or skipped: handoff flows (no flows in qa/flows/handoff yet (PLAN Q004); handoff job not run)

Tested: 52 device stages, 919 unit tests, 133 backend tests, coverage 223/223 ACs. Passed: 50. New issues: 1. Critical: 0. Product gaps: n/a (judge entry follows).
