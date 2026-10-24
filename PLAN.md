# Build plan

Ordered tasks, about one focused hour each. The builder ticks a task only when its acceptance criteria pass and `./gradlew assembleDebug testDebugUnitTest lintDebug` (and `npm test` in `backend/` for backend tasks) are green. After every five build tasks there is a review task (R) and after every milestone a visual review (V) that looks at the committed screenshot PNGs against `docs/ui-standards.md`. Format: `- [x] T012: <what> (acceptance: AC-C5, AC-C6)`.

The builder may append notes and new subtasks under a task (as `  - [ ] T012a: ...`) and never reorders or deletes tasks. Tasks the builder cannot do are marked `(blocked: see BLOCKERS)` and skipped.

## M0 Foundation

- [x] T001: Scaffold the Gradle project: wrapper (Gradle 9.x), version catalog with every approved dependency from CLAUDE.md, modules `:core`, `:core-ui`, `:house-model`, `:house-manual`, `:field-log`, Compose + Material 3 1.4 + Navigation 3, AGP 9.x built-in Kotlin, KSP, Room, screenshot-test plugin, one empty screen per app, a passing sample test in each module, `.gitignore`, `README.md` links (acceptance: builds, both apps launch)
- [x] T002: Design system in `:core-ui`: `MaterialExpressiveTheme` with dynamic color + brand fallbacks (HM blue/teal, FL orange/amber), typography, shapes, `MotionScheme.standard()`, `enableEdgeToEdge`, light/dark window themes, adaptive launcher icons for both apps (acceptance: AC-U8, AC-U9)
- [x] T003: App shell in `:core-ui`: `HomeRecordApp` with `NavigationSuiteScaffold`, per-tab `NavDisplay` back stacks (Navigation 3, saveable), `ScreenScaffold` with top app bar + back arrow, predictive back wired; House Manual tabs Home/House/Items/Reminders/More and Field Log tabs Today/Jobs/Clock/Schedule/More with placeholder screens (acceptance: AC-U1, AC-U2)
- [x] T004: Core-ui components: `RecordListItem`, `EmptyState`/`LoadingState`/`ErrorState`, `LinkChipRow`, `FormScaffold` with discard prompt, `LabeledField`, `DateField`, `MoneyField`, `LengthField` + `LengthParser` `[unit]`, `EnumPicker`, `QuickAddSheet`, `OptionsSheet`, previews for each (acceptance: AC-U3..U7, AC-C4)
- [x] T005: Screenshot testing: `com.android.compose.screenshot` plugin in every UI module, `@Preview` conventions (light, dark, 200% font), `updateDebugScreenshotTest` wired, reference PNGs committed for the shells and components; CI workflow (`.github/workflows/testing-apks.yml`) updated for the new module names and to run `validateDebugScreenshotTest` (acceptance: AC-U12)
- [x] T005a (done in T011 for `:house-manual`; `:field-log` already had it): Apply the screenshot plugin to `:house-manual` and `:field-log` and add screenshot tests for each app's screens as they land (the plugin fails a module with no @PreviewTest, so it is applied with the first real screen)
- [x] R01: Review T001–T005 against SPEC.md sections 2, 3, 8 and `docs/ui-standards.md`; fix drift, missing previews, missing tests
- [x] T006: `:core` data foundation: Room database `homerecord.db` with every entity in SPEC.md section 9 (house model + Field Log), DAOs, converters, soft delete, `updatedAt` triggers in repositories, `ItemCategory` taxonomy with icons and defaults `[unit]` (acceptance: AC-C29, section 9)
- [x] T007: Backend skeleton in `backend/`: Hono Worker, `wrangler.toml` (D1, R2, KV bindings), D1 migrations for tenancy tables, `/health`, `/me`, JWT sessions, vitest with workers pool, `npm test` green, README with the secrets list (acceptance: AC-B1 partial, AC-B2, AC-B60)
- [x] T008 (split: T008a and T008b done, T008c open): Auth end to end: Sign in with Google (Credential Manager) and email code (Resend; dev mode logs the code) in both apps (`:core` `AuthRepository`, secure token store), backend verification, `/me` returns memberships (acceptance: AC-B1, AC-B2, AC-H15 sign-in part, AC-F4)
- [x] T008a: Backend `/auth/email/start`, `/auth/email/verify`, `/auth/google` (Resend via fetch, dev mode logs code, Google ID token verified with jose JWKS), `[unit]` tests (acceptance: AC-B1)
- [x] T008b: `:core` `AuthRepository` with `AuthApi`, `TokenStore`, `GoogleCredentialSource` interfaces, fakes and tests (acceptance: AC-F4 logic)
- [x] T008c: Android implementations: Retrofit `AuthApi` (reads `X-Refreshed-Token`), `EncryptedSharedPreferences` `TokenStore`, Credential Manager `GoogleCredentialSource`, HM-Account and FL sign-in screens with email-code flow and previews, `AppContainer` wiring (acceptance: AC-B1, AC-H15 sign-in part, AC-F4)
- [x] T009: Field Log company and team: create company, invite code and link, join, roles, FL-Team, FL-Company settings; backend endpoints with role enforcement tests (acceptance: AC-F1, AC-F2, AC-F3, AC-B3)
- [x] T010: Sync engine in `:core`: outbox, push/pull, conflict rules, device id, `SyncState`; backend `/sync/push` and `/sync/pull` with `serverSeq` and idempotency; `SyncStatusChip` (acceptance: AC-F53, AC-B20)
- [x] T010a: Wire `SyncEngine` into both `AppContainer`s (device id, `RetrofitSyncApi`, sync on sign-in, after local writes, on reconnect and every 30 s while open) and show `SyncStatusChip` on Today; repositories call `recordChange` after each write (lands with the first repository that writes synced entities, T011)
- [x] R02: Review T006–T010; run a two-device sync scenario in unit tests (two in-memory databases through a fake server)
- [x] V0: Visual review of M0: open every committed screenshot PNG, compare with `docs/ui-standards.md` rules 1–12 and 24–30, fix what fails, record in REVIEW.md under "Builder visual review"

