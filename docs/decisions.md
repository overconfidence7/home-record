# Decisions

Dated log of library, architecture, pricing and scope choices, with the reason. Open questions live in BLOCKERS.md.

## 2026-10-19 — Re-scope and restart (Nate)

- **Restart from scratch in the same repo.** v1 (now branch `archive/v1`) built a panel-first product and a card-stack UI; Nate tested the APKs and rejected both apps. The v1 tree was deleted from `main` and `build`; nothing from v1 is carried forward except five non-code files (voice guide, billing notes, SDK setup script, CI workflow). Reason: the data model, navigation and screens all need to change; carrying code would carry the shape of the old product.
- **The house model is the core of both apps**: floors, rooms drawn on a grid, walls with staged photos, 3D view, overlays for pipes and wires, items placed in rooms. Reason: Nate's description of the product; competitors have inventory or floor plans, none have both tied together.
- **Items replace "equipment".** Any household thing is an item with category and subtype; the breaker panel and the shutoffs are items with extra screens. Reason: v1 made the panel the product.
- **Warranty is looked up once by a backend service and stored with the item** (Cloudflare Worker calling the Claude API web search tool, cached in KV per make+model). Reason: no manufacturer publishes machine-readable warranty terms and no UPC database carries them; an LLM with web search restricted to manufacturer domains is the only practical source; caching makes each model a one-time cost. Nate chose this over assisted-manual lookup.
- **Field Log gets a team backend on Cloudflare** (Workers + Hono, D1, R2, KV). Reason: a crew app needs accounts, shared jobs, punches and files; Nate chose Cloudflare over Firebase and Supabase (he already uses Cloudflare; cheapest at scale). Cost: we build sync ourselves (SPEC AC-F53, AC-B20) instead of getting it from Firestore.
- **House Manual stays local-first with optional sign-in** for backup, multi-device sync and sharing. Reason: no account required to use the app; privacy of a house map.
- **Field Log scope = everything a 1 to 10 person crew expects** (time clock with GPS, timesheets and approval, files and plans with markup, photos, daily logs, tasks, change orders with signature, schedule, materials, reports, roles, offline). Reason: competitor research (`docs/competitors.md`); flat pricing is the wedge, so the feature set must be table-stakes complete.
- **Location is foreground only.** GPS captured at punch events and on request; distance-to-job flag with a reason; no geofence auto clock-in, no background location. Reason: Play's background-location policy review would stall releases; competitors that require "Allow all the time" get battery and drift complaints.
- **UI standards are part of the contract** (`docs/ui-standards.md`, SPEC AC-U). Navigation 3, Material 3 1.4 Expressive, bottom navigation, top app bar with back on every non-root screen, ListItem rows, one FAB, screenshot tests committed. Reason: v1's UI failed on structure; rules must be testable by QA every night.
- **QA tests the product, not only the ACs.** The nightly tester reads PRODUCT.md, audits the UI rules, performs `qa/product-walkthrough.md`, files "Product gap" findings and writes a verdict. Reason: v1 QA reported "New issues: 0" on a product Nate rejected because its instructions only covered acceptance criteria.

## 2026-10-19 — Technical choices (from research, confirm by building)

