# QA review log

Findings from three sources, all in the same bullet format: the automated suite (`## QA auto <date> (commit <sha>)`, four times a day, ids like `[auto:flow:house-manual:H02-draw-plan]`), the QA judge routine (`## QA judge <date> (commit <sha>)`, verdict, Product gap and UI findings, ids like `[judge:hm-items-cards]`), and the Antigravity nightly tester when it runs (`## QA <date> (commit <sha>)`). Plus the builder's own visual reviews. Each finding is one bullet:

```
- [ ] [Critical|Major|Minor|Product gap|UI] <problem> - steps: <how to reproduce> - expected: <SPEC AC / ui-standards rule / PRODUCT.md step> - actual: <what happened> - screenshot: qa/screenshots/latest/<file>.png
```

Severity: **Critical** = crash, data loss, a core flow cannot be completed, or a screen the user cannot leave. **Major** = a feature misses its acceptance criterion. **Product gap** = the feature exists but a homeowner or contractor would not accept it as described in PRODUCT.md; the builder treats it like Major. **UI** = a numbered rule in docs/ui-standards.md fails (Major for rules 1–6, 13, 15, 16, 28; otherwise Minor). **Minor** = polish.

The builder fixes findings (Critical, Major and Product gap before starting new tasks) and marks them:

```
- [x] [Major] ... - fixed in <sha>
```

Findings that are really spec changes go to BLOCKERS.md under "Decisions to confirm" instead.

Builder visual reviews (V0, V1, ...) go under `## Builder visual review V<n> (commit <sha>)` with the rules checked and what was changed.

---

## QA 2026-10-19 (commit d79790d)

- **Verdict (House Manual)**: Right now it is only a five-tab navigation shell where every tab says "Nothing here yet. This screen is on its way." with a dummy detail button. The biggest gap is that there is no home setup or house model yet—you cannot name a house, draw a floor plan, or add a single item.
- **Verdict (Field Log)**: Like House Manual, it is a bare five-tab scaffold with placeholder text on Today, Jobs, Clock, Schedule and More. The biggest gap is that a contractor cannot sign in, create a company or job, or clock in.

### Findings

- [x] [UI] Major: Landscape phone width (≥ 600 dp, e.g. 891 dp on Pixel 8 landscape) keeps a bottom navigation bar instead of switching to a navigation rail - steps: Launch either app on `qa_pixel` (1080×2400, 420 dpi) and rotate to landscape (`2400×1080`, width ~891 dp) - expected: AC-U1 / `docs/ui-standards.md` rule 1 ("On width ≥ 600 dp use a navigation rail") - actual: `NavigationSuiteScaffold` default checks compact height first and renders a wide bottom navigation bar in phone landscape; it only switches to a rail when both width and height are ≥ 600 dp (tablet `1800×2400`) - screenshot: qa/screenshots/latest/fl-schedule-landscape-rail.png - fixed in 77955a2
- [ ] [UI] Minor: At 200% font scale, bottom navigation bar labels wrap mid-word across two lines ("Remin / ders" in House Manual, "Sched / ule" in Field Log) - steps: Set `adb shell settings put system font_scale 2.0` and open House Manual or Field Log in portrait - expected: AC-U9 / `docs/ui-standards.md` rule 32 ("200% font scale clips nothing; rows grow") without broken mid-word label wrapping in the bottom navigation bar - actual: "Reminders" wraps as "Remin\nders" and "Schedule" wraps as "Sched\nule" - screenshot: qa/screenshots/latest/hm-reminders-font200.png
- [ ] [UI] Minor: `:core-ui` has committed `@PreviewTest` screenshot tests for only 4 of the 6 component preview groups (`LinkChipRowPreview`, `FormFieldsPreview`, and the app shells have no `@PreviewTest` reference PNGs yet) - steps: Inspect `core-ui/src/screenshotTest/kotlin/com/kurtz/homerecord/coreui/ComponentScreenshots.kt` after T005 - expected: AC-U12 / `docs/ui-standards.md` rule 36 and PLAN.md T005 ("reference PNGs committed for the shells and components") - actual: Only `RecordListItemScreenshot`, `EmptyStateScreenshot`, `LoadingStateScreenshot`, and `ErrorStateScreenshot` exist (12 PNGs total) - screenshot: qa/screenshots/latest/hm-home.png

### Product walkthrough

#### House Manual

| Step | Score | Note | Screenshot |
| --- | --- | --- | --- |
| H1 | Blocked | Not built yet (T011/T030 open); launches directly to `PlaceholderRoot("Home")` | qa/screenshots/latest/hm-home.png |
| H2 | Blocked | Not built yet (T012–T014 open) | qa/screenshots/latest/hm-house.png |
| H3 | Blocked | Not built yet (T015/T017 open); House tab shows placeholder | qa/screenshots/latest/hm-house.png |
| H4 | Blocked | Not built yet (T021–T026 open) | qa/screenshots/latest/hm-items.png |
| H5 | Blocked | Not built yet (T021 open) | qa/screenshots/latest/hm-items.png |
| H6 | Blocked | Not built yet (T027 open); Items tab shows placeholder | qa/screenshots/latest/hm-items.png |
| H7 | Blocked | Not built yet (T027 open) | qa/screenshots/latest/hm-items.png |
| H8 | Blocked | Not built yet (T026 open) | qa/screenshots/latest/hm-items.png |
| H9 | Blocked | Not built yet (T048 open) | qa/screenshots/latest/hm-house.png |
| H10 | Blocked | Not built yet (T049–T051 open) | qa/screenshots/latest/hm-house.png |
| H11 | Blocked | Not built yet (T052–T053 open) | qa/screenshots/latest/hm-house.png |
| H12 | Blocked | Not built yet (T066–T067 open) | qa/screenshots/latest/hm-more.png |
| H13 | Blocked | Not built yet (T070–T071 open) | qa/screenshots/latest/hm-home.png |
| H14 | Blocked | Not built yet (T072 open); Reminders tab shows placeholder | qa/screenshots/latest/hm-reminders.png |
| H15 | Blocked | Not built yet (T073/T077 open); More tab shows placeholder | qa/screenshots/latest/hm-more.png |
| H16 | Blocked | Not built yet (T074 open) | qa/screenshots/latest/hm-more.png |
| H17 | Blocked | Not built yet (T008c/T075 open) | qa/screenshots/latest/hm-more.png |
| H18 | Blocked | Not built yet (T063 open) | qa/screenshots/latest/hm-more.png |
| H19 | Blocked | Not built yet (T032 open) | qa/screenshots/latest/hm-items.png |

#### Field Log

