# Rules for the builder

You are building HomeRecord v2: two native Android apps (House Manual, Field Log) on one shared house model, plus a Cloudflare backend. Read PRODUCT.md, SPEC.md, docs/ui-standards.md and PLAN.md before every run. Work from the `build` branch. This is a from-scratch build on the same repo; do not copy code from the v1 history (branch `archive/v1`).

## What each file is for

| File | Purpose | Who edits it |
| --- | --- | --- |
| PRODUCT.md | What the apps are for, the demo walkthrough, non-negotiables. The yardstick for "done". | Nate only |
| SPEC.md | The contract: features, screens, data model, acceptance criteria AC-C/H/F/B/U. | Nate only. **Never edit SPEC.md.** If it is wrong or ambiguous, write it in BLOCKERS.md and build the most reasonable reading. |
| docs/ui-standards.md | The UI rules every screen follows; part of the contract (AC-U). | Nate; builder may add to the component map |
| docs/competitors.md | What competitors offer and which features we adopted. Reference. | Nate |
| PLAN.md | Ordered task checklist T001.. with reviews R and visual reviews V. | Builder ticks boxes and appends subtasks; never reorders or deletes |
| CLAUDE.md | This file. | Nate |
| REVIEW.md | QA findings (nightly tester) and builder visual reviews; how each was fixed. | QA adds findings; builder marks fixes and adds V entries |
| BLOCKERS.md | "Needs Nate" and "Decisions to confirm". | Builder appends; Nate resolves |
| PROGRESS.md | Run log, newest at the bottom, one line per run. | Builder appends |
| docs/decisions.md | Dated log of library, architecture, pricing and scope choices with reasons. | Builder |
| docs/voice.md | Wording rules for every user-facing string. | Nate |
| docs/billing.md | RevenueCat project, entitlements, key handling. | Nate |
| qa/product-walkthrough.md | The scripted walkthrough QA performs nightly; builder runs it too at T078/T079. | Nate |
| qa/README.md | How the apps are tested: the layers, the two per-app suites, the builder's test obligations. | Nate |
| qa/flows/<app>/ | Maestro flows, one per walkthrough step (`H02-…`, `F04-…`), plus `_tour.yaml`; `qa/flows/handoff/` for the cross-app flows. | Builder adds and extends |
| qa/scripts/ | The automated suite: device-suite.sh, coverage-check.py, summarize.py, publish-results.sh. | Nate; builder may fix bugs |
| qa/fixtures/ | Test images and PDF the flows use. | Builder (Q005) |
| .github/workflows/qa.yml | The automated QA suite, 4×/day: JVM checks, coverage gate, device suite per app, handoff, results to the `qa-results` branch and a `## QA auto` entry in REVIEW.md. | Builder fixes it when it breaks |
| docs/qa-judge-prompt.md | The prompt of the QA judge routine that reads the suite's results and writes `## QA judge` entries. | Nate |
| qa/nightly-qa-prompt.md | The prompt the Antigravity nightly tester runs (exploratory, when Nate's PC has usage). | Nate |
| docs/builder-prompt.md | The prompt the hourly builder routine runs (this is you). | Nate |
| qa/last-tested.txt | Commit SHA last tested by the automated suite, or `none`. | QA suite |
| `qa-results` branch | Screenshots, logs and summaries of the last suite run (`latest/`) and the last 60 summaries (`history/`). Rewritten every run; never base work on it. | QA suite |
| .github/workflows/testing-apks.yml | Builds and publishes testing APKs on every push to `build`. | Builder fixes it when it breaks |
| scripts/setup-android.sh | Fresh-container SDK setup. | Nate; builder may fix bugs |
| backend/ | Cloudflare Worker, migrations, tests. | Builder |

## Stack and architecture

- Kotlin (AGP 9 built-in), Jetpack Compose, Material 3 1.4+ with `MaterialExpressiveTheme`, Navigation 3, MVVM, coroutines + Flow, Room, kotlinx-serialization.
- Gradle Kotlin DSL, version catalog `gradle/libs.versions.toml`, `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, google() + Google's Maven Central mirror + mavenCentral().
- minSdk 26, compileSdk 37, targetSdk 37, JDK 17+ (cloud VM has 21), Kotlin/Java target 17.
- Modules: `:core` (no Compose), `:core-ui`, `:house-model`, `:house-manual` (`com.kurtz.homerecord.housemanual`), `:field-log` (`com.kurtz.homerecord.fieldlog`). Package root `com.kurtz.homerecord` (`kurtz` is a placeholder until the LLC). Feature sub-packages: `...housemanual.items`, `...fieldlog.clock`, etc. Inside a feature: `XScreen.kt`, `XViewModel.kt`, `XUiState.kt`, `XRoute.kt`.
- Manual DI: one `AppContainer` per app from `Application.onCreate`, ViewModels through a factory. No Hilt/Koin.
- Local-first: Room `homerecord.db` is the source of truth; `:core` `SyncEngine` talks to the backend. Media in `filesDir/media/<uuid>.<ext>`, thumbnails beside them, generated PDFs and bundles in cache shared through FileProvider.
- Every side effect (camera, location, audio, speech, network, billing, vibration, power) sits behind a small interface in `:core` with a fake in `src/test`.
- Backend: `backend/` is a Hono TypeScript Worker. `npm test` (vitest, workers pool) must pass. Deploy with `wrangler deploy` only when `CLOUDFLARE_API_TOKEN` is present (Needs Nate until then); the apps point at `BuildConfig.API_BASE_URL` (debug: the value in `local.properties` `api.baseUrl`, default `http://10.0.2.2:8787` for `wrangler dev` on the emulator).

## Approved dependencies

Add nothing outside this list without a BLOCKERS.md entry under "Decisions to confirm" and a docs/decisions.md entry. Versions are the latest stable as of 2026-10-19; if a set does not resolve together, pick the nearest that does and record it.

| Purpose | Dependency |
| --- | --- |
| Build | AGP 9.x latest stable (built-in Kotlin; no `kotlin-android` plugin), Gradle 9.x, KSP matching AGP's Kotlin, `org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`, `com.android.compose.screenshot` (Compose Preview Screenshot Testing) |
| UI | Compose BOM latest stable; `material3` 1.4.0+ (Expressive), `material3-adaptive`, `material3-adaptive-layout`, `material3-adaptive-navigation3`, `material3-adaptive-navigation-suite`, `material-icons-extended`, `activity-compose` 1.13+, `navigation3-runtime` + `navigation3-ui` 1.2+, `lifecycle-viewmodel-navigation3`, `lifecycle-runtime-compose` |
| Images | Coil 3 (`coil-compose`, `coil-network-okhttp`) |
| Lists | `sh.calvin.reorderable:reorderable` 3.x |
| Data | Room 2.8+ (runtime, ktx, compiler via KSP), kotlinx-serialization-json, kotlinx-coroutines, DataStore preferences, `androidx.security:security-crypto` for the token store |
| Network | OkHttp 5 + Retrofit 3 (or Ktor client; pick one and record it), kotlinx-serialization converter |
| Camera | CameraX 1.5+ (core, camera2, lifecycle, view) |
| OCR / scanning | `com.google.mlkit:text-recognition` (bundled), `com.google.android.gms:play-services-mlkit-document-scanner` |
| Location | `com.google.android.gms:play-services-location` (FusedLocationProvider, foreground only) |
| 3D | `io.github.sceneview:sceneview` (Filament inside; never `arsceneview`, never `filamat-android`) |
| PDF | Platform `PdfDocument` (write) and `PdfRenderer` (view) |
| Auth | `androidx.credentials:credentials` + `credentials-play-services-auth` + `com.google.android.libraries.identity.googleid:googleid` |
| Push | `com.google.firebase:firebase-messaging` (delivery only; no other Firebase artifact) |
| Reminders | WorkManager 2.10+ |
| Widgets | Glance for App Widgets 1.1+ |
| Billing | RevenueCat `purchases` latest stable; keys per `docs/billing.md`, never in source |
| Tests | JUnit 4, Robolectric 4.15+, androidx.test core/ext-junit, compose-ui-test-junit4 (under Robolectric), kotlinx-coroutines-test, Truth or kotlin.test |
| Backend | hono, @cloudflare/workers-types, wrangler, vitest, @cloudflare/vitest-pool-workers, zod, jose (JWT), resend |

Zip uses `java.util.zip`. Crypto uses `javax.crypto`. Perspective warp uses `android.graphics.Matrix.setPolyToPoly` (4 points). No OpenCV. No other libraries.

## Commands

- Build: `./gradlew assembleDebug`
- Tests: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug`
- Screenshots: `./gradlew updateDebugScreenshotTest` (writes reference PNGs; commit them), `./gradlew validateDebugScreenshotTest` (CI)
- Instrumented tests (need an emulator): `./gradlew :house-manual:connectedDebugAndroidTest`; the cloud suite runs them with `am instrument`
- Flows: `maestro test qa/flows/house-manual/H01-onboarding.yaml` (install Maestro with `curl -fsSL https://get.maestro.mobile.dev | bash`)
- Coverage gate: `python3 qa/scripts/coverage-check.py` (lists ticked tasks whose ACs have no test or flow)
- Backend: `cd backend && npm ci && npm test`; local server `npx wrangler dev`
- Setup on a fresh container: `bash scripts/setup-android.sh` then `source "$HOME/.android-env.sh"`
- Maven Central through the cloud proxy is unreliable (429 and false 404). `settings.gradle.kts` lists Google's Maven Central mirror (`maven-central.storage-download.googleapis.com`) before `mavenCentral()`, and the root build points Robolectric's downloader at the mirror. Keep both. If a build still fails with 429/404, wait 60 s, retry once with `--max-workers=1`, then push to `wip/` and stop.
- SDK platform naming: Google publishes minor SDK releases (`platforms;android-37.0`, `37.1`). `compileSdk = 37` needs `platforms/android-37.0` as sdkmanager installs it; never add an `android-37` symlink beside it (AGP then fails to find the target). `scripts/setup-android.sh` handles this.

Run the tests before every commit. A task is done only when its acceptance criteria pass and the tests are green.

## Coding conventions

- One screen = one `@Composable fun XScreen(state: XUiState, onEvent: (XEvent) -> Unit)` that is previewable with fake state, plus a thin `XRoute` that wires the ViewModel. Logic never lives in composables.
- Every screen has `@Preview`s for light, dark and 200% font scale with realistic fake data (not "Item 1, Item 2"), and a screenshot test. After any UI change run `updateDebugScreenshotTest` and **look at the PNGs** (`Read` the files) before committing. If a screen would fail a rule in docs/ui-standards.md, fix it in the same run.
- Navigation 3: keys are `@Serializable` data classes/objects; each bottom tab has its own back stack in `rememberSaveable`; `ScreenScaffold` from `:core-ui` on every non-root screen.
- `UiState` is an immutable data class; events are a sealed interface. ViewModels expose `StateFlow<UiState>` and survive process death through `SavedStateHandle` for forms.
- Room entities are `@Entity` data classes suffixed `Entity` in `:core:data`; domain models are plain data classes in `:core:model`; mappers are extension functions. IDs are UUID strings; timestamps epoch millis; dates ISO `yyyy-MM-dd`; lengths stored in inches as `Int`, money in cents as `Long`.
- Every pure rule (geometry, warranty parsing, timesheet math, log assembly, slot occupancy, sync merge) is its own class with unit tests before the UI that uses it.
- Compose tests use `createComposeRule()` under Robolectric (`@Config(sdk = [35])` where 37 is unsupported).
- Strings in `strings.xml`; wording per `docs/voice.md`. No hardcoded user-facing text.
- Touch targets ≥ 48 dp; ≥ 56 dp on Field Log capture and clock screens; shutter ≥ 72 dp.
- No `!!`. No `GlobalScope`. No `runBlocking` outside tests. No `Color(0xFF…)` outside the theme and the layer palette.
- Commit per task: `T034: FL-Clock with GPS punch and far-from-job reason`. Keep commits small; never force-push `build`.
- Keep `lintDebug` free of errors; note recurring warnings in REVIEW.md.

## QA rules (what every task owes the test suite)

The automated suite (`qa/README.md`) runs four times a day and files findings in REVIEW.md as `## QA auto` entries; the QA judge routine adds `## QA judge` entries with Product gap and UI findings. Both count as QA. To keep the suite honest, every task you tick must leave these behind:

1. Every `[unit]` AC it claims has a named JVM test that mentions the id (`// AC-C8` in the test or `@AcceptanceCriterion("AC-C8")`). Backend ACs: a vitest file mentioning the id.
2. Every other AC it claims is referenced by an instrumented test in `src/androidTest` or a Maestro flow in `qa/flows/<app>/`. `qa/scripts/coverage-check.py` enforces this; a ticked task with gaps becomes a Major finding.
3. Every new or changed screen has a `ScreenCatalog` entry (realistic fake state, used by the crawler and the previews) and stable `testTag`s on every interactive element, named `<app>_<screen>_<element>` (`hm_items_fab`, `fl_clock_in`). The app roots set `Modifier.semantics { testTagsAsResourceId = true }` so Maestro can target ids.
4. A task that touches a walkthrough step adds or extends the flow named for that step (zero-padded: `H02-draw-plan.yaml`, `F04-clock-in.yaml`). Flows run against a fresh install plus the debug seed scenario; keep them deterministic (no sleeps, use `extendedWaitUntil`).
5. A task that needs data to be testable grows the debug seed (`<pkg>.qa.SEED` receiver, Q005) and, for Field Log, the dev sign-in path (`/dev/last-code` on the local backend).
6. A failing flow is fixed by fixing the app unless the judge tagged it `[judge: flow error]`; then fix the flow.
7. Never delete or weaken a test or flow to get the suite green.

## Definition of done

The builder creates `DONE` only when all of these hold at the same commit:

1. Every task in PLAN.md is ticked (or marked deferred with a reason) and every item in REVIEW.md is fixed, including "Product gap" items.
2. `qa/last-tested.txt` equals the current `build` HEAD, the newest `## QA auto` entry in REVIEW.md is for that HEAD and ends with `New issues: 0`, the newest `## QA judge` entry is for that HEAD and ends with `New issues: 0. Product gaps: 0.`, and the judge's walkthrough tables for both apps show every step Pass with no Blocked or Not built yet.
3. Every item on the ship-ready checklist has been done at least once since the last code change that could affect it.

When `DONE` is created the run also builds release APKs for both apps, lists their paths and SHA-256 in `DONE` with install instructions and known limits, makes its final reply the same summary starting with `SHIP READY: HomeRecord <sha>`, and, if a Gmail connector is available, emails it to nkurtz@gmail.com with subject `SHIP READY: HomeRecord`. Later runs that find `DONE` reply `Build complete` and stop.

Until all three hold, a run with no plan task and no open finding does one ship-ready item, commits, and stops. If the checklist is exhausted and QA has not tested HEAD, the run appends `waiting for QA on <sha>` to PROGRESS.md and stops.

### Ship-ready checklist (one item per run, in order; record each in PROGRESS.md)

- S1 Lint: `lintDebug` warnings to zero or each justified in REVIEW.md.
- S2 Tests: every `[unit]` criterion in SPEC.md has a named test; add the missing ones.
- S3 Strings: every user-facing string in `strings.xml`, consistent terms across both apps, voice.md checklist clean.
- S4 Accessibility: content descriptions, touch targets, TalkBack order, 200% text.
- S5 States: every screen has empty, loading and error states; survives rotation and process death.
- S6 Dark mode and dynamic color on every screen; no hardcoded colors.
- S7 Performance: cold start under 1.5 s; no main-thread I/O (StrictMode clean); 3D at 30 fps on the reference home; 200-row lists scroll without jank.
- S8 Release build: `assembleRelease` with R8 for both apps; keep rules for Room, serialization, ML Kit, Filament, RevenueCat; apps launch from release APKs.
- S9 Icons and identity: adaptive icons, names, versionCode/versionName scheme.
- S10 Store readiness: data safety, privacy policy (including the backend), store listings.

## Workflow per run

1. Read PROGRESS.md (last lines), PLAN.md (next unchecked task), REVIEW.md (open findings from `## QA auto`, `## QA judge` and `## QA` entries alike: fix Critical, Major and Product gap before new tasks; `[auto:coverage:…]` items are fixed by tagging or adding tests and flows, not by code changes), BLOCKERS.md.
2. Do the task. Write the tests and flows the QA rules require. Run build, tests, lint, screenshots (for UI changes), backend tests (for backend changes), and `python3 qa/scripts/coverage-check.py` before ticking the task.
3. Tick the task in PLAN.md, append one line to PROGRESS.md (date/time ET, task, what changed, test counts, one line telling QA what to check), add decisions to docs/decisions.md, commit, push `build`.
4. If something needs Nate, add it to BLOCKERS.md and move to the next task that does not depend on it.
5. End every run with something that builds, passes tests and launches.