- **Navigation 3** (`androidx.navigation3` 1.2+) instead of Navigation Compose 2.x. Reason: Nav2 is in maintenance mode; Nav3 owns the back stack as Compose state, supports per-tab stacks, predictive back and adaptive list-detail.
- **Material 3 1.4.0+ with `MaterialExpressiveTheme`**, `material3-adaptive` 1.3+, `NavigationSuiteScaffold`. Reason: first stable with Expressive components (FAB menu, loading indicator, flexible top bars); adaptive scaffolds for tablets.
- **3D with SceneView** (`io.github.sceneview:sceneview`, Filament inside), meshes built at runtime from the plan, stock lit material, textures from the wall photos; never ship `filamat-android` (shader compiler, +10 MB per ABI) or `arsceneview`. Reason: the only maintained Compose-friendly 3D library; Filament types are exposed so dropping to Filament directly later carries the geometry code over. Expect roughly +4–7 MB download.
- **Perspective correction with `android.graphics.Matrix.setPolyToPoly` (4 points)**, which is a full homography. No OpenCV (+15–25 MB per ABI; only worth it for automatic corner detection, not in scope).
- **No AR room scanning.** Reason: Android has no RoomPlan equivalent; magicplan removed its Android AR scan; manual draw with typed dimensions is what Android users use (Floor Plan Creator).
- **On-device OCR with ML Kit text recognition (bundled)** plus a brand-aware parser; the backend Claude vision call is a fallback only. ML Kit document scanner for receipts and plans.
- **Serial-number age decoding hand-coded per brand** from public references (building-center.org, InspectApedia, manufacturer pages) for the 14 brands in AC-C32. HomeSpy's API is a possible later fallback.
- **Weather from Open-Meteo** (free, no key) through the backend. **Recalls from the CPSC saferproducts.gov API** (free, no key).
- **Push through Firebase Cloud Messaging** for delivery only (Cloudflare has no push). Pending Nate's confirmation (BLOCKERS).
- **Email through Resend** (free tier) for sign-in codes and handoff emails. Pending Nate's confirmation (BLOCKERS).
- **Auth: Sign in with Google via Credential Manager + email code**; sessions as JWTs stored with `security-crypto`. Reason: no password handling, works for crews with Gmail accounts and for anyone else by email.
- **Sync: outbox/inbox with `serverSeq`**, last-writer-wins per field for scalars, union for lists, server-authoritative for Owner/Office punch edits. Reason: simple, testable, good enough for a 10-person company; a conflict log keeps nothing silent.
- **Screenshot tests with the Compose Preview Screenshot Testing plugin**, reference PNGs committed. Reason: the cloud builder has no emulator; committed PNGs let the builder look at its own UI and let Nate and QA review screens without installing.
- **Monorepo**: Android modules and `backend/` in one repo. Reason: one plan, one builder, one QA; API changes land with the client changes that need them.

## 2026-10-20 — Automated QA suite in the cloud

- **Testing moves off Nate's PC.** A GitHub Actions workflow (`qa.yml`) runs the mechanical suite four times a day on KVM emulators: JVM checks, a coverage gate, a device suite per app (Maestro flows, instrumented acceptance tests and screen crawler, dark/200%/landscape tour, cold start, jank, monkey, release launch, logcat scan), a handoff suite, results on the `qa-results` branch and a `## QA auto` entry in REVIEW.md. A Claude Code routine (`docs/qa-judge-prompt.md`) reads the results and screenshots and writes the product verdict. Antigravity stays as the exploratory layer when it has usage. Reason: the nightly run was skipped for lack of Antigravity usage; the suite had to be detached from the PC without losing the product-judgment half.
- **One suite per app, plus a handoff suite.** Separate jobs, flow folders, instrumented packages and walkthrough tables, with focus guards that assert the other app's concepts do not leak. Reason: Nate wants the apps to stay distinct even though they share modules.
- **Coverage gate.** Every AC a ticked task claims must be referenced by a test or flow (`qa/scripts/coverage-check.py`), otherwise a Major is filed. Reason: this is how the suite follows the builder automatically; the builder's own tests are the suite.
- **Maestro for end-to-end flows**, instrumented Compose tests for acceptance criteria and the crawler, `testTagsAsResourceId` for stable targets. Reason: flows read like the walkthrough and are cheap for the builder and the judge to write; instrumented tests give accessibility checks and screenshots per screen.
- **Cost**: ~35–55 billed Actions minutes per run on a private repo; four runs a day exceeds the free 2,000 minutes a month. Nate decides between paying, two runs a day, or a public repo. The account's $0 spending limit means runs stop rather than bill.

## Open questions

