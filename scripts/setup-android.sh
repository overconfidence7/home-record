#!/usr/bin/env bash
# setup-android.sh — idempotent Android SDK setup for a fresh Ubuntu container.
#
# Safe to paste as a cloud environment "setup script" or to run from the repo.
# Installs only what is missing:
#   - a JDK >= 17 (uses the preinstalled one if present; otherwise apt installs 17)
#   - Android command-line tools into $ANDROID_HOME/cmdline-tools/latest
#   - platform-tools, platforms;android-37 (or android-37.N, whichever sdkmanager lists), build-tools;37.0.0
# Accepts SDK licenses, writes $HOME/.android-env.sh, hooks it into ~/.bashrc and
# ~/.profile, and writes local.properties (sdk.dir) if a Gradle project is found.
#
# Versions were taken from https://developer.android.com/studio#command-line-tools-only
# and https://developer.android.com/about/versions/17/setup-sdk on 2026-10-13.
# Override with environment variables if they move on:
#   CMDLINE_TOOLS_BUILD, CMDLINE_TOOLS_SHA256, ANDROID_PLATFORM, ANDROID_BUILD_TOOLS,
#   ANDROID_HOME (default /opt/android-sdk as root, else $HOME/android-sdk), PROJECT_DIR,
#   SETUP_DEBUG=1 (trace every command), SETUP_SOFT_FAIL=1 (exit 0 on failure; use
#   this when pasting the script into a cloud environment so the session still starts)
#
# Network: dl.google.com (SDK + Google Maven), plus archive.ubuntu.com only if a JDK
# or unzip/curl has to be installed.
#
# Usage:  bash scripts/setup-android.sh
# Then:   source "$HOME/.android-env.sh"

set -euo pipefail
[ -n "${SETUP_DEBUG:-}" ] && set -x

# SETUP_SOFT_FAIL=1: never block a cloud session from starting. The builder prompt
# re-runs this script inside the session if the SDK is missing, where errors are visible.
if [ -n "${SETUP_SOFT_FAIL:-}" ]; then
  trap 'echo "setup-android.sh: a step failed (see above). Session continues; the builder will retry." >&2; exit 0' ERR
fi

CMDLINE_TOOLS_BUILD="${CMDLINE_TOOLS_BUILD:-15859902}"
CMDLINE_TOOLS_SHA256="${CMDLINE_TOOLS_SHA256:-4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583}"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-android-37}"
ANDROID_BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-37.0.0}"
HOME="${HOME:-/root}"
# Root installs to a world-readable path so a non-root session user can use it too.
if [ -z "${ANDROID_HOME:-}" ]; then
  if [ "$(id -u)" -eq 0 ]; then ANDROID_HOME=/opt/android-sdk; else ANDROID_HOME="$HOME/android-sdk"; fi
fi
ENV_FILE="$HOME/.android-env.sh"

# Where the Gradle project is: explicit PROJECT_DIR, else the repo this script sits in
# (when run from a file), else the current directory.
if [ -z "${PROJECT_DIR:-}" ]; then
  script_path="${BASH_SOURCE[0]:-}"
  if [ -n "$script_path" ] && [ -f "$script_path" ]; then
    PROJECT_DIR="$(cd "$(dirname "$script_path")/.." && pwd)"
  else
    PROJECT_DIR="$PWD"
  fi
fi

log() { printf '\n==> %s\n' "$*"; }
as_root() { if [ "$(id -u)" -eq 0 ]; then "$@"; else sudo "$@"; fi; }

# ---------------------------------------------------------------- base tools
need_apt=()
command -v unzip >/dev/null 2>&1 || need_apt+=(unzip)
command -v curl  >/dev/null 2>&1 || need_apt+=(curl)

# ---------------------------------------------------------------------- JDK
# Accept any JDK >= 17 (the cloud VM ships OpenJDK 21). Install 17 only if none.
java_major() {
  "$1" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1
}
JAVA_HOME_DETECTED=""
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
  JAVA_HOME_DETECTED="$JAVA_HOME"
elif command -v javac >/dev/null 2>&1; then
  JAVA_HOME_DETECTED="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
if [ -n "$JAVA_HOME_DETECTED" ]; then
  major="$(java_major "$JAVA_HOME_DETECTED/bin/java")"
  if [ -z "$major" ] || [ "$major" -lt 17 ]; then
    JAVA_HOME_DETECTED=""
  fi
fi
[ -n "$JAVA_HOME_DETECTED" ] || need_apt+=(openjdk-17-jdk-headless)

