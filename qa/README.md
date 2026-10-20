# QA

How HomeRecord is tested, in layers from cheapest to most human. Every layer writes its findings into REVIEW.md in the same bullet format, so the builder treats them all alike (Critical, Major and Product gap before new work).

| Layer | Where it runs | When | What it catches |
| --- | --- | --- | --- |
| 1. JVM checks | GitHub Actions (`testing-apks.yml` on every push to `build`; `qa.yml`) | every push, and 4×/day | unit tests, lint errors, screenshot references that drifted, backend tests |
| 2. Coverage gate | `qa.yml` → `qa/scripts/coverage-check.py` | 4×/day | a ticked task whose acceptance criteria have no test or flow (this is what makes the suite act on everything new) |
| 3. Device suite, one job per app | `qa.yml` → `qa/scripts/device-suite.sh` on a KVM emulator | 4×/day | install, cold start time, instrumented acceptance tests and the screen crawler, Maestro flows for the walkthrough steps, dark/200%/landscape tour, scroll jank, monkey crashes, release (R8) launch, logcat crashes/ANRs/StrictMode |
| 4. Handoff suite | `qa.yml`, both apps on one emulator | 4×/day | Field Log → House Manual handoff flows (`qa/flows/handoff/`) |
| 5. QA judge | Claude Code routine (`docs/qa-judge-prompt.md`) | 4×/day, after the suite | the product verdict, Product gap and UI findings from the screenshots, walkthrough scoring, "Not built yet" |
| 6. Antigravity nightly | Nate's PC (`qa/nightly-qa-prompt.md`) | 23:30 when it has usage | exploratory testing a script cannot do; it reads the suite's results first and does not repeat them |

Results of layers 1–4 are published to the `qa-results` branch (`latest/` for the newest run with every screenshot and log; `history/` for the last 60 summaries) and summarized in a `## QA auto <date> (commit <sha>)` entry in REVIEW.md. Layer 5 adds a `## QA judge` entry. Layer 6 adds a `## QA` entry.

## Two apps, two suites

House Manual and Field Log share `:core`, `:core-ui` and `:house-model`, and the device suite deliberately runs them as separate jobs with separate flow folders (`qa/flows/house-manual/`, `qa/flows/field-log/`), separate instrumented test packages and separate walkthrough tables. Each suite also carries **focus guards**: assertions that the other app's concepts do not leak (House Manual never shows Clock, Jobs, Today, crew or punches; Field Log never shows Reminders, warranties-expiring cards or the homeowner onboarding). The one place they meet, the handoff, has its own suite.

## How a feature gets tested (the builder's obligations, also in CLAUDE.md)

For every PLAN task the builder ticks:

1. Every `[unit]` AC has a named JVM test that mentions the AC id (e.g. `// AC-C8` or `@AcceptanceCriterion("AC-C8")`).
2. Every other AC is referenced by an instrumented test in `androidTest` or by a Maestro flow in `qa/flows/<app>/`. The coverage gate files a Major for each ticked task that misses this.
3. Every new screen gets a `ScreenCatalog` entry (fake state for the crawler) and stable `testTag`s (`hm_items_fab`, `fl_clock_in`), exposed as resource ids with `testTagsAsResourceId` so Maestro can find them by id instead of text.
4. If the task touches a walkthrough step, the flow named for that step (`H02-…`, `F04-…`) is added or extended. Flow names start with the zero-padded step id from `qa/product-walkthrough.md`.
5. If the task needs data to be testable (a seeded home, a company with punches), the debug seed scenario (`<pkg>.qa.SEED`, PLAN Q005) grows with it.

## Running it locally

```
./gradlew testDebugUnitTest lintDebug validateDebugScreenshotTest
python3 qa/scripts/coverage-check.py
# with an emulator booted and APKs in qa-out/apk/ (see qa.yml "Build APKs" step):
bash qa/scripts/device-suite.sh house-manual qa-out
bash qa/scripts/device-suite.sh field-log qa-out
python3 qa/scripts/summarize.py --input <artifacts dir> --sha <short sha> --review REVIEW.md --out qa-publish
```

Maestro: `curl -fsSL https://get.maestro.mobile.dev | bash`, then `maestro test qa/flows/house-manual/H01-onboarding.yaml` against any emulator.

## Where the schedule lives

GitHub runs scheduled workflows from the default branch (`main`), so `qa.yml` must exist there; the workflow's own `sync-workflow` job copies it from `build` to `main` whenever it changes (the builder never pushes `main` itself). Everything the suite tests is checked out from `build`.

## Cost

GitHub Actions on a private repo: 2,000 free minutes a month (Free plan), then $0.008 a minute if the account's spending limit allows it (the default limit is $0, so runs stop rather than bill). A full suite run is about 35–55 billed minutes; at four runs a day that is roughly 4,500–6,500 minutes a month. To cut it, change the cron line in `qa.yml` to two runs a day (`"20 11,23 * * *"`), or make the repo public.

## Fixtures

`qa/fixtures/` holds the images and PDF the flows and the walkthrough use; the device suite pushes them to `/sdcard/Download/qa-fixtures/`.
