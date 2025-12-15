#!/usr/bin/env bash
# Telegram Auth Contract & Wiring Guardrails
# Fails fast on any attempt to duplicate the contract, mis-wire DI, or break layer boundaries.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
CANONICAL_CONTRACT="core/feature-api/src/main/kotlin/com/fishit/player/core/feature/auth/TelegramAuthRepository.kt"
FEATURE_ONBOARDING_GRADLE="feature/onboarding/build.gradle.kts"
DATA_TELEGRAM_SRC="infra/data-telegram/src/main"
APP_V2_DIR="app-v2"

cd "$REPO_ROOT"

VIOLATIONS=0

if command -v rg >/dev/null 2>&1; then
  SEARCH_TOOL="rg"
else
  SEARCH_TOOL="grep"
fi

run_search() {
  local pattern="$1"; shift
  local paths=("$@")
  if [[ "$SEARCH_TOOL" == "rg" ]]; then
    rg --no-heading --line-number --hidden \
      --glob '*.kt' \
      --glob '!**/build/**' \
      --glob '!**/.gradle/**' \
      --glob '!legacy/**' \
      --glob '!**/generated/**' \
      --glob '!**/out/**' \
      -e "$pattern" "${paths[@]}" 2>/dev/null || true
  else
    grep -R -n -E "$pattern" "${paths[@]}" \
      --include='*.kt' \
      --exclude-dir=build \
      --exclude-dir=.gradle \
      --exclude-dir=legacy \
      --exclude-dir=generated \
      --exclude-dir=out 2>/dev/null || true
  fi
}

fail() {
  echo "❌ $1"
  VIOLATIONS=$((VIOLATIONS + 1))
}

echo "Checking Telegram auth guardrails..."

# ----------------------------------------------------------------------
# A) Single source of truth for contract/state
# ----------------------------------------------------------------------
if [[ ! -f "$CANONICAL_CONTRACT" ]]; then
  fail "Canonical contract missing at $CANONICAL_CONTRACT"
else
  echo "✅ Canonical contract present"
fi

duplicates=$(run_search "(interface\\s+TelegramAuthRepository|sealed\\s+class\\s+TelegramAuthState)" "." \
  | grep -v "$CANONICAL_CONTRACT" \
  | grep -v "infra/transport-telegram/" || true)
if [[ -n "$duplicates" ]]; then
  echo "$duplicates"
  fail "Duplicate Telegram auth contract/state detected (must exist only in $CANONICAL_CONTRACT)"
else
  echo "✅ No duplicate Telegram auth contract/state definitions"
fi

# Explicit guard: feature layer must not define TelegramAuthState
feature_defs=$(run_search "sealed\\s+class\\s+TelegramAuthState" "feature")
if [[ -n "$feature_defs" ]]; then
  echo "$feature_defs"
  fail "feature/** must not declare TelegramAuthState (use core/feature-api contract)"
fi

# Forbid UiTelegramAuthState anywhere
ui_aliases=$(run_search "UiTelegramAuthState" ".")
if [[ -n "$ui_aliases" ]]; then
  echo "$ui_aliases"
  fail "UiTelegramAuthState is forbidden; use core TelegramAuthState"
fi

# ----------------------------------------------------------------------
# B) Feature dependency wall (onboarding must not depend on infra/pipeline/playback/app)
# ----------------------------------------------------------------------
if [[ ! -f "$FEATURE_ONBOARDING_GRADLE" ]]; then
  fail "Missing $FEATURE_ONBOARDING_GRADLE"
else
  bad_deps=$(run_search "project\(\":(infra|pipeline|playback):" "$FEATURE_ONBOARDING_GRADLE")
  bad_app_dep=$(run_search "project\(\":app-v2" "$FEATURE_ONBOARDING_GRADLE")
  if [[ -n "$bad_deps$bad_app_dep" ]]; then
    echo "$bad_deps$bad_app_dep"
    fail "feature:onboarding has forbidden dependencies (infra/pipeline/playback/app-v2)"
  else
    echo "✅ feature:onboarding has no infra/pipeline/playback/app-v2 deps"
  fi
fi

# ----------------------------------------------------------------------
# C) DI single binding guarantee
# ----------------------------------------------------------------------
bindings=$(run_search "bindTelegramAuthRepository" "." || true)
binding_count=$(echo "$bindings" | sed '/^$/d' | wc -l | tr -d ' ')
if [[ "$binding_count" -ne 1 ]]; then
  echo "$bindings"
  fail "Expected exactly one binding for TelegramAuthRepository (found $binding_count)"
else
  binding_path=$(echo "$bindings" | head -1 | cut -d':' -f1)
  binding_path=${binding_path#./}
  if [[ "$binding_path" != infra/data-telegram/* ]]; then
    echo "$bindings"
    fail "TelegramAuthRepository binding must live in infra/data-telegram (found in $binding_path)"
  else
    echo "✅ Single TelegramAuthRepository binding located in infra/data-telegram"
  fi
fi

if [[ -n "$(run_search "TelegramAuthRepository" "$APP_V2_DIR" | grep -E "@Binds|@Provides" || true)" ]]; then
  fail "App-v2 must not provide/bind TelegramAuthRepository"
else
  echo "✅ No TelegramAuthRepository bindings in app-v2"
fi

# ----------------------------------------------------------------------
# D) Data layer must not import transport internals / TDLib
# ----------------------------------------------------------------------
forbidden_imports=$(run_search "com\\.fishit\\.player\\.infra\\.transport\\.telegram\\.auth\\.|dev\\.g000sha256\\.|org\\.drinkless\\.tdlib\\." "$DATA_TELEGRAM_SRC")
if [[ -n "$forbidden_imports" ]]; then
  echo "$forbidden_imports"
  fail "infra/data-telegram imports transport internals/TDLib (only transport API surface is allowed)"
else
  echo "✅ infra/data-telegram imports limited to transport API surface"
fi

# ----------------------------------------------------------------------
# E) No app-level stub/provider reinsertion
# ----------------------------------------------------------------------
app_stub=$(run_search "TelegramAuthRepository" "$APP_V2_DIR" | grep -E "@Binds|@Provides|TdlClient\.create" || true)
if [[ -n "$app_stub" ]]; then
  echo "$app_stub"
  fail "app-v2 contains TelegramAuthRepository DI or TdlClient.create stub wiring (forbidden)"
else
  echo "✅ No app-v2 stubs/providers for TelegramAuthRepository"
fi

# ----------------------------------------------------------------------
# RESULT
# ----------------------------------------------------------------------
if [[ $VIOLATIONS -eq 0 ]]; then
  echo "✅ Telegram auth guardrails passed"
  exit 0
else
  echo "❌ Telegram auth guardrails failed ($VIOLATIONS violation(s))"
  exit 1
fi
