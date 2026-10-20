#!/usr/bin/env bash
# Publish a QA run to the qa-results branch.
#
# Layout of the branch (one commit, rewritten each run so the repo does not grow):
#   latest/sha                      short sha of the tested build commit
#   latest/summary.md, results.json, review-entry.md
#   latest/jvm/, backend/, apk/     logs and reports from the prepare job
#   latest/<app>/screenshots/*.png  every screenshot the device suite took
#   latest/<app>/maestro/, instrumented/, crawler/, logs/
#   history/<date>-<sha>/summary.md + results.json   small per-run record, kept across runs
#
# Runs inside the checked-out repo (a git worktree shares its credentials).
# Usage: publish-results.sh <qa-publish dir with summary.md> <short sha>
set -euo pipefail
PUB="$1"; SHA="$2"
IN="${QA_INPUT_DIR:-qa-in}"
STAMP="$(date -u +%Y-%m-%d-%H%M)-$SHA"
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
  echo "$SHA" > latest/sha
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
  done
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
