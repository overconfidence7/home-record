# QA review log

QA findings from emulator or device testing, grouped by test session. Each session is a heading `## QA <YYYY-MM-DD> (commit <sha>)`. Each finding is one bullet:

```
- [ ] [Critical|Major|Minor] <problem> - steps: <how to reproduce> - expected: <what SPEC.md says> / actual: <what happened> - screenshot: qa/screenshots/<file>.png
```

Severity: Critical = crash, data loss or an acceptance criterion that fails outright; Major = feature works but wrong or unusable; Minor = cosmetic or wording.

The builder fixes findings (Critical and Major before starting new tasks) and marks them:

```
- [x] [Major] ... - fixed in <sha>
```

Findings that are really spec changes go to BLOCKERS.md under "Decisions to confirm" instead.

---

## QA 2026-10-15 (commit 3c9f751)

Tested: AC-1, AC-2, AC-3, AC-4, rotation, back button, dark mode, long text input. Passed: 4. New issues: 0.
