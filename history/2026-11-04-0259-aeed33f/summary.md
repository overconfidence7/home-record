# QA suite summary
Commit `aeed33fb`, 2026-11-04 02:59 UTC

- Unit tests: 1596 (0 failing)
- Backend tests: 161 (0 failing)
- Coverage gate: {'acs_claimed': 343, 'acs_missing': 0, 'ticked_tasks': 192}
- Device stages: 64/64 passed

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
| field-log | flow:F10-house-3d | pass | ok |
| field-log | flow:F10b-behind-wall | pass | ok |
| field-log | flow:F11-tasks | pass | ok |
| field-log | flow:F11b-task-pin | pass | ok |
| field-log | flow:F11c-file-pin | pass | ok |
| field-log | flow:F15-reports | pass | ok |
| field-log | flow:F16-offline | pass | ok |
| field-log | flow:x-seeded-company | pass | ok |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | pass | tour ok |
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | jank:field-log | pass | 7% of 120 frames ran the UI thread past the frame budget (software renderer, overall 69% janky of 138; 200-row list, benchmark build) |
| field-log | release:field-log | pass | R8 build launches (1088 ms) |
| field-log | coldstart:field-log | pass | median 635 ms (release, profile compiled) |
| field-log | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
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
| house-manual | flow:H06-items | pass | ok |
| house-manual | flow:H07-item-page | pass | ok |
| house-manual | flow:H09-wall-photo | pass | ok |
| house-manual | flow:H11-overlay-pipe | pass | ok |
| house-manual | flow:H17-sign-in | pass | ok |
| house-manual | flow:H19-paywall | pass | ok |
| house-manual | flow:x-contacts | pass | ok |
| house-manual | flow:x-documents | pass | ok |
| house-manual | variant:dark | pass | tour ok |
| house-manual | variant:font200 | pass | tour ok |
| house-manual | variant:landscape | pass | tour ok |
| house-manual | monkey:house-manual | pass | 1500 events, no crash |
| house-manual | jank:house-manual | pass | 8% of 108 frames ran the UI thread past the frame budget (software renderer, overall 91% janky of 107; 200-row list, benchmark build) |
| house-manual | release:house-manual | pass | R8 build launches (1793 ms) |
| house-manual | coldstart:house-manual | pass | median 891 ms (release, profile compiled) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

none new

## REVIEW.md entry

## QA auto 2026-11-04 (commit aeed33fb)

Automated suite run at 2026-11-04 02:59 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

No new automated findings.

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Pass, field-log F03 Pass, field-log F04 Pass, field-log F04b Pass, field-log F04c Pass, field-log F06 Pass, field-log F07 Pass, field-log F07b Pass, field-log F07c Pass, field-log F08 Pass, field-log F08b Pass, field-log F09 Pass, field-log F09b Pass, field-log F10 Pass, field-log F10b Pass, field-log F11 Pass, field-log F11b Pass, field-log F11c Pass, field-log F15 Pass, field-log F16 Pass, house-manual H01 Pass, house-manual H01b Pass, house-manual H02 Pass, house-manual H02b Pass, house-manual H03 Pass, house-manual H04 Pass, house-manual H06 Pass, house-manual H07 Pass, house-manual H09 Pass, house-manual H11 Pass, house-manual H17 Pass, house-manual H19 Pass

Not built yet or skipped: handoff flows (no flows in qa/flows/handoff yet (PLAN Q004); handoff job not run)

Tested: 64 device stages, 1596 unit tests, 161 backend tests, coverage 343/343 ACs. Passed: 64. New issues: 0. Critical: 0. Product gaps: n/a (judge entry follows).
