#!/usr/bin/env bash
# TDLib Android Builder (JNI + Java Bindings/JAR) for FishIT-Player
# - Baut libtdjni.so für arm64-v8a / armeabi-v7a
# - Erzeugt/übernimmt Java-Bindings (TdApi.java, Client.java, Log.java)
# - Optional: baut tdlib-<describe>.jar aus example/java/tdlib
# - Schreibt Meta-Dateien (TDLIB_VERSION.txt + .tdlib_meta JSON)
#
# Defaults sind auf robuste CI/Lokal-Reproduzierbarkeit getrimmt.
# Neue/aktuelle Toolchain-Empfehlungen: NDK r27b (27.1.12297006), CMake 3.30+, Ninja aktuell.

set -euo pipefail
trap 'cs=$?; echo "ERROR at ${BASH_SOURCE[0]}:${LINENO} (exit $cs)" >&2; exit $cs' ERR

log(){ printf '[tdlib-android] %s\n' "$*"; }
fail(){ printf '[tdlib-android][ERROR] %s\n' "$*" >&2; exit 1; }

usage(){ cat <<'USAGE'
TDLib Android Builder (official Telegram workflow oriented)
Builds TDLib JNI (.so) for Android (arm64-v8a/armeabi-v7a) and the Java bindings JAR from upstream master/tags.

Options (supports --k=v):
  --ref=<branch|tag|commit>  Override TDLib ref (default: master)
  --latest-tag               Build newest upstream v* tag instead of master
  --abis=<comma,list>        Target ABIs (default: arm64-v8a,armeabi-v7a)
  --only-arm64 | --only-v7a  Convenience ABI toggles
  --api-level=<N>            Android API level (default: 24)
  --build-type=<Release|MinSizeRel>  CMake build type (default: MinSizeRel)
  --release                  Alias for --build-type=Release
  --minsize                  Alias for --build-type=MinSizeRel
  --ndk=<path>               Path to Android NDK (auto-detect via ANDROID_NDK_HOME/ROOT/SDK)
  --clean                    Remove previous TDLib build directories before building
  --help                     This help

Environment overrides (examples):
  TD_REF, USE_LATEST_TAG=1, ABIS, API_LEVEL, BUILD_TYPE, NDK_PATH,
  MAKE_JAR=1, STRIP_SO=1, OUT_DIR, JAVA_DST, JNI_DST, JAR_DST_DIR

Outputs (relative zum Repo-Root):
  - Java sources  : libtd/src/main/java/org/drinkless/tdlib/
  - JNI libs      : libtd/src/main/jnilibs/<abi>/libtdjni.so
  - tdlib JAR     : libtd/libs/tdlib-<describe>.jar
  - Meta          : libtd/TDLIB_VERSION.txt  +  libtd/.tdlib_meta
USAGE
}

# -------------------- Defaults --------------------
TD_REMOTE="${TD_REMOTE:-https://github.com/tdlib/td.git}"
TD_REF="${TD_REF:-master}"
ABIS="${ABIS:-arm64-v8a,armeabi-v7a}"
API_LEVEL="${ANDROID_API:-24}"
USE_LATEST_TAG="${USE_LATEST_TAG:-0}"
BUILD_TYPE="${BUILD_TYPE:-MinSizeRel}"
NDK_PATH="${NDK_PATH:-${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}}"
MAKE_JAR="${MAKE_JAR:-1}"
STRIP_SO="${STRIP_SO:-1}"

# Repo-Root früh setzen (Ordner des Scripts/..)
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")"/.. && pwd)"