## Q QA infrastructure (added 2026-10-20; do these before continuing M1)

The automated suite (`qa/README.md`, `.github/workflows/qa.yml`) runs four times a day. These tasks give it something to run and clear the coverage findings it files for T001–T015. Each is about an hour.

- [x] Q001: Test tags: `Modifier.semantics { testTagsAsResourceId = true }` on both app roots; stable `testTag`s (`<app>_<screen>_<element>`) on every interactive element that exists today (tabs, back arrows, FABs, top-bar actions, list rows as `row_<id>`, form fields, primary buttons, plan editor controls, onboarding pickers); the tag names listed in `qa/testtags.md` per screen so flows can use ids instead of text (acceptance: AC-U12)
- [x] Q001a: Remaining Q001 tags (shell done: root `testTagsAsResourceId`, `<app>_tab_<id>`, `<app>_back`, `qa/testtags.md`): onboarding pickers, floors, blueprint (layer chips, floor chips, edit plan), plan editor controls, FL welcome/team/company/sign-in/more; list rows `row_<id>`; document each in `qa/testtags.md`
- [x] Q002: Screen catalog and crawler: `ScreenCatalog` object per app (debug source set) listing every screen with realistic fake state; a debug-only intent `<pkg>.qa.OPEN_SCREEN` (extra `screen=<id>`) that opens a catalog screen with that state; an instrumented crawler test in each app's `src/androidTest` that, for every catalog entry, opens it, asserts a top app bar with the back arrow on non-root screens and a bottom bar on roots, runs Compose accessibility checks (content descriptions, 48 dp targets), captures light, dark, 200% and landscape screenshots to `/sdcard/Download/qa/<screen>-<variant>.png`, and reports as JUnit (acceptance: AC-U1, AC-U2, AC-U4, AC-U8, AC-U9, AC-U10, AC-U12)
- [x] Q003: Acceptance test tagging: `@AcceptanceCriterion("AC-…")` annotation in `:core` test fixtures (usable from JVM and instrumented tests); tag every existing JVM test with the AC it proves; add instrumented acceptance tests in `src/androidTest` for the non-unit ACs claimed by T001–T015 (house types seeding floors on screen, floor management, plan editor add/move/resize/undo, polygon edit, doors/windows/stairs, blueprint layers, room tap and long press, onboarding, tabs); `python3 qa/scripts/coverage-check.py` reports 0 gaps (acceptance: AC-C1, AC-C2, AC-C3, AC-C5, AC-C6, AC-C7, AC-C9, AC-C10, AC-C11, AC-C12, AC-C13, AC-C14, AC-C15, AC-C29, AC-U3, AC-U5, AC-U6, AC-U7)
- [ ] Q004: Flows for everything built so far: fix and extend `qa/flows/house-manual/H01–H03` and `_tour` to the real screens (use the Q001 ids), add `H02b-shape-doors-stairs.yaml`; extend `qa/flows/field-log/F01-welcome.yaml` with the dev sign-in path and add `F02-invite-join.yaml` (second account via the backend dev seed, role change, remove) and `_tour`; focus-guard assertions in both; every flow passes locally against an emulator before commit (acceptance: AC-C4, AC-F1, AC-F2, AC-F3, AC-F4, AC-H15)
- [x] Q005: Debug seed and dev backend: broadcast receiver `<pkg>.qa.SEED` in debug builds with scenario `regression` (House Manual: a two-story home with basement, 12 placed rooms, doors, windows, stairs; Field Log: a company "QA Electric" with Owner + Crew members and one job) and `reset`; backend routes under `/dev/*` enabled only when `ENVIRONMENT=dev` (`GET /dev/last-code?email=`, `POST /dev/seed`, `POST /dev/reset`), with tests proving they are absent otherwise; `qa/fixtures/` filled per its README (acceptance: AC-B2, AC-B3, AC-B20, AC-B60, AC-F53)
- [x] Q005a: Backend half of Q005: `/dev/last-code`, `/dev/seed`, `/dev/reset` only when `ENVIRONMENT=dev` (acceptance: AC-B2)
- [x] Q005b: Android half of Q005: debug `<pkg>.qa.SEED` receivers (`regression`, `reset`) for both apps (Field Log seed signs in as the dev-backend owner; its job arrives with T018)
- [x] Q005c: `qa/fixtures/` files per its README (images and plan.pdf), then tick Q005
- [ ] Q006: First green run: read `origin/qa-results:latest/summary.md` and the `## QA auto` entries in REVIEW.md, fix every Critical and Major the suite found (app bugs, flow mistakes, coverage gaps), and record in PROGRESS.md which stages still skip and why (acceptance: newest `## QA auto` entry ends with `New issues: 0`)

