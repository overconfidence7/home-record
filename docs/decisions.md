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