if [ "${#need_apt[@]}" -gt 0 ]; then
  log "Installing apt packages: ${need_apt[*]}"
  export DEBIAN_FRONTEND=noninteractive
  as_root apt-get update -qq
  as_root apt-get install -y -qq --no-install-recommends "${need_apt[@]}"
  if [ -z "$JAVA_HOME_DETECTED" ]; then
    for d in /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-17-openjdk-arm64; do
      [ -x "$d/bin/javac" ] && JAVA_HOME_DETECTED="$d" && break
    done
  fi
else
  log "Base tools and JDK already present"
fi
[ -n "$JAVA_HOME_DETECTED" ] || { echo "No JDK >= 17 found after install" >&2; exit 1; }
export JAVA_HOME="$JAVA_HOME_DETECTED"
export PATH="$JAVA_HOME/bin:$PATH"
log "Using JDK at $JAVA_HOME ($(java -version 2>&1 | grep -i 'version' | head -n 1))"

# ------------------------------------------------------- command-line tools
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [ -x "$SDKMANAGER" ]; then
  log "Command-line tools already installed at $ANDROID_HOME/cmdline-tools/latest"
else
  log "Downloading command-line tools build $CMDLINE_TOOLS_BUILD"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  curl -fsSL --retry 3 --connect-timeout 20 -o "$tmp/cmdline-tools.zip" "$CMDLINE_TOOLS_URL"
  if ! echo "$CMDLINE_TOOLS_SHA256  $tmp/cmdline-tools.zip" | sha256sum -c - >/dev/null 2>&1; then
    echo "WARNING: command-line tools checksum differs from the one recorded on 2026-10-13; continuing (HTTPS from dl.google.com)." >&2
  fi
  unzip -q "$tmp/cmdline-tools.zip" -d "$tmp"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  [ -x "$SDKMANAGER" ] || { echo "sdkmanager not found after unzip" >&2; exit 1; }
fi

export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

# ------------------------------------------------------------ SDK packages
log "Accepting SDK licenses"
yes 2>/dev/null | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true

# Platform package names can carry a minor version (Android 17 ships as
# "platforms;android-37.0"), while AGP looks for "platforms/android-37". Accept
# either on disk; when installing, ask sdkmanager which name exists.
platform_base="${ANDROID_PLATFORM%%.*}"            # android-37
platform_installed=""
for d in "$ANDROID_HOME/platforms/${platform_base}" "$ANDROID_HOME/platforms/${platform_base}".*; do
  [ -d "$d" ] && { platform_installed="$(basename "$d")"; break; }
done
platform_pkg=""
if [ -z "$platform_installed" ]; then
  # Prefer the plain name, then the .0 minor release (what compileSdk=37 expects);
  # later minors (37.1, 37.2) are for compileSdkMinor and Gradle rejects them here.
  listed="$("$SDKMANAGER" --sdk_root="$ANDROID_HOME" --list 2>/dev/null \
    | grep -oE "platforms;${platform_base}(\.[0-9]+)?" | sort -uV || true)"
  platform_pkg=""
  for cand in "platforms;${platform_base}" "platforms;${platform_base}.0"; do
    if printf '%s\n' "$listed" | grep -qx "$cand"; then platform_pkg="$cand"; break; fi
  done
  [ -n "$platform_pkg" ] || platform_pkg="$(printf '%s\n' "$listed" | head -n 1)"
  [ -n "$platform_pkg" ] || platform_pkg="platforms;${ANDROID_PLATFORM}"
  log "Platform package resolved to $platform_pkg"
fi

packages=("platform-tools" "build-tools;${ANDROID_BUILD_TOOLS}")
missing=()
[ -d "$ANDROID_HOME/platform-tools" ] || missing+=("platform-tools")
[ -n "$platform_installed" ] || missing+=("$platform_pkg")
[ -d "$ANDROID_HOME/build-tools/${ANDROID_BUILD_TOOLS}" ] || missing+=("build-tools;${ANDROID_BUILD_TOOLS}")
if [ "${#missing[@]}" -gt 0 ]; then
  log "Installing SDK packages: ${missing[*]}"
  # `yes` dies of SIGPIPE when sdkmanager exits, so judge by sdkmanager's status only.
  set +o pipefail
  yes 2>/dev/null | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --install "${missing[@]}"
  status=${PIPESTATUS[1]}
  set -o pipefail
  [ "$status" -eq 0 ] || { echo "sdkmanager --install failed with status $status" >&2; exit "$status"; }
else
  log "SDK packages already installed: ${packages[*]} platforms;${platform_installed}"
fi

