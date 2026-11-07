# QA suite summary
Commit `5125597`, 2026-10-22 02:14 UTC

- Unit tests: 240 (0 failing)
- Backend tests: 54 (0 failing)
- Coverage gate: {'acs_claimed': 73, 'acs_missing': 0, 'ticked_tasks': 23}
- Device stages: 33/57 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | pass | debug apk |
| field-log | install-test:field-log | pass | androidTest apk |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | coldstart:field-log | fail | median 4234 ms over 1500 ms (AC-U11) |
| field-log | seed:field-log | skip | no debug seed receiver yet (PLAN Q005) |
| field-log | instrumented:field-log | fail | 1 failing of 2 |
| field-log | flow:F01-welcome | fail | see maestro/F01-welcome.log |
| field-log | flow:F02-invite-join | fail | see maestro/F02-invite-join.log |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | pass | tour ok |
| field-log | jank:field-log | fail | 100% janky frames of 8 (AC-U11) |
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | release:field-log | pass | R8 build launches (1964 ms) |
| field-log | logcat:com.kurtz.homerecord.fieldlog | warn | 0 crashes, 0 ANRs, 2 StrictMode violations |
| handoff | install:field-log | pass | debug apk |
| handoff | install-test:field-log | pass | androidTest apk |
| handoff | install:house-manual | pass | debug apk |
| handoff | install-test:house-manual | pass | androidTest apk |
| handoff | permissions | pass | granted where declared |
| handoff | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| handoff | coldstart:field-log | fail | median 5264 ms over 1500 ms (AC-U11) |
| handoff | coldstart:house-manual | fail | median 4584 ms over 1500 ms (AC-U11) |
| handoff | seed:field-log | skip | no debug seed receiver yet (PLAN Q005) |
| handoff | seed:house-manual | pass | regression scenario seeded |
| handoff | instrumented:field-log | fail | 1 failing of 2 |
| handoff | instrumented:house-manual | pass | 19 tests |
| handoff | flows | skip | no flows in qa/flows/handoff yet (PLAN Q004) |
| handoff | variants | skip | no _tour.yaml for handoff |
| handoff | jank:field-log | fail | 83% janky frames of 6 (AC-U11) |
| handoff | jank:house-manual | fail | 100% janky frames of 3 (AC-U11) |
| handoff | monkey:field-log | pass | 1500 events, no crash |
| handoff | monkey:house-manual | pass | 1500 events, no crash |
| handoff | release:field-log | pass | R8 build launches (2819 ms) |
| handoff | release:house-manual | pass | R8 build launches (1875 ms) |
| handoff | logcat:com.kurtz.homerecord.fieldlog | warn | 0 crashes, 0 ANRs, 4 StrictMode violations |
| handoff | logcat:com.kurtz.homerecord.housemanual | warn | 0 crashes, 0 ANRs, 4 StrictMode violations |
| house-manual | install:house-manual | pass | debug apk |
| house-manual | install-test:house-manual | pass | androidTest apk |
| house-manual | permissions | pass | granted where declared |
| house-manual | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| house-manual | coldstart:house-manual | fail | median 5427 ms over 1500 ms (AC-U11) |
| house-manual | seed:house-manual | pass | regression scenario seeded |
| house-manual | instrumented:house-manual | pass | 19 tests |
| house-manual | flow:H01-onboarding | pass | ok |
| house-manual | flow:H02-draw-plan | fail | see maestro/H02-draw-plan.log |
| house-manual | flow:H02b-shape-doors-stairs | fail | see maestro/H02b-shape-doors-stairs.log |
| house-manual | flow:H03-blueprint-room | fail | see maestro/H03-blueprint-room.log |
| house-manual | flow:H15-sign-in | fail | see maestro/H15-sign-in.log |
| house-manual | variant:dark | pass | tour ok |
| house-manual | variant:font200 | pass | tour ok |
| house-manual | variant:landscape | pass | tour ok |
| house-manual | jank:house-manual | fail | 100% janky frames of 4 (AC-U11) |
| house-manual | monkey:house-manual | pass | 1500 events, no crash |
| house-manual | release:house-manual | pass | R8 build launches (1795 ms) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | warn | 0 crashes, 0 ANRs, 188 StrictMode violations |

## Findings

none new

## REVIEW.md entry

## QA auto 2026-10-22 (commit 5125597)

Automated suite run at 2026-10-22 02:14 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

No new automated findings.

Still open from earlier runs: [auto:coldstart:field-log:field-log], [auto:instrumented:field-log:field-log], [auto:flow:field-log:F01-welcome], [auto:flow:field-log:F02-invite-join], [auto:jank:field-log:field-log], [auto:logcat:field-log:com.kurtz.homerecord.fieldlog], [auto:coldstart:handoff:field-log], [auto:coldstart:handoff:house-manual], [auto:instrumented:handoff:field-log], [auto:jank:handoff:field-log], [auto:jank:handoff:house-manual], [auto:logcat:handoff:com.kurtz.homerecord.fieldlog], [auto:logcat:handoff:com.kurtz.homerecord.housemanual], [auto:coldstart:house-manual:house-manual], [auto:flow:house-manual:H02-draw-plan], [auto:flow:house-manual:H02b-shape-doors-stairs], [auto:flow:house-manual:H03-blueprint-room], [auto:flow:house-manual:H15-sign-in], [auto:jank:house-manual:house-manual], [auto:logcat:house-manual:com.kurtz.homerecord.housemanual]

Walkthrough steps exercised by flows: field-log F01 Fail, field-log F02 Fail, house-manual H01 Pass, house-manual H02 Fail, house-manual H02b Fail, house-manual H03 Fail, house-manual H15 Fail

Not built yet or skipped: field-log seed:field-log (no debug seed receiver yet (PLAN Q005)); handoff seed:field-log (no debug seed receiver yet (PLAN Q005)); handoff flows (no flows in qa/flows/handoff yet (PLAN Q004)); handoff variants (no _tour.yaml for handoff)

Tested: 57 device stages, 240 unit tests, 54 backend tests, coverage 73/73 ACs. Passed: 33. New issues: 0. Critical: 0. Product gaps: n/a (judge entry follows).
