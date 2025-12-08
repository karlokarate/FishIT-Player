#!/usr/bin/env bash
set -euo pipefail

# Usage: tools/audit_tv_focus.sh [ci]
# When run with 'ci', writes a report to tools/audit_tv_focus_report.txt and exits non‑zero on findings.
#
# Aligned with tools/Zentralisierung.txt (2025):
# - Prefer TvFocusRow over raw horizontal containers in TV paths
# - Forbid androidx.tv.foundation TvLazyRow
# - Avoid duplicate focus indicators (ring/scale) – use tvFocusFrame + neutral tvClickable
# - Centralize bring-into-view (row-level), avoid per-tile scroll hacks
# - Keep focus primitives defined only in central modules

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPORT_MODE="${1:-}"
REPORT_FILE="$ROOT_DIR/tools/audit_tv_focus_report.txt"

section() { echo "$1"; }
hr() { echo ""; }
capture() {
  local name="$1"; shift
  local tmp
  tmp="$(rg -n --hidden \
    -g '!**/build/**' \
    -g '!**/.git/**' \
    -g '!a/**' \
    -g '!b/**' \
    "$@" || true)"
  printf -v "$name" '%s' "$tmp"
}

# 1) Horizontale Container: Roh-LazyRow etc. (TvFocusRow/RowCore ausnehmen)
section "== HORIZONTALE CONTAINER (prüfen/aufrüsten) =="
capture RAW_ROWS \
  -g '*.kt' \
  -e 'LazyRow\s*\(' -e 'LazyHorizontalGrid\s*\(' -e 'HorizontalPager\s*\(' -e 'ScrollableTabRow\s*\(' -e 'Row\s*\(.*horizontalScroll\('
# Filter out known engine wrappers
RAW_ROWS="$(printf "%s" "$RAW_ROWS" | rg -v -e '/ui/tv/TvFocusRow\.kt' -e '/ui/components/rows/RowCore\.kt')"
echo "$RAW_ROWS"
RAW_ROWS_COUNT=$(printf "%s" "$RAW_ROWS" | sed -n '/./p' | wc -l | tr -d ' ')
hr

# 1b) TV foundation widgets (forbidden): TvLazyRow
section "== TV FOUNDATION (verboten) =="
capture TV_FOUND \
  -g '*.kt' \
  -e '\bTvLazyRow\s*\(' -e 'androidx\\.tv\\.foundation'
echo "$TV_FOUND"
TV_FOUND_COUNT=$(printf "%s" "$TV_FOUND" | sed -n '/./p' | wc -l | tr -d ' ')
hr

# 2) Clickable ohne TV-Focusable: Roh-clickable statt tvClickable
section "== CLICKABLE (prüfen: tvClickable bevorzugen) =="
capture RAW_CLICK \
  -g '*.kt' \
  -g '!**/ui/skin/TvModifiers.kt' \
  -e '(^|[^A-Za-z0-9_])clickable\s*\(' \
  -e '(^|[^A-Za-z0-9_])clickable\s*\{' # also catch trailing-lambda form
echo "$RAW_CLICK"
RAW_CLICK_COUNT=$(printf "%s" "$RAW_CLICK" | sed -n '/./p' | wc -l | tr -d ' ')
hr

# 3) Screens: onPreviewKeyEvent / Directional keys ohne Feature-Bezug
section "== SCREENS: onPreviewKeyEvent / DPAD (prüfen) =="
capture SCREENS_DPAD \
  -g 'app/src/main/java/com/chris/m3usuite/ui/screens/**.kt' \
  -e 'onPreviewKeyEvent\s*\{' -e 'Key\.(Direction(Right|Left|Up|Down))'
echo "$SCREENS_DPAD"
SCREENS_DPAD_COUNT=$(printf "%s" "$SCREENS_DPAD" | sed -n '/./p' | wc -l | tr -d ' ')
hr

# 4) Buttons ohne TV-Fokus-Optik (Heuristik): Material-Buttons ohne tvFocusFrame/TvButtons/focusScaleOnTv
section "== BUTTONS OHNE TV-FOCUS (prüfen) =="
capture RAW_BTNS \
  -g 'app/src/main/java/**/*.kt' \
  -g '!**/ui/common/TvButtons.kt' \
  -e '\b(Button|TextButton|IconButton|OutlinedButton)\s*\(' \
  -N
# Filter alle Vorkommen, bei denen die gleiche Zeile bereits tvFocusFrame oder focusScaleOnTv enthält
RAW_BTNS_FILTERED="$(printf "%s" "$RAW_BTNS" | rg -v 'tvFocusFrame\s*\(|focusScaleOnTv\s*\(' || true)"
echo "$RAW_BTNS_FILTERED"
RAW_BTNS_COUNT=$(printf "%s" "$RAW_BTNS_FILTERED" | sed -n '/./p' | wc -l | tr -d ' ')
hr

# 5) Bring-Into-View nur zentral: per-Item bringIntoView/scroll heuristics (outside central modules)
section "== BRING-INTO-VIEW (roh/per-Item) =="
capture RAW_BRING \
  -g '*.kt' \
  -e 'bringIntoViewRequester|BringIntoViewRequester|bringIntoView\s*\(|onFocusChanged\s*\{|scrollToItem\s*\(|animateScrollToItem\s*\(' \
  -N
# Filter allowed central modules
RAW_BRING_FILTERED="$(printf "%s" "$RAW_BRING" | rg -v '/ui/tv/TvFocusRow\.kt|/ui/tv/TvRowScroll\.kt|/ui/components/rows/RowCore\.kt|/ui/skin/TvModifiers\.kt|/ui/focus/FocusKit\.kt' || true)"
echo "$RAW_BRING_FILTERED"
RAW_BRING_COUNT=$(printf "%s" "$RAW_BRING_FILTERED" | sed -n '/./p' | wc -l | tr -d ' ')
hr

