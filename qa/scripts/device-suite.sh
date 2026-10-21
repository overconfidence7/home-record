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
#   coldstart    `am start -W` three times, median TotalTime (AC-U11: under 1500 ms)
#   permissions  runtime permissions granted so flows don't hit system dialogs
#   seed         debug-only seed broadcast (<pkg>.qa.SEED) for the regression scenario
#   instrumented androidTest run: acceptance tests + screen crawler (Q002/Q003 in PLAN.md)
#   flows        every Maestro flow in qa/flows/<app>/, each in its own process, JUnit output
#   variants     the _tour flow under dark mode, 200% font and landscape (screenshots)
#   jank         scroll the longest list and read dumpsys gfxinfo janky-frame percentage
#   monkey       1,500 random events, must not crash
#   release      release APK installs and launches (signed with a throwaway key if unsigned)
#   logcat       FATAL EXCEPTION / ANR / StrictMode scan for the package
set -uo pipefail

APP="${1:?app}"; ROOT="${2:?qa-out dir}"
OUT="$ROOT/device/$APP"; APK="$ROOT/apk"
mkdir -p "$OUT/screenshots" "$OUT/logs" "$OUT/maestro" "$OUT/instrumented" "$OUT/crawler"
export MAESTRO_CLI_NO_ANALYTICS=1

case "$APP" in
  house-manual) PKGS="com.kurtz.homerecord.housemanual"; APPS="house-manual" ;;
  field-log)    PKGS="com.kurtz.homerecord.fieldlog";    APPS="field-log" ;;
  handoff)      PKGS="com.kurtz.homerecord.fieldlog com.kurtz.homerecord.housemanual"; APPS="field-log house-manual" ;;
  *) echo "unknown app $APP"; exit 2 ;;
esac
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
for a in $APPS; do
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
    echo "{\"app\":\"$a\",\"coldStartMs\":[$(echo $times | tr ' ' ',')],\"medianMs\":$med}" > "$OUT/coldstart-$a.json"
    if [ "$med" -le 1500 ]; then record "coldstart:$a" pass "median ${med} ms"; else record "coldstart:$a" fail "median ${med} ms over 1500 ms (AC-U11)"; fi
  else
    record "coldstart:$a" fail "MainActivity did not start"
  fi
  shot "$a-first-launch"
done

# ------------------------------------------------------------------- seed
for a in $APPS; do
  p=$(pkg_of "$a")
  r=$(adb shell am broadcast -a "$p.qa.SEED" -p "$p" --es scenario regression 2>/dev/null | tr -d '\r')
  if echo "$r" | grep -q "result=1"; then record "seed:$a" pass "regression scenario seeded"; else record "seed:$a" skip "no debug seed receiver yet (PLAN Q005)"; fi
done

# ------------------------------------------------------------ instrumented
for a in $APPS; do
  p=$(pkg_of "$a")
  if adb shell pm list packages 2>/dev/null | grep -q "^package:$p.test$"; then
    adb shell am instrument -w -r -e package "$p" "$p.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/instrumented/$a.txt" 2>&1
    adb pull /sdcard/Download/qa "$OUT/crawler/$a" >/dev/null 2>&1 || true
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
  # Current Maestro writes takeScreenshot and failure captures into --test-output-dir, older ones into the cwd;
  # collect both and keep them with a stable name
  # tour shots keep their own tour-<variant>-<screen> names; failure captures of other flows get a flow- prefix
  local prefix="flow-$label-"; case "$label" in tour-*) prefix="" ;; esac
  for s in "$OUT"/maestro/*.png "$OUT/maestro/out-$label"/*.png; do [ -f "$s" ] && mv "$s" "$OUT/screenshots/$prefix$(basename "$s")"; done
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
if [ -f "$FLOWDIR/_tour.yaml" ]; then
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

# ------------------------------------------------------------------- jank
for a in $APPS; do
  p=$(pkg_of "$a")
  adb shell dumpsys gfxinfo "$p" reset >/dev/null 2>&1
  adb shell am start -n "$p/.MainActivity" >/dev/null 2>&1; sleep 2
  for i in 1 2 3 4 5 6; do adb shell input swipe 540 1600 540 600 200 >/dev/null 2>&1; done
  for i in 1 2 3; do adb shell input swipe 540 600 540 1600 200 >/dev/null 2>&1; done
  g=$(adb shell dumpsys gfxinfo "$p" 2>/dev/null | tr -d '\r')
  total=$(echo "$g" | sed -n 's/^Total frames rendered: *//p' | head -n 1); janky=$(echo "$g" | sed -n 's/^Janky frames: *\([0-9]*\).*/\1/p' | head -n 1)
  if [ -n "$total" ] && [ "$total" -gt 0 ]; then
    pct=$((janky*100/total)); echo "{\"app\":\"$a\",\"frames\":$total,\"janky\":$janky,\"jankPercent\":$pct}" > "$OUT/jank-$a.json"
    [ "$pct" -le 10 ] && record "jank:$a" pass "$pct% janky of $total frames" || record "jank:$a" fail "$pct% janky frames of $total (AC-U11)"
  else
    record "jank:$a" skip "gfxinfo reported no frames"
  fi
