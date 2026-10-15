# Rules for the builder

You are building HomeRecord: two native Android apps (House Manual, Field Log) on one shared data model. Read SPEC.md and PLAN.md before every run. Work from the `build` branch.

## What each file is for

| File | Purpose | Who edits it |
| --- | --- | --- |
| SPEC.md | The contract: features, screens, data model, acceptance criteria AC-1..AC-100 | Nate only. **Never edit SPEC.md.** If the spec is wrong or ambiguous, write it in BLOCKERS.md and build the most reasonable reading. |
| PLAN.md | Ordered task checklist T01..T40 with review tasks R1..R8 | Builder ticks boxes; may append notes under a task, never reorders or deletes tasks |
| CLAUDE.md | This file | Nate |
| REVIEW.md | QA findings from emulator testing and how they were fixed | QA adds findings; builder marks fixes |
| BLOCKERS.md | "Needs Nate" (cannot proceed without him) and "Decisions to confirm" (judgment calls made) | Builder appends; Nate resolves |
| PROGRESS.md | Run log, newest at the bottom, one line per run: date, tasks done, test counts, commit | Builder appends |
| qa/last-tested.txt | Commit SHA last tested by QA, or `none` | QA |
| docs/decisions.md | Dated log of library, architecture, pricing and scope choices with reasons | Builder |
| scripts/setup-android.sh | Fresh-container SDK setup; also pasted into the cloud environment with `SETUP_SOFT_FAIL=1` | Nate; builder may fix bugs in it |

## Stack and architecture

