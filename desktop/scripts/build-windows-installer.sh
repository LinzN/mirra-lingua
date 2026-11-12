#!/bin/sh
# Builds the Windows installer (Parla-<version>-windows-setup.exe) on Linux - no Windows machine needed.
# Called by `./gradlew distWindows`, which first collects the app jar and its Windows libraries.
#
# Usage: scripts/build-windows-installer.sh <version> <app dir with lib/> <output .exe>
#
# Needs: makensis (apt install nsis), unzip, curl or wget.
# The Windows Java runtime (Temurin JRE 21, x64) is downloaded once into ~/jdks/temurin-21-jre-windows-x64;
# JRE_WINDOWS_URL can override the download (e.g. for a local mirror).
set -e

log() { echo "build-windows-installer: $*" >&2; }

[ $# -eq 3 ] || { log "usage: $0 <version> <app dir> <output .exe>"; exit 2; }
VERSION=$1
APP_DIR=$(cd "$2" && pwd)
OUT=$3
case $OUT in /*) ;; *) OUT="$(pwd)/$OUT" ;; esac
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
PKG_DIR="$SCRIPT_DIR/../packaging"
ICON="$PKG_DIR/parla.ico"

for tool in makensis unzip; do
  command -v "$tool" >/dev/null 2>&1 || { log "'$tool' not found - install it (apt install nsis unzip)"; exit 1; }
done
[ -d "$APP_DIR/lib" ] || { log "$APP_DIR/lib is missing - run ./gradlew windowsApp first"; exit 1; }

download() { # url target
  if command -v curl >/dev/null 2>&1; then curl -fsSL -o "$2" "$1"; else wget -q -O "$2" "$1"; fi
}

# ---- Windows Java runtime (cached)
JRE="$HOME/jdks/temurin-21-jre-windows-x64"
if [ ! -f "$JRE/bin/javaw.exe" ]; then
  log "downloading the Windows Java runtime (Temurin JRE 21) into $JRE"
  TMPJ=$(mktemp -d)
  download "${JRE_WINDOWS_URL:-https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse}" "$TMPJ/jre.zip"
  unzip -q "$TMPJ/jre.zip" -d "$TMPJ/x"
  mkdir -p "$HOME/jdks"
  rm -rf "$JRE"
  mv "$TMPJ"/x/* "$JRE"
  rm -rf "$TMPJ"
  [ -f "$JRE/bin/javaw.exe" ] || { log "the download does not contain bin/javaw.exe"; exit 1; }
fi

# ---- Stage the install folder: Parla.exe, lib\, runtime\
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
mkdir -p "$STAGE/app"
cp -r "$APP_DIR/lib" "$STAGE/app/lib"
cp -r "$JRE" "$STAGE/app/runtime"

log "building Parla.exe launcher"
makensis -V2 -DVERSION="$VERSION" -DICON="$ICON" -DOUTFILE="$STAGE/app/Parla.exe" "$PKG_DIR/windows/launcher.nsi"

SIZE_KB=$(du -sk "$STAGE/app" | cut -f1)
log "building $(basename "$OUT") (${SIZE_KB} KB unpacked)"
mkdir -p "$(dirname "$OUT")"
makensis -V2 -DVERSION="$VERSION" -DAPPDIR="$STAGE/app" -DICON="$ICON" -DSIZE_KB="$SIZE_KB" \
  -DOUTFILE="$OUT" "$PKG_DIR/windows/installer.nsi"
log "done: $OUT"
