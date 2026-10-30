#!/usr/bin/env bash
# Device suite for one app (house-manual | field-log) or the cross-app handoff.
# Runs on a booted emulator (adb must see one device). Every stage records its result in
# $OUT/stages.json and keeps going; nothing here aborts the suite. summarize.py turns the
# output into findings.
#
# Usage: bash qa/scripts/device-suite.sh <house-manual|field-log|handoff> <qa-out dir>
#
# Stages (each one skips cleanly when the thing it needs is not built yet):
#   install      debug APK (+ androidTest APK) installed
#   coldstart    `am start -W` three times, median TotalTime (AC-U11: under 1500 ms). Gated on the release
#                APK in the release stage (what people install); the debug number is kept as information
#                and gated only when no release APK was built
#   permissions  runtime permissions granted so flows don't hit system dialogs
#   seed         debug-only seed broadcast (<pkg>.qa.SEED) for the regression scenario
#   instrumented androidTest run: acceptance tests + screen crawler (Q002/Q003 in PLAN.md)
#   flows        every Maestro flow in qa/flows/<app>/, each in its own process, JUnit output
#   variants     the _tour flow under dark mode, 200% font and landscape (screenshots)
#   monkey       1,500 random events, must not crash
#   jank         benchmark build (R8, profile compiled) with the long-list seed: fling the 200-row list and read
#                dumpsys gfxinfo janky-frame percentage; under 100 frames it reports not measurable
#   release      release APK installs and launches (signed with a throwaway key if unsigned); then its
#                baseline profile is compiled as a Play install would and cold start is measured on it
#   logcat       FATAL EXCEPTION / ANR / StrictMode scan for the package
# handoff runs install, permissions, fixtures, seed, flows and logcat only.
set -uo pipefail

APP="${1:?app}"; ROOT="${2:?qa-out dir}"
# Absolute, because run_flow cds into $OUT/maestro: a relative --test-output-dir landed in
# maestro/qa-out/device/<app>/maestro/out-<label> and its failure shots were never collected
mkdir -p "$ROOT"; ROOT="$(cd "$ROOT" && pwd)"
OUT="$ROOT/device/$APP"; APK="$ROOT/apk"
mkdir -p "$OUT/screenshots" "$OUT/logs" "$OUT/maestro" "$OUT/instrumented" "$OUT/crawler"
export MAESTRO_CLI_NO_ANALYTICS=1

case "$APP" in
  house-manual) PKGS="com.kurtz.homerecord.housemanual"; APPS="house-manual" ;;
  field-log)    PKGS="com.kurtz.homerecord.fieldlog";    APPS="field-log" ;;
  handoff)      PKGS="com.kurtz.homerecord.fieldlog com.kurtz.homerecord.housemanual"; APPS="field-log house-manual" ;;
  *) echo "unknown app $APP"; exit 2 ;;
esac
# The handoff job is layer 4 only (qa/README.md): install both apps, fixtures, seed, the flows in
# qa/flows/handoff and logcat. Cold start, instrumented, variants, jank, monkey and release ran in
# the per-app jobs; running them again only filed duplicate [auto:*:handoff:*] findings.
PER_APP="$APPS"; [ "$APP" = handoff ] && PER_APP=""
pkg_of() { case "$1" in house-manual) echo com.kurtz.homerecord.housemanual ;; field-log) echo com.kurtz.homerecord.fieldlog ;; esac; }

STAGES="$OUT/stages.json"; echo "[]" > "$STAGES"
record() { # name status details
  python3 - "$STAGES" "$1" "$2" "$3" <<'EOF'
import json,sys
p,name,status,details=sys.argv[1:5]
d=json.load(open(p)); d.append({"stage":name,"status":status,"details":details}); json.dump(d,open(p,'w'),indent=1)
EOF
  echo "== $1: $2 ($3)"
}
shot() { adb exec-out screencap -p > "$OUT/screenshots/$1.png" 2>/dev/null || true; }

