#!/usr/bin/env bash
# Publish a QA run to the qa-results branch.
#
# Layout of the branch (one commit, rewritten each run so the repo does not grow):
#   latest/sha                      full sha of the tested commit (newest non-bookkeeping commit on build)
#   latest/summary.md, results.json, review-entry.md
#   latest/jvm/, backend/, apk/     logs and reports from the prepare job
#   latest/<app>/screenshots/*.png  every screenshot the device suite took
#   latest/<app>/maestro/, instrumented/, crawler/, logs/
#   history/<date>-<sha>/summary.md + results.json   small per-run record, kept across runs
#
# Runs inside the checked-out repo (a git worktree shares its credentials).
# Usage: publish-results.sh <qa-publish dir with summary.md> <full sha>
set -euo pipefail
PUB="$1"; FULL="$2"; SHA="${FULL:0:7}"
IN="${QA_INPUT_DIR:-qa-in}"
STAMP="$(date -u +%Y-%m-%d-%H%M)-$SHA"
LIMIT=$((90 * 1024 * 1024))
KEEP_LOG=$((20 * 1024 * 1024))

# Keep every published file under GitHub's 100 MB push limit (see the call below).
cap_large_files() {
  find "$1" -type f -size +"$((LIMIT / 1024))"k -print0 | while IFS= read -r -d '' f; do
    size=$(stat -c %s "$f")
    case "$f" in
      *.txt|*.log)
        tmp="$f.tail"
        { echo "[publish-results: cut from $size bytes; last $KEEP_LOG bytes kept]"; tail -c "$KEEP_LOG" "$f"; } > "$tmp"
        mv "$tmp" "$f"
        echo "capped $f ($size bytes) to its last $KEEP_LOG bytes" ;;
      *)
        gzip -9 -f "$f"
        if [ "$(stat -c %s "$f.gz")" -gt "$LIMIT" ]; then
          rm -f "$f.gz"
          echo "Omitted by publish-results: $f was $size bytes, over GitHub's 100 MB file limit even gzipped. It is in the run's workflow artifacts." > "$f.omitted.txt"
          echo "omitted $f ($size bytes)"
        else
          echo "gzipped $f ($size bytes)"
        fi ;;
    esac
  done
}
work=$(mktemp -d)
rmdir "$work"
git worktree add -q --detach "$work" HEAD
(
  cd "$work"
  git checkout -q --orphan qa-results
  git rm -rfq . >/dev/null 2>&1 || true
  rm -rf ./* .github 2>/dev/null || true
  if git rev-parse --verify origin/qa-results >/dev/null 2>&1; then
    git archive origin/qa-results history 2>/dev/null | tar -x 2>/dev/null || true
  fi
  mkdir -p latest "history/$STAMP"
  echo "$FULL" > latest/sha
  cp "$OLDPWD/$PUB"/summary.md "$OLDPWD/$PUB"/results.json "$OLDPWD/$PUB"/review-entry.md latest/ 2>/dev/null || true
  cp "$OLDPWD/$PUB"/summary.md "$OLDPWD/$PUB"/results.json "history/$STAMP/" 2>/dev/null || true
  for d in jvm backend apk; do
    [ -d "$OLDPWD/$IN/qa-prepare/$d" ] && { mkdir -p "latest/$d"; cp -r "$OLDPWD/$IN/qa-prepare/$d/." "latest/$d/"; rm -f "latest/$d"/*.apk; }
  done
  for appdir in "$OLDPWD/$IN"/qa-device-*; do
    [ -d "$appdir" ] || continue
    app=$(basename "$appdir"); app=${app#qa-device-}
    mkdir -p "latest/$app"; cp -r "$appdir/." "latest/$app/"
    rm -f "latest/$app"/*.apk "latest/$app"/*.jks
    # Per-flow device logcats from Maestro (the suite's logs/logcat.txt covers the run) are not published
    find "latest/$app" -path '*/maestro/*' -name 'device-logcat.txt' -type f -delete 2>/dev/null || true
  done
  # GitHub refuses any file over 100 MB (GH001), and one such file rejects the whole push, so no results land at all
  # (runs 56-61, 2026-11-05..07: a 109 MB logcat.txt and a 153 MB screenshot HTML report). Logs keep their last
  # 20 MB; other large files are gzipped, and dropped with a note if still too big.
  cap_large_files latest
  ls -1d history/* 2>/dev/null | sort | head -n -60 | xargs -r rm -rf
  cat > README.md <<EOF
# qa-results

Output of the automated QA suite (.github/workflows/qa.yml). \`latest/\` is the most recent run
(commit $SHA); \`history/\` keeps the summary of the last 60 runs. This branch is rewritten on
every run; do not base work on it.
EOF
  git config user.name "homerecord-qa"; git config user.email "qa@homerecord.invalid"
  git add -A
  git commit -q -m "QA results for $SHA ($(date -u +%Y-%m-%d\ %H:%M) UTC)"
  git push -q --force origin qa-results
)
git worktree remove --force "$work" 2>/dev/null || true
git branch -D qa-results >/dev/null 2>&1 || true
echo "published qa-results for $SHA"
