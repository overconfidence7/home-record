# HomeRecord — specification

Two native Android apps in one repository, built on one shared data model (the "home record"):

- **House Manual** (homeowner): breaker panel map, shutoff map and emergency screen, equipment record, one-person circuit mapping.
- **Field Log** (contractor): jobs, daily capture (photos, notes, voice recordings), behind-the-wall photos, template daily report, close-out handoff export.

The link between them is the `.homerecord` bundle: Field Log exports a job's close-out package, House Manual imports it and the homeowner starts with a pre-filled record.

Source planning docs live in `AndroidStudioProjects/AndroidAppIdeas/` (house-manual, field-log, shared/home-record-format.md). This file is the build contract; the builder never edits it.

## 1. Purpose

Homeowners do not know which breaker feeds what, where the shutoffs are, or how old the water heater is until something breaks. Contractors photograph walls before close-up and write daily logs, but that information never reaches the homeowner in usable form. HomeRecord captures it once, on the jobsite or at home, and keeps it on the phone.

## 2. Target users

- **House Manual:** homeowners 30–60 who do some DIY and own a house 10+ years old. Secondary: small landlords, families sharing a house record.
- **Field Log:** owner-operators and crews of small residential contractors (remodelers, electricians, AV/low-voltage, deck builders) who work on Android phones, often with gloves and poor signal.

## 3. Constraints (apply to every milestone)

- Native Android: Kotlin, Jetpack Compose, Material 3, single activity per app, MVVM, Gradle Kotlin DSL with a version catalog.
- minSdk 26, compileSdk/targetSdk 37 (Android 17, the current stable platform).
- v1 is fully on-device: Room for structured data, app-private storage for photos, audio and exports. No accounts, backend, analytics, ads or crash reporting.
- Every feature's logic lives in a testable class. JUnit + Robolectric + Compose UI tests run with `./gradlew testDebugUnitTest` and need no emulator.
- No sensitive permissions (no Accessibility, SMS, call log, background location).

## 4. Milestones and features

### M1 — House Manual, smallest usable app

| ID | Feature | Acceptance criteria |
| --- | --- | --- |
| F1 | Onboarding: create home, quick-add rooms | AC-1..AC-4 |
| F2 | Breaker panel map: photo, slot grid, breaker editor | AC-5..AC-12 |
| F3 | Breaker ↔ room/endpoint links with confidence | AC-13..AC-16 |
| F4 | Search and room lookup | AC-17..AC-21 |
| F5 | Printable panel legend (PDF) | AC-22..AC-24 |
| F6 | Shutoffs and emergency screen | AC-25..AC-31 |
| F7 | Quick Settings tile | AC-32..AC-34 |
| F8 | Mapping mode (power-loss alarm) | AC-35..AC-41 |
| F9 | Equipment record with data-plate OCR | AC-42..AC-50 |

### M2 — Field Log capture and the handoff

| ID | Feature | Acceptance criteria |
| --- | --- | --- |
| F10 | Jobs | AC-51..AC-54 |
| F11 | Daily capture: photos, typed notes, voice recordings | AC-55..AC-62 |
| F12 | Behind-the-wall capture per room and wall | AC-63..AC-66 |
| F13 | Template daily report PDF | AC-67..AC-70 |
| F14 | `.homerecord` export (Field Log) and import (House Manual) | AC-71..AC-79 |

### M2.5 — Panel profile and insights (added 2026-10-16)

| ID | Feature | Acceptance criteria |
| --- | --- | --- |
| F20 | Panel profile: kind, service size, brand/model, tandem listing, main breaker, feed breaker; room kinds | AC-101..AC-105 |
| F21 | Panel insights: capacity, tandem and circuit-count checks, hazard-brand warnings, GFCI/AFCI expectations, dedicated-circuit checklist | AC-106..AC-113 |
| F22 | Load estimate (NEC 220.82 optional method) against service size | AC-114..AC-118 |

### M3 — Keep, protect, pay

| ID | Feature | Acceptance criteria |
| --- | --- | --- |
| F15 | Reminders: seasonal and per-equipment tasks | AC-80..AC-85 |
| F16 | Emergency home-screen widget | AC-86..AC-87 |
| F17 | House Manual backup export/import (passphrase-encrypted `.homerecord`) | AC-88..AC-91 |
| F18 | Free limits and Pro entitlement (RevenueCat) | AC-92..AC-96 |
| F19 | Multiple panels, consumables per equipment | AC-97..AC-100 |

## 5. Screens

### House Manual (`:house-manual`, applicationId `com.kurtz.homerecord.housemanual`)

