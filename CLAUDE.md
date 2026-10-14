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
| Build | AGP 8.13.x (latest 8.13 patch), Kotlin 2.2.x (latest stable 2.2 patch), Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`), KSP matching the Kotlin version |
| UI | Compose BOM (latest stable), material3, material-icons-extended, activity-compose, navigation-compose 2.9.x, lifecycle-viewmodel-compose, lifecycle-runtime-compose |
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

## Workflow per run

1. Read PROGRESS.md (last line), PLAN.md (next unchecked task), REVIEW.md (open findings — fix Critical and Major before new tasks), BLOCKERS.md.
2. Do the task. Write the tests. Run build, tests, lint.
3. Tick the task in PLAN.md, append one line to PROGRESS.md, add any decisions to docs/decisions.md, commit, push `build`.
4. If something needs Nate, add it to BLOCKERS.md and move to the next task that does not depend on it.
5. End every run with something that builds and passes tests.
