#!/usr/bin/env python3
"""Turn the QA suite's raw output into: summary.md (human), results.json (machine) and a
"## QA auto" entry for REVIEW.md in the same bullet format the nightly tester uses, so the
builder picks findings up with no special casing.

Input layout (what the workflow downloads into --input):
  qa-prepare/jvm/*.xml             JUnit XML from testDebugUnitTest
  qa-prepare/jvm/lint-<module>.xml lint results
  qa-prepare/jvm/exit-code         exit code of the JVM gradle invocation
  qa-prepare/jvm/gradle.log
  qa-prepare/jvm/coverage.json     from coverage-check.py
  qa-prepare/backend/junit.xml, exit-code
  qa-prepare/apk/exit-code
  qa-device-<app>/stages.json      from device-suite.sh (app = house-manual | field-log | handoff)
  qa-device-<app>/maestro/*.xml    Maestro JUnit output per flow
  qa-device-<app>/screenshots/*.png

Every finding carries a stable id in square brackets, e.g. [auto:flow:house-manual:H02-draw-plan],
so a problem that is already open in REVIEW.md is not filed twice, and a fixed one that comes
back is filed as a regression.

Usage: summarize.py --input qa-in --sha abc1234 --review REVIEW.md --out qa-publish [--apply-review]
"""
import argparse, datetime, glob, json, os, re, sys
import xml.etree.ElementTree as ET

CRIT, MAJOR, MINOR = "Critical", "Major", "Minor"
EXPECTED_DEVICE_APPS = ("house-manual", "field-log", "handoff")


def read(p, default=""):
    try:
        return open(p, encoding="utf-8", errors="ignore").read()
    except OSError:
        return default


def junit_failures(path):
    out = []
    try:
        root = ET.parse(path).getroot()
    except Exception:
        return out
    for tc in root.iter("testcase"):
        f = tc.find("failure")
        if f is None:
            f = tc.find("error")
        if f is not None:
            msg = (f.get("message") or (f.text or "").strip().splitlines()[0:1] or [""])
            msg = msg if isinstance(msg, str) else (msg[0] if msg else "")
            out.append((tc.get("classname", "?"), tc.get("name", "?"), msg[:200]))
    return out


def junit_counts(path):
    try:
        root = ET.parse(path).getroot()
    except Exception:
        return 0, 0
    suites = [root] if root.tag == "testsuite" else list(root.iter("testsuite"))
    tests = sum(int(s.get("tests", 0)) for s in suites)
    bad = sum(int(s.get("failures", 0)) + int(s.get("errors", 0)) for s in suites)
    return tests, bad


STEP_FLOW = re.compile(r"^([HF]\d{2}[a-z]?)-")


