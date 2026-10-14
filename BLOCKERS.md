# Blockers

## Needs Nate

(Things the builder cannot do without Nate: credentials, accounts, purchases, device-only checks.)

## Decisions to confirm

(Judgment calls the builder made. Nate deletes a line once confirmed or writes the correction.)
- 2026-10-14: AGP 8.13.2 only officially supports compileSdk up to 36.1; building against 37 needs `android.suppressUnsupportedCompileSdk=37.0` and an `android-37` symlink. Compose BOM is held at 2026.06.01 (newer needs AGP 9.1). Confirm or approve AGP 9.x.
