#!/usr/bin/env bash

# WSL2 local builder for Gradle build/test without touching Windows/Android Studio.

# - Downloads a local JDK 17 and Android SDK cmdline-tools into the project.

# - Uses ANDROID_SDK_ROOT + JAVA_HOME scoped to this process only.

# - Installs required SDK packages based on app/build.gradle(.kts) (compileSdk/buildTools).

# - Runs: ./gradlew --version && ./gradlew build && ./gradlew test

# Save as: wsl-build.sh, then run: bash wsl-build.sh

set -Eeuo pipefail

log()  { printf "\033[1;32m[INFO]\033[0m %s\n" "$*"; }
warn() { printf "\033[1;33m[WARN]\033[0m %s\n" "$"; }
err()  { printf "\033[1;31m[ERR ]\033[0m %s\n" "$*" >&2; }
die()  { err "$"; exit 1; }

# 1) Preconditions

[[ -f "./gradlew" ]] || die "Bitte im Projektordner ausführen (gradlew nicht gefunden)."

for dep in curl unzip tar grep sed awk; do
command -v "$dep" >/dev/null 2>&1 || die "Benötigtes Tool fehlt: $dep (sudo apt-get install -y $dep)"
done

if ! grep -qi microsoft /proc/version 2>/dev/null; then
warn "WSL nicht erkannt. Skript sollte auch in normalem Linux laufen, aber ist für WSL2 gedacht."
fi

# 2) Local tool dirs (keine globalen Änderungen)

ROOT="$(pwd)"
SDK_DIR="$ROOT/.wsl-android-sdk"
JDK_DIR="$ROOT/.wsl-java-17"
GRADLE_USER_HOME="$ROOT/.wsl-gradle"
TMP_DIR="$ROOT/.wsl-tmp"
mkdir -p "$SDK_DIR" "$JDK_DIR" "$GRADLE_USER_HOME" "$TMP_DIR"

cleanup() {
rm -f "$TMP_DIR"/jdk.tgz "$TMP_DIR"/cmdtools.zip 2>/dev/null || true
rmdir "$TMP_DIR" 2>/dev/null || true
}
trap cleanup EXIT

# 3) Fetch JDK 17 locally (Temurin)

if [[ ! -x "$JDK_DIR/bin/java" ]]; then
log "Lade lokale JDK 17 (Temurin) ..."
JDK_URL="https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
curl -fsSL "$JDK_URL" -o "$TMP_DIR/jdk.tgz"

# Robust entpacken (Tarball hat Top-Level-Verzeichnis)

STAGE_DIR="$TMP_DIR/jdk-extract"
rm -rf "$STAGE_DIR"; mkdir -p "$STAGE_DIR"
tar -xzf "$TMP_DIR/jdk.tgz" -C "$STAGE_DIR"

# Move contents to JDK_DIR

FIRST_SUB="$(find "$STAGE_DIR" -mindepth 1 -maxdepth 1 -type d | head -n1 || true)"
if [[ -n "${FIRST_SUB:-}" ]] && [[ -d "$FIRST_SUB" ]]; then
    rm -rf "$JDK_DIR"
    mkdir -p "$JDK_DIR"
    shopt -s dotglob
    mv "$FIRST_SUB"/* "$JDK_DIR"/
    shopt -u dotglob
else
    die "Entpacken der JDK-Archive fehlgeschlagen."
fi
log "JDK 17 bereit unter $JDK_DIR"
fi

export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"

# 4) Fetch Android cmdline-tools locally

SDKMGR_BIN="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$SDKMGR_BIN" ]]; then
log "Lade Android cmdline-tools ..."

# Hinweis: Versionsnummer kann sich ändern; aktualisiere bei Bedarf.

CMDTOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
curl -fsSL "$CMDTOOLS_URL" -o "$TMP_DIR/cmdtools.zip"
mkdir -p "$SDK_DIR/cmdline-tools"

# Entpacken in temp, dann in 'latest' verschieben (benötigte Struktur)

CT_TEMP="$SDK_DIR/cmdline-tools/.tmp"
rm -rf "$CT_TEMP"; mkdir -p "$CT_TEMP"
unzip -q "$TMP_DIR/cmdtools.zip" -d "$CT_TEMP"
if [[ -d "$CT_TEMP/cmdline-tools" ]]; then
    rm -rf "$SDK_DIR/cmdline-tools/latest"
    mkdir -p "$SDK_DIR/cmdline-tools/latest"
    shopt -s dotglob
    mv "$CT_TEMP/cmdline-tools/"* "$SDK_DIR/cmdline-tools/latest"/
    shopt -u dotglob
    rm -rf "$CT_TEMP"
else
    die "cmdline-tools Struktur unerwartet. Konnte 'cmdline-tools' im ZIP nicht finden."
fi
log "cmdline-tools installiert."
fi

export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

# 5) Ermittele compileSdk / buildTools aus Gradle-Dateien

APP_GRADLE=""
if [[ -f "app/build.gradle.kts" ]]; then
APP_GRADLE="app/build.gradle.kts"
elif [[ -f "app/build.gradle" ]]; then
APP_GRADLE="app/build.gradle"
fi

COMPILE_SDK=""
BUILD_TOOLS=""
if [[ -n "$APP_GRADLE" ]]; then

# compileSdk (KTS und Groovy Varianten)

COMPILE_SDK="$(grep -E 'compileSdk(|Version)\s*[= ]' "$APP_GRADLE" | head -n1 | grep -Eo '[0-9]{2}' || true)"

# buildToolsVersion "x.y.z"

BUILD_TOOLS="$(grep -E 'buildToolsVersion' "$APP_GRADLE" | head -n1 | sed -E 's/."([0-9.]+)"./\1/' || true)"
fi

if [[ -z "$COMPILE_SDK" ]]; then
COMPILE_SDK="34"
warn "compileSdk nicht gefunden – standardisiere auf $COMPILE_SDK"
log "compileSdk erkannt: $COMPILE_SDK"
fi

if [[ -z "$BUILD_TOOLS" ]]; then
case "$COMPILE_SDK" in
    35) BUILD_TOOLS="35.0.0" ;;
    34) BUILD_TOOLS="34.0.0" ;;
    33) BUILD_TOOLS="33.0.2" ;;
    32) BUILD_TOOLS="32.0.0" ;;
    *)  BUILD_TOOLS="34.0.0" ;;
esac
warn "buildToolsVersion nicht gefunden – verwende $BUILD_TOOLS"
else
log "buildToolsVersion erkannt: $BUILD_TOOLS"
fi

# 6) Installiere benötigte Android SDK Pakete

log "Akzeptiere Android-Lizenzen ..."
yes | "$SDKMGR_BIN" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null || true

log "Installiere SDK Pakete: platform-tools, platforms;android-$COMPILE_SDK, build-tools;$BUILD_TOOLS ..."
"$SDKMGR_BIN" --sdk_root="$ANDROID_SDK_ROOT" \
"platform-tools" \
"platforms;android-$COMPILE_SDK" \
"build-tools;$BUILD_TOOLS"

# 7) Gradle Konfiguration lokal halten

export GRADLE_USER_HOME="$GRADLE_USER_HOME"
chmod +x ./gradlew

# 8) Build & Test

log "Gradle Version prüfen ..."
./gradlew --version --no-daemon

log "Starte Gradle build ..."
./gradlew build --no-daemon

log "Starte Gradle test ..."
./gradlew test --no-daemon

log "Fertig. Java unter $JAVA_HOME, Android SDK unter $ANDROID_SDK_ROOT (projektlokal)."
