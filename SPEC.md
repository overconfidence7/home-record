# HomeRecord v2 — specification

Two native Android apps on one shared house model, plus a small Cloudflare backend.

- **House Manual** (homeowner): the house model (floor plan, rooms, walls, 3D, pipes and wires), every household item with make, model, dates and stored warranty, breaker panel, shutoffs and emergency, documents, reminders, home report, optional cloud backup and sharing.
- **Field Log** (contractor and crew): company and team, jobs, time clock with GPS, files and plans, photos, daily logs, tasks and punch lists, change orders with signature, schedule, timesheets and reports, offline sync, and the house model built on the job and handed to the homeowner.

PRODUCT.md says what this is for and must be read first. This file is the build contract; the builder never edits it. `docs/ui-standards.md` is part of this contract (AC-U). `docs/voice.md` governs every string.

Acceptance criteria are numbered by area: **AC-C** (shared house model and core), **AC-H** (House Manual), **AC-F** (Field Log), **AC-B** (backend), **AC-U** (UI standards). A criterion tagged `[unit]` must have a named JVM test; everything else is verified on the emulator by QA.

---

## 1. Purpose and users

See PRODUCT.md. In one line: capture what is in a house and where, tied to a model of the house, once; for homeowners (House Manual) and for the crews who build and fix houses (Field Log).

- House Manual users: homeowners 30 to 65 who own a house 10+ years old, small landlords, families sharing one record, buyers receiving a record from a contractor or inspector.
- Field Log users: owner-operators and crews of 1 to 10 in residential trades (remodelers, electricians, plumbers, HVAC, AV and low-voltage, deck and porch builders). Owner buys, lead and crew use it on the jobsite on Android phones, often with gloves and no signal.

## 2. Constraints

- Native Android: Kotlin, Jetpack Compose, Material 3 (1.4+, Expressive theme), Navigation 3, MVVM, Gradle Kotlin DSL with a version catalog. Details and approved libraries in CLAUDE.md.
- minSdk 26, compileSdk/targetSdk 37.
- Local-first: Room is the source of truth on the device. Field Log syncs with the backend; House Manual syncs only when signed in.
- Backend: Cloudflare Workers (TypeScript, Hono), D1, R2, KV. Lives in `backend/` in this repo.
- No sensitive permissions: no Accessibility, SMS, call log, background location. Location is foreground only (while in use) and captured only at punch events and when the user asks for "nearby".
- Everything that can be unit tested is unit tested without an emulator (`./gradlew testDebugUnitTest`). Compose screenshot tests render every screen to PNG on the JVM.
- No analytics or ads. Crash reporting is a later decision.

## 3. Modules

| Module | Holds |
| --- | --- |
| `:core` | Domain model, Room database, repositories, sync engine, bundle format, pure rules (plan geometry, warranty parsing, timesheet math, log assembly). No Compose. |
| `:core-ui` | Design system: theme, typography, shapes, `HomeRecordScaffold` (bottom nav + top bar), list items, empty/error/loading states, photo tiles, pickers, chips, bottom sheets, signature pad, camera screen, PDF viewer, markup canvas. Used by both apps. |
| `:house-model` | Floor plan editor and blueprint renderer (Canvas), wall photo capture and corner outline, homography warp, 3D scene (SceneView/Filament), overlays editor. Used by both apps. |
| `:house-manual` | App. `com.kurtz.homerecord.housemanual`. |
| `:field-log` | App. `com.kurtz.homerecord.fieldlog`. |
| `backend/` | Cloudflare Worker (Hono + TypeScript), D1 schema and migrations, R2 presigning, KV warranty cache, vitest tests. |

## 4. Shared house model (`:core` + `:house-model`)

### 4.1 Home and house type

| ID | Criterion |
| --- | --- |
| AC-C1 | Creating a home asks for a name, an optional address, and a house type from: Rancher, Two-story, Split-level, Raised ranch (split-foyer), Cape Cod (1.5 story), Colonial, Townhouse, Bungalow, Other. Foundation: Basement, Walkout basement, Crawlspace, Slab. Attached garage: yes/no. |
| AC-C2 `[unit]` | `HouseTypeTemplate` seeds floors from the type and foundation: Rancher = Main (+ Basement if basement/walkout); Two-story and Colonial = Main, Upper (+ Basement); Cape Cod = Main, Upper (half story flag) (+ Basement); Split-level = Lower, Main, Upper (half-level offsets); Raised ranch = Lower, Upper; Townhouse = Main, Upper (+ Lower if basement). Walkout marks the basement as having an exterior wall at grade. Attached garage seeds a Garage room on the Main floor. Floors carry an order, a name, a default ceiling height (8 ft, 9 ft for Main on Two-story/Colonial built after 2000 is NOT assumed; default 8 ft everywhere, editable). |
| AC-C3 | Floors can be added, renamed, reordered and deleted (deleting a floor with rooms asks for confirmation and names the room count). |
| AC-C4 | Units are feet and inches by default with a metric option in Settings; every length field accepts `12'6"`, `12.5`, `12 6` and `3.8 m` in metric mode `[unit]` (`LengthParser`). |

### 4.2 Floor plan editor

| ID | Criterion |
| --- | --- |
| AC-C5 | The plan editor shows one floor at a time on a grid (6 in snap; 10 cm in metric) with pinch zoom and two-finger pan. A floor switcher (chips or dropdown) is always visible. |
| AC-C6 | "Add room" places a rectangle (default 12 × 12 ft) at the viewport center with a name field focused and a room kind picker (Kitchen, Living room, Dining room, Bedroom, Bathroom, Office, Laundry, Garage, Basement, Utility, Hallway, Closet, Mudroom, Porch, Deck, Attic, Mechanical, Other). Width and length are editable by typing; handles on each edge drag to resize; the body drags to move. Snapping to the grid and to neighboring room edges is on by default. |
| AC-C7 | A room can be converted to a polygon: tap "Edit shape", tap an edge to add a corner, drag corners, tap a corner to delete it (minimum 3). L-shaped rooms are therefore possible. |
| AC-C8 `[unit]` | `WallDeriver` turns each room polygon into walls (one per edge) with length, orientation (N/E/S/W by compass heading, or a running index for diagonal edges) and an `exterior` flag when no other room on the same floor shares that edge within 4 in. Shared edges produce one wall per room (each room owns its side). |
| AC-C9 | Doors and windows can be placed on a wall (tap wall, choose Door or Window, drag along the wall, set width); stairs can be placed in a room and linked to a floor above or below; they render on the blueprint. |
| AC-C10 | Undo and redo (at least 50 steps) are available in the editor; leaving the editor autosaves. |
| AC-C11 `[unit]` | `PlanValidator` flags overlapping rooms and rooms smaller than 2 × 2 ft; the editor shows them in the error color and the warning is dismissible, never blocking. |
| AC-C12 | Rotation and process death preserve the editor state (selected room, zoom, unsaved edits). |

