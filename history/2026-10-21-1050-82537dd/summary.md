# QA suite summary
Commit `82537dd`, 2026-10-21 10:50 UTC

- Unit tests: 233 (0 failing)
- Backend tests: 51 (0 failing)
- Coverage gate: {'acs_claimed': 67, 'acs_missing': 0, 'ticked_tasks': 21}
- Device stages: 23/57 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | fail | adb: failed to install qa-out/apk/field-log-debug.apk: Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.kurtz.homerecord.fieldlog signatures do not match newer version; ignoring!]   |
| field-log | install-test:field-log | fail |  |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | coldstart:field-log | fail | MainActivity did not start |
| field-log | seed:field-log | skip | no debug seed receiver yet (PLAN Q005) |
| field-log | instrumented:field-log | fail | runner did not report (see instrumented/field-log.txt) |
| field-log | flow:F01-welcome | fail | see maestro/F01-welcome.log |
| field-log | flow:F02-invite-join | fail | see maestro/F02-invite-join.log |
| field-log | variant:dark | fail | tour failed under dark |
| field-log | variant:font200 | fail | tour failed under font200 |
| field-log | variant:landscape | fail | tour failed under landscape |
| field-log | jank:field-log | skip | gfxinfo reported no frames |
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | release:field-log | pass | R8 build launches (1787 ms) |
| field-log | logcat:com.kurtz.homerecord.fieldlog | warn | 0 crashes, 0 ANRs, 2 StrictMode violations |
| handoff | install:field-log | fail | adb: failed to install qa-out/apk/field-log-debug.apk: Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.kurtz.homerecord.fieldlog signatures do not match newer version; ignoring!]   |
| handoff | install-test:field-log | fail |  |
| handoff | install:house-manual | pass | debug apk |
| handoff | install-test:house-manual | pass | androidTest apk |
| handoff | permissions | pass | granted where declared |
| handoff | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| handoff | coldstart:field-log | fail | MainActivity did not start |
| handoff | coldstart:house-manual | fail | median 4692 ms over 1500 ms (AC-U11) |
| handoff | seed:field-log | skip | no debug seed receiver yet (PLAN Q005) |
| handoff | seed:house-manual | skip | no debug seed receiver yet (PLAN Q005) |
| handoff | instrumented:field-log | fail | runner did not report (see instrumented/field-log.txt) |
| handoff | instrumented:house-manual | fail | 2 failing of 18 |
| handoff | flows | skip | no flows in qa/flows/handoff yet (PLAN Q004) |
| handoff | variants | skip | no _tour.yaml for handoff |
| handoff | jank:field-log | skip | gfxinfo reported no frames |
| handoff | jank:house-manual | fail | 100% janky frames of 6 (AC-U11) |
| handoff | monkey:field-log | pass | 1500 events, no crash |
| handoff | monkey:house-manual | pass | 1500 events, no crash |
| handoff | release:field-log | pass | R8 build launches (2677 ms) |
| handoff | release:house-manual | pass | R8 build launches (1991 ms) |
| handoff | logcat:com.kurtz.homerecord.fieldlog | warn | 0 crashes, 0 ANRs, 3 StrictMode violations |
| handoff | logcat:com.kurtz.homerecord.housemanual | warn | 0 crashes, 0 ANRs, 3 StrictMode violations |
| house-manual | install:house-manual | pass | debug apk |
| house-manual | install-test:house-manual | pass | androidTest apk |
| house-manual | permissions | pass | granted where declared |
| house-manual | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| house-manual | coldstart:house-manual | fail | median 4403 ms over 1500 ms (AC-U11) |
| house-manual | seed:house-manual | skip | no debug seed receiver yet (PLAN Q005) |
| house-manual | instrumented:house-manual | fail | 2 failing of 18 |
| house-manual | flow:H01-onboarding | pass | ok |
| house-manual | flow:H02-draw-plan | fail | see maestro/H02-draw-plan.log |
| house-manual | flow:H02b-shape-doors-stairs | fail | see maestro/H02b-shape-doors-stairs.log |
| house-manual | flow:H03-blueprint-room | fail | see maestro/H03-blueprint-room.log |
| house-manual | flow:H15-sign-in | fail | see maestro/H15-sign-in.log |
| house-manual | variant:dark | pass | tour ok |
| house-manual | variant:font200 | pass | tour ok |
| house-manual | variant:landscape | pass | tour ok |
| house-manual | jank:house-manual | fail | 100% janky frames of 3 (AC-U11) |
| house-manual | monkey:house-manual | pass | 1500 events, no crash |
| house-manual | release:house-manual | pass | R8 build launches (1557 ms) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Critical] field-log: install failed [auto:install:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: APK installs on the emulator - actual: adb: failed to install qa-out/apk/field-log-debug.apk: Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.kurtz.homerecord.fieldlog signatures do not match newer version; ignoring!]  
- [ ] [Critical] field-log: install-test failed [auto:install-test:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: APK installs on the emulator - actual: 
- [ ] [Major] Tour fails under dark on field-log [auto:variant:field-log:dark] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under dark
- [ ] [Major] Tour fails under font200 on field-log [auto:variant:field-log:font200] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under font200
- [ ] [Major] Tour fails under landscape on field-log [auto:variant:field-log:landscape] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under landscape
- [ ] [Critical] field-log: install failed [auto:install:handoff:field-log] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: APK installs on the emulator - actual: adb: failed to install qa-out/apk/field-log-debug.apk: Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.kurtz.homerecord.fieldlog signatures do not match newer version; ignoring!]  
- [ ] [Critical] field-log: install-test failed [auto:install-test:handoff:field-log] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: APK installs on the emulator - actual: 
- [ ] [Major] Flow H02-draw-plan fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H02-draw-plan] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: walkthrough step H02 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02-draw-plan.log
- [ ] [Major] Flow H02b-shape-doors-stairs fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H02b-shape-doors-stairs] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: walkthrough step H02b passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02b-shape-doors-stairs.log
- [ ] [Major] Flow H03-blueprint-room fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H03-blueprint-room] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: walkthrough step H03 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H03-blueprint-room.log
- [ ] [Major] Flow H15-sign-in fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H15-sign-in] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: walkthrough step H15 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H15-sign-in.log

