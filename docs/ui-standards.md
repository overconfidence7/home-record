# UI standards

These rules are part of the build contract (SPEC.md section 8). The builder follows them on every screen; the nightly tester checks them. Based on Material 3 (including Material 3 Expressive), Google's core and adaptive app quality guidelines, and what the best Android apps (Google Keep and Tasks, Todoist, Sortly, CompanyCam, Airbnb) actually do. Research notes and sources: `docs/competitors.md`.

## Why the first build failed the eye test

Stacked same-shaped cards as the only layout; no bottom navigation; no top app bar, so no back arrow and no screen title; no icons; no hierarchy between a summary and a list; forms with no labels on their chips; nothing told the user where they were or how to get out. None of that is fixable with colors. The structure below is mandatory.

## Structure

1. **Bottom navigation bar** with 3 to 5 destinations on phones, each with an icon and a label. Selected = filled icon and indicator. Reselecting the current tab pops to that tab's root. On width ≥ 600 dp use a navigation rail (`NavigationSuiteScaffold` does this).
2. **Top app bar on every non-root screen** with the back arrow (`Icons.AutoMirrored.Filled.ArrowBack`) as the navigation icon, a one-line title that names the record ("Kitchen", "Water heater", not "Detail"), and at most 3 icon actions; the rest in an overflow menu. Root screens may use a large or center-aligned top bar with the search field or a profile action.
3. **No dead ends.** From every screen: the back arrow, system back and the back gesture all return to the previous screen with its scroll position; predictive back previews it. Full-screen editors use Close (X) and Save instead of Back, and ask before discarding changes.
4. **One hierarchy, separate routes.** Home → room → item are separate screens; the child's title names the parent in the subtitle or a chip that navigates up.
5. **Navigation 3** (`NavDisplay`, `rememberNavBackStack`, keys as `@Serializable` data classes) with per-tab back stacks saved in `rememberSaveable`.
6. **Adaptive**: on ≥ 840 dp width, collection screens use `ListDetailPaneScaffold` (list stays, detail on the right).

## Lists, cards, content

7. Homogeneous collections (rooms, items, photos by day, punches, tasks, files) are a `LazyColumn` of `ListItem` rows: leading icon or 40/56 dp thumbnail, headline, supporting text, trailing meta (date, count, chevron). Row heights 56/72/88 dp. Stable keys. `animateItem()` on add/remove.
8. **Cards only for summaries and highlights**: the Emergency card on Home, "Needs attention", a stat tile, an image-led tile. Never a column of identical cards acting as a list or a menu.
9. Section headers (`titleSmall`, primary color, 16 dp top padding) group content on detail screens; dividers only inside a group.
10. Every list has three states: empty (icon, one-line headline, one sentence, one primary action that matches the FAB), loading (`LoadingIndicator`), error (what failed and a Retry).
11. Search on any list that can exceed 20 rows (`SearchBar` or a top-bar search icon); filters as a horizontal row of `FilterChip`s; "No results" offers to clear filters or create the thing.
12. Hub screens (Home, Today) are a vertical mix of: a search bar, one or two summary cards, a horizontal row of quick actions, and short "Recent" lists with "See all". Nothing on a hub is deeper than one tap from its full list.

## Actions

13. At most one FAB per screen (bottom-end) for the single primary create action; `Scaffold(floatingActionButton=...)` so it stays put; lists get 88 dp bottom `contentPadding`. A FAB menu (`FloatingActionButtonMenu`) is allowed when there are 2 to 4 create actions (Photo, Note, Voice).
14. Quick add = FAB → bottom sheet with the first field focused and the keyboard open; IME Done saves; the new row is visible on return; snackbar "Added · Undo".
15. Create/edit with more than two fields is a full-screen route with Close and Save in the top app bar. Two fields or fewer may be a bottom sheet or dialog.
16. Destructive actions always confirm with an `AlertDialog` (title, one sentence, Cancel and a verb button). Deleting from a list offers Undo in a snackbar when it is reversible.
17. Bottom sheets dismiss by drag, scrim tap and back; max width 640 dp on tablets.
18. Long-press on a list row enters selection mode with a contextual top bar (count, actions); back exits it.

## Linking records

