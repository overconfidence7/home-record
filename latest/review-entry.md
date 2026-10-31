## QA auto 2026-10-31 (commit c1240620)

Automated suite run at 2026-10-31 17:09 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Major] T044a is ticked but these acceptance criteria have no test or flow: AC-F34, AC-F36 [auto:coverage:T044a] - steps: automated, see qa-results branch latest/ for commit c1240620 - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found
- [ ] [Major] Instrumented tests fail on field-log (regression of a fixed item) [auto:instrumented:field-log:field-log] - steps: automated, see qa-results branch latest/ for commit c1240620 - expected: all acceptance tests and the screen crawler pass - actual: 1 failing of 23 (qa-results latest/field-log/instrumented/field-log.txt)
- [ ] [Major] Flow F09-daily-log fails on field-log [auto:flow:field-log:F09-daily-log] - steps: automated, see qa-results branch latest/ for commit c1240620 - expected: walkthrough step F09 passes (qa/product-walkthrough.md) - actual: see qa-results latest/field-log/maestro/F09-daily-log.log - screenshot: qa-results:latest/field-log/screenshots/flow-F09-daily-log-F09-daily-log.png

Walkthrough steps exercised by flows: field-log F01 Pass, field-log F02 Pass, field-log F03 Pass, field-log F04 Pass, field-log F04b Pass, field-log F04c Pass, field-log F06 Pass, field-log F07 Pass, field-log F07b Pass, field-log F07c Pass, field-log F08 Pass, field-log F08b Pass, field-log F09 Fail, field-log F15 Pass, house-manual H01 Pass, house-manual H01b Pass, house-manual H02 Pass, house-manual H02b Pass, house-manual H03 Pass, house-manual H04 Pass, house-manual H06 Pass, house-manual H17 Pass, house-manual H19 Pass

Not built yet or skipped: handoff flows (no flows in qa/flows/handoff yet (PLAN Q004); handoff job not run)

Tested: 54 device stages, 1017 unit tests, 140 backend tests, coverage 245/247 ACs. Passed: 52. New issues: 3. Critical: 0. Product gaps: n/a (judge entry follows).
