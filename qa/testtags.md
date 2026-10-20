# Test tags

Format `<app>_<screen>_<element>`; `hm` = House Manual, `fl` = Field Log. Both app roots set `testTagsAsResourceId`, so Maestro targets these as ids.

| Tag | Where |
| --- | --- |
| `<app>_tab_<id>` | Bottom bar / rail item (`home`, `house`, `items`, `reminders`, `more`; `today`, `jobs`, `clock`, `schedule`, `more`) |
| `<app>_back` | Back arrow in `ScreenScaffold` on every non-root screen |
