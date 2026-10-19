# Decisions

Newest first. Each entry: date, decision, reason. Open questions at the bottom.

## Log

- 2026-10-17: T33 widget uses RemoteViews + AppWidgetProvider instead of Glance (PLAN allows either): 2x1 fixed layout, no extra dependency, count pushed from a Flow collector in HouseManualApp.

- **2026-10-13** Two apps in one repo (`:house-manual`, `:field-log`) sharing `:core` and `:core-ui`. Reason: Nate's call; the apps have different users and store listings but one data model.
- **2026-10-13** Native Android (Kotlin + Jetpack Compose), not Kotlin Multiplatform or Flutter. Reason: build constraints for the autonomous builder; matches CircuitSiegeDualFront; iOS can be revisited once the apps earn it.
- **2026-10-13** AGP 8.13.x rather than 9.x. Reason: same plugin model as CircuitSiegeDualFront (`kotlin-android` + `kotlin-compose` plugins); AGP 9 changes the Kotlin integration and is a separate upgrade task once the build is stable.
- **2026-10-13** compileSdk/targetSdk 37 (Android 17, stable since June 2026), minSdk 26.
- **2026-10-13** Mapping mode runs in the foreground activity with keep-screen-on, not a foreground service. Reason: the phone is plugged in and visible during mapping; avoids the `specialUse` foreground-service type and its Play declaration.
- **2026-10-13** House Manual panel and shutoff photos use the system camera (`TakePicture` contract); Field Log uses CameraX. Reason: Field Log needs volume-key shutter and glove-size controls; House Manual does not.
- **2026-10-13** Data-plate OCR uses ML Kit text recognition, bundled model. Reason: on-device, no network, no account; the only Google SDK in v1.
- **2026-10-13** Billing deferred to M3 behind an `Entitlements` interface; RevenueCat only in T36. Reason: cannot be verified without Play and a signed build; the rest of the app must not depend on it.
- **2026-10-13** Manual DI (`AppContainer`) instead of Hilt. Reason: two small apps; fewer annotation processors; faster builds.
- **2026-10-13** `.homerecord` is a zip of `record.json` + `media/`, optional AES-256-GCM wrapper with a PBKDF2 key (M3). Reason: inspectable, no library, matches `shared/home-record-format.md`.
- **2026-10-13** Names: HomeRecord (repo `home-record`, package root `com.kurtz.homerecord`). Reason: neutral name covering both apps; `kurtz` is a placeholder until the LLC exists.

## Open questions

- [ ] Store names for the two apps ("House Manual" and "Field Log" are working titles; check Play and trademark conflicts).
- [ ] LLC name → final package root (rename before the first Play upload; application IDs cannot change after).
- [ ] Climate zones for seasonal reminders: four coarse zones or IECC zones?

## Build decisions

- 2026-10-15 (Nate): AGP 9.x approved, replacing the 2026-10-13 "AGP 8.13.x" decision. Reason: AGP 8.13 does not officially support compileSdk 37 (needs a suppress flag and a platform-directory symlink) and holds Compose at a June 2026 BOM. The codebase is four tasks old, so the migration is cheapest now. Done as T04a with a wip/ fallback so the pipeline never stalls on it.
- 2026-10-15: setup-android.sh resolves the platform package name itself (`platforms;android-37` or `android-37.N`, whichever sdkmanager lists) and symlinks `platforms/android-37` when needed, so the cloud environment's cached setup and the in-repo script agree.
- 2026-10-14: Compose BOM pinned to 2026.06.01. BOM 2026.08.00+ (Compose 1.12) requires AGP 9.1, but CLAUDE.md fixes AGP at 8.13.x. Revisit if Nate approves AGP 9.
- 2026-10-14: Versions: AGP 8.13.2, Kotlin 2.2.21, Gradle 8.14.3, Robolectric 4.15.1. KSP/Room are added when T02 needs them (KSP 2.2.21-2.0.5).
- 2026-10-14: compileSdk 37 with AGP 8.13.2 (tested to 36.1) works with `android.suppressUnsupportedCompileSdk=37.0`. The SDK package is `platforms;android-37.0`; setup-android.sh now installs that and symlinks `platforms/android-37` because AGP 8.13 looks for that directory name.
- 2026-10-14: Maven Central returns HTTP 429 under parallel resolution in the cloud proxy; use `--max-workers=2` and retry.
- 2026-10-14: T02 added Room 2.8.4 + KSP 2.2.21-2.0.5 to `:core`. Entities/DAOs in package `core.data`, domain models in `core.model` (packages, not Gradle modules). Enums use Room's built-in name storage; only `List<Int>` slots needs a converter. DAOs use `@Upsert` (never REPLACE, which would cascade-delete children). Endpoint.breakerId is FK SET_NULL so deleting a breaker detaches endpoints (AC-12). Schema export is off (v1, no migrations yet).

