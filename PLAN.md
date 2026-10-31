# Build plan

Ordered tasks, about one focused hour each. The builder ticks a task only when its acceptance criteria pass and `./gradlew assembleDebug testDebugUnitTest lintDebug` (and `npm test` in `backend/` for backend tasks) are green. After every five build tasks there is a review task (R) and after every milestone a visual review (V) that looks at the committed screenshot PNGs against `docs/ui-standards.md`. Format: `- [x] T012: <what> (acceptance: AC-C5, AC-C6)`.

The builder may append notes and new subtasks under a task (as `  - [ ] T012a: ...`) and never reorders or deletes tasks. Tasks the builder cannot do are marked `(blocked: see BLOCKERS)` and skipped.

## CR Full code review (added 2026-10-26 by Nate; do CR1 next run before any other task, then CR2 in order)

The builder moved from Sonnet 5.5 to Opus 5.5 on 2026-10-26. Everything on `build` so far was written by the earlier model. Before any more feature work, review all of it with fresh eyes and write down what needs fixing or cleaning up so the following runs can work through it.

- [x] CR1: Full code review of `build` HEAD. Read every module (`:core`, `:core-ui`, `:house-model`, `:house-manual`, `:field-log`, `backend/`, `qa/`, `.github/workflows`) against SPEC.md, PRODUCT.md, docs/ui-standards.md, docs/decisions.md and CLAUDE.md. Look for: bugs and spec drift; duplicated or dead code; wrong module boundaries (shared UI or logic living in an app module, app-specific code in a shared one); MVVM and state-holder violations; missing, weak or flaky tests; sync/outbox and offline edge cases; backend tenancy, auth and input-validation holes; dependency or build-config problems; anything that will make M4–M7 harder than it needs to be; QA flows that fail for flow reasons rather than app reasons. Do not fix anything in this run except a red build. Write findings to REVIEW.md under a new heading `## Builder code review 2026-10-26 (commit <sha>)`, one unchecked line per finding in the standard format: `- [ ] [Critical|Major|Minor] <problem> [cr:<short-key>] - where: <file:line> - expected: <SPEC AC, ui-standards rule, or CLAUDE.md rule> - actual: <what the code does> - fix: <one line>`, most important first. Group related cleanups that need more than one run into CR2 subtasks below (as `  - [ ] CR2a: ...`). End the entry with a short "Next session" list: the three to five things to do first, in order. Record counts by severity in PROGRESS.md. (acceptance: the entry exists, every finding names a file, CR2 has subtasks or an explicit "none needed")
- [x] CR2: Cleanup from CR1. Subtasks are added by the CR1 run; do them in order, one per run, before returning to M3. Critical and Major `[cr:*]` items in REVIEW.md are picked under rule 2a as usual.
  - [x] CR2a: Sync merge safety, client and server: union only an explicit allowlist of set-like `*Json` fields in RowMerge and merge.ts; dead-letter a refused op and keep syncing; catch every exception in SyncEngine and set Failed; RoomRowStore tolerates unknown or missing columns; tests for polygon edits, list removals and a 400 on one op (cr:sync-json-union, cr:sync-poison-op, cr:sync-exceptions, part of cr:sync-test-gaps)
    - [x] CR2a2: show refused changes (SyncEngine.refusedCount, the conflict log) on the sync status in both apps so a set-aside change is never silent (rest of cr:sync-poison-op "show it")
  - [x] CR2b: Sync per tenant: sync_state and sync_base keyed by tenant, cleared when a membership ends; apply in a transaction keeping only fields this device changed; device-local columns (path, thumbPath, voicePath, offline) out of synced rows; `r2Key` on photo and note (cr:sync-global-cursor, cr:sync-merge-race, cr:sync-local-columns, rest of cr:sync-test-gaps)
    - [x] CR2b1: pull cursor per tenant (sync_cursor, Room v2 with AutoMigration and a migration test), sync_base tagged by tenant and dropped with the cursor when a company or home is left, pulled rows applied in a transaction keeping only the fields in this phone's unsent payloads, two-tenant tests (cr:sync-global-cursor, cr:sync-merge-race, rest of cr:sync-test-gaps)
    - [x] CR2b2: device-local columns (job_file path and offline, photo path and thumbPath, note voicePath) out of synced rows; `r2Key` on photo and note set on upload (cr:sync-local-columns)
    - [x] CR2b3: a photo or voice memo pulled from another phone (path "", r2Key set) downloads by its key on view, with the Photos tile, FL-Photo and capture ghost showing a placeholder until then (rest of cr:sync-local-columns "cannot download", AC-F55)
    - [x] CR2b4: crew Clock "My timesheet" shows who changed an edited punch, when and why (the audit note the owner wrote), not only the Edited flag; found while adding the AC-F55 device test, which proves the note reaches the crew phone
  - [x] CR2c: Field Log capture and clock data loss: request FINE+COARSE and accept coarse; write the photo row before the GPS fix; save a memo that is recording on leave; re-check camera/location on resume; awaitInstance for CameraX (cr:fl-loc-coarse, cr:fl-capture-save-cancel, cr:fl-capture-perm-resume, cr:fl-camera-main-block)
    - [x] CR2c1: request FINE+COARSE and accept coarse; write the photo row before the GPS fix; save a memo that is recording on leave (cr:fl-loc-coarse, cr:fl-capture-save-cancel)
    - [x] CR2c2: re-check camera and location permission on resume; `ProcessCameraProvider.awaitInstance` for CameraX (cr:fl-capture-perm-resume, cr:fl-camera-main-block)
  - [x] CR2d: Quick crash fixes: data plate duplicate key, PdfRenderer close race, OptionsSheet/AttachDocumentSheet scroll (cr:plate-dup-key, cr:pdf-close-race, cr:sheet-scroll)
  - [x] CR2e: Backend sync role policy per entity and role for push and pull, AC-B3 vitest cases, column list per role on GET /companies/:id, one company per account (cr:sync-role-gate, cr:test-acb3-sync, cr:tenant-select-star, cr:join-one-company)
    - [x] CR2e1: sync role policy per entity and role for push and pull (`backend/src/sync/policy.ts`: Crew/Sub own time, captures and their own tasks; Lead also time, tasks, files, logs, change orders, materials and schedule on jobs they are on, and the job's house model; company, members, clients, jobs, crew lists, approvals and share links Owner/Office only; pull hides other members' time and every pay rate), AC-B3 vitest cases (cr:sync-role-gate, cr:test-acb3-sync)
    - [x] CR2e2: column list per role on GET /companies/:id and one company per account on join (cr:tenant-select-star, cr:join-one-company)
  - [x] CR2f: Backend auth and environment: rate limits and a cumulative lockout on email codes, HMAC code hash, dev-only code log, ENVIRONMENT defaults to production with an [env.dev] block, token_version revocation, README secrets (cr:email-code-bruteforce, cr:dev-env-default, cr:code-hash, cr:code-log, cr:session-revoke, cr:readme-secrets)
    - [x] CR2f1: rate limits on email codes (per email and per IP on start and verify, a check counter a new code does not reset, D1 `auth_limits` with an atomic upsert, pruned by the cron, off only in dev), 429 `rate_limited` shown by both apps, code logged only in dev, QA's `.dev.vars` sets ENVIRONMENT=dev (cr:email-code-bruteforce, cr:code-log)
    - [x] CR2f2: ENVIRONMENT defaults to production in wrangler.toml, `npm run dev` passes `--var ENVIRONMENT:dev`, deploy script explicit, README; only once `git show origin/main:.github/workflows/qa.yml` contains `ENVIRONMENT=dev` (scheduled QA reads qa.yml from main) (cr:dev-env-default)
    - [x] CR2f3: HMAC code hash, token_version revocation, README secrets and a 32-byte JWT_SECRET floor (cr:code-hash, cr:session-revoke, cr:readme-secrets)
  - [x] CR2g: Backend media: orphan detection by entity row, separate presign-put and presign-get with ownership and entity checks, signed size and type, one bucket setting, dev reset tables (cr:media-orphan-false-positive, cr:presign-overwrite, cr:presign-size, cr:media-cron-scope, cr:dev-reset-tables)
    - [x] CR2g1: orphan cron keeps an object while the live (not soft-deleted) sync row named by its key (tenant/entity/id) exists, one primary-key lookup per object instead of a scan of every row's JSON; AC-B21 tests with photo and voice memo rows that store no key (cr:media-orphan-false-positive, scan half of cr:media-cron-scope)
    - [x] CR2g2: separate presign-put and presign-get with entity list and ownership checks, signed size and type, one bucket setting, dev reset tables (cr:presign-overwrite, cr:presign-size, rest of cr:media-cron-scope, cr:dev-reset-tables)
  - [x] CR2h: Backend sync atomicity: per-op batch with compare-and-swap (or a per-tenant Durable Object), committed-seq watermark for pull, body limit, pruning, concurrency tests (cr:sync-seq-race, cr:sync-limits)
    - [x] CR2h1: each pushed op is one compare-and-swap D1 batch (idempotency record and row read together, seq claimed, counter bumped, conflicts logged and row written only while the row is at the version merged against, re-merged on a lost race), pull capped at the committed counter, concurrency tests (cr:sync-seq-race)
    - [x] CR2h2: push body limit with SyncEngine batches sized to fit it, and daily pruning of sync_applied and sync_conflicts (cr:sync-limits)
  - [x] CR2i: Core threading and storage: injected IO dispatcher in media, cache, transfers and RowStore; PDF markup saved to filesDir; upload Rejected counts toward the limit; outbox pruning (cr:core-main-io, cr:pdfmarkup-cache-loss, cr:upload-rejected-loop, cr:outbox-growth, cr:fl-viewer-main-io)
    - [x] CR2i1: injected IO dispatcher in RetrofitMediaTransferApi, MediaCache, JobFileTransfers and RoomRowStore (Main hops to IO, transaction threads query in place); FileViewer not-on-phone check off Main (cr:core-main-io, cr:fl-viewer-main-io)
    - [x] CR2i2: PDF markup saved to filesDir; upload Rejected counts toward the limit; outbox pruning (cr:pdfmarkup-cache-loss, cr:upload-rejected-loop, cr:outbox-growth)
  - [x] CR2j: Room versioning: bump the schema version from now on, exported schemas committed, MigrationTestHelper test (cr:room-schema-v1)
  - [x] CR2k: Field Log sign-out wipe with an unsynced-data warning, and seed reset clearing Room and media (cr:fl-signout-wipe, cr:fl-seed-reset)
  - [x] CR2l: Field Log company settings and role as a local Flow: settings synced or sent with the membership, role in every UiState, actions hidden by role, lead limited to assigned jobs, JobStatusRule (cr:fl-settings-source, cr:fl-role-gates, cr:fl-job-status-stale)
    - [x] CR2l1: company clock rules sent with the membership on /me, cached with it, and a live Flow in Clock, Timesheets and Reports (cr:fl-settings-source)
    - [x] CR2l2: JobStatusRule.effective(job, today) used everywhere instead of the stored status (cr:fl-job-status-stale)
    - [x] CR2l3: role in every UiState, actions hidden by role, lead limited to assigned jobs (cr:fl-role-gates)
  - [x] CR2m: QA truthfulness: coverage-check regex and loose rules, flows renamed to walkthrough steps, tour variant env, probe sha, F01 silent skip, H04 rooms, comment-only AC claims, handoff stage set (cr:cov-task-regex, cr:cov-loose-rules, cr:qa-flow-step-mismatch, cr:qa-tour-variant-env, cr:ci-probe-sha, cr:qa-f01-silent-skip, cr:qa-h04-kitchen, cr:qa-ac-claims-comment, cr:ci-handoff-dup)
    - [x] CR2m1: flows renamed to their walkthrough steps (H01b-home, H17-sign-in, H19-paywall, F04c-today-clock-in, F07-files, F07b-storage, F15-reports); contacts and documents, which are no step, are x-contacts and x-documents; summarize.py counts only H/F-step names toward the walkthrough (cr:qa-flow-step-mismatch)
    - [x] CR2m2: coverage-check reads every ticked task and subtask id ([TQRV] plus any suffix) and ACs anywhere on the line, a [unit] AC needs a JVM test, screenshot tests and node_modules are not coverage; add the tests and flows the stricter gate finds missing (cr:cov-task-regex, cr:cov-loose-rules)
    - [x] CR2m3: flow header comments no longer count as coverage, only tagged asserted steps; F04 gains far-from-job and switch-job steps (cr:qa-ac-claims-comment)
    - [x] CR2m4: tour variant env, full-sha probe against the newest non-bookkeeping commit, F01 fails without a code, handoff job runs handoff flows only (cr:qa-tour-variant-env, cr:ci-probe-sha, cr:qa-f01-silent-skip, cr:ci-handoff-dup)
  - [x] CR2n: House model integrity: item and room/floor delete cascades in a transaction, wall identity by endpoints, plan editor commits on Done, transactional savePlan (cr:item-delete-cascade, cr:room-delete-cascade, cr:wall-identity, cr:plan-keystroke-commit)
    - [x] CR2n1: item and room/floor delete cascades in one transaction, transactional savePlan (cr:item-delete-cascade, cr:room-delete-cascade)
    - [x] CR2n2: wall identity by endpoints, every wall upserted on save (cr:wall-identity)
    - [x] CR2n3: plan editor commits name and size on Done, not per keystroke (cr:plan-keystroke-commit)
  - [x] CR2o: Photo data model: separate pairRole from phase, ghost off by default and same room only, EXIF-correct annotated copy, annotate discard prompt (cr:fl-before-phase, cr:fl-ghost-autopair, cr:fl-annotate-exif, cr:fl-annotate-discard)
    - [x] CR2o1: separate pairRole column from the phase (Room v4, migration moves stage "Before"), Before toggle on FL-Photo, ghost off by default and same room only, one After per Before (cr:fl-before-phase, cr:fl-ghost-autopair)
    - [x] CR2o2: EXIF-correct, size-capped annotated copy with a rotated-fixture test; FL-Annotate as a form with Close, Save and a discard prompt when there are marks (cr:fl-annotate-exif, cr:fl-annotate-discard)
  - [x] CR2p: Release build: R8 with keep rules, API_BASE_URL and cleartext per build type, signing config, dependency list reconciled (cr:build-no-r8, cr:build-release-debug-leak, cr:ci-apks-signing, cr:deps-approved)
    - [x] CR2p1: release builds minified (R8, shrinkResources, proguard-rules.pro per app), API_BASE_URL per build type (release: HTTPS production URL, never `api.baseUrl`), cleartext only in src/debug (cr:build-no-r8, cr:build-release-debug-leak)
    - [x] CR2p2: release signing config reading the testing-apks properties, and the dependency list reconciled with CLAUDE.md (cr:ci-apks-signing, cr:deps-approved)
  - [x] CR2q: Shared UI for both apps: tag prefix in :house-model, RoomViewModel, PdfViewer and scanner in :core-ui, ItemDetail navigation out of the VM, pending capture in SavedStateHandle (cr:shared-hm-tags, cr:room-route-mvvm, cr:pdf-viewer-dup, cr:vm-nav-lambdas, cr:photo-pending-remember)
    - [x] CR2q1: pending camera/gallery capture (kind and file) kept in ItemEditViewModel's SavedStateHandle, answered by CameraResult/GalleryResult (cr:photo-pending-remember)
    - [x] CR2q2: ItemDetail navigation out of the VM: one-shot closed/openCopyId in UiState handled by the route (cr:vm-nav-lambdas)
    - [x] CR2q3: tag prefix in :house-model, RoomViewModel, PdfViewer and scanner in :core-ui (cr:shared-hm-tags, cr:room-route-mvvm, cr:pdf-viewer-dup)
      - [x] CR2q3a: :house-model screens tag with the app prefix (HomeRecordTheme provides `hm`/`fl` by Brand); Floors rows `<app>_floors_row_<id>` (cr:shared-hm-tags)
      - [x] CR2q3b: RoomViewModel shared by both apps, RecordListItem item rows with icon and warranty status (cr:room-route-mvvm)
      - [x] CR2q3c: PdfViewer (pages, thumbnails, zoom) and the document scanner moved into :core-ui, used by HM documents and FL files (cr:pdf-viewer-dup)
  - [x] CR2r: House Manual form and list conformance: FormScaffold with dirty and SavedStateHandle drafts, upkeep delete confirm, Cards to RecordListItem rows (cr:form-dialog, cr:upkeep-delete-confirm, cr:card-lists)
    - [x] CR2r1: House Manual form dialog (contacts, consumable, service) asks before discarding when dirty, Save on for a new record or a changed edit, upkeep drafts and their originals in SavedStateHandle (cr:form-dialog)
    - [x] CR2r2: consumable and service record Delete confirm with an AlertDialog (cr:upkeep-delete-confirm)
    - [x] CR2r3: Contacts, Documents and item page sections as RecordListItem rows under section headers instead of Cards (cr:card-lists)
      - [x] CR2r3a: Contacts and Documents lists as RecordListItem rows (row opens, trailing call/email or rename buttons), folder headers per rule 9
      - [x] CR2r3b: item page Details, Warranty, Recalls, Consumables, Service history and Documents sections as section headers with RecordListItem rows instead of Cards
  - [x] CR2s: Field Log UI conformance: 64 dp clock buttons, JobEdit client phone/email and room delete confirm, Today pull to refresh, tab replay, Photos grid and FAB (cr:fl-clock-64dp, cr:fl-jobedit-client-contact, cr:fl-jobedit-room-delete, cr:fl-today-pull, cr:fl-tab-replay, cr:fl-photos-grid)
    - [x] CR2s1: tab replay: a widget tab request is handled once (cr:fl-tab-replay)
    - [x] CR2s2: Clock in and Clock out 64 dp, clock dialog actions and rows 56 dp (cr:fl-clock-64dp)
    - [x] CR2s3: JobEdit client phone and email, saved on the client row and reloaded, validated, kept through process death (cr:fl-jobedit-client-contact)
    - [x] CR2s4: JobEdit room removal deletes only unplaced, empty rooms, or confirms and names what goes (cr:fl-jobedit-room-delete)
    - [x] CR2s5: Today pull to refresh, Photos grid and FAB (cr:fl-today-pull, cr:fl-photos-grid)
  - [x] CR2t: House Manual debug seed with items, warranties, consumables, an overdue task, contacts and a PDF (cr:seed-items)
  - [x] CR2u: Minor sweep: the remaining Minor [cr:*] items in REVIEW.md, several per run, oldest first (16 sweeps; cr:no-domain-models stays open in REVIEW.md as its own fix says, models and mappers as each feature is next touched)

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
- [x] Q004: Flows for everything built so far: fix and extend `qa/flows/house-manual/H01–H03` and `_tour` to the real screens (use the Q001 ids), add `H02b-shape-doors-stairs.yaml`; extend `qa/flows/field-log/F01-welcome.yaml` with the dev sign-in path and add `F02-invite-join.yaml` (second account via the backend dev seed, role change, remove) and `_tour`; focus-guard assertions in both; every flow passes locally against an emulator before commit (acceptance: AC-C4, AC-F1, AC-F2, AC-F3, AC-F4, AC-H15)
- [x] Q005: Debug seed and dev backend: broadcast receiver `<pkg>.qa.SEED` in debug builds with scenario `regression` (House Manual: a two-story home with basement, 12 placed rooms, doors, windows, stairs; Field Log: a company "QA Electric" with Owner + Crew members and one job) and `reset`; backend routes under `/dev/*` enabled only when `ENVIRONMENT=dev` (`GET /dev/last-code?email=`, `POST /dev/seed`, `POST /dev/reset`), with tests proving they are absent otherwise; `qa/fixtures/` filled per its README (acceptance: AC-B2, AC-B3, AC-B20, AC-B60, AC-F53)
- [x] Q005a: Backend half of Q005: `/dev/last-code`, `/dev/seed`, `/dev/reset` only when `ENVIRONMENT=dev` (acceptance: AC-B2)
- [x] Q005b: Android half of Q005: debug `<pkg>.qa.SEED` receivers (`regression`, `reset`) for both apps (Field Log seed signs in as the dev-backend owner; its job arrives with T018)
- [x] Q005c: `qa/fixtures/` files per its README (images and plan.pdf), then tick Q005
- [ ] Q006: First green run: read `origin/qa-results:latest/summary.md` and the `## QA auto` entries in REVIEW.md, fix every Critical and Major the suite found (app bugs, flow mistakes, coverage gaps), and record in PROGRESS.md which stages still skip and why (acceptance: newest `## QA auto` entry ends with `New issues: 0`)
  - note 2026-10-31: every Critical and Major of the last run (6a0e6db4) is fixed; the only stage that still skips is the handoff job (no cross-app flows until the handoff tasks); acceptance waits on the next suite run of `build` HEAD

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
- [x] T034: FL-Clock screen: Clock in with job picker, GPS fix (FusedLocationProvider, while-in-use, 5 s cap), far-from-job reason sheet, Break / Switch / Clock out, elapsed ticker, permission explanation and no-GPS path, "My timesheet" week view (acceptance: AC-F8, AC-F10, AC-F18)
  - [x] T034a: `LocationProvider` (Fused, 5 s cap) in :core and `ClockController` (clock in, break, switch, out, far reason, no-location and require-GPS paths) with tests (AC-F8, AC-F10, AC-F18)
  - [x] T034b: FL-Clock screen on the Clock tab (job picker, permission explanation, far-from-job sheet, GPS-required Settings prompt, elapsed ticker, My timesheet week view) with ScreenCatalog, screenshots, F04 flow
