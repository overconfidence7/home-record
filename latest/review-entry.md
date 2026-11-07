## QA auto 2026-11-07 (commit 86d14c0b)

Automated suite run at 2026-11-07 23:03 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] Instrumented tests fail on field-log (regression of a fixed item) [auto:instrumented:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit 86d14c0b - expected: all acceptance tests and the screen crawler pass - actual: 1 failing of 27 (qa-results latest/field-log/instrumented/field-log.txt)
- [ ] [Major] Instrumented tests fail on house-manual (regression of a fixed item) [auto:instrumented:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit 86d14c0b - expected: all acceptance tests and the screen crawler pass - actual: 1 failing of 22 (qa-results latest/house-manual/instrumented/house-manual.txt)

Still open from earlier runs: [auto:flow:field-log:F18-more], [auto:flow:house-manual:H07-item-page], [auto:flow:house-manual:H12-breaker-panel], [auto:flow:house-manual:H17b-second-phone], [auto:flow:house-manual:H18b-claim-link], [auto:flow:house-manual:x-more], [auto:flow:house-manual:x-share-viewer], [auto:flow:house-manual:x-share]

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Pass, field-log F03 Pass, field-log F04 Pass, field-log F04b Pass, field-log F04c Pass, field-log F06 Pass, field-log F07 Pass, field-log F07b Pass, field-log F07c Pass, field-log F08 Pass, field-log F08b Pass, field-log F09 Pass, field-log F09b Pass, field-log F10 Pass, field-log F10b Pass, field-log F10c Pass, field-log F10d Pass, field-log F10e Pass, field-log F11 Pass, field-log F11b Pass, field-log F11c Pass, field-log F12 Pass, field-log F13 Pass, field-log F14 Pass, field-log F15 Pass, field-log F16 Pass, field-log F17 Pass, field-log F18 Fail, house-manual H01 Pass, house-manual H01b Pass, house-manual H02 Pass, house-manual H02b Pass, house-manual H03 Pass, house-manual H04 Pass, house-manual H05 Pass, house-manual H06 Pass, house-manual H07 Fail, house-manual H08 Pass, house-manual H09 Pass, house-manual H10 Pass, house-manual H11 Pass, house-manual H12 Fail, house-manual H13 Pass, house-manual H14 Pass, house-manual H15 Pass, house-manual H16 Pass, house-manual H17 Pass, house-manual H17b Fail, house-manual H18 Pass, house-manual H18b Fail, house-manual H19 Pass

Not built yet or skipped: handoff variants (no _tour.yaml for handoff)

Tested: 102 device stages, 2261 unit tests, 222 backend tests, coverage 495/495 ACs. Passed: 91. New issues: 2. Critical: 0. Product gaps: n/a (judge entry follows).
