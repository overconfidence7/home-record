# Competitors and what we take from them

Research done 2026-10-19 (web). Used to make sure SPEC.md covers every table-stakes feature. Prices change; treat them as that day's numbers.

## Field Log: jobsite apps for 1 to 10 person crews

### What they charge (entry tier)

| Product | Entry price | What is gated |
| --- | --- | --- |
| CompanyCam | Core $63/mo (1 user, max 2); Crew $119-129/mo (3 users) | Checklists, tasks, proposals on Crew; signatures, dashboards, sub access on Scale ($199+) |
| Raken | Quote; in-app ~$35-93 per user/mo | Analytics on top tier; no admin on mobile |
| Buildertrend | Quote by volume; last published $449+/mo | Estimating, COs on Advanced; selections, warranty on Complete |
| JobTread | $199/mo + $20/user | Nothing gated; PWA only (not in Play Store) |
| Procore | Annual contract, $15-30k+/yr | Per module; not for small crews |
| Fieldwire | Free (5 users, 3 projects, 100 sheets); Pro $39/user/mo | Reports, sheet compare on Pro; COs on Business Plus ($89/user) |
| Contractor Foreman | Basic $49/mo (1 user); Standard $105; Plus $166 (8 users) | Scheduling, daily logs, QBO not on Basic; time cards on Plus+ |
| Jobber | Core $49/mo (1 user); Connect $139 | Time tracking on Connect; geofence timers on Grow ($199+) |
| Housecall Pro | Basic $59/mo; Essentials $149 (5 users) | GPS tracking, photo reports on Essentials |
| busybusy | Free (unlimited users, GPS time clock); Pro $9.99/user + $40 | Scheduling, breadcrumbs on Pro; photos, docs, messaging on Premium |
| ClockShark | $40 base + $9/user | PTO, forms on Pro |
| QuickBooks Time | $20 base + $10/user | Geofencing on Elite |
| Workyard | ~$6-13/user + base | Geofence modes, scheduling on Pro; auto clock-in on Autopilot |
| Knowify | $99/mo + $29/user | Daily logs, portal on Advanced ($329) |

Nothing gives a 5 to 10 person crew time clock + photos + plans + daily logs under about $105 to $199 a month. Flat $39 / $79 is the wedge. busybusy gives GPS time clock away free, so the time clock alone cannot be the paid feature.

### Table stakes (all in SPEC.md)

1. GPS-stamped time clock with breaks, switch job, crew clock-in (AC-F8..F11)
2. Timesheet review and approval with flags, CSV and QuickBooks export (AC-F12..F15)
3. Project-organized photos with time/GPS stamps, before/after, annotation (AC-F24..F28)
4. Plan and PDF storage with viewer and markup, offline (AC-F19..F23)
5. Daily log with auto weather, crew, work done, materials, photos, PDF (AC-F30..F33)
6. Tasks and punch list with assignee, photo, due date, plan pin (AC-F34..F36)
7. Crew schedule by day with notifications (AC-F41..F43)
8. Offline capture with background sync (AC-F52..F55)
9. Roles and permissions (AC-F3, AC-B3)
10. Change orders with client e-signature (AC-F37..F40)
11. Receipts and materials tied to a job (AC-F44)
12. QuickBooks-ready time export (AC-F15)
13. Client share links for photos and logs (AC-B35)
14. Per-job comment thread (notes, AC-F27; team chat is out of scope for v2)
15. Hours by job and person, photo report, job summary (AC-F46, F47, F28)

### What nobody does well (our differentiators)

- One flat price for the whole crew.
- Time + photos + plans + logs in one app that is fast on a $200 Android phone (Raken and Bluebeam are weak on Android; JobTread is a PWA).
- Daily log that writes itself from punches, notes, photos and tasks (AC-F30).
- Foreground-only GPS that respects Play policy: GPS at punch events, distance flag with a reason, owner map of pins. No "Allow all the time" (Jobber and QuickBooks Time require it and users complain about battery and drift).
- Timesheet anomaly flags with one-tap fixes (forgot to clock out, far from job, no break).
- The house model: behind-the-wall photos and overlays that become the homeowner's record. Nobody has this.

### How the good ones navigate (Android)

