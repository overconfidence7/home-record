# Progress

One line per builder run, newest at the bottom: date and time (Eastern), task, what changed, test counts, and one line telling QA what to check on the emulator.

2026-10-19 ~10:30 ET — setup complete: v2 contract written (PRODUCT.md, SPEC.md, PLAN.md, CLAUDE.md, docs/ui-standards.md, docs/competitors.md, qa/product-walkthrough.md, qa/nightly-qa-prompt.md); no code yet — emulator: nothing to test until T001 lands
2026-10-19 09:40 ET — T001: Gradle 9.8.1 wrapper, version catalog, modules :core :core-ui :house-model :house-manual :field-log, one empty screen per app; assembleDebug, testDebugUnitTest, lintDebug green (5 tests) — emulator: both debug APKs should launch to a blank screen titled with the app name
2026-10-19 10:25 ET — T002: HomeRecordTheme in :core-ui (MaterialExpressiveTheme, dynamic color + HM blue/teal and FL orange/amber fallbacks, standard motion, shapes), DayNight window themes, adaptive launcher icons (with monochrome) for both apps; assembleDebug, testDebugUnitTest (7 tests), lintDebug green — emulator: both apps launch edge-to-edge with themed title, correct icon, dark mode follows system