- [ ] T035: FL-Today: clock status card, today's jobs for me, my tasks due (placeholder until T044), recent photos, capture FAB; Owner "Who's on site now" with map pin links; clock widget 2×2 (acceptance: AC-F16, AC-F17, Today shell)
  - [x] T035a: `OnSiteBuilder` in :core (open shifts to "Who's on site" rows with clock-in pin) with tests (AC-F16)
  - [x] T035b: FL-Today screen (clock status card opening the Clock tab, my jobs today, Owner/Office on-site list with map pin links), TodayMapper/ViewModel, ScreenCatalog, screenshots, F05 flow (AC-F16)
  - [x] T035c1: Today refreshes the cached team list on open (`TodayDeps.refreshMembers`)
  - [x] T035c2: clock widget 2x2 with Glance (AC-F17)
  - [ ] T035c3: capture FAB and recent photos (after T039/T040), my tasks due (after T044)
- [x] T036: Lead crew clock-in and FL-Timesheets: multi-select crew punch, week per person with flags, edit with reason + audit, Approve person/all with lock, crew-side "Approved" (acceptance: AC-F11, AC-F14)
  - [x] T036a: `CrewClock` (one fix, one IN punch per selected member with `clockedInBy`, skips people already in), `TimesheetApprovals` + `WeekKey`, edits refused in an approved week (AC-F11, AC-F14)
  - [x] T036b: crew clock-in UI for leads (multi-select on the Clock tab) with ScreenCatalog, screenshots, flow (AC-F11)
  - [x] T036c1: Timesheets week builder, punch time parser and `TimesheetsViewModel` (week picker, edit with reason and audit, approve person/all) (AC-F14)
  - [x] T036c2: FL-Timesheets screen (week per person with flags, punch edit sheet with required reason and audit list, Approve person / all) from More, ScreenCatalog, screenshots, F06 flow (AC-F14)
  - [x] T036c3: crew-side "Approved" line on My timesheet in FL-Clock (AC-F14)
