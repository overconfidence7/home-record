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