### 4.3 Blueprint view and rooms

| ID | Criterion |
| --- | --- |
| AC-C13 | The blueprint renders a floor as a clean plan: wall lines, room names, dimensions along the outer edges, doors as arcs, windows as triple lines, stairs as treads, north arrow. It is readable in light and dark theme and at 200% font scale. |
| AC-C14 | Tapping a room on the blueprint opens that room's page (AC-H10 / AC-F62). A long press shows a sheet with Rename, Edit shape, Delete. |
| AC-C15 | Layer toggles on the blueprint: Items (icons at their positions), Electrical (outlets, switches, lights, panel), Plumbing (pipes, shutoffs, fixtures), HVAC (ducts, registers, equipment), Low-voltage (data, coax, speaker, security), Gas, Drain and vent. Each layer draws its overlays and item markers in its own color; toggles persist per home. |
| AC-C16 | Export blueprint as a PDF (one page per floor, to scale with a scale bar and the home name) through the share sheet. |

### 4.4 Walls, wall photos and the 3D view

| ID | Criterion |
| --- | --- |
| AC-C17 | A room page lists its walls (by orientation and length). Each wall can hold photos for stages: Framing, Rough-in, Insulation, Finished. Field Log's "behind the wall" capture writes the Rough-in stage; House Manual defaults to Finished. |
| AC-C18 | "Photograph this wall" opens the camera (or picks from the gallery). After the shot, the outline screen shows the photo with four draggable corner handles (initially inset 10%), a magnifier loupe while dragging, edge lines between handles, and a "Use" button. The wall's known aspect ratio (length × height) is shown as a dashed guide. |
| AC-C19 `[unit]` | `WallTextureBuilder` uses a 4-point perspective transform (`Matrix.setPolyToPoly`, 4 points) to warp the outlined quad to a rectangle of the wall's aspect ratio, at most 2048 px on the long side, saved as JPEG in app-private storage. A unit test checks that a synthetic trapezoid maps its corners to the rectangle corners within 1 px. |
| AC-C20 | The 3D view renders the selected floor's rooms as extruded walls (floor height from the floor record) with each wall face textured by its Finished stage photo when present (a stage selector switches to Rough-in and others), untextured faces flat-shaded in the room's color, floors as a neutral plane, doors as openings, windows as lighter panels. Orbit, pan and zoom with touch; a "Reset view" button. |
| AC-C21 | Floors stack in 3D: a floor selector shows one floor with the ones below ghosted; "All floors" explodes them vertically. |
| AC-C22 | Tapping a room in 3D (or "View in 3D" on a room page) places the camera inside the room at eye height (5 ft 6 in) looking at the longest wall; drag to look around; pinch to zoom; a "Back to floor" button exits. |
| AC-C23 | Overlays (AC-C24) and item markers render in 3D on the wall or floor they belong to, with the same layer toggles as the blueprint; tapping a marker opens the item or overlay. |
| AC-C24 | The 3D view holds 30 fps on a Pixel 6a-class device for a 3,000 sq ft, three-floor home with 20 textured walls; first render in under 2 s from a warm start. QA measures with the emulator's GPU profiler and notes the result. |

### 4.5 Overlays: pipes, wires and what is behind the wall

| ID | Criterion |
| --- | --- |
| AC-C25 | An overlay is drawn on a wall surface (in the wall photo or the plain wall) or on the floor plan as a polyline or a point. Kinds: Water supply (hot/cold), Drain, Vent, Gas, Electrical wire, Low-voltage (data, coax, speaker, security, doorbell), Duct, Stud, Blocking, Junction box, Outlet, Switch, Light, Register, Valve, Cleanout, Other. Each kind has an icon and a layer color. |
| AC-C26 | Each overlay has: kind, label, note, depth (Behind drywall, In slab, In ceiling, In attic, Underground, Exposed), optional photo, optional linked item (the equipment it serves) and the stage it was recorded in. |
| AC-C27 | Overlays drawn on a wall photo at the Rough-in stage remain in the same wall position when the Finished photo is shown (overlay coordinates are stored in wall space, 0..1 by 0..1, not photo pixels) `[unit]` (`WallSpaceMapper`). |
| AC-C28 | Overlays are editable (drag points, delete) and searchable by label and kind. |

### 4.6 Items

