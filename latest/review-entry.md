## QA auto 2026-10-24 (commit 2f19f4e)

Automated suite run at 2026-10-24 10:26 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] Flow H05-contacts fails on house-manual [auto:flow:house-manual:H05-contacts] - steps: automated, see qa-results branch latest/ for commit 2f19f4e - expected: walkthrough step H05 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H05-contacts.log
- [ ] [Major] Flow H06-documents fails on house-manual [auto:flow:house-manual:H06-documents] - steps: automated, see qa-results branch latest/ for commit 2f19f4e - expected: walkthrough step H06 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H06-documents.log

Still open from earlier runs: [auto:coldstart:field-log:field-log], [auto:flow:field-log:F01-welcome], [auto:flow:field-log:F02-invite-join], [auto:flow:field-log:F03-jobs], [auto:jank:field-log:field-log], [auto:coldstart:handoff:field-log], [auto:coldstart:handoff:house-manual], [auto:instrumented:handoff:house-manual], [auto:jank:handoff:field-log], [auto:jank:handoff:house-manual], [auto:coldstart:house-manual:house-manual], [auto:instrumented:house-manual:house-manual], [auto:flow:house-manual:H02b-shape-doors-stairs], [auto:flow:house-manual:H04-add-item], [auto:flow:house-manual:H15-sign-in], [auto:jank:house-manual:house-manual]

Walkthrough steps exercised by flows: field-log F01 Fail, field-log F02 Fail, field-log F03 Fail, house-manual H01 Pass, house-manual H02 Pass, house-manual H02b Fail, house-manual H03 Pass, house-manual H04 Fail, house-manual H05 Fail, house-manual H06 Fail, house-manual H15 Fail

Not built yet or skipped: handoff flows (no flows in qa/flows/handoff yet (PLAN Q004)); handoff variants (no _tour.yaml for handoff)

Tested: 61 device stages, 365 unit tests, 64 backend tests, coverage 90/90 ACs. Passed: 41. New issues: 2. Critical: 0. Product gaps: n/a (judge entry follows).
