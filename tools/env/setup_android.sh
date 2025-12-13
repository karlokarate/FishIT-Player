#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)"
REPO_ROOT="$(git -C "${SCRIPT_DIR}" rev-parse --show-toplevel 2>/dev/null || cd "${SCRIPT_DIR}/../.." && pwd)"
CACHE_DIR="${REPO_ROOT}/.cache/android-sdk"
SDK_VERSION_ZIP="commandlinetools-linux-10406996_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/${SDK_VERSION_ZIP}"

mkdir -p "${CACHE_DIR}"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-"${HOME}/.android-sdk"}}"
CMDLINE_DIR="${SDK_ROOT}/cmdline-tools"
CMDLINE_BIN="${CMDLINE_DIR}/latest/bin"
SDKMANAGER="${CMDLINE_BIN}/sdkmanager"

REQUIRED_PACKAGES=(
  "platform-tools"
  "platforms;android-35"
  "build-tools;35.0.0"
)

ensure_dependency() {
  local dep="$1"
  if command -v "${dep}" >/dev/null 2>&1; then
    return 0
  fi

  if command -v apt-get >/dev/null 2>&1; then
    sudo apt-get update -y
    sudo apt-get install -y "${dep}"
  else
    echo "Missing required dependency: ${dep}" >&2
    return 1
  fi
}

for dep in curl unzip; do
  ensure_dependency "${dep}"
done

mkdir -p "${SDK_ROOT}"

if [ ! -x "${SDKMANAGER}" ]; then
  echo "Installing Android cmdline-tools into ${CMDLINE_DIR}" >&2
  download_path="${CACHE_DIR}/${SDK_VERSION_ZIP}"
  if [ ! -f "${download_path}" ]; then
    curl -fLo "${download_path}" "${CMDLINE_TOOLS_URL}"
  fi

  temp_dir="$(mktemp -d)"
  unzip -q "${download_path}" -d "${temp_dir}"
  rm -rf "${CMDLINE_DIR}/latest"
  mkdir -p "${CMDLINE_DIR}"
  mv "${temp_dir}/cmdline-tools" "${CMDLINE_DIR}/latest"
  rm -rf "${temp_dir}"
fi

export ANDROID_SDK_ROOT="${SDK_ROOT}"
export ANDROID_HOME="${SDK_ROOT}"
export PATH="${CMDLINE_BIN}:${SDK_ROOT}/platform-tools:${PATH}"

missing_packages=()
for package in "${REQUIRED_PACKAGES[@]}"; do
  case "${package}" in
    platforms;android-*)
      platform_dir="${SDK_ROOT}/platforms/${package#platforms;}"
      [ -d "${platform_dir}" ] || missing_packages+=("${package}")
      ;;
    build-tools;*)
      build_dir="${SDK_ROOT}/build-tools/${package#build-tools;}"
      [ -d "${build_dir}" ] || missing_packages+=("${package}")
      ;;
    *)
      component_dir="${SDK_ROOT}/${package}"
      [ -d "${component_dir}" ] || missing_packages+=("${package}")
      ;;
  esac
done

if [ ${#missing_packages[@]} -gt 0 ]; then
  yes | "${SDKMANAGER}" --licenses >/dev/null
  "${SDKMANAGER}" --install "${missing_packages[@]}"
else
  echo "Required Android SDK packages already installed." >&2
fi