## M1 House model

- [x] T011: Home creation and house types: `HouseTypeTemplate` `[unit]`, HM-Onboarding (name, type, foundation, garage), floors seeded, floor management screen (acceptance: AC-C1, AC-C2, AC-C3)
- [x] T012: Plan geometry in `:house-model`: `PlanModel` (rooms as polygons in inches), grid snapping, neighbor-edge snapping, `WallDeriver` `[unit]`, `PlanValidator` `[unit]`, undo/redo stack `[unit]` (acceptance: AC-C8, AC-C11, AC-C10 logic)
- [x] T013: Plan editor screen: Canvas rendering, pinch zoom and pan, floor switcher, Add room (rectangle, name, kind, typed W×L), drag to move, edge handles to resize, selection, delete, autosave on leave, saved-state across rotation (acceptance: AC-C5, AC-C6, AC-C10, AC-C12)
- [x] T014a: Polygon rooms: Edit shape mode (add corner on edge, drag, delete corner), L-shapes (acceptance: AC-C7)
- [x] T014b: Doors, windows and stairs placement on walls/rooms with floor link, persisted as opening/stair rows (acceptance: AC-C9)
- [x] T015: Blueprint view: clean plan renderer (walls, names, dimensions, doors, windows, stairs, north arrow), room tap → room page, long-press sheet, layer toggles persisted per home, light/dark/200% (acceptance: AC-C13, AC-C14, AC-C15)
- [x] R03: Review T011–T015; geometry edge cases (concave rooms, shared walls between a rectangle and a polygon, zero-length edges)
- [x] T016: Blueprint PDF export (one page per floor, to scale, scale bar, home name) through the share sheet (acceptance: AC-C16)
- [x] T017: HM-House tab and HM-Room skeleton: floor chips, blueprint, Edit plan / 3D (placeholder) / Export actions; HM-Room with sections (Items, Walls, Photos, Overlays, Breakers, Shutoffs, Notes) as empty states; Add room FAB inside the editor (acceptance: AC-H4, AC-H10 shell)
  - [ ] T017a: 3D placeholder action on HM-House is deferred to the 3D task (a dead button would be a placeholder); HM-Room sections gain real lists as items, overlays, breakers and shutoffs land
- [x] T018: Field Log jobs with rooms: FL-Jobs list, FL-JobEdit (client, address with on-device Geocoder → lat/lng, rooms chips, dates, crew, phases, budget, office emails), FL-Job Overview tab and tab shell, `JobOrdering` `[unit]`, sync (acceptance: AC-F5, AC-F6, AC-F7)
  - [x] T018a: `JobOrdering` + `JobSummary` in `:core` model with unit tests (AC-F6); jobs list, JobEdit, Overview and sync remain under T018
  - [x] T018b: FL-Jobs list on the Jobs tab (grouped Active/Upcoming/Done, search, show archived, next date, client and address), `JobListMapper` and `JobsViewModel` with tests (AC-F6); no New job button until JobEdit
  - [x] T018c1: FL-JobEdit form (name, client by name reused case-insensitively, address, dates, budget hours, notes) with New job FAB on FL-Jobs; saves offline and queues job+client for sync (AC-F5); tapping a row opens the form until the Overview shell exists
  - [x] T018c2a: rooms chips on FL-JobEdit; rooms become unplaced rooms (empty polygon) on a home created for the job, job.homeId set (AC-F5, AC-F48)
  - [x] T018c2b1: address geocode: `Geocoder` interface + `AndroidGeocoder` in :core, `JobGeocoder` resolves pending jobs after save and when the network returns, queues the job (AC-F5)
  - [x] T018c2b2a: Open in Maps link on FL-JobEdit once the address has resolved (AC-F5)
  - [x] T018c2b2a2: phases and office emails chips on FL-JobEdit, stored as JSON on the job (AC-F5)
  - [x] T018c2b2: crew done in T018c3b1; home/floor/room rows created for a job now sync under the company tenant (backend accepts them, JobEdit queues them)
  - [x] T018c3a: FL-Job tab shell (eight tabs, only Overview built) and Overview (status, address, map link, dates, phases, hours this week from punches via `JobHours`, photos, open tasks, next day, crew; stats open their tab) (AC-F7)
  - [x] T018c3b1: crew picker on JobEdit (cached team list, job_crew rows queued for sync)
  - [x] T018c3b: sync of job rows verified against backend (backend test AC-F5); Photos, Log, Files, Tasks, Time, House, Changes tabs arrive with T019+ and later tasks
  - [x] (old) T018c: FL-JobEdit form (client, address geocode, rooms chips, dates, crew, phases, budget, notes) with New job FAB on FL-Jobs, then FL-Job tab shell and Overview (AC-F5, AC-F7), then sync of job rows