See BLOCKERS.md "Decisions to confirm".

## 2026-10-19 — T001 scaffold versions

- Gradle 9.8.1, AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.21, KSP 2.3.12, Compose BOM 2026.09.00, Room 2.8.5, Nav3 1.2.0, screenshot plugin 0.0.1-alpha16 (applied in T005).
- **material3 1.5.0-beta01 instead of 1.4.0**: in 1.4.0 `MaterialExpressiveTheme` and `ExperimentalMaterial3ExpressiveApi` are internal; the 1.5.0 line is the first to expose them. Revisit when 1.5.0 is stable.
- No `jvmToolchain`: the VM has only JDK 21, so Java/Kotlin target 17 is set through `compileOptions`.
- Catalog versions for dependencies not yet used (okhttp, retrofit, mlkit, firebase, googleid, camerax, etc.) are best guesses; verify when each is first added.

## 2026-10-19 — T002 theme
- Brand palettes are hand-picked tonal values in `core-ui/.../theme/Brand.kt` (no seed-color generator library). Typography and shapes use M3 defaults with 8 dp-grid radii; revisit at V review.
- Window theme: Material.Light on API 26-28, DeviceDefault.DayNight on 29+ (lint NewApi).

- 2026-10-19: Added `com.android.tools.screenshot:screenshot-validation-api` (same version as the screenshot plugin, alpha16) as `screenshotTestImplementation` in :core-ui; it supplies `@PreviewTest`, without which the plugin discovers no previews. Plugin needs the flag both in gradle.properties and as `experimentalProperties` in the module.

- 2026-10-19: backend npm uses `.npmrc legacy-peer-deps=true` because npm 10.9 crashes (arborist `edgesOut`) resolving @cloudflare/vitest-pool-workers peers. Pool package is deprecated in favor of @cloudflare/vitest-plugin (not on the approved list); kept the approved one. wrangler.toml compatibility_date 2026-08-01 (local workerd supports up to 2026-08-22).

## 2026-10-19 T009 company and team

- One live invite code per company (8 chars, no 0/O/1/I); rotation replaces it. Joiners always start as Crew. Reason: simple revocation, matches AC-F2.
- Admin routes (Team, Company profile, invite) return 404 to non-admin members, same as a missing company, so role gates reveal nothing (AC-B3).
- Field Log caches the last membership in SharedPreferences so it opens offline; sign out clears it through an AuthRepository sign-out listener (AC-F4).
- Added Robolectric and compose-ui-test (already approved) to :field-log tests; applied the screenshot plugin to :field-log (closes T005a for that module).

## 2026-10-20 — Sync design (T010)
- Sync reads and writes rows as JSON straight from the Room tables (`RoomRowStore`, entity name = table name) instead of one adapter per DAO, so new entities sync with no extra code; the server whitelists entity names per tenant kind.
- Pushes carry only fields changed since the server-confirmed row (`sync_base` table), because whole-row payloads would defeat field-level last-writer-wins. Fields named `*Json` that hold arrays are unioned. Outbox gained `tenantKind`/`tenantId`/`serverSeq`; schema v1 edited in place (no released build).
- Only punches are server-authoritative: an Owner/Office edit of an existing punch pins it; later Crew/Lead/Sub writes are refused and logged in `sync_conflicts`.

- 2026-10-20 (T011): HomeRepository lives in :house-model (needs the template) and takes an onChange callback; sync wiring (T010a) stays open until the engine is wired into AppContainer. Skip for now creates one empty Main floor and ignores the template and garage. Draw your floor plan creates the templated floors and lands on Home until the editor exists (T013). Split-level and raised ranch halves sit at ±48 in; Garage goes on Main, or Lower when there is no Main. Restore from a backup and Sign in on onboarding wait for the backup task; Sign in stays on More.