## REVIEW.md entry

## QA auto 2026-10-21 (commit 82537dd)

Automated suite run at 2026-10-21 10:50 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Critical] field-log: install failed [auto:install:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: APK installs on the emulator - actual: adb: failed to install qa-out/apk/field-log-debug.apk: Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.kurtz.homerecord.fieldlog signatures do not match newer version; ignoring!]  
- [ ] [Critical] field-log: install-test failed [auto:install-test:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: APK installs on the emulator - actual: 
- [ ] [Major] Tour fails under dark on field-log [auto:variant:field-log:dark] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under dark
- [ ] [Major] Tour fails under font200 on field-log [auto:variant:field-log:font200] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under font200
- [ ] [Major] Tour fails under landscape on field-log [auto:variant:field-log:landscape] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under landscape
- [ ] [Critical] field-log: install failed [auto:install:handoff:field-log] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: APK installs on the emulator - actual: adb: failed to install qa-out/apk/field-log-debug.apk: Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.kurtz.homerecord.fieldlog signatures do not match newer version; ignoring!]  
- [ ] [Critical] field-log: install-test failed [auto:install-test:handoff:field-log] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: APK installs on the emulator - actual: 
- [ ] [Major] Flow H02-draw-plan fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H02-draw-plan] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: walkthrough step H02 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02-draw-plan.log
- [ ] [Major] Flow H02b-shape-doors-stairs fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H02b-shape-doors-stairs] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: walkthrough step H02b passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02b-shape-doors-stairs.log
- [ ] [Major] Flow H03-blueprint-room fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H03-blueprint-room] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: walkthrough step H03 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H03-blueprint-room.log
- [ ] [Major] Flow H15-sign-in fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H15-sign-in] - steps: automated, see qa-results branch latest/ for commit 82537dd - expected: walkthrough step H15 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H15-sign-in.log

Still open from earlier runs: [auto:coldstart:field-log:field-log], [auto:instrumented:field-log:field-log], [auto:flow:field-log:F01-welcome], [auto:flow:field-log:F02-invite-join], [auto:logcat:field-log:com.kurtz.homerecord.fieldlog], [auto:coldstart:handoff:field-log], [auto:coldstart:handoff:house-manual], [auto:instrumented:handoff:field-log], [auto:instrumented:handoff:house-manual], [auto:jank:handoff:house-manual], [auto:logcat:handoff:com.kurtz.homerecord.fieldlog], [auto:logcat:handoff:com.kurtz.homerecord.housemanual], [auto:coldstart:house-manual:house-manual], [auto:instrumented:house-manual:house-manual], [auto:jank:house-manual:house-manual]

Walkthrough steps exercised by flows: field-log F01 Fail, field-log F02 Fail, house-manual H01 Pass, house-manual H02 Fail, house-manual H02b Fail, house-manual H03 Fail, house-manual H15 Fail

Not built yet or skipped: field-log seed:field-log (no debug seed receiver yet (PLAN Q005)); field-log jank:field-log (gfxinfo reported no frames); handoff seed:field-log (no debug seed receiver yet (PLAN Q005)); handoff seed:house-manual (no debug seed receiver yet (PLAN Q005)); handoff flows (no flows in qa/flows/handoff yet (PLAN Q004)); handoff variants (no _tour.yaml for handoff); handoff jank:field-log (gfxinfo reported no frames); house-manual seed:house-manual (no debug seed receiver yet (PLAN Q005))

Tested: 57 device stages, 233 unit tests, 51 backend tests, coverage 67/67 ACs. Passed: 23. New issues: 11. Critical: 4. Product gaps: n/a (judge entry follows).