| Step | Score | Note | Screenshot |
| --- | --- | --- | --- |
| F1 | Blocked | Not built yet (T008c/T009 open); launches to `PlaceholderRoot("Today")` | qa/screenshots/latest/fl-today.png |
| F2 | Blocked | Not built yet (T009 open) | qa/screenshots/latest/fl-more.png |
| F3 | Blocked | Not built yet (T018 open); Jobs tab shows placeholder | qa/screenshots/latest/fl-jobs.png |
| F4 | Blocked | Not built yet (T034–T035 open); Clock tab shows placeholder | qa/screenshots/latest/fl-clock.png |
| F5 | Blocked | Not built yet (T034 open) | qa/screenshots/latest/fl-clock.png |
| F6 | Blocked | Not built yet (T036–T037 open) | qa/screenshots/latest/fl-more.png |
| F7 | Blocked | Not built yet (T041–T042 open) | qa/screenshots/latest/fl-jobs.png |
| F8 | Blocked | Not built yet (T039–T040 open) | qa/screenshots/latest/fl-today.png |
| F9 | Blocked | Not built yet (T043/T045 open) | qa/screenshots/latest/fl-jobs.png |
| F10 | Blocked | Not built yet (T019/T054/T062/T063 open) | qa/screenshots/latest/fl-jobs.png |
| F11 | Blocked | Not built yet (T044 open) | qa/screenshots/latest/fl-today.png |
| F12 | Blocked | Not built yet (T056–T058 open) | qa/screenshots/latest/fl-jobs.png |
| F13 | Blocked | Not built yet (T059 open); Schedule tab shows placeholder | qa/screenshots/latest/fl-schedule.png |
| F14 | Blocked | Not built yet (T060 open) | qa/screenshots/latest/fl-more.png |
| F15 | Blocked | Not built yet (T037/T061 open) | qa/screenshots/latest/fl-more.png |
| F16 | Blocked | Not built yet (T010/T046 open) | qa/screenshots/latest/fl-today.png |
| F17 | Blocked | Not built yet (T064 open) | qa/screenshots/latest/fl-more.png |
| F18 | Blocked | Not built yet (T065 open); More tab shows placeholder | qa/screenshots/latest/fl-more.png |

Not built yet: H1, H2, H3, H4, H5, H6, H7, H8, H9, H10, H11, H12, H13, H14, H15, H16, H17, H18, H19, F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18.

Tested: AC-C4, AC-C29, AC-B1, AC-B2, AC-B60, AC-F4 (logic), AC-U1..AC-U12. Passed: 15. New issues: 3. Product gaps: 0. Walkthrough: HM 0/0, FL 0/0.

## Builder visual review V0 (commit 3dbf5e2)

Opened committed PNGs (light, dark, 200% font) for Team, More (Owner/Crew), Today (Admin/Crew), Welcome (create/join error), Sign in (email/code), plus the core-ui component sets. Rules checked: 1-12, 24-30.
- Rules 9, 10, 21, 24, 25, 26, 28, 30: pass. Section headers, error supporting text, labelled fields, theme colors, 56 dp+ buttons, empty states with one primary action all present.
- Rule 32: pass. At 200% the Team and Sign in screens grow and scroll; nothing clips.
- Dark mode: orange/amber scheme has adequate contrast; error text readable.
- Not yet applicable: rules 11-14 (search, FAB, hub layout) land with Jobs/Today content (T034+); Today empty state is a root with no hub widgets yet.
- Open Minor items in the QA section (nav label wrapping at 200%, missing LinkChipRow/FormFields screenshots) are left for their turn; no changes needed for V0.

## QA auto 2026-10-20 (commit cbf81fa)

Automated suite run at 2026-10-20 12:52 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [x] [Major] T004 is ticked but these acceptance criteria have no test or flow: AC-C4, AC-U3, AC-U4, AC-U5, AC-U6, AC-U7 [auto:coverage:T004] - steps: automated, see qa-results branch latest/ for commit cbf81fa - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found - AC-U3/U5/U6/U7 now referenced by instrumented ListAndFormRulesTest (AC-C4 and others by flows); AC-U4 already covered by crawler
- [x] [Major] T005 is ticked but these acceptance criteria have no test or flow: AC-U12 [auto:coverage:T005] - steps: automated, see qa-results branch latest/ for commit cbf81fa - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found - fixed in this commit (AC-U12 tagged in HomeRecordAppTest and crawler; AC-H15 by H15-sign-in flow)
- [x] [Major] T006 is ticked but these acceptance criteria have no test or flow: AC-C29 [auto:coverage:T006] - steps: automated, see qa-results branch latest/ for commit cbf81fa - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found - fixed: AC id tags on JVM tests
- [x] [Major] T007 is ticked but these acceptance criteria have no test or flow: AC-B60 [auto:coverage:T007] - steps: automated, see qa-results branch latest/ for commit cbf81fa - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found - fixed in flows F02/H02b/H03 (this commit)
- [x] [Major] T008b is ticked but these acceptance criteria have no test or flow: AC-F4 [auto:coverage:T008b] - steps: automated, see qa-results branch latest/ for commit cbf81fa - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found - fixed in flows F02/H02b/H03 (this commit)
- [x] [Major] T008c is ticked but these acceptance criteria have no test or flow: AC-F4, AC-H15 [auto:coverage:T008c] - steps: automated, see qa-results branch latest/ for commit cbf81fa - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found - fixed in this commit (H15-sign-in flow, F02 flow)
- [x] [Major] T009 is ticked but these acceptance criteria have no test or flow: AC-F2, AC-F3 [auto:coverage:T009] - steps: automated, see qa-results branch latest/ for commit cbf81fa - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found - fixed in flows F02/H02b/H03 (this commit)
- [x] [Major] T010 is ticked but these acceptance criteria have no test or flow: AC-F53 [auto:coverage:T010] - steps: automated, see qa-results branch latest/ for commit cbf81fa - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found - fixed: AC id tags on JVM tests
- [x] [Major] T011 is ticked but these acceptance criteria have no test or flow: AC-C2 [auto:coverage:T011] - steps: automated, see qa-results branch latest/ for commit cbf81fa - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found - fixed: AC id tags on JVM tests
- [x] [Major] T012 is ticked but these acceptance criteria have no test or flow: AC-C8, AC-C11 [auto:coverage:T012] - steps: automated, see qa-results branch latest/ for commit cbf81fa - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found - fixed: AC id tags on JVM tests
- [x] [Major] T014a is ticked but these acceptance criteria have no test or flow: AC-C7 [auto:coverage:T014a] - steps: automated, see qa-results branch latest/ for commit cbf81fa - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found - fixed in flows F02/H02b/H03 (this commit)
- [x] [Major] T014b is ticked but these acceptance criteria have no test or flow: AC-C9 [auto:coverage:T014b] - steps: automated, see qa-results branch latest/ for commit cbf81fa - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found - fixed in flows F02/H02b/H03 (this commit)
- [x] [Major] T015 is ticked but these acceptance criteria have no test or flow: AC-C15 [auto:coverage:T015] - steps: automated, see qa-results branch latest/ for commit cbf81fa - expected: every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules) - actual: no reference found - fixed in flows F02/H02b/H03 (this commit)

Tested: 0 device stages, 229 unit tests, 51 backend tests, coverage 20/41 ACs. Passed: 0. New issues: 13. Critical: 0. Product gaps: n/a (judge entry follows).

## QA judge 2026-10-20 (commit cbf81fa)

