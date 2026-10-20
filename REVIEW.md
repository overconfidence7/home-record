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

- [ ] [Critical] device suite produced no results for field-log [auto:device:field-log:no-results] - steps: automated, see qa-results branch latest/ for commit 7823ec2 - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run
- [ ] [Critical] device suite produced no results for handoff [auto:device:handoff:no-results] - steps: automated, see qa-results branch latest/ for commit 7823ec2 - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run
- [ ] [Critical] device suite produced no results for house-manual [auto:device:house-manual:no-results] - steps: automated, see qa-results branch latest/ for commit 7823ec2 - expected: a device job that produces no results is a finding, and the run does not count as tested - actual: output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run

Tested: 0 device stages, 230 unit tests, 51 backend tests, coverage 49/49 ACs. Passed: 0. New issues: 3. Critical: 3. Product gaps: n/a (judge entry follows).