| Screen | Purpose | Main actions |
| --- | --- | --- |
| HM-Onboarding | First launch: name the home, pick rooms | Enter home name; toggle rooms from quick-add list (Kitchen, Living Room, Dining Room, Primary Bedroom, Bedroom 2, Bedroom 3, Bathroom, Primary Bath, Garage, Basement, Attic, Laundry, Office, Hallway, Exterior); add custom room; Continue → HM-Panel |
| HM-Home | Hub after onboarding | Search field; cards: Panel (slot count, % labeled), Emergency (shutoff count), Equipment (count), Rooms; overflow → Settings |
| HM-Panel | The panel map | Take/replace panel photo; set slot count and layout; grid of slots (two columns for North American, one list for DIN); tap slot → HM-Breaker; long-press slot → set as tandem; "Legend PDF"; "Mapping mode" |
| HM-Breaker | Edit one breaker | Label, amps, poles (1/2), type (Standard, GFCI, AFCI, Dual-function), rooms (multi-select), endpoints (add: kind + label + room), confidence (Confirmed/Likely/Unknown), delete |
| HM-Search | Results for a query | List of matching breakers, endpoints, equipment, shutoffs; tap → highlight slot on HM-Panel or open item |
| HM-Room | One room | Breakers feeding this room (with confidence), endpoints, equipment, shutoffs in this room; rename room; delete room |
| HM-Rooms | All rooms | List; add room; tap → HM-Room |
| HM-Shutoffs | All shutoffs | List grouped by kind; add → HM-ShutoffEdit; tap → HM-ShutoffEdit |
| HM-ShutoffEdit | Add/edit a shutoff | Kind (Water main, Gas main, Electrical main, HVAC disconnect, Fixture valve, Sump, Cleanout, Other), room, photo, instructions (free text), delete |
| HM-Emergency | Large-type emergency view | Sections Water / Gas / Electrical / Other with photo and instructions; tap photo → full screen; "Call 911" is NOT included (out of scope) |
| HM-Mapping | One-person circuit mapping | Shows charging state; "Start" arms the alarm; alarm screen: big "POWER LOST" + Stop; after stop: pick the breaker you flipped, pick/create the endpoint, Save |
| HM-Equipment | All equipment | List with age and warranty badge; add → HM-EquipmentEdit; tap → HM-EquipmentEdit |
| HM-EquipmentEdit | Add/edit equipment | Category, make, model, serial, room, installed on, manufactured on, warranty ends, label photo, "Scan data plate" → OCR fills make/model/serial for confirmation; notes; delete |
| HM-Import (M2) | Import a `.homerecord` | Pick file; preview counts per entity; conflicts list; Import |
| HM-Settings | Housekeeping | Export backup (M3), Import, climate zone (M3), about, version |

### Field Log (`:field-log`, applicationId `com.kurtz.homerecord.fieldlog`)

| Screen | Purpose | Main actions |
| --- | --- | --- |
| FL-Jobs | All jobs | List sorted by last activity; add → FL-JobEdit; tap → FL-Job |
| FL-JobEdit | Add/edit a job | Name, address, client name, client phone/email, rooms (quick-add list, same as House Manual), archive |
| FL-Job | One job | Entries grouped by day, newest first; big "Capture" button; "Today's report"; "Behind the wall"; "Handoff" |
| FL-Capture | Fast capture | Full-screen camera (CameraX) with shutter, volume-key shutter, glove-sized buttons; "Note" opens typed note; "Voice" starts/stops recording with elapsed time; each capture becomes an entry on the current job and day |
| FL-Entry | Edit one entry | Photo/audio preview, caption or note text, room tag, delete |
| FL-BehindWall | Guided wall capture | Pick room → pick wall (N/E/S/W or custom label) → camera; each photo saved with stage = behind-wall, room and wall; running count per wall |
| FL-Report | Daily report preview | Date picker; auto-filled: job, date, weather (manual entry in v1), crew count, hours, work completed (from notes), delays (picker + text), materials, next steps; every field editable; "Make PDF" → share sheet |
| FL-Handoff | Close-out export | Checklist of what will be included (rooms, behind-wall photos, equipment, finishes, panel changes); "Export .homerecord" → share sheet; "Export PDF" |
| FL-Settings | Company details for report header | Company name, phone, email, logo photo |

## 6. Data model

All entities carry `id: String (UUID)`, `createdAt`, `updatedAt` (epoch millis) and `source` (`house-manual`, `field-log`, `import`). Room database `homerecord.db` in `:core`; both apps use it with their own instances.