# AGP 8.13 resolves compileSdk 37 to platforms/android-37.0 by itself. Do NOT add an
# android-37 symlink or copy next to it: with both present AGP fails with
# "Failed to find target with hash string 'android-37'" and re-downloads 37.0 every build.
if [ -e "$ANDROID_HOME/platforms/${platform_base}" ] && [ -d "$ANDROID_HOME/platforms/${platform_base}.0" ]; then
  rm -rf "$ANDROID_HOME/platforms/${platform_base}"
fi

# ------------------------------------------------------------- environment
# Gradle's download cache lives outside $HOME when root installs, so a non-root
# session user shares it. The cloud snapshot keeps it between hourly runs.
if [ -z "${GRADLE_USER_HOME:-}" ]; then
  if [ "$(id -u)" -eq 0 ]; then GRADLE_USER_HOME=/opt/gradle-home; else GRADLE_USER_HOME="$HOME/.gradle"; fi
fi
export GRADLE_USER_HOME
mkdir -p "$GRADLE_USER_HOME"

log "Writing $ENV_FILE"
mkdir -p "$(dirname "$ENV_FILE")"
cat > "$ENV_FILE" <<EOF
# Generated by scripts/setup-android.sh
export JAVA_HOME="$JAVA_HOME"
export ANDROID_HOME="$ANDROID_HOME"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$GRADLE_USER_HOME"
case ":\$PATH:" in
  *":\$ANDROID_HOME/platform-tools:"*) ;;
  *) export PATH="\$JAVA_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH" ;;
esac
EOF
for rc in "$HOME/.bashrc" "$HOME/.profile"; do
  touch "$rc"
  grep -qF "$ENV_FILE" "$rc" || printf '\n[ -f "%s" ] && . "%s"\n' "$ENV_FILE" "$ENV_FILE" >> "$rc"
done
# Also make the SDK findable and writable for other users (Gradle may add packages).
if [ "$(id -u)" -eq 0 ]; then
  [ -d /etc/profile.d ] && cp "$ENV_FILE" /etc/profile.d/android-env.sh 2>/dev/null || true
  chmod -R a+rwX "$ANDROID_HOME" 2>/dev/null || true
fi

# --------------------------------------------------------- local.properties
if [ -f "$PROJECT_DIR/settings.gradle.kts" ] || [ -f "$PROJECT_DIR/settings.gradle" ]; then
  log "Writing $PROJECT_DIR/local.properties"
  printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$PROJECT_DIR/local.properties"
else
  log "No Gradle project at $PROJECT_DIR; skipping local.properties (Gradle will use ANDROID_HOME)"
fi

# ------------------------------------------------------ Gradle cache warm-up
# Hourly cloud runs start from the environment snapshot. Pre-downloading the
# wrapper, plugins and dependency set here means a run downloads only what is
# new, which keeps Maven Central's rate limiting (HTTP 429) out of the builder.
# Capped so the whole script stays inside the snapshot time budget; a partial
# warm-up still leaves every finished download in the cache.
# Opt-in with SETUP_WARM_GRADLE=1 (it does not fit the cloud snapshot budget next to
# the SDK install, so the cloud relies on the Maven mirror instead); WARM_SECONDS caps it.
if [ "${SETUP_WARM_GRADLE:-0}" = "1" ] && [ -x "$PROJECT_DIR/gradlew" ]; then
  warm_cap="${WARM_SECONDS:-150}"
  log "Warming Gradle cache in $GRADLE_USER_HOME (cap ${warm_cap}s; log: /tmp/gradle-warm.log)"
  targets=()
  for m in $(sed -n 's/^include(\(.*\))/\1/p' "$PROJECT_DIR/settings.gradle.kts" | tr -d '" ' | tr ',' ' '); do
    targets+=("${m}:dependencies")
  done
  [ "${#targets[@]}" -gt 0 ] || targets=(dependencies)
  if ( cd "$PROJECT_DIR" && timeout "$warm_cap" ./gradlew --no-daemon --max-workers=1 -q \
        "${targets[@]}" >/tmp/gradle-warm.log 2>&1 ); then
    log "Gradle cache warm"
  else
    log "Gradle warm-up stopped early (status $?); partial cache kept"
  fi
  [ "$(id -u)" -eq 0 ] && chmod -R a+rwX "$GRADLE_USER_HOME" 2>/dev/null || true
fi

log "Done. ANDROID_HOME=$ANDROID_HOME  JAVA_HOME=$JAVA_HOME  GRADLE_USER_HOME=$GRADLE_USER_HOME"
echo "Run:  source \"$ENV_FILE\"   before using gradlew in this shell."
