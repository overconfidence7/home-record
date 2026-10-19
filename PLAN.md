# HomeRecord — build plan

Ordered checklist (M2.5 tasks T30a–T30c added 2026-10-16 after a scope change; see SPEC.md). Each task is about one hour of focused work. Tick a task only when its acceptance criteria (SPEC.md §8) pass and `./gradlew testDebugUnitTest` is green. After every 5 build tasks there is a review task. Log choices in `docs/decisions.md`; log each run in PROGRESS.md.

## M1 — House Manual

- [x] T01: Scaffold the Gradle project: wrapper, `gradle/libs.versions.toml`, `settings.gradle.kts` with modules `:core`, `:core-ui`, `:house-manual`, `:field-log`; Compose Material 3 theme in `:core-ui`; one empty "HomeRecord" screen in each app; a passing sample unit test in `:core`; Android `.gitignore`. (acceptance: `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` succeed)
- [x] T02: `:core` domain model and Room schema for Home, Room, Wall, Panel, Breaker, BreakerRoom, Endpoint, Shutoff, Equipment, Photo per SPEC §6, with enum converters, `HomeRecordDatabase`, DAOs and Robolectric in-memory DAO tests for insert/update/delete of each entity. (acceptance: AC-3 data part, AC-12 data part)
- [x] T03: Repository layer in `:core` (`HomeRepository`, `PanelRepository`, `ShutoffRepository`, `EquipmentRepository`) exposing Flows; `AppContainer` manual DI in each app; photo file store (`MediaStore`-free, app files dir) with path helper and tests. (acceptance: AC-6 storage part, AC-27 storage part)
- [x] T04: House Manual navigation (single activity, Navigation Compose), start-destination logic, HM-Onboarding with quick-add rooms and custom room, OnboardingViewModel tests, Compose test for Continue disabled state. (acceptance: AC-1, AC-2, AC-3, AC-4)
- [x] T04a (DEFERRED, see PROGRESS.md/BLOCKERS.md): Upgrade the build to AGP 9.x (latest stable) and the Gradle wrapper it requires (≥ 9.6): built-in Kotlin per CLAUDE.md, remove `android.suppressUnsupportedCompileSdk` from gradle.properties, move Compose BOM to the latest stable, bump Room/KSP/navigation only as far as the new Kotlin requires. Keep every existing test. If the upgrade is not green by the end of the run, push to `wip/T04a`, reset `build`, note why in PROGRESS.md and BLOCKERS.md, and tick this task as "deferred" so T05 proceeds on AGP 8.13.2. (acceptance: `./gradlew assembleDebug testDebugUnitTest lintDebug` green with no suppress flag; 31/31 tests still pass)
- [x] T05: HM-Home hub: search field, Panel/Emergency/Equipment/Rooms cards with live counts; HM-Rooms and HM-Room (rename, delete) screens. (acceptance: AC-21 room lists for equipment/shutoffs, AC-28 card)
- [x] R1: Review all changes since the last review against SPEC.md; fix bugs, missing tests, and spec drift.
  - R1 notes: fixed raw enum names shown in HM-Room (shutoff kind, equipment category) -> strings.xml labels. No other spec drift found for AC-1..4, AC-20, AC-21, AC-28. HM-Home overflow→Settings deferred to T34 (no Settings screen yet).
- [x] T06: Panel setup and slot grid: `PanelLayoutCalculator` (slot → row/column for NA_NUMBERED and DIN), HM-Panel empty state with slot count/layout, grid rendering, panel photo capture via `TakePicture`. (acceptance: AC-5, AC-6, AC-7)
- [x] T07: HM-Breaker editor: label, amps, poles, type, confidence; `SlotOccupancy` rules for two-pole and tandem; slot-count shrink guard; delete detaches endpoints. (acceptance: AC-8, AC-9, AC-10, AC-11, AC-12)
- [x] T08: Breaker links: room chips, endpoint add/remove on HM-Breaker, confidence dots on the grid. (acceptance: AC-13, AC-14, AC-15)
- [x] T09: `SearchEngine` (token match across breakers, endpoints, rooms, equipment, shutoffs), HM-Search results, highlight-and-scroll on HM-Panel, HM-Room breaker list with confidence. (acceptance: AC-17, AC-18, AC-19, AC-20)
- [x] T10: Legend PDF: `LegendRowBuilder` (pure, tested) + `PdfDocument` renderer + share sheet via FileProvider. (acceptance: AC-22, AC-23, AC-24)
- [x] R2: Review all changes since the last review against SPEC.md; fix bugs, missing tests, and spec drift.
- [x] T11: Shutoffs: HM-Shutoffs list with empty state, HM-ShutoffEdit with kind/room/photo/instructions and validation. (acceptance: AC-25, AC-26, AC-27)
- [x] T12: HM-Emergency: `EmergencyGrouper` (pure, tested), large-type layout, full-screen zoomable photo, offline check. (acceptance: AC-28, AC-29, AC-30, AC-31)
- [x] T13: Quick Settings tile: `EmergencyTileService`, `TileStateChooser`, deep link into HM-Emergency, lock-screen handling. (acceptance: AC-32, AC-33, AC-34)
- [x] T14: Mapping mode core: `MappingStateMachine` (Idle/PluggedIn/Armed/Alarm/Confirm) driven by `PowerEvent`s, `ChargingMonitor` (BatteryManager + ACTION_POWER_CONNECTED/DISCONNECTED receiver), `AlarmPlayer` (ToneGenerator/AudioTrack loop + Vibrator) behind interfaces with fakes. (acceptance: AC-35, AC-37, AC-38)
- [x] T15: HM-Mapping screen: keep-screen-on, armed and alarm states, breaker picker, endpoint picker with "New outlet…", save confirms the link; disarm on leave. (acceptance: AC-36, AC-39, AC-40, AC-41, AC-16)
- [x] R3: Review all changes since the last review against SPEC.md; fix bugs, missing tests, and spec drift.
- [x] T16: Equipment list and editor: categories, dates, `AgeAndWarrantyFormatter`, validation, delete with file cleanup. (acceptance: AC-42, AC-43, AC-44, AC-50)
- [x] T17: Data-plate OCR: `TextRecognizer` interface with ML Kit bundled implementation, `DataPlateParser` heuristics with fixture tests, result sheet with confirm/edit, empty-result path, label photo kept. (acceptance: AC-45, AC-46, AC-47, AC-48, AC-49)
- [x] T18: House Manual polish for M1: app icon, string review, empty states, back-stack sanity, `lintDebug` clean, release build config (unsigned), README screenshots list. (acceptance: re-check AC-1..AC-50 on emulator; lint has no errors)