| Entity | Fields | Notes |
| --- | --- | --- |
| Home | name, address?, yearBuilt?, climateZone?, squareFeet? (M2.5) | House Manual has exactly one Home in v1. Field Log creates one Home per Job. |
| Room | homeId, name, floor?, kind (M2.5: `KITCHEN`, `BATHROOM`, `BEDROOM`, `LIVING`, `DINING`, `FAMILY`, `OFFICE`, `HALLWAY`, `CLOSET`, `LAUNDRY`, `GARAGE`, `BASEMENT`, `CRAWL_SPACE`, `ATTIC`, `EXTERIOR`, `UTILITY`, `OTHER`) | Quick-add names map to a kind; the user can change it. Rules in F21 key off `kind`, never the name. |
| Wall | roomId, label | "N", "E", "S", "W" or free text |
| Panel | homeId, name, locationRoomId?, photoPath?, slotCount, layout (`NA_NUMBERED`, `DIN`), **M2.5:** kind (`MAIN`, `SUB`), ratingAmps? (service size for MAIN, feed size for SUB: 60, 100, 125, 150, 200, 225, 400 or custom), brand?, model?, maxCircuits? (from the panel label), tandemPolicy (`ALLOWED`, `NOT_ALLOWED`, `UNKNOWN`), hasMainBreaker (Boolean, default true; false = split-bus or back-fed), yearInstalled?, feedBreakerId? (SUB only: the breaker in another panel that feeds it) | |
| Breaker | panelId, slots: List<Int>, poles (1/2), amps?, type (`STANDARD`, `GFCI`, `AFCI`, `DUAL`), label, confidence (`CONFIRMED`, `LIKELY`, `UNKNOWN`), tandemIndex? (0/1), **M2.5:** isMain (Boolean, default false) | Two-pole = two consecutive slots. Tandem = two breakers sharing one slot, tandemIndex 0 and 1. At most one isMain breaker per panel; it is excluded from circuit counts and protection checks. |
| BreakerRoom | breakerId, roomId | many-to-many |
| Endpoint | breakerId?, roomId, kind (`OUTLET`, `LIGHT`, `SWITCH`, `APPLIANCE`, `HARDWIRED`), label | breakerId null = not yet mapped |
| Shutoff | homeId, kind (`WATER_MAIN`, `GAS_MAIN`, `ELECTRICAL_MAIN`, `HVAC_DISCONNECT`, `FIXTURE_VALVE`, `SUMP`, `CLEANOUT`, `OTHER`), roomId?, photoPath?, instructions | |
| Equipment | homeId, roomId?, category (`WATER_HEATER`, `FURNACE`, `AC`, `HEAT_PUMP`, `RANGE`, `REFRIGERATOR`, `DISHWASHER`, `WASHER`, `DRYER`, `SUMP_PUMP`, `WATER_SOFTENER`, `OTHER`), make?, model?, serial?, manufacturedOn?, installedOn?, warrantyEnds?, labelPhotoPath?, notes? | Dates are ISO `yyyy-MM-dd` strings |
| Consumable (M3) | equipmentId, kind (`FILTER`, `BULB`, `CARTRIDGE`, `OTHER`), spec, reorderUrl? | |
| Finish (M2) | roomId, kind (`PAINT`, `FLOORING`, `TILE`, `TRIM`), brand?, name?, code?, sheen?, sku?, leftoverLocation? | |
| Photo | homeId, roomId?, wallId?, stage (`BEFORE`, `ROUGH_IN`, `BEHIND_WALL`, `FINISHED`, `GENERAL`), takenAt, caption?, path | path relative to app files dir |
| MaintenanceTask (M3) | homeId, equipmentId?, title, rule (`CALENDAR`, `SEASONAL`), intervalMonths?, season?, lastDone?, nextDue | |
| LoadProfile (M2.5) | homeId, squareFeet, appliances: List<ApplianceLoad {kind (`RANGE`, `OVEN`, `COOKTOP`, `DRYER`, `WATER_HEATER`, `DISHWASHER`, `DISPOSAL`, `MICROWAVE`, `EV_CHARGER`, `HOT_TUB`, `WELL_PUMP`, `POOL_PUMP`, `OTHER`), volts (120/240), va}>, hvac {kind (`NONE`, `AC_ONLY`, `HEAT_PUMP`, `HEAT_PUMP_WITH_STRIPS`, `ELECTRIC_HEAT_UNDER_4_UNITS`, `ELECTRIC_HEAT_4_PLUS_UNITS`), coolingVa?, heatingVa?, stripHeatVa?} | One per Home. Default VA per appliance kind comes from a table in `:core` and is editable. |
| Job (Field Log) | homeId, name, address?, clientName?, clientContact?, archived | One Home per Job |
| LogEntry (Field Log) | jobId, day (`yyyy-MM-dd`), kind (`PHOTO`, `NOTE`, `VOICE`), text?, mediaPath?, durationMs?, roomId? | |
| DailyReport (Field Log) | jobId, day, weather?, crewCount?, hours?, workCompleted, delays?, delayReason?, materials?, nextSteps?, pdfPath? | Editable draft, one per job per day |
| CompanyProfile (Field Log) | name, phone?, email?, logoPath? | single row |

