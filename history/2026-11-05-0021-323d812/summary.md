# QA suite summary
Commit `323d8128`, 2026-11-05 00:21 UTC

- Unit tests: 1767 (0 failing)
- Backend tests: 177 (0 failing)
- Coverage gate: {'acs_claimed': 379, 'acs_missing': 0, 'ticked_tasks': 215}
- Device stages: 60/67 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |
| field-log | install:field-log | pass | debug apk |
| field-log | install-test:field-log | pass | androidTest apk |
| field-log | permissions | pass | granted where declared |
| field-log | fixtures | pass | pushed to /sdcard/Download/qa-fixtures |
| field-log | seed:field-log | pass | regression scenario seeded |
| field-log | instrumented:field-log | fail | 1 failing of 27 |
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
| field-log | flow:F09b-log-send | fail | see maestro/F09b-log-send.log |
| field-log | flow:F10-house-3d | pass | ok |
| field-log | flow:F10b-behind-wall | pass | ok |
| field-log | flow:F11-tasks | pass | ok |
| field-log | flow:F11b-task-pin | pass | ok |
| field-log | flow:F11c-file-pin | pass | ok |
| field-log | flow:F12-change-order | fail | see maestro/F12-change-order.log |
| field-log | flow:F13-schedule | fail | see maestro/F13-schedule.log |
| field-log | flow:F15-reports | fail | see maestro/F15-reports.log |
| field-log | flow:F16-offline | pass | ok |
| field-log | flow:F18-more | fail | see maestro/F18-more.log |
| field-log | flow:x-seeded-company | pass | ok |
| field-log | variant:dark | pass | tour ok |
| field-log | variant:font200 | pass | tour ok |
| field-log | variant:landscape | pass | tour ok |
| field-log | monkey:field-log | pass | 1500 events, no crash |
| field-log | jank:field-log | pass | 8% of 112 frames ran the UI thread past the frame budget (software renderer, overall 88% janky of 112; 200-row list, benchmark build) |
| field-log | release:field-log | pass | R8 build launches (1646 ms) |
| field-log | coldstart:field-log | pass | median 843 ms (release, profile compiled) |
| field-log | logcat:com.kurtz.homerecord.fieldlog | fail | 1 crashes, 0 ANRs, 0 StrictMode violations |
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
| house-manual | jank:house-manual | pass | 2% of 110 frames ran the UI thread past the frame budget (software renderer, overall 92% janky of 109; 200-row list, benchmark build) |
| house-manual | release:house-manual | pass | R8 build launches (1348 ms) |
| house-manual | coldstart:house-manual | pass | median 732 ms (release, profile compiled) |
| house-manual | logcat:com.kurtz.homerecord.housemanual | pass | 0 crashes, 0 ANRs, 0 StrictMode violations |

## Findings

