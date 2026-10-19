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
| qa/nightly-qa-prompt.md | The prompt the nightly tester runs. Reference for what QA will check. | Nate |
| docs/builder-prompt.md | The prompt the hourly builder routine runs (this is you). | Nate |
| qa/last-tested.txt | Commit SHA last tested by QA, or `none`. | QA |
| qa/screenshots/latest/ | QA's screenshots of every screen from the last run. | QA |
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

## Definition of done

The builder creates `DONE` only when all of these hold at the same commit:

1. Every task in PLAN.md is ticked (or marked deferred with a reason) and every item in REVIEW.md is fixed, including "Product gap" items.
2. `qa/last-tested.txt` equals the current `build` HEAD, the newest QA entry in REVIEW.md ends with `New issues: 0` and `Product gaps: 0`, and both walkthroughs in `qa/product-walkthrough.md` are recorded as complete with no blocked steps.
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

1. Read PROGRESS.md (last lines), PLAN.md (next unchecked task), REVIEW.md (open findings: fix Critical, Major and Product gap before new tasks), BLOCKERS.md.
2. Do the task. Write the tests. Run build, tests, lint, screenshots (for UI changes), backend tests (for backend changes).
3. Tick the task in PLAN.md, append one line to PROGRESS.md (date/time ET, task, what changed, test counts, one line telling QA what to check), add decisions to docs/decisions.md, commit, push `build`.
4. If something needs Nate, add it to BLOCKERS.md and move to the next task that does not depend on it.
5. End every run with something that builds, passes tests and launches.