### `.homerecord` bundle

Zip file containing `record.json` (`formatVersion: 1`, all entities above except Job, LogEntry, DailyReport, CompanyProfile) and `media/` with referenced photos by relative path. Optional passphrase encryption (M3): the zip is wrapped as `record.enc` using AES-256-GCM with a PBKDF2-derived key; header byte identifies encrypted vs plain.

Import rules: IDs are UUIDs so records merge; an imported entity never overwrites an entity with the same id whose `source` is `house-manual` — it is listed as a conflict for the user to pick; entities with no local counterpart are inserted with `source = import`.

## 7. Out of scope for v1

- Accounts, sign-in, cloud sync, team invites, crew roles.
- AI: transcription, report drafting, bilingual output.
- Change orders and e-signature.
- Weather API, recall feed, manual lookup, serial-date decoding, replacement forecasts.
- Floor-plan sketch with pins, two-phone and smart-plug mapping modes.
- NFC/QR stickers, house-sitter share links, home resume PDF.
- Wear OS, tablets layouts, iOS.
- Analytics, crash reporting, ads.
- Guardian Mode and watch faces (separate projects).

## 8. Acceptance criteria

Each criterion is checkable on an emulator (and, where marked `[unit]`, also by a unit test). "Fresh install" means app data cleared.

### F1 Onboarding
- **AC-1** On a fresh install House Manual opens HM-Onboarding, not HM-Home. `[unit]`
- **AC-2** Continue is disabled until the home name has at least one non-space character.
- **AC-3** Toggling three quick-add rooms and adding one custom room, then Continue, creates one Home and four Rooms and lands on HM-Panel. `[unit]`
- **AC-4** Killing and relaunching the app after onboarding opens HM-Home with the same home name.

### F2 Panel map
- **AC-5** HM-Panel with no panel yet shows "Set up your panel" with slot count (default 40) and layout choice; saving creates a Panel with that many empty slots. `[unit]`
- **AC-6** Taking a panel photo stores it under app files and shows it above the grid; relaunch keeps it.
- **AC-7** North American layout renders slots in two columns, odd numbers left, even numbers right, top to bottom. DIN renders one column.
- **AC-8** Tapping an empty slot opens HM-Breaker with slot number prefilled; Save creates a Breaker occupying that slot and the slot shows its label and amps. `[unit]`
- **AC-9** Setting poles = 2 on slot N makes the breaker occupy N and N+2 (same column). The grid draws it as **one cell spanning both rows** (the height of two one-pole slots) that shows both slot numbers ("2 · 4"), a "2-pole · 240 V" marker, one label and one amps value; tapping anywhere in the cell opens the same breaker. It must be obvious at a glance both that it is a single breaker and that it occupies two positions. If N+2 is occupied Save is blocked with a message. `[unit]` (occupancy) + Compose test (one node, two slot numbers)
- **AC-10** Long-press on a slot → "Make tandem" splits it into two half-height cells with their own labels; both breakers have the same slot and tandemIndex 0 and 1. `[unit]`
- **AC-11** Changing slot count to a smaller number that would drop occupied slots is refused with a message naming the highest occupied slot. `[unit]`
- **AC-12** Delete on HM-Breaker removes the breaker, clears its slot(s) and detaches (not deletes) its endpoints. `[unit]`

### F3 Links and confidence
- **AC-13** HM-Breaker lists all rooms as chips; selecting two rooms and saving creates two BreakerRoom rows. `[unit]`
- **AC-14** Adding an endpoint (kind Outlet, label "Freezer", room Garage) on HM-Breaker creates an Endpoint linked to that breaker and room. `[unit]`
- **AC-15** Confidence defaults to Unknown; each slot in the grid shows a colored dot: green Confirmed, amber Likely, grey Unknown.
- **AC-16** Saving a breaker with confidence Confirmed from HM-Mapping (AC-40) is reflected on HM-Panel without restarting the app.

### F4 Search and room lookup
- **AC-17** Typing "garage freezer" in HM-Home search returns the breaker labeled "Garage" that has an endpoint labeled "Freezer" as the top result; matching is case-insensitive and token-based (every token must match the breaker label, an endpoint label, a room name, or equipment make/model/category). `[unit]`
- **AC-18** Tapping a breaker result opens HM-Panel scrolled to that slot with the slot highlighted for at least 2 seconds.
- **AC-19** Search with no matches shows "Nothing found" and no results list.
- **AC-20** HM-Room lists every breaker linked to the room directly or through an endpoint in that room, each with its confidence dot. `[unit]`
- **AC-21** HM-Room shows the equipment and shutoffs whose roomId is this room.

