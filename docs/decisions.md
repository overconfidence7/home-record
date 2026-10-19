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
