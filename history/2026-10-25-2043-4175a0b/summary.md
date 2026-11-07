# QA suite summary
Commit `4175a0b`, 2026-10-25 20:43 UTC

- Unit tests: 503 (0 failing)
- Backend tests: 77 (0 failing)
- Coverage gate: {'acs_claimed': 109, 'acs_missing': 0, 'ticked_tasks': 42}
- Device stages: 42/67 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | pass | debug apk |
| field-log | install-test:field-log | pass | androidTest apk |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | coldstart:field-log | fail | median 3843 ms over 1500 ms (AC-U11) |
| field-log | seed:field-log | pass | regression scenario seeded |
| field-log | instrumented:field-log | pass | 2 tests |
| field-log | flow:F01-welcome | pass | ok |
| field-log | flow:F02-invite-join | fail | see maestro/F02-invite-join.log |
| field-log | flow:F03-jobs | fail | see maestro/F03-jobs.log |
| field-log | flow:F04-clock-in | fail | see maestro/F04-clock-in.log |
| field-log | flow:F05-today | fail | see maestro/F05-today.log |
| field-log | flow:F06-timesheets | fail | see maestro/F06-timesheets.log |
| field-log | flow:F07-reports | fail | see maestro/F07-reports.log |
| field-log | variant:dark | fail | tour failed under dark |
| field-log | variant:font200 | fail | tour failed under font200 |
| field-log | variant:landscape | fail | tour failed under landscape |
| field-log | jank:field-log | fail | 61% janky frames of 84 (AC-U11) |
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | release:field-log | pass | R8 build launches (1489 ms) |
| field-log | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | install:field-log | pass | debug apk |
| handoff | install-test:field-log | pass | androidTest apk |
| handoff | install:house-manual | pass | debug apk |
| handoff | install-test:house-manual | pass | androidTest apk |
| handoff | permissions | pass | granted where declared |
| handoff | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| handoff | coldstart:field-log | fail | median 4857 ms over 1500 ms (AC-U11) |
| handoff | coldstart:house-manual | fail | median 4297 ms over 1500 ms (AC-U11) |
| handoff | seed:field-log | pass | regression scenario seeded |
| handoff | seed:house-manual | pass | regression scenario seeded |
| handoff | instrumented:field-log | pass | 2 tests |
| handoff | instrumented:house-manual | fail | 1 failing of 20 |
| handoff | flows | skip | no flows in qa/flows/handoff yet (PLAN Q004) |
| handoff | variants | skip | no _tour.yaml for handoff |
| handoff | jank:field-log | fail | 66% janky frames of 6 (AC-U11) |
| handoff | jank:house-manual | fail | 87% janky frames of 8 (AC-U11) |
| handoff | monkey:field-log | pass | 1500 events, no crash |
| handoff | monkey:house-manual | pass | 1500 events, no crash |
| handoff | release:field-log | pass | R8 build launches (2652 ms) |
| handoff | release:house-manual | pass | R8 build launches (2094 ms) |
| handoff | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| house-manual | install:house-manual | pass | debug apk |
| house-manual | install-test:house-manual | pass | androidTest apk |
| house-manual | permissions | pass | granted where declared |
| house-manual | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| house-manual | coldstart:house-manual | fail | median 4937 ms over 1500 ms (AC-U11) |
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
| house-manual | jank:house-manual | fail | 100% janky frames of 11 (AC-U11) |
| house-manual | monkey:house-manual | pass | 1500 events, no crash |
| house-manual | release:house-manual | pass | R8 build launches (1961 ms) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

none new

## REVIEW.md entry

## QA auto 2026-10-25 (commit 4175a0b)

Automated suite run at 2026-10-25 20:43 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

No new automated findings.

Still open from earlier runs: [auto:coldstart:field-log:field-log], [auto:flow:field-log:F02-invite-join], [auto:flow:field-log:F03-jobs], [auto:flow:field-log:F04-clock-in], [auto:flow:field-log:F05-today], [auto:flow:field-log:F06-timesheets], [auto:flow:field-log:F07-reports], [auto:variant:field-log:dark], [auto:variant:field-log:font200], [auto:variant:field-log:landscape], [auto:jank:field-log:field-log], [auto:coldstart:handoff:field-log], [auto:coldstart:handoff:house-manual], [auto:instrumented:handoff:house-manual], [auto:jank:handoff:field-log], [auto:jank:handoff:house-manual], [auto:coldstart:house-manual:house-manual], [auto:instrumented:house-manual:house-manual], [auto:flow:house-manual:H02b-shape-doors-stairs], [auto:flow:house-manual:H04-add-item], [auto:flow:house-manual:H05-contacts], [auto:flow:house-manual:H15-sign-in], [auto:jank:house-manual:house-manual]

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Fail, field-log F03 Fail, field-log F04 Fail, field-log F05 Fail, field-log F06 Fail, field-log F07 Fail, house-manual H01 Pass, house-manual H02 Pass, house-manual H02b Fail, house-manual H03 Pass, house-manual H04 Fail, house-manual H05 Fail, house-manual H06 Pass, house-manual H07 Pass, house-manual H15 Fail, house-manual H18 Pass

Not built yet or skipped: handoff flows (no flows in qa/flows/handoff yet (PLAN Q004)); handoff variants (no _tour.yaml for handoff)

Tested: 67 device stages, 503 unit tests, 77 backend tests, coverage 109/109 ACs. Passed: 42. New issues: 0. Critical: 0. Product gaps: n/a (judge entry follows).