- Raken: bottom bar Projects, Activity, Time Clock, More; Clock screen has Clock / Timesheets tabs.
- Buildertrend: job switcher at top; tabs Summary (clock in/out, tasks, schedule), Schedule, Time Clock (Overview / Shifts / Map), Activity; Quick Add (+) for photo, log, task; home-screen widget for clock in/out.
- Jobber: Home, Schedule, Timesheet, Search, More; floating + for create.
- busybusy: Time Clock, Time Cards, Schedule, Reports; complaint: admin menu only reachable from the dashboard.
- Fieldwire: project → Plans first (folders, recents, search), plan viewer with pins for tasks and photos, Tasks with "My tasks" and swipe to complete.
- CompanyCam: camera first; open, shoot, auto-filed to the nearest project; timeline feed.

Takeaway used in SPEC.md: 5 bottom destinations with the time clock as its own tab, a job with tabs, a capture FAB, admin reachable from More on every screen.

### GPS time tracking patterns

| App | At punch | Geofence | During shift |
| --- | --- | --- | --- |
| Workyard | GPS per punch, kiosk, selfie | Reminder / auto clock-in / restricted modes; drive-by discard; clock-out trim | Pings every ~5 min while clocked in |
| Buildertrend | GPS at clock in/out only | Out-of-bounds flag for reviewer | None ("location will not be tracked during your shift") |
| busybusy | GPS at punch (free) | Pro: arrival reminder, on-site restriction | Pro: breadcrumbs |
| ClockShark | GPS at punch, breaks, switches | Reminders only, never blocks | Optional breadcrumbs (needs All the time) |
| QuickBooks Time | GPS while clocked in | Elite: reminders | On the clock only |

Play policy: background location needs a declaration form, prominent disclosure, a video, and "core functionality" justification; approval stalls releases. SPEC.md keeps everything foreground-only (AC-F8, AC-F18). A foreground service with a persistent notification for optional while-clocked-in breadcrumbs is a possible later addition.

## House Manual: home record, inventory and warranty apps

| App | What it does | Warranty handling | Rooms | Price |
| --- | --- | --- | --- | --- |
| HomeZada | Inventory, documents, maintenance, projects, finances, AI chat | A typed field; no lookup | By room | Free; Premium $99/yr; transfer to buyer $99 |
| Centriq | Photo the data plate → model → manual, parts, recalls; by room | Warranties filed per item | Yes | Appears defunct (site redirects) |
| Nest Egg (iOS) | Possession inventory, UPC scan | Expiry alerts | Locations, QR labels | $6.99 one-time |
| HomeBinder | Inspector-seeded binder: appliances, service history, paint, permits | Recall notifications by make/model | By room | Partner-channel only |
| HomeKeep (iOS) | Maintenance scheduler, equipment records, shutoffs list | Dates and docs | No | $3.99/mo |
| Fix app | Label photo → model → manuals, parts, recalls | Per item | No | Free (parts sales) |
| Sortly | Business inventory, folders, barcode labels | Custom fields, date alerts | Folders | Free 100 items; $24-49/mo |
| PanelMap / Breaker Box (iOS) | Breaker panel map, legend PDF; PanelMap Pro has a sketched floor map with pinned outlets | n/a | Yes | Free + $9.99 |

### Table stakes (all in SPEC.md)

Item record with make/model/serial/dates/cost/photos (AC-C30); documents per item (AC-C37); organize by room (AC-C30, AC-H10); maintenance reminders with history (AC-C49, AC-H12); warranty expiry alerts (AC-C33, AC-H2); contacts tied to items (AC-C39); home report PDF and transfer to the next owner (AC-H13, AC-H16); local data with optional cloud backup and household sharing (AC-H14..H16); label capture to cut typing (AC-C31); recall alerts (AC-B12, later surfacing in the app).

### Nobody does this (our differentiators)

- A floor plan where tapping a room opens a 3D view textured with the user's own wall photos.
- Pipes, wires, ducts and studs recorded in-wall and viewable later.
- Warranty terms looked up once and stored with the source, not an expiry date the user types.
- House-type-aware setup that seeds floors.
- Shutoffs and breakers on the same plan as the items they control.
- Serial-number age decoding at entry.
- Android first (most of the above are iOS only or iOS best).

## Floor plan and 3D apps on Android

