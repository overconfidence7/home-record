#!/usr/bin/env python3
"""Coverage gate: every acceptance criterion that a ticked PLAN.md task claims must be
referenced by a test or a flow.

What counts as a reference:
  - `[unit]` criteria: the AC id appears in any `src/test/**/*.kt` file (a named JVM test) or,
    for backend criteria, in a `backend/**/*.test.ts` file.
  - every other criterion: the AC id appears in an `androidTest` file (instrumented acceptance
    test, PLAN Q003), a Maestro flow under qa/flows/, or a screenshot test file.

A criterion mentioned in a file only as prose ("see AC-H5") still counts; the intent is that
the builder tags the test with the id. The output JSON lists missing ids per task so
summarize.py can file one Major finding per task with gaps.

Usage: coverage-check.py --plan PLAN.md --spec SPEC.md --out coverage.json
"""
import argparse, glob, json, re, sys

AC = re.compile(r"AC-([CHFBU])(\d+)(?:\.\.(?:AC-)?([CHFBU])?(\d+))?")


def expand(text):
    ids = set()
    for m in AC.finditer(text):
        p, a, p2, b = m.groups()
        if b:
            for i in range(int(a), int(b) + 1):
                ids.add(f"AC-{p}{i}")
        else:
            ids.add(f"AC-{p}{a}")
    return ids


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--plan", default="PLAN.md")
    ap.add_argument("--spec", default="SPEC.md")
    ap.add_argument("--out", default="coverage.json")
    a = ap.parse_args()

    spec = open(a.spec, encoding="utf-8").read()
    unit = set()
    for line in spec.splitlines():
        m = re.match(r"\| (AC-[CHFBU]\d+)\s*`\[unit\]`", line)
        if m:
            unit.add(m.group(1))
    defined = set(re.findall(r"\| (AC-[CHFBU]\d+)", spec))

    unit_files = (glob.glob("**/src/test/**/*.kt", recursive=True)
                  + glob.glob("backend/**/*.test.ts", recursive=True) + glob.glob("backend/**/*.spec.ts", recursive=True))
    device_files = (glob.glob("**/src/androidTest/**/*.kt", recursive=True)
                    + glob.glob("qa/flows/**/*.yaml", recursive=True)
                    + glob.glob("**/src/screenshotTest*/**/*.kt", recursive=True))
    unit_refs, device_refs = set(), set()
    for f in unit_files:
        unit_refs |= expand(open(f, encoding="utf-8", errors="ignore").read())
    for f in device_files:
        device_refs |= expand(open(f, encoding="utf-8", errors="ignore").read())

    tasks = []
    for line in open(a.plan, encoding="utf-8"):
        m = re.match(r"\s*- \[x\] ([TQ]\d+[a-z]?):(.*)", line)
        if not m:
            continue
        tid, rest = m.group(1), m.group(2)
        acm = re.search(r"\(acceptance:([^)]*)\)", rest)
        if not acm:
            continue
        ids = sorted(expand(acm.group(1)) & defined, key=lambda s: (s[3], int(s[4:])))
        missing = []
        for ac in ids:
            if ac in unit or ac.startswith("AC-B"):
                ok = ac in unit_refs or ac in device_refs
            else:
                ok = ac in device_refs
            if not ok:
                missing.append(ac)
        tasks.append({"task": tid, "acs": ids, "missing": missing})

    total = sum(len(t["acs"]) for t in tasks)
    missing = sum(len(t["missing"]) for t in tasks)
    out = {"ticked_tasks": len(tasks), "acs_claimed": total, "acs_missing": missing,
           "unit_test_files": len(unit_files), "device_test_files": len(device_files),
           "tasks": [t for t in tasks if t["missing"]]}
    json.dump(out, open(a.out, "w"), indent=1)
    print(f"coverage: {total - missing}/{total} claimed ACs referenced by a test or flow; "
          f"{len(out['tasks'])} ticked tasks with gaps")
    for t in out["tasks"]:
        print(f"  {t['task']}: missing {', '.join(t['missing'])}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