| ID | Criterion |
| --- | --- |
| AC-C29 `[unit]` | `ItemCategory` taxonomy (categories with subtypes, each with an icon and default consumables/reminders): **Appliances** (refrigerator, freezer, range, oven, cooktop, range hood, dishwasher, microwave, disposal, washer, dryer, wine fridge); **Heating and cooling** (furnace, boiler, air handler, heat pump, AC condenser, mini-split, thermostat, humidifier, dehumidifier, ERV/HRV, whole-house fan, radiant system); **Water** (water heater, tankless heater, water softener, water filter/RO, well pump, pressure tank, sump pump, ejector pump, sewage pump, backflow preventer, PRV, expansion tank, irrigation controller); **Electrical** (breaker panel, sub-panel, generator, transfer switch, EV charger, solar inverter, surge protector); **Shutoffs** (water main, gas main, electrical main, HVAC disconnect, fixture shutoff, sprinkler, sump, cleanout, propane tank); **Safety** (smoke detector, CO detector, fire extinguisher, security panel, camera, doorbell, radon fan); **Fixtures** (faucet, toilet, shower valve, tub, sink, garbage disposal); **Doors and openers** (garage door opener, garage door, entry door, lock, gate); **Exterior** (roof, gutters, siding, windows, deck, fence, driveway, chimney); **Finishes** (paint, flooring, tile, countertop, cabinets, trim); **Networking and smart home** (router, modem, mesh node, switch, hub, NVR); **Other**. |
| AC-C30 | An item has: name, category and subtype, room (required; "Exterior" and "Whole house" are pseudo-rooms), optional position on the plan and wall, make, model, serial, purchased on, in service on, purchase price, purchased from, warranty (AC-C33), manual URL, notes, photos (data plate, item, receipt), documents, consumables, maintenance schedule, service history, linked breaker, linked shutoff, linked overlays, source (house-manual, field-log, import). |
| AC-C31 | Scanning a data plate (camera or gallery, on-device ML Kit) proposes make, model and serial with the recognized lines shown; the user can tap a line to assign it to a field. Works offline. |
| AC-C32 `[unit]` | `SerialAgeDecoder` decodes the manufacture date for at least these brands from documented serial formats: Rheem/Ruud, AO Smith, Bradford White, State, Carrier/Bryant/Payne, Trane/American Standard, Lennox, Goodman/Amana, York, Whirlpool/Maytag/KitchenAid, GE, Samsung, LG, Bosch. The decoded date fills "manufactured on" with a "decoded from serial" note and can be overridden. |
| AC-C33 | Warranty record: parts term, labor term, special terms (compressor, heat exchanger, tank), ends on (computed from in-service date when terms are years), registration required, registered, registration URL, source URL, looked up at, confidence (High/Medium/Low/Manual). When ends-on is in the past the item shows "Warranty expired <date>"; within 60 days it shows "Expires <date>". |
| AC-C34 | Warranty lookup: when make and model are present and no warranty is stored, "Look up warranty" (and an automatic attempt on first save, if online) calls the backend (AC-B10), shows the returned terms with the source link, and the user confirms, edits or discards. The result is stored with the item. "Look up again" is the only way it is fetched twice. If offline, the lookup is queued and runs when connectivity returns, then shows a notification-free badge on the item. |
| AC-C35 | Consumables per item: kind (filter, bulb, battery, cartridge, belt, salt, UV lamp, other), spec text (e.g. `16x25x1 MERV 8`), quantity, reorder URL; searchable so "16x25" finds the furnace. |
| AC-C36 | Service history per item: date, vendor/contact, description, cost, documents; sorted newest first. |
| AC-C37 | Documents: PDFs and images attached to the home, a room or an item; folders; in-app viewer with pinch zoom; share. |
| AC-C38 `[unit]` | `SearchEngine` searches items (name, make, model, serial, subtype, consumable specs), rooms, overlays, shutoffs, breakers, documents and contacts; ranked with prefix and token matches; "freezer" finds the garage freezer item and the breaker labeled Freezer. |
| AC-C39 | Contacts: name, company, trade, phone, email, notes; linked to items and service records; tap to call or email. |

### 4.7 Breaker panel (as an item with extra screens)

| ID | Criterion |
| --- | --- |
| AC-C40 | An item of subtype Breaker panel or Sub-panel opens a panel editor: panel photo, slot count (default 40), layout (North American two-column or DIN single column), main breaker amps, brand. Slots are a grid; tap a slot to edit the breaker (label, amps, poles 1/2, type Standard/GFCI/AFCI/Dual-function, rooms, items it feeds); two-pole breakers draw as one tall cell spanning both slots `[unit]` (`SlotOccupancy`); long-press makes a tandem. |
| AC-C41 | Every item with a `poweredBy` link shows "Breaker 14" on its page and the breaker shows what it feeds; the room page lists breakers feeding the room with Confirmed/Likely/Unknown dots. |
| AC-C42 | Mapping mode: with the phone charging from an outlet, flipping breakers at the panel makes the phone sound an alarm when its power drops; the user then picks the breaker and the outlet/room, which marks the link Confirmed. Keep-screen-on while armed; no alarm after leaving the screen. `[unit]` (`MappingStateMachine`). |
| AC-C43 | Legend PDF: one page per panel, one row per slot, shared through the share sheet; a header with panel name and service size. |

### 4.8 Shutoffs and emergency

| ID | Criterion |
| --- | --- |
| AC-C44 | Shutoff items carry instructions and a photo; the Emergency screen groups them Water / Gas / Electrical / HVAC / Other with mains first, in large type (24 sp instructions), full-width photos, pinch-zoom, fully offline. |
| AC-C45 | Items can link to the shutoff that isolates them ("Shutoff: Kitchen sink cold valve"). |

### 4.9 Bundle, import and export

| ID | Criterion |
| --- | --- |
| AC-C46 `[unit]` | `.homerecord` bundle: zip with `record.json` (`formatVersion` 2, every entity in this section, UUID ids, `createdAt`/`updatedAt`/`source`) plus `media/`. Reader rejects newer versions cleanly, tolerates unknown fields, guards against zip-slip. |
| AC-C47 `[unit]` | `ImportPlanner` merges a bundle into a local home: new records added, same-id records compared by `updatedAt`, conflicts (both changed) listed for the user to choose Keep mine / Use imported; never silently overwrites user-entered data; idempotent. |
| AC-C48 | Encrypted backup: export the whole home as a passphrase-encrypted bundle (AES-256-GCM, PBKDF2 210k) `[unit]` (`BundleCrypto`); restore on a fresh install from onboarding. |

### 4.10 Reminders and maintenance

| ID | Criterion |
| --- | --- |
| AC-C49 `[unit]` | `MaintenanceRule`: per-item intervals (default per subtype: furnace filter 3 months, water heater flush 12 months, softener salt 2 months, smoke detector battery 12 months, dryer vent 12 months, gutters 6 months, sump test 3 months, HVAC service 12 months), seasonal tasks by climate zone (Cold, Mixed, Hot-humid, Hot-dry, Marine), warranty-expiry reminders 60 and 7 days before, Done / Snooze (1 week, 1 month) math. |
| AC-C50 | A daily WorkManager check posts one notification summarizing due tasks (08:00 local) and deep-links to Reminders; notification permission is requested only after the first reminder exists. |

## 5. House Manual

### 5.1 Navigation

Bottom navigation: **Home**, **House**, **Items**, **Reminders**, **More**. A FAB on Items ("Add item") and on House ("Add room" while in the editor). Emergency is one tap from Home (a prominent card), from the quick settings tile and from the widget.