- [x] T037: Time exports and reports: shift CSV, QuickBooks-ready CSV, `HoursReport` `[unit]`, FL-Reports hours by job/person (acceptance: AC-F15, AC-F46)
  - [x] T037a: `HoursReport` (hours by person and job over whole Monday weeks, OT split per week) and `TimeExports` (shift CSV, QuickBooks-ready CSV, formula-safe cells) in :core with tests (AC-F15, AC-F46)
  - [x] T037b: export wiring: build shift rows (GPS in/out from punches) and QuickBooks job names, write CSV to cache, share through FileProvider (AC-F15)
  - [x] T037c: FL-Reports screen from More for Owner/Office (week range picker, hours by person and by job, Export shift CSV / QuickBooks CSV), ScreenCatalog, screenshots, flow (AC-F15, AC-F46)
- [x] R07: Review T033–T037; timesheet math against worked examples (40 h week with a 10 h day under weekly-only and California rules)
- [x] T038: Media pipeline: `MediaStore`-free app-private storage, thumbnails, upload queue with presigned R2 PUT (backend `/media/presign`), retry/resume, download cache with cap and "Free up" (acceptance: AC-F54, AC-B21)
  - [x] T038a: backend `POST /media/presign` (SigV4 presigned R2 PUT 10 min and GET 7 days, key tenant/entity/id/filename, secrets R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY), `media_objects` table, daily cron removing unreferenced objects over 30 days (AC-B21)
  - [x] T038b: app side in :core: `MediaUploader` (queue in Room, presign, PUT with retry/backoff and resume, WorkManager), download cache with cap and Free up (AC-F54)