## 2026-10-14 — T03 repository layer
- Repositories take `Source`, `Clock`, `IdGenerator` so tests are deterministic; `save*` methods treat a blank id as insert.
- `MediaFileStore` stores paths relative to `filesDir` and rejects anything not directly under `media/` (also serves AC-91 later).
- Deleting/replacing a record's photo removes the file from disk in the repository, not the UI.

## 2026-10-14 — T04 onboarding and navigation
- Added navigation-compose 2.9.6 (approved list). Start route is resolved once by `StartDestination` (no Home → onboarding, else hub); nav host renders nothing until it resolves.
- Home + rooms are created in one Room transaction (`HomeRepository.createHomeWithRooms`); room names for quick-add chips are resolved from strings.xml in the nav host and passed to the ViewModel, so the stored name matches the UI language.
- Duplicate custom rooms (case-insensitive) are ignored. HM-Home and HM-Panel are placeholders until T05/T06.

## 2026-10-15 — Maven Central 429s
- Cause: every hourly run starts from a snapshot with an empty Gradle cache and re-downloads the whole dependency set through a shared proxy, which rate-limits Maven Central.
- Fix: `scripts/setup-android.sh` now warms the Gradle cache (`:module:dependencies` for every module, capped at 150 s) into `GRADLE_USER_HOME=/opt/gradle-home` so it is part of the environment snapshot; `gradle.properties` raises Gradle's repository retry count (8) and initial backoff (3 s). Builder falls back to one `--max-workers=1` retry after 60 s.

## 2026-10-15 (evening) — Maven mirror, SDK minor versions
- The Gradle cache warm-up cannot fit the cloud snapshot's five-minute budget next to the SDK install, and the snapshot was never being built, so every run paid a cold download through a proxy that rate-limits Maven Central. Replaced with Google's Maven Central mirror (`maven-central.storage-download.googleapis.com`, on the default allowlist via `*.googleapis.com`) listed before `mavenCentral()` in `settings.gradle.kts`, plus `robolectric.dependency.repo.url` pointed at the same mirror for Robolectric's android-all jars. Warm-up stays in the script as opt-in (`SETUP_WARM_GRADLE=1`).
- `scripts/setup-android.sh` now prefers `platforms;android-37` then `android-37.0` and repoints a stale `android-37` symlink from a later minor (37.1/37.2) back to 37.0; Gradle rejects the later minors for `compileSdk = 37`.

## 2026-10-15 — T07 slot rendering
Two-pole breakers draw as two cells (one per slot, second marked "Two-pole (with slot N)") instead of a spanning cell, since the LazyColumn grid is row-based. Tandem needs an existing one-pole breaker; the new half starts with a blank label and shows as Empty until filled.
- 2026-10-16: Equipment dates are entered as typed `yyyy-MM-dd` text fields with inline validation (no date-picker dependency or Material DatePicker state), matching the spec's ISO storage format. A picker can replace them in polish if QA finds them clumsy.
- 2026-10-16: setup-android.sh no longer creates `platforms/android-37`; AGP 8.13 finds `android-37.0` itself, and having both makes target lookup fail.

## 2026-10-16 — T17 OCR
- Added `com.google.mlkit:text-recognition:16.0.1` (approved in CLAUDE.md, bundled model, no network) in `:house-manual` only. Wrapped by `TextRecognizer` in `:core` so tests use a fake; parsing is pure in `DataPlateParser`.
- Recognizer exceptions are treated as "no text found" (AC-47: never crash).

## 2026-10-16 (T18)
- House Manual release build: R8 minify + resource shrinking enabled, unsigned; keep rules in house-manual/proguard-rules.pro for Room, kotlinx-serialization, ML Kit. Adaptive launcher icon (house glyph, monochrome layer) is a simple vector placeholder-free drawable, no new dependencies.