### F5 Legend PDF
- **AC-22** "Legend PDF" on HM-Panel writes a one-page PDF (Letter, portrait) to app cache and opens the share sheet.
- **AC-23** The legend has one row per slot in panel order: slot number, label, amps, poles, type, rooms; empty slots print "—". `[unit]` (row builder is a pure function)
- **AC-24** Two-pole breakers print once with "N, N+2" in the slot column; tandems print as "N a" and "N b". `[unit]`

### F6 Shutoffs and emergency
- **AC-25** HM-Shutoffs empty state explains the three mains to add first (water, gas, electrical).
- **AC-26** Saving a shutoff requires kind and instructions; room and photo are optional. `[unit]`
- **AC-27** Shutoff photo taken through the camera is stored under app files and shown as a thumbnail in the list.
- **AC-28** HM-Emergency is reachable from HM-Home in one tap (prominent red card).
- **AC-29** HM-Emergency groups shutoffs: Water (WATER_MAIN, FIXTURE_VALVE), Gas (GAS_MAIN), Electrical (ELECTRICAL_MAIN, HVAC_DISCONNECT), Other (rest), in that order; mains print first inside each group. `[unit]`
- **AC-30** HM-Emergency uses at least 24sp for instructions and shows the photo at full width; tapping the photo opens it full screen with pinch zoom.
- **AC-31** HM-Emergency works with airplane mode on (no network dependency).

### F7 Quick Settings tile
- **AC-32** The app registers a TileService labeled "House emergency" that appears in the Quick Settings edit list after install.
- **AC-33** Tapping the tile opens HM-Emergency directly (over the lock screen it prompts to unlock, then opens).
- **AC-34** The tile is in the "active" state when at least one shutoff exists and "inactive" otherwise. `[unit]` (state chooser is a pure function)

### F8 Mapping mode
- **AC-35** HM-Mapping shows "Plug in to begin" while not charging and enables Start only when the device reports charging. `[unit]` (state machine)
- **AC-36** While armed, the screen stays on (keep-screen-on flag) and shows "Armed — go flip breakers".
- **AC-37** When power is disconnected while armed, within 1 second the screen turns red with "POWER LOST", a repeating tone plays at maximum stream volume and the device vibrates in a repeating pattern. `[unit]` (transition on `PowerEvent.Disconnected`)
- **AC-38** The alarm stops when the user taps Stop or when power is reconnected. `[unit]`
- **AC-39** After Stop, the screen asks "Which breaker did you flip?" listing all breakers by slot and label, then "Which outlet is the phone in?" listing endpoints in the chosen room with "New outlet…".
- **AC-40** Save links the endpoint to the breaker and sets the breaker's confidence to Confirmed. `[unit]`
- **AC-41** Leaving HM-Mapping disarms the alarm and unregisters the receiver (no alarm fires after leaving the screen).

### F9 Equipment and OCR
- **AC-42** HM-Equipment empty state suggests the first three items: water heater, furnace/air handler, AC/heat pump.
- **AC-43** Saving equipment requires category; everything else optional. `[unit]`
- **AC-44** With installedOn set, the list shows age as "N yrs" (whole years, floor); with warrantyEnds set it shows "Warranty until <date>" or "Warranty expired". `[unit]`
- **AC-45** "Scan data plate" takes a photo and runs on-device text recognition; the result sheet shows the recognized lines.
- **AC-46** The parser proposes make, model and serial from recognized text using label heuristics ("MODEL", "MOD", "M/N", "SERIAL", "SER", "S/N", and known brand names); the user confirms or edits before fields are filled. `[unit]` with fixture strings
- **AC-47** If recognition returns no text, the sheet says so and offers manual entry; the app does not crash.
- **AC-48** The label photo is kept with the equipment and shown on HM-EquipmentEdit.
- **AC-49** Text recognition runs with no network (bundled model); verified with airplane mode on.
- **AC-50** Deleting equipment removes it and its label photo file. `[unit]`

### F10 Jobs
- **AC-51** Field Log opens on FL-Jobs; empty state has one button "New job".
- **AC-52** Saving a job requires a name; it creates a Job and a Home with the same name, plus any rooms chosen. `[unit]`
- **AC-53** FL-Jobs sorts by most recent LogEntry (jobs with none sort by createdAt). `[unit]`
- **AC-54** Archived jobs are hidden behind a "Show archived" toggle.