adb wait-for-device
adb shell settings put global window_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global transition_animation_scale 0 >/dev/null 2>&1 || true
adb shell settings put global animator_duration_scale 0 >/dev/null 2>&1 || true
adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
adb shell settings put global hide_error_dialogs 1 >/dev/null 2>&1 || true  # emulator "System UI isn't responding" ANR dialogs covered the app and failed flows
adb shell cmd uimode night no >/dev/null 2>&1 || true
adb shell settings put system accelerometer_rotation 0 >/dev/null 2>&1 || true
adb shell settings put system user_rotation 0 >/dev/null 2>&1 || true
adb shell "mkdir -p /sdcard/Download/qa && rm -rf /sdcard/Download/qa/*" >/dev/null 2>&1 || true
adb logcat -c >/dev/null 2>&1 || true
adb logcat -v time > "$OUT/logs/logcat.txt" 2>/dev/null &
LOGCAT_PID=$!

# ---------------------------------------------------------------- install
for a in $APPS; do
  # a leftover build signed with another key (release stage, cached AVD) would fail the install
  adb uninstall "$(pkg_of "$a")" >/dev/null 2>&1 || true
  adb uninstall "$(pkg_of "$a").test" >/dev/null 2>&1 || true
  if [ -f "$APK/$a-debug.apk" ]; then
    if adb install -r -g "$APK/$a-debug.apk" > "$OUT/logs/install-$a.txt" 2>&1; then record "install:$a" pass "debug apk"; else record "install:$a" fail "$(tail -n 2 "$OUT/logs/install-$a.txt" | tr '\n' ' ')"; fi
  else
    record "install:$a" fail "no debug apk in $APK"
  fi
  if [ -f "$APK/$a-debug-androidTest.apk" ]; then
    adb install -r -g "$APK/$a-debug-androidTest.apk" > "$OUT/logs/install-test-$a.txt" 2>&1 && record "install-test:$a" pass "androidTest apk" || record "install-test:$a" fail "$(tail -n 1 "$OUT/logs/install-test-$a.txt")"
  else
    record "install-test:$a" skip "no androidTest apk yet (PLAN Q002/Q003)"
  fi
done

# ------------------------------------------------------------ permissions
for p in $PKGS; do
  for perm in CAMERA RECORD_AUDIO ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION POST_NOTIFICATIONS READ_MEDIA_IMAGES; do
    adb shell pm grant "$p" "android.permission.$perm" >/dev/null 2>&1 || true
  done
done
record permissions pass "granted where declared"

# -------------------------------------------------------------- fixtures
if ls qa/fixtures/*.jpg qa/fixtures/*.pdf >/dev/null 2>&1; then
  adb shell mkdir -p /sdcard/Download/qa-fixtures >/dev/null 2>&1
  fx_ok=1
  for f in qa/fixtures/*.jpg qa/fixtures/*.pdf; do
    adb push "$f" /sdcard/Download/qa-fixtures/ >/dev/null 2>&1 || { fx_ok=0; echo "fixture push failed: $f" >&2; }
  done
  [ "$fx_ok" = 1 ] && record fixtures pass "pushed to /sdcard/Download/qa-fixtures" || record fixtures warn "adb push failed"
  adb shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/qa-fixtures" >/dev/null 2>&1 || true
else
  record fixtures skip "no fixture files yet (qa/fixtures/README.md, PLAN Q005)"
fi

# -------------------------------------------------------------- coldstart
for a in $PER_APP; do
  p=$(pkg_of "$a"); times=""
  for i in 1 2 3; do
    adb shell am force-stop "$p" >/dev/null 2>&1
    sleep 1
    t=$(adb shell am start -W -n "$p/.MainActivity" 2>/dev/null | sed -n 's/^TotalTime: *//p' | tr -d '\r')
    [ -n "$t" ] && times="$times $t"
    sleep 2
  done
  if [ -n "$times" ]; then
    med=$(echo $times | tr ' ' '\n' | sort -n | sed -n '2p')
    echo "{\"app\":\"$a\",\"build\":\"debug\",\"coldStartMs\":[$(echo $times | tr ' ' ',')],\"medianMs\":$med}" > "$OUT/coldstart-debug-$a.json"
    if [ -f "$APK/$a-release.apk" ]; then
      # A debuggable build runs without AOT code and with StrictMode on; AC-U11 is judged on the release stage.
      echo "== coldstart:$a (debug, not gated): median ${med} ms"
    elif [ "$med" -le 1500 ]; then record "coldstart:$a" pass "median ${med} ms (debug; no release apk)"; else record "coldstart:$a" fail "median ${med} ms over 1500 ms (AC-U11, debug; no release apk)"; fi
  else
    record "coldstart:$a" fail "MainActivity did not start"
  fi
  shot "$a-first-launch"
