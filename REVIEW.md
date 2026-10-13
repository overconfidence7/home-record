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