- [x] T039: FL-Capture: CameraX with lens limiter, 80 dp shutter, volume keys, job/room/phase chips, flash/flip, gallery import, Note and Voice (3 min, on-device transcription best effort), permission-denied states (acceptance: AC-F24, AC-F27, AC-F29)
  - [x] T039a: `JobPhotos` in :core (photo saved with job, room, phase, time and fix, queued for sync and upload, after linked to its before), `LensLimiter`, `PhotoGrouping` by day (AC-F24, AC-F25, AC-F29)
  - [x] T039b: CameraX preview, 80 dp shutter, volume keys, chips, flash/flip, gallery import, permission-denied state (AC-F24, AC-F29)
  - [x] T039c1: `JobNotes`, `VoiceRecorder`, `SpeechTranscriber` in :core (typed notes, voice memos kept without transcript, editable transcript, 3 min cap constant) (AC-F27)
  - [x] T039c2: Android MediaRecorder + SpeechRecognizer implementations, RECORD_AUDIO permission, Note and Voice buttons and sheets on FL-Capture, AppContainer wiring (AC-F27)
- [x] T040: Photos tab and FL-Photo: grouped by day, tags, before/after with ghost overlay, annotate (arrow, circle, rectangle, freehand, text, measure label) saved as a copy, caption, share, delete (acceptance: AC-F25, AC-F26)
  - [x] T040a: `PhotoGallery` (day groups, before/after pairs), `Annotation` marks + `AnnotationEditor` undo + JSON codec, annotated copy keeps the original in :core (AC-F25, AC-F26)
  - [x] T040b1: Photos tab on FL-Job: gallery by day, before/after pairs side by side, empty and loading states, ScreenCatalog, screenshots (AC-F24, AC-F25)
  - [x] T040b2a: `PhotoEdits` in :core (caption, room/phase tags, Before mark, soft delete, all queued for sync) (AC-F25)
  - [x] T040b2a2: FL-Photo screen (open a tile, caption, room and phase tags, Before mark, share, delete with confirm) (AC-F25)
  - [x] T040b2: ghost overlay of the Before shot on After in FL-Capture (AC-F25)
  - [x] T040b3a: `PhotoAnnotator` in :core (annotated copy saved beside the original, queued for sync and upload) and the Android `BitmapAnnotationRenderer` in field-log (AC-F26)
  - [x] T040b3: annotate canvas screen (arrow, circle, rectangle, freehand, text, measure label, undo, Save copy) opened from FL-Photo, ScreenCatalog, screenshots, flow (AC-F26)