- [x] T019: FL-House tab: the same plan editor and blueprint bound to the job's home; unplaced rooms from FL-JobEdit shown in a tray to drag onto the plan (acceptance: AC-F48)
  - [x] T019a: HM-Room page shared with FL-House (RoomScreen/RoomRoute and room strings moved to :house-model; tapping a room on the job plan opens it)
- [x] T020: Search engine `[unit]` (rooms, items, overlays, breakers, documents, contacts, consumable specs) and the HM-Home search bar with results list (acceptance: AC-C38, AC-H3 rooms part)
  - [x] T020a: `SearchEngine` in :core model (ranked prefix/word/substring, all tokens required) with SearchEngineTest (AC-C38)
  - [x] T020b: HM-Home search bar and results list, building SearchDocuments from rooms/items/breakers etc. (blocked on HM-Home screen, still a placeholder tab; AC-H3)
- [x] R04: Review T016–T020 (T020b still blocked on T030; reviewed what exists)
- [x] V1: Visual review of M1 screenshots (sampled; one Minor logged)

## M2 Items and warranty (House Manual usable)

- [x] T021: Item model and HM-ItemEdit form: category/subtype picker (grouped sheet with icons), room, name, make/model/serial, dates, price, vendor, notes, photos (data plate, item), `FormScaffold`; save creates default reminders and consumables from the subtype (acceptance: AC-C30, AC-C35 defaults)
  - [x] T021a: `ItemDraft` validation and `ItemRepository` (save, edit, soft delete, pseudo-rooms Exterior/Whole house, default consumables and reminders from the subtype) in :house-model, ItemRepositoryTest 6 (AC-C30, AC-C35)
  - [x] T021b: HM-ItemEdit screen: grouped subtype picker with icons, room picker, fields, date pickers, photos; ScreenCatalog entry, previews, screenshot test, flow (AC-C30)
  - [x] T021c: item photos (data plate, item) on HM-ItemEdit, behind a camera interface (AC-C30)
- [x] T022: Data plate OCR: ML Kit text recognition (bundled), recognized lines sheet, tap-to-assign, `DataPlateParser` `[unit]`, works offline (acceptance: AC-C31)
  - [x] T022a: `DataPlateParser` in :core model (labelled model/serial, next-line values, whole-word brand match) with DataPlateParserTest 4 (AC-C31)
  - [x] T022b: ML Kit bundled text recognition behind a `TextRecognizer` interface in :core, recognized-lines sheet with tap-to-assign on HM-ItemEdit (Scan data plate), camera/gallery, offline (AC-C31)
- [x] T023a (partial T023): `SerialAgeDecoder` `[unit]` in :core model with per-brand tests (AC-C32)
- [x] T023: `SerialAgeDecoder` `[unit]` for the brands in AC-C32 with documented rules and tests per brand; "manufactured on" fill with override (acceptance: AC-C32)
- [x] T024: Warranty record and status: `Warranty` entity, `WarrantyStatus` `[unit]` (expired / expiring 60 days / active / unknown), `WarrantyStatusLine` component, item page warranty section with Registered toggle and source link (acceptance: AC-C33)
  - [x] T024a: `WarrantyStatus` (Unknown/Active/Expiring 60 days/Expired, ends-on from in-service date + longest term) in :core model with WarrantyStatusTest 6 (AC-C33); `WarrantyStatusLine` component in :core-ui with previews and screenshots
  - [x] T024b: item page warranty section (terms, Registered toggle, source link) arrives with HM-Item in T027 (AC-C33)
