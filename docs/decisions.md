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

## 2026-10-27 — Sync push applies each op as a compare-and-swap D1 batch (CR2h1)
Read, merge, counter bump and write were separate D1 round trips, so two pushes to one row could each merge against the same stored row and the later write dropped the other's fields, a seq could be taken before its row was written, and a retried duplicate raced into a primary-key 500. Each op now reads its idempotency record and stored row in one batch, merges in the Worker, then writes one batch (one transaction) whose statements all check that the row is still at the `server_seq` it was merged against: claim the next seq in `sync_applied` (`INSERT OR IGNORE`), bump the counter only if that claim landed, log conflicts and write the row under the same claim. A lost race makes the batch a no-op and the op is merged again on the new row (up to 10 tries; after that the push stops at that op and the phone retries the rest, keeping order). Chosen over a per-tenant Durable Object: no new binding or deploy step, and contention on one tenant is a handful of phones. Pull also caps at the committed counter.

## 2026-10-27 — Media presign split into PUT and GET, with owner, row rule, size and type (CR2g2)
`/media/presign` handed any member a PUT and a GET for any key in their tenant, so a crew member could overwrite another member's photo or a plan, and the download path minted a PUT. Now `/media/presign` signs only a PUT and `/media/presign-get` only a GET. A PUT needs a file-carrying entity (photo, wall_photo, note up to 25 MB; document, job_file up to 100 MB), the company write rule for that row (`canUpload`: the sync push rule when the row is on the server, else captures for everyone, a Lead's job rows, the rest Owner/Office; the row's own push is still checked when it syncs), and the key's owner: the first member to sign a key owns it (`media_objects.owner_user_id`, migration 0007) and only they or Owner/Office may sign it again; home members share the whole home. The PUT signs `Content-Length` and `Content-Type`, so R2 refuses any other size or type. A GET needs a live row of the caller's tenant that the pull rules let them see. One bucket name (`MEDIA_BUCKET`, matching the `MEDIA` binding; `R2_BUCKET` dropped) so the orphan cron deletes where uploads land. The app sends size and type when it signs, treats 413 like 403 (stop retrying), and downloads job files through presign-get. `/dev/reset` also clears media_objects, lookup_log, lookup quotas and the local R2 bucket.

## 2026-10-27 — Company clock rules ride the membership on /me (CR2l1)
Clock, Timesheets and Reports read the clock rules (require GPS, punch distance, break rule, overtime rule) from `GET /companies/:id/profile`, which only Owner and Office may read, so Crew and Lead always clocked in under the defaults, offline phones too, and the value was read once per screen. `/me` now sends each company membership its `settings` (merged with the defaults; the profile stays admin-only for the other fields). The app caches them with the membership (`PrefsCompanyCache`, so a restart offline keeps them), `CompanyRepository.settings` is a Flow the three ViewModels combine, and Field Log refreshes `/me` on every return to the foreground so a change from the office reaches crew phones. Chosen over syncing the company row: the settings live in the D1 `companies` table, not in the sync log, and `/me` is already called at start.

## 2026-10-28 — Job status is read for today, not trusted from the row (CR2l2, cr:fl-job-status-stale)
The `job.status` column was set only when JobEdit saved, so a job saved as Upcoming never became Active (missing from the Clock picker and Today) and a finished job stayed Active. `JobStatusRule.effective` in `:core` derives the status from the dates on the day asked and is used by Jobs, the job Overview, Today, Clock and the Timesheets job picker. Archived always stays archived; a Done job with no end date stays done (no date contradicts it). The column is still written on save so other readers (backend, older builds) see a sensible value.

## 2026-10-28 — Field Log role gates in one RoleGates rule (CR2l3, cr:fl-role-gates)
Only More hid admin items; Crew could create and edit jobs, add, rename and delete files and delete photos, and a Lead could clock in the crew on any job, all of which the server then refused on sync (AC-F3, AC-B3). `RoleGates` in `:core` (role plus the Lead's jobs from job_crew and assignment, live) mirrors `backend/src/sync/policy.ts`: New job and Edit job Owner/Office only; file add, folder, rename and delete, photo delete and crew clock-in for Owner/Office and the Lead on their own jobs. `AppContainer.roleGates` follows the cached membership role (least privilege while no company is active), so a role change lands on the next /me refresh. Screens hide what the role cannot do (no greyed-out buttons) and the ViewModels ignore the events too, so a stale tap does nothing.

## 2026-10-28 — Before/After pairing is its own tag and opt-in at capture (CR2o1, cr:fl-before-phase, cr:fl-ghost-autopair)
A photo's Before mark lives in `photo.pairRole` (Room v4), not the phase column, so a photo can be both "Rough-in" and a Before, and the mark can be taken off. The After is still identified by `beforeOfPhotoId`. Capture never pairs a shot unless the person turned on "Line up with Before", and only a Before from the selected room is offered (no room matches no room); the overlay turns itself off after one paired shot. The backend stores rows as JSON, so no D1 migration is needed; a row from an older testing APK with phase "Before" reads as a phase until that phone updates.

## 2026-10-28 — Annotated copies are written upright, decoded under 8 MP (CR2o2, cr:fl-annotate-exif)
The editor (Coil) shows a photo upright by its EXIF orientation, so the renderer now reads the tag with the platform `android.media.ExifInterface` (API 24+, no new dependency; `androidx.exifinterface` is not on the approved list), turns the pixels before drawing, and writes the copy with no orientation tag so every viewer, PDF and report sees it the same way. `ImageOrientation` in :core maps the eight EXIF values and picks a power-of-two decode step that keeps the bitmap at or under 8 MP (32 MB), decoded mutable in one pass; only a turned photo needs a second bitmap. The editor's aspect uses the same upright size. FL-Annotate moved to `FormScaffold` (Close and Save, discard prompt when there are marks), with Undo next to the tool chips.

## 2026-10-28 — Removing a room on FL-JobEdit confirms when the room is in use (CR2s4, cr:fl-jobedit-room-delete)
`RoomRemoval.of` decides: a room not yet saved, or saved but unplaced (empty polygon) with no live photo, item or task pointing at it, comes off at once; otherwise the form shows a rule-16 AlertDialog naming what goes. Counts come from one `RoomDao.usage` query (three sub-selects, no schema change). Rows that point at the room keep their job and lose the room rather than being deleted, since a homeowner's items and a crew's photos outlive a room name on a job form.

## 2026-10-28 — Delete cascades in the house model (CR2n1, cr:item-delete-cascade, cr:room-delete-cascade)
Soft deletes cascade in the repository, inside one Room transaction, with an onChange per row so every child tombstone syncs; no SQL foreign keys (soft delete never fires ON DELETE, and synced rows can arrive before their parent). Item: warranty, consumables, service records, reminders, photos, breaker links and a panel with its breakers go; documents and overlays stay with itemId cleared; other items' shutoff link is cleared. Room: walls (with openings and wall photos), stairs, overlays and breaker links go; items move to Whole house (losing wall and plan position), photos and documents stay with no room. Floor: each room as above, and stairs elsewhere that led to it lose their destination. savePlan is one transaction too. Field Log's JobEdit room removal is unchanged (CR2s4 decision).

## 2026-10-28 — Walls keep their identity by endpoints (CR2n2, cr:wall-identity)
Every edge of a placed room now has a `wall` row (before, only edges with a door or window did), so wall photos and wall overlays (T048) have a wall to attach to. On save, `WallMatcher` matches the stored outline to the new one: an edge whose corners did not move keeps its wall; otherwise a wall goes to the new edge running the same way along an overlapping stretch whose corners moved least (dragging a side keeps that wall and its two neighbours, adding a corner keeps the wall on the part of the split edge nearest its old corners, a reversed winding is compared reversed, and a room moved across the plan is compared as if it had not moved). `wall.edgeIndex` is rewritten to the new index. A wall whose edge is gone (a corner removed, an edge turned diagonal) is soft deleted with its wall photos and wall overlays; items hung on it stay in the room with no wall. Chosen over a stable edge id in the polygon JSON because it needs no schema or sync format change; a home saved before this gets its missing wall rows on its next plan save.

## 2026-10-28 — Plan editor typing is one edit (CR2n3, cr:plan-keystroke-commit)
A typed room name is shown on the plan as a draft (`PlanEditor.previewRename`) and a typed width/length is held without resizing, both in the ViewModel's SavedStateHandle; IME Done, focus leaving the field, Done, the editor closing or any other edit commits them as one undo step each and one save. Chosen over merging per-keystroke undo steps because a half-typed size ("1" on the way to "14") would still resize the room and write the outbox. Plan saves run under NonCancellable so a save started as the editor closes finishes.

## 2026-10-28 — The theme provides the test tag prefix (CR2q3a, cr:shared-hm-tags)
`Brand` carries its tag prefix (`hm`, `fl`) and `HomeRecordTheme` provides it as `LocalTagPrefix`, so every screen in :house-model (House, Floors, Plan editor, Room, Item form) tags `<app>_<screen>_<element>` by the app that shows it, including in previews, screenshot tests and Robolectric tests that wrap the screen in the theme. The apps' own `LocalTagPrefix` providers stay (same value). Floors rows are `<app>_floors_row_<id>` instead of the bare `row_<id>`. House Manual ids are unchanged, so its flows need no edit.

## 2026-10-28 — PdfViewer and the document scanner live in :core-ui (CR2q3c, cr:pdf-viewer-dup)
One `PdfViewer(pages, tagPrefix)` serves House Manual documents and Field Log files, so thumbnails, zoom and page rendering stay identical in both apps; the screen passes its tag prefix (`hm_document`, `fl_file`) because the screen name differs per app. `rememberDocumentScanner` moved with it and :core-ui now carries the ML Kit document scanner dependency (approved list) so Field Log can scan into Files (T042c) and Materials without depending on House Manual code.

## 2026-10-28 — Form Save: on for new records, on for edits only once changed (CR2r1, cr:form-dialog)
House Manual's full-screen forms (contacts, consumables, service visits) enable Save for a new record so a tap shows what is missing inline (ui-standards rule 21's "or it focuses/shows the first invalid field" path, which the H05 flow relies on), and for an edit only after something changed (nothing to save otherwise). Close and back ask "Discard changes?" only when the draft differs from what the form opened with; the open draft and that original both live in SavedStateHandle. One `DiscardChangesDialog` in :core-ui serves FormScaffold and the House Manual form dialog.

## 2026-10-28 — Upkeep Delete confirms over the open form (CR2r2, cr:upkeep-delete-confirm)
Delete in the consumable and service forms opens "Delete <spec or what was done>?" over the form instead of deleting at once (ui-standards rule 16). A confirm dialog rather than Undo, matching the item, contact and document deletes already in House Manual. The pending record is kept as kind and id in SavedStateHandle and named from the saved row, so it survives process death and a record deleted elsewhere shows no confirm; Cancel keeps the form open, Close on the form drops the confirm.

## 2026-10-28 — Contact and document rows: tap opens, trailing icon buttons act (CR2r3a, cr:card-lists)
Contacts and Documents are `RecordListItem` rows instead of one Card each (AC-U3, rules 7-9). A contact row opens Edit contact on tap (replacing the "Edit" text link) and keeps tap to call and email (AC-C39) as trailing 48 dp icon buttons, labelled "Call <name> at <phone>" and "Email <name>"; supporting text is trade and company, then "Serviced …" (notes fill in when one is missing). A document row opens the viewer on tap; rename and move is a trailing pencil ("Rename or move <name>"). Folder names are section headers (titleSmall, primary, 16 dp top). `RecordListItem` gains `supportingMaxLines` (default 2) so the contact row grows at 200% font instead of cutting "Serviced …" (rule 32). Also: the upkeep Delete confirm now closes the form before deleting, so no state shows the form over a record the list already dropped (the CR2r2 test caught it intermittently).

## 2026-10-28 — Item page sections as headers over rows (CR2r3b, cr:card-lists)
The item page's Details, Warranty, Recalls, Consumables, Service history, Documents, Photos and Notes are no longer Cards: each is a `SectionHeader` (new in :core-ui; titleSmall, primary, 16 dp top, heading semantics, rule 9) over full-width content. Collections (recalls, consumables, service records, documents) are `RecordListItem` rows that open on tap (edit form, viewer or recall notice), like Contacts. Details is a key/value group, not a collection, so it is a compact label/value table instead of one 72 dp ListItem per field (which pushed every other section off the first screen); from 150% text the label stacks above the value so "Manufactured" never breaks mid-word. Reorder is a trailing 48 dp cart icon button labelled "Reorder <spec>". `RecordListItem` gains `overline` and `onClickLabel`.

## 2026-10-29 — House Manual debug seed fills the records, not just the plan (CR2t, cr:seed-items)
`RegressionSeed` (debug only) now adds 22 items through `ItemRepository.save`, so each type gets the consumables and reminders a real user's item would: items in eight rooms plus Exterior and Whole house, two shutoffs, warranties that are active, expiring (30 and 45 days) and expired, three items missing a make or model, the furnace's HVAC service 12 days overdue, a seasonal "Clean gutters" reminder, Dale Harlan (HVAC) and Maria Soto (Plumber) with a service visit each, and two PDFs (a home inspection report in "Purchase" and the furnace manual in "Manuals"). Dates are relative to the day the seed runs, so Expiring and Overdue stay true on any day. The PDF bytes come from a copy of `qa/fixtures/plan.pdf` in `house-manual/src/debug/assets/qa/sample.pdf` (debug source set, never in release). Seed and reset delete `filesDir/documents` so cleared tables leave no orphan files. Flows are unaffected: they `clearState` before running (cr:qa-seed-wiped is the open item that changes that).

## 2026-10-29 — Flow names follow the walkthrough; non-step flows use `x-` (CR2m1, cr:qa-flow-step-mismatch)
Every flow whose name starts with a step id now tests that step of qa/product-walkthrough.md: H01b-home (H1 lands on Home), H17-sign-in, H19-paywall, F04c-today-clock-in (F4 clock in from Today), F07-files and F07b-storage (F7), F15-reports. Contacts and Documents are not a walkthrough step, so their flows are `x-contacts` and `x-documents`; summarize.py reads a step only from an `[HF]NN[a-z]?-` name, so an `x-` flow is still run and still filed as a finding when it fails, but never marks a walkthrough step Pass or Fail. Old `auto:flow` ids for the renamed flows will not recur; new failures carry the new names.

