#!/bin/sh
# Makes sure the desktop build has a JDK it can use: Gradle 8 needs 17-24, and jpackage
# (which builds the .deb/.msi) runs on the same JDK, so we stick to 17-21. If JAVA_HOME /
# the java on PATH is outside that range, a Temurin JDK 21 is downloaded into ~/jdks once
# and used for the build only - the system Java is left alone.
#
# Usage: scripts/setup-jdk.sh > .build-env && . ./.build-env   -> sets JAVA_HOME
# (JDK_URL can override the JDK download, e.g. for a local mirror)
set -e

log() { echo "setup-jdk: $*" >&2; }

download() { # url target
  if command -v curl >/dev/null 2>&1; then curl -fsSL -o "$2" "$1"; else wget -q -O "$2" "$1"; fi
}

java_major() { # path-to-java -> major version (e.g. 21), empty if unusable
  "$1" -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = //p' | sed 's/^1\.//' | cut -d. -f1
}

JAVA_BIN=""
if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then JAVA_BIN="$JAVA_HOME/bin/java"
elif command -v java >/dev/null 2>&1; then JAVA_BIN=$(command -v java); fi
major=""
[ -n "$JAVA_BIN" ] && major=$(java_major "$JAVA_BIN")

bin_dir=""
[ -n "$JAVA_BIN" ] && bin_dir=$(dirname "$JAVA_BIN")
if [ -n "$major" ] && [ "$major" -ge 17 ] && [ "$major" -le 21 ] && [ -x "$bin_dir/javac" ] && [ -x "$bin_dir/jpackage" ]; then
  BUILD_JAVA_HOME=$(cd "$bin_dir/.." && pwd)
else
  BUILD_JAVA_HOME="$HOME/jdks/temurin-21"
  if [ ! -x "$BUILD_JAVA_HOME/bin/jpackage" ]; then
    log "Java ${major:-none} is not usable for the desktop build - installing Temurin JDK 21 into $BUILD_JAVA_HOME"
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
log "using JDK $(java_major "$BUILD_JAVA_HOME/bin/java") at $BUILD_JAVA_HOME"

# jpackage needs these to build a .deb (dpkg-deb, fakeroot).
for tool in dpkg-deb fakeroot; do
  command -v "$tool" >/dev/null 2>&1 || log "warning: '$tool' not found - packageDeb will fail (apt install dpkg-dev fakeroot)"
done

echo "export JAVA_HOME=\"$BUILD_JAVA_HOME\""