- [x] T025: Backend warranty lookup: `POST /lookup/warranty` with Claude API web search (allowed domains, max 3 searches, structured JSON), KV cache by normalized make+model `[unit]`, per-account rate limit, cost log; vitest with a recorded response (acceptance: AC-B10)
- [x] R05: Review T021–T025
- [x] T026: Warranty lookup in the app: automatic on first save when online, "Look up warranty" and "Look up again", confirm/edit/discard sheet, offline queue with badge, stored once (acceptance: AC-C34, AC-H5)
  - [x] T026a: `WarrantyLookupApi` (Retrofit, years→months) in :core and `WarrantyLookupRepository` in :house-model (lookup once, force for Look up again, offline PENDING queue, runPending → REVIEW rows as the badge, confirm/edit/discard, ends-on from in-service date) with tests (AC-C34)
  - [x] T026b: confirm/edit/discard sheet, Look up warranty / Look up again buttons, auto attempt on first save, badge and runPending on reconnect; needs the item page from T027 (AC-C34, AC-H5)
- [ ] T027: HM-Items tab: grouped by category with counts, by-room toggle, filter chips (category, room, warranty status), search, FAB; HM-Item detail screen with hero photo and all sections; overflow Move/Duplicate/Delete (acceptance: AC-H6, AC-H7 chips shell)
  - [x] T027a: Items tab list: `ItemListBuilder` `[unit]` (group by category/room with counts, filter category, room, warranty status, search), ItemsViewModel (state survives process death), ItemsScreen with previews and screenshots; row tap opens the item form until the detail screen exists (AC-H6)
  - [x] T027b: HM-Item detail screen: hero photo, status line, Details, Warranty (terms, source link, looked-up date, Registered toggle), Photos, Notes, Edit, overflow Duplicate/Delete with confirm; Items row opens it (AC-H7, AC-C33)
  - [ ] T027c: item page Move to room, Consumables, Reminders, Service history, Documents, Links (Breaker, Shutoff, Overlays) sections, Share; arrive with T028/T029/T031 and the breaker work (AC-H7)
- [x] T028: Consumables, service history, contacts: consumables editor and search, service record form, HM-Contacts with tap-to-call/email and links to items (acceptance: AC-C35, AC-C36, AC-C39)
  - [x] T028a: `UpkeepRepository` in :house-model (consumable, service record and contact drafts with validation, save/edit/delete, service newest first, contacts with serviced items, delete unlinks records) (AC-C35, AC-C36, AC-C39)
  - [x] T028b: consumables editor on the item page (kind, spec, quantity, reorder link) and service history section with the service record form (AC-C35, AC-C36)
  - [x] T028c: HM-Contacts screen (More tab) with tap-to-call and email, contact form, items serviced list, contact picker on the service form (AC-C39)
    - [x] T028c1: Contacts screen from the More tab (cards with call/email, form sheet, items serviced, delete with confirm)
    - [x] T028c2: contact picker on the service form (AC-C39)
- [x] T029: Documents: `Document` entity, HM-Documents folders, upload from Files and camera scan (ML Kit document scanner), in-app PDF/image viewer with pinch zoom, attach to home/room/item, share (acceptance: AC-C37)
  - [x] T029a: `DocumentRepository` and `DocumentFiles` interface (add PDF/image, folders, rename, move, attach to home/room/item, delete) with tests (AC-C37)
  - [x] T029b: Android `DocumentFiles` impl, HM-Documents screen (folders, upload from Files, rename/move/delete), More entry
  - [x] T029b2: camera scan into Documents with the ML Kit document scanner (AC-C37)
  - [x] T029c: in-app PDF/image viewer with pinch zoom and share (AC-C37)
  - [x] T029d: item page Documents section: list, attach existing, add from Files/scan, open in viewer (AC-C37)
- [x] T030: HM-Home: search bar, Emergency card (count), "Needs attention" rows, Recent items and photos, quick actions (Add item, Scan data plate, Photograph a wall → placeholder until M4) (acceptance: AC-H1, AC-H2, AC-H3)
  - [x] T030a: `HomeSummaryBuilder` `[unit]` (overdue reminders, warranties expiring in 60 days, items missing make/model, shutoff count, recent items) and `ItemRepository.observeHomeSummary` (AC-H2)
  - [x] T030c: HM-Home search bar and results (items by model/serial/consumable spec, rooms, contacts, documents; breakers/overlays join when built) (AC-H3)
  - [x] T030b: HM-Home screen: quick actions, Emergency card, Needs attention (one line when empty), Recent; Photograph a wall shows a note until M4 (AC-H1, AC-H2)
  - [ ] T030c: Home search bar and results (= T020b), reminder rows opening the reminder once the Reminders tab exists (AC-H3)
