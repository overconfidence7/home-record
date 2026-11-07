# QA suite summary
Commit `f97d6ee`, 2026-10-21 02:12 UTC

- Unit tests: 230 (0 failing)
- Backend tests: 51 (0 failing)
- Coverage gate: {'acs_claimed': 49, 'acs_missing': 0, 'ticked_tasks': 20}
- Device stages: 24/57 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | pass | debug apk |
| field-log | install-test:field-log | pass | androidTest apk |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | coldstart:field-log | fail | median 5444 ms over 1500 ms (AC-U11) |
| field-log | seed:field-log | skip | no debug seed receiver yet (PLAN Q005) |
| field-log | instrumented:field-log | fail | 1 failing of 2 |
| field-log | flow:F01-welcome | fail | see maestro/F01-welcome.log |
| field-log | flow:F02-invite-join | fail | see maestro/F02-invite-join.log |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | pass | tour ok |
| field-log | jank:field-log | fail | 100% janky frames of 5 (AC-U11) |
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | release:field-log | pass | R8 build launches (1884 ms) |
| field-log | logcat:com.kurtz.homerecord.fieldlog | warn | 0 crashes, 0 ANRs, 4 StrictMode violations |
| handoff | install:field-log | pass | debug apk |
| handoff | install-test:field-log | pass | androidTest apk |
| handoff | install:house-manual | pass | debug apk |
| handoff | install-test:house-manual | pass | androidTest apk |
| handoff | permissions | pass | granted where declared |
| handoff | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| handoff | coldstart:field-log | fail | median 5651 ms over 1500 ms (AC-U11) |
| handoff | coldstart:house-manual | fail | median 3780 ms over 1500 ms (AC-U11) |
| handoff | seed:field-log | skip | no debug seed receiver yet (PLAN Q005) |
| handoff | seed:house-manual | skip | no debug seed receiver yet (PLAN Q005) |
| handoff | instrumented:field-log | fail | 2 failing of 2 |
| handoff | instrumented:house-manual | fail | 3 failing of 16 |
| handoff | flows | skip | no flows in qa/flows/handoff yet (PLAN Q004) |
| handoff | variants | skip | no _tour.yaml for handoff |
| handoff | jank:field-log | fail | 80% janky frames of 5 (AC-U11) |
| handoff | jank:house-manual | fail | 87% janky frames of 8 (AC-U11) |
| handoff | monkey:field-log | pass | 1500 events, no crash |
| handoff | monkey:house-manual | pass | 1500 events, no crash |
| handoff | release:field-log | pass | R8 build launches (2583 ms) |
| handoff | release:house-manual | pass | R8 build launches (1964 ms) |
| handoff | logcat:com.kurtz.homerecord.fieldlog | warn | 0 crashes, 0 ANRs, 3 StrictMode violations |
| handoff | logcat:com.kurtz.homerecord.housemanual | warn | 0 crashes, 0 ANRs, 3 StrictMode violations |
| house-manual | install:house-manual | pass | debug apk |
| house-manual | install-test:house-manual | pass | androidTest apk |
| house-manual | permissions | pass | granted where declared |
| house-manual | fixtures | warn | adb push failed |
| house-manual | coldstart:house-manual | fail | median 4501 ms over 1500 ms (AC-U11) |
| house-manual | seed:house-manual | skip | no debug seed receiver yet (PLAN Q005) |
| house-manual | instrumented:house-manual | fail | 3 failing of 16 |
| house-manual | flow:H01-onboarding | fail | see maestro/H01-onboarding.log |
| house-manual | flow:H02-draw-plan | fail | see maestro/H02-draw-plan.log |
| house-manual | flow:H02b-shape-doors-stairs | fail | see maestro/H02b-shape-doors-stairs.log |
| house-manual | flow:H03-blueprint-room | fail | see maestro/H03-blueprint-room.log |
| house-manual | flow:H15-sign-in | fail | see maestro/H15-sign-in.log |
| house-manual | variant:dark | fail | tour failed under dark |
| house-manual | variant:font200 | fail | tour failed under font200 |
| house-manual | variant:landscape | fail | tour failed under landscape |
| house-manual | jank:house-manual | fail | 100% janky frames of 10 (AC-U11) |
| house-manual | monkey:house-manual | pass | 1500 events, no crash |
| house-manual | release:house-manual | pass | R8 build launches (1718 ms) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | warn | 0 crashes, 0 ANRs, 1 StrictMode violations |