| Screen | Purpose and main actions |
| --- | --- |
| HM-Onboarding | Name the home, house type, foundation, garage (AC-C1) → floors seeded → "Draw your floor plan" or "Skip for now" (creates a single "Main" floor with no rooms; the House tab then shows the empty state with "Draw floor plan"). Also "Restore from a backup" and "Sign in". |
| HM-Home | Search bar at top. Emergency card (shutoff count). "Needs attention": warranties expiring in 60 days, overdue reminders, items missing make/model. "Recent" items and photos. Quick actions: Add item, Scan data plate, Photograph a wall. |
| HM-House | Blueprint of the selected floor with floor chips, layer toggles, "Edit plan", "3D" and "Export PDF" actions. Tap room → HM-Room. |
| HM-Room | Title is the room name; sections: Items (list), Walls (with photo thumbnails per stage and "Photograph"), Photos, Overlays, Breakers feeding this room, Shutoffs here, Notes. Actions: View in 3D, Add item, Edit room. |
| HM-3D | AC-C20..C24. |
| HM-WallCapture / HM-WallOutline | AC-C18. |
| HM-Items | All items grouped by category (collapsible) or by room (toggle), filter chips (category, room, warranty status), search, FAB Add item. |
| HM-Item | Hero photo, name, subtype and room chip, warranty status line with color, sections: Details (make/model/serial/dates/price/vendor), Warranty (terms, source, Look up again, Registered toggle), Consumables, Reminders, Service history, Documents, Photos, Links (Breaker, Shutoff, Overlays), Notes. Actions: Edit, Share, overflow (Move to room, Duplicate, Delete). |
| HM-ItemEdit | Full-screen form with Close and Save, category/subtype picker first, room, Scan data plate, make/model/serial, dates (date pickers), price, vendor, photos. Warranty lookup runs on first save. |
| HM-Panel, HM-Breaker, HM-Mapping | AC-C40..C43. |
| HM-Emergency | AC-C44. Large-type; "Manage shutoffs" at the bottom. |
| HM-Reminders | Due now / Upcoming / Done; Done and Snooze on each row; climate zone chips; "Add reminder". |
| HM-Documents | Folders and files; upload from files/camera; viewer. |
| HM-Contacts | List; tap to call/email; linked items. |
| HM-Report | Home report PDF: cover with address and house type, blueprint per floor, every room with its items (make, model, serial, in-service date, warranty status), shutoffs with photos, panel legend; share sheet. |
| HM-More | Reminders? (no, it is a tab) → Emergency, Breaker panels, Documents, Contacts, Home report, Import (.homerecord), Backup and restore, Account and sync, Share this house, Units, Climate zone, Pro, About. Uses ListItem rows with icons. |
| HM-Account | Sign in with Google (Credential Manager) or email code; what sync does; sign out; delete cloud copy. |
| HM-Share | Invite family (editor) or a buyer/house-sitter (viewer) by link; list of people; remove. |
| HM-Paywall | Free limits and Pro (annual or lifetime), prices from RevenueCat, Restore purchases. |

### 5.2 Acceptance criteria

| ID | Criterion |
| --- | --- |
| AC-H1 | Fresh install reaches HM-Home in at most 4 screens (onboarding with Skip) and the Home empty state offers the three quick actions. |
| AC-H2 | Home "Needs attention" lists warranties expiring within 60 days, overdue reminders and items missing make or model, each row opening the item or reminder. With nothing to show it shows one line, not an empty card. |
| AC-H3 | Search from Home finds an item by model number, a consumable spec, a breaker label and a room, and opens the right thing. |
| AC-H4 | HM-House shows the floor plan drawn in onboarding; adding a room in the editor appears on the blueprint and in HM-Room lists immediately. |
| AC-H5 | Adding a water heater with Scan data plate on a photo of a real label fills make, model and serial; saving with dates set computes the warranty end date once the lookup returns, and the item page shows terms, source link and "Looked up <date>". Reopening the app shows the same without a network call (airplane mode). |
| AC-H6 | Items list groups by category with counts, switches to by-room, filters by warranty status (Active, Expiring, Expired, Unknown) and opens any item in one tap. |
| AC-H7 | Item page links: tapping the breaker chip opens the panel scrolled to that slot with a 2 s highlight; tapping the shutoff chip opens the shutoff item; tapping a room chip opens the room. |
| AC-H8 | Photographing a wall from HM-Room and outlining it shows the photo on that wall in HM-3D within one navigation; the wall list shows the thumbnail. |
| AC-H9 | Drawing a pipe overlay on a wall photo in HM-Room and switching the wall to another stage keeps the overlay in place; the blueprint shows it on the Plumbing layer. |
| AC-H10 | HM-Room lists every item in the room with icon, name, make/model and warranty status, and "Add item" preselects the room. |
| AC-H11 | HM-Emergency opens within 1 s from the Home card, the quick settings tile (works from the lock screen after unlock) and the 2×1 home-screen widget (shows the shutoff count). |
| AC-H12 | Reminders: picking a climate zone seeds seasonal tasks; adding an item seeds its default reminders; Done rolls the next due date forward; the 08:00 notification deep-links to the list. |
| AC-H13 | Home report PDF contains every floor's blueprint and every room's items with warranty status; a 40-item home produces the PDF in under 5 s. |
| AC-H14 | Backup export and restore round-trips a home with 3 floors, 12 rooms, 25 items, 10 wall photos and 15 overlays with nothing missing (QA compares counts and spot-checks photos). |
| AC-H15 | Signing in (Google or email code) uploads the home; a second device signed in to the same account shows the same home with photos within 60 s; edits on either side merge (AC-B20); signing out keeps the local copy. |
| AC-H16 | Share: an editor invite link opens House Manual on the other phone (or the Play listing if not installed) and, after sign-in, shows the shared home; a viewer cannot edit (edit controls hidden, not just disabled). |
| AC-H17 | Import of a Field Log `.homerecord` handoff (from the file picker or a share) previews counts and conflicts, imports, and HM-Room shows the rough-in wall photos and overlays the crew recorded. |
| AC-H18 | Free tier: one home, 15 items, one panel, no cloud sync; the 16th item, the second panel or Sign in opens HM-Paywall with prices; a Test Store purchase unlocks Pro and the limits disappear without restarting. |
| AC-H19 | Every HM screen passes the AC-U rules; the tester's nightly walkthrough (`qa/product-walkthrough.md`, House Manual section) completes with no step marked blocked. |

## 6. Field Log

### 6.1 Roles and navigation

