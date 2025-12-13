#!/usr/bin/env bash
set -euo pipefail

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-"$HOME/.android-sdk"}}"
CMDLINE_DIR="${SDK_ROOT}/cmdline-tools"
CMDLINE_BIN="${CMDLINE_DIR}/latest/bin"
SDKMANAGER="${CMDLINE_BIN}/sdkmanager"
CMDLINE_TOOLS_ZIP="https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip"
REQUIRED_PACKAGES=(
  "platform-tools"
  "platforms;android-35"
  "build-tools;35.0.0"
)

for dependency in curl unzip; do
  if ! command -v "${dependency}" >/dev/null 2>&1; then
    echo "Missing required dependency: ${dependency}" >&2
    exit 1
  fi
done

mkdir -p "${SDK_ROOT}"

if [ ! -x "${SDKMANAGER}" ]; then
  echo "Installing Android cmdline-tools into ${CMDLINE_DIR}" >&2
  temp_dir="$(mktemp -d)"
  curl -fo "${temp_dir}/cmdline-tools.zip" "${CMDLINE_TOOLS_ZIP}"
  unzip -q "${temp_dir}/cmdline-tools.zip" -d "${temp_dir}"

  rm -rf "${CMDLINE_DIR}/latest"
  mkdir -p "${CMDLINE_DIR}"
  mv "${temp_dir}/cmdline-tools" "${CMDLINE_DIR}/latest"
  rm -rf "${temp_dir}"
fi

export ANDROID_SDK_ROOT="${SDK_ROOT}"
export ANDROID_HOME="${SDK_ROOT}"
export PATH="${CMDLINE_BIN}:${SDK_ROOT}/platform-tools:${PATH}"

yes | "${SDKMANAGER}" --licenses >/dev/null
"${SDKMANAGER}" --install "${REQUIRED_PACKAGES[@]}"