- [x] T041: Files: `JobFile` with folders, upload from Files with progress, `PdfViewer` (PdfRenderer, thumbnails, zoom, first page < 2 s), Available offline, rename/move/search (acceptance: AC-F19, AC-F20, AC-F23)
  - [x] T041a: JobFiles in :core (folders incl. empty ones, add, rename, move, search by name, Available offline flag, soft delete) with JobFilesTest 5 (AC-F23)
  - [x] T041b1: Files tab UI (folders, search, add via picker with queued upload, rename, delete) (AC-F19, AC-F23)
  - [x] T041b2a: FL-File viewer (PdfRenderer pages, thumbnail strip, pinch zoom, photos, share, not-on-phone prompt); PageSource moved to :core-ui (AC-F19, AC-F20)
  - [x] T041b2b: upload progress, Available offline download (opens in airplane mode), storage in Settings with Free up (AC-F19, AC-F20)
- [x] T042: PDF markup and document scanner: `MarkupCanvas` (pen, arrow, rectangle, text, highlight) → new version with author/time, version history; ML Kit document scanner to PDF into Files (acceptance: AC-F21, AC-F22)
  - [x] T042a: `PdfMarkup` in :core (marks saved as a new file version with author and time, original untouched, version history, list shows latest only) (AC-F21)
  - [x] T042b: MarkupCanvas screen + `MarkedPdfWriter` (PdfDocument over PdfRenderer pages) + version history sheet on FL-File (AC-F21)
  - [x] T042c: ML Kit document scanner to PDF into Files (AC-F22)