## 2026-10-16 — T19 Field Log shell
- Room DB bumped to v2 (Job, LogEntry, DailyReport, CompanyProfile tables) with `fallbackToDestructiveMigration`: nothing has shipped, so no real user data exists to migrate. Replace with a real migration before the first release (T39).
- Field Log duplicates the quick-add room list from House Manual (own `room_*` strings) rather than moving it to `:core-ui`, to avoid resource-name clashes between the two apps' string files.
- "Last activity" for AC-53 uses LogEntry.createdAt (when it was recorded), not the entry's `day`.
- FL-JobEdit shows the room quick-add only for new jobs; editing an existing job shows the archive switch instead.

## 2026-10-16 — Scope change: panel profile, insights, load estimate (Nate)
- AC-9 rewritten: a two-pole breaker is one cell spanning both rows that still shows both slot numbers and a 2-pole marker. Reason: must read as one device occupying two positions.
- New milestone M2.5 (F20–F22, AC-101..AC-118, tasks T30a–T30c) between M2 and M3. Reason: a filled-out panel should tell the owner something — capacity, tandem/circuit-count legality, known-hazard brands, where GFCI/AFCI protection is expected, which dedicated circuits are missing from the labels, and a NEC 220.82 load estimate against the service size. Rules are informational ("verify") never prescriptive; every card cites its NEC section; disclaimer on the sheet.
- Sources used for the rules: 2023 NEC 210.8(A) GFCI locations, 210.12(B) AFCI locations, 210.11(C) required branch circuits, 220.82 optional dwelling calculation, 408.54 overcurrent-device limits; home-inspector guidance on tandem (CTL) breakers and on FPE Stab-Lok, Zinsco/Sylvania, Challenger, Bulldog Pushmatic and split-bus panels.

## 2026-10-17 — kotlinx-serialization for `.homerecord` (T26)
Added `kotlinx-serialization-json` 1.9.0 and the serialization compiler plugin (both on the approved list) to `:core`. Core model classes are annotated `@Serializable` directly instead of keeping parallel DTOs; `Source` uses `@SerialName` for the wire values. The reader ignores unknown keys (tolerant of newer writers) but rejects `formatVersion` > 1 before decoding. Media travels only as direct children of `media/`; the reader extracts to a staging dir and skips unsafe names.

## 2026-10-17 (T27)
- FL-Handoff finishes count is always 0: the Finish entity has no storage or screen yet, so there is nothing to count. Panels are listed as an extra row ("panel changes" in the screen table). The handoff PDF is a counts-only summary rendered through `ReportPdfWriter`.

## 2026-10-17 — T29 import entry points
HM-Import opens from a Home hub card now (Settings screen is T34) and from an ACTION_VIEW filter (content scheme, application/octet-stream, `.homerecord` path pattern). A shared URI is parked in `AppContainer.pendingImportUri` and consumed once by the import view model. Staging extraction goes to `cacheDir/import-<uuid>` and is deleted after apply/cancel.

## 2026-10-17 — T30a panel profile and room kinds
- Room DB bumped to v3 with the existing destructive fallback (nothing is released; no migration to maintain). New columns: Panel profile fields, Breaker.isMain, Room.kind, Home.squareFeet. All new model fields have defaults so older `.homerecord` bundles still decode.
- The main breaker keeps its normal slot(s) (so slot occupancy rules are untouched). It is shown in a "Main breaker" banner above the grid and tagged MAIN in its grid cell; excluded-from-circuit-count is applied by the T30b insights rules.
- Saving a breaker with isMain clears the flag on the panel's previous main inside one transaction; the breaker editor shows the replacement message before Save. Tandem halves cannot be main.
- Quick-add and custom rooms both go through `RoomKindMapper` (keyword match); unmatched names become OTHER, editable on HM-Room.
- Feed breaker: deleting the feeding breaker or its panel clears `feedBreakerId` on the sub-panel. HM-Panel still shows one panel until T37, so the feed picker is empty (with a hint) until a second panel exists.
- Home.squareFeet has no UI yet (T30c form prefill).

## 2026-10-17 — T30b panel insights
Rules are pure functions in `:core` (`PanelInsights`) returning typed `Insight` values; the UI maps them to strings. Keyword matching is whole-word (optional plural) so "Orange" never matches "range" and "Square D" never matches "fpe". Interpretations are listed in BLOCKERS.md.