Roles: **Owner** (everything, billing), **Office** (everything except billing and deleting the company), **Lead** (jobs assigned to them: crew clock-in, logs, tasks, change orders, files), **Crew** (own time, capture, tasks assigned to them, view files), **Client** (read-only share links; no app account in v1), **Sub** (view assigned job's files and tasks, own time; later).

Bottom navigation: **Today**, **Jobs**, **Clock**, **Schedule**, **More**. The capture FAB (camera icon) sits on Today and on a Job. Owner and Office see the same tabs plus admin items under More (Team, Timesheets, Reports, Company, Billing).

| Screen | Purpose and main actions |
| --- | --- |
| FL-Welcome | Create a company or Join with a code; Sign in with Google or email code. |
| FL-Today | Clock status card (Clocked in at Kitchen Remodel since 7:02, elapsed; Clock out / Break / Switch job) or a big Clock in; today's jobs for me; my tasks due; recent photos; capture FAB. Owner: "Who's on site now" list with job and since-time. |
| FL-Jobs | List grouped Active / Upcoming / Done with client, address, next date, crew avatars; search; filter; FAB New job. |
| FL-Job | Top app bar with job name, subtitle client; tabs: **Overview** (address with map link, dates, crew, status, phase, totals: hours this week, photos, open tasks), **Photos**, **Log** (daily logs), **Files**, **Tasks**, **Time** (punches for this job), **House** (house model: plan, rooms, walls, items, handoff), **Changes** (change orders). FAB capture on Photos; "New" on each tab. |
| FL-JobEdit | Client (name, phone, email), address (autocomplete via on-device Geocoder; stores lat/lng), rooms (quick-add chips), start/end dates, crew, phase list, budget hours (optional), notes. |
| FL-Clock | Clock in/out/break/switch with job picker, GPS fix indicator, note; "My timesheet" (this week, by day, totals, flags); Lead/Owner: "Crew clock-in" (multi-select crew, one punch each). |
| FL-Capture | CameraX full screen, 80 dp shutter, volume keys, job/room/phase chips above the shutter (remembered), flash, flip, gallery import; Note and Voice buttons; after a shot: thumbnail with "Annotate". |
| FL-Photo | Full photo, zoom; annotate (arrow, circle, rectangle, text, freehand, measure label); tags (room, phase, before/after pair); caption; share; delete. |
| FL-Log | Daily log for a date: assembled sections (Crew on site with hours from punches; Work done from notes and voice transcripts; Photos; Tasks completed; Materials delivered; Delays and reasons; Weather auto; Visitors; Safety notes; Notes for client), each editable; Preview PDF; Send (share sheet or email to client/office). |
| FL-Files | Folders (Plans, Permits, Contracts, Specs, Other), upload from Files or camera scan (document scanner), each file with name, size, version, "Available offline" toggle; viewer with pinch zoom and page thumbnails; Markup (pen, arrow, rectangle, text, highlight) saved as a new version; version history. |
| FL-Tasks | Per job and "My tasks" (Today): title, assignee, due, room, photo, status Open/Done, pin on plan; punch list is a filter (`punch` tag); swipe to complete; reorder. |
| FL-ChangeOrder | Title, description (voice-to-text), photos, line items with price, total, client name; "Sign on phone" opens the signature pad; status Draft / Signed / Declined; PDF; share link for remote approval (AC-F40). |
| FL-Schedule | Week view: rows per crew member, cells per day with job chips; drag a job chip onto a day (Owner/Office/Lead); crew sees their own week; push notification the evening before a change (AC-B30). |
| FL-Timesheets (Owner/Office) | Week picker; per person: days, hours, OT, flags (missing clock-out, far from job, over 14 h, no break); tap a punch to edit with a required reason; Approve person / Approve all; Export CSV; QuickBooks export file (IIF/CSV, AC-F55). |
| FL-Reports | Hours by job and by person (date range), photo report PDF for a job and range, job summary (hours vs budget, change orders, tasks). |
| FL-Team | Members with role, invite code / link, change role, remove; rates (optional, Owner only). |
| FL-Company | Name, logo, phone, email, address, default OT rule, break rule, require GPS at punch (on/off), punch distance threshold (default 500 ft), photo on punch (off by default). |
| FL-House (per job) | The house model for the job's home: floor plan editor, blueprint, rooms, walls with Framing/Rough-in/Finished stages ("Behind the wall" capture), overlays, installed items with make/model/serial/warranty, 3D. Handoff: export `.homerecord` or send to the homeowner's House Manual account by email (AC-B40). |
| FL-More | Team, Timesheets, Reports, Company, Billing (Owner), Materials and receipts, Clients, Settings (units, offline storage, notifications), Account, About. |
| FL-Materials | Receipt capture (document scanner) → job, vendor, amount, category, date, note; list by job; CSV export. |
| FL-Paywall | 14-day trial, Solo ($39/mo) and Crew up to 10 ($79/mo), prices from RevenueCat; Owner only. |

### 6.2 Acceptance criteria

**Company, team, auth**

| ID | Criterion |
| --- | --- |
| AC-F1 | Creating a company (name, trade, phone) after sign-in makes the user Owner and lands on FL-Today with an empty state that says to add a job or invite the crew. |
| AC-F2 | Invite: an 8-character code and a link; a second phone joins with the code after sign-in and appears in FL-Team as Crew within 30 s; the Owner can change the role and remove the member. |
| AC-F3 | Role gates: Crew cannot see FL-Timesheets, FL-Team, FL-Reports, FL-Company or other people's punches; Lead can clock in the crew only on jobs they are assigned to; UI hides what the role cannot do (no greyed-out admin menus). |
| AC-F4 | Sign-in persists across restarts; sign-out clears cached company data after confirmation. |

**Jobs**

| ID | Criterion |
| --- | --- |
| AC-F5 | New job with client, address and rooms saves offline and syncs; address resolves to coordinates (shown as a map thumbnail link) when online, later if not. |
| AC-F6 `[unit]` | `JobOrdering`: Active jobs by next scheduled day then name; Upcoming by start date; Done by end date desc; archived hidden behind a toggle. |
| AC-F7 | FL-Job Overview shows hours this week (from punches), photo count, open task count, next scheduled day and crew; each stat opens its tab. |

**Time clock**

| ID | Criterion |
| --- | --- |
| AC-F8 | Clock in captures time, job, and a GPS fix (fine location, while-in-use) with accuracy; the button shows "Getting location…" at most 5 s then proceeds with the best fix or none (noted). Punches save offline and sync later. |
| AC-F9 `[unit]` | `PunchDistance`: if the fix is farther than the company threshold from the job address, the punch is flagged `farFromJob` and the app asks for a reason (picker: Picking up materials, Supply house, Shop, Other + text); the reason is stored with the punch. |
| AC-F10 | Break start/end and Switch job create the right punch pairs; clock out closes the open shift; the Today card shows elapsed time ticking. |
| AC-F11 | Lead crew clock-in: select crew members, one tap, each gets a punch with the lead's location and `clockedInBy`. |
| AC-F12 `[unit]` | `ShiftFlags`: missing clock-out after 14 h (auto-closed at 14 h with a flag), far-from-job, no break on a shift over 6 h (when the company break rule is on), overlapping shifts, edited punches. |
| AC-F13 `[unit]` | `TimesheetMath`: daily and weekly totals, unpaid break deduction, weekly OT over 40 h (optional daily OT over 8 h and double over 12 h, California preset), rounding off by default (optional 15-minute rounding). |
| AC-F14 | FL-Timesheets shows a week per person with flags; editing a punch requires a reason and keeps an audit entry (who, when, before/after) visible on the punch; Approve locks the week for that person (crew sees "Approved"). |
| AC-F15 | CSV export has one row per shift (person, job, in, out, break minutes, hours, OT hours, flags, GPS in/out) and opens the share sheet; a QuickBooks-ready timesheet CSV (customer:job, service item, hours, date, employee) is a second option. |
| AC-F16 | Owner's "Who's on site now" lists open shifts with job and start time and a map pin of the clock-in location; refreshes on pull. |
| AC-F17 | A home-screen widget (2×2) shows clock status and a Clock in / Clock out button that opens FL-Clock with the last job preselected. |
| AC-F18 | Location permission is requested only on the first clock-in with an explanation; denial still allows clocking in (punch marked "no location") when the company does not require GPS; when it requires GPS the app explains and offers Settings. |

**Files and plans**

| ID | Criterion |
| --- | --- |
| AC-F19 | Upload a 20 MB PDF plan from Files: progress shown, resumable on reconnect, appears for other members within 60 s; the viewer opens it with page thumbnails and pinch zoom; the first page renders in under 2 s. |
| AC-F20 | "Available offline" downloads the file; in airplane mode it opens; files not marked show a download prompt. Storage used is listed in Settings with a "Free up" action. |
| AC-F21 | Markup on a PDF page (pen, arrow, rectangle, text, highlight) saves as a new version with the author and time; version history lists and opens each; the original is never modified. |
| AC-F22 | Document scanner (camera) captures a multi-page document into a PDF in Files. |
| AC-F23 | Folders can be added and files moved and renamed; search by file name. |

**Photos and capture**

| ID | Criterion |
| --- | --- |
| AC-F24 | FL-Capture takes a photo on shutter tap and on volume up/down; each photo is tagged with the job, selected room and phase, time and GPS (if permitted) and appears in the job's Photos grouped by day. |
| AC-F25 | Before/after: mark a photo "Before"; taking the "After" shows a 30% ghost overlay of the Before shot to line it up; the pair shows side by side in the gallery and in the daily log. |
| AC-F26 | Annotate: arrow, circle, rectangle, freehand, text and a measure label; saved as a copy with the original kept; undo. |
| AC-F27 | Notes (typed) and voice memos (up to 3 min) attach to the job and day; voice memos are transcribed on device (Android SpeechRecognizer, best effort) and the text is editable. |
| AC-F28 | Photos upload in the background with a queue indicator (n pending); a job gallery share link (AC-B35) gives the client a web gallery; photo report PDF (date range, up to 4 per page with captions) from FL-Reports. |
| AC-F29 | Camera works on single-camera emulators and devices (lens limiter) and the permission-denied state explains and links to Settings. |

**Daily log**

| ID | Criterion |
| --- | --- |
| AC-F30 `[unit]` | `LogAssembler` builds the day's draft from punches (crew and hours), notes and transcripts (Work done, in order), photos (count and thumbnails), tasks completed that day, change orders signed, materials logged, weather snapshot; every section editable; re-assembling after edits never overwrites a manually edited section. |
| AC-F31 | Weather: the day's high/low/conditions/precipitation at the job location from the backend weather proxy (AC-B32) when online, cached; "No weather (offline)" otherwise. |
| AC-F32 | Delay reasons picker (Weather, Materials, Inspection, Client, Other) with note; delays appear in a job timeline in FL-Reports. |
| AC-F33 | Send: PDF with company logo, job, date, sections, photos; share sheet or email to the client and office addresses stored on the job; the log is marked Sent with time and recipients; a sent log can be re-sent. |

**Tasks**

| ID | Criterion |
| --- | --- |
| AC-F34 | Tasks have title, assignee, due date, room, photo, note, punch flag; "My tasks" on Today shows due today and overdue; swipe completes with Undo. |
| AC-F35 | Pin a task on the floor plan (FL-House) or on a plan file page; the pin opens the task. |
| AC-F36 | Task changes sync; assigning a task notifies the assignee (push, AC-B30). |

**Change orders**

| ID | Criterion |
| --- | --- |
| AC-F37 | Create from a job: title, description (keyboard or voice), photos, line items with quantity and price, total `[unit]` (`MoneyMath`, cents), client name. |
| AC-F38 | Sign on phone: full-screen signature pad, "Clear", "Sign"; the signature image, the signer name and time are stored; status becomes Signed; a Signed order is read-only (duplicate to change). |
| AC-F39 | PDF includes the company, job, description, photos, line items, total, signature and time; share sheet. |
| AC-F40 | Remote approval: a share link opens a web page (backend, AC-B36) where the client views and signs; the app shows Signed within 60 s. |

**Schedule**

| ID | Criterion |
| --- | --- |
| AC-F41 | Week view with crew rows; Owner/Office/Lead assign a job to a person and day by tap (picker) or drag; crew see their own week on Today ("Tomorrow: Smith deck, 7:00") and in Schedule. |
| AC-F42 | Changing tomorrow's assignment sends the affected crew a push notification (AC-B30); the notification opens the day. |
| AC-F43 | Conflicts (same person, two jobs, same day) are allowed but shown with a warning badge. |

**Materials, clients, reports**

| ID | Criterion |
| --- | --- |
| AC-F44 | Receipt capture → job, vendor, amount (OCR suggestion from the receipt total when found), category, note; list per job with totals; CSV export. |
| AC-F45 | Clients list (from jobs) with phone/email tap-to-act and their jobs. |
| AC-F46 `[unit]` | `HoursReport`: hours by job and by person for a date range, with OT split; totals match FL-Timesheets for the same range. |
| AC-F47 | Job summary report: hours vs budget, change order total, tasks open/done, days on site, delays; PDF. |

**House model on the job and handoff**

| ID | Criterion |
| --- | --- |
| AC-F48 | FL-House for a job uses the same editor and blueprint as House Manual (AC-C5..C16); rooms added on FL-JobEdit appear on the plan as unplaced rooms to drag into position. |
| AC-F49 | "Behind the wall": pick room → wall → camera; the outline step writes the Rough-in stage texture; overlays drawn on it carry stage Rough-in; a second capture on the same wall after drywall writes Finished. |
| AC-F50 | Installed items entered on the job (water heater, panel changes, fixtures) carry make/model/serial and get the warranty lookup; they show in FL-House rooms and in the handoff. |
| AC-F51 | Handoff: export `.homerecord` (AC-C46) through the share sheet, or "Send to homeowner" by email (AC-B40) which creates a claim link; House Manual imports either (AC-H17). The handoff summary lists rooms, walls with photos, overlays, items, documents, and change orders included. |

**Sync and offline**

| ID | Criterion |
| --- | --- |
| AC-F52 | In airplane mode every capture, punch, task, log edit and change order works; a pending-sync count shows on Today; reconnecting syncs everything within 60 s with no duplicates. |
| AC-F53 `[unit]` | `SyncEngine`: each change is a row in `outbox` (entity, id, op, payload, updatedAt, deviceId); push sends in order; pull applies server changes newer than the local `updatedAt`; last-writer-wins per field for scalars, union for lists (photos, tasks), server-authoritative for punches edited by Owner/Office; a conflict log is kept. |
| AC-F54 | Media (photos, files) upload via presigned R2 URLs with retry and resume; downloads cache to app storage; cache size cap configurable. |
| AC-F55 | Two phones on the same company: a photo taken on one appears on the other's job gallery within 60 s; a punch edit by the Owner appears on the crew phone with the audit note. |

**Billing**

| ID | Criterion |
| --- | --- |
| AC-F56 | Trial: 14 days from company creation with everything on; after that Owner sees FL-Paywall; Solo allows 1 member, Crew allows 10; crew phones see "Ask your owner to renew" and read-only access to their own timesheet. Purchases through RevenueCat (Owner only), entitlement `fieldlog_solo` / `fieldlog_crew`, verified on the backend from RevenueCat's webhook (AC-B50). |
| AC-F57 | Every FL screen passes the AC-U rules; the tester's nightly walkthrough (`qa/product-walkthrough.md`, Field Log section) completes with no step marked blocked. |

## 7. Backend (`backend/`, Cloudflare)

Stack: Workers + Hono (TypeScript), D1 (SQLite) with migrations, R2 for media and files, KV for the warranty cache and rate limits, Cron Triggers for cleanup. Tests with vitest and `@cloudflare/vitest-pool-workers`. Deployed with `wrangler deploy` by the builder once Nate provides a Cloudflare API token (BLOCKERS); until then the builder runs the Worker locally with `wrangler dev` in tests only.

| ID | Criterion |
| --- | --- |
| AC-B1 | Auth: Sign in with Google (ID token verified server-side) and email code (6 digits, 10 min, sent through Resend; dev mode logs the code). Sessions are JWTs (30 days) in a secure store on the device; refresh on use. `[unit]` token verification. |
| AC-B2 | Tenancy: every row belongs to a `companyId` (Field Log) or a `homeId` (House Manual); every request is scoped by the session's memberships; a request for another tenant returns 404, never 403 with details. `[unit]`. |
| AC-B3 | Roles enforced server-side exactly as AC-F3 (a Crew token cannot read other members' punches or approve timesheets). `[unit]`. |
| AC-B10 | `POST /lookup/warranty` with `{make, model, category, serial?}`: returns `{partsYears, laborYears, specialTerms[], registrationRequired, registrationUrl, manualUrl, sourceUrl, confidence, summary}`; results cached in KV by normalized make+model for 180 days so a model is looked up once across all users; implemented with the Claude API web search tool restricted to manufacturer and retailer domains, max 3 searches, structured output; rate limited per account (20/day free, 200/day Pro); cost logged per call. `[unit]` for normalization and cache keys. |
| AC-B11 | `POST /lookup/dataplate` (optional): image → `{make, model, serial, manufacturedOn}` through Claude vision, used when on-device OCR finds nothing; same rate limit bucket. |
| AC-B12 | `GET /recalls?make=&model=` proxies the CPSC recalls API with a 24 h cache. |
| AC-B20 | Sync: `POST /sync/push` (batch of outbox rows) and `GET /sync/pull?since=` per tenant; server assigns a monotonically increasing `serverSeq`; conflict rules match AC-F53; idempotent on retry (dedupe by `(deviceId, outboxId)`). `[unit]`. |
| AC-B21 | Media: `POST /media/presign` returns an R2 presigned PUT (10 min) and the final GET URL (signed, 7 days); objects keyed `tenant/entity/id/filename`; a cron removes orphaned objects older than 30 days. |
| AC-B30 | Push notifications through Firebase Cloud Messaging (delivery only; no other Firebase use): device tokens registered per member; events: task assigned, schedule changed for tomorrow, change order signed, timesheet approved. |
| AC-B32 | `GET /weather?lat=&lng=&date=` proxies Open-Meteo (no key) with a 6 h cache. |
| AC-B35 | Share links: `GET /share/gallery/:token` serves a read-only web gallery (job name, photos by day, captions) for a job; `GET /share/log/:token` the daily log; tokens revocable from the app. |
| AC-B36 | `GET /share/co/:token` serves the change order with a signature pad; `POST` stores the signature and marks Signed; the app sees it on next pull. |
| AC-B40 | Handoff: `POST /handoff` stores the bundle in R2 and emails the homeowner a claim link; claiming with a House Manual account copies the home into their tenant; links expire in 30 days. |
| AC-B50 | RevenueCat webhook updates company entitlement; `GET /me` returns entitlement and trial end so the apps gate consistently; House Manual Pro entitlement likewise. |
| AC-B60 | Every endpoint has a vitest test; `npm test` in `backend/` is green; schema migrations run with `wrangler d1 migrations apply`; secrets (`CLAUDE_API_KEY`, `RESEND_API_KEY`, `FCM_SERVICE_ACCOUNT`, `REVENUECAT_WEBHOOK_SECRET`, `JWT_SECRET`, `GOOGLE_CLIENT_ID`) come from `wrangler secret`, never from source. |

## 8. UI standards (AC-U)

`docs/ui-standards.md` holds the full numbered checklist; these are the global criteria QA checks on every screen every night.

| ID | Criterion |
| --- | --- |
| AC-U1 | Every root destination is in a bottom navigation bar with icon and label; the selected item is highlighted; reselecting it returns to the root of that tab. On width ≥ 600 dp the bar becomes a navigation rail. |
| AC-U2 | Every non-root screen has a top app bar with a back arrow at the start that returns to the previous screen with its state; system back and the back gesture do the same; predictive back previews the previous screen. No screen is a dead end. |
| AC-U3 | Homogeneous collections are `LazyColumn`s of `ListItem`s (leading icon or thumbnail, headline, supporting text, trailing meta or chevron), not stacks of cards. Cards are used only for summaries and highlights. |
| AC-U4 | Each list has an empty state (icon, one headline, one sentence, one action), a loading state and an error state. |
| AC-U5 | One FAB per screen at most, bottom-end, for the primary create action; lists keep 88 dp bottom padding so it never covers the last row. |
| AC-U6 | Create/edit of more than two fields is a full-screen route with Close and Save in the top bar; two fields or fewer may use a bottom sheet; destructive actions confirm with an AlertDialog. |
| AC-U7 | Links between records are chips on the detail screen; tapping a chip navigates; the target shows the reverse link. |
| AC-U8 | Material 3 theme with dynamic color on Android 12+, light and dark, no hardcoded colors, typography from the theme, 8 dp grid, 16 dp margins, touch targets ≥ 48 dp (≥ 56 dp on Field Log capture and clock screens, shutter ≥ 72 dp). |
| AC-U9 | Edge-to-edge with correct insets on every screen; keyboard never covers the focused field; rotation keeps state; 200% font scale clips nothing. |
| AC-U10 | Every icon-only control has a content description; TalkBack reads list rows in order. |
| AC-U11 | Screens render in under 16 ms per frame while scrolling a 200-row list on the emulator (no jank visible); cold start under 1.5 s. |
| AC-U12 | Every screen has a Compose preview and a screenshot test; `./gradlew updateDebugScreenshotTest` produces PNGs under `<module>/src/debug/screenshotTest/reference/` that are committed. |

## 9. Data model (`:core`)

All ids are UUID strings. Every entity has `createdAt`, `updatedAt` (epoch millis), `deletedAt?` (soft delete for sync), `source`. Tenancy: `homeId` for house-model entities, `companyId` for Field Log entities; a Job has a `homeId`.

House model: `Home` (name, address, lat/lng, houseType, foundation, attachedGarage, yearBuilt, climateZone, units, squareFeet), `Floor` (homeId, order, name, ceilingHeightIn, halfLevel, elevationIn), `Room` (floorId, name, kind, polygon[], color, notes), `Wall` (roomId, index, orientation, lengthIn, heightIn, exterior, sharedWithWallId?), `WallPhoto` (wallId, stage, originalPath, texturePath, corners[4], takenAt), `Opening` (wallId, kind door/window, offsetIn, widthIn, heightIn), `Stair` (roomId, toFloorId, polygon), `Overlay` (homeId, roomId?, wallId?, kind, label, note, depth, points[] in wall or plan space, stage, photoPath?, itemId?), `Item` (homeId, roomId, category, subtype, name, make, model, serial, manufacturedOn, purchasedOn, inServiceOn, price, vendor, manualUrl, notes, planX/planY?, wallId?, poweredByBreakerId?, shutoffItemId?), `Warranty` (itemId, partsMonths, laborMonths, specialTerms json, endsOn, registrationRequired, registered, registrationUrl, sourceUrl, lookedUpAt, confidence, summary), `Consumable`, `ServiceRecord`, `Document` (homeId, roomId?, itemId?, folder, name, path, mime, size), `Photo` (homeId, roomId?, itemId?, jobId?, stage, path, thumbPath, takenAt, lat/lng?, caption, tags[], beforeOfPhotoId?, annotations json), `Contact`, `MaintenanceTask`, `Panel` (itemId, slotCount, layout, mainAmps, brand, photoPath), `Breaker` (panelId, slots[], poles, amps, type, label, confidence), `BreakerLink` (breakerId, roomId?, itemId?, endpointLabel?, confidence).

Field Log: `Company` (name, trade, logoPath, phone, email, address, settings json: otRule, breakRule, requireGps, punchDistanceFt, photoOnPunch), `Member` (companyId, userId, name, role, rateCents?, fcmToken?), `Client`, `Job` (companyId, homeId, clientId, name, address, lat/lng, status, phases[], startDate, endDate, budgetHours?, officeEmails[], notes), `JobCrew` (jobId, memberId), `Punch` (companyId, memberId, jobId, kind in/out/breakStart/breakEnd, at, lat/lng/accuracy?, farFromJob, reason?, clockedInBy?, note, edits json[]), `Shift` (derived, cached), `TimesheetApproval` (memberId, weekStart, approvedBy, at), `JobFile` (jobId, folder, name, path, r2Key, mime, size, version, parentFileId?, author, offline), `Note` (jobId, day, text, voicePath?, transcript?), `DailyLog` (jobId, day, sections json, weather json, status, sentAt, recipients[], pdfPath), `Task` (jobId, title, assigneeId, due, roomId?, photoId?, note, done, punch, planPin json?, fileId?/page/x/y), `ChangeOrder` (jobId, title, description, lines json, totalCents, clientName, status, signaturePath, signedAt, signedBy, pdfPath, shareToken), `Assignment` (jobId, memberId, date), `Material` (jobId, vendor, amountCents, category, date, receiptPath, note), `ShareLink` (kind, jobId, token, revoked), `Outbox` (entity, entityId, op, payload, updatedAt, deviceId, sent), `SyncState` (lastServerSeq).

## 10. Milestones

| Milestone | What a user can do after it |
| --- | --- |
| **M0 Foundation** | Both apps launch with the real navigation shells, theme, design system, screenshot tests; backend skeleton with auth and `/me`; CI builds testing APKs. |
| **M1 House model** | Create a home, draw floors and rooms, see the blueprint, open rooms (both apps). |
| **M2 Items and warranty (House Manual usable)** | Add any household item to a room, scan data plates, warranty looked up and stored, documents, search, Home dashboard, Items tab. |
| **M3 Field Log core (Field Log usable)** | Company and team, jobs, time clock with GPS, timesheets, files and plans, photos, daily log, tasks, offline sync. |
| **M4 Walls, overlays and 3D** | Photograph and outline walls, pipes and wires overlays, 3D view, behind-the-wall on the job. |
| **M5 Field Log complete** | Change orders with signature and remote approval, schedule and push, materials, reports, share links, handoff, billing and trial. |
| **M6 House Manual complete** | Breaker panel and mapping mode, shutoffs and emergency (tile, widget), reminders, home report, backup, sign-in and sync, sharing, import of handoff, Pro. |
| **M7 Ship** | Polish checklist S1..S10 in CLAUDE.md, store listings, privacy, release builds. |

## 11. Pricing

- House Manual: free (1 home, 15 items, 1 panel, no sync); Pro $24.99/yr or $59 lifetime (unlimited, sync and sharing, home report).
- Field Log: 14-day trial; Solo $39/mo (1 member); Crew $79/mo (up to 10 members). Flat, no per-seat charge. Prices live in Play Console and RevenueCat; never hardcoded.

## 12. Out of scope for v2

AR room scanning; furniture catalogs; estimates and invoicing (Field Log integrates with QuickBooks for time only); client accounts in the app (share links instead); background location and geofence auto clock-in; iOS; Wear OS; multi-language (English only); smart-home integrations; AI-written reports beyond the log assembly rules (voice transcription is on-device).
