# QA suite summary
Commit `a1dbe16`, 2026-10-24 15:30 UTC

- Unit tests: 374 (0 failing)
- Backend tests: 64 (0 failing)
- Coverage gate: {'acs_claimed': 97, 'acs_missing': 1, 'ticked_tasks': 37}
- Device stages: 43/62 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | pass | debug apk |
| field-log | install-test:field-log | pass | androidTest apk |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | coldstart:field-log | fail | median 5200 ms over 1500 ms (AC-U11) |
| field-log | seed:field-log | pass | regression scenario seeded |
| field-log | instrumented:field-log | pass | 2 tests |
| field-log | flow:F01-welcome | fail | see maestro/F01-welcome.log |
| field-log | flow:F02-invite-join | fail | see maestro/F02-invite-join.log |
| field-log | flow:F03-jobs | fail | see maestro/F03-jobs.log |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | pass | tour ok |
| field-log | jank:field-log | fail | 100% janky frames of 4 (AC-U11) |
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | release:field-log | pass | R8 build launches (1985 ms) |
| field-log | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | install:field-log | pass | debug apk |
| handoff | install-test:field-log | pass | androidTest apk |
| handoff | install:house-manual | pass | debug apk |
| handoff | install-test:house-manual | pass | androidTest apk |
| handoff | permissions | pass | granted where declared |
| handoff | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| handoff | coldstart:field-log | fail | median 3805 ms over 1500 ms (AC-U11) |
| handoff | coldstart:house-manual | fail | median 2315 ms over 1500 ms (AC-U11) |
| handoff | seed:field-log | pass | regression scenario seeded |
| handoff | seed:house-manual | pass | regression scenario seeded |
| handoff | instrumented:field-log | pass | 2 tests |
| handoff | instrumented:house-manual | fail | 1 failing of 20 |
| handoff | flows | skip | no flows in qa/flows/handoff yet (PLAN Q004) |
| handoff | variants | skip | no _tour.yaml for handoff |
| handoff | jank:field-log | fail | 50% janky frames of 2 (AC-U11) |
| handoff | jank:house-manual | fail | 87% janky frames of 8 (AC-U11) |
| handoff | monkey:field-log | pass | 1500 events, no crash |
| handoff | monkey:house-manual | pass | 1500 events, no crash |
| handoff | release:field-log | pass | R8 build launches (2466 ms) |
| handoff | release:house-manual | pass | R8 build launches (1845 ms) |
| handoff | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| house-manual | install:house-manual | pass | debug apk |
| house-manual | install-test:house-manual | pass | androidTest apk |
| house-manual | permissions | pass | granted where declared |
| house-manual | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| house-manual | coldstart:house-manual | fail | median 4090 ms over 1500 ms (AC-U11) |
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
| house-manual | variant:dark | pass | tour ok |
| house-manual | variant:font200 | pass | tour ok |
| house-manual | variant:landscape | pass | tour ok |
| house-manual | jank:house-manual | fail | 100% janky frames of 8 (AC-U11) |
| house-manual | monkey:house-manual | pass | 1500 events, no crash |
| house-manual | release:house-manual | pass | R8 build launches (1793 ms) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Major] T024 is ticked but these acceptance criteria have no test or flow: AC-C33 [auto:coverage:T024] - steps: automated, see qa-results branch latest/ for commit a1dbe16 - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found

## REVIEW.md entry

## QA auto 2026-10-24 (commit a1dbe16)

Automated suite run at 2026-10-24 15:30 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] T024 is ticked but these acceptance criteria have no test or flow: AC-C33 [auto:coverage:T024] - steps: automated, see qa-results branch latest/ for commit a1dbe16 - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found

Still open from earlier runs: [auto:coldstart:field-log:field-log], [auto:flow:field-log:F01-welcome], [auto:flow:field-log:F02-invite-join], [auto:flow:field-log:F03-jobs], [auto:jank:field-log:field-log], [auto:coldstart:handoff:field-log], [auto:coldstart:handoff:house-manual], [auto:instrumented:handoff:house-manual], [auto:jank:handoff:field-log], [auto:jank:handoff:house-manual], [auto:coldstart:house-manual:house-manual], [auto:instrumented:house-manual:house-manual], [auto:flow:house-manual:H02b-shape-doors-stairs], [auto:flow:house-manual:H04-add-item], [auto:flow:house-manual:H05-contacts], [auto:flow:house-manual:H15-sign-in], [auto:jank:house-manual:house-manual]

Walkthrough steps exercised by flows: field-log F01 Fail, field-log F02 Fail, field-log F03 Fail, house-manual H01 Pass, house-manual H02 Pass, house-manual H02b Fail, house-manual H03 Pass, house-manual H04 Fail, house-manual H05 Fail, house-manual H06 Pass, house-manual H07 Pass, house-manual H15 Fail

Not built yet or skipped: handoff flows (no flows in qa/flows/handoff yet (PLAN Q004)); handoff variants (no _tour.yaml for handoff)

Tested: 62 device stages, 374 unit tests, 64 backend tests, coverage 96/97 ACs. Passed: 43. New issues: 1. Critical: 0. Product gaps: n/a (judge entry follows).