Judged the results the suite published for cbf81fa (run 39419820023, the first run of qa.yml). `build` HEAD is already two commits ahead (3fd544b Q001, 65ec903 the auto entry); the suite has not tested HEAD. There are no screenshots to judge: all three emulator jobs failed before the first flow ran, so `results.json` has `stages: []` and `latest/` holds only JVM, lint, backend and coverage output. The UI audit (ui-standards rules 1-36), the committed-PNG comparison and the walkthrough scoring could not be done this run; the builder's own reference PNGs under `*/src/screenshotTest*/reference/` are the only pictures of the apps that exist and they are not evidence of what runs on a device.

Verdict: House Manual: nobody has seen this build on a device; the QA pipeline that was supposed to be Nate's eyes died at emulator boot, so M0 and the plan editor (T011-T015) are still only unit-tested. The single biggest gap is that the device suite does not run, and until it does every ticked task is unverified against PRODUCT.md. / Field Log: same story; sign-in, company and team (T008-T009) have JVM tests and 51 backend tests but no on-device check, and the walkthrough cannot start at F1. Fix the suite before building more surface area.

- [x] [Major] QA device jobs never boot the emulator: `emulator` exits at once with `FATAL | Not enough space to create userdata partition. Available: 6236 MB at /home/runner/.android/avd/test.avd, need 7372.80 MB`, then android-emulator-runner polls adb for 600 s and fails with "Timeout waiting for emulator to boot" [judge:qa-emulator-disk] - steps: open run 39419820023, jobs "Device suite (house-manual)" step "Create AVD snapshot" (12:28:56 UTC), "Device suite (field-log)" and "Handoff between the apps" (12:42:13 UTC); all three carry the same FATAL line - expected: qa.yml runs the device suite per app plus handoff and publishes screenshots (CLAUDE.md "What each file is for", qa/README.md) - actual: 0 device stages, no screenshots, `qa-out/device/*` empty. Cause: qa.yml installs a second SDK under `${{ github.workspace }}/.android-sdk` (`ANDROID_HOME`, line 32) on the same filesystem as `~/.android/avd`, leaving ~6.2 GB, while emulator 37.2.12 needs a 7.2 GB userdata partition for the pixel_7 / API 36 image. Fix in qa.yml (builder, not app code): set `disk-size` on the three `reactivecircus/android-emulator-runner@v2` steps (e.g. `disk-size: 4096M`) and/or free runner disk before the emulator steps (`/usr/share/dotnet`, `/opt/ghc`, `/usr/local/.ghcup`, CodeQL) or use the runner's preinstalled SDK for the device jobs. Also: the AVD cache key `avd-36-google_apis-x86_64-pixel7-v1` missed in all three jobs; check that the cache save step runs after a successful boot. - screenshot: none (no device output on qa-results:latest/) - fixed: disk-size 3072M on all three emulator steps in qa.yml
- [x] [Major] The suite reports a run with zero device stages as a clean run: summary.md says "Device stages: 0/0 passed", the auto entry says "Tested: 0 device stages ... Passed: 0. New issues: 13" and files nothing about the three failed jobs, and `qa/last-tested.txt` was advanced to cbf81fa as if it had been tested [judge:qa-silent-device-failure] - steps: compare `git show origin/qa-results:latest/results.json` (`"stages": []`) with the "## QA auto 2026-10-20 (commit cbf81fa)" entry above and with the run's job conclusions (3 failures) - expected: a device job that produces no results is itself a Critical finding (the definition of done keys on "QA has tested HEAD" and on `qa/last-tested.txt`, so an empty suite must not count as a test) - actual: no finding, last-tested advanced. Fix in qa/scripts/summarize.py and publish-results.sh: when any device job's output directory is missing or `stages` is empty, file `[Critical] device suite produced no results for <app>` with the job's failure step, and do not write `qa/last-tested.txt`. - screenshot: none - fixed in the next commit (summarize.py files [Critical] per missing/empty device job and writes tested-ok only when all of house-manual, field-log, handoff produced stages; qa.yml updates qa/last-tested.txt only then)

House Manual walkthrough

| Step | Score | Note | Screenshot |
| --- | --- | --- | --- |
| H1 | Blocked (untested) | T011 ticked; flow H01-onboarding.yaml exists but never ran (no emulator) | none |
| H2 | Blocked (untested) | T012-T014b ticked; flow H02 never ran; the walkthrough's 5-minute, 10-room, one-L-shape target has never been attempted on a device | none |
| H3 | Blocked (untested) | T015 ticked; flow H03 never ran | none |

Not built yet: H4-H19.

Field Log walkthrough

| Step | Score | Note | Screenshot |
| --- | --- | --- | --- |
| F1 | Blocked (untested) | T008a-c, T009 ticked; flow F01 never ran; `/dev/last-code` sign-in path untested on device | none |
| F2 | Blocked (untested) | T009 ticked; no F02 flow in qa/flows/field-log/ yet (only F01 and the tour) | none |

Not built yet: F3-F18.

"Blocked (untested)" above is not scored as a Product gap: the feature may well work, the suite simply never reached it. These five steps become Product gap findings only if they fail once the emulator boots.

Focus check: nothing to check on screen this run. From the strings and screen catalogs in the repo the two apps keep separate tab sets (HM: Home/House/Items/Reminders/More; FL: Today/Jobs/Clock/Schedule/More); re-check on screenshots next run.

Auto entry review: the 13 `[auto:coverage:T004…T015]` findings are real (41 ACs claimed, 21 without a test or flow); none is a flow error. The builder clears them by tagging existing tests with the AC ids or adding the missing Maestro flows, not by code changes.

Coverage note: the auto entry lists T004, T005, T006, T007, T008b, T008c, T009, T010, T011, T012, T014a, T014b and T015 as ticked with untested ACs (21 of 41); T001-T003, T005a, T008a, T010a and T013 are clean.

Judged: 0 screenshots, 5 walkthrough steps. New issues: 2. Product gaps: 0.

## QA auto 2026-10-20 (commit 7823ec2)

Automated suite run at 2026-10-20 21:23 UTC. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.

- [x] [Critical] device suite produced no results for field-log [auto:device:field-log:no-results] - steps: automated, see qa-results branch latest/ for commit 7823ec2 - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run - fixed: device stages ran with results on f97d6ee (emulator boots after disk-size fix)
- [x] [Critical] device suite produced no results for handoff [auto:device:handoff:no-results] - steps: automated, see qa-results branch latest/ for commit 7823ec2 - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run - fixed: device stages ran with results on f97d6ee (emulator boots after disk-size fix)
- [x] [Critical] device suite produced no results for house-manual [auto:device:house-manual:no-results] - steps: automated, see qa-results branch latest/ for commit 7823ec2 - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run - fixed: device stages ran with results on f97d6ee (emulator boots after disk-size fix)

Tested: 0 device stages, 230 unit tests, 51 backend tests, coverage 49/49 ACs. Passed: 0. New issues: 3. Critical: 3. Product gaps: n/a (judge entry follows).

## QA judge 2026-10-21 (commit 7823ec2)