## M2 — Field Log and the handoff

- [x] T19: Field Log app shell: navigation, `AppContainer`, FL-Jobs list with empty state and archive toggle, FL-JobEdit with rooms quick-add; Job/LogEntry/DailyReport/CompanyProfile entities + DAOs in `:core`. (acceptance: AC-51, AC-52, AC-53, AC-54)
- [x] T20: FL-Capture with CameraX: full-screen preview, 72dp shutter, JPEG to app files, PHOTO entry creation, volume-key shutter handler, permission denied state. (acceptance: AC-55, AC-56, AC-57, AC-60)
- [x] R4: Review all changes since the last review against SPEC.md; fix bugs, missing tests, and spec drift.
- [x] T21: Notes and voice: typed note entry, `VoiceRecorder` (MediaRecorder AAC/.m4a) behind an interface with a 3:00 cap, VOICE entries with duration, mic permission state. (acceptance: AC-58, AC-59, AC-60)
- [x] T22: FL-Job day grouping (`EntryGrouper`), thumbnails, note text, audio duration and playback, FL-Entry edit/delete, crash-safety check. (acceptance: AC-61, AC-62)
- [x] T23: FL-BehindWall: room → wall picker (N/E/S/W/custom → Wall rows), camera reuse, Photo rows with stage BEHIND_WALL, running counts. (acceptance: AC-63, AC-64, AC-65)
- [x] T24: FL-Report: `ReportDraftBuilder` prefill from entries, editable draft persisted as DailyReport, delay reason picker, FL-Settings company profile. (acceptance: AC-67, AC-68)
- [x] T25: Report PDF: `ReportSectionBuilder` (pure), `PdfDocument` renderer with header and two-per-row photos, overwrite per day, share sheet. (acceptance: AC-69, AC-70)
- [x] R5: Review all changes since the last review against SPEC.md; fix bugs, missing tests, and spec drift.
- [x] T26: `.homerecord` bundle format in `:core`: `RecordJson` (kotlinx.serialization), `BundleWriter` (zip + media), `BundleReader` with formatVersion guard and path sanitizer, JVM round-trip tests. (acceptance: AC-72, AC-73, AC-74, AC-91)
- [x] T27: FL-Handoff screen: counts, export via share sheet, PDF summary of the handoff. (acceptance: AC-71, AC-72)
- [x] T28: Import engine in `:core`: `ImportPlanner` (counts, conflicts, homeId remap), `ImportApplier` with keep-mine/use-imported choices, idempotent re-import. (acceptance: AC-76, AC-77, AC-78, AC-79)
- [x] T29: HM-Import screen and `ACTION_VIEW` intent filter for `.homerecord`; end-to-end check: Field Log export → House Manual import → behind-wall photos visible in HM-Room. (acceptance: AC-75, AC-66, AC-77)
- [x] T30: M2 polish: Field Log icon, glove-size audit of FL-Capture targets (≥56dp), lint clean, string review. (acceptance: re-check AC-51..AC-79 on emulator)
- [x] T30a: Panel profile and room kinds (SPEC F20): add Panel.kind/ratingAmps/brand/model/maxCircuits/tandemPolicy/hasMainBreaker/yearInstalled/feedBreakerId, Breaker.isMain, Room.kind, Home.squareFeet with a Room DB migration (or a version bump with destructive migration, recorded in decisions.md — nothing is released yet); `RoomKindMapper` for quick-add names; panel setup/edit form with the brand suggestion list and the sub-panel feed-breaker picker; main-breaker option on HM-Breaker; legend PDF header; bundle export/import of the new fields with a tolerant reader. (acceptance: AC-101, AC-102, AC-103, AC-104, AC-105)
- [x] T30b: Panel insights (SPEC F21): `PanelInsights` rules in `:core` — capacity, tandem/circuit-count checks, `HazardBrandMatcher`, `ProtectionRules` (GFCI 210.8(A) and AFCI 210.12(B) by room kind), `DedicatedCircuitChecklist` with keyword fixtures — each a pure function with tests; "Panel insights" sheet on HM-Panel rendering severity cards and the disclaimer. (acceptance: AC-106, AC-107, AC-108, AC-109, AC-110, AC-111, AC-112, AC-113)
- [x] T30c: Load estimate (SPEC F22): LoadProfile entity and DAO, appliance defaults table, `LoadEstimator` implementing NEC 220.82 with the worked-example test, estimate form and result card inside Panel insights. (acceptance: AC-114, AC-115, AC-116, AC-117, AC-118)
- [x] R6: Review all changes since the last review against SPEC.md; fix bugs, missing tests, and spec drift.

