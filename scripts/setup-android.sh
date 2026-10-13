#!/usr/bin/env bash
# setup-android.sh — idempotent Android SDK setup for a fresh Ubuntu container.
#
# Installs (only what is missing):
#   - JDK 17 (openjdk-17-jdk-headless) plus unzip/curl/git
#   - Android command-line tools into $HOME/android-sdk/cmdline-tools/latest
#   - platform-tools, platforms;android-37, build-tools;37.0.0
# Accepts all SDK licenses, exports ANDROID_HOME/ANDROID_SDK_ROOT for this and
# future shells, and writes local.properties (sdk.dir) if a Gradle project is found.
#
# Versions were taken from https://developer.android.com/studio#command-line-tools-only
# and https://developer.android.com/about/versions/17/setup-sdk on 2026-10-13.
# Override with environment variables if they move on:
#   CMDLINE_TOOLS_BUILD, CMDLINE_TOOLS_SHA256, ANDROID_PLATFORM, ANDROID_BUILD_TOOLS,
#   ANDROID_HOME, PROJECT_DIR
#
# Usage:  bash scripts/setup-android.sh
# Then:   source "$HOME/.android-env.sh"   (new shells pick it up from ~/.bashrc)

set -euo pipefail

CMDLINE_TOOLS_BUILD="${CMDLINE_TOOLS_BUILD:-15859902}"
CMDLINE_TOOLS_SHA256="${CMDLINE_TOOLS_SHA256:-4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583}"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-android-37}"
ANDROID_BUILD_TOOLS="${ANDROID_BUILD_TOOLS:-37.0.0}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
ENV_FILE="$HOME/.android-env.sh"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${PROJECT_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"

log() { printf '\n==> %s\n' "$*"; }

# Run a command as root, with or without sudo.
as_root() {
  if [ "$(id -u)" -eq 0 ]; then "$@"; else sudo "$@"; fi
}

# ---------------------------------------------------------------- apt packages
need_apt=()
command -v unzip >/dev/null 2>&1 || need_apt+=(unzip)
command -v curl  >/dev/null 2>&1 || need_apt+=(curl)
command -v git   >/dev/null 2>&1 || need_apt+=(git)
[ -f /etc/ssl/certs/ca-certificates.crt ] || need_apt+=(ca-certificates)

have_jdk17=false
if command -v javac >/dev/null 2>&1; then
  if javac -version 2>&1 | grep -q '^javac 17\.'; then have_jdk17=true; fi
fi
if [ "$have_jdk17" = false ] && [ -x /usr/lib/jvm/java-17-openjdk-amd64/bin/javac ]; then
  have_jdk17=true
fi
[ "$have_jdk17" = true ] || need_apt+=(openjdk-17-jdk-headless)

if [ "${#need_apt[@]}" -gt 0 ]; then
  log "Installing apt packages: ${need_apt[*]}"
  export DEBIAN_FRONTEND=noninteractive
  as_root apt-get update -qq
  as_root apt-get install -y -qq --no-install-recommends "${need_apt[@]}"
else
  log "apt packages already present"
fi

# Prefer JDK 17 even if another JDK is first on PATH.
if [ -d /usr/lib/jvm/java-17-openjdk-amd64 ]; then
  JAVA_HOME_17=/usr/lib/jvm/java-17-openjdk-amd64
elif [ -d /usr/lib/jvm/java-17-openjdk-arm64 ]; then
  JAVA_HOME_17=/usr/lib/jvm/java-17-openjdk-arm64
else
  JAVA_HOME_17="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
export JAVA_HOME="$JAVA_HOME_17"
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
  curl -fsSL --retry 3 -o "$tmp/cmdline-tools.zip" "$CMDLINE_TOOLS_URL"
  echo "$CMDLINE_TOOLS_SHA256  $tmp/cmdline-tools.zip" | sha256sum -c -
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
yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true

packages=(
  "platform-tools"
  "platforms;${ANDROID_PLATFORM}"
  "build-tools;${ANDROID_BUILD_TOOLS}"
)
missing=()
for p in "${packages[@]}"; do
  dir="$ANDROID_HOME/${p//;//}"
  [ -d "$dir" ] || missing+=("$p")
done
if [ "${#missing[@]}" -gt 0 ]; then
  log "Installing SDK packages: ${missing[*]}"
  # `yes` dies of SIGPIPE when sdkmanager exits, so judge by sdkmanager's status only.
  set +o pipefail
  yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --install "${missing[@]}"
  status=${PIPESTATUS[1]}
  set -o pipefail
  [ "$status" -eq 0 ] || { echo "sdkmanager --install failed with status $status" >&2; exit "$status"; }
else
  log "SDK packages already installed: ${packages[*]}"
fi

# ------------------------------------------------------------- environment
log "Writing $ENV_FILE"
mkdir -p "$(dirname "$ENV_FILE")"
cat > "$ENV_FILE" <<EOF
# Generated by scripts/setup-android.sh
export JAVA_HOME="$JAVA_HOME"
export ANDROID_HOME="$ANDROID_HOME"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
case ":\$PATH:" in
  *":\$JAVA_HOME/bin:"*) ;;
  *) export PATH="\$JAVA_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH" ;;
esac
EOF
for rc in "$HOME/.bashrc" "$HOME/.profile"; do
  touch "$rc"
  grep -qF "$ENV_FILE" "$rc" || printf '\n[ -f "%s" ] && . "%s"\n' "$ENV_FILE" "$ENV_FILE" >> "$rc"
done

# --------------------------------------------------------- local.properties
if [ -f "$PROJECT_DIR/settings.gradle.kts" ] || [ -f "$PROJECT_DIR/settings.gradle" ]; then
  log "Writing $PROJECT_DIR/local.properties"
  printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$PROJECT_DIR/local.properties"
else
  log "No Gradle project at $PROJECT_DIR yet; skipping local.properties (rerun after T01)"
fi

log "Done. ANDROID_HOME=$ANDROID_HOME"
echo "Run:  source \"$ENV_FILE\"   before using gradlew in this shell."