## 2026-10-29 — Coverage gate reads every ticked id and AC, by the right kind of test (CR2m2, cr:cov-task-regex, cr:cov-loose-rules)
`qa/scripts/coverage-check.py` now reads every ticked PLAN line whose id is T, Q, R, V or CR plus any suffix (T040b2, T041b2b, CR2g1) and takes AC ids from anywhere on the line, not only an `(acceptance: …)` group. A `[unit]` criterion needs a JVM test under `src/test` (a flow no longer stands in), an AC-B criterion a vitest file, every other criterion an `androidTest` file or a Maestro flow; screenshot tests are no longer coverage and nothing under `node_modules`, `build` or `.gradle` counts. CR tasks are included because they tick work with AC ids (CR2b2, CR2g1). The stricter gate found AC-F17, F21, F25, F26 and F52 claimed by eleven ticked subtasks with no device test; Field Log gets its first acceptance test class, `PhotosClockAndOfflineAcceptanceTest` (ghost overlay, side-by-side pair, Before mark, every annotate tool with undo and save, widget registered as 2×2, PDF markup on real files and Room, offline punch and the Today waiting count), and `truth` on its androidTest classpath (already approved, used by House Manual's).

## 2026-10-29 — Flow coverage counts only tagged asserted steps; typed coordinates as a job address (CR2m3, cr:qa-ac-claims-comment)
`qa/scripts/coverage-check.py` no longer counts an AC id anywhere in a flow file. In a Maestro flow an AC counts only from a comment in the commands section (after `---`) whose steps, up to the next comment, include an assertion (`assertVisible`, `assertNotVisible`, `assertTrue`, `extendedWaitUntil`, `scrollUntilVisible`); the header comment above `---` and comments over taps alone do not count. Reason: header prose claimed AC-F8/F10/F11, AC-F54 and AC-C35 for flows that never exercised them, and the gate passed them. The stricter gate found 68 claimed ACs with no asserted step; each now has one (tags over the asserts that test it, new steps where none did: H02 rotation for AC-C12 via `setOrientation`, H03 reading back the 12'6" length for AC-C4, H04 Home search by model number and room for AC-H3, H17 local copy kept after sign-out, F03 Overview stats, F04 far-from-job reason and switch job, new F08b shutter photo for AC-F24) or a device test (FilesAndMediaAcceptanceTest for AC-F19, F20, F54; ClockCrewAcceptanceTest for AC-F11, which F04 no longer claims since it only checked the card is there). Ids a flow did not exercise were dropped from its header (F08: AC-F24 moved to F08b, AC-F54 to the device test).
F04 needs a job location without a network geocoder on the emulator, so `CoordinateAddress` lets a job address typed as "lat, lng" be its own location (stored on save, offline). That is also what a contractor on a lot with no street number wants; recorded under Decisions to confirm.

## 2026-10-29 — Release builds use R8 and HTTPS only (CR2p1, cr:build-no-r8, cr:build-release-debug-leak)
Both apps' release build type now sets `isMinifyEnabled` and `isShrinkResources` with `proguard-android-optimize.txt` plus a per-app `proguard-rules.pro`. The libraries (Room, kotlinx-serialization, Retrofit, OkHttp, Coil, ML Kit, CameraX, Glance, RevenueCat) ship consumer rules; ours add only what reflection needs past them: `@Serializable` companions and `serializer()` (API bodies, Navigation 3 keys, sync rows), Retrofit service interfaces and their generic signatures, the `RoomDatabase` `_Impl` constructor, and source/line attributes so field stack traces map back. `API_BASE_URL` moved from defaultConfig into the build types: debug keeps `local.properties` `api.baseUrl` (default `http://10.0.2.2:8787`), release reads `api.releaseBaseUrl` or `API_RELEASE_BASE_URL` and defaults to `https://api.homerecord.app`; the build fails if the release URL is not https. `allow_cleartext` is false in `src/main` and true only in `src/debug`. The production domain is a Needs Nate item.

## 2026-10-29 — Cold start: lazy HTTP clients, baseline profiles, gated on the release build (auto:coldstart)
Each app container built three `OkHttpClient`s in `Application.onCreate` (sign-in, the authenticated client, the plain presigned-URL client); each one loads the platform trust store and TLS provider on the main thread. The Retrofit factories in `:core` now take `okhttp3.Call.Factory`, and the containers pass `LazyCallFactory`, which builds the client on its first request (always off the main thread). Both apps ship `src/main/baseline-prof.txt` with wildcard rules for all `com.kurtz.homerecord` code; AGP expands them against the R8 output and merges them with the profiles Compose, Room and the other libraries ship; `androidx.profileinstaller` was already on the classpath through Compose, so no dependency was added. The QA suite gates AC-U11 on the release APK after compiling that profile (`INSTALL_PROFILE` broadcast, then `cmd package compile -m speed-profile`), which is what a Play install does and what Macrobenchmark's `CompilationMode.Partial` measures; a debuggable APK has no AOT code and StrictMode on, so its number is kept as information only (`coldstart-debug-<app>.json`) and gated only when no release APK was built. The QA judge has recommended gating on release since 2026-10-22.

## 2026-10-29 — Scroll jank measured on a benchmark build with a seeded 200-row list (auto:jank, judge:qa-jank-no-list, cr:qa-perf-gates)
The jank stage swiped the launch screen of the debug build and reported 5–30 launch frames as "100% janky"; nothing scrolled. AC-U11 and S7 ask about a 200-row list, and a debuggable Compose build is not what people run. Both apps gain a `benchmark` build type: `initWith(release)` (R8, resource shrinking, baseline profile), signed with the debug key, `matchingFallbacks = release`, the dev `API_BASE_URL`, and the `src/debug` sources, assets, res and manifest so the QA seed receiver exists. It is built only by `qa.yml`; `release` itself stays free of debug code. The seed receivers take a `long-list` scenario (House Manual: the regression home grown to 200 items, the extra ones each room's finishes and devices; Field Log: the regression company plus 200 local jobs, not queued for sync). The stage installs the benchmark APK, compiles its profile as a Play install would, seeds, opens Items or Jobs with `qa/flows/<app>/_jank.yaml`, resets gfxinfo, flings 16 times at the screen's own size and gates on 10% janky frames; under 100 rendered frames it reports "not measurable" instead of a percentage. The emulator renders with SwiftShader, so a failure there still needs a look at `logs/gfxinfo-<app>.txt` before it is read as an app problem.

## 2026-10-29 — House Manual registers its offline-made home on the server before syncing (judge:hm-signin-sync-failed, AC-H15)
House Manual creates its home on the phone with its own UUID before anyone signs in, but the backend had no way to create a home, so every sync pass after sign-in hit `/homes/<id>/sync/push` with no membership and got 404 ("Sync failed"). New `PUT /homes/:id` (idempotent): an unknown UUID creates the home with the caller as owner (201), a home the caller already belongs to answers 200, someone else's home answers 404 like any unseen tenant (AC-B2). The client side is a `prepare` hook on `SyncEngine` (default no-op, so Field Log is unchanged) that House Manual fills with `HomeRegistrar`: it registers the home once per process and account (reset on sign-out), and a failed registration ends the pass as Offline/Failed like any other sync error. Client-chosen ids rather than a server-issued id so the offline-first local data never has to be re-keyed. Failed passes are now logged under tag `HomeRecordSync`.

## 2026-10-29 — Per-role tenant columns and one company per account (CR2e2)
`GET /companies/:id` and `GET /homes/:id` select an explicit column list per role. Company: every member gets id, name, trade, phone and the clock settings (merged with defaults, the same rules `/me` already gives every member); Owner/Office also get email, address, logoKey, trialEndsAt, entitlement, createdAt. Home: members get id and name; the owner also gets entitlement and createdAt. Join enforces one company per account (BLOCKERS T009): a user with any company membership other than the code's company gets 409 `already_in_company`, which Field Log shows as "This account is already on a company…". Creating a company is not limited here; only joining was in scope.

## 2026-10-29 — Email-code HMAC, token_version revocation and a 32-byte JWT_SECRET floor (CR2f3)
Email codes are stored as HMAC-SHA256(`JWT_SECRET`, "email-code:<email>:<code>") instead of a bare SHA-256, so a leaked `email_codes` table cannot be brute-forced over the million six-digit codes; reusing `JWT_SECRET` avoids a second secret Nate must set, and the domain prefix keeps the two uses apart. Sessions still refresh on use (AC-B1) but every token now carries `ver` = `users.token_version` (migration 0008); `requireSession` refuses any other version and any token without one, and `POST /auth/revoke` bumps it to sign out all of an account's phones. The apps do not call it yet: a plain sign-out on one phone must not sign out the other (H17 second phone), so it waits for a "Sign out everywhere" or account deletion screen. A `JWT_SECRET` under 32 bytes now throws on sign, verify and code hashing, so sign-in and every signed-in route answer 500 rather than run on a guessable key (QA's dev secret is 54 bytes). README lists the three R2 secrets.

## 2026-10-29 — Sync push limits and pruning (CR2h2)
`/sync/push` takes at most 2 MiB through Hono's `bodyLimit` (413 `too_large`) and 200 ops. The per-op payload cap (256,000 characters) moved out of the zod schema into a per-op refusal (`too_large`), because a schema failure answered 400 for the whole batch and one oversized row would have blocked a phone forever. Phones size batches with `PushBatches` to 1 MiB of encoded ops, half the server limit, so JSON escaping and the envelope never push a batch over; a single op always goes alone (worst case ~1.5 MB with escaped control characters, still under 2 MiB), and an op over the payload cap is set aside on the phone as refused (shown in "Changes that didn't sync") without being sent. The daily cron deletes `sync_applied` and `sync_conflicts` rows older than 90 days (migration 0009 adds `sync_applied.applied_at`; existing rows are stamped with the migration time). Trade-off: a retry of an op applied more than 90 days earlier is merged again rather than deduped; the merge is per-field last-writer-wins on the op's own `updatedAt`, so the replay changes no field a newer write set, only takes a new seq. `sync_rows` (the data) is never pruned.

## 2026-10-29 — Release signing and the dependency list reconciled (CR2p2)
Both apps get a `release` signingConfig built from `local.properties` keys `homerecord.storeFile`, `homerecord.storePassword`, `homerecord.keyAlias` and `homerecord.keyPassword` (the keys the testing-apks workflow already wrote and nothing read). When `homerecord.storeFile` is absent the config is not created and `assembleRelease` emits an unsigned APK as before, so a fresh container still builds; the workflow keeps its apksigner fallback for that case. A store build puts the upload key under the same keys; the keystore and passwords never enter the repo.
Two artifacts outside the approved table stay, recorded here and under Decisions to confirm: `androidx.lifecycle:lifecycle-viewmodel-compose` (the `viewModel()` factory call the Routes use; same lifecycle release and already pulled in transitively by the approved `lifecycle-viewmodel-navigation3`, declared directly because the code imports it) and `androidx.room:room-testing` (test-only, `MigrationTestHelper` for the Room migration test; same Room release as the approved runtime). Eight catalog entries no module used (material3-adaptive, -layout, -navigation3, coil-network-okhttp, reorderable, datastore-preferences, sceneview, firebase-messaging) and their version keys were removed; each comes back with the task that first uses it, at the then-current stable version.

## 2026-10-29 — Shifts derived on read; one API error mapping (CR2u)
SPEC lists `Shift` as "derived, cached". Nothing read the cache, and every punch rewrote all of a member's rows, so the writes and `ShiftDao` are gone: every screen builds shifts from punches with `ShiftBuilder` (cheap at a crew's scale). The `shift` table stays in the schema (no migration) so a `shift` row pulled from sync still lands; a cache can return if a list ever needs it. The Retrofit clients share `core/net/apiCall {}`: 400 and 413 are Invalid, 403 Forbidden, 404 NotFound, others Failed, no network Offline, and an unreadable body (SerializationException) Failed rather than a crash; the warranty and lookup clients keep "400 Invalid, anything else Failed"; auth keeps its own `AuthResult` mapping.

## 2026-10-29 — Lookup quota in D1 (CR2u sweep 2)
The per-account daily lookup limit (AC-B10) moved from KV (read, then write; eventually consistent, so a burst of parallel lookups all read the same count) to a D1 table `lookup_quota (user_id, day, used)` (migration 0010). Taking a request is one conditional upsert (`ON CONFLICT … DO UPDATE SET used = used + 1 WHERE used < limit RETURNING used`); no row back means 429. A refund decrements the same row. The daily cron deletes rows older than yesterday, and `/dev/reset` clears the table. KV still holds cached lookup answers.

## 2026-10-30 — Warranty lookup answers through a strict tool; one money parser (CR2u sweep 3)
AC-B10 asks for structured output. The warranty lookup now gives the model a client tool `report_warranty` with `strict: true` and a JSON schema (every field required, `additionalProperties: false`, nullable numbers and URLs, confidence as an enum) and reads the tool call's input, still checked by the zod `resultSchema` for the ranges a strict schema cannot express (0–100 years, URLs). A strict tool was chosen over `output_config.format` because a web search answer carries citations, which JSON output mode refuses; `tool_choice` stays `auto` (forced tool choice is refused by current models) and the prompt asks for exactly one call. If no valid call comes back, the old text JSON parse is the fallback, so a billed answer is still used when it can be. Money: `Money.parseCents` and `Money.format` in `:core` units are the one parser (item price, service cost, the crew rate dialog); `MoneyField` gains a text overload for forms whose drafts live in SavedStateHandle as text.

## 2026-10-30 — House Manual resizes for the keyboard (judge:hm-rail-ime-status-bar)
House Manual's activity sets `android:windowSoftInputMode="adjustResize"`. With no mode the system picked pan for the Compose window: opening the keyboard in phone landscape slid the whole window up, so the navigation rail sat under the status bar, and the pan stacked on the `imePadding` the plan editor and forms already apply. Every House Manual text field is in an `imePadding` container, a top search bar, a dialog or a sheet, so nothing relied on the pan. Field Log keeps the default for now: its capture note and photo caption fields have no `imePadding` yet. The plan editor also drops the floor chips when its area is under 200 dp tall, so the canvas keeps that row.

## 2026-10-30 — Field Log Files: one FAB menu; EmptyState action optional (judge:fl-files-empty-and-add)
The Files tab's top row of Add file and New folder buttons is gone. Both actions sit behind one `FloatingActionButtonMenu` at bottom end (rule 13 allows a menu for 2 to 4 create actions), the same pattern as House Manual Documents. The empty state follows rule 10 (folder icon, "No files yet", one sentence, Add file opening the same menu), and the search field is hidden until the job has a file or folder. Crew and Subs, who may only view (AC-F3), get no FAB and an empty state with no action, so `:core-ui` `EmptyState` now takes a nullable `actionLabel`; existing callers are unchanged.

- 2026-10-30: Far-from-job punch reasons are stored as stable codes (`materials`, `supply_house`, `shop`) for the presets and as the typed text for Other, so a punch reads the same in any phone language; `FarReason.fromStored` maps a code back to its strings.xml label, and older English values show as typed. HM-Items: a search shows every group open, even ones the owner folded, so no match is hidden (cr:fl-far-reason-strings, cr:items-collapse).

## 2026-10-30 — House Manual sign-out asks first; previews live in debug
- Sign out in House Manual opens a "Sign out?" dialog (matching Field Log's More) because one tap ended backup with no warning (cr:route-logic). State in AccountViewModel (:core-ui), shared by both apps' AccountRoute.
- House Manual `Previews.kt` moved from `src/main` to `src/debug`: debug, benchmark (which reuses src/debug) and screenshot tests still compile against it; release no longer ships 600+ lines of fake data (cr:previews-main). `MoreScreen` takes the account panel as a slot instead of a state object, since its only state is the account's own.

## 2026-10-30 — Field Log More and job house start move into ViewModels
- FL-More gets a MoreViewModel fed by `MoreDeps` (lambdas for storage and Free up, the outbox count) so the composables only render and route; the storage total is recounted each time More is shown, as before, because downloads on other screens change it (cr:fl-logic-in-ui).
- The geocode after saving a job runs on the AppContainer's own scope (`geocodePending`), not a composable scope, so it finishes even if the person leaves right away.

## 2026-10-30 — Today, Capture and Photo take one event callback
- `TodayScreen`, `CaptureScreen` and `PhotoDetailScreen` now follow `XScreen(state, onEvent)`: navigation, share, gallery and the map pin are events (`TodayEvent`, `CaptureEvent.Back/PickGallery`, `PhotoDetailEvent.Share/Annotate/Back`) that the route answers; the ViewModels ignore them. Capture keeps its `camera` slot, which draws the live preview rather than reporting a tap (cr:fl-screen-signature).
- Today's destinations are one `TodayNavigation` object built in AppTabs and shared by the route and the signed-out fallback, and `todayEvent()` handles events for both, so the map-pin intent left the composable.

## 2026-10-30 — Edit punch is a full-screen form with a time picker
- FL-Timesheets' Edit punch was an `AlertDialog` with three inputs; ui-standards rule 15 puts any create/edit with more than two fields on a full-screen route with Close and Save, so it now draws through `FormScaffold` in place of the week list while `TimesheetsUiState.edit` is set (no new Nav3 key: the form belongs to the week the owner is looking at, and its state already lives in TimesheetsViewModel). Close or Back asks "Discard changes?" once the time, job or reason changed (rule 3).
- The time is picked with Material's `TimePicker` in `TimePickerDialog` through a new `:core-ui` `TimeField` (rule 22), 12- or 24-hour from the device setting. The free-text parser `TimeText` and its "Enter a time like 7:30 am" error are gone with the text field.
- The open form (punch, time, job, reason) is kept in SavedStateHandle and reopened once the week reloads after process death.

## 2026-10-30 — Build config lives in build-logic convention plugins
- `build-logic/` is an included build (`pluginManagement { includeBuild("build-logic") }`) with four plugins: `homerecord.android.library` (compileSdk 37, minSdk 26, Java 17, Android resources in unit tests, JUnit/Truth/Robolectric/androidx.test core/coroutines-test), `homerecord.android.application` (the same plus targetSdk 37, version, instrumentation runner and androidTest deps, `GOOGLE_WEB_CLIENT_ID`, `API_BASE_URL` per build type, release with R8 and the local.properties signing key, the QA-only `benchmark` build type and its debug source dirs), `homerecord.android.compose` and `homerecord.android.screenshot`. Module scripts keep only namespace, applicationId, their own fields and dependencies (cr:build-convention).
- Kotlin classes compiled with `kotlin-dsl`, AGP's DSL from `com.android.tools.build:gradle-api` (compileOnly, same version as AGP; part of AGP, so no new approved dependency). The root keeps `apply false` for every plugin the conventions apply by id so they load in one classloader.
- build-logic's plugin repositories put `gradlePluginPortal()` before `mavenCentral()`: kotlin-dsl's artifacts are only on the portal and the cloud proxy's 429 from Maven Central otherwise fails resolution before the portal is tried.
- `house-manual` adds `REVENUECAT_API_KEY` to `release` and `benchmark` by name: `benchmark` copies `release` when the convention creates it, before the module script runs.

## 2026-10-30 — Item type names and seeded upkeep text from string resources
- :core's `ItemCategory` catalog keeps only ids, icons, intervals and text keys (`ConsumableDefault.specKey`, `ReminderDefault.titleKey`); house-model maps them to `item_subtype_*`, `consumable_default_*` and `reminder_default_*` strings (`ItemSubtypeLabels.kt`, an explicit map rather than `getIdentifier`, so R8 resource shrinking keeps them and lint stays quiet).
- Code that needs the text outside Compose takes a resolver: `ItemRepository(db, strings: (Int) -> String)` (the app passes `Resources::getString`), which seeds the consumables and reminders and names new items; `SearchRepository` and `SearchIndexBuilder` take `subtypeName`; `SubtypeSearch.matching` takes `subtypeName`. Tests resolve through Robolectric resources, or a stand-in lambda for pure ones.
- Seeded rows store the resolved text (consumable spec, reminder title, item name): they are the homeowner's data from then on, editable, and not re-translated.

## 2026-10-30 — Jank gate on a software renderer measures the UI thread
- The first measured jank stage (6a0e6db4) reported 94% (House Manual Items) and 84% (Field Log Jobs) janky frames. Every janky frame was "Slow issue draw commands" (96/96, 93/93), the GPU histogram was empty, and the fastest frame took 14–21 ms: the CI emulator runs `-gpu swiftshader_indirect`, so the CPU rasterises each 1080×2400 frame and no app can make the 16.7 ms deadline there. The baseline profile was installed and compiled (`speed-profile`, result=1).
- `device-suite.sh` now also saves `dumpsys gfxinfo <pkg> framestats`; when SurfaceFlinger's GLES line or `ro.hardware.egl` says SwiftShader, the gate is the share of frames whose UI-thread work (HandleInputStart → SyncQueued: input, animation, measure/layout, draw recording) runs past the frame interval, still ≤ 10% (`qa/scripts/framestats.py`, flagged frames excluded as Macrobenchmark does). The overall gfxinfo figure stays in the stage detail and `jank-<app>.json`. On a hardware GPU the overall figure is still the gate.
- Items and Jobs rows and headers declare `contentType` so the lazy lists reuse row compositions.

## 2026-10-31 — PDF markup writes rasterized pages with vector marks (T042b, AC-F21)
- The platform can read PDFs (PdfRenderer) and write them (PdfDocument) but cannot copy PDF content, and no PDF library is approved. A marked-up version therefore draws each source page as an image (up to 2× its point size, capped at 12 MP so a 24×36 sheet fits in memory) into a page of the same size, then draws the marks as vectors on top. The original file is never touched; text in a marked-up version is no longer selectable.
- Mark colours are fixed ARGB constants in `:core` `PdfMarkColors` (red ink, see-through yellow highlight), shared by the editor and the writer so the saved version matches what was drawn and prints the same in dark mode; like the layer palette, they are not theme colours.
- Mark up is offered to those who manage the job's files (Lead on their jobs, Office, Owner; `RoleGates.canManageFiles`): SPEC gives Crew "view files". After Save the editor and the old viewer close and the new version opens.
- The Field Log `regression` debug seed now adds a phone-local job "Garage subpanel" with a two-sheet plan PDF (`PlanFileSeed`) so flow F07c can mark up a file without the system picker.

## 2026-10-31 — Weather proxy and daily log draft (T043a, T043b; AC-B32, AC-F30)
- `GET /weather` uses Open-Meteo's forecast API for days from 80 days ago to 15 days ahead and the archive API for older days (the forecast API keeps only ~92 past days; the archive lags a few days, so recent days must come from the forecast). Units are Fahrenheit and inches (US contractors); the answer carries a condition key (`clear`, `rain`, …) from the WMO code, not words, so the wording stays in the apps' string resources. The cache key rounds lat/lng to 2 decimals (~1 km) so a crew on one job shares one upstream call. Days past the forecast answer 404; Open-Meteo failures answer 502 and are not cached. Free and keyless, so it takes no lookup quota.
- `DailyLogSections` is the `daily_log.sectionsJson` shape: assembled sections hold structured values (crew lines with minutes, work-done lines, photo ids, task, change-order and material lines, weather) wrapped in `LogSection(value, edited)`; editing a section stores the edited value with `edited = true`, and `LogAssembler` keeps such sections, the delays and the typed-only sections (Visitors, Safety, Notes for client) as they are. Structured values (not prose) keep the PDF and the screen localizable and let a person correct one crew member's hours without retyping the section.
- Crew hours count only the job's segments inside the day (a shift across midnight splits between days); a running shift counts until now and is marked on site. Weather is kept from the saved draft when a fetch fails (offline), which is AC-F31's "cached".

## 2026-10-31 — Daily log repository and weather client (T043c; AC-F30, AC-F31)
- A job's log for a day has a fixed id, `UUID.nameUUIDFromBytes("daily_log:<jobId>:<day>")` (a version 3 UUID, still a UUID string): two phones that open the same day offline write the same row, and sync merges it instead of leaving two logs. Rows with other ids (none yet) are read as the oldest of that day.
- A task has no "done at" column; the log treats a done task's `updatedAt` as the time it was ticked (SPEC's Task has no completion time). T044 keeps that true by not touching done tasks except to untick them.
- The weather snapshot lives on the row (inside `sectionsJson`, mirrored to `weatherJson`). It is looked up when the row has none, or when the one kept is 6 h old (the backend cache) and was taken before the day ended; one taken after the day ended is final. A failed lookup keeps the old snapshot; with none, the screen says "No weather (offline)". A job without map coordinates gets no lookup, and a day past the forecast range (backend 404) is "not yet", so neither is called offline.
- A re-assembly that changes nothing writes and queues nothing, so opening the Log tab does not churn sync.

## 2026-10-31 — FL-Log screen (T043d1; AC-F30, AC-F31, AC-F32)
- Who edits the log follows the server rule (`backend/src/sync/policy.ts`, `daily_log` is a Lead job row): Owner and Office on every job, a Lead on the jobs they are on. Crew and Sub read the saved log and never build a draft, since their phone could not sync it. Listed under Decisions to confirm.
- Opening a day assembles and saves the draft (no "Build log" button): the log is meant to write itself (PRODUCT step 6). Future days cannot be picked.
- Edits follow rule 15: typed sections are a one-field sheet, a crew line a one-field dialog (decimal hours, `7:30` and `7h 30m` also read), a delay a two-field sheet with the five reasons as a radio list (more than four options, rule 22). Work done is edited as paragraphs, one entry each.
- Editing one person's hours fixes the whole crew section as typed and clears "on site now", so a frozen list never claims someone is still on site.
- `DailyLogRepository` now runs every read-change-write of a log row under one mutex, and assembly re-reads the row under it after the weather lookup: two quick edits, or an edit made while the weather is still being fetched, both land instead of the later write undoing the earlier.

## 2026-10-31 T043d2: leaving lines out of a daily log, correcting the weather

- Photos, tasks, change orders and materials are left out one line at a time (confirm dialog, rule 16) and recorded as ids per kind in `DailyLogSections.dropped`, not by marking the section edited: a photo left out stays out on every re-assembly while photos taken later that day still arrive. "Put back N left out" under the section clears that kind and rebuilds the day. The record itself never leaves the job.
- The weather is corrected on a full-screen route (`LogWeatherKey`, FormScaffold with Close/Save, rule 15: conditions plus three numbers). Conditions are a radio list of the ten backend keys (rule 22, more than four), high/low whole °F from −80 to 140 (decimals round), rain or snow 0 to 40 in, blanks allowed. A typed weather is marked edited and never fetched again; the log shows "Typed in by hand". "Look it up again" clears the mark, sets fetchedAt to 0 and rebuilds the day, so the proxy is asked at once when online; offline the typed values stay shown until a lookup answers.

## 2026-10-31 FIX F07c: a straight stroke with Box or Highlight draws a thin strip

- `PdfMarkSketch` used to drop any box or highlight with zero width or height, so a highlighter run along a line of text (the natural way to highlight) and any perfectly straight drag drew nothing and left Save disabled; Maestro's straight swipes in F07c hit exactly that. Now a drag shorter than `MIN_EXTENT` (2% of the page) both ways is a tap and draws nothing, and a straight stroke keeps its length and gets `MIN_EXTENT` thickness centred on it, kept on the page. The live preview uses the same rule, so what is drawn is what saves.

## 2026-10-31 T043e: delays as a job timeline in FL-Reports

- "Job timeline" (AC-F32) is read as a Delays section in FL-Reports for the same week range as the hours: one block per job ("Hale kitchen · 2 days delayed"), most delayed job first, then each delay oldest first with its reason icon, the day and the note. The pure rule is `DelayTimeline` in `:core` model with tests; the logs come from a new `DailyLogDao.observeBetween` query (no schema change) through `DailyLogRepository.observeRange`, which keeps one row per job and day the same way `observe` does.
- A delay row opens the job on its Log tab at that day: `JobKey` gained optional `tab` and `day`, `JobRoute` takes `initialTab`/`initialLogDay`, and `LogDeps.initialDay` sets the first day shown (never past today; a day picked later wins and survives process death).
- The Delays section sits under the exports so the CSV buttons stay next to the hours they export.

## 2026-10-31 T044a: task order and done time
- Task gains `sortOrder` (drag to reorder, FL-Tasks) and `doneAt` (Tasks completed in the daily log) in Room v5 by AutoMigration; tasks done before v5 take their last change as the done time, which is what the log used until now. Rows synced from an older app with `doneAt` missing fall back the same way.
- Reorder renumbers the open list 0..n-1 and writes only rows whose number changes, so a drag syncs the fewest rows; ties from two phones adding at once are settled by created time, then id.
- "My tasks" (AC-F34) is open tasks assigned to me on the current company's jobs that are overdue or due today; overdue first by due day, then today's in the crew's order. Tasks with no due day stay on the job's Tasks tab only.
- The punch list is a filter on a job's tasks (All / Punch list), not a separate list, per FL-Tasks.

## 2026-10-31 T044b1: Tasks tab and task form
- The tasks flow is `F11-tasks.yaml`, named for walkthrough step F11 (Tasks) in qa/product-walkthrough.md; PLAN T044b said "F10", which is the FL-House step.
- Ticking the check box completes a task with the "Done: … / Undo" snackbar now; swipe to complete and drag to reorder come in T044b2 with `sh.calvin.reorderable` (already approved).
- Only Owner, Office and the job's Lead (`RoleGates.canManageTasks`, same as backend policy.ts) see Add task and open the form; Crew see only tasks assigned to them, read the who/when/room/note line on the row, and tick them.
- A task's photo is picked from the job's photos (three-across sheet) rather than taking a new one, so it stays one of the job's photos and syncs with them; taking a photo for a task is the Photos tab's capture.
- The assignee picker lists the company's members with the job's crew first.

## 2026-10-31 T044b2: swipe and drag on FL-Tasks
- `sh.calvin.reorderable:reorderable` 3.1.0 (approved in CLAUDE.md; latest stable on the Maven Central mirror, published April 2026) is back in the catalog for Field Log only.
- Swiping an open task either way marks it done (same Undo snackbar as the check box); done tasks and tasks this person cannot tick do not swipe.
- Managers (Owner, Office, the job's Lead) drag an open task by its trailing handle; the handle replaces the chevron on open rows, the row still opens on tap. The new order is saved once, when the drag ends (`JobTasks.move`, only moved rows sync). No reordering on the Punch list filter, since it shows only part of the list. TalkBack gets Move up / Move down custom actions on each row.

## 2026-10-31 — T044c My tasks on Today
- Today lists my open tasks overdue (oldest due day first) then due today, under "My tasks" after "My jobs today"; no section when none are due; archived jobs' tasks are left out.
- Check box or a swipe either way ticks a task done with the same "Done: … · Undo" snackbar as the Tasks tab; the swipe is one shared `SwipeToComplete` in the tasks package used by both screens.
- A tap opens the job on its Tasks tab, plus the task form on top for those who manage the job's tasks (RoleGates.canManageTasks); Crew can't edit tasks, so they get the tab.
- `RecordListItem` gains `headlineMaxLines` (default 2, nothing else changes); task rows on Today and the Tasks tab allow 6 so long titles don't clip at 200% (rule 32).

## 2026-10-31 — T044d1: task pins on the floor plan (AC-F35)

- The pin is `TaskPlanPin(floorId, xIn, yIn)` stored as JSON in the existing task column `planPinJson` (no schema change; it syncs with the row). An unreadable value reads as no pin, so a bad pin never breaks the task.
- Pinning lives in the task form ("Pin on plan" field, shown only when the job's house has rooms) and opens a plan picker over the form, one floor at a time; a tap drops or moves the pin. The pin is part of the typed form, so it is kept by Save and dropped by Close → Discard like any other field.
- Dropping a pin in a room fills the task's Room when it is empty; it never replaces a room already chosen.
- FL-House draws open tasks' pins as numbered map pins (tertiary color, fixed size so the number does not grow with font scale; TalkBack reads "Task n: title"). Managers (Owner, Office, the job's Lead) see every pin and a tap opens the task form; Crew see only their own tasks' pins, as on the Tasks tab, and a tap switches to the Tasks tab. Done tasks' pins are hidden.
- The file-page pin (task `fileId`/`page`/`x`/`y`) is split out as T044d2.

## 2026-10-31 T044d2: task pins on a file page (AC-F35)

- The file pin is `TaskFilePin(fileId, page, x, y)` in the task's existing `fileId`/`page`/`x`/`y` columns (no schema change; the backend syncs the whole row). `page` is 0-based and `x`/`y` are fractions of the page, so a pin stays put at any zoom or render width. Missing or out-of-range columns read as no pin.
- A task can carry both pins: the floor plan pin and one file page pin. They answer different questions (where in the house, where on the drawing).
- Pinning starts in the file viewer, not the task form: "Pin a task" (Owner, Office, the job's Lead) turns on pin mode, a tap on a page opens "Pin which task?" with New task here and the job's open tasks. Choosing an existing task moves its file pin; New task here opens the task form with the spot filled in (counted as typed, so Close asks first).
- The task form shows "Pinned on a file: <file> · Page n" with Remove. Saving the form writes the file pin only when the form changed it, so a pin dropped from the viewer while the form was open elsewhere is kept.
- Pins follow the plan across versions: the viewer shows pins on any version of the file (marking up saves a new version with a new id). Pins on a page the open version lacks are left off.
- Pins are drawn as Compose nodes over the page (same map pin as FL-House, fixed number size, 48 dp head, TalkBack "Task n: title"); a tap opens the task form for managers or the job's Tasks tab for Crew, who see only their own pins. The shared `PdfViewer` gained `onPageTap` and `pageOverlay`; House Manual's document viewer passes neither and is unchanged.

## 2026-10-31 — Today's camera button picks the job without asking when it can (T035c3)
SPEC puts a capture FAB on Today but capture is always tagged to a job. Order: the job I'm clocked in on, then my only job today, then the company's only live job; otherwise a "Photo for which job?" sheet (my jobs today first). Reason: a crew member on site should get one tap to the camera; asking only when there is a real choice. Recent photos are company-wide (crew can see every job in Jobs too), the 12 newest on non-archived jobs.

## 2026-11-01 — Daily log PDF: pure layout in :core, drawn with PdfDocument; empty sections left out (T045a)
The log PDF is laid out by `LogPdfLayout` in :core (pages of text, image and rule steps, measured through an interface) and drawn by `AndroidLogPdfWriter` with the platform `PdfDocument`, so pagination, wrapping and the photo grid are unit tested without a device. US Letter, sans serif, photos three across cropped 4:3, logo fitted in a 56 pt square, "Page n of m" footer. Sections with nothing in them are left out of the PDF (the client reads what happened, not a list of "None"); the Log tab still shows them. Email recipients are the client's address then the job's office addresses, valid and unique. Sending marks the row `SENT` with time, recipients and the PDF path; a re-send replaces them (the log shows its latest send). Reason: AC-F33; no new dependency.

## 2026-11-01 — Log Send: marked Sent when the email app or share sheet opens; email through a mailto selector (T045b)
Android cannot tell an app whether the user actually pressed Send in Gmail or picked a target in the share sheet, so the log is marked Sent (time, recipients, PDF path) as soon as an app took the intent; Send again stays on offer and replaces the last send. Email goes as `ACTION_SEND` with the PDF attached and a `mailto:` selector so only email apps are offered; a phone with no email app falls back to the share sheet. A job with no client or office email gets one action, Share PDF. Preview PDF builds the same PDF and opens it in the shared `PdfViewer` on its own screen (`LogPdfKey`), so what the lead previews is what the client gets. Crew and Sub can preview a saved log but not send it (AC-F3).

## 2026-11-01 — Conflict log is device-local and lives under More > Sync (T046a)
The server already logs every conflict (`sync_conflicts`, AC-B20) and returns them per push; until now the app dropped them. Each pass's conflicts are now kept in a device-local `sync_conflict` table (Room v6, never synced, wiped on sign-out with the rest), newest 200 rows, with the record's name copied in so a line still reads after the record is gone. Bookkeeping columns (`updatedAt`, `createdAt`) are not logged. FL-More has no Settings screen yet, so the viewer is a "Sync" row on More (every role, since crew phones lose conflicts too) opening FL-Sync, which also shows the pending count and Sync now. House Manual keeps no log for now (no viewer there).

## 2026-11-01 — Field Log's Today sync chip opens FL-Sync (T046b)
T046 asks for one place that explains sync. On Field Log the Today chip now always opens FL-Sync (status, pending count, Sync now, conflict log, which already lists refused changes) instead of syncing in place or popping the refused-changes dialog; Clear log there also clears the refused list the chip counts. House Manual keeps the shared `SyncStatus` (sync now, refused dialog) until it gets a sync screen. Known edge: refused changes from before T046a have no log row, so the chip can count them while FL-Sync shows "No conflicts" with no Clear button; they clear on sign-out.

## 2026-11-01 — Push: data-only FCM messages from the sync push, phone writes the words (T047a)
The Worker decides who hears what from the row before and after each accepted sync op (`pushEventsFor`), so no app has to call a separate "notify" endpoint and a change made offline still notifies once it syncs; deduped retries notify nothing. Messages are data-only (type, ids, job name, task or change order title): the phone builds the notification from strings.xml, keeping every user-facing word in the apps (voice.md) and letting it pick the channel and deep link. FCM HTTP v1 is called with `fetch` and an RS256 service-account JWT signed with jose (already approved), so no Google SDK runs in the Worker. Delivery runs after the push answers (`waitUntil`). Every notification is written to `push_log` (30 days) whether or not `FCM_SERVICE_ACCOUNT` is set, and `/dev/pushes` (dev only) lists them so flows can check a push went out on the local backend without Firebase. "Schedule changed for tomorrow" also covers today, read in the company's `settings.timeZone` (Eastern until the app sets it). Change order signed goes to Owner and Office.


## 2026-11-01 — Field Log push: Firebase from BuildConfig, registrar in :core (T047b1)
`com.google.firebase:firebase-messaging` 26.0.1 (approved; latest stable on Google Maven) is the only Firebase artifact; it is set up with `FirebaseApp.initializeApp(options)` from `firebase.*` local properties or FIREBASE_* env vars baked into BuildConfig, so neither the google-services plugin nor a committed json is needed, and a build without the values simply has no push. The manifest turns off BigQuery delivery-metric export and analytics collection. `PushRegistrar` in `:core` (pure, fakes in tests) keeps the backend's token in step: register on sign in, on token refresh and after each reconnect until it succeeds (`pushSessions`), delete before an explicit sign out while the session still answers; the last registration is kept in plain prefs `push` (the token is not a secret). Notifications use androidx.core `NotificationCompat` (already on the classpath through activity) on four channels; the notification id is the message key's hash, so a second push about the same task, day, change order or week replaces the first. The FCM Task is awaited with a listener, since kotlinx-coroutines-play-services is not approved.

## 2026-11-01 — Notification taps and the permission ask (T047b2)
A notification tap reaches MainActivity with the push data as extras (`PushIntents`); `PushDestination` picks a tab and
the screens to open on it, and the shell's `TabRequests` now carries those keys (the tab's stack is cut to its root and
the keys pushed, so back returns to the tab root). Task: the job's Tasks tab, plus the task form for whoever manages the
job's tasks (same as a task row on Today). Schedule: the Schedule tab until T059 builds FL-Schedule (T059a opens the
day). Change order: the job's Changes tab. Timesheet approved: Timesheets on that week for Owner/Office (`TimesheetsKey`
gained `week`), the Clock tab (own week) for everyone else, since Timesheets is Owner/Office only (AC-F3).
The POST_NOTIFICATIONS ask comes the first time the person reaches the company's tabs, once per install, and only when
the build has Firebase config: without it no notification can ever arrive, and an ask would also put a system dialog in
front of every Maestro flow on the runner.