### F11 Capture
- **AC-55** FL-Capture opens the CameraX preview full screen within 1.5 s on the emulator; the shutter button is at least 72dp.
- **AC-56** Pressing the shutter saves a JPEG under app files and creates a PHOTO LogEntry for the job and today's date. `[unit]` (entry creation)
- **AC-57** Volume-up and volume-down both trigger the shutter while FL-Capture is visible. `[unit]` (key handler)
- **AC-58** "Note" opens a text field; Save creates a NOTE entry; empty notes are not saved. `[unit]`
- **AC-59** "Voice" starts recording (AAC in .m4a), shows elapsed time, and stops at 3:00 automatically or when tapped; a VOICE entry with durationMs is created. `[unit]` (duration cap logic)
- **AC-60** Denying the camera or microphone permission shows an explanation with a Settings button; the app does not crash.
- **AC-61** FL-Job groups entries by day with headers like "Tue, Oct 13" and shows thumbnails, note text and audio duration. `[unit]` (grouping)
- **AC-62** Killing the app mid-session loses no saved entry (each capture is committed to Room immediately).

### F12 Behind-the-wall capture
- **AC-63** FL-BehindWall requires choosing a room, then a wall (N/E/S/W or custom); custom wall labels are saved as Wall rows. `[unit]`
- **AC-64** Each photo creates a Photo with stage BEHIND_WALL, roomId and wallId, and a PHOTO LogEntry for the day. `[unit]`
- **AC-65** A running count per wall is shown ("North: 3 photos").
- **AC-66** Photos tagged behind-wall appear in HM-Room after import (see AC-77).

### F13 Daily report PDF
- **AC-67** FL-Report for a day prefills "Work completed" with that day's NOTE entries joined by newlines and "Photos" with that day's PHOTO entries; every field is editable and saved as a DailyReport draft. `[unit]`
- **AC-68** Delay reason picker offers: None, Weather, Materials, Client, Inspection, Subcontractor, Other.
- **AC-69** "Make PDF" produces a PDF with a header (company name, job, address, date), the fields, and up to 6 photos laid out two per row; the share sheet opens. `[unit]` (section builder)
- **AC-70** Re-generating the PDF overwrites the previous file for that day rather than accumulating files. `[unit]`

### F14 Handoff export and import
- **AC-71** FL-Handoff lists counts: rooms, walls, behind-wall photos, equipment, finishes, shutoffs for the job's Home. `[unit]`
- **AC-72** "Export .homerecord" writes a zip with `record.json` and `media/` and opens the share sheet. `[unit]` (writer round-trip in JVM)
- **AC-73** `record.json` includes `formatVersion: 1` and every entity listed in §6 for that Home, with `source` preserved. `[unit]`
- **AC-74** The bundle reader rejects a file whose `formatVersion` is greater than 1 with a readable error. `[unit]`
- **AC-75** HM-Import opens from HM-Settings and from an `ACTION_VIEW` intent for `.homerecord` files (MIME `application/octet-stream`, extension filter).
- **AC-76** HM-Import preview shows counts per entity and the list of conflicts (same id, local `source = house-manual`). `[unit]`
- **AC-77** Import inserts new entities and media under the local home (remapping `homeId` to the local Home), and behind-wall photos show in their HM-Room. `[unit]`
- **AC-78** For each conflict the user picks "Keep mine" or "Use imported"; the choice is applied exactly. `[unit]`
- **AC-79** Importing the same bundle twice does not duplicate anything. `[unit]`

### F15 Reminders
- **AC-80** HM-Settings lets the user pick a climate zone (Cold, Mixed, Hot-humid, Hot-dry); picking one seeds the seasonal task list for that zone. `[unit]`
- **AC-81** Equipment edit offers "Remind me every N months" (1–24); saving creates a MaintenanceTask with nextDue = installedOn or today + N months. `[unit]`
- **AC-82** A Reminders screen lists tasks sorted by nextDue with Done and Snooze (1 week); Done sets lastDone = today and advances nextDue. `[unit]`
- **AC-83** A notification is posted on the morning of nextDue (WorkManager periodic check); tapping it opens the task.
- **AC-84** Notification permission is requested only when the first task is created.
- **AC-85** Deleting equipment deletes its tasks. `[unit]`

### F16 Widget
- **AC-86** A 2×1 home-screen widget "House emergency" opens HM-Emergency.
- **AC-87** The widget shows the count of shutoffs and updates after a shutoff is added.

