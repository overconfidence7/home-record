#!/usr/bin/env bash
# Runs ./gradlew with the given tasks. On failure, emits the "What went wrong" block
# (and the first failing test names) as a GitHub Actions error annotation so the
# cause is visible through the check-runs REST API, then exits non-zero.
set -uo pipefail
log="$(mktemp)"
./gradlew --no-daemon --console=plain "$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ]; then
  {
    grep -n -A25 -m3 'What went wrong' "$log"
    echo '--- failing tests ---'
    grep -E '^\S.* > .* FAILED$' "$log" | head -20
    echo '--- first errors ---'
    grep -E '^e: |error:|Exception' "$log" | head -20
    echo '--- sdk lines ---'
    grep -iE 'package\.xml|platforms|sdk.*(fail|warn|ignor|parse|schema|unknown|invalid)|android-37|repository2|license' "$log" | grep -v '^\s*at ' | head -40
  } > "$log.summary" 2>/dev/null
  msg="$(sed ':a;N;$!ba;s/\n/%0A/g' "$log.summary" | cut -c1-6000)"
  echo "::error title=Gradle failed ($*)::$msg"
  exit "$status"
fi