## 2026-11-01 — The daily log PDF's header comes from the membership when there is no local company row (judge:fl-log-pdf-company-fallback)

The company profile lives on the Worker (`companies`), and nothing writes the device's Room `company` table, so for a
company made on the welcome screen `LogPdfMaker` found no row and printed the app name. Rather than add a new sync
kind, `/me` now returns the company's `phone` for every role and its `email` for Owner and Office (the AC-B3 column
split), `CompanyMembership` and its prefs cache keep them, and the pure `LogPdfCompany.of` fills the PDF's company from
the membership (only the job's own company; a non-blank local row still wins). The logo stays local-only until the
company logo upload is built. A Lead's PDF therefore carries the phone but not the email.

## 2026-11-01 Empty daily log warns before Send

QA judge [judge:fl-log-send-empty]: Send on a day with nothing logged now opens "Send an empty log?" (Cancel / Send
anyway) before the Send sheet, rather than disabling Send. A contractor may still want to send a "no work today" page,
so Send stays available, but a blank page never goes to a client by accident. "Nothing logged" means no crew time, work
done, photos, tasks, change orders, materials, delays, visitors, safety or client notes; weather alone does not count.

## 2026-11-01 — Daily log sections sync one column each (R09a)

`daily_log.sectionsJson` held every section in one value, and sync merges a row field by field, last writer wins. An
office phone with a running shift's day open re-assembles crew hours and saves the whole value, so a Lead's Visitors or
delays typed offline were lost when the office phone's save was newer. Each section now has its own column (Room v7,
`crewJson` … `clientNotes`, mapped by the pure `DailyLogColumns`); the phone pushes only the columns that changed, so two
phones editing different sections both keep their edits. Two phones changing the same section still resolve last
writer wins, as every other field does. The old `sectionsJson` stays as a read fallback for a column still null (logs
saved before v7); it is no longer written. No backend change: the Worker stores rows as JSON and merges any field.
Chosen over a per-section merge rule inside one JSON value, which would need matching special cases in the Worker and
`RowMerge`.

