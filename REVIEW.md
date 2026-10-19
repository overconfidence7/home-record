# QA review log

Findings from the nightly tester (emulator) and the builder's own visual reviews. Each QA session is a heading `## QA <YYYY-MM-DD> (commit <sha>)` and starts with a Verdict line per app. Each finding is one bullet:

```
- [ ] [Critical|Major|Minor|Product gap|UI] <problem> - steps: <how to reproduce> - expected: <SPEC AC / ui-standards rule / PRODUCT.md step> - actual: <what happened> - screenshot: qa/screenshots/latest/<file>.png
```

Severity: **Critical** = crash, data loss, a core flow cannot be completed, or a screen the user cannot leave. **Major** = a feature misses its acceptance criterion. **Product gap** = the feature exists but a homeowner or contractor would not accept it as described in PRODUCT.md; the builder treats it like Major. **UI** = a numbered rule in docs/ui-standards.md fails (Major for rules 1–6, 13, 15, 16, 28; otherwise Minor). **Minor** = polish.

The builder fixes findings (Critical, Major and Product gap before starting new tasks) and marks them:

```
- [x] [Major] ... - fixed in <sha>
```

Findings that are really spec changes go to BLOCKERS.md under "Decisions to confirm" instead.

Builder visual reviews (V0, V1, ...) go under `## Builder visual review V<n> (commit <sha>)` with the rules checked and what was changed.

---

(no sessions yet)
