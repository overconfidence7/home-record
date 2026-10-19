# Product walkthrough

The nightly tester performs this after the acceptance-criteria checks, from the user's point of view, on the emulator, with both apps installed from the current `build`. It answers one question: would Nate accept this build as the product described in PRODUCT.md? Each step is scored:

- **Pass**: possible, obvious (found without hunting), and looks right (docs/ui-standards.md).
- **Clunky**: possible but hard to find, slow, ugly or confusing. Becomes a `[Product gap] Minor` or `Major` finding.
- **Blocked**: cannot be done, or the feature does not exist yet. Becomes `[Product gap] Major` if PLAN.md has already ticked the task that should deliver it, otherwise it is listed under "Not built yet" (not a finding) so Nate can see progress.

Record the result as a table in REVIEW.md under the night's heading, one row per step, with a screenshot name for every Clunky or Blocked step. Always take a screenshot of every screen you reach and save it to `qa/screenshots/latest/<app>-<screen>.png`, even when it passes, so Nate can review the UI without installing.

## UI audit (both apps, every night)

Before the walkthrough, on every screen reached, check:

1. Bottom navigation bar with icons and labels on root screens; selected tab highlighted.
2. Top app bar with a back arrow on every non-root screen; the arrow, system back and the back gesture all return to the previous screen. **Any screen you cannot leave is a Critical finding.**
3. Lists are list rows (icon or thumbnail, title, subtitle, trailing), not stacks of identical cards.
4. Empty states have an icon, a headline, a sentence and an action.
5. At most one FAB; it does not cover the last row.
6. Nothing overlaps the status bar or sits under the navigation bar; rotate once per screen.
7. Dark mode (`adb shell cmd uimode night yes`) on at least a third of the screens each night, all screens once per milestone.
8. Font scale 200% (`adb shell settings put system font_scale 2.0`) on at least five screens each night.
9. Record detail screens show their links as chips and the chips navigate.
10. Icons everywhere a category, kind or action is shown.

Each failure is a finding tagged `[UI]` with the rule number.

## House Manual

| # | Step | What "pass" looks like |
| --- | --- | --- |
| H1 | Fresh install, open the app | Onboarding asks for name, house type, foundation, garage; can skip the plan; lands on Home with quick actions |
| H2 | Draw a two-floor plan: 6 rooms on Main (kitchen, living, dining, half bath, garage, mudroom), 4 on Upper (3 bedrooms, bath), one L-shaped | Grid, typed dimensions, drag, resize, snap; floor switcher; undo; under 5 minutes |
| H3 | Open the House tab | Clean blueprint, room names, dimensions; tap a room opens it |
| H4 | Add a water heater to the mechanical room with a photo of a data plate (use `qa/fixtures/dataplate-rheem.jpg`) | Scan fills make/model/serial; dates entered; after save the warranty section shows terms, end date, source link |
| H5 | Add 8 more items across rooms (fridge, range, dishwasher, furnace, AC, sump pump, garage door opener, smoke detector) | Category picker is grouped with icons; room preselected when added from a room |
| H6 | Open the Items tab | Grouped by category with counts; by-room toggle; warranty filter; search by model finds the item |
| H7 | Open the water heater | Hero photo, warranty status line, sections, chips for room/breaker/shutoff; Edit works; back returns to the list at the same scroll |
| H8 | Airplane mode, reopen the water heater | Warranty still shown, no spinner, no error |
| H9 | Photograph a wall in the kitchen (use `qa/fixtures/wall-kitchen.jpg`), outline the corners | Four handles, loupe, Use; thumbnail appears on the wall row |
| H10 | View the kitchen in 3D | Textured wall visible, other walls flat; orbit, zoom; Back to floor; All floors view |
| H11 | Draw a cold-water pipe on that wall photo with a note | Shows in 3D and on the blueprint Plumbing layer; switching stage keeps it in place |
| H12 | Add the breaker panel as an item, label 6 breakers, link the fridge to breaker 12 | Panel editor; fridge page shows "Breaker 12"; tapping it opens the panel scrolled and highlighted |
| H13 | Add water main and gas main shutoffs with photos; open Emergency from Home, from the quick settings tile, from the widget | Large type, grouped, mains first, offline |
| H14 | Reminders tab | Seeded tasks from the items and the climate zone; Done moves the date; Snooze works |
| H15 | More → Home report | PDF with blueprints and every room's items and warranty status |
| H16 | More → Backup; wipe app data; Restore | Everything back, including wall photos and overlays |
| H17 | Sign in on a second emulator (or clear data and sign in again) | The home appears with photos; edit on one side shows on the other |
| H18 | Import the Field Log handoff from F10 | Preview, import, rough-in wall photos and overlays visible in the rooms |
| H19 | Add items until the free limit | Paywall with prices; Test Store purchase unlocks; limit gone without restart |