## 2026-10-20 Plan editor lives in :house-manual for now
T013 puts the editor screen, ViewModel and canvas in `:house-manual` (`...housemanual.plan`). The editing rules are in `:house-model` (`PlanEditor`) and are shared. When FL-House (AC-F48) is built the canvas composable moves to a shared UI module. Room polygons are stored as JSON via `org.json` (no new dependency). Metric grid is 4 in (about 10 cm). Rooms with no polygon (the seeded Garage) show as "Place" chips rather than on the canvas. Room selection by TalkBack is not covered yet; tracked for S4.

## 2026-10-20 — Blueprint lives in :house-model with Compose
The blueprint renderer is shared by House Manual and Field Log (T019), so :house-model now applies the Compose plugin and exposes :core-ui as api. Layer toggles persist per home in SharedPreferences (PrefsLayerStore), not Room, since they are per-device view state and need no sync. Layer markers are drawn from a BlueprintMarker list; nothing feeds it until items and overlays exist (later tasks).

2026-10-20 — Added `androidx.test:runner` (androidTest only) for the instrumented crawler; it ships with the approved androidx.test family. Catalog and QaActivity live in each app's `src/debug` so release builds carry neither. Dark mode and font scale for the crawler come from system settings (`cmd uimode`, `font_scale`), not from the activity.
- 2026-10-21: wrangler.toml ENVIRONMENT defaults to dev (email codes logged, /dev routes allowed locally); production deploy passes --var ENVIRONMENT:production. Espresso pinned to 3.7.0 in androidTest for API 37 (InputManager.getInstance reflection).

## 2026-10-21 — Token store opened off the main thread
Cold start was 4–5 s; `EncryptedSharedPreferences`/MasterKey creation ran on the main thread inside `AuthRepository`'s constructor. `AuthState.Restoring` now covers the gap; containers build `AuthRepository(restoreNow = false)` and call `restore()` on `Dispatchers.IO`. Tests and tools keep the synchronous default.

## 2026-10-21 — Dev sign-in code storage
`/dev/last-code` needs the plaintext code, but `email_codes` only stores a hash. In dev only, `/auth/email/start` also writes the code to KV (`devcode:<email>`, 10 min TTL). Production never writes it, and `/dev/*` returns 404 unless `ENVIRONMENT=dev`.

- 2026-10-21: Onboarding 'Skip for now' seeds the house type's floors (no rooms, no Garage room) instead of a single Main floor. PRODUCT.md step 1 says the type seeds floors; SPEC's single-Main wording contradicted it. Recorded in BLOCKERS.md for Nate to confirm.

## 2026-10-21 — Debug seed receivers
House Manual seeds through `HomeRepository` (same code path as the app) after `clearAllTables`; Field Log has no local data, so its seed drives the dev backend (`/dev/seed`, `/dev/last-code`) and signs in as the seeded owner. Receivers live in the debug source set only. `AppContainer.db` became `internal` so the debug receiver can reach it.