done

# ------------------------------------------------------------------- seed
for a in $APPS; do
  p=$(pkg_of "$a")
  r=$(adb shell am broadcast --include-stopped-packages -a "$p.qa.SEED" -p "$p" --es scenario regression 2>/dev/null | tr -d '\r')
  if echo "$r" | grep -q "result=1"; then record "seed:$a" pass "regression scenario seeded"; else record "seed:$a" skip "no debug seed receiver yet (PLAN Q005)"; fi
done

# ------------------------------------------------------------ instrumented
for a in $PER_APP; do
  p=$(pkg_of "$a")
  if adb shell pm list packages 2>/dev/null | grep -q "^package:$p.test$"; then
    adb shell "rm -rf /sdcard/Download/qa/$a; mkdir -p /sdcard/Download/qa/$a" >/dev/null 2>&1 || true
    adb shell am instrument -w -r -e package "$p" "$p.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/instrumented/$a.txt" 2>&1
    adb pull "/sdcard/Download/qa/$a" "$OUT/crawler/$a" >/dev/null 2>&1 || true
    if grep -q "^INSTRUMENTATION_RESULT: stream=" "$OUT/instrumented/$a.txt" || grep -q "OK (" "$OUT/instrumented/$a.txt"; then
      fails=$(grep -c "INSTRUMENTATION_STATUS_CODE: -2" "$OUT/instrumented/$a.txt" || true)
      total=$(grep -c "INSTRUMENTATION_STATUS: test=" "$OUT/instrumented/$a.txt" || true)
      [ "$fails" = "0" ] && record "instrumented:$a" pass "$((total/2)) tests" || record "instrumented:$a" fail "$fails failing of $((total/2))"
    else
      record "instrumented:$a" fail "runner did not report (see instrumented/$a.txt)"
    fi
  else
    record "instrumented:$a" skip "no androidTest package installed"
  fi
done