## M3 — Keep, protect, pay

- [x] T31: Reminders data: MaintenanceTask entity, `SeasonalTaskSeeder` per climate zone, equipment interval tasks, due-date math with tests. (acceptance: AC-80, AC-81, AC-85)
  - [x] T31a: "Remind me every N months" field (1–24) on HM-EquipmentEdit calling `MaintenanceRepository.setEquipmentReminder` on save (AC-81 UI); do it with T32.
  - [x] T31b: climate-zone picker calling `MaintenanceRepository.applyClimateZone` (AC-80 UI); belongs on HM-Settings (T34) and, until then, the Reminders screen (T32).
- [x] T32: Reminders screen (Done/Snooze), WorkManager daily check, notification with deep link, permission request on first task. (acceptance: AC-82, AC-83, AC-84)
- [x] T33: Emergency widget (Glance or RemoteViews) with shutoff count and update on change. (acceptance: AC-86, AC-87)
  - Built with RemoteViews (no new dependency); flaky PanelViewModelTest AC-106 test failed once on a cold run, passed on rerun — look at it in R7.
- [x] T34: Encrypted backup: `BundleCrypto` (AES-256-GCM, PBKDF2), export from HM-Settings, import detection and passphrase prompt, restore test. (acceptance: AC-88, AC-89, AC-90)
- [x] T35: `Entitlements` interface + `FakeEntitlements`, `FreeLimits` checker, paywall screen with yearly/lifetime placeholders, limit gates on panel and equipment save. (acceptance: AC-92, AC-93, AC-94, AC-95)
  - Second-panel gate (`LimitGate.check(ADD_PANEL)`) is ready but has no caller until T37 adds the add-sub-panel UI; app uses `UnavailableEntitlements` (free tier, paywall shows Retry state) until T36.
- [x] R7: Review all changes since the last review against SPEC.md; fix bugs, missing tests, and spec drift.
- [x] T36: RevenueCat implementation of `Entitlements` (API key from `local.properties`/BuildConfig, never committed), offline state, Retry. (acceptance: AC-95, AC-96)
  - Implemented, but real purchases stay unverified until Nate supplies the RevenueCat key (BLOCKERS.md). Without `revenuecat.apiKey` the app runs the free tier with the Retry paywall.
- [x] T37: Multiple panels: panel switcher, per-panel grid, search and legend across panels. (acceptance: AC-97, AC-98)
- [x] T38: Consumables per equipment and search over specs. (acceptance: AC-99, AC-100)
- [x] T39: Release prep: signing config placeholders, versionCode scheme, Data safety notes in `docs/data-safety.md`, privacy policy draft in `docs/privacy-policy.md`, store listing drafts. (acceptance: `./gradlew assembleRelease` succeeds unsigned)
- [x] T40 (DEFERRED to nightly QA, see note): Full regression on emulator against AC-1..AC-100; update REVIEW.md and qa/last-tested.txt. (acceptance: all ACs checked)
  - The builder has no emulator; QA owns REVIEW.md entries and qa/last-tested.txt. QA should run the full AC-1..AC-100 pass against build HEAD.
- [x] R8: Review all changes since the last review against SPEC.md; fix bugs, missing tests, and spec drift.

  - R3 note: reviewed T11–T15 against AC-25..AC-41; no spec drift or bugs found. Recurring warning: tests use deprecated `createComposeRule` (v2 variant suggested); to be tidied in S1.
- [ ] T41: Make the backup complete: include Consumable, MaintenanceTask and LoadProfile rows in the `.homerecord` bundle, the encrypted backup and import (new optional arrays; older bundles still import; the Field Log handoff export may omit MaintenanceTask/LoadProfile); round-trip tests. Follow-up from the T31/T34/T38 decisions. (acceptance: AC-72, AC-73, AC-79, AC-88, AC-90 re-verified with all three present; restoring a backup onto a fresh install brings back reminders and the load estimate)