## 2026-10-17 — T31 maintenance tasks
- `maintenance_task` table (DB v5, destructive fallback, pre-release). Equipment FK cascades so deleting equipment deletes its tasks (AC-85).
- Pure rules in `DueDateMath` (interval 1–24, first due with roll-forward, Done/Snooze math ready for T32) and `SeasonalTaskSeeder` (per-zone templates plus common ones).
- Not added to the bundle: spec does not list MaintenanceTask in the `.homerecord` format.
- 2026-10-17: WorkManager 2.10.5 added (approved list). App implements Configuration.Provider with the default initializer removed from the manifest, so scheduling also works under Robolectric. Reminder check runs daily with an initial delay to 08:00 local (KEEP policy).

## 2026-10-17 — Encrypted backup format (T34)
AES-256-GCM via javax.crypto, key from PBKDF2WithHmacSHA256 (210k iterations, 16-byte salt, 12-byte IV), file = `HRENC001` magic + iterations + salt + IV + ciphertext/tag, magic used as AAD. Encrypts the whole `.homerecord` zip so plain bundles stay unchanged. No new dependency.

## 2026-10-17 T35
- Billing boundary lives in :core (`Entitlements`); `LimitGate` reads counts from repositories. Paywall is skipped when no breaker has a label (AC-93). Until T36 the app binds `UnavailableEntitlements`, so the paywall shows the AC-96 state rather than fake prices.
- **2026-10-17** T36: RevenueCat `purchases` 10.28.1 in :house-manual only (RevenueCatEntitlements is the sole SDK importer, AC-95). API key read from gitignored local.properties `revenuecat.apiKey` into BuildConfig; blank key falls back to UnavailableEntitlements. Reason: key must never be committed; app must not crash without it (AC-96).

## 2026-10-18 (S7 Performance)
- Added `DebugStrictMode` (core-ui), enabled from both Application classes only when the app is debuggable. Policy is detectAll + penaltyLog (not penaltyDeath) so QA can read violations from logcat tag `StrictMode` without crashing. Audit found photo decoding already runs on Dispatchers.IO, Room is opened lazily and queried via suspend/Flow, no runBlocking in main code. Cold-start timing needs an emulator and is left to QA.

## 2026-10-18 — RevenueCat account and key handling

- RevenueCat project HomeRecord (`b05900f1`) with one Play Store app (House Manual) and the built-in Test Store; entitlement `pro`; offering `default` with `$rc_annual` → `pro_annual` and `$rc_lifetime` → `pro_lifetime`. Details and keys in `docs/billing.md`.
- Debug builds use the RevenueCat Test Store key so the paywall and purchase flow can be exercised on the emulator (nightly QA) before Play Console exists; release builds use the Play key. Reason: Play products and the service-account JSON wait on the Play Console account, and QA should not be blocked on that.
- Keys stay out of source (local.properties on the PC, environment variables in the cloud builder). RevenueCat public SDK keys are designed to ship in the APK, so this is hygiene, not secrecy; a secret key (`sk_`) never goes anywhere in the repo.
- Field Log stays free in v1 (no billing module).

## 2026-10-18 — Testing APKs from GitHub Actions

- `.github/workflows/testing-apks.yml` builds debug and release (R8) APKs of both apps on every push to `build`, runs the unit tests first, and republishes the rolling pre-release `testing` (https://github.com/overconfidence7/home-record/releases/tag/testing). Reason: Nate wants to install and test on a phone without Android Studio or the cloud builder in the loop.
- Both variants are signed with a throwaway keystore kept in the Actions cache (`testing-keystore-v1`) so builds install over each other. Store builds keep the real key from `docs/release.md`.
- SDK layout on the runner: leave `platforms/android-37.0` as sdkmanager installs it and pre-install `build-tools;35.0.0`. Copying, symlinking or re-declaring an `android-37` package made AGP 8.13 re-download 37.0 and fail with "Failed to find target with hash string 'android-37'". Same conclusion the cloud builder reached; recorded here so nobody tries it a third time.
- Gradle failures in CI are surfaced as check-run annotations (`scripts/ci-gradle.sh`) because the raw log host is unreachable from the builder's proxy.