## Field Log

| # | Step | What "pass" looks like |
| --- | --- | --- |
| F1 | Fresh install, sign in, create a company | Lands on Today with an empty state pointing to Jobs and Team |
| F2 | Invite a crew member; join from a second emulator (or a second account on the same emulator) | Code works; member appears in Team as Crew; role change works |
| F3 | Create a job with client, address, 4 rooms, crew and dates | Address resolves; job shows in Jobs under Active; Overview has stats |
| F4 | Clock in from Today | GPS indicator, under 5 s; status card ticks; far-from-job asks a reason (emulator location set 2 miles away) |
| F5 | Break, switch job, clock out | Punches correct in My timesheet; flags as expected |
| F6 | Owner: Timesheets for the week | Flags visible; edit with reason leaves an audit note; Approve locks; CSV and QuickBooks export open the share sheet |
| F7 | Files: upload `qa/fixtures/plan.pdf`, mark offline, open in airplane mode, mark it up | Thumbnails, zoom, markup saved as a version with history |
| F8 | Capture 6 photos with room and phase chips, one before/after pair, annotate one, add a note and a voice memo | Volume key shutter works; gallery grouped by day; pair side by side; transcript editable |
| F9 | Daily log for today | Pre-filled crew and hours, work done from the notes, photos, weather; Send produces a PDF and marks Sent |
| F10 | FL-House: place the rooms on the plan, behind-the-wall capture on two walls (`qa/fixtures/wall-rough-in.jpg`), draw a wire and a drain overlay, add the installed water heater with a data plate | Rough-in stage textures; overlays; item with warranty; Handoff export produces a `.homerecord` |
| F11 | Tasks: add 3, assign one to the crew member, pin one on the plan | My tasks on Today for the assignee; swipe completes with Undo; pin opens the task |
| F12 | Change order with two line items, photo, client signs on the phone | Signature stored; Signed read-only; PDF; share link opens a web page |
| F13 | Schedule: assign the crew member to the job tomorrow | Crew's Today shows "Tomorrow: …"; push notification received on the crew device (or logged in dev mode) |
| F14 | Materials: snap `qa/fixtures/receipt.jpg` to the job | Amount suggested; list total; CSV |
| F15 | Reports: hours by job, photo report, job summary | PDFs open and match the timesheet totals |
| F16 | Airplane mode: take photos, clock in, add a task, edit the log; reconnect | Pending count shown, then everything syncs to the other device within 60 s, no duplicates |
| F17 | Trial expiry (set the company creation date back 15 days in the debug menu) | Owner sees the paywall; crew sees the read-only message |
| F18 | Every screen in More (Team, Timesheets, Reports, Company, Billing, Materials, Clients, Settings, Account) | List rows with icons; every one opens and returns |

## Fixtures

`qa/fixtures/` holds the test images and PDF named above. QA creates them on its first run if missing (a photographed appliance data plate, a plain wall photo taken at an angle, a rough-in wall with visible studs and wiring, a one-page PDF plan, a store receipt) and commits them.
