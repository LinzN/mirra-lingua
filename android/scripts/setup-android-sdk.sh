#!/bin/sh
# Makes sure everything the Android build needs exists:
#  1. a JDK Gradle 8.x can run on (17-24). If JAVA_HOME / the java on PATH is outside that
#     range (e.g. Java 25), a Temurin JDK 21 is downloaded into ~/jdks once and used for the
#     build only - the system Java is left alone.
#  2. an Android SDK in $ANDROID_HOME (or $ANDROID_SDK_ROOT), default ~/android-sdk: on first
#     run Google's command-line tools are downloaded, the SDK licences accepted and platform +
#     build tools installed.
# Later runs are a quick no-op.
#
# Usage: scripts/setup-android-sdk.sh > .build-env && . ./.build-env   -> sets JAVA_HOME and ANDROID_HOME
# (JDK_URL can override the JDK download, e.g. for a local mirror)
set -e

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"
TOOLS_URL="https://dl.google.com/android/repository/$TOOLS_ZIP"
PACKAGES="platform-tools platforms;android-35 build-tools;35.0.0"

log() { echo "setup-android-sdk: $*" >&2; }

download() { # url target
  if command -v curl >/dev/null 2>&1; then curl -fsSL -o "$2" "$1"; else wget -q -O "$2" "$1"; fi
}

java_major() { # path-to-java -> major version (e.g. 21), empty if unusable
  "$1" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = //p' | sed 's/^1\.//' | cut -d. -f1
}

# ---------------------------------------------------------------- JDK for Gradle
JAVA_BIN=""
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then JAVA_BIN="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then JAVA_BIN=$(command -v java); fi
major=""
[ -n "$JAVA_BIN" ] && major=$(java_major "$JAVA_BIN")

if [ -n "$major" ] && [ "$major" -ge 17 ] && [ "$major" -le 24 ] && [ -x "$(dirname "$JAVA_BIN")/javac" ]; then
  BUILD_JAVA_HOME=$(cd "$(dirname "$JAVA_BIN")/.." && pwd)
else
  BUILD_JAVA_HOME="$HOME/jdks/temurin-21"
  if [ ! -x "$BUILD_JAVA_HOME/bin/javac" ]; then
    log "Java ${major:-none} is not supported by Gradle 8 - installing Temurin JDK 21 into $BUILD_JAVA_HOME"
    case $(uname -m) in
      x86_64|amd64) arch=x64 ;;
      aarch64|arm64) arch=aarch64 ;;
      *) log "unsupported CPU architecture $(uname -m); install a JDK 21 and set JAVA_HOME"; exit 1 ;;
    esac
    TMPJ=$(mktemp -d)
    download "${JDK_URL:-https://api.adoptium.net/v3/binary/latest/21/ga/linux/$arch/jdk/hotspot/normal/eclipse}" "$TMPJ/jdk.tar.gz"
    tar -xzf "$TMPJ/jdk.tar.gz" -C "$TMPJ"
    mkdir -p "$HOME/jdks"
    rm -rf "$BUILD_JAVA_HOME"
    mv "$TMPJ"/jdk-21* "$BUILD_JAVA_HOME"
    rm -rf "$TMPJ"
  fi
fi
export JAVA_HOME="$BUILD_JAVA_HOME"
log "using JDK $(java_major "$JAVA_HOME/bin/java") at $JAVA_HOME"

# ---------------------------------------------------------------- Android SDK

SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  log "installing Android command-line tools into $SDK"
  for tool in unzip; do
    command -v "$tool" >/dev/null 2>&1 || { log "'$tool' is required (apt install $tool)"; exit 1; }
  done
  mkdir -p "$SDK/cmdline-tools"
  TMP=$(mktemp -d)
  trap 'rm -rf "$TMP"' EXIT
  download "$TOOLS_URL" "$TMP/$TOOLS_ZIP"
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

echo "export JAVA_HOME=\"$JAVA_HOME\""
echo "export ANDROID_HOME=\"$SDK\""
