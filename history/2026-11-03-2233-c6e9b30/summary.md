# QA suite summary
Commit `c6e9b307`, 2026-11-03 22:33 UTC

- Unit tests: 0 (0 failing)
- Backend tests: n/a (0 failing)
- Coverage gate: {'acs_claimed': None, 'acs_missing': None, 'ticked_tasks': None}
- Device stages: 0/0 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |

## Findings

- [ ] [Critical] device suite produced no results for field-log (regression of a fixed item) [auto:device:field-log:no-results] - steps: automated, see qa-results branch latest/ for commit c6e9b307 - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run
- [ ] [Critical] device suite produced no results for house-manual (regression of a fixed item) [auto:device:house-manual:no-results] - steps: automated, see qa-results branch latest/ for commit c6e9b307 - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run

## REVIEW.md entry

## QA auto 2026-11-03 (commit c6e9b307)

Automated suite run at 2026-11-03 22:33 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Critical] device suite produced no results for field-log (regression of a fixed item) [auto:device:field-log:no-results] - steps: automated, see qa-results branch latest/ for commit c6e9b307 - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run
- [ ] [Critical] device suite produced no results for house-manual (regression of a fixed item) [auto:device:house-manual:no-results] - steps: automated, see qa-results branch latest/ for commit c6e9b307 - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run

Not built yet or skipped: handoff flows (no flows in qa/flows/handoff yet (PLAN Q004); handoff job not run)

Tested: 0 device stages, 0 unit tests, 0 backend tests, coverage 0/0 ACs. Passed: 0. New issues: 2. Critical: 2. Product gaps: n/a (judge entry follows).
