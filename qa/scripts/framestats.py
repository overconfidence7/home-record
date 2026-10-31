#!/usr/bin/env python3
"""UI-thread jank from `dumpsys gfxinfo <pkg> framestats` (AC-U11, S7).

The jank stage's gfxinfo summary counts a frame janky when the whole frame misses its deadline, which on the CI
emulator's software renderer (SwiftShader) is every frame: rasterising 1080x2400 on the CPU alone takes longer than
16 ms, so the summary measures the runner, not the app. This reads the per-frame timestamps and counts a frame janky
when the app's own UI-thread work for it (input, animation, measure, layout and recording the draw: HandleInputStart
to SyncQueued) runs past the frame interval. Frames with non-zero Flags (first draw, window resize, skipped) are left
out, as Macrobenchmark does.

    python3 qa/scripts/framestats.py gfxinfo-framestats.txt   ->  one JSON object on stdout
"""
import json
import sys

DEFAULT_INTERVAL_NS = 16_666_667


def parse(text):
    """Rows of the PROFILEDATA blocks as dicts keyed by the header names."""
    rows, header, inside = [], None, False
    for line in text.splitlines():
        line = line.strip()
        if line == "---PROFILEDATA---":
            inside, header = not inside, None
            continue
        if not inside or not line:
            continue
        cells = [c for c in line.split(",") if c != ""]
        if header is None:
            header = cells
            continue
        if len(cells) < len(header):
            continue
        try:
            rows.append({k: int(v) for k, v in zip(header, cells)})
        except ValueError:
            continue
    return rows


def percentile(values, p):
    if not values:
        return 0
    s = sorted(values)
    return s[min(len(s) - 1, int(round(p / 100 * (len(s) - 1))))]


def summarize(text):
    frames = [r for r in parse(text) if r.get("Flags", 0) == 0 and r.get("SyncQueued", 0) > r.get("HandleInputStart", 0) > 0]
    ui_ms, janky, budget_ms = [], 0, DEFAULT_INTERVAL_NS / 1e6
    for r in frames:
        interval = r.get("FrameInterval", 0) or DEFAULT_INTERVAL_NS
        budget_ms = interval / 1e6
        ui = r["SyncQueued"] - r["HandleInputStart"]
        ui_ms.append(round(ui / 1e6, 1))
        if ui > interval:
            janky += 1
    n = len(frames)
    return {
        "uiFrames": n,
        "uiJanky": janky,
        "uiJankPercent": (janky * 100 // n) if n else 0,
        "uiP50Ms": percentile(ui_ms, 50),
        "uiP90Ms": percentile(ui_ms, 90),
        "budgetMs": round(budget_ms, 1),
    }


if __name__ == "__main__":
    with open(sys.argv[1], encoding="utf-8", errors="replace") as f:
        print(json.dumps(summarize(f.read())))