- Kotlin, Jetpack Compose, Material 3, Navigation Compose, MVVM, Kotlin coroutines + Flow.
- Gradle Kotlin DSL, version catalog at `gradle/libs.versions.toml`. Match the conventions of CircuitSiegeDualFront (plugins via `alias(libs.plugins...)`, `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, google() + mavenCentral()).
- minSdk 26, compileSdk 37, targetSdk 37. Build with JDK 17 or newer (the cloud VM ships OpenJDK 21). Java/Kotlin target 17.
- SDK location: `ANDROID_HOME` (the cloud setup script installs to `/opt/android-sdk`). If Gradle reports "SDK location not found", run `source /etc/profile.d/android-env.sh` or `bash scripts/setup-android.sh`.
- Modules:
  - `:core` — Android library. Domain model, Room database, DAOs, repositories, search engine, `.homerecord` bundle reader/writer, import planner, PDF row builders, pure logic. No Compose.
  - `:core-ui` — Android library. Material 3 theme, shared composables (photo tile, room picker, empty state), FileProvider helpers.
  - `:house-manual` — application, `applicationId com.kurtz.homerecord.housemanual`, one `MainActivity`.
  - `:field-log` — application, `applicationId com.kurtz.homerecord.fieldlog`, one `MainActivity`.
- Package root: `com.kurtz.homerecord`. Sub-packages by feature: `com.kurtz.homerecord.housemanual.panel`, `...housemanual.emergency`, `...fieldlog.capture`, etc. Inside a feature: `XScreen.kt` (composables), `XViewModel.kt`, `XUiState.kt`.
- Manual DI: one `AppContainer` per app created in `Application.onCreate`, passed to ViewModels through a `ViewModelProvider.Factory`. No Hilt/Koin.
- Local-first: Room (`homerecord.db`), app-private files for media (`filesDir/media/<uuid>.jpg`, `.m4a`), cache dir for generated PDFs and bundles shared through FileProvider. No network calls anywhere in v1.
- Every side effect (camera, audio, vibration, power events, text recognition, billing) sits behind a small interface in `:core` or the app module with a fake in `src/test`.

## Approved dependencies

Add nothing outside this list without a BLOCKERS.md entry under "Decisions to confirm" and a docs/decisions.md entry.

| Purpose | Dependency |
| --- | --- |
| Build | AGP 9.x, latest stable (9.4.0 as of Oct 2026; needs Gradle ≥ 9.6 and supports compileSdk 37 without `android.suppressUnsupportedCompileSdk`). AGP 9 has built-in Kotlin: drop the `org.jetbrains.kotlin.android` plugin and `kotlinOptions {}`, keep `org.jetbrains.kotlin.plugin.compose` and `org.jetbrains.kotlin.plugin.serialization` at the Kotlin version AGP bundles, and use a KSP release built for that Kotlin version. If KSP/Kotlin pairing cannot be made to resolve in one run, fall back to `android.builtInKotlin=false` with the `kotlin-android` plugin (Kotlin 2.2.x) and record it. Until T04a lands, the AGP 8.13.2 + suppress-flag setup stays as is. |
| UI | Compose BOM (latest stable once on AGP 9; 2026.06.01 while on AGP 8.13), material3, material-icons-extended, activity-compose, navigation-compose 2.9.x, lifecycle-viewmodel-compose, lifecycle-runtime-compose |
| Data | Room 2.8.x (runtime, ktx, compiler via KSP), kotlinx-serialization-json, kotlinx-coroutines |
| Camera (Field Log) | CameraX 1.5.x (core, camera2, lifecycle, view) |
| OCR (House Manual) | `com.google.mlkit:text-recognition` (bundled, on-device) |
| Reminders (M3) | WorkManager 2.10.x |
| Widget (M3) | Glance for App Widgets 1.1.x |
| Billing (M3, T36 only) | RevenueCat `purchases` (latest stable) |
| Tests | JUnit 4, Robolectric 4.15.x, androidx.test core/ext-junit, compose-ui-test-junit4 (run under Robolectric), kotlinx-coroutines-test, Truth or kotlin.test |

PDFs use the platform `android.graphics.pdf.PdfDocument`. Zip uses `java.util.zip`. Crypto uses `javax.crypto`. No other libraries.

If the exact versions above do not resolve together, pick the nearest set that does, record it in docs/decisions.md, and keep going.

## Commands

- Build: `./gradlew assembleDebug`
- Tests: `./gradlew testDebugUnitTest`
- Lint: `./gradlew lintDebug`
- Setup on a fresh container: `bash scripts/setup-android.sh`
- In the cloud, Maven Central can answer 429 under parallel resolution: add `--max-workers=2` and retry once before treating a dependency failure as real.

Run the tests before every commit. A task is done only when its acceptance criteria pass and the tests are green.

## Coding conventions

- One screen = one `@Composable fun XScreen(state: XUiState, onEvent: (XEvent) -> Unit)` that is previewable with fake state, plus a thin `XRoute` that wires the ViewModel. Logic never lives in composables.
- `UiState` is an immutable data class; events are a sealed interface. ViewModels expose `StateFlow<UiState>`.
- Room entities are `@Entity` data classes suffixed `Entity` in `:core:data`; domain models are plain data classes in `:core:model`; mappers are extension functions.
- IDs are `UUID.randomUUID().toString()`. Timestamps are epoch millis; dates are ISO `yyyy-MM-dd` strings.
- Every pure rule (slot occupancy, search matching, grouping, parsing, due-date math) is its own class with unit tests before the UI that uses it.
- Compose tests use `createComposeRule()` under Robolectric (`@RunWith(AndroidJUnit4::class)`, `@Config(sdk = [34])` where Robolectric does not yet support 37).
- Strings go in `strings.xml`. No hardcoded user-facing text in Kotlin.
- Touch targets ≥ 48dp everywhere; ≥ 56dp in Field Log capture screens; shutter ≥ 72dp.
- No `!!`. No `GlobalScope`. No `runBlocking` outside tests.
- Commit per task: `T07: breaker editor with slot occupancy rules`. Keep commits small; never force-push `build`.
- Keep `lintDebug` free of errors; warnings are fine but note recurring ones in REVIEW.md.

## Definition of done

The builder creates `DONE` only when all of these are true at the same commit:

1. Every task in PLAN.md is ticked (or marked deferred with a reason) and every item in REVIEW.md is fixed.
2. `qa/last-tested.txt` equals the current `build` HEAD, and the newest QA entry in REVIEW.md ends with `New issues: 0`.
3. Every item on the ship-ready checklist below has been done at least once since the last code change that could affect it.

When `DONE` is created, the run also: builds release APKs for both apps (`assembleRelease`), lists their paths and SHA-256 in `DONE` with install instructions and known limits, writes the same summary as the run's final reply starting with the line `SHIP READY: HomeRecord <sha>`, and, if a Gmail connector is available in the run, emails that summary to nkurtz@gmail.com with subject `SHIP READY: HomeRecord`. Later runs that find `DONE` reply `Build complete` and stop.

Until all three hold, a run with no plan task and no open finding does one ship-ready item, commits, and stops. If the checklist is also exhausted and QA has not yet tested HEAD, the run appends `waiting for QA on <sha>` to PROGRESS.md and stops without changes.

### Ship-ready checklist (one item per run, in this order; record each in PROGRESS.md)

- S1 Lint: `lintDebug` warnings driven to zero or each remaining one justified in REVIEW.md.
- S2 Tests: every `[unit]` acceptance criterion in SPEC.md has a named test; add the missing ones.
- S3 Strings: every user-facing string in `strings.xml`, no typos, consistent capitalization and terminology across both apps.
- S4 Accessibility: content descriptions on every icon button and image, touch targets ≥ 48dp (≥ 56dp Field Log capture), TalkBack order sensible, text scales to 200% without clipping.
- S5 States: every screen has an empty state, an error state where an operation can fail, and survives rotation and process death without losing input.
- S6 Dark mode and dynamic color: every screen checked; no hardcoded colors.
- S7 Performance: cold start under 1.5 s on the emulator; no main-thread database or file I/O (StrictMode clean in debug).
- S8 Release build: `assembleRelease` succeeds with R8 enabled; ProGuard/R8 keep rules for Room, kotlinx-serialization and ML Kit; app launches from the release APK.
- S9 Icons and identity: adaptive launcher icons for both apps, correct app names, versionCode/versionName scheme in place.
- S10 Store readiness: `docs/data-safety.md` and `docs/privacy-policy.md` match what the apps actually do; store listing drafts in `docs/store-listing.md`.

## Workflow per run

1. Read PROGRESS.md (last line), PLAN.md (next unchecked task), REVIEW.md (open findings — fix Critical and Major before new tasks), BLOCKERS.md.
2. Do the task. Write the tests. Run build, tests, lint.
3. Tick the task in PLAN.md, append one line to PROGRESS.md, add any decisions to docs/decisions.md, commit, push `build`.
4. If something needs Nate, add it to BLOCKERS.md and move to the next task that does not depend on it.
5. End every run with something that builds and passes tests.
