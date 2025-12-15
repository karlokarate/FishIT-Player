#!/usr/bin/env bash
# Global Duplicate Contract & Shadow Type Guardrails
# Enforces single source of truth for core contracts and detects architectural smells.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

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
      --glob '*.java' \
      --glob '!**/build/**' \
      --glob '!**/.gradle/**' \
      --glob '!**/.wsl-gradle/**' \
      --glob '!legacy/**' \
      --glob '!**/generated/**' \
      --glob '!**/out/**' \
      -e "$pattern" "${paths[@]}" 2>/dev/null || true
  else
    grep -R -n -E "$pattern" "${paths[@]}" \
      --include='*.kt' \
      --include='*.java' \
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

echo "🔍 Checking for duplicate contracts and shadow types..."

# ======================================================================
# A) Core Contract Duplication Checks
# ======================================================================

# 1. TelegramAuthRepository + TelegramAuthState
CANONICAL_TELEGRAM_AUTH="core/feature-api/src/main/kotlin/com/fishit/player/core/feature/auth/TelegramAuthRepository.kt"

# Validate canonical file exists and contains both interface and state
if [[ ! -f "$CANONICAL_TELEGRAM_AUTH" ]]; then
  fail "Canonical TelegramAuthRepository file missing: $CANONICAL_TELEGRAM_AUTH"
else
  if ! grep -q "interface TelegramAuthRepository" "$CANONICAL_TELEGRAM_AUTH"; then
    fail "Canonical file missing TelegramAuthRepository interface: $CANONICAL_TELEGRAM_AUTH"
  fi
  if ! grep -q "sealed class TelegramAuthState" "$CANONICAL_TELEGRAM_AUTH"; then
    fail "Canonical file missing TelegramAuthState sealed class: $CANONICAL_TELEGRAM_AUTH"
  fi
fi

telegram_repo_dups=$(run_search "interface\\s+TelegramAuthRepository" "." \
  | grep -v "$CANONICAL_TELEGRAM_AUTH" || true)
telegram_state_dups=$(run_search "sealed\\s+(class|interface)\\s+TelegramAuthState" "." \
  | grep -v "$CANONICAL_TELEGRAM_AUTH" || true)

if [[ -n "$telegram_repo_dups" ]]; then
  echo "$telegram_repo_dups"
  fail "TelegramAuthRepository duplicated (canonical: $CANONICAL_TELEGRAM_AUTH)"
fi

if [[ -n "$telegram_state_dups" ]]; then
  echo "$telegram_state_dups"
  fail "TelegramAuthState duplicated (canonical: $CANONICAL_TELEGRAM_AUTH)"
fi

# 2. XtreamAuthRepository + XtreamAuthState (if exists)
CANONICAL_XTREAM_AUTH_FILE="core/feature-api/src/main/kotlin/com/fishit/player/core/feature/xtream/XtreamAuthRepository.kt"
if [[ -f "$CANONICAL_XTREAM_AUTH_FILE" ]]; then
  # Validate canonical file contains both interface and state
  if ! grep -q "interface XtreamAuthRepository" "$CANONICAL_XTREAM_AUTH_FILE"; then
    fail "Canonical file missing XtreamAuthRepository interface: $CANONICAL_XTREAM_AUTH_FILE"
  fi
  if ! grep -q "sealed class XtreamAuthState" "$CANONICAL_XTREAM_AUTH_FILE"; then
    fail "Canonical file missing XtreamAuthState sealed class: $CANONICAL_XTREAM_AUTH_FILE"
  fi

  xtream_repo_dups=$(run_search "interface\\s+XtreamAuthRepository" "." \
    | grep -v "core/feature-api" || true)
  xtream_state_dups=$(run_search "sealed\\s+(class|interface)\\s+XtreamAuthState" "." \
    | grep -v "core/feature-api" || true)

  if [[ -n "$xtream_repo_dups" ]]; then
    echo "$xtream_repo_dups"
    fail "XtreamAuthRepository duplicated (canonical: $CANONICAL_XTREAM_AUTH)"
  fi

  if [[ -n "$xtream_state_dups" ]]; then
    echo "$xtream_state_dups"
    fail "XtreamAuthState duplicated (canonical: $CANONICAL_XTREAM_AUTH)"
  fi
fi

# 3. RawMediaMetadata
CANONICAL_RAW_MEDIA="core/model"
raw_media_dups=$(run_search "data\\s+class\\s+RawMediaMetadata" "." \
  | grep -v "core/model/" || true)

if [[ -n "$raw_media_dups" ]]; then
  echo "$raw_media_dups"
  fail "RawMediaMetadata duplicated (canonical: $CANONICAL_RAW_MEDIA)"
fi

# 4. MetadataNormalizer
CANONICAL_NORMALIZER="core/metadata-normalizer"
normalizer_dups=$(run_search "interface\\s+MetadataNormalizer" "." \
  | grep -v "core/metadata-normalizer/" || true)

if [[ -n "$normalizer_dups" ]]; then
  echo "$normalizer_dups"
  fail "MetadataNormalizer duplicated (canonical: $CANONICAL_NORMALIZER)"
fi

if [[ $VIOLATIONS -eq 0 ]]; then
  echo "✅ No duplicate core contracts"
fi