- [x] R06: Review T026–T030; run the House Manual walkthrough steps 1–5 of PRODUCT.md mentally against the screens and list gaps in BLOCKERS "Decisions to confirm"
- [x] T031: Backend data plate fallback and recalls: `POST /lookup/dataplate` (Claude vision) used when on-device OCR finds no model; `GET /recalls` CPSC proxy with cache; item page shows "Check recalls" result (acceptance: AC-B11, AC-B12)
  - [x] T031a: backend `POST /lookup/dataplate` (Claude vision, shared quota, cost log) and `GET /recalls` (CPSC proxy, 24 h KV cache, model matches first) with vitest (AC-B11, AC-B12)
  - [x] T031b: app side: data-plate scan falls back to `/lookup/dataplate` when OCR finds no model; item page "Check recalls" button and result list (AC-B11, AC-B12)
- [ ] T032: Free limits and Pro for House Manual: `Entitlements` interface, RevenueCat SDK, `LimitGate` `[unit]` (1 home, 15 items, 1 panel, sync), HM-Paywall with prices and Restore, Test Store on debug (acceptance: AC-H18)
  - [x] T032a: `Entitlements` interface (Unavailable/Fake), `LimitGate` `[unit]` (1 home, 15 items, 1 panel, no sync) in :core billing (AC-H18)
  - [x] T032b1: RevenueCat `purchases` behind `ProBilling`/`Entitlements`, key via BuildConfig per docs/billing.md, HM-Paywall with prices, Restore and Retry, More > House Manual Pro, gate on the 16th item (AC-H18)
  - [ ] T032b2: gates on the second panel (with the breaker work) and on Sign in / cloud sync (AC-H18)
- [x] V2: Visual review of M2 screenshots; also install on the emulator yourself if available and walk PRODUCT.md House Manual steps 1–5 (sampled; no emulator in this container)

## M3 Field Log core (Field Log usable)

- [x] T033: Time clock data and rules: `Punch`, `Shift` derivation `[unit]`, `PunchDistance` `[unit]`, `ShiftFlags` `[unit]`, `TimesheetMath` `[unit]` (OT rules, breaks, rounding), sync (acceptance: AC-F9, AC-F12, AC-F13)
  - [x] T033a: pure rules in :core model: `PunchDistance`, `ShiftBuilder`/`Shift`, `ShiftFlags`, `TimesheetMath` with tests (AC-F9, AC-F12, AC-F13)
  - [x] T033b: `PunchRepository` (save punch offline with outbox row, derive and cache shifts, edit with audit) and sync of punches (AC-F8, AC-F52)
- [ ] T034: FL-Clock screen: Clock in with job picker, GPS fix (FusedLocationProvider, while-in-use, 5 s cap), far-from-job reason sheet, Break / Switch / Clock out, elapsed ticker, permission explanation and no-GPS path, "My timesheet" week view (acceptance: AC-F8, AC-F10, AC-F18)
- [ ] T035: FL-Today: clock status card, today's jobs for me, my tasks due (placeholder until T044), recent photos, capture FAB; Owner "Who's on site now" with map pin links; clock widget 2×2 (acceptance: AC-F16, AC-F17, Today shell)
- [ ] T036: Lead crew clock-in and FL-Timesheets: multi-select crew punch, week per person with flags, edit with reason + audit, Approve person/all with lock, crew-side "Approved" (acceptance: AC-F11, AC-F14)
- [ ] T037: Time exports and reports: shift CSV, QuickBooks-ready CSV, `HoursReport` `[unit]`, FL-Reports hours by job/person (acceptance: AC-F15, AC-F46)
- [ ] R07: Review T033–T037; timesheet math against worked examples (40 h week with a 10 h day under weekly-only and California rules)
- [ ] T038: Media pipeline: `MediaStore`-free app-private storage, thumbnails, upload queue with presigned R2 PUT (backend `/media/presign`), retry/resume, download cache with cap and "Free up" (acceptance: AC-F54, AC-B21)
- [ ] T039: FL-Capture: CameraX with lens limiter, 80 dp shutter, volume keys, job/room/phase chips, flash/flip, gallery import, Note and Voice (3 min, on-device transcription best effort), permission-denied states (acceptance: AC-F24, AC-F27, AC-F29)
- [ ] T040: Photos tab and FL-Photo: grouped by day, tags, before/after with ghost overlay, annotate (arrow, circle, rectangle, freehand, text, measure label) saved as a copy, caption, share, delete (acceptance: AC-F25, AC-F26)
- [ ] T041: Files: `JobFile` with folders, upload from Files with progress, `PdfViewer` (PdfRenderer, thumbnails, zoom, first page < 2 s), Available offline, rename/move/search (acceptance: AC-F19, AC-F20, AC-F23)
- [ ] T042: PDF markup and document scanner: `MarkupCanvas` (pen, arrow, rectangle, text, highlight) → new version with author/time, version history; ML Kit document scanner to PDF into Files (acceptance: AC-F21, AC-F22)
- [ ] R08: Review T038–T042; offline scenario in tests (queue survives process death; no duplicate uploads)
- [ ] T043: Daily log: `LogAssembler` `[unit]`, FL-Log sections (crew/hours, work done, photos, tasks, materials, delays, weather, visitors, safety, client notes), delay picker, backend `/weather` proxy (Open-Meteo) with cache, "No weather (offline)" (acceptance: AC-F30, AC-F31, AC-F32, AC-B32)
- [ ] T044: Tasks: entity, FL-Tasks per job and My tasks on Today, swipe complete with Undo, punch filter, reorder, pin on plan (FL-House) and on a file page (acceptance: AC-F34, AC-F35)
- [ ] T045: Log PDF and Send: logo header, sections, photos; share sheet and email to client/office; Sent status, re-send (acceptance: AC-F33)
- [ ] T046: Offline hardening: airplane-mode pass over every FL flow, pending count on Today, reconnect sync within 60 s, conflict log viewer under Settings (acceptance: AC-F52, AC-F55)
- [ ] T047: Push notifications: FCM registration per member (delivery only), backend send for task assigned / schedule changed / change order signed / timesheet approved, deep links (acceptance: AC-B30, AC-F36)
- [ ] R09: Review T043–T047
- [ ] V3: Visual review of M3 screenshots; walk PRODUCT.md Field Log steps 1–6

