# QA suite summary
Commit `cc6c4af0`, 2026-11-07 05:20 UTC

- Unit tests: 2210 (0 failing)
- Backend tests: 218 (0 failing)
- Coverage gate: {'acs_claimed': 492, 'acs_missing': 0, 'ticked_tasks': 296}
- Device stages: 76/102 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | pass | debug apk |
| field-log | install-test:field-log | pass | androidTest apk |
| field-log | backend:field-log | pass | GET /health answered |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | seed:field-log | pass | regression scenario seeded |
| field-log | instrumented:field-log | pass | 27 tests |
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
| field-log | flow:F09-daily-log | fail | see maestro/F09-daily-log.log |
| field-log | flow:F09b-log-send | pass | ok |
| field-log | flow:F10-house-3d | pass | ok |
| field-log | flow:F10b-behind-wall | pass | ok |
| field-log | flow:F10c-installed-item | pass | ok |
| field-log | flow:F10d-handoff-export | pass | ok |
| field-log | flow:F10e-handoff-send | pass | ok |
| field-log | flow:F11-tasks | pass | ok |
| field-log | flow:F11b-task-pin | pass | ok |
| field-log | flow:F11c-file-pin | pass | ok |
| field-log | flow:F12-change-order | fail | see maestro/F12-change-order.log |
| field-log | flow:F13-schedule | pass | ok |
| field-log | flow:F14-materials | fail | see maestro/F14-materials.log |
| field-log | flow:F15-reports | pass | ok |
| field-log | flow:F16-offline | pass | ok |
| field-log | flow:F17-trial-expiry | pass | ok |
| field-log | flow:F18-more | fail | see maestro/F18-more.log |
| field-log | flow:x-seeded-company | pass | ok |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | fail | tour ran but shots are portrait: tour-landscape-clock.png 1080x2400; tour-landscape-company.png 1080x2400; tour-landscape-jobs.png 1080x2400; tour-landscape-launch.png 1080x2400; tour-landscape-more.png 1080x2400; tour-landscape-schedule.png 1080x2400; tour-landscape-team.png 1080x2400; tour-landscape-today.png 1080x2400 |
| field-log | monkey:field-log | pass | 1500 events, no crash; foreground after: com.google.android.apps.nexuslauncher |
| field-log | jank:field-log | skip | not measurable: 98 frames rendered while flinging the 200-row list (need 100) |
| field-log | release:field-log | pass | R8 build launches (2368 ms) |
| field-log | coldstart:field-log | pass | median 972 ms (release, profile compiled) |
| field-log | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | install:field-log | pass | debug apk |
| handoff | install-test:field-log | pass | androidTest apk |
| handoff | install:house-manual | pass | debug apk |
| handoff | install-test:house-manual | pass | androidTest apk |
| handoff | backend:handoff | pass | GET /health answered |
| handoff | permissions | pass | granted where declared |
| handoff | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| handoff | seed:field-log | pass | regression scenario seeded |
| handoff | seed:house-manual | pass | regression scenario seeded |
| handoff | flow:X02-claim-link | fail | see maestro/X02-claim-link.log |
| handoff | variants | skip | no _tour.yaml for handoff |
| handoff | logcat:com.kurtz.homerecord.fieldlog | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |
| handoff | logcat:com.kurtz.homerecord.housemanual | fail | 1 crashes, 0 ANRs, 0 StrictMode violations |
| house-manual | install:house-manual | pass | debug apk |
| house-manual | install-test:house-manual | pass | androidTest apk |
| house-manual | backend:house-manual | pass | GET /health answered |
| house-manual | permissions | pass | granted where declared |
| house-manual | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| house-manual | seed:house-manual | pass | regression scenario seeded |
| house-manual | instrumented:house-manual | fail | 1 failing of 22 |
| house-manual | flow:H01-onboarding | pass | ok |
| house-manual | flow:H01b-home | pass | ok |
| house-manual | flow:H02-draw-plan | pass | ok |
| house-manual | flow:H02b-shape-doors-stairs | pass | ok |
| house-manual | flow:H03-blueprint-room | pass | ok |
| house-manual | flow:H04-add-item | pass | ok |
| house-manual | flow:H05-add-items | fail | see maestro/H05-add-items.log |
| house-manual | flow:H06-items | pass | ok |
| house-manual | flow:H07-item-page | fail | see maestro/H07-item-page.log |
| house-manual | flow:H08-offline-item | pass | ok |
| house-manual | flow:H09-wall-photo | pass | ok |
| house-manual | flow:H10-3d | fail | see maestro/H10-3d.log |
| house-manual | flow:H11-overlay-pipe | pass | ok |
| house-manual | flow:H12-breaker-panel | fail | see maestro/H12-breaker-panel.log |
| house-manual | flow:H13-emergency | fail | see maestro/H13-emergency.log |
| house-manual | flow:H14-reminders | fail | see maestro/H14-reminders.log |
| house-manual | flow:H15-home-report | pass | ok |
| house-manual | flow:H16-backup-restore | fail | see maestro/H16-backup-restore.log |
| house-manual | flow:H17-sign-in | fail | see maestro/H17-sign-in.log |
| house-manual | flow:H17b-second-phone | fail | see maestro/H17b-second-phone.log |
| house-manual | flow:H18-import-handoff | pass | ok |
| house-manual | flow:H18b-claim-link | fail | see maestro/H18b-claim-link.log |
| house-manual | flow:H19-paywall | fail | see maestro/H19-paywall.log |
| house-manual | flow:x-contacts | pass | ok |
| house-manual | flow:x-documents | pass | ok |
| house-manual | flow:x-more | fail | see maestro/x-more.log |
| house-manual | flow:x-share-viewer | fail | see maestro/x-share-viewer.log |
| house-manual | flow:x-share | fail | see maestro/x-share.log |
| house-manual | 3d:house-manual | fail | flow failed (maestro/3d-perf.log); 3d/orbit.mp4 recorded; first frame 4485 ms; 6.7 fps |
| house-manual | variant:dark | pass | tour ok |
| house-manual | variant:font200 | fail | tour failed under font200 |
| house-manual | variant:landscape | pass | tour ok, shots landscape |
| house-manual | monkey:house-manual | pass | 1500 events, no crash; foreground after: com.google.android.apps.nexuslauncher |
| house-manual | jank:house-manual | pass | 6% of 109 frames ran the UI thread past the frame budget (software renderer, overall 93% janky of 105; 200-row list, benchmark build) |
| house-manual | release:house-manual | pass | R8 build launches (1652 ms) |
| house-manual | coldstart:house-manual | pass | median 759 ms (release, profile compiled) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Major] Flow F09-daily-log fails on field-log (regression of a fixed item) [auto:flow:field-log:F09-daily-log] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step F09 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F09-daily-log.log - screenshot: qa-results:latest/field-log/screenshots/flow-F09-daily-log-step-080-assertCondition-Township_inspector.png
- [ ] [Major] Flow F12-change-order fails on field-log (regression of a fixed item) [auto:flow:field-log:F12-change-order] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step F12 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F12-change-order.log - screenshot: qa-results:latest/field-log/screenshots/flow-F12-change-order-F12-change-order-link-sheet.png
- [ ] [Major] Flow F14-materials fails on field-log [auto:flow:field-log:F14-materials] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step F14 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F14-materials.log - screenshot: qa-results:latest/field-log/screenshots/flow-F14-materials-F14-receipt-form.png
- [ ] [Major] Flow F18-more fails on field-log (regression of a fixed item) [auto:flow:field-log:F18-more] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step F18 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F18-more.log - screenshot: qa-results:latest/field-log/screenshots/flow-F18-more-F18-clients.png
- [ ] [Major] Tour fails under landscape on field-log (regression of a fixed item) [auto:variant:field-log:landscape] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour ran but shots are portrait: tour-landscape-clock.png 1080x2400; tour-landscape-company.png 1080x2400; tour-landscape-jobs.png 1080x2400; tour-landscape-launch.png 1080x2400; tour-landscape-more.png 1080x2400; tour-landscape-schedule.png 1080x2400; tour-landscape-team.png 1080x2400; tour-landscape-today.png 1080x2400 - screenshot: qa-results:latest/field-log/screenshots/tour-landscape-clock.png
- [ ] [Major] Flow X02-claim-link fails on handoff [auto:flow:handoff:X02-claim-link] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/handoff/maestro/X02-claim-link.log - screenshot: qa-results:latest/handoff/screenshots/flow-X02-claim-link-X02-handoff-sent.png
- [ ] [Critical] logcat for com.kurtz.homerecord.housemanual: 1 crashes, 0 ANRs, 0 StrictMode violations (regression of a fixed item) [auto:logcat:handoff:com.kurtz.homerecord.housemanual] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: no crashes or ANRs; StrictMode clean (S7) - actual: see qa-results latest/handoff/logs/crashes.txt and strictmode.txt
- [ ] [Major] Instrumented tests fail on house-manual (regression of a fixed item) [auto:instrumented:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: all acceptance tests and the screen crawler pass - actual: 1 failing of 22 (qa-results latest/house-manual/instrumented/house-manual.txt)
- [ ] [Major] Flow H05-add-items fails on house-manual [auto:flow:house-manual:H05-add-items] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H05 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H05-add-items.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H05-add-items-H01-home-after-onboarding.png
- [ ] [Major] Flow H07-item-page fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H07-item-page] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H07 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H07-item-page.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H07-item-page-H07-dragged.png
- [ ] [Major] Flow H10-3d fails on house-manual [auto:flow:house-manual:H10-3d] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H10 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H10-3d.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H10-3d-step-008-tapOnElement-Kitchen.png
- [ ] [Major] Flow H12-breaker-panel fails on house-manual [auto:flow:house-manual:H12-breaker-panel] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H12 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H12-breaker-panel.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H12-breaker-panel-H12-breaker-labeled.png
- [ ] [Major] Flow H13-emergency fails on house-manual [auto:flow:house-manual:H13-emergency] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H13 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H13-emergency.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H13-emergency-H13-emergency.png
- [ ] [Major] Flow H14-reminders fails on house-manual [auto:flow:house-manual:H14-reminders] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H14 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H14-reminders.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H14-reminders-step-013-assertCondition-Overdue_since.png
- [ ] [Major] Flow H16-backup-restore fails on house-manual [auto:flow:house-manual:H16-backup-restore] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H16 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H16-backup-restore.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H16-backup-restore-H16-backup-share.png
- [ ] [Major] Flow H17-sign-in fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H17-sign-in] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H17 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H17-sign-in.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H17-sign-in-H17-free-sign-in.png
- [ ] [Major] Flow H17b-second-phone fails on house-manual [auto:flow:house-manual:H17b-second-phone] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H17b passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H17b-second-phone.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H17b-second-phone-step-037-assertCondition-hm_cloud_restore_intro.png
- [ ] [Major] Flow H18b-claim-link fails on house-manual [auto:flow:house-manual:H18b-claim-link] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H18b passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H18b-claim-link.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H18b-claim-link-H18b-claim-not-found.png
- [ ] [Major] Flow H19-paywall fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H19-paywall] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H19 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H19-paywall.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H19-paywall-H19-paywall.png
- [ ] [Major] Flow x-more fails on house-manual [auto:flow:house-manual:x-more] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/house-manual/maestro/x-more.log - screenshot: qa-results:latest/house-manual/screenshots/flow-x-more-step-021-tapOnElement-hm_more_units_metric.png
- [ ] [Major] Flow x-share-viewer fails on house-manual [auto:flow:house-manual:x-share-viewer] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/house-manual/maestro/x-share-viewer.log - screenshot: qa-results:latest/house-manual/screenshots/flow-x-share-viewer-step-038-assertCondition-Send_the_invite.png
- [ ] [Major] Flow x-share fails on house-manual [auto:flow:house-manual:x-share] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/house-manual/maestro/x-share.log - screenshot: qa-results:latest/house-manual/screenshots/flow-x-share-step-041-assertCondition-Send_the_invite.png
- [ ] [Major] 3d:house-manual failed on house-manual [auto:3d:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: stage passes - actual: flow failed (maestro/3d-perf.log); 3d/orbit.mp4 recorded; first frame 4485 ms; 6.7 fps
- [ ] [Major] Tour fails under font200 on house-manual (regression of a fixed item) [auto:variant:house-manual:font200] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under font200 - screenshot: qa-results:latest/house-manual/screenshots/tour-font200-home.png

## REVIEW.md entry

## QA auto 2026-11-07 (commit cc6c4af0)

Automated suite run at 2026-11-07 05:20 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] Flow F09-daily-log fails on field-log (regression of a fixed item) [auto:flow:field-log:F09-daily-log] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step F09 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F09-daily-log.log - screenshot: qa-results:latest/field-log/screenshots/flow-F09-daily-log-step-080-assertCondition-Township_inspector.png
- [ ] [Major] Flow F12-change-order fails on field-log (regression of a fixed item) [auto:flow:field-log:F12-change-order] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step F12 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F12-change-order.log - screenshot: qa-results:latest/field-log/screenshots/flow-F12-change-order-F12-change-order-link-sheet.png
- [ ] [Major] Flow F14-materials fails on field-log [auto:flow:field-log:F14-materials] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step F14 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F14-materials.log - screenshot: qa-results:latest/field-log/screenshots/flow-F14-materials-F14-receipt-form.png
- [ ] [Major] Flow F18-more fails on field-log (regression of a fixed item) [auto:flow:field-log:F18-more] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step F18 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F18-more.log - screenshot: qa-results:latest/field-log/screenshots/flow-F18-more-F18-clients.png
- [ ] [Major] Tour fails under landscape on field-log (regression of a fixed item) [auto:variant:field-log:landscape] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour ran but shots are portrait: tour-landscape-clock.png 1080x2400; tour-landscape-company.png 1080x2400; tour-landscape-jobs.png 1080x2400; tour-landscape-launch.png 1080x2400; tour-landscape-more.png 1080x2400; tour-landscape-schedule.png 1080x2400; tour-landscape-team.png 1080x2400; tour-landscape-today.png 1080x2400 - screenshot: qa-results:latest/field-log/screenshots/tour-landscape-clock.png
- [ ] [Major] Flow X02-claim-link fails on handoff [auto:flow:handoff:X02-claim-link] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/handoff/maestro/X02-claim-link.log - screenshot: qa-results:latest/handoff/screenshots/flow-X02-claim-link-X02-handoff-sent.png
- [ ] [Critical] logcat for com.kurtz.homerecord.housemanual: 1 crashes, 0 ANRs, 0 StrictMode violations (regression of a fixed item) [auto:logcat:handoff:com.kurtz.homerecord.housemanual] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: no crashes or ANRs; StrictMode clean (S7) - actual: see qa-results latest/handoff/logs/crashes.txt and strictmode.txt
- [ ] [Major] Instrumented tests fail on house-manual (regression of a fixed item) [auto:instrumented:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: all acceptance tests and the screen crawler pass - actual: 1 failing of 22 (qa-results latest/house-manual/instrumented/house-manual.txt)
- [ ] [Major] Flow H05-add-items fails on house-manual [auto:flow:house-manual:H05-add-items] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H05 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H05-add-items.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H05-add-items-H01-home-after-onboarding.png
- [ ] [Major] Flow H07-item-page fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H07-item-page] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H07 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H07-item-page.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H07-item-page-H07-dragged.png
- [ ] [Major] Flow H10-3d fails on house-manual [auto:flow:house-manual:H10-3d] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H10 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H10-3d.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H10-3d-step-008-tapOnElement-Kitchen.png
- [ ] [Major] Flow H12-breaker-panel fails on house-manual [auto:flow:house-manual:H12-breaker-panel] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H12 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H12-breaker-panel.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H12-breaker-panel-H12-breaker-labeled.png
- [ ] [Major] Flow H13-emergency fails on house-manual [auto:flow:house-manual:H13-emergency] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H13 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H13-emergency.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H13-emergency-H13-emergency.png
- [ ] [Major] Flow H14-reminders fails on house-manual [auto:flow:house-manual:H14-reminders] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H14 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H14-reminders.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H14-reminders-step-013-assertCondition-Overdue_since.png
- [ ] [Major] Flow H16-backup-restore fails on house-manual [auto:flow:house-manual:H16-backup-restore] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H16 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H16-backup-restore.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H16-backup-restore-H16-backup-share.png
- [ ] [Major] Flow H17-sign-in fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H17-sign-in] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H17 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H17-sign-in.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H17-sign-in-H17-free-sign-in.png
- [ ] [Major] Flow H17b-second-phone fails on house-manual [auto:flow:house-manual:H17b-second-phone] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H17b passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H17b-second-phone.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H17b-second-phone-step-037-assertCondition-hm_cloud_restore_intro.png
- [ ] [Major] Flow H18b-claim-link fails on house-manual [auto:flow:house-manual:H18b-claim-link] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H18b passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H18b-claim-link.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H18b-claim-link-H18b-claim-not-found.png
- [ ] [Major] Flow H19-paywall fails on house-manual (regression of a fixed item) [auto:flow:house-manual:H19-paywall] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: walkthrough step H19 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H19-paywall.log - screenshot: qa-results:latest/house-manual/screenshots/flow-H19-paywall-H19-paywall.png
- [ ] [Major] Flow x-more fails on house-manual [auto:flow:house-manual:x-more] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/house-manual/maestro/x-more.log - screenshot: qa-results:latest/house-manual/screenshots/flow-x-more-step-021-tapOnElement-hm_more_units_metric.png
- [ ] [Major] Flow x-share-viewer fails on house-manual [auto:flow:house-manual:x-share-viewer] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/house-manual/maestro/x-share-viewer.log - screenshot: qa-results:latest/house-manual/screenshots/flow-x-share-viewer-step-038-assertCondition-Send_the_invite.png
- [ ] [Major] Flow x-share fails on house-manual [auto:flow:house-manual:x-share] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: the flow passes (not a walkthrough step) - actual: see qa-results latest/house-manual/maestro/x-share.log - screenshot: qa-results:latest/house-manual/screenshots/flow-x-share-step-041-assertCondition-Send_the_invite.png
- [ ] [Major] 3d:house-manual failed on house-manual [auto:3d:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: stage passes - actual: flow failed (maestro/3d-perf.log); 3d/orbit.mp4 recorded; first frame 4485 ms; 6.7 fps
- [ ] [Major] Tour fails under font200 on house-manual (regression of a fixed item) [auto:variant:house-manual:font200] - steps: automated, see qa-results branch latest/ for commit cc6c4af0 - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under font200 - screenshot: qa-results:latest/house-manual/screenshots/tour-font200-home.png

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Pass, field-log F03 Pass, field-log F04 Pass, field-log F04b Pass, field-log F04c Pass, field-log F06 Pass, field-log F07 Pass, field-log F07b Pass, field-log F07c Pass, field-log F08 Pass, field-log F08b Pass, field-log F09 Fail, field-log F09b Pass, field-log F10 Pass, field-log F10b Pass, field-log F10c Pass, field-log F10d Pass, field-log F10e Pass, field-log F11 Pass, field-log F11b Pass, field-log F11c Pass, field-log F12 Fail, field-log F13 Pass, field-log F14 Fail, field-log F15 Pass, field-log F16 Pass, field-log F17 Pass, field-log F18 Fail, house-manual H01 Pass, house-manual H01b Pass, house-manual H02 Pass, house-manual H02b Pass, house-manual H03 Pass, house-manual H04 Pass, house-manual H05 Fail, house-manual H06 Pass, house-manual H07 Fail, house-manual H08 Pass, house-manual H09 Pass, house-manual H10 Fail, house-manual H11 Pass, house-manual H12 Fail, house-manual H13 Fail, house-manual H14 Fail, house-manual H15 Pass, house-manual H16 Fail, house-manual H17 Fail, house-manual H17b Fail, house-manual H18 Pass, house-manual H18b Fail, house-manual H19 Fail

Not built yet or skipped: field-log jank:field-log (not measurable: 98 frames rendered while flinging the 200-row list (need 100)); handoff variants (no _tour.yaml for handoff)

Tested: 102 device stages, 2210 unit tests, 218 backend tests, coverage 492/492 ACs. Passed: 76. New issues: 24. Critical: 1. Product gaps: n/a (judge entry follows).