Judged the results the suite published for 7823ec2 (run 39493986015, the first scheduled run). `build` HEAD is four commits ahead (a823b55 auto entry, 1e73f82 diagnosis, a6fea0c and 0522771 Q003 parts 2-3); the suite has not tested HEAD. Second run in a row with no screenshots: all three emulator jobs died in "Create AVD snapshot" / "Run the handoff suite" with the same `FATAL | Not enough space to create userdata partition. Available: 6232.53 MB at /home/runner/.android/avd/test.avd, need 7372.80 MB` as cbf81fa, because a scheduled run uses the workflow file on `main`, and `main` was still at 42bfb58 (no `disk-size`) when the device jobs started; the in-run sync job copied the fixed qa.yml to `main` (3f2878b) only after they had been scheduled. The builder's diagnosis in 1e73f82 is right. The UI audit (rules 1-36), the committed-PNG comparison and the walkthrough scoring could not be done; `results.json` has `stages: []` and `latest/` holds only JVM, lint, backend, screenshot-report and coverage output. The next scheduled slot (23:20 UTC) had not fired by 00:50 UTC on 2026-10-21; GitHub runs schedules late under load (run 2 fired 3 h 20 min after its 17:20 slot), so that is an observation, not a finding, until the next judge run.

Verdict: House Manual: still unseen on a device after two suite runs; T011-T015 (onboarding, plan editor, blueprint) exist only as unit tests, 230 green JVM tests and reference PNGs the builder drew for itself. The biggest gap is unchanged: the QA pipeline has never booted an emulator, so nothing ticked is verified against PRODUCT.md, and the builder spent this cycle writing instrumented tests (Q003) that have also never run. / Field Log: same; sign-in, company and team (T008-T009) have 51 backend tests and no on-device check, F1 has never been attempted. If Nate installed either build tonight he would be the first person to see it run.

- [x] [Major] `qa/last-tested.txt` was advanced to 7823ec2 by a run with zero device stages, so the repo now claims a sha was tested that nobody tested [judge:qa-last-tested-false] - steps: `git log --oneline -2 -- qa/last-tested.txt` (a823b55 "QA auto 2026-10-20 21:23 UTC (commit 7823ec2)") against `git show origin/qa-results:latest/results.json` (`"stages": []`) - expected: definition of done item 2 keys on `qa/last-tested.txt`; the builder's own fix (qa.yml line 293, `if [ -f qa-publish/tested-ok ]`) is meant to leave it alone when any device job produced nothing - actual: the run executed the stale qa.yml on `main` (42bfb58 line 289 writes the sha unconditionally), so the gate was bypassed while summarize.py (checked out from `build`) correctly filed the three Critical findings. Fix: reset `qa/last-tested.txt` to `none` in the next builder commit (no sha has ever had device results); the first real run overwrites it. Keep the summarize.py/qa.yml gate as is. - screenshot: none - fixed: qa/last-tested.txt reset to none
- [ ] [Minor] Any qa.yml fix on `build` costs one wasted 6-hour cycle: the schedule trigger reads the workflow file on `main`, and the "Keep qa.yml on main in step with build" job runs inside the same run, after the device jobs were already laid out from the stale file [judge:qa-yml-main-lag] - steps: compare run 39493986015 (head_branch main, head_sha 42bfb58, no `disk-size`) with 3f2878b on `main` committed at 20:57 UTC by that same run - expected: a qa.yml fix pushed to `build` takes effect at the next scheduled run (CLAUDE.md: "Builder fixes it when it breaks") - actual: it takes effect one run later; the fix for [judge:qa-emulator-disk] was on `build` at 12:5x UTC and the 20:40 UTC run still failed on it. Fix options for the builder (workflow only, no app code): (a) when a commit changes `.github/workflows/qa.yml`, also push that one file to `main` in the same run (CLAUDE.md forbids the judge, not the builder, from touching main); or (b) make the scheduled workflow on `main` a trampoline whose only step is `gh workflow run qa.yml --ref build` so the real suite always runs `build`'s file. - screenshot: none

House Manual walkthrough

| Step | Score | Note | Screenshot |
| --- | --- | --- | --- |
| H1 | Blocked (untested) | T011 ticked; flow H01-onboarding.yaml never ran (no emulator, second run) | none |
| H2 | Blocked (untested) | T012-T014b ticked; flows H02 and H02b never ran; 5-minute, 10-room, one-L-shape target never attempted on a device | none |
| H3 | Blocked (untested) | T015 ticked; flow H03 (with the layer-toggle step) never ran | none |

Not built yet: H4-H19.

Field Log walkthrough

| Step | Score | Note | Screenshot |
| --- | --- | --- | --- |
| F1 | Blocked (untested) | T008a-c, T009 ticked; flow F01 never ran; `/dev/last-code` (Q005) still unbuilt so H15/F01 sign-in will fail at code lookup even once the emulator boots | none |
| F2 | Blocked (untested) | T009 ticked; flow F02-invite-join.yaml now exists, never ran | none |

Not built yet: F3-F18.

"Blocked (untested)" is not scored as a Product gap; these become Product gap findings only if they fail once the emulator boots.

Focus check: nothing to check on screen this run; the two apps still declare separate tab sets in their catalogs (HM: Home/House/Items/Reminders/More; FL: Today/Jobs/Clock/Schedule/More). Re-check on screenshots next run.

Auto entry review: the three `[auto:device:*:no-results]` Critical findings are real and not flow errors; they stay open until a run publishes `stages` for all three jobs. Nothing in the app needs changing for them; the fix (disk-size 3072M) is already on both branches, so the next scheduled run is the test of it. Open items from the previous judge entry ([judge:qa-emulator-disk], [judge:qa-silent-device-failure]) are marked fixed by the builder; the fixes are in the file but unproven until a run executes them.

Coverage note: the auto entry reports 49/49 claimed ACs covered across 20 ticked tasks (0 gaps); nothing to list.