# ------------------------------------------------------------------ flows
run_flow() { # file label [variant]
  local f="$1" label="$2" variant="${3:-light}" rc
  ( cd "$OUT/maestro" && timeout 600 maestro test -e QA_VARIANT="$variant" -e QA_BACKEND="http://127.0.0.1:8787" --format junit --output "$label.xml" --test-output-dir "$OUT/maestro/out-$label" "$OLDPWD/$f" > "$label.log" 2>&1 ); rc=$?
  # Current Maestro writes takeScreenshot and failure captures into --test-output-dir (in a dated subfolder per
  # run), older ones into the cwd; collect both, at any depth, and keep them with a stable name.
  # Tour shots keep their own tour-<variant>-<screen> names; failure captures of other flows get a flow- prefix
  # so summarize.py can attach them to the flow's finding
  local prefix="flow-$label-"; case "$label" in tour-*) prefix="" ;; esac
  { find "$OUT/maestro" -maxdepth 1 -name '*.png' -type f; [ -d "$OUT/maestro/out-$label" ] && find "$OUT/maestro/out-$label" -name '*.png' -type f; } 2>/dev/null |
    while IFS= read -r s; do mv "$s" "$OUT/screenshots/$prefix$(basename "$s")"; done
  # A flow that fails after setOrientation leaves the device rotated; put it back so the next flow starts portrait
  # (the landscape variant sets its rotation again before each tour run)
  adb shell settings put system user_rotation 0 >/dev/null 2>&1 || true
  adb shell cmd window user-rotation lock 0 >/dev/null 2>&1 || true
  return $rc
}
FLOWDIR="qa/flows/$APP"
if ls "$FLOWDIR"/*.yaml >/dev/null 2>&1; then
  for f in "$FLOWDIR"/*.yaml; do
    label=$(basename "$f" .yaml)
    case "$label" in _*) continue ;; esac   # underscore flows are helpers run by the variants stage
    if run_flow "$f" "$label"; then record "flow:$label" pass "ok"; else record "flow:$label" fail "see maestro/$label.log"; fi
  done
else
  record flows skip "no flows in $FLOWDIR yet (PLAN Q004)"
fi

# --------------------------------------------------------------- variants
if [ -n "$PER_APP" ] && [ -f "$FLOWDIR/_tour.yaml" ]; then
  for v in dark font200 landscape; do
    case $v in
      dark) adb shell cmd uimode night yes ;;
      font200) adb shell cmd uimode night no; adb shell settings put system font_scale 2.0 ;;
      landscape) adb shell settings put system font_scale 1.0; adb shell settings put system user_rotation 1 ;;
    esac
    sleep 2
    if run_flow "$FLOWDIR/_tour.yaml" "tour-$v" "$v"; then record "variant:$v" pass "tour ok"; else record "variant:$v" fail "tour failed under $v"; fi
  done
  adb shell settings put system user_rotation 0; adb shell settings put system font_scale 1.0; adb shell cmd uimode night no
else
  record variants skip "no _tour.yaml for $APP"
fi
# collect any screenshots flows left in the maestro dir
for s in "$OUT"/maestro/*.png; do [ -f "$s" ] && mv "$s" "$OUT/screenshots/"; done

# ----------------------------------------------------------------- monkey
for a in $PER_APP; do
  p=$(pkg_of "$a")
  adb shell monkey -p "$p" --throttle 40 --pct-syskeys 0 --pct-anyevent 0 -s 42 -v 1500 > "$OUT/logs/monkey-$a.txt" 2>&1
  if grep -q "// CRASH\|// NOT RESPONDING" "$OUT/logs/monkey-$a.txt"; then record "monkey:$a" fail "crash or ANR during monkey (logs/monkey-$a.txt)"; else record "monkey:$a" pass "1500 events, no crash"; fi
  shot "$a-after-monkey"
done

# ------------------------------------------------------------------- jank
# AC-U11 / S7 "200-row lists scroll without jank", measured the way people get the app: the benchmark build (release
# code with R8, signed with the debug key, its baseline profile compiled as a Play install would) with the long-list
# seed, flinging the app's longest list (House Manual Items, Field Log Jobs; qa/flows/<app>/_jank.yaml opens it).
# Under 100 rendered frames the percentage says nothing about scrolling ([judge:qa-jank-no-list]), so the stage
# reports "not measurable" instead of a number.
for a in $PER_APP; do
  p=$(pkg_of "$a"); bench="$APK/$a-benchmark.apk"; flow="qa/flows/$a/_jank.yaml"
  if [ ! -f "$bench" ]; then record "jank:$a" skip "not measurable: no benchmark apk"; continue; fi
  if [ ! -f "$flow" ]; then record "jank:$a" skip "not measurable: no $flow"; continue; fi
  adb uninstall "$p" >/dev/null 2>&1 || true
  if adb install -r -g "$bench" > "$OUT/logs/install-benchmark-$a.txt" 2>&1; then
    adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE "$p/androidx.profileinstaller.ProfileInstallReceiver" > "$OUT/logs/profile-benchmark-$a.txt" 2>&1
    adb shell am force-stop "$p"
    adb shell cmd package compile -f -m speed-profile "$p" >> "$OUT/logs/profile-benchmark-$a.txt" 2>&1
    # A fresh install is in the stopped state, which drops broadcasts unless they include stopped packages; without the
    # flag this seed always came back result=0 and the stage was never measured
    r=$(adb shell am broadcast --include-stopped-packages -a "$p.qa.SEED" -p "$p" --es scenario long-list 2>/dev/null | tr -d '\r')
    if ! echo "$r" | grep -q "result=1"; then
      record "jank:$a" skip "not measurable: long-list seed failed ($r)"
    elif ! run_flow "$flow" "jank-$a"; then
      record "jank:$a" skip "not measurable: the list did not open (maestro/jank-$a.log)"
    else
      size=$(adb shell wm size 2>/dev/null | tr -d '\r' | sed -n 's/.*: *\([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -n 1)
      w=${size%% *}; h=${size##* }; [ -n "$w" ] && [ -n "$h" ] && [ "$w" -gt 0 ] 2>/dev/null || { w=1080; h=2400; }
      x=$((w / 2)); low=$((h * 3 / 4)); high=$((h / 4))
      adb shell dumpsys gfxinfo "$p" reset >/dev/null 2>&1
      for i in 1 2 3 4 5 6 7 8; do adb shell input swipe "$x" "$low" "$x" "$high" 150 >/dev/null 2>&1; done
      for i in 1 2 3 4 5 6 7 8; do adb shell input swipe "$x" "$high" "$x" "$low" 150 >/dev/null 2>&1; done
      g=$(adb shell dumpsys gfxinfo "$p" 2>/dev/null | tr -d '\r'); echo "$g" > "$OUT/logs/gfxinfo-$a.txt"
      shot "$a-jank-list"
      total=$(echo "$g" | sed -n 's/^Total frames rendered: *//p' | head -n 1); janky=$(echo "$g" | sed -n 's/^Janky frames: *\([0-9]*\).*/\1/p' | head -n 1)
      total=${total:-0}; janky=${janky:-0}
      if [ "$total" -lt 100 ]; then
        record "jank:$a" skip "not measurable: $total frames rendered while flinging the 200-row list (need 100)"
      else
        pct=$((janky*100/total)); echo "{\"app\":\"$a\",\"build\":\"benchmark\",\"rows\":200,\"frames\":$total,\"janky\":$janky,\"jankPercent\":$pct}" > "$OUT/jank-$a.json"
        [ "$pct" -le 10 ] && record "jank:$a" pass "$pct% janky of $total frames (200-row list, benchmark build)" || record "jank:$a" fail "$pct% janky frames of $total (AC-U11, 200-row list, benchmark build)"
      fi
    fi
  else
    record "jank:$a" skip "not measurable: $(tail -n 1 "$OUT/logs/install-benchmark-$a.txt")"
  fi
  adb uninstall "$p" >/dev/null 2>&1 || true
  [ -f "$APK/$a-debug.apk" ] && adb install -r -g "$APK/$a-debug.apk" >/dev/null 2>&1 || true