- [ ] [Major] Instrumented tests fail on field-log (regression of a fixed item) [auto:instrumented:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit 323d8128 - expected: all acceptance tests and the screen crawler pass - actual: 1 failing of 27 (qa-results latest/field-log/instrumented/field-log.txt)
- [ ] [Major] Flow F09b-log-send fails on field-log (regression of a fixed item) [auto:flow:field-log:F09b-log-send] - steps: automated, see qa-results branch latest/ for commit 323d8128 - expected: walkthrough step F09b passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F09b-log-send.log - screenshot: qa-results:latest/field-log/screenshots/flow-F09b-log-send-F09b-log-pdf.png
- [ ] [Major] Flow F12-change-order fails on field-log (regression of a fixed item) [auto:flow:field-log:F12-change-order] - steps: automated, see qa-results branch latest/ for commit 323d8128 - expected: walkthrough step F12 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F12-change-order.log - screenshot: qa-results:latest/field-log/screenshots/flow-F12-change-order-step-070-tapOnElement-fl_co_form_line_0_price.png
- [ ] [Major] Flow F13-schedule fails on field-log (regression of a fixed item) [auto:flow:field-log:F13-schedule] - steps: automated, see qa-results branch latest/ for commit 323d8128 - expected: walkthrough step F13 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F13-schedule.log - screenshot: qa-results:latest/field-log/screenshots/flow-F13-schedule-step-048-assertCondition-fl_schedule_title.png
- [ ] [Major] Flow F15-reports fails on field-log [auto:flow:field-log:F15-reports] - steps: automated, see qa-results branch latest/ for commit 323d8128 - expected: walkthrough step F15 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F15-reports.log - screenshot: qa-results:latest/field-log/screenshots/flow-F15-reports-F15-job-summaries.png
- [ ] [Major] Flow F18-more fails on field-log (regression of a fixed item) [auto:flow:field-log:F18-more] - steps: automated, see qa-results branch latest/ for commit 323d8128 - expected: walkthrough step F18 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F18-more.log - screenshot: qa-results:latest/field-log/screenshots/flow-F18-more-F18-clients.png

## REVIEW.md entry

## QA auto 2026-11-05 (commit 323d8128)

Automated suite run at 2026-11-05 00:21 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] Instrumented tests fail on field-log (regression of a fixed item) [auto:instrumented:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit 323d8128 - expected: all acceptance tests and the screen crawler pass - actual: 1 failing of 27 (qa-results latest/field-log/instrumented/field-log.txt)
- [ ] [Major] Flow F09b-log-send fails on field-log (regression of a fixed item) [auto:flow:field-log:F09b-log-send] - steps: automated, see qa-results branch latest/ for commit 323d8128 - expected: walkthrough step F09b passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F09b-log-send.log - screenshot: qa-results:latest/field-log/screenshots/flow-F09b-log-send-F09b-log-pdf.png
- [ ] [Major] Flow F12-change-order fails on field-log (regression of a fixed item) [auto:flow:field-log:F12-change-order] - steps: automated, see qa-results branch latest/ for commit 323d8128 - expected: walkthrough step F12 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F12-change-order.log - screenshot: qa-results:latest/field-log/screenshots/flow-F12-change-order-step-070-tapOnElement-fl_co_form_line_0_price.png
- [ ] [Major] Flow F13-schedule fails on field-log (regression of a fixed item) [auto:flow:field-log:F13-schedule] - steps: automated, see qa-results branch latest/ for commit 323d8128 - expected: walkthrough step F13 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F13-schedule.log - screenshot: qa-results:latest/field-log/screenshots/flow-F13-schedule-step-048-assertCondition-fl_schedule_title.png
- [ ] [Major] Flow F15-reports fails on field-log [auto:flow:field-log:F15-reports] - steps: automated, see qa-results branch latest/ for commit 323d8128 - expected: walkthrough step F15 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F15-reports.log - screenshot: qa-results:latest/field-log/screenshots/flow-F15-reports-F15-job-summaries.png
- [ ] [Major] Flow F18-more fails on field-log (regression of a fixed item) [auto:flow:field-log:F18-more] - steps: automated, see qa-results branch latest/ for commit 323d8128 - expected: walkthrough step F18 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F18-more.log - screenshot: qa-results:latest/field-log/screenshots/flow-F18-more-F18-clients.png

Still open from earlier runs: [auto:logcat:field-log:com.kurtz.homerecord.fieldlog]

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Pass, field-log F03 Pass, field-log F04 Pass, field-log F04b Pass, field-log F04c Pass, field-log F06 Pass, field-log F07 Pass, field-log F07b Pass, field-log F07c Pass, field-log F08 Pass, field-log F08b Pass, field-log F09 Pass, field-log F09b Fail, field-log F10 Pass, field-log F10b Pass, field-log F11 Pass, field-log F11b Pass, field-log F11c Pass, field-log F12 Fail, field-log F13 Fail, field-log F15 Fail, field-log F16 Pass, field-log F18 Fail, house-manual H01 Pass, house-manual H01b Pass, house-manual H02 Pass, house-manual H02b Pass, house-manual H03 Pass, house-manual H04 Pass, house-manual H06 Pass, house-manual H07 Pass, house-manual H09 Pass, house-manual H11 Pass, house-manual H17 Pass, house-manual H19 Pass

Not built yet or skipped: handoff flows (no flows in qa/flows/handoff yet (PLAN Q004); handoff job not run)

Tested: 67 device stages, 1767 unit tests, 177 backend tests, coverage 379/379 ACs. Passed: 60. New issues: 6. Critical: 0. Product gaps: n/a (judge entry follows).
