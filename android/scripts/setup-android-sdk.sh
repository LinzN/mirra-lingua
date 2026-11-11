#!/bin/sh
# Makes sure an Android SDK with everything the app build needs exists.
# Uses $ANDROID_HOME (or $ANDROID_SDK_ROOT); defaults to ~/android-sdk.
# On first run it downloads Google's command-line tools, accepts the SDK licences
# and installs platform + build tools. Later runs are a quick no-op.
#
# Usage: eval "$(scripts/setup-android-sdk.sh)"   -> exports ANDROID_HOME for the caller
set -e

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"
TOOLS_URL="https://dl.google.com/android/repository/$TOOLS_ZIP"
PACKAGES="platform-tools platforms;android-35 build-tools;35.0.0"

log() { echo "setup-android-sdk: $*" >&2; }

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  log "installing Android command-line tools into $SDK"
  for tool in unzip; do
    command -v "$tool" >/dev/null 2>&1 || { log "'$tool' is required (apt install $tool)"; exit 1; }
  done
  mkdir -p "$SDK/cmdline-tools"
  TMP=$(mktemp -d)
  trap 'rm -rf "$TMP"' EXIT
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL -o "$TMP/$TOOLS_ZIP" "$TOOLS_URL"
  else
    wget -q -O "$TMP/$TOOLS_ZIP" "$TOOLS_URL"
  fi
  unzip -q "$TMP/$TOOLS_ZIP" -d "$TMP"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$TMP/cmdline-tools" "$SDK/cmdline-tools/latest"
fi

# Install missing packages (licences are accepted non-interactively).
missing=""
for p in $PACKAGES; do
  dir=$(echo "$p" | tr ';' '/')
  [ -d "$SDK/$dir" ] || missing="$missing $p"
done
if [ -n "$missing" ]; then
  log "installing:$missing"
  yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
  # shellcheck disable=SC2086
  "$SDKMANAGER" --sdk_root="$SDK" $missing >&2
fi

echo "export ANDROID_HOME=\"$SDK\""