def walkthrough_step(label):
    """Walkthrough step of a flow named for it (`H02b-shape-doors-stairs` -> `H02b`), or None for
    flows that are not a walkthrough step (`x-contacts`), so they never mark a step Pass or Fail."""
    m = STEP_FLOW.match(label)
    return m.group(1) if m else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--sha", required=True)
    ap.add_argument("--review", default="REVIEW.md")
    ap.add_argument("--out", required=True)
    ap.add_argument("--apply-review", action="store_true")
    ap.add_argument("--no-handoff", action="store_true",
                    help="the handoff job was skipped because qa/flows/handoff has no flows yet; do not expect its results")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    inp = a.input
    findings = []   # dicts: id, severity, text, expected, actual, screenshot, app
    skipped = []    # (app, stage, details)
    stats = {}

    def add(fid, sev, text, expected, actual, screenshot="", app=""):
        findings.append({"id": fid, "severity": sev, "text": text, "expected": expected,
                         "actual": actual, "screenshot": screenshot, "app": app})

    # ---------------------------------------------------------------- JVM
    jvm = os.path.join(inp, "qa-prepare", "jvm")
    unit_tests = unit_bad = 0
    for x in glob.glob(os.path.join(jvm, "TEST-*.xml")):
        t, b = junit_counts(x); unit_tests += t; unit_bad += b
        for cls, name, msg in junit_failures(x):
            add(f"auto:unit:{cls}.{name}", MAJOR, f"Unit test {cls}.{name} fails", "test passes (CLAUDE.md: never weaken tests)", msg)
    stats["unitTests"] = unit_tests; stats["unitFailures"] = unit_bad
    for x in glob.glob(os.path.join(jvm, "lint-*.xml")):
        mod = os.path.basename(x)[5:-4]
        try:
            for issue in ET.parse(x).getroot().iter("issue"):
                if issue.get("severity") == "Error":
                    loc = issue.find("location"); where = f"{loc.get('file','?').split('/')[-1]}:{loc.get('line','?')}" if loc is not None else "?"
                    add(f"auto:lint:{mod}:{issue.get('id')}:{where}", MAJOR, f"Lint error {issue.get('id')} in {mod} ({where})", "lintDebug has no errors (S1)", (issue.get("message") or "")[:200])
        except Exception:
            pass
    glog = read(os.path.join(jvm, "gradle.log"))
    if "validateDebugScreenshotTest FAILED" in glog or re.search(r"Screenshot.*(differ|mismatch)", glog, re.I):
        add("auto:screenshots:validate", MAJOR, "Screenshot validation failed: committed reference PNGs differ from the current previews",
            "updateDebugScreenshotTest run and PNGs inspected and committed after every UI change (CLAUDE.md)", "validateDebugScreenshotTest FAILED; see qa-results jvm/gradle.log")
    jvm_exit = read(os.path.join(jvm, "exit-code"), "0").strip()
    if jvm_exit not in ("", "0") and not any(f["id"].startswith(("auto:unit", "auto:lint", "auto:screenshots")) for f in findings):
        add("auto:jvm:build", CRIT, "JVM checks (testDebugUnitTest lintDebug validateDebugScreenshotTest) failed before producing results",
            "green build", "gradle exit code " + jvm_exit + "; see qa-results jvm/gradle.log")
    cov = {}
    try:
        cov = json.load(open(os.path.join(jvm, "coverage.json")))
        for t in cov.get("tasks", []):
            add(f"auto:coverage:{t['task']}", MAJOR, f"{t['task']} is ticked but these acceptance criteria have no test or flow: {', '.join(t['missing'])}",
                "every AC of a ticked task is referenced by an instrumented test, a Maestro flow under qa/flows/, or (for [unit] ACs) a JVM test (CLAUDE.md QA rules)", "no reference found")
    except Exception:
        pass
    stats["coverage"] = {k: cov.get(k) for k in ("acs_claimed", "acs_missing", "ticked_tasks")}

    # ------------------------------------------------------------ backend
    be = os.path.join(inp, "qa-prepare", "backend")
    if os.path.isdir(be):
        t, b = junit_counts(os.path.join(be, "junit.xml")); stats["backendTests"] = t; stats["backendFailures"] = b
        for cls, name, msg in junit_failures(os.path.join(be, "junit.xml")):
            add(f"auto:backend:{cls}.{name}", MAJOR, f"Backend test {cls} / {name} fails", "npm test green (AC-B60)", msg)
        if read(os.path.join(be, "exit-code"), "0").strip() not in ("", "0") and b == 0:
            add("auto:backend:run", CRIT, "Backend test run failed to complete", "npm test green", "see qa-results backend/npm-test.log")

    apk_exit = read(os.path.join(inp, "qa-prepare", "apk", "exit-code"), "0").strip()
    if apk_exit not in ("", "0"):
        add("auto:apk:build", CRIT, "APK build failed (assembleDebug / assembleDebugAndroidTest / assembleRelease)", "both apps build", "see qa-results apk/gradle.log")

    # ------------------------------------------------------------- device
    stage_rows = []
    walkthrough = {}
    device_ok = True   # False when any expected device job produced no stages: the run does not count as a test
    expected_apps = set(EXPECTED_DEVICE_APPS)
    if a.no_handoff:
        expected_apps.discard("handoff")
        skipped.append(("handoff", "flows", "no flows in qa/flows/handoff yet (PLAN Q004); handoff job not run"))
    for app in sorted(expected_apps | {os.path.basename(d)[len("qa-device-"):] for d in glob.glob(os.path.join(inp, "qa-device-*"))}):
        appdir = os.path.join(inp, "qa-device-" + app)
        try:
            stages = json.load(open(os.path.join(appdir, "stages.json")))
            if not stages:
                raise ValueError("empty")
        except Exception:
            device_ok = False
            add(f"auto:device:{app}:no-results", CRIT, f"device suite produced no results for {app}", "a device job that produces no results is a finding, and the run does not count as tested",
                "output directory or stages.json missing or empty: the emulator job failed before running stages; see the job's failing step in the workflow run", app=app)
            continue
        for s in stages:
            stage_rows.append((app, s["stage"], s["status"], s["details"]))
            st, name, det = s["status"], s["stage"], s["details"]
            shots = sorted(glob.glob(os.path.join(appdir, "screenshots", "*.png")))
            if st == "skip":
                skipped.append((app, name, det)); continue
            if st not in ("fail", "warn"):
                continue
            kind = name.split(":")[0]
            label = name.split(":", 1)[1] if ":" in name else ""
            shot = ""
            cands = [p for p in shots if label and label in os.path.basename(p)]
            if cands:
                shot = f"qa-results:latest/{app}/screenshots/{os.path.basename(cands[0])}"
            if kind == "flow":
                step = walkthrough_step(label)
                if step:
                    walkthrough[(app, step)] = "Fail"
                expected = f"walkthrough step {step} passes (qa/product-walkthrough.md)" if step else "the flow passes (not a walkthrough step)"
                add(f"auto:flow:{app}:{label}", MAJOR, f"Flow {label} fails on {app}", expected, f"see qa-results latest/{app}/maestro/{label}.log", shot, app)
            elif kind in ("install", "install-test"):
                add(f"auto:{kind}:{app}:{label}", CRIT, f"{label}: {kind} failed", "APK installs on the emulator", det, app=app)
            elif kind == "coldstart":
                add(f"auto:coldstart:{app}:{label}", MAJOR, f"{label} cold start too slow", "under 1,500 ms (AC-U11)", det, app=app)
            elif kind == "jank":
                add(f"auto:jank:{app}:{label}", MAJOR, f"{label} scroll jank", "under 10% janky frames (AC-U11)", det, app=app)
            elif kind == "instrumented":
                add(f"auto:instrumented:{app}:{label}", MAJOR, f"Instrumented tests fail on {label}", "all acceptance tests and the screen crawler pass", det + f" (qa-results latest/{app}/instrumented/{label}.txt)", app=app)
            elif kind == "variant":
                add(f"auto:variant:{app}:{label}", MAJOR, f"Tour fails under {label} on {app}", "every screen survives dark mode, 200% font and landscape (AC-U8, AC-U9)", det, shot, app)
            elif kind == "monkey":
                add(f"auto:monkey:{app}:{label}", CRIT, f"{label} crashed or hung under random input", "no crash in 1,500 monkey events", det + f" (qa-results latest/{app}/logs/monkey-{label}.txt)", app=app)
            elif kind == "release":
                add(f"auto:release:{app}:{label}", MAJOR, f"{label} release (R8) build problem", "release APK installs and launches (S8)", det, app=app)
            elif kind == "logcat":
                sev = CRIT if st == "fail" else MINOR
                add(f"auto:logcat:{app}:{label}", sev, f"logcat for {label}: {det}", "no crashes or ANRs; StrictMode clean (S7)", f"see qa-results latest/{app}/logs/crashes.txt and strictmode.txt", app=app)
            else:
                add(f"auto:{name}:{app}", MAJOR, f"{name} failed on {app}", "stage passes", det, app=app)
        # walkthrough steps covered by passing flows
        for s in stages:
            if s["stage"].startswith("flow:") and s["status"] == "pass":
                step = walkthrough_step(s["stage"][5:])
                if step:
                    walkthrough.setdefault((app, step), "Pass")

    # ------------------------------------------------------- dedupe vs REVIEW
    review = read(a.review)
    open_ids = set(re.findall(r"^- \[ \] .*?\[(auto:[^\]]+)\]", review, re.M))
    fixed_ids = set(re.findall(r"^- \[x\] .*?\[(auto:[^\]]+)\]", review, re.M))
    new, still_open, regressed = [], [], []
    for f in findings:
        if f["id"] in open_ids:
            still_open.append(f)
        elif f["id"] in fixed_ids:
            f["regressed"] = True; regressed.append(f); new.append(f)
        else:
            new.append(f)

    # ------------------------------------------------------------ outputs
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    date = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d")
    total_stages = len(stage_rows); passed = sum(1 for r in stage_rows if r[2] == "pass")
    crit = sum(1 for f in new if f["severity"] == CRIT)

    def bullet(f):
        tag = " (regression of a fixed item)" if f.get("regressed") else ""
        shot = f" - screenshot: {f['screenshot']}" if f["screenshot"] else ""
        return (f"- [ ] [{f['severity']}] {f['text']}{tag} [{f['id']}] - steps: automated, see qa-results branch latest/ for commit {a.sha}"
                f" - expected: {f['expected']} - actual: {f['actual']}{shot}")

    lines = [f"## QA auto {date} (commit {a.sha})", "",
             f"Automated suite run at {now}. Full output (screenshots, logs, Maestro and instrumented results) is on the `qa-results` branch under `latest/`; a QA judge entry with the product verdict follows separately.", ""]
    if new:
        lines += [bullet(f) for f in new]
    else:
        lines.append("No new automated findings.")
    if still_open:
        lines += ["", "Still open from earlier runs: " + ", ".join(f"[{f['id']}]" for f in still_open)]
    if walkthrough:
        lines += ["", "Walkthrough steps exercised by flows: " + ", ".join(f"{app} {step} {res}" for (app, step), res in sorted(walkthrough.items()))]
    if skipped:
        lines += ["", "Not built yet or skipped: " + "; ".join(f"{app} {stage} ({det})" for app, stage, det in skipped)]
    lines += ["", f"Tested: {total_stages} device stages, {unit_tests} unit tests, {stats.get('backendTests', 0)} backend tests, coverage {(stats['coverage'].get('acs_claimed') or 0) - (stats['coverage'].get('acs_missing') or 0)}/{(stats['coverage'].get('acs_claimed') or 0)} ACs. Passed: {passed}. New issues: {len(new)}. Critical: {crit}. Product gaps: n/a (judge entry follows).", ""]
    entry = "\n".join(lines)

    summary = ["# QA suite summary", f"Commit `{a.sha}`, {now}", "",
               f"- Unit tests: {unit_tests} ({unit_bad} failing)",
               f"- Backend tests: {stats.get('backendTests', 'n/a')} ({stats.get('backendFailures', 0)} failing)",
               f"- Coverage gate: {stats['coverage']}",
               f"- Device stages: {passed}/{total_stages} passed", "", "## Stages", "", "| App | Stage | Status | Details |", "| --- | --- | --- | --- |"]
    summary += [f"| {r[0]} | {r[1]} | {r[2]} | {r[3].replace('|', '/')} |" for r in stage_rows]
    summary += ["", "## Findings", ""] + ([bullet(f) for f in new] or ["none new"]) + ["", "## REVIEW.md entry", "", entry]
    open(os.path.join(a.out, "summary.md"), "w").write("\n".join(summary))
    open(os.path.join(a.out, "review-entry.md"), "w").write(entry)
    ok_marker = os.path.join(a.out, "tested-ok")
    if device_ok:
        open(ok_marker, "w").write(a.sha)
    elif os.path.exists(ok_marker):
        os.remove(ok_marker)
    json.dump({"sha": a.sha, "at": now, "stats": stats, "stages": stage_rows, "findings": findings,
               "new": [f["id"] for f in new], "stillOpen": [f["id"] for f in still_open], "skipped": skipped,
               "walkthrough": {f"{k[0]}:{k[1]}": v for k, v in walkthrough.items()}}, open(os.path.join(a.out, "results.json"), "w"), indent=1)

    if a.apply_review:
        with open(a.review, "a", encoding="utf-8") as fh:
            fh.write("\n" + entry)
        print(f"appended QA auto entry: {len(new)} new, {len(still_open)} still open, {len(regressed)} regressions")
    return 0


if __name__ == "__main__":
    sys.exit(main())