# ======================================================================
# B) Shadow UI/Domain State Types
# ======================================================================

# Match only Kotlin type definitions (class/interface/object/data/sealed), not comments or docs
# Pattern breakdown:
# - ^[^/]* = Not inside a comment (no leading //)
# - (sealed\s+)?(data\s+)?(class|interface|object)\s+ = Kotlin type keywords
# - Ui.*AuthState|Domain.*AuthState|Ui.*Image|Ui.*Thumb|.*ThumbState = Forbidden names

shadow_ui_states=$(run_search "^[^/]*(sealed\\s+)?(data\\s+)?(class|interface|object)\\s+Ui.*AuthState" ".")
shadow_domain_states=$(run_search "^[^/]*(sealed\\s+)?(data\\s+)?(class|interface|object)\\s+Domain.*AuthState" ".")

# Image/Thumbnail shadow types (type definitions only)
shadow_ui_images=$(run_search "^[^/]*(sealed\\s+)?(data\\s+)?(class|interface|object)\\s+Ui.*Image" ".")
shadow_ui_thumbs=$(run_search "^[^/]*(sealed\\s+)?(data\\s+)?(class|interface|object)\\s+Ui.*Thumb" ".")
shadow_thumb_states=$(run_search "^[^/]*(sealed\\s+)?(data\\s+)?(class|interface|object)\\s+\\w*ThumbState" ".")

if [[ -n "$shadow_ui_states" ]]; then
  echo "$shadow_ui_states"
  fail "Shadow UI state types found (UiTelegramAuthState, UiXtreamAuthState, etc. are forbidden)"
fi

if [[ -n "$shadow_domain_states" ]]; then
  echo "$shadow_domain_states"
  fail "Shadow Domain state types found (use import aliasing instead of new types)"
fi

if [[ -n "$shadow_ui_images" ]]; then
  echo "$shadow_ui_images"
  fail "Shadow UI image types found (UiImageRef, UiImage, etc. are forbidden - use core:model ImageRef)"
fi

if [[ -n "$shadow_ui_thumbs" ]]; then
  echo "$shadow_ui_thumbs"
  fail "Shadow UI thumbnail types found (UiThumbState, UiThumbnail, etc. are forbidden - use ImageRef.InlineBytes)"
fi

if [[ -n "$shadow_thumb_states" ]]; then
  echo "$shadow_thumb_states"
  fail "Thumbnail state types found (*ThumbState classes forbidden - use ImageRef variants directly)"
fi

if [[ $VIOLATIONS -eq 0 ]]; then
  echo "✅ No shadow UI/Domain state types"
fi

# ======================================================================
# C) Imaging System Guardrails
# ======================================================================

# 1. Coil configuration in app-v2 (DI only)
app_v2_coil_config=$(run_search "(ImageLoader\\.Builder|DiskCache\\.Builder|MemoryCache\\.Builder|\\.components\\s*\\{)" "app-v2/src" \
  | grep -v "app-v2/src/main/java/com/fishit/player/v2/di/" || true)

if [[ -n "$app_v2_coil_config" ]]; then
  echo "$app_v2_coil_config"
  fail "Coil configuration found in app-v2 outside DI layer (must be in core:ui-imaging/GlobalImageLoader)"
fi

# 2. TelegramThumbFetcher implementation in core:ui-imaging
core_thumb_impl=$(run_search "^[^/\\*]*class\\s+\\w*TelegramThumbFetcher\\w*(Impl)?" "core/ui-imaging/src" \
  | grep -v "interface TelegramThumbFetcher" \
  | grep -v "class NoOpTelegramThumbFetcher" || true)

if [[ -n "$core_thumb_impl" ]]; then
  echo "$core_thumb_impl"
  fail "Concrete TelegramThumbFetcher implementation found in core:ui-imaging (belongs in infra/*)"
fi

if [[ $VIOLATIONS -eq 0 ]]; then
  echo "✅ Imaging system boundaries respected"
fi

# ======================================================================
# D) Role Duplication Smells
# ======================================================================

# Find files with smell keywords in their names (outside legacy)
smell_files=$(find . -type f \( -name "*.kt" -o -name "*.java" \) \
  ! -path "*/build/*" \
  ! -path "*/.gradle/*" \
  ! -path "*/legacy/*" \
  ! -path "*/generated/*" \
  ! -path "*/out/*" \
  ! -path "*/.wsl-gradle/*" \
  | grep -E "(Bridge|Stopgap|Temp|Copy|Old|Backup|Alt)[A-Z]|[A-Z].*(Bridge|Stopgap|Temp|Copy|Old|Backup|Alt)" \
  || true)

if [[ -n "$smell_files" ]]; then
  echo "$smell_files"
  fail "Role duplication smells detected (Bridge/Stopgap/Temp/Copy/Old/Backup/Alt in filenames)"
fi

if [[ $VIOLATIONS -eq 0 ]]; then
  echo "✅ No role duplication smells"
fi

# ======================================================================
# RESULT
# ======================================================================

if [[ $VIOLATIONS -eq 0 ]]; then
  echo ""
  echo "✅ All duplicate contract and shadow type guardrails passed"
  exit 0
else
  echo ""
  echo "❌ Duplicate contract/shadow type guardrails failed ($VIOLATIONS violation(s))"
  exit 1
fi