## Findings

- [ ] [Major] field-log cold start too slow [auto:coldstart:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: under 1,500 ms (AC-U11) - actual: median 5444 ms over 1500 ms (AC-U11)
- [ ] [Major] Instrumented tests fail on field-log [auto:instrumented:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: all acceptance tests and the screen crawler pass - actual: 1 failing of 2 (qa-results latest/field-log/instrumented/field-log.txt)
- [ ] [Major] Flow F01-welcome fails on field-log [auto:flow:field-log:F01-welcome] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step F01 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F01-welcome.log
- [ ] [Major] Flow F02-invite-join fails on field-log [auto:flow:field-log:F02-invite-join] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step F02 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F02-invite-join.log
- [ ] [Major] field-log scroll jank [auto:jank:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: under 10% janky frames (AC-U11) - actual: 100% janky frames of 5 (AC-U11)
- [ ] [Minor] logcat for com.kurtz.homerecord.fieldlog: 0 crashes, 0 ANRs, 4 StrictMode violations [auto:logcat:field-log:com.kurtz.homerecord.fieldlog] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: no crashes or ANRs; StrictMode clean (S7) - actual: see qa-results latest/field-log/logs/crashes.txt and strictmode.txt
- [ ] [Major] field-log cold start too slow [auto:coldstart:handoff:field-log] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: under 1,500 ms (AC-U11) - actual: median 5651 ms over 1500 ms (AC-U11)
- [ ] [Major] house-manual cold start too slow [auto:coldstart:handoff:house-manual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: under 1,500 ms (AC-U11) - actual: median 3780 ms over 1500 ms (AC-U11)
- [ ] [Major] Instrumented tests fail on field-log [auto:instrumented:handoff:field-log] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: all acceptance tests and the screen crawler pass - actual: 2 failing of 2 (qa-results latest/handoff/instrumented/field-log.txt)
- [ ] [Major] Instrumented tests fail on house-manual [auto:instrumented:handoff:house-manual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: all acceptance tests and the screen crawler pass - actual: 3 failing of 16 (qa-results latest/handoff/instrumented/house-manual.txt)
- [ ] [Major] field-log scroll jank [auto:jank:handoff:field-log] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: under 10% janky frames (AC-U11) - actual: 80% janky frames of 5 (AC-U11)
- [ ] [Major] house-manual scroll jank [auto:jank:handoff:house-manual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: under 10% janky frames (AC-U11) - actual: 87% janky frames of 8 (AC-U11)
- [ ] [Minor] logcat for com.kurtz.homerecord.fieldlog: 0 crashes, 0 ANRs, 3 StrictMode violations [auto:logcat:handoff:com.kurtz.homerecord.fieldlog] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: no crashes or ANRs; StrictMode clean (S7) - actual: see qa-results latest/handoff/logs/crashes.txt and strictmode.txt
- [ ] [Minor] logcat for com.kurtz.homerecord.housemanual: 0 crashes, 0 ANRs, 3 StrictMode violations [auto:logcat:handoff:com.kurtz.homerecord.housemanual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: no crashes or ANRs; StrictMode clean (S7) - actual: see qa-results latest/handoff/logs/crashes.txt and strictmode.txt
- [ ] [Major] fixtures failed on house-manual [auto:fixtures:house-manual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: stage passes - actual: adb push failed
- [ ] [Major] house-manual cold start too slow [auto:coldstart:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: under 1,500 ms (AC-U11) - actual: median 4501 ms over 1500 ms (AC-U11)
- [ ] [Major] Instrumented tests fail on house-manual [auto:instrumented:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: all acceptance tests and the screen crawler pass - actual: 3 failing of 16 (qa-results latest/house-manual/instrumented/house-manual.txt)
- [ ] [Major] Flow H01-onboarding fails on house-manual [auto:flow:house-manual:H01-onboarding] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step H01 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H01-onboarding.log
- [ ] [Major] Flow H02-draw-plan fails on house-manual [auto:flow:house-manual:H02-draw-plan] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step H02 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02-draw-plan.log
- [ ] [Major] Flow H02b-shape-doors-stairs fails on house-manual [auto:flow:house-manual:H02b-shape-doors-stairs] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step H02b passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02b-shape-doors-stairs.log
- [ ] [Major] Flow H03-blueprint-room fails on house-manual [auto:flow:house-manual:H03-blueprint-room] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step H03 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H03-blueprint-room.log
- [ ] [Major] Flow H15-sign-in fails on house-manual [auto:flow:house-manual:H15-sign-in] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step H15 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H15-sign-in.log
- [ ] [Major] Tour fails under dark on house-manual [auto:variant:house-manual:dark] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under dark
- [ ] [Major] Tour fails under font200 on house-manual [auto:variant:house-manual:font200] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under font200
- [ ] [Major] Tour fails under landscape on house-manual [auto:variant:house-manual:landscape] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under landscape
- [ ] [Major] house-manual scroll jank [auto:jank:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: under 10% janky frames (AC-U11) - actual: 100% janky frames of 10 (AC-U11)
- [ ] [Minor] logcat for com.kurtz.homerecord.housemanual: 0 crashes, 0 ANRs, 1 StrictMode violations [auto:logcat:house-manual:com.kurtz.homerecord.housemanual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: no crashes or ANRs; StrictMode clean (S7) - actual: see qa-results latest/house-manual/logs/crashes.txt and strictmode.txt

## REVIEW.md entry

## QA auto 2026-10-21 (commit f97d6ee)

Automated suite run at 2026-10-21 02:12 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] field-log cold start too slow [auto:coldstart:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: under 1,500 ms (AC-U11) - actual: median 5444 ms over 1500 ms (AC-U11)
- [ ] [Major] Instrumented tests fail on field-log [auto:instrumented:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: all acceptance tests and the screen crawler pass - actual: 1 failing of 2 (qa-results latest/field-log/instrumented/field-log.txt)
- [ ] [Major] Flow F01-welcome fails on field-log [auto:flow:field-log:F01-welcome] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step F01 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F01-welcome.log
- [ ] [Major] Flow F02-invite-join fails on field-log [auto:flow:field-log:F02-invite-join] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step F02 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F02-invite-join.log
- [ ] [Major] field-log scroll jank [auto:jank:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: under 10% janky frames (AC-U11) - actual: 100% janky frames of 5 (AC-U11)
- [ ] [Minor] logcat for com.kurtz.homerecord.fieldlog: 0 crashes, 0 ANRs, 4 StrictMode violations [auto:logcat:field-log:com.kurtz.homerecord.fieldlog] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: no crashes or ANRs; StrictMode clean (S7) - actual: see qa-results latest/field-log/logs/crashes.txt and strictmode.txt
- [ ] [Major] field-log cold start too slow [auto:coldstart:handoff:field-log] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: under 1,500 ms (AC-U11) - actual: median 5651 ms over 1500 ms (AC-U11)
- [ ] [Major] house-manual cold start too slow [auto:coldstart:handoff:house-manual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: under 1,500 ms (AC-U11) - actual: median 3780 ms over 1500 ms (AC-U11)
- [ ] [Major] Instrumented tests fail on field-log [auto:instrumented:handoff:field-log] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: all acceptance tests and the screen crawler pass - actual: 2 failing of 2 (qa-results latest/handoff/instrumented/field-log.txt)
- [ ] [Major] Instrumented tests fail on house-manual [auto:instrumented:handoff:house-manual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: all acceptance tests and the screen crawler pass - actual: 3 failing of 16 (qa-results latest/handoff/instrumented/house-manual.txt)
- [ ] [Major] field-log scroll jank [auto:jank:handoff:field-log] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: under 10% janky frames (AC-U11) - actual: 80% janky frames of 5 (AC-U11)
- [ ] [Major] house-manual scroll jank [auto:jank:handoff:house-manual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: under 10% janky frames (AC-U11) - actual: 87% janky frames of 8 (AC-U11)
- [ ] [Minor] logcat for com.kurtz.homerecord.fieldlog: 0 crashes, 0 ANRs, 3 StrictMode violations [auto:logcat:handoff:com.kurtz.homerecord.fieldlog] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: no crashes or ANRs; StrictMode clean (S7) - actual: see qa-results latest/handoff/logs/crashes.txt and strictmode.txt
- [ ] [Minor] logcat for com.kurtz.homerecord.housemanual: 0 crashes, 0 ANRs, 3 StrictMode violations [auto:logcat:handoff:com.kurtz.homerecord.housemanual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: no crashes or ANRs; StrictMode clean (S7) - actual: see qa-results latest/handoff/logs/crashes.txt and strictmode.txt
- [ ] [Major] fixtures failed on house-manual [auto:fixtures:house-manual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: stage passes - actual: adb push failed
- [ ] [Major] house-manual cold start too slow [auto:coldstart:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: under 1,500 ms (AC-U11) - actual: median 4501 ms over 1500 ms (AC-U11)
- [ ] [Major] Instrumented tests fail on house-manual [auto:instrumented:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: all acceptance tests and the screen crawler pass - actual: 3 failing of 16 (qa-results latest/house-manual/instrumented/house-manual.txt)
- [ ] [Major] Flow H01-onboarding fails on house-manual [auto:flow:house-manual:H01-onboarding] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step H01 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H01-onboarding.log
- [ ] [Major] Flow H02-draw-plan fails on house-manual [auto:flow:house-manual:H02-draw-plan] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step H02 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02-draw-plan.log
- [ ] [Major] Flow H02b-shape-doors-stairs fails on house-manual [auto:flow:house-manual:H02b-shape-doors-stairs] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step H02b passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02b-shape-doors-stairs.log
- [ ] [Major] Flow H03-blueprint-room fails on house-manual [auto:flow:house-manual:H03-blueprint-room] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step H03 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H03-blueprint-room.log
- [ ] [Major] Flow H15-sign-in fails on house-manual [auto:flow:house-manual:H15-sign-in] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step H15 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H15-sign-in.log
- [ ] [Major] Tour fails under dark on house-manual [auto:variant:house-manual:dark] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under dark
- [ ] [Major] Tour fails under font200 on house-manual [auto:variant:house-manual:font200] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under font200
- [ ] [Major] Tour fails under landscape on house-manual [auto:variant:house-manual:landscape] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under landscape
- [ ] [Major] house-manual scroll jank [auto:jank:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: under 10% janky frames (AC-U11) - actual: 100% janky frames of 10 (AC-U11)
- [ ] [Minor] logcat for com.kurtz.homerecord.housemanual: 0 crashes, 0 ANRs, 1 StrictMode violations [auto:logcat:house-manual:com.kurtz.homerecord.housemanual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: no crashes or ANRs; StrictMode clean (S7) - actual: see qa-results latest/house-manual/logs/crashes.txt and strictmode.txt

Walkthrough steps exercised by flows: field-log F01 Fail, field-log F02 Fail, house-manual H01 Fail, house-manual H02 Fail, house-manual H02b Fail, house-manual H03 Fail, house-manual H15 Fail

Not built yet or skipped: field-log seed:field-log (no debug seed receiver yet (PLAN Q005)); handoff seed:field-log (no debug seed receiver yet (PLAN Q005)); handoff seed:house-manual (no debug seed receiver yet (PLAN Q005)); handoff flows (no flows in qa/flows/handoff yet (PLAN Q004)); handoff variants (no _tour.yaml for handoff); house-manual seed:house-manual (no debug seed receiver yet (PLAN Q005))

Tested: 57 device stages, 230 unit tests, 51 backend tests, coverage 49/49 ACs. Passed: 24. New issues: 27. Critical: 0. Product gaps: n/a (judge entry follows).