- [x] R08: Review T038–T042; offline scenario in tests (queue survives process death; no duplicate uploads)
- [x] T043: Daily log: `LogAssembler` `[unit]`, FL-Log sections (crew/hours, work done, photos, tasks, materials, delays, weather, visitors, safety, client notes), delay picker, backend `/weather` proxy (Open-Meteo) with cache, "No weather (offline)" (acceptance: AC-F30, AC-F31, AC-F32, AC-B32)
  - [x] T043a: backend `GET /weather` (Open-Meteo forecast or archive by date, no key, Fahrenheit/inches, condition keys, 6 h KV cache per ~1 km and day, 502/404) with vitest (AC-B32)
  - [x] T043b: `LogAssembler` `[unit]` and `DailyLogSections` (stored JSON) in :core: crew minutes per person from job segments clipped to the day less breaks, work done from notes and transcripts in order, the day's photos, tasks done, change orders signed, materials, weather kept when offline; edited and typed-only sections survive re-assembly (AC-F30)
  - [x] T043c: weather client in :core (`WeatherApi`, cached snapshot, "No weather (offline)") and `DailyLogRepository` (load/assemble/save per job and day, outbox, sync) (AC-F30, AC-F31)
  - [x] T043d: FL-Log screen on the job's Log tab (day picker, every section editable, delay picker with Weather/Materials/Inspection/Client/Other and note, Visitors, Safety, Notes for client), ScreenCatalog, screenshots, F09 flow (AC-F30, AC-F31, AC-F32)
    - [x] T043d1: Log tab screen: day bar with date picker (never past today), crew hours per person, Work done, delays (picker with the five reasons and note, remove with confirm), weather or why there is none, Visitors, Safety notes, Notes for client editable; Crew read only; ScreenCatalog, screenshots, F09 flow (AC-F30, AC-F31, AC-F32)
    - [x] T043d2: the remaining sections editable: drop photos, tasks, change orders and materials lines from the day's log, and correct the weather (full-screen form, rule 15) (AC-F30)
  - [x] T043e: delays in a job timeline in FL-Reports (AC-F32)
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