## M4 Walls, overlays and 3D

- [ ] T048: Wall photos: `WallPhoto` per stage, "Photograph this wall" flow, outline screen with four handles, loupe, aspect guide; `WallTextureBuilder` `[unit]` (setPolyToPoly, 2048 px, JPEG) (acceptance: AC-C17, AC-C18, AC-C19)
- [ ] T049: 3D scene foundation: SceneView in `:house-model`, `PlanMeshBuilder` `[unit]` (extrude rooms to walls, floor plane, openings, UVs), stock lit material, textured faces from wall photos, room colors for untextured faces, orbit/pan/zoom, Reset view, HM-3D and FL-House 3D entry (acceptance: AC-C20)
- [ ] T050: Floors in 3D (ghost below, All floors exploded), stage selector (Framing/Rough-in/Insulation/Finished), performance pass to hit 30 fps on a 20-wall home (texture downscale, frustum culling via Filament), first render < 2 s (acceptance: AC-C21, AC-C24)
- [ ] T051: Room view: tap room → camera inside at eye height facing the longest wall, drag to look, pinch zoom, Back to floor; shared-element transition from the room page (acceptance: AC-C22)
- [ ] T052: Overlays: entity and kinds with icons/colors, `WallSpaceMapper` `[unit]`, overlay editor on a wall photo (polyline/point, kind, label, note, depth, photo, linked item, stage) and on the plan, edit/delete, search (acceptance: AC-C25..C28)
- [ ] R10: Review T048–T052; texture memory under rotation; overlay coordinates after a wall is resized in the editor
- [ ] T053: Overlays and item markers in 3D with layer toggles; tap marker opens the record; blueprint layers show overlays and items (acceptance: AC-C23, AC-C15 overlays part)
- [ ] T054: Field Log behind the wall: room → wall → capture writes Rough-in; second capture after drywall writes Finished; overlays carry stage; HM-Room shows stages (acceptance: AC-F49, AC-H8, AC-H9)
- [ ] T055: Item positions: place an item on the plan (drag marker) and on a wall; item page "Where" section; markers in blueprint and 3D (acceptance: AC-C30 position, AC-C23)
- [ ] V4: Visual review of M4 screenshots and a recorded 3D screen capture (QA records the video; builder reviews the frames)

## M5 Field Log complete

