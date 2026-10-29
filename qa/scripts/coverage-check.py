#!/usr/bin/env python3
"""Coverage gate: every acceptance criterion that a ticked PLAN.md task claims must be
referenced by a test or a flow.

Which tasks: every ticked PLAN.md line whose id is T, Q, R, V or CR plus any suffix (T040b2,
T041b2b, CR2g1), with AC ids read from anywhere on the line, not only an `(acceptance: …)` group.

What counts as a reference:
  - `[unit]` criteria: the AC id appears in a `src/test/**/*.kt` file (a named JVM test). A flow
    or an instrumented test does not stand in for the JVM test (CLAUDE.md QA rule 1).
  - backend criteria (AC-B*): the AC id appears in a `backend/**/*.test.ts` vitest file.
  - every other criterion: the AC id appears in an `androidTest` file (instrumented acceptance
    test, PLAN Q003) or in a tagged asserted step of a Maestro flow under qa/flows/. Screenshot
    tests are not coverage.
  - nothing under node_modules or build/ counts.

A tagged asserted step in a flow is a comment in the commands section (after `---`) naming the
AC, followed by the steps up to the next comment, at least one of which asserts
(assertVisible, assertNotVisible, assertTrue, extendedWaitUntil, scrollUntilVisible). The flow's
header comment above `---` and a comment over steps that only tap or type do not count
(cr:qa-ac-claims-comment). The output JSON lists missing ids per task so summarize.py can file
one Major finding per task with gaps.

Usage: coverage-check.py --plan PLAN.md --spec SPEC.md --out coverage.json
"""
import argparse, glob, json, re, sys

AC = re.compile(r"AC-([CHFBU])(\d+)(?:\.\.(?:AC-)?([CHFBU])?(\d+))?")
TASK = re.compile(r"\s*- \[x\] ((?:CR|[TQRV])\d+[a-z0-9]*):(.*)")
SKIP = re.compile(r"(^|/)(node_modules|build|\.gradle)/")
COMMENT = re.compile(r"^\s*#")
ASSERT = re.compile(r"^\s*-?\s*(assertVisible|assertNotVisible|assertTrue|extendedWaitUntil|scrollUntilVisible)\b")


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


def flow_ids(text):
    """AC ids from the flow's tagged asserted steps: a comment after `---` and the steps under it."""
    lines = [l.rstrip("\n") for l in text.splitlines()]
    stripped = [l.strip() for l in lines]
    if "---" not in stripped:
        return set()
    ids, tag, asserted, in_comment = set(), "", False, False
    for line in lines[stripped.index("---") + 1:] + ["#"]:
        if COMMENT.match(line):
            if not in_comment:  # a new step block starts; settle the previous one
                if asserted:
                    ids |= expand(tag)
                tag, asserted = "", False
            tag += line + "\n"
            in_comment = True
        else:
            in_comment = False
            asserted = asserted or bool(ASSERT.match(line))
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

    def files(*patterns):
        found = set()
        for pat in patterns:
            found |= {f for f in glob.glob(pat, recursive=True) if not SKIP.search(f)}
        return sorted(found)

    def refs(paths):
        ids = set()
        for f in paths:
            text = open(f, encoding="utf-8", errors="ignore").read()
            ids |= flow_ids(text) if f.endswith(".yaml") else expand(text)
        return ids

    jvm_files = files("**/src/test/**/*.kt")
    backend_files = files("backend/**/*.test.ts", "backend/**/*.spec.ts")
    device_files = files("**/src/androidTest/**/*.kt", "qa/flows/**/*.yaml")
    unit_files = jvm_files + backend_files
    jvm_refs, backend_refs, device_refs = refs(jvm_files), refs(backend_files), refs(device_files)

    tasks = []
    for line in open(a.plan, encoding="utf-8"):
        m = TASK.match(line)
        if not m:
            continue
        tid, rest = m.group(1), m.group(2)
        ids = sorted(expand(rest) & defined, key=lambda s: (s[3], int(s[4:])))
        if not ids:
            continue
        missing = []
        for ac in ids:
            if ac.startswith("AC-B"):
                ok = ac in backend_refs
            elif ac in unit:
                ok = ac in jvm_refs
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
