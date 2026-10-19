# What we are building

Read this before SPEC.md. SPEC.md says what to build; this file says what it is for. If a screen passes its acceptance criteria but a homeowner or a contractor would not want to use it, it is not done. The builder and the nightly tester both measure against this file.

## The one idea

Every house has information that only lives in people's heads: which breaker feeds the garage freezer, where the water main is, what size filter the furnace takes, when the water heater went in and whether it is still under warranty, where the pipes and wires run behind the drywall. HomeRecord captures that once and keeps it tied to a model of the house, so you tap a room and see what is in it.

Two Android apps share that model:

- **House Manual** is for the homeowner. It is the owner's manual the house never came with.
- **Field Log** is for a small contractor and their crew. It runs the jobsite (time, files, photos, logs, tasks, change orders) and, as a side effect of doing the work, builds the house model the homeowner gets at the end.

## What done looks like (the demo)

This is the walkthrough the nightly tester performs. Every step must be possible, obvious and pleasant.

### House Manual

1. Install, open. Name the house, pick the house type (rancher, two-story, split-level, raised ranch, cape cod, colonial, townhouse, with basement / walkout basement / crawlspace / slab, attached garage). The app seeds the floors.
2. Draw a rough floor plan: tap to place rooms on a grid, type widths and lengths, name them, repeat for each floor. Add stairs. Five minutes for a normal house.
3. The House tab shows a clean blueprint of each floor. Tap a room and you are in that room: its items, photos, walls, and a 3D button.
4. Add items to rooms: the water heater, water softener, furnace, AC, sump pump, fridge, range, dishwasher, washer, dryer, garage door opener, thermostat, smoke detectors, the breaker panel, the shutoffs, the roof, the deck. Scan the data plate and the app reads make, model and serial. Type the dates. The app looks the warranty up once, shows what it found and where, and keeps it with the item so it never has to be looked up again.
5. Open an item: make, model, serial, purchased on, in service on, warranty (what is covered, until when, registered or not), manual, receipts, photos, service history, consumables (filter sizes), reminders, which breaker powers it, which shutoff isolates it.
6. Photograph each wall of a room, drag the four corners onto the wall edges, and the app paints that photo onto the wall in the 3D view. Walk around the room in 3D. Toggle the pipes and wires layer and see where they run.
7. Mark where things run: draw a pipe, wire, duct, drain or gas line on a wall or on the plan, with a note and a photo. It shows in 2D and 3D.
8. The breaker panel is one item among many, with its own editor: slots, labels, what each breaker feeds, a mapping mode, a printable legend. Items link to their breaker and shutoff.
9. Emergency: one tap from Home, the quick settings tile or the widget shows every shutoff with photo and instructions, in big type, offline.
10. Reminders tell you what is due (filters, seasonal tasks, warranties expiring). A home report PDF lists every room and item for an insurer, a buyer or a house-sitter.
11. Everything works with no account. Sign in to back up to the cloud and share the house with family or the next owner.

### Field Log

1. Owner installs, creates the company, invites the crew with a code. Crew installs and joins. Roles: owner, office, lead, crew.
2. Create a job with the client, the address and the rooms. Assign crew and days.
3. Crew opens the app in the morning: Today shows where they are working, their tasks, and a big Clock in button. Clock in records the time and a GPS point; if they are not near the job it asks why. Breaks, switch job, clock out. The lead can clock the whole crew in.
4. Files: the owner drops the plans, permit and contract into the job. Crew opens the plan on the phone, pinches to zoom, marks it up, and it is there offline in the basement.
5. Capture: photos (volume key shutter, gloves on) tagged to job, room and phase, before/after, annotations. Notes and voice memos. Everything syncs when there is signal.
6. The daily log writes itself from the day: who was on site and for how long, what got done (from notes), photos, weather, materials delivered, delays. Lead reviews, taps Send, the client and the office get a PDF.
7. Tasks and punch list per job, assigned, with photos, pinned to a room. Change order on the spot: describe it, photo it, price it, the client signs on the phone.
8. Owner's side: who is clocked in right now and where, timesheets to approve each week with the odd ones flagged (forgot to clock out, punched far from the job), hours by job and by person, export to CSV or QuickBooks.
9. Behind the wall: before drywall, photograph each wall and outline it, mark where the pipes and wires went. That becomes the house model's rough-in layer. Installed equipment is entered with make, model, serial and warranty.
10. Close-out: hand the house model to the homeowner. They open it in House Manual and start with everything filled in.

## Non-negotiables

- **It must look and feel like a first-rate Android app.** Bottom navigation with 3 to 5 destinations, a top app bar with a back arrow on every screen that is not a root, lists that look like lists, one floating action button for the main create action, Material 3 throughout, dark mode, dynamic color, predictive back, no dead ends. The rules are in `docs/ui-standards.md` and the tester checks them every night.
- **The house model is the center of both apps**, and the breaker panel, shutoffs, items, photos and overlays all hang off rooms. No feature gets its own silo.
- **Items are the heart of House Manual.** Any household thing can be added, put in a room, and opened to see make, model, dates and warranty. The breaker panel is one such item with extra screens.
- **Warranty is looked up once and stored.** Never make the user look it up twice. Show the source.
- **The 3D view is real, not a gimmick**: extruded from the plan, textured with the user's photos, multiple floors, tap a room to enter it, overlays visible.
- **Field Log is complete for a 1 to 10 person crew**: time with GPS, files and plans, photos, daily logs, tasks, change orders with signature, schedule, timesheets and reports, roles, offline. Everything a competitor app offers at the table-stakes level (see `docs/competitors.md`) is in scope.
- **Offline first.** Both apps work with no signal. Field Log syncs when it can.
- **Plain language everywhere** (`docs/voice.md`).

## What v1 got wrong (so it is not repeated)

The first build (this repo's history up to branch `archive/v1`, October 13 to 19, 2026) was spec-driven and passed its own acceptance criteria, and it missed the product. Specifically:

- It treated the breaker panel as the product. The panel is one feature.
- There was no house model: no floor plan, no 3D, no place to put things.
- Items were an afterthought ("equipment", ten categories, no warranty lookup).
- Field Log was a photo and notes capture tool, not a jobsite app: no team, no time clock, no files.
- The UI was a stack of plain cards with no navigation bar, no top app bar, no back arrow, no icons. Users got stuck inside screens.
- The tester checked acceptance criteria and never asked whether the app did what it was for.

This build starts from zero on the same repo: the v1 tree was removed and lives only in git history (branch `archive/v1`). Nothing from v1 is carried forward except the voice guide, the billing notes, the SDK setup script and the CI workflow.