# 6) Doppelte Fokus-Indikatoren (Heuristik): tvClickable mit eigenem Scale/Border (außer zentral/Buttons)
section "== DOPPELTE FOKUS-INDIKATOREN (prüfen) =="
capture RAW_DUPL \
  -g '*.kt' \
  -g '!**/ui/common/TvButtons.kt' \
  -g '!**/ui/skin/TvModifiers.kt' \
  -e 'tvClickable\s*\(' \
  -N
# Heuristik: tvClickable mit scaleFocused/scalePressed != 1f oder focusBorderWidth != 0.dp
RAW_DUPL_FILTERED="$(printf "%s" "$RAW_DUPL" | rg '(scaleFocused\s*=\s*(?!1f))|(scalePressed\s*=\s*(?!1f))|(focusBorderWidth\s*=\s*(?!0\s*\.?(dp|DP)))' -n -S || true)"
echo "$RAW_DUPL_FILTERED"
RAW_DUPL_COUNT=$(printf "%s" "$RAW_DUPL_FILTERED" | sed -n '/./p' | wc -l | tr -d ' ')
hr

# 7) Verstreute Fokus-Utilities: Definitionen außerhalb zentraler Module
section "== SSOT: FOKUS-UTILITIES AUSSERHALB ZENTRALER MODULE =="
capture RAW_SSOT \
  -g '*.kt' \
  -e 'fun\s+.*Modifier\.(tvClickable|tvFocusFrame|focusScaleOnTv|tvFocusableItem)\s*\(' \
  -N
RAW_SSOT_FILTERED="$(printf "%s" "$RAW_SSOT" | rg -v '/ui/skin/TvModifiers\.kt|/ui/skin/PackageScope\.kt|/ui/tv/TvFocusRow\.kt|/ui/compat/FocusCompat\.kt|/ui/focus/FocusKit\.kt' || true)"
echo "$RAW_SSOT_FILTERED"
RAW_SSOT_COUNT=$(printf "%s" "$RAW_SSOT_FILTERED" | sed -n '/./p' | wc -l | tr -d ' ')
hr

# Gesamtfazit
TOTAL=$(( RAW_ROWS_COUNT + TV_FOUND_COUNT + RAW_CLICK_COUNT + SCREENS_DPAD_COUNT + RAW_BRING_COUNT + RAW_DUPL_COUNT + RAW_SSOT_COUNT ))
echo "== SUMMARY =="
echo "Horizontal-Container (roh): $RAW_ROWS_COUNT"
echo "TV foundation (verboten):  $TV_FOUND_COUNT"
echo "Clickable (roh):           $RAW_CLICK_COUNT"
echo "Screens-DPAD:              $SCREENS_DPAD_COUNT"
echo "Buttons ohne TV-Focus:     $RAW_BTNS_COUNT (advisory)"
echo "Bring-into-view (roh):     $RAW_BRING_COUNT"
echo "Doppelte Fokus-Indikator.: $RAW_DUPL_COUNT (advisory)"
echo "SSOT-Verstöße (Defs):      $RAW_SSOT_COUNT"
echo "Total findings:            $TOTAL"

if [ "$REPORT_MODE" = "ci" ]; then
  {
    echo "Audit: TV Focus/DPAD"
    echo "Generated: $(date -u +%F' '%T'Z')"
    echo
    echo "== HORIZONTALE CONTAINER (prüfen/aufrüsten) =="
    echo "$RAW_ROWS"
    echo
    echo "== TV FOUNDATION (verboten) =="
    echo "$TV_FOUND"
    echo
    echo "== CLICKABLE (prüfen: tvClickable bevorzugen) =="
    echo "$RAW_CLICK"
    echo
    echo "== SCREENS: onPreviewKeyEvent / DPAD (prüfen) =="
    echo "$SCREENS_DPAD"
    echo
    echo "== BUTTONS OHNE TV-FOCUS (prüfen) =="
    echo "$RAW_BTNS_FILTERED"
    echo
    echo "== BRING-INTO-VIEW (roh/per-Item) =="
    echo "$RAW_BRING_FILTERED"
    echo
    echo "== DOPPELTE FOKUS-INDIKATOREN (prüfen) =="
    echo "$RAW_DUPL_FILTERED"
    echo
    echo "== SSOT: FOKUS-UTILITIES AUSSERHALB ZENTRALER MODULE =="
    echo "$RAW_SSOT_FILTERED"
    echo
    echo "== SUMMARY =="
    echo "Horizontal-Container (roh): $RAW_ROWS_COUNT"
    echo "TV foundation (verboten):  $TV_FOUND_COUNT"
    echo "Clickable (roh):           $RAW_CLICK_COUNT"
    echo "Screens-DPAD:              $SCREENS_DPAD_COUNT"
    echo "Buttons ohne TV-Focus:     $RAW_BTNS_COUNT (advisory)"
    echo "Bring-into-view (roh):     $RAW_BRING_COUNT"
    echo "Doppelte Fokus-Indikator.: $RAW_DUPL_COUNT (advisory)"
    echo "SSOT-Verstöße (Defs):      $RAW_SSOT_COUNT"
    echo "Total findings:            $TOTAL"
  } > "$REPORT_FILE"
  echo "Report written to: $REPORT_FILE"
  # Fail CI when findings exist
  if [ "$TOTAL" -gt 0 ]; then exit 1; fi
fi