### F17 Backup
- **AC-88** HM-Settings "Export backup" produces a passphrase-encrypted `.homerecord` (passphrase ≥ 8 chars) via the share sheet. `[unit]` (encrypt/decrypt round-trip)
- **AC-89** HM-Import detects an encrypted bundle and asks for the passphrase; a wrong passphrase gives a readable error. `[unit]`
- **AC-90** Restoring a backup onto a fresh install recreates the home exactly (entity counts and ids equal). `[unit]`
- **AC-91** The bundle writer never includes files outside the app's own storage. `[unit]` (path sanitizer)

### F18 Free limits and Pro
- **AC-92** Free tier allows one panel and ten equipment items; the eleventh equipment or second panel shows the paywall instead of saving. `[unit]` (limit checker with a fake entitlement)
- **AC-93** The paywall is never shown before the panel has at least one labeled breaker. `[unit]`
- **AC-94** The paywall offers Yearly and Lifetime; with the entitlement active the limits disappear immediately. `[unit]` (fake entitlement toggles)
- **AC-95** Billing is isolated behind an `Entitlements` interface; the RevenueCat implementation is the only class that imports the SDK.
- **AC-96** With no network the paywall shows a "can't load prices" state and a Retry; the app never crashes.

### F19 Multiple panels and consumables
- **AC-97** HM-Panel gets a panel switcher; adding a sub-panel creates a second Panel with its own slot grid. `[unit]`
- **AC-98** Search and legend PDF cover all panels (legend prints one page per panel). `[unit]`
- **AC-99** Equipment edit lists consumables (kind, spec); adding "Filter 16x25x1 MERV 8" saves a Consumable. `[unit]`
- **AC-100** Search matches consumable specs ("16x25" finds the furnace). `[unit]`

### F20 Panel profile and room kinds
- **AC-101** HM-Panel setup and edit offer: kind (Main panel / Sub-panel), rating in amps (picker 60, 100, 125, 150, 200, 225, 400, or custom), brand (free text with suggestions: Square D QO, Square D Homeline, Eaton BR, Eaton CH, Siemens, GE, Leviton, Federal Pacific, Zinsco, Challenger, Other), model or catalog number, max circuits (optional, "from the label inside the door"), tandems allowed (Yes / No / Unknown), main breaker present (Yes / No), year installed (optional). All optional except kind; defaults: Main, Unknown, Yes. `[unit]` (validation)
- **AC-102** For a Sub-panel, the editor asks "Which breaker feeds it?" and lists breakers from the other panels; the chosen breaker's label gains a "→ feeds <panel name>" note in HM-Panel and the legend. `[unit]`
- **AC-103** HM-Breaker offers "This is the main breaker" (isMain); only one per panel is allowed (choosing a second clears the first with a message); the main breaker renders at the top of the grid with a distinct style and is excluded from circuit counts. `[unit]`
- **AC-104** Rooms have a kind: quick-add rooms get it automatically (Kitchen → KITCHEN, Primary Bath/Bathroom → BATHROOM, Bedroom N → BEDROOM, Garage → GARAGE, Basement → BASEMENT, Laundry → LAUNDRY, Exterior → EXTERIOR, Hallway → HALLWAY, Living Room → LIVING, Dining Room → DINING, Office → OFFICE, Attic → ATTIC); custom rooms default to OTHER and HM-Room lets the user change the kind. `[unit]` (name → kind mapper)
- **AC-105** The legend PDF header prints panel kind, brand, model, rating ("200 A main") and year installed when known; `.homerecord` export and import carry every new field, and bundles without them still import. `[unit]`