## 2026-11-01 — Weather snapshots carry the Worker's fetch time; empty days are not "offline" (R09c)

`GET /weather` now returns `fetchedAt` (when the Worker fetched the day from Open-Meteo; a KV cache hit keeps it) and,
when Open-Meteo answers with no values for the day, `200 {date, empty: true, fetchedAt}` instead of a 502. The app
stamps the snapshot with that time (falling back to the phone's clock for an older Worker), so a cached evening forecast
handed out after midnight is not treated as the day's final weather; an empty day shows "No weather recorded." through
the new `WeatherStatus.NoData`. A thrown upstream fetch is a 502 like a failed one. Empty answers are cached for the
same 6 h so a cell with no data does not call Open-Meteo on every open.

## 2026-11-01 — Log tab day rollover, delay ids, "Changed since sent", notes by filed day (R09d)

- "Today" on the Log tab is a flow: `Midnight.ticks()` (in `:core`) fires a second after each local midnight and the
  route re-checks on resume (a sleeping phone's wait does not count deep sleep). Someone looking at today follows it to
  the new day unless a sheet is open; a past day stays put and Next reaches the new day.
- `LogDelay` gains an `id` (UUID on new delays); Remove targets `LogDelay.key` (the id, or reason and note for delays
  saved before ids), so a delay synced in from another phone while the dialog is open cannot shift the target.
- "Changed since sent" is `sent && updatedAt > sentAt`: any save after the send (hand edit, re-assembly that changed a
  column, a new weather snapshot) means the client's PDF is out of date. No schema change.
- The assembler files a note by `NoteEntity.day` (the day the writing phone filed it under) and only falls back to its
  `createdAt` in the viewer's zone when no day is known.

## 2026-11-01 — R09f: task form from a push, company time zone

- A task form opened for a task that is not on the phone yet (a task-assigned push tapped before the next sync) shows
  its error and watches the job's tasks and the edit right (`canManage`); once both are there it loads by itself. A task
  that lands on another job keeps the error.
- `settings.timeZone` (IANA name, checked with `Intl.DateTimeFormat`, unknown names refused with 400) is accepted on
  `PATCH /companies/:id/profile` and merged like the other settings; unset still means Eastern. It is not added to
  `DEFAULT_SETTINGS`, so the profile and `/me` shapes stay as they are until it is set. No phone screen sets it yet;
  FL-Company gains the picker next (R09f1).
- "Schedule push opens the day" stays with T059a, since FL-Schedule does not exist yet.

## 2026-11-01 R09f1: company time zone picker

- FL-Company shows a "Time zone" row (after the address) that opens a bottom-sheet list (ui-standards rule 22, more
  than four options): Eastern, Central, Mountain, Arizona (no daylight saving), Pacific, Alaska, Hawaii, as the IANA
  zones `America/New_York` ... `Pacific/Honolulu`. US only because the product is for US contractors; a zone set some
  other way through the API is kept and listed last under its IANA name.
- A company with no zone shows Eastern (the backend default) and saves nothing for it, so the backend keeps its
  default until someone picks one. Picking Eastern stores `America/New_York` explicitly.
- `CompanySettings.timeZone` is cached with the membership so a later phone screen can read it offline.

## 2026-11-02 T048a: wall photos and textures

- One photo per wall per stage. Photographing a wall again at the same stage replaces the earlier photo (soft delete, both
  rows to the outbox): the room page and the 3D view show one picture per stage, and a retake is how a homeowner fixes a
  bad shot. When two phones photograph the same stage before a sync, the later `takenAt` wins. The replaced photo's files
  stay on disk for now; media clean-up is a separate concern.
- Outline corners are stored 0..1 in the upright photo (EXIF orientation applied), top-left, top-right, bottom-right,
  bottom-left, in `wall_photo.cornersJson`. "Use" needs a convex outline covering at least 1% of the photo.
- Texture size: the wall's length × height aspect (the outline's own when the wall has no size), the long side as long as
  the outline's longer edges in the photo (no upscaling), capped at 2048 px; JPEG quality 88. Sources over 16 MP are
  decoded at a power-of-two sample first.

## 2026-11-02 T048b: Walls section on the room page

- Walls list in compass order (North, East, South, West, then angled walls), plan order within a side; a side with two or
  more walls numbers them ("North wall 1", "North wall 2"), a lone wall has no number. The plan's top edge is "North"
  (WallDeriver's convention); the app does not know true north.
- The stage picker is a four-option segmented row (rule 22) without the check icon, each label one line that steps its
  size down at large text rather than splitting mid-word. House Manual opens on Finished, Field Log's room page on
  Rough-in (its "behind the wall" stage, AC-C17); the choice survives process death (SavedStateHandle).
- Each wall row shows the selected stage's picture (the straightened texture when there is one, else the photo as taken)
  and which stages have photos in words. Photograph/Retake is drawn only when the app can capture (`canPhotograph`), so
  it stays off until T048c wires the camera; at ≥ 150% font the button moves under the row's text.
- New DAO query `WallPhotoDao.observeForRoom` (join on live walls); no schema change.

## 2026-11-02 T048c: Photograph this wall and the outline screen

- "Photograph" on a wall opens a sheet (Take photo / Choose from gallery, the same words as the item form); the camera
  writes into `filesDir/media` through `MediaFiles`, the picker's photo is copied there. The wall, the stage shown on the
  room page and the camera file wait in the room's `SavedStateHandle`, so a result after rotation or process death still
  opens the outline. Field Log's room page now gets a `MediaFiles` too (the same `AndroidMediaFiles`).
- HM-WallOutline is one shared screen in `:house-model` (`WallOutlineKey` in House Manual, `JobWallOutlineKey` in Field
  Log, both full screen without the tab bar). One pointer handler on the photo picks the nearest handle within 48 dp;
  the handles are separate 48 dp nodes with tags and TalkBack actions (move left/right/up/down by 1%). The loupe (3x)
  draws the same painter, at the top corner away from the finger. The dashed guide is the wall's length × height
  centered in 80% of the photo (`OutlineGuide`), drawn white dashes over a dark line so it reads on any wall colour.
- Use refuses crossed or tiny outlines (`WallOutline.isUsable`) with a message; otherwise `WallTextureMaker`
  (`AndroidWallTextureMaker`: `WallTextureBuilder` on Dispatchers.Default) writes `<photo>-wall.jpg` beside the photo
  and `WallPhotoRepository.save` files it at the stage. Back discards the new photo; a texture failure keeps the
  screen with the reason.
- Wall photo files do not upload yet (new subtask T048d); the rows sync, the pictures stay on the phone that took them.

## 2026-11-02 T048d: wall photo files to the cloud

- `wall_photo` gets two synced columns, `r2Key` (photo as taken) and `textureR2Key` (straightened copy); Room v8,
  AutoMigration 7→8. The paths stay device-local (`*Path`), so a pulled row arrives with empty paths.
- R2 names are fixed per photo: `<tenant>/wall_photo/<id>/original.<ext>` and `.../texture.jpg` (`WallPhotoFiles`),
  so the upload queue stays idempotent per file and `UploadedKeys` knows which column a finished upload fills.
- Downloads: the room page's photo flow passes rows on first, then fetches the missing files of the photos it shows
  (newest per wall and stage, texture first) through `MediaDownloads`/`MediaCache`; a fetched file sets the local
  path without a sync, the flow emits again and the picture appears. A failed fetch keeps the plain tile.
- Backend: `wall_photo` joins the job house entities a company tenant syncs, and counts as a capture (Crew and Sub
  may push and upload it), because the behind-the-wall photo is a crew job (AC-F49). Orphan cleanup already keys on
  the row, so both files live as long as the row.
- House Manual passes no upload/fetch hooks yet: it has no media upload path until its cloud sync (T075a).

## 2026-11-02 T049a: 3D mesh from the plan

- `PlanMeshBuilder` (`:house-model` `scene`) is pure Kotlin with no SceneView types, so the geometry is unit-tested
  on the JVM and the renderer (T049b) only uploads float/int arrays into Filament vertex and index buffers.
- World frame: meters, y up, plan x → x, plan y → z, footprint centered on the origin; a camera looking down with
  -z as screen-up sees the blueprint unmirrored. Floors take the floor record's `ceilingHeightIn` and `elevationIn`.
- Walls are 4.5" thick (2×4 with drywall). Each room draws its own inside face set in half a thickness, so a
  shared wall is two faces 4.5" apart with no z-fighting, and each face is a separate part keyed by
  (room, edge) for its stage photo. Exterior edges also get the outside face; tops and jambs make walls read solid.
- Wall-face UVs run u along the wall from its first corner and v from the top down, the orientation of the
  straightened wall photo bitmap (`WallTextureBuilder`), so the texture maps with no flipping in our coordinates.
- Windows have no sill height in the plan model: their head sits at 80" (a 6'8" door head) and they hang down by
  their height; any opening stops 4" below the ceiling. A door drawn on one room's side of a shared wall opens the
  neighbour's face too.
- Floors are ear-clipped (own `PolygonTriangulator`, no library) so L-shaped and other concave rooms work.

## 2026-11-02 T049b: HM-3D on SceneView

- `io.github.sceneview:sceneview` **4.60.0** (approved library; the release current on 2026-10-19, built on Kotlin 2.4.21
  and Compose BOM 2026.09.00 like the project; Filament 1.72.1 inside). The tree has no `arsceneview` and no
  `filamat-android`: we use SceneView's bundled precompiled `opaque_colored`, `transparent_colored` and
  `opaque_textured` materials. It merges `INTERNET`, `VIBRATE` (normal permissions) and a required
  `glEsVersion 3.0` feature into House Manual (and Field Log once T049c uses it); record them in the data safety pass (S10).
- One `MeshNode` per `MeshPart` (built through SceneView's `Geometry.Builder`, which turns normals into the tangent
  quaternions lit materials need). Material instances and textures are cached per engine and released together;
  buffers are released in a `DisposableEffect` declared before `SceneView` so Compose forgets the nodes first.
  T050 owns the performance pass (merging parts, culling) for AC-C24.
- Wall pictures load off the main thread, downscaled to 1024 px on the long side, straightened copy first.
- Untextured faces use a paint-like tint per room-kind group (`RoomTint`, `RoomPalette` beside the layer palette):
  `Room.color` exists in the schema but nothing sets it yet, and kinds keep every kitchen alike.
- Reset view builds a new camera manipulator at the start pose (south of the floor, turned 30°, looking down 40°).
- Filament cannot run in screenshot tests, Robolectric or the screen catalog, so `House3DScreen` takes a scene slot:
  the route passes the live `FloorSceneView`, previews and the catalog pass `FloorScenePicture`, a flat-shaded
  painter's-algorithm still of the same mesh from the same start pose.
- The stage selector (AC-C20 names it) ships now with Finished first; floor chips switch floors. Stacked floors,
  ghosting and "All floors" stay with T050 (AC-C21).

## 2026-11-02 T049c: FL-House 3D

- Field Log opens the job's house in 3D through the same `House3DRoute`/`House3DViewModel` as House Manual (one
  screen in `:house-model`, `JobHouse3DKey` in Field Log), with a `defaultStage` parameter: Field Log starts on
  Rough-in, matching the job room page's behind-the-wall default (T048b), House Manual on Finished (AC-C20 wording).
  A stage the user picks still wins after process death.