| App | Build the plan | Floors | 3D | Price | Known pain |
| --- | --- | --- | --- | --- | --- |
| magicplan | Manual draw or laser meter on Android (AR scan removed from Android late 2024) | Yes | Yes | $25+/project, contractor pricing | Learning curve, subscription |
| CubiCasa | Walk-through video, processed off-device (hours) | Per floor, paid | Yes | ~$23/plan | Slow, editing cumbersome |
| Polycam | Photogrammetry on Android; room mode is iOS only | iOS | Yes | $30/mo | Billing complaints |
| Planner 5D | Draw walls, auto-trace an uploaded image | Yes (laggy) | Yes | $20/mo or $60/yr | Upsell prompts, lag |
| Floor Plan Creator (Android native) | Draw rooms with typed dimensions, symbols | Unlimited, free | Free | $4.99 / $7.99 one-time | Dense UI; the Android benchmark for "draw a plan fast" |
| Home Design 3D | Draw plot and rooms | With Gold | Yes | ~$10 + IAP | Catalog limits |
| AR Plan 3D | AR tape measure per room | Per room | Yes | Free + IAP | Accuracy depends on ARCore |

Takeaways used in SPEC.md: Android has no reliable AR room capture, so the plan is drawn by hand with typed dimensions and grid snapping (Floor Plan Creator model, AC-C5..C12); unlimited floors and a free 3D view are expected; nobody textures walls with the user's own photos.

## Technical choices that came out of the research (see docs/decisions.md)

- 3D: SceneView (Filament) with meshes built from the plan; stock materials; `Matrix.setPolyToPoly` with 4 points is a full homography, so no OpenCV.
- Warranty lookup: no manufacturer API exists; a Cloudflare Worker calling the Claude API web search tool (max 3 searches, manufacturer domains) with a KV cache per make+model is the realistic route at well under $0.05 per new model.
- Serial age decoding: hand-coded per-brand rules from public references (building-center.org, InspectApedia, manufacturer pages); HomeSpy API as a possible fallback later.
- Recalls: CPSC `saferproducts.gov` REST API, free, no key.
- Weather: Open-Meteo, free, no key.
- Push: Firebase Cloud Messaging for delivery only.

## Sources

Field Log: CompanyCam pricing (pricingsaas.com, companycam.com), Raken (connecteam.com, jibble.io, Play listing), Buildertrend (projul.com, buildertrend.com help: Time Clock on Mobile, Mobile Navigation), JobTread (jobtread.com/pricing), Procore (getonecrew.com, support.procore.com), Fieldwire (fieldwire.com/pricing, support), Contractor Foreman (contractorforeman.com/pricing, Play listing), Jobber (getonecrew.com, help.getjobber.com location timers), Housecall Pro (housecallpro.com/pricing, help center GPS), busybusy (connecteam.com review, help center), ClockShark (hivedesk.com, help.clockshark.com GPS FAQ), QuickBooks Time (hivedesk.com, quickbooks.intuit.com geofencing), Workyard (workyard.com/pricing), Knowify (knowify.com/pricing), Joist (getonecrew.com), Houzz Pro (dupple.com, pro.houzz.com help), Bluebeam (drawboard.com, arkance.us), Android background location (developer.android.com/training/location/background), Play location policy (support.google.com/googleplay/android-developer/answer/9799150).

House Manual and 3D: HomeZada pricing (homezada.com), Centriq (alternativeto.net, thisoldhouse.com), Nest Egg (App Store), HomeBinder (pages.homebinder.com), HomeKeep (App Store), Fix app (App Store), Sortly (sortly.com/pricing), PanelMap and Breaker Box (App Store), magicplan review (illustrarch.com), Polycam comparison (poly.cam blog), CubiCasa (getapp.com), Planner 5D (tooliverse.ai), RoomSketcher (getapp.com), Floor Plan Creator (floorplancreator.net/pricing), AR Plan 3D (apppricinglab.com), SceneView (github.com/SceneView/sceneview-android, mvnrepository.com), Filament (github.com/google/filament), android.graphics.Matrix reference, ARCore devices and issue #1772, ML Kit release notes, Claude API web search tool docs (platform.claude.com), CPSC Recalls API (cpsc.gov), HomeSpy API (homespy.io/documentation), building-center.org, InspectApedia.

UI: Compose Material 3 releases, Navigation 3 stable post and guide, Material 3 Adaptive releases, Android design guides (layout and nav patterns, content structure, grids and units), Compose component guides (navigation bar, app bars, card, FAB, bottom sheets, dialog, search bar, chip, text fields), predictive back guide, core app quality guidelines, adaptive app quality tiers, Androidify M3 Expressive post, 9to5Google coverage of Keep and Tasks redesigns, Sortly product overview, Todoist quick add, Notion mobile home, Airbnb save-icon test (GoodUI), Tiimo sensory design.