### F21 Panel insights
Each insight is a card with a severity (**Warning**, **Check**, **Info**), one plain-English sentence, and a short "why" with the code reference. The sheet ends with: "General information based on the U.S. National Electrical Code (2023). Local codes differ and older installations are often grandfathered. This is not a substitute for a licensed electrician." All rules are pure functions in `:core` (`PanelInsights`) with unit tests; the UI only renders their output.
- **AC-106** HM-Panel has a "Panel insights" entry point; the sheet opens in under 300 ms with no network and reflects the current data without restart. `[unit]` (rules) + Compose test (renders a given insight list)
- **AC-107** Capacity (Info): slots used / free, circuit count (a tandem counts as 2, the main breaker as 0), number of 240 V (two-pole) circuits, % of slots labeled, % confirmed. `[unit]`
- **AC-108** Tandem and circuit-count checks: tandems present and tandemPolicy = NOT_ALLOWED → **Warning** "This panel is not listed for tandem breakers (NEC 408.54)"; tandems present and UNKNOWN → **Check** "Look for the max-circuits label or notched bus stabs; tandems are only allowed where the panel is listed for them"; circuits > maxCircuits (when set) → **Warning**; yearInstalled < 2008 and circuits > 42 → **Warning** "Panels of this era were limited to 42 circuits". `[unit]`
- **AC-109** Hazard-brand warning (**Warning**, shown first): brand or model matching, case-insensitively, any of "federal pacific", "fpe", "stab-lok", "federal pioneer", "zinsco", "sylvania", "challenger", "bulldog", "pushmatic", or hasMainBreaker = false → "Home inspectors and many insurers flag this panel type; have a licensed electrician evaluate it." with a one-line reason per match (FPE/Stab-Lok and Zinsco: breakers may fail to trip; Challenger/Bulldog: documented failures; no main breaker/split-bus: no single disconnect). `[unit]` (matcher with fixtures, including "GTE Sylvania", "FEDERAL PACIFIC ELECTRIC", "Square D" → no match)
- **AC-110** GFCI expectations (**Check**): for every non-main breaker linked (directly or through an endpoint) to a room of kind BATHROOM, KITCHEN, GARAGE, EXTERIOR, BASEMENT, CRAWL_SPACE, LAUNDRY or UTILITY whose type is STANDARD or AFCI → "GFCI protection is expected for receptacles in <rooms> (NEC 210.8(A)); it may be provided at the outlet instead of the breaker — verify." One card per breaker, rooms listed. `[unit]`
- **AC-111** AFCI expectations (**Check**): for every non-main, one-pole breaker linked to a room of kind BEDROOM, LIVING, FAMILY, DINING, KITCHEN, OFFICE, HALLWAY, CLOSET or LAUNDRY whose type is STANDARD or GFCI → "AFCI protection is required for new 15/20 A circuits in <rooms> (NEC 210.12(B)); existing circuits may be grandfathered — verify." `[unit]`
- **AC-112** Dedicated-circuit checklist (**Info** per row, **Check** when missing): expected rows — two 20 A kitchen small-appliance circuits, 20 A bathroom, 20 A laundry, 20 A garage (NEC 210.11(C)); and appliance circuits dishwasher, disposal, microwave, range or cooktop (two-pole), dryer (two-pole), water heater (two-pole), HVAC / furnace / air handler. A row is "Found" when a breaker label, endpoint label or linked room kind matches its keyword list (e.g. range: "range", "stove", "oven", "cooktop"); otherwise "Not labeled yet — label it, or it may not exist." Keyword matching is case-insensitive and tested with fixtures. `[unit]`
- **AC-113** Insights never block saving anything, never modify data, and are recomputed from the database on every open; a panel with no breakers shows only the capacity card and the disclaimer. `[unit]`

### F22 Load estimate
- **AC-114** From Panel insights, "Estimate load" opens a form: square feet (prefilled from Home), appliances (toggle list with default VA each: range 12,000; wall oven 8,000; cooktop 7,200; dryer 5,000; water heater 4,500; dishwasher 1,200; disposal 900; microwave 1,500; EV charger 7,200 (level 2, editable); hot tub 6,000; well pump 1,500; pool pump 1,500; other: user VA), and HVAC (none; A/C only with VA; heat pump with VA; heat pump + strip heat VA; electric heat < 4 units VA; electric heat 4+ units VA). Values are editable and saved as the LoadProfile. `[unit]` (defaults table)
- **AC-115** `LoadEstimator` implements NEC 220.82: general load = 3 VA × sq ft + 4,500 VA (two small-appliance + laundry) + sum of appliance VA; demand = first 10,000 VA at 100% + remainder at 40%; HVAC = the single largest of: 100% A/C; 100% heat pump; 100% heat pump + 65% strips; 65% electric heat (< 4 units); 40% electric heat (4+ units); result amps = (demand + HVAC) ÷ 240, rounded up. `[unit]` with a worked example: 2,000 sq ft, range 12,000, dryer 5,000, water heater 4,500, dishwasher 1,200, disposal 900, A/C 3,500 VA → general 6,000 + 4,500 + 23,600 = 34,100 → demand 10,000 + 40% × 24,100 = 19,640 → + 3,500 = 23,140 VA → 23,140 ÷ 240 = 96.4 → 97 A.
- **AC-116** The result card shows "Estimated demand: 97 A of 200 A service" with a bar; ≤ 80% of rating → Info "Room to grow"; 80–100% → Check "Near capacity; plan large additions with an electrician"; > 100% → Warning "Estimated load exceeds the service rating; have a licensed electrician perform a full load calculation." `[unit]`
- **AC-117** The card explains in one line that breaker amps are not added up because breaker ratings protect wire, not predict load, and states the method ("NEC 220.82 optional method for an existing dwelling"). 
- **AC-118** If rating is unknown the card shows the amps estimate alone with "Set your service size in panel setup to compare."