- The H side of AC-C20 stays in H03 (the House tab already opens 3D there); the Field Log side is the new
  `F10-house-3d.yaml`, the first part of walkthrough F10 (capture, overlays and the installed item come later).

## 2026-11-02 — T050a: floors stacking in 3D (AC-C21)

- One floor chosen shows every floor whose elevation is lower, faded (a single see-through grey, no photos, no
  glass); floors above are left out so they never hide the chosen one. A floor at the same elevation is not "below".
- All floors draws every floor solid, bottom first, each raised by a 60 in gap and never below the top of the floor
  under it, so floors with missing or overlapping elevations still separate.
- Floors are stacked by translating each floor's own mesh onto one shared plan origin (the middle of the combined
  footprint) instead of changing `PlanMeshBuilder` or `MeshMaker`; the per-floor build stays the unit under test.
- The camera frames the solid floors only: the chosen floor's floor height, or the middle of the stack for All floors.
- An empty chosen floor shows the empty state (Edit plan) even when floors below have rooms.
- T050 is split: T050a (stacking, done) and T050b (performance pass, AC-C24).

## 2026-11-02 — T050b: 3D performance pass (AC-C24)

- Draw calls: `SceneBatcher` merges the scene's parts into one renderable per room and material (room tint for the
  shell and unphotographed walls, floor, glass, ghost, and one per wall picture), off the main thread. Batching per
  room rather than per floor keeps Filament's per-renderable frustum culling useful (each batch has its own bounding
  box), which matters for the inside-the-room view in T051; the reference home's All floors view draws 53
  renderables instead of 89 (14 rooms, 56 walls, 490 triangles: the cost is draw calls and texture memory, not
  geometry). Filament culls against bounding boxes by default, so no extra culling code.
- A photographed wall keeps its own batch and is drawn in the room tint until its texture loads, so batches do not
  change when textures arrive (no geometry rebuild).
- Texture memory: `TextureBudget` sizes every wall picture so all the pictures the scene holds at any stage fit 64 MB
  together (RGBA plus a third for mipmaps): 1024 px up to 11 pictures, 512 px up to 45, 256 px beyond. Decided per
  scene, not per stage, so switching stages never needs a larger size. One texture per file, shared by every face
  that shows it, as before.
- Measurement path for QA: the live view logs `FrameStats` every 2 s under logcat tag `HomeRecord3D` (first frame in
  ms from the screen opening, frames per second over the last 2 s, draws, textures and their size). The debug seed
  scenario `3d-reference` (regression home grown to ~3,000 sq ft with a garage and a bonus room, Finished photos on
  20 walls, 1600 × 1200 JPEGs drawn by the seed) is the reference home; `qa/flows/house-manual/_3d-perf.yaml` seeds
  it, opens All floors and orbits. The device suite does not run it yet (qa/scripts belongs to Nate); QA runs it by
  hand or the suite can add a stage.

## 2026-11-02 — T051: room view in 3D (AC-C22)

