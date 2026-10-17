# Blockers

## Needs Nate

(Things the builder cannot do without Nate: credentials, accounts, purchases, device-only checks.)

## Decisions to confirm

(Judgment calls the builder made. Nate deletes a line once confirmed or writes the correction.)
- ~~2026-10-14: AGP 8.13.2 only officially supports compileSdk up to 36.1; building against 37 needs `android.suppressUnsupportedCompileSdk=37.0` and an `android-37` symlink. Compose BOM is held at 2026.06.01 (newer needs AGP 9.1). Confirm or approve AGP 9.x.~~ **Resolved 2026-10-15: AGP 9.x approved. See T04a in PLAN.md and the dependency table in CLAUDE.md.**
- ~~2026-10-15 (T07): Two-pole breakers render as two linked cells (N and N+2 each show the label) rather than one spanning tall cell; long-press "Make tandem" only works on an occupied one-pole slot (empty slot is a no-op). (RoomViewModelTest rename flake was a real test race; fixed in T20.)~~ **Nate 2026-10-16: Two-pole — no; follow the rewritten AC-9 (one spanning cell that shows both slot numbers and a 2-pole marker). Fix via the Major REVIEW item. Tandem long-press on occupied slot only — accepted.**
- ~~2026-10-16 (T09): HM-Search is implemented inline on HM-Home (results replace hub cards while the query is non-blank) rather than as a separate screen. Endpoint results with no breaker open their room. Consumable-spec search (AC-100) waits for the Consumable model.~~ **Nate 2026-10-16: accepted.**
- ~~2026-10-16 (T11, resolved in T12): Emergency card now opens HM-Emergency; "Manage shutoffs" button there opens the list.~~ **Nate 2026-10-16: accepted.**
- ~~2026-10-16 (T20): CameraX 1.5.1 added to the catalog (approved list says 1.5.x). Volume keys are routed from MainActivity.onKeyDown via ShutterKeyRouter; held keys do not repeat the shutter. Capture entries have no room tag yet (FL-Entry edit, T22). Permission-denied screen appears after the first request is declined.~~ **Nate 2026-10-16: accepted.**
- ~~2026-10-16 (T21): Voice duration is measured by the VM's 250 ms tick count (not MediaRecorder metadata), so it can differ from the file length by a fraction of a second. Microphone denial shows the same explanation/Settings screen pattern as the camera; "Back" returns to the camera.~~ **Nate 2026-10-16: accepted for v1.**

- T29: HM-Settings does not exist until T34, so HM-Import is reachable from a Home hub card ("Import from Field Log"); T34 should add the Settings entry too (AC-75). The ACTION_VIEW filter matches content:// URIs ending in `.homerecord`; senders that give extensionless content URIs won't list House Manual — use the in-app picker then.
- 2026-10-17 (T30b): No-main-breaker hazard applies to MAIN panels only (sub-panels normally lack a main). Pushmatic has no reason line in AC-109, so it reads "discontinued, replacement breakers are hard to find". Two-pole dedicated rows (range, dryer, water heater) only match two-pole breakers. 20 A rows match by linked room kind only when the breaker is ≥ 20 A. Percent confirmed is by slots, like percent labeled.
