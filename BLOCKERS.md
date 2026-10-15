# Blockers

## Needs Nate

(Things the builder cannot do without Nate: credentials, accounts, purchases, device-only checks.)

## Decisions to confirm

(Judgment calls the builder made. Nate deletes a line once confirmed or writes the correction.)
- ~~2026-10-14: AGP 8.13.2 only officially supports compileSdk up to 36.1; building against 37 needs `android.suppressUnsupportedCompileSdk=37.0` and an `android-37` symlink. Compose BOM is held at 2026.06.01 (newer needs AGP 9.1). Confirm or approve AGP 9.x.~~ **Resolved 2026-10-15: AGP 9.x approved. See T04a in PLAN.md and the dependency table in CLAUDE.md.**
- 2026-10-15 (T07): Two-pole breakers render as two linked cells (N and N+2 each show the label) rather than one spanning tall cell; long-press "Make tandem" only works on an occupied one-pole slot (empty slot is a no-op). RoomViewModelTest "rename trims" is intermittently flaky (timing); passed on rerun.