- Opening camera (`RoomViewpoint`, pure): eye height 66" above the room's floor, looking straight at the middle of
  the room's longest wall (the first one when two tie), standing back from it along its inward normal until 18" short
  of the first wall that normal meets (halfway across when the room is under 36" deep). Standing on that first
  stretch keeps the camera inside L- and U-shaped rooms, where the centroid can fall outside.
- Look-around (`LookAround`, pure; `LookAroundManipulator` adapts it to SceneView's `CameraManipulator`): one finger
  turns the camera in place with the picture following the finger (0.15° per pixel, up/down stops at 80°). Pinch
  "zoom" steps the camera forward or back along its heading, 1.5 m per doubling of finger spread, and refuses any
  step that would bring it within 30 cm of a wall or out of the room. A field-of-view zoom was considered; it needs
  the camera node's projection, which SceneView's orbit view would then inherit on Back to floor, so it was dropped.
- Tapping a room: each batched renderable is named by its room id and SceneView's tap listener reports the hit node,
  so a tap on any part of a room (walls, floor) opens it; ghost floors below are not rooms the camera can enter.
- "View in 3D" sits on the room page (both apps) under the size line and opens the room's floor standing inside it;
  Back from there returns to the room page, while a room entered by tapping returns to the floor on system Back.
  The still picture used for previews and screenshots shows only the chosen room, cut away.
- The shared-element transition named in the plan is left for T051a.

## 2026-11-02 T051a: shared-element transition into the 3D room view

- The shell (`HomeRecordApp`) wraps each tab's NavDisplay in one `SharedTransitionLayout` and passes it to
  NavDisplay's `sharedTransitionScope`; `LocalSharedTransitionScope` hands it to screens. `Modifier.sharedScreenBounds(key)`
  in `:core-ui` applies `sharedBounds` with NavDisplay's `LocalNavAnimatedContentScope`, and is a no-op outside the
  shell (previews, screenshot tests, catalog), so reference PNGs are unchanged.
- Container transform, not a scaled snapshot: `ResizeMode.RemeasureToBounds`, so the button's label is not stretched
  and the scene's surface is laid out at each animated size; bounds follow the theme's `MotionScheme.standard()`
  spatial spring (settles in about 350 ms, inside rule 29's 400 ms).
- Shared key `room-3d:<roomId>` (`SharedKeys.room3D`) sits on the room page's "View in 3D" button and on the 3D
  screen's body while it was opened on that room; after "Back to floor" the key is dropped, so a later Back from the
  whole floor uses the plain transition.

## 2026-11-02 T052a: overlay model and wall space

- T052 split into T052a (model, `WallSpaceMapper`, search, repository; this run), T052b (editor on a wall photo, room
  Overlays section) and T052c (overlays on the plan, search screen).
- Wall space is the straightened texture's space: x 0..1 from the wall's left end seen from inside the room, y 0..1 down
  from the ceiling line, the same orientation the texture warp uses (top-left corner of the outline is 0,0). A wall
  photo's outline maps it with the unit-square-to-quad perspective transform, written in plain Kotlin (`Homography`)
  so it is unit-testable; the texture and the plain wall tile need no mapping. Taps up to 2% outside the outline still
  count (a finger on the edge line) and clamp onto the wall; a crossed outline places nothing.
- Kinds: the AC-C25 list with Water supply split into Hot water and Cold water and Low-voltage into Data cable, Coax,
  Speaker wire, Security wire and Doorbell wire (22 kinds). Each has a layer from AC-C15; Stud, Blocking and Other
  belong to no system layer and use a neutral slate (`LayerPalette.structure`). "Valve" keeps the SPEC's word although
  voice.md prefers "shutoff": a shutoff in this app is an item with its own page, and a valve drawn on a wall need not
  be one. Unknown stored kinds read as Other and unknown depths as Behind drywall, so nothing drawn is lost.
- Search ranks label matches (prefix of the whole label first) above kind matches; kind matches also use a few plain
  search words per kind ("pipe", "ethernet", "romex").

## 2026-11-02 T052b1: the wall overlay editor

- T052b split into T052b1 (this run: HM-Wall, form, drag, delete, room Overlays section, H11 flow) and T052b2 (overlay
  photo and linked item, the Field Log F10 flow).
- HM-Wall is its own route (`RoomWallKey` in House Manual, `JobWallKey` in Field Log), opened by tapping a wall row on
  the room page or an overlay row in the room's Overlays section. It is full screen (no bottom bar) because it is a
  drawing surface and its form is a full-screen editor (rule 15).
- The overlay form is a full-screen state of the same route (`OverlayFormScreen` on `FormScaffold`), not a separate
  Navigation 3 key: the points being saved live in the wall's view model and its `SavedStateHandle`, so they survive
  rotation and process death without travelling through a nav key. System back steps out of the form, then the
  drawing, then the selection, then the wall.
- What the overlays are drawn on: the stage's straightened copy when there is one (already in wall space), else the
  photo as taken through its saved outline, else the plain wall at the wall's own length:height. Every overlay on the
  wall shows on every stage (AC-C27); the stage only picks the picture and is what a new overlay is recorded at.
- Drawing is tap per point (one tap is a spot, two or more a run), with Undo and Next, instead of a freehand stroke:
  pipes and wires run straight between turns, and taps are steadier on a phone held against a wall.
- A point drag shows live and writes once when the finger lifts.

## 2026-11-02 T052b2: overlay photo and linked item

- The overlay form's "Serves" field links one item of the house (AC-C26 "the equipment it serves"). Its sheet searches by
  item name and room (every typed word must match), lists the wall's room first, and starts with "Nothing linked".
  The list reads the item table directly through `OverlayRepository.observeItemChoices`, not `ItemRepository`, so
  both apps get it without the item repository's string and warranty dependencies.
- The overlay photo uses the same camera-or-gallery path as item photos (`MediaFiles`, `TakePicture` /
  `PickVisualMedia`), with the pending camera file in the view model's `SavedStateHandle`. A photo taken or picked in
  the form is deleted again on Close or when replaced before saving; a saved photo is only unlinked on Remove, never
  deleted before Save.
- The item page's Links section lists the overlays linked to the item (kind icon in its color, room, stage); a wall
  overlay opens its wall with it picked. The section hides while nothing is linked; breaker and shutoff links join it
  with T027c and the breaker work. Plan overlays (T052c) list but do not open yet.

## 2026-11-02 T052c2: overlays on the floor plan
- The plan editor gains a Rooms | Overlays segmented switch (both apps). Overlays mode reuses the wall editor's events
  (`PlanEditorEvent.OverlayEvent(WallOverlaysEvent)`), drawing bar, selection bar, delete dialog and full-screen form, so
  a plan overlay is drawn and edited exactly like a wall one: tap points (whole plan inches), Next, form, Save; tap to
  pick, drag a point, Edit, Delete. Undo and Redo leave the top bar in Overlays mode (they step through room edits).
- The form's logic moved out of `WallOverlaysViewModel` into `OverlayFormController` (fields, sheets, photo, process
  death), used by both view models; no behavior change on walls.
- `PlanOverlayPlacer` files a plan overlay under a room (first point's room, else nearest walls) because overlays carry
  no floor; a floor's plan overlays are those of its rooms. New overlays are recorded at Finished in House Manual and
  Rough-in on a job, matching each app's wall default.
- The blueprint (House tab in both apps) draws the floor's plan overlays when their layer is on: runs as lines in the
  layer color over a surface halo, spots as discs with the kind's icon. Wall overlays on the blueprint and 3D stay T053.
- `OverlayTarget.Room` became `OverlayTarget.Plan`: a search result or a room-page row for a plan overlay opens the plan
  editor in Overlays mode on the overlay's floor with it picked (`PlanKey.overlayId`, `JobPlanKey.overlayId`).

## 2026-11-03 R10: overlays through a plan reshape, 3D texture memory
- Wall overlays are stored in wall space (AC-C27), so a wall whose corners move (a side dragged, a length typed) keeps
  its overlays at the same place between its corners, the way the wall photo outlined corner to corner stretches with
  it. A plan edit that changes which wall runs along a stretch moves the overlays by their place along the line
  instead (`WallOverlayCarrier`): a wall split by a new corner hands each overlay to the part it sat on (a run across
  the corner goes where most of its points are and stops at the corner); two walls merged by removing the corner
  between them keep every overlay where it was on the longer wall; an overlay whose wall is gone with nothing left on
  its line (a notch removed) goes onto the floor plan at its place along the old wall. Before R10 a split stretched the
  overlays over the shorter part and a removed wall deleted them.
- A split wall's photo still stays whole on one part (R10a crops it per part).
- The 3D view releases the textures of pictures the shown scene no longer has (another floor) once their nodes are
  gone, and no longer keeps the decoded bitmaps after upload: switching floors back and forth used to pile up every
  floor's textures past the 64 MB budget sized for one scene. A rotation rebuilds the view and its engine and releases
  everything as before.

## 2026-11-03 R10a: wall photos through a split or merged wall
- A split wall's photo is cut at the new corner through the photo's perspective (`WallPhotoCarrier`, wall space to
  photo with `WallSpaceMapper.wallToPhoto`): the part that kept the wall's id keeps the photo row with the cut outline,
  the new part gets a copy of the row (same photo file and R2 key, its own outline). Each stage is cut on its own, and
  overlays (cut the same way by `WallOverlayCarrier`) stay over the same spot of the picture.
- A merged wall keeps its own photo per stage (else the one of the old wall that covered most of it), its outline
  stretched over the whole merged wall when the photo saw that far, else kept as it was (decision to confirm).
- The straightened copies of recut photos are cleared in the plan save's transaction and made again afterwards on the
  app scope (`HomeRepository.onWallPhotosRecut` → `WallPhotoRepository.remakeTextures`), each in its own file
  `<photo>-<row id prefix>-wall.jpg` so the two parts of a split wall never share one; Field Log then uploads the new
  texture as for a new photo. Until the copy is made the wall shows the photo as taken. A copied photo whose file is
  not on this phone yet keeps no straightened copy until it is photographed again (rare: a reshape on a phone that
  never downloaded the photo). Old texture files are left on disk (R10c deletes replaced media).

## 2026-11-03 Back on the wall screen (H11)

- The top bar's back arrow on HM-Wall / FL-Wall always goes up (after stepping out of an open form or drawing);
  Done unpicks an overlay. The system back unpicks an overlay picked on this screen, but leaves when the picked
  overlay is the one the wall was opened on (from the room's Overlays list, an item's Links or Home search), since
  that is what the person came to look at. Rule in pure `WallBack` (house-model). Found by the H11 flow, whose
  `hm_back` only unpicked the overlay and left it on the wall screen.

## 2026-11-03 Wall space follows the inside face, not the corner order (R10b)

- Wall space (wall photo outline, overlay points, the 3D wall face's u) runs left to right as you stand in the room
  facing the wall. Rooms are not normalized to one winding (that would renumber edges, walls, openings and overlays);
  instead the pure `WallWinding` rule says whether an edge's wall space starts at its first corner (corners clockwise
  on the plan, the default for drawn rectangles) or its second (counter-clockwise, e.g. after a corner is dragged
  across). `PlanMeshBuilder` and both reshape carriers (`WallPhotoCarrier`, `WallOverlayCarrier`) go through it. The
  2D wall screens already show the photo as taken, so stored data needs no migration.

## 2026-11-03 R10d: plan saves hand on the photo files they drop

- A plan save or floor delete returns `PlanPhotoChanges` (recut photo ids plus dropped files) through
  `HomeRepository.onPlanPhotosChanged`; `WallPhotoRepository.afterPlanChange` remakes the straightened copies first and
  then sweeps the dropped files through `ReplacedFiles` (only files no live wall photo or overlay still uses). Besides
  the recut copies the task named, the same path now covers the photos of walls a reshape removed and the wall and
  overlay photos of rooms and floors deleted from the plan, which leaked the same way. The sweep runs even when a remake
  fails: the dropped copy is wrong for the new outline either way and no row points at it.

## 2026-11-03 — 3D markers (T053b)
- Wall overlays show in 3D at every stage, not only the stage they were recorded at: the point of a rough-in pipe is to see it behind the finished wall. Markers use the light layer palette in both themes because they sit on the scene's pale wall tints, not on the app surface.
- One Filament node per marker (not batched) so a tap can name the marker; homes carry tens of overlays, not thousands. TalkBack reaches markers through custom "Open …" actions on the scene, since a 3D node cannot be focused.
- Field Log has no item page yet (T062): an item marker opens the room it stands in.

## 2026-11-03 — Blueprint layer marks (T053c)
- From above a wall overlay keeps only its position along the wall: each wall-space x lands on the wall line (left end per `WallWinding`, like the 3D faces) and the mark is drawn 7 dp inside the room so it reads beside the wall stroke at any zoom. A run spanning under 6" along the wall (a riser, a drop to an outlet) is drawn as a spot of its kind.
- Items draw as a disc in the Items layer color carrying the item's category icon (rule 26); `ItemSpot` carries the icon name. Draw order: wall marks, then plan overlays, then items on top.
- Marks are not tappable on the blueprint yet (the room under them opens); TalkBack gets one node per mark naming it. Tapping a mark is T055a.

## 2026-11-03 — Behind the wall capture (T054)
- The capture stage is a pure rule (`BehindTheWall.captureStage`): Rough-in until the wall has a Rough-in photo, then Finished. It is fixed when the camera opens and kept in SavedStateHandle, so a result after process death lands at the same stage even if another phone added a photo meanwhile.
- FL-BehindWall lives in `:house-model` `ui` beside the room page and outline screen it hands off to; only Field Log opens it (`HouseScreen.onBehindWall`). No new dependencies.

## 2026-11-03 — Items list scroll cost (QA fix auto:jank:house-manual)
- The House Manual Items list uses `LazyLayoutCacheWindow(ahead = 360.dp, behind = 120.dp)` (foundation 1.12, `@ExperimentalFoundationApi`, part of the approved Compose BOM; no new dependency). The suite's framestats put the over-budget frames in Compose's animation phase (rows composed as they scroll in, up to 38 ms); the cache window composes about a screen ahead in idle frame time. If the API changes in a later BOM, the fallback is the default prefetch.
- `ItemsViewModel` maps rows on `Dispatchers.Default` (injectable), as FL-Jobs does; `ItemCategory.subtype` is a map lookup; the warranty line keeps one date formatter per locale instead of building one per row.

## 2026-11-03 — Nate's decisions pass (BLOCKERS.md "Decisions to confirm", 43 lines)
- Confirmed as built: T054 behind-the-wall stage order; R10a merged-wall photos; T052c2 plan overlays filed by first point, points in plan inches; T052b2 one "Serves" link per overlay; T052a framing overlays on no layer, Hot/Cold water, five low-voltage kinds; judge:fl-log-send-empty "Send an empty log?" confirm; judge:fl-log-pdf-company-fallback company email only for Owner/Office sends; T047b2 notification permission on first company tab; T047b1 Firebase by hand, four channels, drop pushes after sign-out; T044c My tasks on Today; T044b1 crew tick only, task photos from the job's photos; T043d1 crew read-only daily log; T049b room tint by kind, VIBRATE stays; jank gate on UI-thread time per frame; cold start measured on the release APK (debug need not hold AC-U11); CR2n1 room delete moves items to Whole house; CR2l3 Lead "assigned jobs" = job_crew or assignment rows; v1 docs carried; FCM for push delivery only; Resend for email; pricing Solo $39 / Crew $79 / HM free, $24.99 yr, $59 lifetime, 15-item free limit; T008c Google sign-in via google.webClientId; T009 invite link, one company per account, rotate to revoke, role rules; judge:hm-skip-drops-floors Skip seeds the house type's floors; R06 walkthrough order; T035b one clock button on Today; CR1 cr:ci-probe-sha reading of Definition of done item 2; CR2f2 ENVIRONMENT defaults to production; CR2e1 crew/sub sync write limits, crew captures on every job; CR2s4 room chip removal keeps photos/items/tasks; CR2m1 `x-` flows; CR2m3 coordinates as a job location; CR2p2 lifecycle-viewmodel-compose and room-testing stay; T046a Sync row on More; T047a schedule push for today/tomorrow, CO-signed notifies Owner/Office; R09 Lead off a job may tick a task; R09d midnight log roll and weather counts as changed; R09f1 US time zones only in v1; T049c Field Log 3D opens on Rough-in.
- Changed (tasks N001–N005 in PLAN.md): CR2n2 reversed, a removed wall's photos and overlays stay on the room with no wall; T034b gets a settings endpoint readable by every member so crew clock-ins obey require-GPS and distance; CR2l3 crew and sub may delete their own photos (author column, Room bump, server rule); R09 tasks become private on the server (crew/sub receive only tasks assigned to them); the builder may make the four housekeeping edits to Nate's files named in CR1/CR2f2/CR2m1/CR2m3 and add the two dependencies to CLAUDE.md's table.
- Process: a "Decision to confirm" unanswered for 7 days is confirmed and moves here (Nate, 2026-11-03).


## 2026-11-03 — Item positions on the plan (T055b)
- An item is in the room it is placed in: a tap inside another room on Place on plan moves the item to that room; a tap outside every room lands 6" inside the nearest room (`ItemPlacement`). Changing the room on Edit takes the item off the plan (its spot belonged to the old room).
- Exterior and Whole house items have no floor, so the Where row says they stay off the plan and opens nothing.
- Place on plan saves each tap right away, the way the plan editor saves each edit, so there is no unsaved state and Back never asks. The screen lives in `:house-model` `ui` so Field Log's item page (T062) can reuse it. A tap moves the marker; dragging it is T055d. No new dependencies.

## 2026-11-03 — Items on a wall (T055c1)
- An item's spot on a wall is stored like a wall overlay's: `item.wallX/wallY` in wall space (x 0..1 left to right seen from inside the room, y 0 ceiling to 1 floor), REAL columns added by Room auto-migration 8 → 9. Sync carries them with no backend change (rows are passed through as JSON).
- Hanging an item on a wall also writes its plan spot 6" inside that wall (`ItemPlacement.onWall`), so the blueprint and its tap targets need no second code path; the 3D view uses the wall spot. A spot picked on the plan, a room change on Edit, or the wall's removal clears the wall spot.
- T055c split: the data and 3D half first (testable without UI), the wall picker on Place on plan as T055c2.
- 2026-11-03 (T055c2): "On a wall" on Place on plan offers only the walls of the room the item is already in (a tap on the plan picks the room first); the wall is drawn to its length:height with its newest-stage picture stretched over it (wall space maps straight onto the straightened copy), and a tap saves the spot right away like a plan tap. The item page's Where row names the wall the room page's way ("North wall", "North wall 2").

## 2026-11-04 — A removed wall's photos stay on its room (N001, reverses CR2n2's delete)
Schema v10: `wall_photo.wallId` is nullable and every wall photo carries `roomId` (backfilled from its wall in the v9→v10 auto-migration; new photos set it on save). A plan save that removes a wall (corner deleted, sides made a diagonal) clears the photo's `wallId` and keeps the row and its files; the room page lists them under "Photos of removed walls" with stage and date, newest first. A wall later drawn in the same place is a new wall and never reclaims them; they go only when their room is deleted. One exception: when a merge already carried the same picture (same file, same stage) onto the wall now running there, the removed wall's row is dropped as a duplicate and its files go to the sweep, which keeps any file a live row uses. Wall overlays on a removed wall already moved onto the floor plan with their room kept (R10), so they needed no change. No released schema had run the delete (testing APKs only), so nothing is restored. The backend stores rows as JSON, so it needed no change.

## 2026-11-04 — N002: the clock rules every member reads are `/me` and `GET /companies/:id`, no new route
Nate's 2026-11-03 correction of T034b asked for a settings endpoint every member can read. CR2l1 (2026-10-27) and the AC-B3 column split already deliver it: `/me` sends each company membership its clock rules (require GPS, punch distance, photo on punch, break and overtime rules, time zone) for every role, and `GET /companies/:id` gives Crew, Lead and Sub the same settings while `/companies/:id/profile` stays Owner/Office only. The app caches them with the membership and refreshes on every return to the foreground, and the Clock reads them for every role. So no `/company/settings` route was added (a second source would drift); N002 adds the missing proofs: a vitest that Lead, Sub and Crew get the owner's rules with the time zone from both routes and a 404 from the profile, and a clock test that a crew member's far-from-job check follows the company's distance rather than the 500 ft default.

## 2026-11-04 — N003: a photo's author may delete it
Nate's correction of CR2l3: Crew and Sub may delete the job photos they took. `photo.authorMemberId` (Room v11, auto-migration 10→11; rows from before stay null, so only Owner, Office or the job's Lead may delete those) is stamped by `JobPhotos.save` with the signed-in member. `RoleGates.canDeletePhoto(jobId, author)` allows whoever leads the job or the author; `backend/src/sync/policy.ts` applies the same rule to a push that deletes a photo (stored row not deleted, incoming `deletedAt` set), keeps `authorMemberId` immutable once set, and refuses a new photo pushed under another member's id; caption, tag and markup edits stay open to every member as before. "Photo row shows the author" is read as a "Taken by Marco Diaz" line on the photo page under the picture (in the app bar subtitle the name was cut off): the job's photo grid is thumbnails only, and the page is where delete lives.

## 2026-11-04 — N004: tasks private on pull, with a stub for the former assignee
Crew and Sub now pull only the tasks assigned to them; a Lead also every task of the jobs they are on (job_crew or assignment); Owner and Office everything. Hiding alone would leave a reassigned task on the old assignee's phone still marked as theirs (they would never pull the change), so the server records every member a task has been assigned to (`task_seen`, migration 0012, written in the push batch for the old and new assignee) and sends that member a stub `{redacted: true, id, jobId, assigneeId}` with only those field times. The phone applies a stub only to a task it already has (the client-side filter then hides it) and never inserts or keeps one as the sync base. Members who never had a task do not learn it exists. The media presign gate treats a stub as not visible (`seesFullRow`). Not covered: a Lead taken off a job keeps that job's tasks already on their phone until they reinstall; the app's role filter still hides tasks not assigned to them once they are no longer the job's Lead.

## 2026-11-04 — Change order money and voice (T056a)
- Quantities are stored in thousandths (`qtyMilli`) so fractional hours and footage stay exact; each line rounds half up to the cent once and the total is the sum of the rounded lines, so the PDF and the client page always add up (MoneyMath). Prices parse through the existing `core.units.Money`, not a second parser.
- The change order's line items live in `change_order.linesJson`; photos need a new column and come with T056b (Room v12).
- "Speak" for the description uses the platform speech-to-text screen (`RecognizerIntent`, result appended to the text) instead of recording a file: no new dependency and no audio kept; hidden when the phone has no recognizer.
- Only Draft change orders can be edited or deleted; Signed and Declined stay on record (AC-F38).

## 2026-11-04 — Change order photos (T056b)
- Photos on a change order are references to the job's own photos (`change_order.photoIdsJson`, a JSON array of photo ids, Room v12 auto-migration with default `[]`), not copies: one picture, one upload, and the same photo shows in the job gallery. The repository keeps only live photos of that job, once each, in the order added.
- "Take photo" opens the existing job camera (`CaptureKey`) rather than a second camera; the form notes the time in its saved state and every job photo taken after that joins the change order once (one the person removes stays off). No new permission or dependency.
- The sync backend stores rows generically (`sync_rows`), so the new column needs no backend change.

## 2026-11-04 — T057a change order signing
- Signature strokes are kept normalised (0..1 of the pad) and drawn into a 1200×480 PNG with black ink on transparent, stored as `change_order.signaturePath` in filesDir/media; the PDF (T057b) uses the same PNG. A signature needs ink of at least a fifth of the pad's width so a tap cannot sign.
- "Sign on phone" saves the form first and needs at least one priced line; the signer name starts as the client's name and is required.
- The signature PNG is local to the signing phone for now (it is not uploaded through media sync); the signed status, signer and time sync. Uploading the image rides with the share links work (T058), where the web signature also needs a stored image.
- Duplicate copies title, description, client, lines (new ids) and photos into a new Draft and opens it.

## 2026-11-04 — T057b change order PDF
- The change order PDF reuses the daily log's drawing ops (`LogPdfOp`, `LogPdfPage`, `AndroidLogPdfWriter`); `LogPdfOp.Image` gained `fit` (drawn whole, for logo and signature) so one writer draws both documents. Layout is the pure `ChangeOrderPdfLayout` in `:core`.
- Share PDF shows on every saved order, Draft included (a contractor often sends a priced draft before the client signs); a Draft's typed changes are saved first so the PDF matches the form. An unsigned PDF prints "Not signed yet" and a blank X line to sign on paper.
- The PDF is written to `cache/change-orders/` and shared through the existing FileProvider; it is not stored on the change order (`pdfPath` stays unused until the share link work in T058 needs a stored copy).

## 2026-11-04 — Share links (T058a)

- A share link is the synced `share_link` row the app writes (Owner/Office only, existing sync rule); the backend serves pages from it rather than a separate create/revoke API, so a link made offline works once the phone syncs and revoking is the same row edit. The row gains `targetId` (the daily log or change order the link names; empty for a job gallery) because one job has many logs and orders. Reason: one source of truth, no extra endpoint, revocation follows the normal sync path.
- A web signature is stored on the change order as `signatureJson` (the same normalised stroke JSON as the app's `Signature`) with `signedVia: "web"`, not as an uploaded PNG, so it needs no R2 credentials and the phone draws the PNG and PDF from the strokes exactly as for a signature taken on the phone. The server writes the row as a server change (next seq, field times = now), so a phone's older draft edit cannot undo a signature.
- Share pages stream photos through the Worker's `MEDIA` binding (no presigned URLs), and a log or change order link only reaches the photos on that log or order.
- (T058b1) Signature strokes are now stored on every signed change order (`signatureJson`, synced), including ones signed on the phone; the PNG path stays a device-local column, and a phone that has only the strokes draws the PNG once (`JobChangeOrders.signatureImage`). Before this, a signature taken on one phone never reached the other phones. Share links are written by Owner and Office only (the backend's existing rule), so Leads see Share PDF but not Send link to sign. The button reads "Send link to sign" on an unsigned order and "Share link" on a signed one; the share text is "Change order: <title>. Read it and sign here: <url>".
- (T058b2, 2026-11-04) The gallery link shows on the Photos tab only once the job has a photo, and a daily log link only once that day's log has been sent (both Owner and Office only, as the backend requires); one `ShareLinkController` in `fieldlog.share` serves both screens so the logic stays out of composables and is unit tested once.

## 2026-11-04 — T059 Schedule

- Weeks start Monday, like timesheets. Schedule rows come from the synced `member` table in its order; Crew and Sub see only their own row. A Lead may schedule only the jobs `RoleGates.leads` gives them (same rule as backend policy.ts LEAD_JOB_ROWS). Assignable jobs are Active and Upcoming; Done jobs' past days still show, Archived ones do not.
- The same job twice for one person on one day is stored once (assign returns the existing row; a move onto a twin removes the moved row). Two different jobs on one day are kept and badged (AC-F43).
- A tapped schedule push sets `AppContainer.scheduleOpenDay`; FL-Schedule jumps to that week and day and clears it (T059a), so the tab stays a root with no extra back stack entry.

## 2026-11-04 — FL-Clients is Owner/Office only and lists clients named on live jobs
SPEC AC-F45 says "Clients list (from jobs)". Clients with no live job are left out, and the screen sits with Team and Company in More's admin rows, so Crew and Leads do not see client phone numbers and emails outside the jobs they work. Phone and email are edited on the job form (the client row is shared), so Clients is read-and-act only.

## 2026-11-04 — FL-Materials (T060b): categories, admin-only for now, the OCR total is a suggestion
- Categories are a fixed list stored by enum name in `material.category`: Materials, Tools, Equipment rental, Fuel, Permits, Other; an unknown stored value reads as Other.
- More → Materials and receipts is Owner/Office only, like Clients. Backend policy also lets a Lead write materials on the jobs they run; the Lead entry comes with T060c.
- Scan receipt uses the ML Kit document scanner in JPEG mode with one page (`rememberReceiptScanner` in :core-ui), copies the page to `filesDir/media/<uuid>.jpg` and reads it with the bundled ML Kit recognizer already used for data plates. `ReceiptTotal` takes the last "Total / Amount due / Balance due / Grand total" line with an amount (same line or the next), skipping subtotal, tax, savings and item counts. The total is offered as a "Use $x from the receipt" chip, never filled in silently, because a misread total would otherwise go to the bookkeeper unnoticed.
- The CSV is Date, Job, Vendor, Category, Amount, Note, then a Total line per job and one for all jobs; cells go through the same quoting and formula defusing as the time exports.
- `receiptPath` is a device-local column (LocalColumns), so the receipt image stays on the phone that scanned it until T060c uploads it.

## 2026-11-04 — T060c1: receipt images in R2

- `material` joins the media entities (25 MB like photos). The key is `<company>/material/<materialId>/<file>.jpg`, and every scan gets a new file name, so a rescan never overwrites the old receipt's object and the orphan cron collects it. No backend migration: the material row (with its new `r2Key`) is a synced JSON row and the upload policy (Owner/Office, Lead on their jobs, never Crew) was already the row's write rule.
- Room v14 adds `material.r2Key` (auto-migration). Saving keeps the key while the receipt image is the same file, and drops it on a new scan or Remove; the new scan is queued once through the existing upload queue.
- Another phone downloads the receipt when its form opens (`MediaDownloads.receipt`), into the download cache; the local `receiptPath` is set without a sync. The list does not show thumbnails, so nothing else needs the bytes.

## 2026-11-04 — R11: a signed change order is sealed on the server

- `mergeRow` (backend/src/sync/merge.ts) treats a stored `change_order` whose status is SIGNED as sealed: every phone write to it is dropped and logged as a conflict with reason `signed`, whatever its updatedAt and whoever sends it. Before, per-field last-writer-wins let an offline phone's later Draft edit (or a second, offline signature) overwrite the client's web signature, putting the order back to Draft with no signature. AC-F38 already makes a Signed order read only in the app ("duplicate to change"), so no legitimate write is lost. The share page's own signing path (`serverWrite`) already refused a second signature.
- Not mirrored in the app's `RowMerge`: the phone pushes before it pulls, so after the refused push the pull brings the sealed row with nothing pending and it lands as is.

## 2026-11-04 — T061a: job summary PDF covers the whole job

- The job summary (AC-F47) runs from the job's first punch to today, not the Reports week range: "hours vs budget" only means something against all the hours. Days on site are the days someone started work on the job (clock in, switch to it, back from a break). Change orders show signed (count and total) apart from drafts and declined, so the total is what the client agreed to. Delays come from the job's saved daily logs, counted by days and by reason.
- Reports lists every job (Active first, then by name) under Job summaries; a tap makes the PDF with the daily log's layout and opens the share sheet. No new dependency.

## 2026-11-04 — T061b: photo report PDF

- Pure `PhotoReportPicker` and `PhotoReportLayout` in :core `log` (job's live photos in the range, oldest first; 2×2 per page with room · phase, date and time, note cut to three lines). It draws through the existing `AndroidLogPdfWriter`, so there is no new dependency.
- The range is the Reports range chips; photos not downloaded to this phone are left out and counted on page one (see BLOCKERS "Decisions to confirm").

## 2026-11-05 — T062: installed items on the job (AC-F50)

- Field Log uses House Manual's item form (`ItemEditRoute`, with the data plate OCR and the vision fallback) and Place on plan unchanged; the room page is the shared one with its item list and "Add an item here", walls still opening on Rough-in. The item page is a Field Log screen (`fieldlog.house.JobItemScreen`): Details (make, model, serial, installed), Where, Warranty (lookup, review, Registered) and Notes. House Manual's item page was not moved to :house-model because its consumables, service history, documents and recalls belong to the homeowner after the handoff, not to the job.
- Who edits: Owner, Office and any Lead add, edit, look up and delete items (new `RoleGates.canEditHouse`, the same rule as the backend's JOB_HOUSE set, since house rows carry no job id); Crew and Sub read the room's items and the item page.
- Sync: the company tenant now accepts `item`, `warranty`, `consumable`, `maintenance_task` (the defaults a new item seeds) and `overlay`. `overlay` was missing although FL-House already drew overlays, so a job's overlays were refused with reason `entity` and never reached the crew's other phones; it joins the same JOB_HOUSE rule. Items and warranties made on a job are stamped `source = field-log` (new `source` parameter on `ItemRepository` and `WarrantyLookupRepository`), which T063's handoff can read.
- Item photos stay on the phone that took them (as in House Manual today); only the row syncs. The warranty lookup uses the same `/lookup/warranty` endpoint and per-account daily quota as House Manual.

- 2026-11-05: `.homerecord` v2 keeps each record as a JSON object per entity type (not typed DTOs) so a v2 reader tolerates fields and entity types a later writer adds (AC-C46) and `ImportPlanner` can compare records generically. Same-id conflicts are decided against an import ledger (last imported `updatedAt` per record); with no ledger, any differing record is a conflict, never an overwrite (AC-C47). A bundle entry outside `media/` is refused, not skipped.

- 2026-11-05 (T063b1): the bundle export reads rows straight from the Room tables through `RoomRowStore.readWhere` instead of per-entity mappers, so every column (including ones added later) travels without a mapper to keep in step. Device-local columns (`path`, `*Path`, per `LocalColumns`) never travel; each that names a file becomes a `_media` entry (column → file name under `media/`), and the importer points the column at its own copy. The import ledger is a Room table (`import_ledger`, v15) keyed by entity type and record id, scoped to a home.

- 2026-11-05 (T063b2): FL-Handoff is its own screen under the job's House tab ("Hand off", Owner/Office/Lead per `RoleGates.canEditHouse`, same gate as editing the house). The summary is counted from the bundle about to be written (`HandoffSummary.of`), so it cannot drift from the file. Change orders counted and shipped are the signed ones only. The file is `cache/handoff/<job> <yyyy-MM-dd>.homerecord`, shared as `application/octet-stream` (no registered MIME type exists; House Manual's import in T063d matches the extension). Export is disabled when the house has nothing a homeowner would see.

- 2026-11-05 (T063c1): the handoff claim (AC-B40) does not copy rows on the server. Claiming binds the handoff to the first signed-in account that claims it, which downloads the bundle and imports it in House Manual with the same preview and conflicts as a file import (AC-H17); House Manual's normal sync then registers the home (`PUT /homes/:id`) and uploads it, which is the copy into the homeowner's tenant. One import path instead of two, and the homeowner sees what arrives before it lands. The bundle is uploaded as the raw request body (no presigned R2 URL), so a handoff is capped at Cloudflare's 100 MB request limit; a bigger home goes by Export `.homerecord`. Only a SHA-256 of the claim token is stored. The email's link opens a small page on the API domain whose button is an Android intent link (`homerecord://handoff/<token>` for package `com.kurtz.homerecord.housemanual`), which House Manual registers in T063d; without the app installed Android opens its Play listing.

- 2026-11-05 (T063c2): "Send to homeowner" sits on FL-Handoff above Export (Send is the filled button, Export now outlined), so the one primary action is the one that needs no file juggling. The address is prefilled from the client of the live job on that house (the active job first) and checked on the phone (something@domain.tld) before anything uploads. The home is named in the email by the job's address, or the job's name without one. The bundle is written to cache, uploaded and deleted; a finished send ("Sent to …, the link works until …") and the typed address survive process death, and Send again clears the sent state. Offline and server errors offer Retry; a house over 100 MB points at Export instead.

## 2026-11-05 — T064a: RevenueCat webhook and Field Log plan limits

- The apps log in to RevenueCat with `company:<companyId>` (Field Log; the Owner buys for the company, so every phone of the company sees the same plan) or `home:<homeId>` (House Manual Pro). The webhook maps those ids (also found in `aliases`) to the tenant; anonymous ids are acknowledged and ignored.
- Grant: INITIAL_PURCHASE, RENEWAL, PRODUCT_CHANGE, UNCANCELLATION, NON_RENEWING_PURCHASE, SUBSCRIPTION_EXTENDED, TEMPORARY_ENTITLEMENT_GRANT; CANCELLATION and BILLING_ISSUE keep the plan until the expiry RevenueCat sends; EXPIRATION clears; TRANSFER clears the ids it left. Event ids are stored once (`billing_events`), and an event older than the last applied one changes nothing.
- `/me` reports only an entitlement still in force (expiry passed reads as none), plus `plan` (trial / solo / crew / expired) and `memberLimit` for companies. The trial counts as Crew (10 members, "everything on"). Joining past the limit is 409 `member_limit`; joining an expired company is 402 `subscription_required`. Existing members are never removed when a plan shrinks; the Owner sees who is over the limit (T064c).

## 2026-11-05 — T064b2: Field Log RevenueCat client

- `CompanyBilling` in :core (`plans`, `purchase`, `restore`, each taking the company id) with `UnavailableCompanyBilling`; Field Log's `RevenueCatCompanyBilling` logs RevenueCat in as `company:<id>` before every call, so a purchase is credited to the company the webhook (T064a) updates. The plan in force is still read from `GET /me` (`FieldLogPlan`); the store's active entitlement (`fieldlog_crew` wins over `fieldlog_solo`) only confirms a purchase or restore.
- Packages are matched to Solo or Crew by "solo" / "crew" in the package or product identifier, since RevenueCat's SDK does not say which entitlement a package unlocks; any other package in the offering is ignored. Nate's Field Log offering should name its products accordingly (e.g. `fieldlog_solo:monthly`, `fieldlog_crew:monthly`).
- Keys: debug reads `revenuecat.fieldLogTestApiKey` (local.properties) or `REVENUECAT_FL_TEST_API_KEY`, then the release chain; release and benchmark read `revenuecat.fieldLogApiKey` or `REVENUECAT_FL_API_KEY` (env names from BLOCKERS). Blank → Unavailable (paywall Retry state). The client is created lazily on first use, so the SDK does not start with the app and House Manual's own RevenueCat setup is untouched.

## 2026-11-05 — T064c FL-Paywall and the lapsed company

- When the company's plan is not in force (`FieldLogPlan.access`), the Owner's whole app is FL-Paywall ("Pick a plan to keep going", Solo and Crew with store prices, Restore, Sign out; no tabs). Everyone else (Office, Lead, Crew) gets the Clock screen in a read-only mode: "Ask your owner to renew", why, Sign out, and their own week (My timesheet and punch changes); no job picker, note or Clock in. Office is treated like crew here because SPEC AC-F56 names only the Owner as the buyer.
- During the trial or a paid plan the Owner reaches the same screen from More → Plan (row supporting text "Trial, N days left" / Solo / Crew); other roles have no Plan row.
- After the store reports a purchase or restore the screen says "<plan> is on. It can take a minute to reach every phone." and re-reads `/me`; the plan in force always comes from the backend (webhook, AC-B50), never from the store answer alone.
- QA: dev-only `POST /dev/age-company {email, days=15}` moves the company the email owns back that many days (created_at and trial_ends_at), standing in for the walkthrough's "set the company creation date back 15 days in the debug menu". Flow F17-trial-expiry uses it.

## 2026-11-05 — T065a FL-Settings, About and the account line

- T065 split into T065a (Settings, About, account) and T065b (units applied to the house screens).
- Units are a per-phone display choice (`:core` `UnitsStore`, plain SharedPreferences `display`), kept across sign out because it is not company data. The `Home.units` column stays as the home's own record; T065b decides how the two combine (phone choice wins for display).
- Storage on this phone with Free up moved from the More root into Settings, as SPEC FL-More lists offline storage under Settings. Test tags `fl_more_storage` / `fl_more_free_up` kept so F07b only gained one tap.
- Account: More's header names "Signed in as <email>" (from `/me` `user`, now cached with the membership) and Sign out stays on the More root with its confirm. No separate Account screen: it would hold only those two things.
- Units are two radio rows, not a segmented row: "Feet and inches" wraps at 200% and a segment cannot (rule 32).

- 2026-11-05 — Units reach the shared house screens through a `LocalUnitSystem` CompositionLocal in `:house-model` (T065b, AC-C4). Field Log provides it at the app root from `UnitsStore`; House Manual provides nothing, so it stays feet and inches until it gets a units setting. Routes overlay the current choice on their UI state, so a change in Settings shows at once on screens already in the back stack. The plan editor's snap grid (1 in imperial, 4 in metric) is chosen when the editor opens, so a change made while it is open applies to the grid the next time it opens; displayed and typed lengths follow the choice immediately.

- 2026-11-05 — An expired Field Log company is read only on the server ([r12:expired-writes-accepted], AC-F56): `/companies/:id/sync/push` and `/media/presign` answer 402 `subscription_required`; pulls and presign-get stay open so crew still see their own week. The phone keeps its queue: SyncEngine skips the push and still pulls, MediaUploader waits on a 402 like offline (no attempt counted), so nothing written before or during the lapse is lost when the company renews. Homes are never gated by a company plan.

- 2026-11-05 — Breaker panel model (T066a, AC-C40): a tandem is two single-pole breakers whose `slotsJson` hold the same slot (no extra column); a two-pole breaker fills its slot and slot+2 on a two-column panel (same column, next row) or slot+1 on a DIN rail. Rooms and items the homeowner picks in the breaker editor are stored as `breaker_link` rows with confidence Likely; Confirmed is left for mapping mode (AC-C42) and Unknown for links nobody has checked. A panel cannot shrink below its highest used slot, and its layout cannot change while it holds breakers (the slot numbers would mean something else). Breakers whose slots clash after a sync merge are listed as unplaced rather than dropped.

- 2026-11-06 — Mapping mode rules (T068a, AC-C42): the phone counts as powered from `ACTION_BATTERY_CHANGED`'s `EXTRA_PLUGGED` (sticky, so the current state is known on attach; no extra permission). A drop within 2 s of arming is a shaky plug and goes back to waiting with no alarm. The alarm (default alarm sound on the alarm stream, looping, plus vibration; `VIBRATE` is a normal permission) keeps sounding when the power comes back, until Silence, so it can be heard from the panel. Leaving the screen is final for that session. A pick upgrades the breaker's existing link to that room/item to Confirmed (or makes one), and marks the breaker itself Confirmed; later breaker-form saves keep it.

- 2026-11-06 — Shutoffs (T070a, AC-C44): grouping lives in :core `ShutoffGroups` keyed by subtype (water_main, fixture_shutoff, sprinkler, sump, cleanout → Water; gas_main, propane_tank → Gas; electrical_main → Electrical; hvac_disconnect → HVAC; anything else → Other); the three mains sort first in their group. Instructions reuse the item Notes column and the photo is the first item photo, so no migration (confirm pending in BLOCKERS.md).

- 2026-11-06 — Reminder check scheduling (T072c, AC-C50): WorkManager periodic work (24 h) with an initial delay to the next 08:00 local, enqueued with KEEP from `HomeRecordApplication.onCreate`, rather than exact alarms (SCHEDULE_EXACT_ALARM is not justified for upkeep reminders) or a self-rescheduling one-time chain. Accepts a one-hour drift after a daylight-saving change.

- 2026-11-06 — Backup format (T074a, AC-C48): a backup is the home's `.homerecord` zip sealed by `BundleCrypto` into a `.hrbackup` file: header `HRBK`, version 1, PBKDF2-HMAC-SHA256 iterations (210 000), 16-byte salt, 8-byte nonce prefix; then 1 MiB blocks, each its own AES-256-GCM message with nonce = prefix ‖ block counter and AAD = header ‖ last-block flag. Blocks rather than one GCM message because Android's GCM decrypt holds the whole ciphertext until the tag checks (a multi-GB home would run out of memory) and so nothing is written before it is authenticated; the counter and the last flag stop reordering and truncation. The plaintext zip passes through one file in the app's cache, deleted before the call returns. javax.crypto only, no new dependency. The passphrase is never stored (not in SavedStateHandle either): after process death the user types it again.

- 2026-11-06 — House Manual media to R2 (T075a1, AC-H15, AC-C17): House Manual queues photo files for upload after each successful sync of its home (`HomeMedia.afterSync`: every live item/room photo and wall photo of the home that is on this phone and has no R2 key yet), not when the photo is saved as Field Log does. House Manual works signed out, so files made before the first sign-in must go up later, and the home must be registered on the server (the sync's first pass) before a presign in its tenant succeeds. The same pass fetches item photos that came from the home's other phones (path empty, key set) into the download cache; wall photos come down when their room page opens. Uploads reuse Field Log's `MediaUploader`, `MediaUploadWorker` and `UploadedKeys` with `TenantRef(Home, homeId)`; keys are `<homeId>/photo/<id>/<file>` and `<homeId>/wall_photo/<id>/original.<ext>|texture.jpg`.

- 2026-11-06 — House Manual documents to R2 (T075a2, AC-H15): `document.r2Key` (Room v16, synced) set by `UploadedKeys` when the upload finishes; `HomeMedia.afterSync` queues documents like photos. Documents from another phone are not fetched after each sync (they can be up to 100 MB each); the viewer fetches one when it is opened, into the download cache (`MediaDownloads.document`, local path only). With no connection the viewer shows "No connection" with Retry instead of "can't be opened".

## 2026-11-06 — T075c Delete cloud copy (AC-H15)

- `DELETE /homes/:id` is owner only (anyone else, including a home member, gets 404 like any tenant they cannot see, AC-B2). It deletes the R2 objects first (every key under `<homeId>/`, the prefix `/media/presign` signs, plus any `media_objects` row of the tenant), then in one D1 batch the tenant's sync rows, applied marks, conflicts, counter, media rows, push log, memberships and the `homes` row. R2 goes first so a Worker that stops half way leaves rows that still name the tenant and a retry finds the rest.
- On the phone the delete happens before anything local changes; a failed call (offline or a server error) signs no one out. After a success the app signs out first (no sync pass can run), then makes the home look never synced: the tenant's outbox rows, cursor and confirmed rows are dropped, every R2 key on its photos, wall photos and documents is cleared, and every row of the home (walked like the `.homerecord` export) is queued again, so signing in later uploads the whole home and its files as a new cloud copy.
- A second phone signed in to the same account is not told; its next sync answers 404 for the home. Sharing (T076) will need a word for members when an owner deletes; noted for T076.
- Wording: a "Cloud copy" section under Sign out with what it does, "Delete cloud copy" in the error colour, a dialog with one sentence and a Delete button (rule 16); after the delete HM-Account shows "Cloud copy deleted. This home is still on this phone. Sign in again to start a new cloud copy." above the sign-in form.

## 2026-11-06 — T075d Second phone gets the home (AC-H15)

- House Manual's sync tenant is the phone's own home, so a fresh install had nothing to sync after sign-in. Onboarding now has "Sign in to get your home" (`hm_onboarding_sign_in`): the shared sign-in form, then `CloudHomeRestore` asks `GET /me` for the account's home memberships and runs one sync pass for the chosen home, which pulls every row; the home row arriving swaps onboarding for the tabs and the normal sync and photo passes (T075a) take over. No backend change.
- Which home: the one the account owns, then any shared one, ties by name then id (stable). One home per phone stays the model (SPEC free tier is one home); a phone that already has a home is never pulled over.
- No home on the account: "No home on this account yet" and Set up a home (back to onboarding, still signed in, so the home made next is uploaded). Failure: Retry, or Set up a home instead.
- The sign-in entry is on onboarding only. Signing in from More on a phone that already has its own home keeps uploading that home (unchanged); merging two homes is out of scope.

## 2026-11-06 — T076a Home sharing on the backend (AC-H16)

- Home roles are `owner` (made the home; invites, removes people, deletes the cloud copy, billing), `editor` (family; edits the whole home) and `viewer` (buyer or house-sitter; reads only). The old home role name `member` reads as editor. `memberships` is rebuilt in migration 0016 because SQLite cannot widen a CHECK constraint; columns and index are unchanged.
- Invites are one link per person: random token (SHA-256 stored), single use, 14 days, revocable while unused; the daily cron drops expired unused links. A link a member of the home opens again answers their current role and uses nothing up, so the owner tapping their own link does no harm. A link another account took answers 409, expired 410, revoked or unknown 404.
- Only the owner makes, lists and revokes links and removes people; anyone else may leave (remove themselves). Every member sees the people list. The owner cannot be removed.
- A viewer is read only on the server too: every sync push op is refused with `role` and `/media/presign` answers 403; pulls and signed downloads stay open. The app hiding edit controls (T076c) is the user-facing half.
- The link page is `/invite/<token>` on the API host and opens `homerecord://invite/<token>` in House Manual through an `intent://` link, which sends a phone without the app to Google Play, like the handoff link.