done

# ----------------------------------------------------------------- monkey
for a in $APPS; do
  p=$(pkg_of "$a")
  adb shell monkey -p "$p" --throttle 40 --pct-syskeys 0 --pct-anyevent 0 -s 42 -v 1500 > "$OUT/logs/monkey-$a.txt" 2>&1
  if grep -q "// CRASH\|// NOT RESPONDING" "$OUT/logs/monkey-$a.txt"; then record "monkey:$a" fail "crash or ANR during monkey (logs/monkey-$a.txt)"; else record "monkey:$a" pass "1500 events, no crash"; fi
  shot "$a-after-monkey"
done

# ---------------------------------------------------------------- release
for a in $APPS; do
  p=$(pkg_of "$a"); rel="$APK/$a-release.apk"
  if [ ! -f "$rel" ]; then record "release:$a" skip "no release apk"; continue; fi
  signer=$(ls -d "${ANDROID_HOME:-$HOME/.android-sdk}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n 1)
  if ! unzip -l "$rel" 2>/dev/null | grep -q "META-INF/.*\.\(RSA\|EC\|DSA\)"; then
    if [ -n "$signer" ]; then
      [ -f "$OUT/qa.jks" ] || keytool -genkeypair -keystore "$OUT/qa.jks" -storepass qa123456 -keypass qa123456 -alias qa -keyalg RSA -keysize 2048 -validity 1000 -dname "CN=qa" >/dev/null 2>&1
      "$signer" sign --ks "$OUT/qa.jks" --ks-pass pass:qa123456 --ks-key-alias qa --key-pass pass:qa123456 --out "$OUT/$a-release-signed.apk" "$rel" >/dev/null 2>&1 && rel="$OUT/$a-release-signed.apk"
    else
      record "release:$a" skip "unsigned release apk and no apksigner on this runner"; continue
    fi
  fi
  adb uninstall "$p" >/dev/null 2>&1 || true
  if adb install -r -g "$rel" > "$OUT/logs/install-release-$a.txt" 2>&1; then
    adb shell am force-stop "$p"; t=$(adb shell am start -W -n "$p/.MainActivity" 2>/dev/null | sed -n 's/^TotalTime: *//p' | tr -d '\r'); sleep 3
    if adb shell pidof "$p" >/dev/null 2>&1; then record "release:$a" pass "R8 build launches (${t:-?} ms)"; shot "$a-release-launch"; else record "release:$a" fail "release build did not stay running"; fi
  else
    record "release:$a" fail "$(tail -n 1 "$OUT/logs/install-release-$a.txt")"
  fi
  adb uninstall "$p" >/dev/null 2>&1 || true
  [ -f "$APK/$a-debug.apk" ] && adb install -r -g "$APK/$a-debug.apk" >/dev/null 2>&1 || true
done

# ----------------------------------------------------------------- logcat
sleep 1; kill $LOGCAT_PID >/dev/null 2>&1 || true
for p in $PKGS; do
  crashes=$(grep -c "FATAL EXCEPTION" "$OUT/logs/logcat.txt" || true)
  anrs=$(grep -c "ANR in $p" "$OUT/logs/logcat.txt" || true)
  strict=$(grep -c "StrictMode policy violation" "$OUT/logs/logcat.txt" || true)
  grep -n -A 12 "FATAL EXCEPTION" "$OUT/logs/logcat.txt" > "$OUT/logs/crashes.txt" 2>/dev/null || true
  grep -n -A 6 "StrictMode policy violation" "$OUT/logs/logcat.txt" > "$OUT/logs/strictmode.txt" 2>/dev/null || true
  details="$crashes crashes, $anrs ANRs, $strict StrictMode violations"
  if [ "$crashes" != "0" ] || [ "$anrs" != "0" ]; then record "logcat:$p" fail "$details"; elif [ "$strict" != "0" ]; then record "logcat:$p" warn "$details"; else record "logcat:$p" pass "$details"; fi
done

echo "device suite for $APP finished"; cat "$STAGES"
exit 0
