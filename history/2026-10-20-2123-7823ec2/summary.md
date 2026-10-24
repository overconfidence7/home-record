# QA suite summary
Commit `7823ec2`, 2026-10-20 21:23 UTC

- Unit tests: 230 (0 failing)
- Backend tests: 51 (0 failing)
- Coverage gate: {'acs_claimed': 49, 'acs_missing': 0, 'ticked_tasks': 20}
- Device stages: 0/0 passed

## Stages

| App | Stage | Status | Details |
| --- | --- | --- | --- |

## Findings

- [ ] [Critical] device suite produced no results for field-log [auto:device:field-log:no-results] - steps: automated, see qa-results branch latest/ for commit 7823ec2 - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run
- [ ] [Critical] device suite produced no results for handoff [auto:device:handoff:no-results] - steps: automated, see qa-results branch latest/ for commit 7823ec2 - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run
- [ ] [Critical] device suite produced no results for house-manual [auto:device:house-manual:no-results] - steps: automated, see qa-results branch latest/ for commit 7823ec2 - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run

## REVIEW.md entry

## QA auto 2026-10-20 (commit 7823ec2)

Automated suite run at 2026-10-20 21:23 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [ ] [Critical] device suite produced no results for field-log [auto:device:field-log:no-results] - steps: automated, see qa-results branch latest/ for commit 7823ec2 - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run
- [ ] [Critical] device suite produced no results for handoff [auto:device:handoff:no-results] - steps: automated, see qa-results branch latest/ for commit 7823ec2 - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run
- [ ] [Critical] device suite produced no results for house-manual [auto:device:house-manual:no-results] - steps: automated, see qa-results branch latest/ for commit 7823ec2 - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run

Tested: 0 device stages, 230 unit tests, 51 backend tests, coverage 49/49 ACs. Passed: 0. New issues: 3. Critical: 3. Product gaps: n/a (judge entry follows).