Judged: 0 screenshots, 5 walkthrough steps. New issues: 2. Product gaps: 0.

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
- [x] [Major] Flow H01-onboarding fails on house-manual [auto:flow:house-manual:H01-onboarding] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step H01 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H01-onboarding.log - flow fixed (ids, onboarding subflow, waits) in the next commit; unverified until QA runs
- [x] [Major] Flow H02-draw-plan fails on house-manual [auto:flow:house-manual:H02-draw-plan] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step H02 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02-draw-plan.log - flow fixed (ids, onboarding subflow, waits) in the next commit; unverified until QA runs
- [x] [Major] Flow H02b-shape-doors-stairs fails on house-manual [auto:flow:house-manual:H02b-shape-doors-stairs] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step H02b passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H02b-shape-doors-stairs.log - flow fixed (ids, onboarding subflow, waits) in the next commit; unverified until QA runs
- [x] [Major] Flow H03-blueprint-room fails on house-manual [auto:flow:house-manual:H03-blueprint-room] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step H03 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H03-blueprint-room.log - flow fixed (ids, onboarding subflow, waits) in the next commit; unverified until QA runs
- [x] [Major] Flow H15-sign-in fails on house-manual [auto:flow:house-manual:H15-sign-in] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: walkthrough step H15 passes (qa/product-walkthrough.md) - actual: see qa-results latest/house-manual/maestro/H15-sign-in.log - flow fixed (ids, onboarding subflow, waits) in the next commit; unverified until QA runs
- [x] [Major] Tour fails under dark on house-manual [auto:variant:house-manual:dark] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under dark - flow fixed (ids, onboarding subflow, waits) in the next commit; unverified until QA runs
- [x] [Major] Tour fails under font200 on house-manual [auto:variant:house-manual:font200] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under font200 - flow fixed (ids, onboarding subflow, waits) in the next commit; unverified until QA runs
- [x] [Major] Tour fails under landscape on house-manual [auto:variant:house-manual:landscape] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9) - actual: tour failed under landscape - flow fixed (ids, onboarding subflow, waits) in the next commit; unverified until QA runs
- [ ] [Major] house-manual scroll jank [auto:jank:house-manual:house-manual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: under 10% janky frames (AC-U11) - actual: 100% janky frames of 10 (AC-U11)
- [ ] [Minor] logcat for com.kurtz.homerecord.housemanual: 0 crashes, 0 ANRs, 1 StrictMode violations [auto:logcat:house-manual:com.kurtz.homerecord.housemanual] - steps: automated, see qa-results branch latest/ for commit f97d6ee - expected: no crashes or ANRs; StrictMode clean (S7) - actual: see qa-results latest/house-manual/logs/crashes.txt and strictmode.txt

Walkthrough steps exercised by flows: field-log F01 Fail, field-log F02 Fail, house-manual H01 Fail, house-manual H02 Fail, house-manual H02b Fail, house-manual H03 Fail, house-manual H15 Fail

Not built yet or skipped: field-log seed:field-log (no debug seed receiver yet (PLAN Q005)); handoff seed:field-log (no debug seed receiver yet (PLAN Q005)); handoff seed:house-manual (no debug seed receiver yet (PLAN Q005)); handoff flows (no flows in qa/flows/handoff yet (PLAN Q004)); handoff variants (no _tour.yaml for handoff); house-manual seed:house-manual (no debug seed receiver yet (PLAN Q005))

Tested: 57 device stages, 230 unit tests, 51 backend tests, coverage 49/49 ACs. Passed: 24. New issues: 27. Critical: 0. Product gaps: n/a (judge entry follows).

## QA 2026-10-21 (commit 8bcf07f)

- **Verdict (House Manual)**: The onboarding flow, floor plan grid editor, and blueprint viewer are solidly functional on device—drawing rooms, editing shapes with corner handles, and toggling utility layers all work smoothly in dark and light modes. The single biggest gap is that the core homeowner features—items, equipment inventory, data plate OCR, and warranty tracking—are completely unbuilt placeholders.
- **Verdict (Field Log)**: The authentication gate, team management with invite code generation/rotation, company settings, and role-gated Today empty states are fully wired and functional on screen. The single biggest gap is that actual contractor job execution—jobs list, time clock punches, photo logs, and daily logs—remains unbuilt scaffolding.

### Findings

- [x] [Critical] Espresso crashes during test idle synchronization on Android API 37: `java.lang.NoSuchMethodException: android.hardware.input.InputManager.getInstance []` - steps: Run `./gradlew connectedDebugAndroidTest` on `qa_pixel` (API 37) - expected: All instrumented tests run and pass on device - actual: All tests in both apps fail during initialization inside `androidx.test.espresso.base.InputManagerEventInjectionStrategy.initialize` because reflection on `InputManager.getInstance` was deprecated/removed in API 36/37 - screenshot: qa/screenshots/latest/fl-launch.png - fixed in this commit
- [x] [Major] `backend/wrangler.toml` hardcodes `ENVIRONMENT = "production"`, causing dev sign-in email-code flow to fail with 502 Bad Gateway - steps: Start backend with `cd backend && npx wrangler dev` without `.dev.vars`, then submit email on Field Log or House Manual Sign-in screen - expected: In dev mode without a Resend key, the code is logged to console and the endpoint returns `{"ok":true}` (AC-B1) - actual: `backend/src/auth/emailCode.ts` checks `if (!env.RESEND_API_KEY) return env.ENVIRONMENT !== "production"`; because `wrangler.toml` defines `ENVIRONMENT = "production"`, `sendCode` returns `false` and `/auth/email/start` returns 502 `send_failed` - screenshot: qa/screenshots/latest/fl-signin-typed.png - fixed in this commit
- [ ] [Minor] `RetrofitAuthApi` silently swallows `IOException` without logging to logcat, leaving no debugging trace when network calls fail - steps: Trigger any network error or backend failure while attempting sign-in - expected: Network exceptions or HTTP errors are logged to logcat with tag or stack trace for developer/QA visibility - actual: Catch blocks in `RetrofitAuthApi.kt` convert `IOException` directly to `AuthResult.Offline` without any logging, making connection issues hard to diagnose from device logs - screenshot: qa/screenshots/latest/fl-signin.png

### Product walkthrough

#### House Manual

| Step | Score | Note | Screenshot |
| --- | --- | --- | --- |
| H1 | Pass | Maple St setup, Two-story house style picker, Basement foundation chip, attached garage toggle switch all function smoothly | qa/screenshots/latest/hm-onboarding.png |
| H2 | Pass | Grid canvas room placement (Garage), corner drag shape editing, dimensions (12' x 12'), add stairs, deselect | qa/screenshots/latest/hm-plan-editor.png |
| H3 | Pass | Blueprint view with north arrow, bold wall outlines, outer dimension labels, floor switcher (Upper/Main/Basement), utility layer chips (Plumbing, Items, Electrical, HVAC, Low-voltage, Gas, Drain & vent), room tap navigates to Room page, room long-press opens edit bottom sheet | qa/screenshots/latest/hm-house-blueprint.png |
| H4 | Blocked | Not built yet (T021–T026 open) | qa/screenshots/latest/hm-items.png |
| H5 | Blocked | Not built yet (T021 open) | qa/screenshots/latest/hm-items.png |
| H6 | Blocked | Not built yet (T027 open); Items tab shows placeholder | qa/screenshots/latest/hm-items.png |
| H7 | Blocked | Not built yet (T027 open) | qa/screenshots/latest/hm-items.png |
| H8 | Blocked | Not built yet (T026 open) | qa/screenshots/latest/hm-items.png |
| H9 | Blocked | Not built yet (T048 open) | qa/screenshots/latest/hm-house.png |
| H10 | Blocked | Not built yet (T049–T051 open) | qa/screenshots/latest/hm-house.png |
| H11 | Blocked | Not built yet (T052–T053 open) | qa/screenshots/latest/hm-house.png |
| H12 | Blocked | Not built yet (T066–T067 open) | qa/screenshots/latest/hm-more.png |
| H13 | Blocked | Not built yet (T070–T071 open) | qa/screenshots/latest/hm-home.png |
| H14 | Blocked | Not built yet (T072 open); Reminders tab shows placeholder | qa/screenshots/latest/hm-reminders.png |
| H15 | Blocked | Not built yet (T073/T077 open); More tab shows Sign in screen | qa/screenshots/latest/hm-more.png |
| H16 | Blocked | Not built yet (T074 open) | qa/screenshots/latest/hm-more.png |
| H17 | Blocked | Not built yet (T008c/T075 open) | qa/screenshots/latest/hm-more.png |
| H18 | Blocked | Not built yet (T063 open) | qa/screenshots/latest/hm-more.png |
| H19 | Blocked | Not built yet (T032 open) | qa/screenshots/latest/hm-items.png |

#### Field Log

| Step | Score | Note | Screenshot |
| --- | --- | --- | --- |
| F1 | Pass | Sign-in gate, email code entry UI, company welcome setup ("Kurtz Electric", trade, phone), join error handling, and Today empty state ("Invite crew") verified | qa/screenshots/latest/fl-welcome.png |
| F2 | Pass | Team screen displays member list with roles (Owner, Office, Lead, Crew) and hourly rates, invite code generation/display ("K7M2 QX9R"), share invite link, rotate code button, role-gated More tab (Crew has no Team or Company options) | qa/screenshots/latest/fl-team.png |
| F3 | Blocked | Not built yet (T018 open) | qa/screenshots/latest/fl-launch.png |
| F4 | Blocked | Not built yet (T034–T035 open) | qa/screenshots/latest/fl-launch.png |
| F5 | Blocked | Not built yet (T034 open) | qa/screenshots/latest/fl-launch.png |
| F6 | Blocked | Not built yet (T036–T037 open) | qa/screenshots/latest/fl-launch.png |
| F7 | Blocked | Not built yet (T041–T042 open) | qa/screenshots/latest/fl-launch.png |
| F8 | Blocked | Not built yet (T039–T040 open) | qa/screenshots/latest/fl-launch.png |
| F9 | Blocked | Not built yet (T043/T045 open) | qa/screenshots/latest/fl-launch.png |
| F10 | Blocked | Not built yet (T019/T054/T062/T063 open) | qa/screenshots/latest/fl-launch.png |
| F11 | Blocked | Not built yet (T044 open) | qa/screenshots/latest/fl-launch.png |
| F12 | Blocked | Not built yet (T056–T058 open) | qa/screenshots/latest/fl-launch.png |
| F13 | Blocked | Not built yet (T059 open) | qa/screenshots/latest/fl-launch.png |
| F14 | Blocked | Not built yet (T060 open) | qa/screenshots/latest/fl-launch.png |
| F15 | Blocked | Not built yet (T037/T061 open) | qa/screenshots/latest/fl-launch.png |
| F16 | Blocked | Not built yet (T010/T046 open) | qa/screenshots/latest/fl-launch.png |
| F17 | Blocked | Not built yet (T064 open) | qa/screenshots/latest/fl-launch.png |
| F18 | Blocked | Not built yet (T065 open) | qa/screenshots/latest/fl-launch.png |

Not built yet: H4, H5, H6, H7, H8, H9, H10, H11, H12, H13, H14, H15, H16, H17, H18, H19, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12, F13, F14, F15, F16, F17, F18.

Tested: AC-C1, AC-C2, AC-C4, AC-C7, AC-C8, AC-C9, AC-C11, AC-C12, AC-C14, AC-C15, AC-C29, AC-B1, AC-B2, AC-B60, AC-F1, AC-F2, AC-F3, AC-F4, AC-U1..AC-U12. Passed: 25. New issues: 3. Product gaps: 0. Walkthrough: HM 3/3, FL 2/2.

## QA judge 2026-10-21 (commit f97d6ee)

Judged the results the suite published for f97d6ee (run of 2026-10-21 02:12 UTC, the first run with device stages: 57 stages, 24 passed). `build` HEAD is seven commits ahead (545fbeb Q003, 8bcf07f and 3eb9477 crawler and acceptance fixes, 8bf7eba the Antigravity QA entry, a9aa17d Espresso and wrangler fixes, 301c3da cold start); the suite has not tested HEAD, so several items below are already addressed there and unverified. First run with screenshots: 258 PNGs on `qa-results:latest/` (crawler shots per catalog screen in dark, 200% font and landscape; launch, after-monkey and release-launch shots; no Maestro flow screenshots, see [judge:qa-flow-screenshots-lost]). The crawler renders each screen alone in `QaActivity`, so bottom bars and the app shell are not in any picture; rules 1 and 5 could not be judged from them.

Verdict: House Manual: the house model is real now: onboarding, the grid editor (L-shapes, doors, stairs, overlap warning, undo) and the blueprint read like a product and hold up in dark mode and at 200%. But finish setup and the app drops you on a Home tab that says "Nothing here yet"; the button said "Draw your floor plan" and nothing opened; the editor is two taps away on another tab, and everything past H3 is still a placeholder. / Field Log: on this build a contractor cannot get past the first screen: Send code returned 502 because the local backend ran as `production` without a Resend key (Antigravity found it, a9aa17d fixes it, unverified), so F1 and F2 never ran. Behind the gate Team, Company and the role-gated More are tidy, but Today and More paint their first row under the status bar, and nothing a crew would use (jobs, clock, photos) exists yet.

- [x] [UI] Major: Field Log root screens paint under the status bar: on Today the "Everything is synced" chip sits over the clock, on More the company name does [judge:fl-roots-status-bar-overlap] - steps: sign in and create a company, look at Today, then More; or open `qa-results:latest/field-log/crawler/field-log/today-admin-dark.png` and `more-crew-font200.png` - expected: ui-standards rule 28 (edge-to-edge, insets from Scaffold, nothing under the status bar); walkthrough UI audit rule 6 - actual: `TodayScreen` and `MoreScreen` are plain Columns inside `NavigationSuiteScaffold`'s content with no top inset (`HouseScreen` in House Manual does `statusBarsPadding()`, Field Log's roots do not); the chip/title overlaps the status bar in all three variants - screenshot: qa-results:latest/field-log/crawler/field-log/today-admin-dark.png - fixed in this commit (statusBarsPadding on Today and More)
- [x] [Product gap] "Draw your floor plan" does not draw a floor plan: both onboarding buttons land on the Home tab placeholder ("Nothing here yet. This screen is on its way.") and the editor is House → Edit plan, two taps away on a tab nobody told you about [judge:hm-draw-plan-lands-on-placeholder] - steps: fresh install, fill name and style, tap "Draw your floor plan" - expected: PRODUCT.md / walkthrough H1 ("lands on Home with quick actions"), H2 starts drawing; SPEC HM-Onboarding "Draw your floor plan" - actual: `AppTabs.kt` gate switches to `HouseManualTabs` with the default tab `home`, a `PlaceholderKey`; nothing pushes `PlanKey`. Until T030 lands, "Draw your floor plan" should open the plan editor (House tab selected, `PlanKey(mainFloor)` pushed) and the app should land on House, not on a placeholder - screenshot: qa-results:latest/house-manual/screenshots/house-manual-first-launch.png (the after-tap state is not in the results; see PlaceholderRoot) - fixed in the next commit (Draw your floor plan opens House tab with the plan editor pushed)
- [x] [UI] Major: the room page names neither its floor nor the house and has nothing on it: title "Kitchen", one line "14' by 12' · Kitchen", then blank [judge:hm-room-page-parent] - steps: House tab, tap Kitchen; or `qa-results:latest/house-manual/crawler/house-manual/room-page-landscape.png` - expected: ui-standards rule 4 (child names the parent in the subtitle or a chip that navigates up), rule 10 (empty state with icon, headline, sentence, action); walkthrough H3 "tap a room opens it" - actual: no subtitle ("Main floor · Maple St"), no floor chip, no empty state ("No items in this room yet" + Add item, even if Add item is disabled until T021); a homeowner who taps a room gets a dead page - screenshot: qa-results:latest/house-manual/crawler/house-manual/room-page-landscape.png - fixed in this commit (subtitle "Main floor · Maple St", empty state)
- [ ] [UI] Minor: in landscape the blueprint is a thumbnail: the plan is drawn about 70 px wide in the middle of an empty band, room names and dimensions gone [judge:hm-blueprint-landscape-tiny] - steps: House tab, rotate; `qa-results:latest/house-manual/crawler/house-manual/house-blueprint-landscape.png` - expected: walkthrough H3 ("clean blueprint, room names, dimensions") and UI audit rule 6 (rotate once per screen); ui-standards rule 6 (≥ 840 dp width adapts the layout) - actual: floor chips, hint text, layer chips and the two buttons take the height; the canvas gets the remainder and scales the plan to fit it. Put chips and buttons beside the canvas in landscape, or let the canvas scroll/zoom - screenshot: qa-results:latest/house-manual/crawler/house-manual/house-blueprint-landscape.png
- [ ] [UI] Minor: the plan editor opens with the plan pinned top-left at a fixed scale, leaving two thirds of the canvas empty; in landscape the selection inspector (55% of height) hides the room being edited [judge:hm-plan-editor-fit] - steps: House → Edit plan on a floor with rooms; select a room; rotate - expected: walkthrough H2 ("grid, typed dimensions, drag, resize, snap… under 5 minutes"): the plan should open fitted and centred (the fit icon exists, it should be the initial state), and in landscape the inspector should sit beside the canvas - actual: `qa-results:latest/handoff/crawler/house-manual/plan-editor-dark.png` shows the plan in the top third; `plan-shape-landscape.png` shows the L-shaped room cut off under the inspector - screenshot: qa-results:latest/house-manual/crawler/house-manual/plan-shape-landscape.png
- [ ] [UI] Minor: layer chips (Items, Electrical, Plumbing, HVAC, Low-voltage, Gas, Drain and vent) are text only [judge:hm-layer-chips-no-icons] - steps: House tab, layer row; `house-layers-font200.png` - expected: ui-standards rule 26 ("every category, kind and layer has an icon"), walkthrough UI audit rule 10 - actual: FilterChips with labels only; selected state is colour only - screenshot: qa-results:latest/house-manual/crawler/house-manual/house-layers-font200.png
- [ ] [Minor] The "room not found" screen is titled "room" in lowercase [judge:hm-room-missing-title] - steps: open a deleted room (catalog `room-missing`) - expected: a screen title per rule 2 ("Room") or the empty-state headline alone; docs/voice.md - actual: top bar title "room" (looks like a raw key fallback) - screenshot: qa-results:latest/house-manual/crawler/house-manual/room-missing-landscape.png
- [ ] [Minor] Field Log catalog entries `welcome-create`, `welcome-join-error` and the company form render without the Scaffold the app wraps them in, so the crawler shows "Set up your company" over the clock and a surface that stops two thirds down; the crawler cannot judge rule 28 on them and anyone reading the PNGs sees a bug the app does not have [judge:fl-catalog-welcome-insets] - steps: `qa-results:latest/field-log/crawler/field-log/welcome-create-dark.png` versus `AppTabs.kt` line 65 (`Scaffold { WelcomeRoute(..., Modifier.padding(it)) }`) - expected: QA rule 3, ScreenCatalog entries show the screen as the app shows it - actual: bare `WelcomeScreen` preview - screenshot: qa-results:latest/field-log/crawler/field-log/welcome-create-dark.png
- [ ] [Minor] Suite: Maestro screenshots never reach qa-results. Every flow calls `takeScreenshot` (H01-onboarding, F01-sign-in, tour-<variant>-<tab>) and failures produce screenshots, yet `latest/*/screenshots/` holds only the suite's own three shots per app and `latest/*/maestro/` only logs and xml [judge:qa-flow-screenshots-lost] - steps: `git ls-tree -r origin/qa-results latest/field-log/screenshots` after a run where tour-dark passed - expected: qa/README.md and the judge prompt ("tour-<variant>-<screen> files show each root screen…; flow-* files are failure screenshots") - actual: device-suite.sh moves `$OUT/maestro/*.png`, but current Maestro writes screenshots and failure captures to its test-output directory (`~/.maestro/tests/<timestamp>/`), not the cwd. Fix in qa/scripts/device-suite.sh: pass `--test-output-dir "$OUT/maestro"` (or collect from `~/.maestro/tests`) and keep the move - screenshot: none
- [ ] [Minor] Suite: `/sdcard/Download/qa` is shared between apps and survives between jobs, so crawler output is mixed and stale. In the handoff job Field Log's crawler failed both tests with `EACCES` on `welcome-create-dark.png` (the file already existed, owned by another uid), the 30 `handoff/crawler/field-log` PNGs are byte-identical to the field-log job's (pulled from the cached AVD, timestamps 1:53 against 2:07 for the House Manual ones), and `handoff/crawler/house-manual/` contains Field Log screens (company-form, team-*, today-*, welcome-*) [judge:qa-crawler-sdcard-stale] - steps: `cmp latest/field-log/crawler/field-log/team-owner-dark.png latest/handoff/crawler/field-log/team-owner-dark.png`; `latest/handoff/instrumented/field-log.txt` - expected: each instrumented run starts from an empty output directory and the pull is per app - actual: as above. Fix in device-suite.sh: `adb shell rm -rf /sdcard/Download/qa` before each `am instrument`, write to `/sdcard/Download/qa/<app>/`, and do not save the AVD cache after tests have run (or snapshot before the suite) - screenshot: qa-results:latest/handoff/crawler/house-manual/today-admin-dark.png
- [ ] [Minor] Suite: about one crawler variant shot in twelve is blank (screen background plus status bar, nothing composed): floors-list-dark, floors-loading-dark, plan-editor-dark, house-blueprint-font200, house-loading-font200, room-page-font200, room-missing-font200 in the house-manual job; house-blueprint-font200 and house-empty-floor-font200 again in the handoff job [judge:qa-crawler-blank-shots] - steps: open the files named - expected: QA rule 3 / Q002: a picture of every catalog screen in each variant - actual: the capture runs before the recreated activity has drawn a frame. Fix in ScreenCrawlerTest: after applying the variant, `waitForIdle()` then wait for a node from the screen (its root test tag) before `captureToImage`/screencap - screenshot: qa-results:latest/house-manual/crawler/house-manual/house-blueprint-font200.png
- [ ] [Minor] Suite: the StrictMode count is not ours. All violations in `latest/*/logs/strictmode.txt` come from other processes: pid 4931/4897 `com.google.android.googlequicksearchbox:search`, 2477 `com.google.android.apps.messaging`, 1559/1718 likewise; no frame mentions `com.kurtz` [judge:qa-strictmode-pid] - steps: `grep -n "Start proc 4931" latest/house-manual/logs/logcat.txt` - expected: the logcat stage counts StrictMode violations in the app's process only (S7) - actual: device-suite.sh line 235 greps the whole logcat. Fix: filter on the app's pids (`pidof`/`Start proc` lines for the package) - screenshot: none
- [ ] [Minor] Suite: the jank stage reports 100%/87%/80% janky of 5 to 10 frames. Ten frames is not a scroll; those are the launch frames of a debug build on a software-rendered emulator, and no screen in either app has a 200-row list yet [judge:qa-jank-no-list] - steps: `latest/house-manual/jank-house-manual.json` (`"frames":10,"janky":10`) - expected: AC-U11 measures a 200-row list scroll - actual: `dumpsys gfxinfo` after a fling that hit nothing scrollable. Fix: gate the stage on a seeded list (Q005) and on at least 100 frames rendered, and measure the release build; report "not measurable" otherwise - screenshot: none