# -------------------- CLI parse --------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --help|-h) usage; exit 0 ;;
    --ref|--ref=*)
      v="${1#*=}"; [[ "$v" == "$1" ]] && { v="${2:-}"; shift; }
      [[ -n "$v" ]] || fail "--ref requires a value"
      TD_REF="$v"
      ;;
    --latest-tag) USE_LATEST_TAG=1 ;;
    --abis|--abis=*)
      v="${1#*=}"; [[ "$v" == "$1" ]] && { v="${2:-}"; shift; }
      [[ -n "$v" ]] || fail "--abis requires a comma separated list"
      ABIS="$v"
      ;;
    --only-arm64) ABIS="arm64-v8a" ;;
    --only-v7a)   ABIS="armeabi-v7a" ;;
    --api-level|--api-level=*)
      v="${1#*=}"; [[ "$v" == "$1" ]] && { v="${2:-}"; shift; }
      [[ -n "$v" ]] || fail "--api-level requires a value"
      API_LEVEL="$v"
      ;;
    --build-type|--build-type=*)
      v="${1#*=}"; [[ "$v" == "$1" ]] && { v="${2:-}"; shift; }
      [[ -n "$v" ]] || fail "--build-type requires a value"
      BUILD_TYPE="$v"
      ;;
    --release) BUILD_TYPE="Release" ;;
    --minsize) BUILD_TYPE="MinSizeRel" ;;
    --ndk|--ndk=*)
      v="${1#*=}"; [[ "$v" == "$1" ]] && { v="${2:-}"; shift; }
      [[ -n "$v" ]] || fail "--ndk requires a value"
      NDK_PATH="$v"
      ;;
    --clean) CLEAN=1 ;;
    *) fail "Unknown argument: $1" ;;
  esac
  shift || true
done

# -------------------- ccache (optional) --------------------
if command -v ccache >/dev/null 2>&1; then
  : "${XDG_CACHE_HOME:=$HOME/.cache}"
  : "${CCACHE_DIR:=$XDG_CACHE_HOME/ccache}"
  export CCACHE_DIR
  mkdir -p "$CCACHE_DIR"
  # Nicht alle Compiler sind via ccache aufrufbar – Launcher-Variante ist robuster
  export CMAKE_C_COMPILER_LAUNCHER=ccache
  export CMAKE_CXX_COMPILER_LAUNCHER=ccache
  # Netter Komfort:
  ccache -p >/dev/null 2>&1 || true
  log "ccache enabled at $CCACHE_DIR"
fi

# -------------------- Pfade/Outputs --------------------
TP="$ROOT/.third_party"
TD_DIR="$TP/td"

JAVA_DST="${JAVA_DST:-$ROOT/libtd/src/main/java/org/drinkless/tdlib}"
JNI_DST="${JNI_DST:-$ROOT/libtd/src/main/jnilibs}"
JAR_DST_DIR="${JAR_DST_DIR:-$ROOT/libtd/libs}"

META_TXT="$ROOT/libtd/TDLIB_VERSION.txt"
META_JSON="$ROOT/libtd/.tdlib_meta"

mkdir -p "$TP" "$JAVA_DST" "$JNI_DST" "$JAR_DST_DIR" "$(dirname "$META_TXT")"