## 2026-10-22 — Blueprint PDF scale
PDF pages use the largest standard drafting scale (1/2" = 1' down to 1/32" = 1') that fits Letter, so the printout is genuinely to scale; the scale bar is 1/2/5/10... feet. Exports go to cache/exports through a FileProvider.

## 2026-10-22 Job rooms' home rows are not synced yet
Home, floor and room rows are home-tenant entities on the backend, but Field Log only holds a company tenant. T018c2a stores them locally; syncing them needs a decision on how a job's home gets a home tenant (create one per job, or sync under the company). Left open in T018c2b2 rather than pushing rows the backend would reject with 400 and block the outbox.

## 2026-10-23 Job home rows sync under the company tenant
Resolves the 2026-10-22 open question: a job's home, floor and room rows sync inside the company tenant (no per-job home tenant). The backend company entity list gains home, floor and room only; T019 adds walls, overlays and items as FL-House needs them. The handoff (AC-B40) copies the house model into the homeowner's home tenant.

## 2026-10-23 — Shared house UI lives in :house-model
Plan editor, House tab, Floors and their ViewModels/strings moved from :house-manual to :house-model (package `housemodel.ui`) so Field Log's job House tab reuses them unchanged (AC-F48). A job's home is created lazily (`JobHomeProvisioner`) when the House tab is first used or a room is typed on the job form.

2026-10-23 — Pseudo-rooms (Exterior, Whole house) are stored as reserved item.roomId values `pseudo:exterior` / `pseudo:whole_house` (no FK on roomId), so AC-C30 'room required' holds without fake room rows on the plan.

2026-10-23: ItemEdit stores its draft in SavedStateHandle as a flat string list (ItemDraft is not parcelable and house-model has no serialization plugin). Blank item name is prefilled with the chosen subtype label. Items tab root is a minimal empty state with Add item until T027 builds the list.

- 2026-10-23: Item photos use the platform TakePicture and PickVisualMedia contracts (no CameraX yet; CameraX is for Field Log capture, T039) and are copied into filesDir/media through a `MediaFiles` interface in :core. Coil 3 (approved) shows thumbnails in :house-model.

## 2026-10-23 — SerialAgeDecoder rules (T023a)
Brand serial formats are from commonly published manufacturer charts and are heuristics, not guarantees. Letter-code families (Bradford White, GE, Samsung) resolve to the latest cycle year not after today; year-digit families (Trane, York, Whirlpool, LG) resolve to the latest matching year; two-digit-year families fall back to 19xx when 20xx would be in the future. Week rules return the Monday-of-week offset from Jan 1; the decoded date is always shown with its rule and can be overridden. Nate to verify GE/Samsung/Bradford White letter tables against real labels.

## 2026-10-23 — Warranty lookup backend (T025)
- Calls the Anthropic Messages API directly with `fetch` (no SDK dependency), model `claude-sonnet-5-5`, `web_search_20250305` tool with `max_uses: 3` and an `allowed_domains` list of manufacturers and big retailers. "Structured output" is a JSON-only prompt validated with zod (bad fields degrade to null/low confidence).
- Quota counts every request (cache hits included) in a KV day bucket per user; Pro means any membership whose company/home `entitlement = 'pro'`. Cost log is a D1 table `lookup_log`, only for upstream calls.

## 2026-10-24 Paywall and billing seam
RevenueCat is wrapped in `ProBilling` (extends `Entitlements`) in :core so ViewModels and tests use a fake; only `RevenueCatBilling` in :house-manual touches the SDK. Plans are mapped from the `default` offering by package type (annual, lifetime) and show the store's formatted price, never a hardcoded one. The free-limit check for items runs at the Add item entry points (Home and Items), counting live items at tap time.

## 2026-10-25 Clock widget (T035c2)
Glance (already in the approved list, 1.2.0 in the catalog) for the 2x2 widget. The button opens the app on the Clock tab rather than punching from the widget, because a punch can need a reason or a location permission prompt (AC-F9, AC-F18). The widget is static text (since time), no live ticker, so it never needs periodic updates; it is refreshed after each punch from AppContainer.recordChange. The last job worked is preselected by the Clock view model itself, so the same default applies from the tab.

## 2026-10-25 — Time report and export rules (T037a)
- Report ranges are whole Monday weeks so person totals equal FL-Timesheets (overtime is computed one week at a time). Job totals are job-segment time minus breaks, unrounded.
- Shift CSV "OT hours" is the person-week's overtime plus double time, assigned to the latest shifts first, since overtime is a week/day property rather than a shift's.
- QuickBooks CSV is one row per person, job and day (midnight-split), Service Item "Labor"; overtime is not split out because QuickBooks pay items are set up per employee.
- CSV cells starting with = + @ (or - before a non-digit) are prefixed with an apostrophe so spreadsheets do not run them as formulas.

- 2026-10-25: Media presigning (T038a) is hand-rolled SigV4 over Web Crypto against R2's S3 endpoint because the Workers R2 binding cannot presign and the AWS SDK is not on the approved list. Needs R2 API token secrets (BLOCKERS: Needs Nate). Orphan = media_objects row older than 30 days whose key appears in no sync_rows payload.

## 2026-10-25 — Media upload resume
A presigned R2 PUT cannot continue part-way, so "resume" means the whole file is re-sent from the Room queue after a restart or network loss. Uploads are keyed by entity/entityId/filename so retries never duplicate; WorkManager (approved) drives the queue.

## 2026-10-25 Job photos without a house
A Field Log photo for a job with no linked home stores the job id in `photo.homeId` (the column is required). Reason: avoids a schema change; the id is stable and unique, and handoff replaces it with the real home id when a house is linked.

## 2026-10-26 Voice memo transcripts
Android's SpeechRecognizer listens to the microphone, not to a file, so a finished memo cannot be transcribed reliably on every device. T039c2 keeps memos without a transcript (`NoTranscriber`); the person can type one (AC-F27 is best effort). Revisit if on-device file transcription becomes available.

## 2026-10-26 Sync merge: which lists are unioned, refused ops
Only append-only lists are merged as a union: today just `punch.editsJson` (the edit audit log). Every other field, `*Json` lists included (room polygons, stair corners, overlay points, panel slots, change-order lines, job phases, office emails, photo tags), is last-writer-wins, because a union mixes old and new corners and brings back removed entries. The allowlist lives in `RowMerge.UNION_FIELDS` (app) and `UNION_FIELDS` in `backend/src/sync/merge.ts` (server); keep them equal. On pull, a pending local field only overrides the server value if this device changed it (it differs from the last confirmed row). The backend now answers a push with `rejected: [{outboxId, reason}]` for an op it refuses (entity not allowed for the tenant, unreadable payload) and applies the rest; the app marks that outbox row sent with `serverSeq = -1` (`SyncEngine.REFUSED`) so it is kept for the log without a schema change (CR2j owns schema versioning). The company tenant now also accepts `wall`, `opening`, `stair` and `photo`, which Field Log already wrote for jobs and the backend refused. Reason: CR1 findings cr:sync-json-union and cr:sync-poison-op.

## 2026-10-27 Sync bookkeeping per tenant; Room schema versioning starts
The server counts `serverSeq` per tenant, so the device now keeps one pull cursor per tenant (`sync_cursor`, keyed by tenant kind and id) instead of one for the phone. `sync_base` rows carry their tenant so both the cursor and the confirmed rows are dropped when the app leaves a tenant (Field Log: the active company changes or ends, including sign-out; House Manual: the home goes away). Unsent outbox rows are not dropped there; the sign-out wipe and its warning are CR2k. A pulled row is merged and written in one Room transaction, and only fields named in this row's unsent outbox payloads count as this phone's edits. This is the first schema change since testing APKs shipped: the database is now version 2 with an `AutoMigration` from 1 (drops `sync_state`, adds `sync_base.tenant`), the old cursor is discarded so each tenant is pulled once from the start (rows merge, nothing duplicates), and every later schema change bumps the version with a migration (rest of CR2j).

## 2026-10-27 Device-local columns stay out of sync; R2 key on photo and note
`path` and every `*Path` column (where a file's bytes sit on this phone) and `job_file.offline` (Available offline, AC-F20) are this phone's own (`LocalColumns` in `:core` sync). They are stripped from every pushed payload and ignored in every pulled row, so older builds' rows on the server cannot overwrite them either; `RoomRowStore` fills a missing NOT NULL column with "" or 0 so a photo pulled from another phone is stored with `path = ""` (not on this phone). The shared way to reach the bytes is `r2Key`, now on `photo` and `note` as well as `job_file` (Room v3, AutoMigration), set by `UploadedKeys` when an upload finishes and synced. Changing only local columns (`JobFiles.setLocal`/`setOffline`) no longer queues a sync. A rule by name rather than a list per table so new file columns (wall photos, signatures, receipts) are local by default; each gets an `r2Key` when its upload lands. Downloading a pulled photo or memo by its key is not built yet (the Photos tab shows its fallback for a photo with no local file).

## 2026-10-27 — Item Type sheet opens expanded with search
The 40-row Type sheet opened half height, so its lowest visible row sat under the navigation bar and taps fell into the gesture zone (H04). It now uses `skipPartiallyExpanded = true` and starts with a search field (ui-standards rule 11, lists over 20 rows). Matching is word-prefix over the type and category labels (`SubtypeSearch`), so a category word lists the whole category.

## 2026-10-27 — Sync role policy (CR2e1, AC-B3)
Sync push and pull are now gated per entity and role in `backend/src/sync/policy.ts`, matching SPEC's role list and AC-F3. Owner and Office: everything. Lead: own time; the crew's punches and shifts, tasks, job files, daily logs, change orders, materials and assignments on jobs they are on (a live `job_crew` or `assignment` row naming them); captures (photo, note) on any job; the job's house rows (home, floor, room, wall, opening, stair) on any job, because those rows carry no job id. Crew and Sub: own punches and shifts, captures on any job, and only marking tasks already assigned to them. Company, member, client, job, job_crew, timesheet_approval and share_link rows are Owner/Office only. A refused op is answered per op with reason `role`, like the existing `entity` and `payload` refusals, so the rest of the batch applies. Pull leaves out other members' punches, shifts and approvals for Crew and Sub (Lead also sees time on their jobs), strips `rateCents` and `fcmToken` from member rows for everyone but Owner and Office, and still moves the cursor past hidden rows. Home tenants are not role-gated.


## 2026-10-27 — Sign-in rate limits in D1 (CR2f1, AC-B1)
Email-code sign-in had a 5-guess limit per code that every new `/auth/email/start` reset, and nothing limited requests, so codes could be guessed without end and a mailbox flooded. Now fixed windows of one hour: 5 code requests per email and 20 per IP; 10 code checks per email (counted before the check, across every code, so a new code does not reset it) and 30 per IP. The counters are one atomic `INSERT … ON CONFLICT … RETURNING` in a D1 table (`auth_limits`) rather than KV, because KV is eventually consistent and lets parallel requests slip under a limit; old windows are pruned by the daily cron. Over a limit the backend answers 429 `rate_limited`, which the apps show as "Too many tries for now. Wait an hour, then try again." The limits are off only when `ENVIRONMENT=dev`, the local Worker the QA flows sign in to many times per run. The QA workflow now writes `ENVIRONMENT=dev` into `.dev.vars` so the default in `wrangler.toml` can become `production` in the next step (CR2f2) without breaking QA sign-in; the switch waits until `main`'s copy of qa.yml carries that line, since scheduled runs read the workflow from `main`.

## 2026-10-27 — Media orphan rule by owning row (CR2g1, AC-B21)
The orphan cron counted an R2 object as referenced only if some synced row's JSON contained its full key. Only `job_file.r2Key` stores a key; photo and note rows carry an `r2Key` only after this phone's upload finishes, so most photos and every voice memo would have been deleted 30 days after upload. The key is `tenant/entity/id/filename`, so the cron now keeps an object while the sync row (tenant kind and id, entity, id) exists with no `deletedAt`, and removes it 30 days after presign once that row is gone or soft-deleted. Keys that do not parse, or whose tenant segment differs from the stored tenant, are kept. This is one primary-key lookup per object instead of a scan of every row's JSON.


## 2026-10-27 — Field Log sign-out wipes the phone (CR2k, AC-F4)
Sign-out only cleared the cached company profile; jobs, clients, punches with GPS, pay rates, photos, files, the outbox and prefs stayed on the phone for the next account. Sign-out the person asks for (More, Welcome, the no-account prompt) now ends the session and then runs `LocalDataWipe` (`:core`): `clearAllTables()`, `filesDir/media`, `filesDir/offline-files`, `cacheDir/downloads`, `cacheDir/exports`, and the `capture`, `clock`, `media` and `blueprint_layers` prefs, inside `SyncEngine.exclusive` so no sync pass writes rows back mid-wipe; then the clock widget refreshes. The device id (`sync` prefs) is kept. The confirm dialog names the count of unsent outbox rows ("3 changes have not reached the cloud yet…") so nothing is lost silently. A 401 from the server still only ends the session and keeps local data, so an expired token never deletes unsent punches; if a different account then signs in on that phone the old company's rows stay until a sign-out. The debug seed (`regression`, `reset`) uses the same wipe (cr:fl-seed-reset).

## 2026-10-27 — Backend ENVIRONMENT defaults to production (CR2f2, cr:dev-env-default)
`wrangler.toml` set `ENVIRONMENT = "dev"`, so a plain `wrangler deploy` would have exposed `/dev/last-code` (anyone's sign-in code), `/dev/reset` and `/dev/seed`, and turned sign-in limits off. The default is now `production`; dev mode is opt-in through `npm run dev` (`--var ENVIRONMENT:dev`) or `ENVIRONMENT=dev` in the gitignored `.dev.vars`, which qa.yml writes (on `main` too, which the scheduled suite reads, so QA keeps its dev routes). `npm run deploy` names production explicitly. Chose `--var`/`.dev.vars` over an `[env.dev]` block because wrangler env blocks do not inherit D1/R2/KV bindings and would duplicate them. `environment.test.ts` pins the default and the scripts.

## 2026-10-27 — :core I/O leaves Main by itself (CR2i1, cr:core-main-io)
View models call :core from `viewModelScope` (Main). OkHttp `execute()` in `RetrofitMediaTransferApi` and the file work in `MediaCache`, `JobFileTransfers` and `RoomRowStore` ran on the caller's thread, so opening a file that was not on the phone would throw NetworkOnMainThreadException. Each class now takes an `io: CoroutineDispatcher = Dispatchers.IO` (tests inject their own) and wraps its blocking work in `withContext(io)`, so callers need no dispatcher of their own. `RoomRowStore` hops to IO only when called on the main thread: repositories call `SyncEngine.record` inside their own `db.withTransaction`, on Room's transaction thread, and a hop there would read through another connection (missing the uncommitted row) or wait on the transaction; off Main it queries in place.

## 2026-10-27 — Released Room schemas are frozen by a test (CR2j, cr:room-schema-v1)
Testing APKs install over each other, so a schema version that has shipped can never change. `SchemaVersionGuardTest` keeps the identity hash of every released version (1–3) and fails when Room's exported JSON for one of them changes, telling the builder to bump `HomeRecordDatabase.version`, add an `AutoMigration`, restore the old JSON and record the new hash. `HomeRecordMigrationTest.everyReleasedVersionMigratesToTheLatest` opens every earlier version and migrates it to the latest. Chosen over a Gradle check so it runs in `testDebugUnitTest` on every run and in CI.

## 2026-10-27 — Outbox rows are deleted once pulled back; refused upload URLs count (CR2i2)
The outbox only flagged rows sent, so it grew forever. After each pull the sync engine deletes accepted rows whose `serverSeq` is at or below the tenant's cursor; refused rows (`serverSeq` = -1) stay so the refused count and conflict log can show them. Outbox ids are `AUTOINCREMENT`, so a deleted id is never handed out again and the server's `(deviceId, outboxId)` dedupe stays sound. In `MediaUploader` a PUT refused on a reused URL still re-signs at once without counting (an expired signature is normal), but a refusal of a URL signed in the same pass counts as an attempt, so a file the server keeps refusing stops at `MAX_ATTEMPTS` and waits for Retry. PDF markup versions are this phone's own files and go to `filesDir/media` (injected into `PdfMarkup`), not beside a download in the evictable cache.