19. Links show as chips on the detail screen under a "Links" or inline label ("Breaker 14", "Kitchen", "Water main shutoff"); tap navigates; the target shows the reverse link.
20. Adding a link opens a searchable picker (bottom sheet or full screen) with "Create new…" at the bottom; removing is the chip's X with Undo.

## Forms

21. Every text field has a label (not only a placeholder), the right `keyboardType`, IME Next/Done, required marker, and inline `supportingText` errors with `isError` after the user has typed or pressed Save. Save is enabled only when required fields pass, or it focuses the first invalid field.
22. Dates use the Material date picker; lengths accept `12'6"` and `12.5`; money uses a numeric keypad and shows currency formatting; pickers for enums use `SingleChoiceSegmentedButtonRow` (≤ 4 options) or a bottom-sheet list.
23. The keyboard never covers the focused field (`imePadding()` / Scaffold insets).

## Look

24. `MaterialExpressiveTheme` with dynamic color on Android 12+ and a brand scheme fallback (House Manual: blue/teal; Field Log: orange/amber), light and dark. Colors only from `MaterialTheme.colorScheme`; no `Color(0xFF…)` in screens (allowed in the theme and in layer colors defined once in `:core-ui`).
25. Typography only from `MaterialTheme.typography`, sizes in sp; titles `titleLarge`, list headlines `bodyLarge`, supporting `bodyMedium`, meta `labelSmall`.
26. 8 dp grid; 16 dp screen margins (24 dp on tablets); icons 24 dp; `Icons.Rounded` set; every category, kind and layer has an icon.
27. Images through Coil with placeholder and error fallback; thumbnails 40 or 56 dp; photo grids 3 columns with 2 dp gaps.
28. Edge-to-edge (`enableEdgeToEdge()`), insets from Scaffold; nothing interactive under the navigation bar or cutout.
29. Motion: `MotionScheme.standard()`; forward navigation slides in from the end and back reverses; list → detail uses a shared-element transition where there is a thumbnail; nothing longer than 400 ms; respects "Remove animations".
30. Touch targets ≥ 48 dp everywhere; ≥ 56 dp on Field Log capture and clock screens; shutter ≥ 72 dp; Clock in / Clock out buttons full width and ≥ 64 dp tall.

## Accessibility and robustness

31. Every icon-only control has a `contentDescription`; decorative images use `null`; list rows read headline then supporting text.
32. 200% font scale clips nothing; rows grow.
33. Rotation, fold and process death keep input, scroll position, selected tab and open sheet.
34. Light and dark both pass contrast (4.5:1 body, 3:1 large).
35. Tablet: keyboard Tab moves through rows and controls with a visible focus ring; Esc closes sheets and dialogs.

## Previews and screenshots

36. Every screen composable has a `@Preview` with fake state for light, dark and 200% font scale, and a screenshot test. `./gradlew updateDebugScreenshotTest` writes PNGs to `<module>/src/debug/screenshotTest/reference/`; they are committed, and the builder looks at them after every UI change before committing.

## Component map (use these, in `:core-ui`)

| Need | Use |
| --- | --- |
| App shell | `HomeRecordApp` → `NavigationSuiteScaffold` + per-tab `NavDisplay` |
| Non-root screen | `ScreenScaffold(title, subtitle?, onBack, actions, fab?)` wrapping `Scaffold` + `TopAppBar` |
| Row | `RecordListItem(leading, headline, supporting, trailing, onClick, onLongClick)` |
| Empty/loading/error | `EmptyState`, `LoadingState`, `ErrorState` |
| Links | `LinkChipRow(links, onOpen, onRemove)` and `LinkPicker` |
| Photos | `PhotoTile`, `PhotoGrid`, `PhotoViewer` (zoom, annotate) |
| Forms | `FormScaffold` (Close/Save, discard prompt), `LabeledField`, `LengthField`, `MoneyField`, `DateField`, `EnumPicker` |
| Sheets | `QuickAddSheet`, `OptionsSheet` |
| Signature | `SignaturePad` |
| Camera | `CaptureScreen` (CameraX, lens limiter, volume shutter, chips row) |
| PDF | `PdfViewer` (PdfRenderer, thumbnails, zoom), `MarkupCanvas` |
| Status | `WarrantyStatusLine`, `SyncStatusChip`, `ClockStatusCard` |