# ABIs normalisieren
IFS=, read -r -a ABIS_LIST <<<"$(echo "$ABIS" | sed 's/[[:space:]]//g')"
[[ ${#ABIS_LIST[@]} -gt 0 ]] || fail "No ABIs specified"

# -------------------- NDK Erkennung --------------------
if [[ -z "${NDK_PATH:-}" ]]; then
  SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  [[ -n "$SDK_ROOT" && -d "$SDK_ROOT" ]] || fail "Android SDK not found. Set ANDROID_SDK_ROOT/ANDROID_HOME or provide --ndk"
  NDK_PATH="$(ls -d "$SDK_ROOT"/ndk/* 2>/dev/null | sort -V | tail -n1 || true)"
  [[ -n "$NDK_PATH" && -d "$NDK_PATH" ]] || fail "No NDK found under $SDK_ROOT/ndk. Install one or pass --ndk"
fi
TOOLCHAIN="$NDK_PATH/build/cmake/android.toolchain.cmake"
[[ -f "$TOOLCHAIN" ]] || fail "Toolchain file missing at $TOOLCHAIN"

# -------------------- Generator / Parallelism --------------------
NPROC="$( command -v nproc >/dev/null 2>&1 && nproc || sysctl -n hw.ncpu 2>/dev/null || echo 4 )"
GENERATOR=""
if command -v ninja >/dev/null 2>&1; then
  GENERATOR="-G Ninja"
fi

# -------------------- TDLib Repo holen --------------------
if [[ ! -d "$TD_DIR/.git" ]]; then
  log "Cloning TDLib repository ($TD_REMOTE)…"
  git clone --filter=blob:none --recurse-submodules "$TD_REMOTE" "$TD_DIR"
fi

pushd "$TD_DIR" >/dev/null
git fetch origin --prune
git fetch --tags --force --prune

if [[ "${USE_LATEST_TAG}" == "1" ]]; then
  # neuestes v*-Tag finden
  LATEST_TAG="$(git tag -l 'v*' | sort -V | tail -n1 || true)"
  [[ -n "$LATEST_TAG" ]] || fail "Unable to determine latest TDLib tag"
  TD_REF="$LATEST_TAG"
fi

log "Checking out TDLib ref: $TD_REF"
git checkout --force --detach "$TD_REF"
git submodule update --init --recursive

TD_COMMIT="$(git rev-parse HEAD)"
TD_DESCRIBE="$(git describe --always --long --tags || echo "$TD_COMMIT")"
popd >/dev/null

# Optionale Säuberung
if [[ "${CLEAN:-0}" == "1" ]]; then
  log "Cleaning previous TDLib build directories"
  rm -rf "$TD_DIR/build-host" "$TD_DIR"/build-android-* || true
fi

# -------------------- Host-Build (Java-API Generator) --------------------
HOST_BUILD="$TD_DIR/build-host"
log "Configuring host build (prepare_cross_compiling + Java API generator)"
cmake -S "$TD_DIR" -B "$HOST_BUILD" \
  -DCMAKE_BUILD_TYPE=Release \
  -DTD_GENERATE_SOURCE_FILES=ON \
  -DTD_ENABLE_JNI=ON \
  $GENERATOR

cmake --build "$HOST_BUILD" --target prepare_cross_compiling -j"$NPROC" || true
# Der Java-Generator kann als eigenes Target vorhanden sein – probieren:
cmake --build "$HOST_BUILD" --target td_generate_java_api -j"$NPROC" || true

# -------------------- Java-Bindings syncen --------------------
JAVA_SRC="$TD_DIR/example/java/td/src/main/java/org/drinkless/tdlib"
if [[ -d "$JAVA_SRC" ]]; then
  log "Syncing Java bindings to libtd module"
  if command -v rsync >/dev/null 2>&1; then
    rsync -a --delete "$JAVA_SRC/" "$JAVA_DST/"
  else
    rm -rf "$JAVA_DST" && mkdir -p "$JAVA_DST"
    (cd "$JAVA_SRC" && tar cf - .) | (cd "$JAVA_DST" && tar xf -)
  fi
else
  fail "Generated Java sources not found at $JAVA_SRC (td_generate_java_api may have failed)"
fi

# -------------------- (Optional) JAR bauen --------------------
JAR_BASENAME=""
if [[ "$MAKE_JAR" == "1" ]]; then
  log "Building tdlib JAR via Gradle"
  pushd "$TD_DIR/example/java" >/dev/null
  # -PskipNative=true verhindert native Rebuilds (wir wollen nur das JAR)
  ./gradlew --no-daemon -PskipNative=true clean tdlib >/dev/null || true
  popd >/dev/null

  JAR_SRC="$(ls -1t "$TD_DIR"/example/java/tdlib/build/libs/tdlib-*.jar 2>/dev/null | head -n1 || true)"
  if [[ -f "$JAR_SRC" ]]; then
    JAR_BASENAME="tdlib-${TD_DESCRIBE}.jar"
    install -m 0644 "$JAR_SRC" "$JAR_DST_DIR/$JAR_BASENAME"
  else
    log "WARN: tdlib JAR not produced (continuing without JAR)"
  fi
fi

# -------------------- ANDROID: JNI Build pro ABI --------------------
for ABI in "${ABIS_LIST[@]}"; do
  ABI_TRIM="$(echo "$ABI" | xargs)"
  [[ -n "$ABI_TRIM" ]] || continue

  BUILD_DIR="$TD_DIR/build-android-$ABI_TRIM"
  log "Configuring TDLib for ABI $ABI_TRIM (API $API_LEVEL, $BUILD_TYPE)"
  cmake -S "$TD_DIR" -B "$BUILD_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
    -DANDROID_ABI="$ABI_TRIM" \
    -DANDROID_PLATFORM="android-$API_LEVEL" \
    -DCMAKE_BUILD_TYPE="$BUILD_TYPE" \
    -DTD_ENABLE_JNI=ON \
    -DTD_GENERATE_SOURCE_FILES=ON \
    $GENERATOR

  log "Building libtdjni.so for $ABI_TRIM"
  cmake --build "$BUILD_DIR" --target tdjni -j"$NPROC"

  SO_PATH="$(find "$BUILD_DIR" -maxdepth 3 -type f -name 'libtdjni.so' | head -n1 || true)"
  [[ -f "$SO_PATH" ]] || fail "libtdjni.so not found for ABI $ABI_TRIM"
  OUT_ABI_DIR="$JNI_DST/$ABI_TRIM"
  mkdir -p "$OUT_ABI_DIR"
  install -m 0755 "$SO_PATH" "$OUT_ABI_DIR/libtdjni.so"

  if [[ "$STRIP_SO" == "1" ]]; then
    STRIP_BIN="$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
    if [[ -x "$STRIP_BIN" ]]; then
      "$STRIP_BIN" --strip-unneeded "$OUT_ABI_DIR/libtdjni.so" || true
    fi
  fi

  log "ABI $ABI_TRIM → $OUT_ABI_DIR/libtdjni.so"
done

# -------------------- Meta schreiben --------------------
UTC_NOW="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
mkdir -p "$(dirname "$META_TXT")"
cat >"$META_TXT" <<EOF_META
TDLib Ref     : $TD_REF
TDLib Commit  : $TD_COMMIT
TDLib Describe: $TD_DESCRIBE
NDK           : $NDK_PATH
API           : $API_LEVEL
ABIs          : ${ABIS_LIST[*]}
Build Type    : $BUILD_TYPE
JAR           : ${JAR_BASENAME:-}
UTC           : $UTC_NOW
EOF_META

mkdir -p "$(dirname "$META_JSON")"
printf '{"ref":"%s","commit":"%s","describe":"%s","ndk":"%s","api":"%s","abis":"%s","build_type":"%s","jar":"%s","utc":"%s"}\n' \
  "$TD_REF" "$TD_COMMIT" "$TD_DESCRIBE" "$NDK_PATH" "$API_LEVEL" "$(IFS=,; echo "${ABIS_LIST[*]}")" "$BUILD_TYPE" "${JAR_BASENAME:-}" "$UTC_NOW" > "$META_JSON"

# -------------------- Stats / Summary --------------------
if command -v ccache >/dev/null 2>&1; then
  ccache --show-stats || true
fi

log "Build complete. Outputs:"
for ABI in "${ABIS_LIST[@]}"; do
  ABI_TRIM="$(echo "$ABI" | xargs)"
  printf ' - %s: %s\n' "$ABI_TRIM" "$JNI_DST/$ABI_TRIM/libtdjni.so"
done
printf ' = Java sources: %s\n' "$JAVA_DST"
[[ -n "${JAR_BASENAME:-}" ]] && printf ' = tdlib.jar : %s\n' "$JAR_DST_DIR/$JAR_BASENAME"
log "Done"