- [ ] T056: Change orders: entity, `MoneyMath` `[unit]`, FL-ChangeOrder form with voice description, photos, line items, total (acceptance: AC-F37)
- [ ] T057: Signature pad and CO PDF: `SignaturePad` in `:core-ui`, Signed state read-only with Duplicate, PDF with signature, share (acceptance: AC-F38, AC-F39)
- [ ] T058: Share links backend: `/share/gallery/:token`, `/share/log/:token`, `/share/co/:token` with web signature, revocation; app "Share link" actions and Signed on pull (acceptance: AC-B35, AC-B36, AC-F40, AC-F28 gallery link)
- [ ] T059: Schedule: `Assignment` entity, FL-Schedule week view (crew rows, day cells, tap/drag assign), crew's own week and "Tomorrow" on Today, conflict badges, push on change for tomorrow (acceptance: AC-F41, AC-F42, AC-F43)
- [ ] T060: Materials and clients: receipt capture with scanner and OCR total suggestion, list per job with totals, CSV; FL-Clients list (acceptance: AC-F44, AC-F45)
- [ ] R11: Review T056–T060
- [ ] T061: Reports: photo report PDF (range, 4 per page), job summary PDF (hours vs budget, COs, tasks, delays) (acceptance: AC-F28 report, AC-F47)
- [ ] T062: Installed items on the job: FL-House rooms with items (same editor as HM), warranty lookup, data plate OCR (acceptance: AC-F50)
- [ ] T063: `.homerecord` v2 bundle writer/reader `[unit]`, `ImportPlanner` `[unit]`, handoff summary and export from FL-House; `/handoff` backend with claim email; HM import (file, share, claim link) with preview and conflicts (acceptance: AC-C46, AC-C47, AC-F51, AC-B40, AC-H17)
- [ ] T064: Field Log billing: trial clock from company creation, RevenueCat entitlements `fieldlog_solo`/`fieldlog_crew`, FL-Paywall (Owner), backend webhook + `/me` entitlement, crew-side read-only state (acceptance: AC-F56, AC-B50)
- [ ] T065: FL-More and Settings: Team, Timesheets, Reports, Company, Billing, Materials, Clients, Settings (units, offline storage, notifications), Account (sign out clears after confirm), About (acceptance: AC-F4, More shell)
- [ ] R12: Review T061–T065
- [ ] V5: Visual review of M5 screenshots; walk PRODUCT.md Field Log steps 1–10

## M6 House Manual complete

- [ ] T066: Breaker panel item: `Panel`/`Breaker` entities, `SlotOccupancy` `[unit]`, HM-Panel grid (two-pole tall cell, tandem), panel photo, HM-Breaker editor with rooms and items fed (acceptance: AC-C40)
- [ ] T067: Breaker links everywhere: item `poweredBy` chip, breaker "feeds" list, room "Breakers feeding this room" with confidence dots, search highlights slot (acceptance: AC-C41, AC-H7)
- [ ] T068: Mapping mode: `MappingStateMachine` `[unit]`, charging monitor, alarm, confirm flow, keep-screen-on (acceptance: AC-C42)
- [ ] T069: Legend PDF per panel (acceptance: AC-C43)
- [ ] T070: Shutoffs and Emergency: shutoff items with instructions/photo, HM-Emergency large type grouped with mains first, item "Shutoff" link chip (acceptance: AC-C44, AC-C45)
- [ ] R13: Review T066–T070
- [ ] T071: Quick settings tile and 2×1 widget opening HM-Emergency; Home Emergency card (acceptance: AC-H11)
- [ ] T072: Reminders: `MaintenanceRule` `[unit]`, seeds per subtype and climate zone, warranty-expiry reminders, HM-Reminders (Due/Upcoming/Done, Done/Snooze, zone chips, Add), WorkManager 08:00 notification with deep link, permission after first reminder (acceptance: AC-C49, AC-C50, AC-H12)
- [ ] T073: Home report PDF (cover, blueprints, rooms with items and warranty status, shutoffs, legend) under 5 s for 40 items (acceptance: AC-H13)
- [ ] T074: Encrypted backup export/restore (`BundleCrypto` `[unit]`), onboarding "Restore from a backup" (acceptance: AC-C48, AC-H14)
- [ ] T075: House Manual cloud sync: sign-in uploads the home (reuse the sync engine with `homeId` tenancy), second device sees it, merges, sign out keeps local; HM-Account screen (acceptance: AC-H15)
- [ ] R14: Review T071–T075
- [ ] T076: Sharing: editor/viewer invites by link, HM-Share people list, viewer mode hides edit controls; backend membership on homes (acceptance: AC-H16)
- [ ] T077: HM-More: Emergency, Breaker panels, Documents, Contacts, Home report, Import, Backup and restore, Account and sync, Share this house, Units, Climate zone, Pro, About as ListItem rows (acceptance: More shell, AC-U3)
- [ ] T078: House Manual walkthrough pass: run `qa/product-walkthrough.md` House Manual section on the emulator if available, otherwise against screenshots; fix every blocked step (acceptance: AC-H19)
- [ ] T079: Field Log walkthrough pass: same for Field Log (acceptance: AC-F57)
- [ ] V6: Visual review of M6 screenshots

## M7 Ship

- [ ] T080: Language pass over both apps and docs per `docs/voice.md`
- [ ] T081: Store readiness: `docs/data-safety.md`, `docs/privacy-policy.md` (both apps, including backend data handling), `docs/store-listing.md`, release signing config, versioning scheme
- [ ] S1–S10: Ship-ready checklist in CLAUDE.md, one item per run, in order (acceptance: AC-U10, AC-U11 under S4 and S7)
- [ ] T082: Release builds for both apps, DONE file per CLAUDE.md "Definition of done"