House Manual walkthrough

| Step | Score | Note | Screenshot |
| --- | --- | --- | --- |
| H1 | Clunky | Onboarding form is right (name, style, foundation chips, garage switch, inline "Required." errors, dark and 200% fine). Flow H01 failed on a flow error (below). After setup the app lands on the Home placeholder and "Draw your floor plan" opens nothing ([judge:hm-draw-plan-lands-on-placeholder]) | qa-results:latest/house-manual/screenshots/house-manual-first-launch.png |
| H2 | Clunky | Editor has typed dimensions, corner handles, L-shape, door/window on a tapped wall, stairs with floor link, overlap warning, undo/redo, unplaced-rooms row. Opens unfitted top-left and loses the canvas to the inspector in landscape ([judge:hm-plan-editor-fit]); the 5-minute, 10-room target is still unmeasured because H02/H02b never got past the first tap (flow error) | qa-results:latest/house-manual/crawler/house-manual/plan-shape-font200.png |
| H3 | Clunky | Portrait blueprint is clean: walls, names, dimensions, north arrow, floor chips, long-press sheet (Rename/Edit shape/Delete). Landscape is a thumbnail ([judge:hm-blueprint-landscape-tiny]); the room page it opens is empty and names no parent ([judge:hm-room-page-parent]); layer chips lack icons | qa-results:latest/house-manual/crawler/house-manual/house-layers-font200.png |