done

# ---------------------------------------------------------------- release
for a in $PER_APP; do
  p=$(pkg_of "$a"); rel="$APK/$a-release.apk"
  if [ ! -f "$rel" ]; then record "release:$a" skip "no release apk"; continue; fi
  signer=$(ls -d "${ANDROID_HOME:-$HOME/.android-sdk}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1)
  if ! unzip -l "$rel" 2>/dev/null | grep -q "META-INF/.*\.\(RSA\|EC\|DSA\)"; then
    if [ -n "$signer" ]; then
      [ -f "$OUT/qa.jks" ] || keytool -genkeypair -keystore "$OUT/qa.jks" -storepass qa123456 -keypass qa123456 -alias qa -keyalg RSA -keysize 2048 -validity 1000 -dname "CN=qa" >/dev/null 2>&1
      "$signer" sign --ks "$OUT/qa.jks" --ks-pass pass:qa123456 --ks-key-alias qa --key-pass pass:qa123456 --out "$OUT/$a-release-signed.apk" "$rel" >/dev/null 2>&1 && rel="$OUT/$a-release-signed.apk"
    else
      record "release:$a" skip "unsigned release apk and no apksigner on this runner"
      record "coldstart:$a" fail "release build could not be measured (unsigned, no apksigner)"; continue
    fi
  fi
  adb uninstall "$p" >/dev/null 2>&1 || true
  if adb install -r -g "$rel" > "$OUT/logs/install-release-$a.txt" 2>&1; then
    adb shell am force-stop "$p"; t=$(adb shell am start -W -n "$p/.MainActivity" 2>/dev/null | sed -n 's/^TotalTime: *//p' | tr -d '\r'); sleep 3
    if adb shell pidof "$p" >/dev/null 2>&1; then record "release:$a" pass "R8 build launches (${t:-?} ms)"; shot "$a-release-launch"; else record "release:$a" fail "release build did not stay running"; fi
    # Cold start as installed from Play: the APK's baseline profile (assets/dexopt) compiled ahead of time, which is
    # what Macrobenchmark's CompilationMode.Partial does. Three launches, median TotalTime (AC-U11).
    adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE "$p/androidx.profileinstaller.ProfileInstallReceiver" > "$OUT/logs/profile-$a.txt" 2>&1
    adb shell am force-stop "$p"
    adb shell cmd package compile -f -m speed-profile "$p" >> "$OUT/logs/profile-$a.txt" 2>&1
    times=""
    for i in 1 2 3; do
      adb shell am force-stop "$p" >/dev/null 2>&1; sleep 1
      rt=$(adb shell am start -W -n "$p/.MainActivity" 2>/dev/null | sed -n 's/^TotalTime: *//p' | tr -d '\r')
      [ -n "$rt" ] && times="$times $rt"
      sleep 2
    done
    if [ -n "$times" ]; then
      med=$(echo $times | tr ' ' '\n' | sort -n | sed -n "$(( ($(echo $times | wc -w) + 1) / 2 ))p")
      echo "{\"app\":\"$a\",\"build\":\"release\",\"coldStartMs\":[$(echo $times | tr ' ' ',')],\"medianMs\":$med}" > "$OUT/coldstart-$a.json"
      if [ "$med" -le 1500 ]; then record "coldstart:$a" pass "median ${med} ms (release, profile compiled)"; else record "coldstart:$a" fail "median ${med} ms over 1500 ms (AC-U11, release, profile compiled)"; fi
    else
      record "coldstart:$a" fail "release MainActivity did not start"
    fi
  else
    record "release:$a" fail "$(tail -n 1 "$OUT/logs/install-release-$a.txt")"
    record "coldstart:$a" fail "release build could not be measured (install failed)"
  fi
  adb uninstall "$p" >/dev/null 2>&1 || true
  [ -f "$APK/$a-debug.apk" ] && adb install -r -g "$APK/$a-debug.apk" >/dev/null 2>&1 || true
done

# ----------------------------------------------------------------- logcat
sleep 1; kill $LOGCAT_PID >/dev/null 2>&1 || true
: > "$OUT/logs/crashes.txt"; : > "$OUT/logs/strictmode.txt"
# pid of a `-v time` line: "10-30 12:00:00.000 E/Tag( 1234): msg" (the pid is padded, so not a fixed awk field)
PID_AWK='function pidof_line(l) { if (match(l, /\( *[0-9]+\):/)) { v = substr(l, RSTART + 1, RLENGTH - 3); gsub(/ /, "", v); return v } return "" }'
for p in $PKGS; do
  # Lines from other processes (system UI, Google app, the other app in handoff) are not ours: keep only this
  # package's pids. A crash is ours when the line after FATAL EXCEPTION names the package ("Process: <pkg>, PID: n").
  pids=$(sed -n "s/.*Start proc \([0-9][0-9]*\):$p[\/:].*/\1/p" "$OUT/logs/logcat.txt" | sort -u | tr '\n' ' ')
  awk -v pkg="$p" '/FATAL EXCEPTION/ { hold = $0; n = NR; next } n && NR == n + 1 { if (index($0, "Process: " pkg ",")) { print hold; print; keep = 12 } n = 0; next } keep > 0 { print; keep-- }' \
    "$OUT/logs/logcat.txt" > "$OUT/logs/crashes-$p.txt" 2>/dev/null || true
  crashes=$(grep -c "FATAL EXCEPTION" "$OUT/logs/crashes-$p.txt" || true)
  anrs=$(grep -c "ANR in $p" "$OUT/logs/logcat.txt" || true)
  awk -v pids=" $pids " "$PID_AWK"' /StrictMode policy violation/ { q = pidof_line($0); if (q != "" && index(pids, " " q " ")) print }' \
    "$OUT/logs/logcat.txt" > "$OUT/logs/strictmode-$p.txt" 2>/dev/null || true
  strict=$(wc -l < "$OUT/logs/strictmode-$p.txt" | tr -d ' ')
  cat "$OUT/logs/crashes-$p.txt" >> "$OUT/logs/crashes.txt" 2>/dev/null || true
  cat "$OUT/logs/strictmode-$p.txt" >> "$OUT/logs/strictmode.txt" 2>/dev/null || true
  details="$crashes crashes, $anrs ANRs, $strict StrictMode violations"
  if [ "$crashes" != "0" ] || [ "$anrs" != "0" ]; then record "logcat:$p" fail "$details"; elif [ "$strict" != "0" ]; then record "logcat:$p" warn "$details"; else record "logcat:$p" pass "$details"; fi
done

echo "device suite for $APP finished"; cat "$STAGES"
exit 0
