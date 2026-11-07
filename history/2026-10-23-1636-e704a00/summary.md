# QA suite summary
Commit `e704a00`, 2026-10-23 16:36 UTC

- Unit tests: 303 (0 failing)
- Backend tests: 56 (0 failing)
- Coverage gate: {'acs_claimed': 83, 'acs_missing': 1, 'ticked_tasks': 29}
- Device stages: 41/59 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | pass | debug apk |
| field-log | install-test:field-log | pass | androidTest apk |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | coldstart:field-log | fail | median 4061 ms over 1500 ms (AC-U11) |
| field-log | seed:field-log | pass | regression scenario seeded |
| field-log | instrumented:field-log | pass | 2 tests |
| field-log | flow:F01-welcome | fail | see maestro/F01-welcome.log |
| field-log | flow:F02-invite-join | fail | see maestro/F02-invite-join.log |
| field-log | flow:F03-jobs | fail | see maestro/F03-jobs.log |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | pass | tour ok |
| field-log | jank:field-log | fail | 75% janky frames of 4 (AC-U11) |
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | release:field-log | pass | R8 build launches (1159 ms) |
| field-log | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | install:field-log | pass | debug apk |
| handoff | install-test:field-log | pass | androidTest apk |
| handoff | install:house-manual | pass | debug apk |
| handoff | install-test:house-manual | pass | androidTest apk |
| handoff | permissions | pass | granted where declared |
| handoff | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| handoff | coldstart:field-log | fail | median 4850 ms over 1500 ms (AC-U11) |
| handoff | coldstart:house-manual | fail | median 3407 ms over 1500 ms (AC-U11) |
| handoff | seed:field-log | pass | regression scenario seeded |
| handoff | seed:house-manual | pass | regression scenario seeded |
| handoff | instrumented:field-log | pass | 2 tests |
| handoff | instrumented:house-manual | fail | 1 failing of 20 |
| handoff | flows | skip | no flows in qa/flows/handoff yet (PLAN Q004) |
| handoff | variants | skip | no _tour.yaml for handoff |
| handoff | jank:field-log | fail | 75% janky frames of 4 (AC-U11) |
| handoff | jank:house-manual | fail | 100% janky frames of 4 (AC-U11) |
| handoff | monkey:field-log | pass | 1500 events, no crash |
| handoff | monkey:house-manual | pass | 1500 events, no crash |
| handoff | release:field-log | pass | R8 build launches (3173 ms) |
| handoff | release:house-manual | pass | R8 build launches (2148 ms) |
| handoff | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| house-manual | install:house-manual | pass | debug apk |
| house-manual | install-test:house-manual | pass | androidTest apk |
| house-manual | permissions | pass | granted where declared |
| house-manual | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| house-manual | coldstart:house-manual | fail | median 5688 ms over 1500 ms (AC-U11) |
| house-manual | seed:house-manual | pass | regression scenario seeded |
| house-manual | instrumented:house-manual | fail | 1 failing of 20 |
| house-manual | flow:H01-onboarding | pass | ok |
| house-manual | flow:H02-draw-plan | pass | ok |
| house-manual | flow:H02b-shape-doors-stairs | fail | see maestro/H02b-shape-doors-stairs.log |
| house-manual | flow:H03-blueprint-room | pass | ok |
| house-manual | flow:H04-add-item | fail | see maestro/H04-add-item.log |
| house-manual | flow:H15-sign-in | fail | see maestro/H15-sign-in.log |
| house-manual | variant:dark | pass | tour ok |
| house-manual | variant:font200 | pass | tour ok |
| house-manual | variant:landscape | pass | tour ok |
| house-manual | jank:house-manual | fail | 100% janky frames of 5 (AC-U11) |
| house-manual | monkey:house-manual | pass | 1500 events, no crash |
| house-manual | release:house-manual | pass | R8 build launches (1742 ms) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Major] T022 is ticked but these acceptance criteria have no test or flow: AC-C31 [auto:coverage:T022] - steps: automated, see qa-results branch latest/ for commit e704a00 - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found

## REVIEW.md entry

## QA auto 2026-10-23 (commit e704a00)

Automated suite run at 2026-10-23 16:36 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] T022 is ticked but these acceptance criteria have no test or flow: AC-C31 [auto:coverage:T022] - steps: automated, see qa-results branch latest/ for commit e704a00 - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found

Still open from earlier runs: [auto:coldstart:field-log:field-log], [auto:flow:field-log:F01-welcome], [auto:flow:field-log:F02-invite-join], [auto:flow:field-log:F03-jobs], [auto:jank:field-log:field-log], [auto:coldstart:handoff:field-log], [auto:coldstart:handoff:house-manual], [auto:instrumented:handoff:house-manual], [auto:jank:handoff:field-log], [auto:jank:handoff:house-manual], [auto:coldstart:house-manual:house-manual], [auto:instrumented:house-manual:house-manual], [auto:flow:house-manual:H02b-shape-doors-stairs], [auto:flow:house-manual:H04-add-item], [auto:flow:house-manual:H15-sign-in], [auto:jank:house-manual:house-manual]

Walkthrough steps exercised by flows: field-log F01 Fail, field-log F02 Fail, field-log F03 Fail, house-manual H01 Pass, house-manual H02 Pass, house-manual H02b Fail, house-manual H03 Pass, house-manual H04 Fail, house-manual H15 Fail

Not built yet or skipped: handoff flows (no flows in qa/flows/handoff yet (PLAN Q004)); handoff variants (no _tour.yaml for handoff)

Tested: 59 device stages, 303 unit tests, 56 backend tests, coverage 82/83 ACs. Passed: 41. New issues: 1. Critical: 0. Product gaps: n/a (judge entry follows).
