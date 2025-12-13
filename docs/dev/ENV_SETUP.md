# Android Environment Setup

This repository includes an idempotent helper to provision the Android SDK without depending on Codespaces-specific paths. Use `$HOME` or explicitly set `ANDROID_SDK_ROOT`/`ANDROID_HOME`; the script will reuse a repo-local cache in `.cache/android-sdk` for downloads.

## Prerequisites
- Bash
- `curl` and `unzip` (installed automatically via `apt-get` when available)
- JDK 17 or newer on the host

## Setup Script
Run from the repository root:

```bash
tools/env/setup_android.sh
```

The script:
- Detects existing `ANDROID_SDK_ROOT`/`ANDROID_HOME` values or defaults to `$HOME/.android-sdk`
- Installs Android cmdline-tools if `sdkmanager` is missing
- Installs `platform-tools`, `platforms;android-35`, and `build-tools;35.0.0` when they are not already present
- Accepts SDK licenses automatically

## CI Smoke Workflow
The `codec-env-smoke.yml` workflow runs on pushes, pull requests, and manual triggers. It:
1. Checks out the repository
2. Installs JDK 17 with Gradle caching enabled
3. Runs `tools/env/setup_android.sh` with the SDK rooted at the workspace
4. Builds a debug APK via `./gradlew :app-v2:assembleDebug`
5. Uploads the resulting APK artifact (if produced)

To trigger manually, use the **Run workflow** button in GitHub Actions. To replicate locally, run the same script and Gradle command.