Not built yet: H4–H19 (H15-sign-in.yaml exercises the email-code sign-in half of H17, not H15's Home report; it is named for the wrong step).

Field Log walkthrough

| Step | Score | Note | Screenshot |
| --- | --- | --- | --- |
| F1 | Blocked | Sign-in screen shows (Email, Send code, Continue with Google; no House Manual wording). "Send code" never produced the Code field: the local backend ran with `ENVIRONMENT = "production"` and no Resend key, so `/auth/email/start` returned 502 (Antigravity's Major in the QA 2026-10-21 entry, fixed in a9aa17d, untested). Welcome, company form and Today empty state look right in the crawler; Today paints under the status bar ([judge:fl-roots-status-bar-overlap]) | qa-results:latest/field-log/screenshots/field-log-first-launch.png |
| F2 | Blocked | Same gate. Team screen (invite card with code, Share invite, Make a new code, members with roles and rates) and the role-gated More look right in the crawler | qa-results:latest/field-log/crawler/field-log/team-owner-font200.png |

Not built yet: F3–F18.

Focus check: tab sets stay separate (HM Home/House/Items/Reminders/More; FL Today/Jobs/Clock/Schedule/More); the shared Sign in screen's wording ("An account backs up your records and keeps your other devices in step") is neutral enough for both. The Field Log screens inside `handoff/crawler/house-manual/` are a suite artefact ([judge:qa-crawler-sdcard-stale]), not shared screens. Nothing to file.

Auto entry review (27 findings for f97d6ee):
- `[auto:flow:house-manual:H01-onboarding]`, `H02-draw-plan`, `H02b-shape-doors-stairs`, `H03-blueprint-room`: [judge: flow error]. All four die at `tapOn: "Home name"`; Maestro matches text as a full regex and the field's label is "Home name (required)". Use `id: hm_onboarding_name` (Q001a) or `Home name.*`. The app screen is fine.
- `[auto:flow:house-manual:H15-sign-in]` and `[auto:variant:house-manual:dark|font200|landscape]` (the `_tour`): [judge: flow error]. Both tap "Skip for now" on an empty form; Skip submits the form, so with no name and no style it only shows "Required." and the tabs never appear ("Element not found: More" / "Home"). H15 must fill name and style before Skip; the tour's `runFlow when: visible` has no wait and ran before the 4 s cold start drew anything. Not an app bug (SPEC: Skip creates a Main floor for a named home), though "Skip for now" under a form that will not let you skip reads oddly; a wording decision for docs/voice.md, not filed.
- `[auto:flow:field-log:F01-welcome]`, `F02-invite-join`: real at f97d6ee (backend 502 on Send code), already fixed in a9aa17d by the builder; keep open until a run passes them. The flow's `extendedWaitUntil visible: "Code"` is fine.
- `[auto:instrumented:handoff:field-log]` (2 of 2): [judge: flow error], `EACCES` writing to a stale `/sdcard/Download/qa` ([judge:qa-crawler-sdcard-stale]). `[auto:instrumented:house-manual:*]` (3 of 16: `hm_layer_items` tag prefix, `hm_plan_add_room` hidden while a room is selected, crawler 48 dp on onboarding-empty) and `[auto:instrumented:field-log:field-log]` (crawler 48 dp on welcome-create) are real at f97d6ee and addressed in 8bcf07f/3eb9477; unverified.
- `[auto:logcat:*]` (4): [judge: flow error], no violation is in our process ([judge:qa-strictmode-pid]).
- `[auto:jank:*]` (4): [judge: flow error], 5–10 frames is not a scroll ([judge:qa-jank-no-list]).
- `[auto:coldstart:*]` (4): real but measured on the debug APK (StrictMode on, no R8, software GL): 3.8–5.7 s. The same run's release stage launched House Manual in 1.7–2.0 s and Field Log in 1.9–2.6 s; that is the number to chase against AC-U11's 1.5 s. The builder's 301c3da (token store off the main thread) is unmeasured. Suggest the suite record both numbers and gate on release.
- `[auto:fixtures:house-manual]`: suite; the builder's per-file push in 301c3da is the fix, unverified.

Coverage note: the auto entry reports 49/49 claimed ACs covered across 20 ticked tasks (0 gaps); nothing to list.

Judged: 156 screenshots (of 258 published: the 30 handoff Field Log crawler shots are byte-identical to the field-log job's, and the remaining handoff House Manual shots repeat screens already judged), 5 walkthrough steps. New issues: 13. Product gaps: 1.
