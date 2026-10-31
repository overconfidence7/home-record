# QA suite summary
Commit `efef134`, 2026-10-24 20:27 UTC

- Unit tests: 393 (0 failing)
- Backend tests: 73 (0 failing)
- Coverage gate: {'acs_claimed': 99, 'acs_missing': 0, 'ticked_tasks': 38}
- Device stages: 35/63 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | fail | adb: failed to install qa-out/apk/field-log-debug.apk: Failure [INSTALL_FAILED_INVALID_APK: Scanning Failed.: Package /data/app/vmdl1550871531.tmp/base.apk code is missing]   |
| field-log | install-test:field-log | pass | androidTest apk |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | coldstart:field-log | fail | MainActivity did not start |
| field-log | seed:field-log | skip | no debug seed receiver yet (PLAN Q005) |
| field-log | instrumented:field-log | fail | runner did not report (see instrumented/field-log.txt) |
| field-log | flow:F01-welcome | fail | see maestro/F01-welcome.log |
| field-log | flow:F02-invite-join | fail | see maestro/F02-invite-join.log |
| field-log | flow:F03-jobs | fail | see maestro/F03-jobs.log |
| field-log | variant:dark | fail | tour failed under dark |
| field-log | variant:font200 | fail | tour failed under font200 |
| field-log | variant:landscape | fail | tour failed under landscape |
| field-log | jank:field-log | skip | gfxinfo reported no frames |
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | release:field-log | pass | R8 build launches (1581 ms) |
| field-log | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | install:field-log | fail | adb: failed to install qa-out/apk/field-log-debug.apk: Failure [INSTALL_FAILED_INVALID_APK: Scanning Failed.: Package /data/app/vmdl1370483801.tmp/base.apk code is missing]   |
| handoff | install-test:field-log | pass | androidTest apk |
| handoff | install:house-manual | pass | debug apk |
| handoff | install-test:house-manual | pass | androidTest apk |
| handoff | permissions | pass | granted where declared |
| handoff | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| handoff | coldstart:field-log | fail | MainActivity did not start |
| handoff | coldstart:house-manual | fail | median 3868 ms over 1500 ms (AC-U11) |
| handoff | seed:field-log | skip | no debug seed receiver yet (PLAN Q005) |
| handoff | seed:house-manual | pass | regression scenario seeded |
| handoff | instrumented:field-log | fail | runner did not report (see instrumented/field-log.txt) |
| handoff | instrumented:house-manual | fail | 1 failing of 20 |
| handoff | flows | skip | no flows in qa/flows/handoff yet (PLAN Q004) |
| handoff | variants | skip | no _tour.yaml for handoff |
| handoff | jank:field-log | skip | gfxinfo reported no frames |
| handoff | jank:house-manual | fail | 100% janky frames of 8 (AC-U11) |
| handoff | monkey:field-log | pass | 1500 events, no crash |
| handoff | monkey:house-manual | pass | 1500 events, no crash |
| handoff | release:field-log | pass | R8 build launches (2502 ms) |
| handoff | release:house-manual | pass | R8 build launches (2152 ms) |
| handoff | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| house-manual | install:house-manual | pass | debug apk |
| house-manual | install-test:house-manual | pass | androidTest apk |
| house-manual | permissions | pass | granted where declared |
| house-manual | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| house-manual | coldstart:house-manual | fail | median 3782 ms over 1500 ms (AC-U11) |
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
| house-manual | jank:house-manual | fail | 100% janky frames of 10 (AC-U11) |
| house-manual | monkey:house-manual | pass | 1500 events, no crash |
| house-manual | release:house-manual | pass | R8 build launches (1611 ms) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Critical] APK build failed (assembleDebug / assembleDebugAndroidTest / assembleRelease) [auto:apk:build] - steps: automated, see qa-results branch latest/ for commit efef134 - expected: both apps build - actual: see qa-results apk/gradle.log
- [ ] [Critical] field-log: install failed (regression of a fixed item) [auto:install:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit efef134 - expected: APK installs on the emulator - actual: adb: failed to install qa-out/apk/field-log-debug.apk: Failure [INSTALL_FAILED_INVALID_APK: Scanning Failed.: Package /data/app/vmdl1550871531.tmp/base.apk code is missing]  
- [ ] [Critical] field-log: install failed (regression of a fixed item) [auto:install:handoff:field-log] - steps: automated, see qa-results branch latest/ for commit efef134 - expected: APK installs on the emulator - actual: adb: failed to install qa-out/apk/field-log-debug.apk: Failure [INSTALL_FAILED_INVALID_APK: Scanning Failed.: Package /data/app/vmdl1370483801.tmp/base.apk code is missing]  

## REVIEW.md entry

## QA auto 2026-10-24 (commit efef134)

Automated suite run at 2026-10-24 20:27 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Critical] APK build failed (assembleDebug / assembleDebugAndroidTest / assembleRelease) [auto:apk:build] - steps: automated, see qa-results branch latest/ for commit efef134 - expected: both apps build - actual: see qa-results apk/gradle.log
- [ ] [Critical] field-log: install failed (regression of a fixed item) [auto:install:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit efef134 - expected: APK installs on the emulator - actual: adb: failed to install qa-out/apk/field-log-debug.apk: Failure [INSTALL_FAILED_INVALID_APK: Scanning Failed.: Package /data/app/vmdl1550871531.tmp/base.apk code is missing]  
- [ ] [Critical] field-log: install failed (regression of a fixed item) [auto:install:handoff:field-log] - steps: automated, see qa-results branch latest/ for commit efef134 - expected: APK installs on the emulator - actual: adb: failed to install qa-out/apk/field-log-debug.apk: Failure [INSTALL_FAILED_INVALID_APK: Scanning Failed.: Package /data/app/vmdl1370483801.tmp/base.apk code is missing]  

Still open from earlier runs: [auto:coldstart:field-log:field-log], [auto:instrumented:field-log:field-log], [auto:flow:field-log:F01-welcome], [auto:flow:field-log:F02-invite-join], [auto:flow:field-log:F03-jobs], [auto:variant:field-log:dark], [auto:variant:field-log:font200], [auto:variant:field-log:landscape], [auto:coldstart:handoff:field-log], [auto:coldstart:handoff:house-manual], [auto:instrumented:handoff:field-log], [auto:instrumented:handoff:house-manual], [auto:jank:handoff:house-manual], [auto:coldstart:house-manual:house-manual], [auto:instrumented:house-manual:house-manual], [auto:flow:house-manual:H02b-shape-doors-stairs], [auto:flow:house-manual:H04-add-item], [auto:flow:house-manual:H05-contacts], [auto:flow:house-manual:H15-sign-in], [auto:jank:house-manual:house-manual]

Walkthrough steps exercised by flows: field-log F01 Fail, field-log F02 Fail, field-log F03 Fail, house-manual H01 Pass, house-manual H02 Pass, house-manual H02b Fail, house-manual H03 Pass, house-manual H04 Fail, house-manual H05 Fail, house-manual H06 Pass, house-manual H07 Pass, house-manual H15 Fail, house-manual H18 Pass

Not built yet or skipped: field-log seed:field-log (no debug seed receiver yet (PLAN Q005)); field-log jank:field-log (gfxinfo reported no frames); handoff seed:field-log (no debug seed receiver yet (PLAN Q005)); handoff flows (no flows in qa/flows/handoff yet (PLAN Q004)); handoff variants (no _tour.yaml for handoff); handoff jank:field-log (gfxinfo reported no frames)

Tested: 63 device stages, 393 unit tests, 73 backend tests, coverage 99/99 ACs. Passed: 35. New issues: 3. Critical: 3. Product gaps: n/a (judge entry follows).
