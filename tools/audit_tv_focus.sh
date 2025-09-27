#!/usr/bin/env bash
set -euo pipefail

# Usage: tools/audit_tv_focus.sh [ci]
# When run with 'ci', writes a report to tools/audit_tv_focus_report.txt and exits non‑zero on findings.

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPORT_MODE="${1:-}"
REPORT_FILE="$ROOT_DIR/tools/audit_tv_focus_report.txt"

section() { echo "$1"; }
hr() { echo ""; }
capture() {
  local name="$1"; shift
  local tmp
  tmp="$(rg -n --hidden -g '!**/build/**' "$@" || true)"
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

# 4) Buttons ohne TV-Fokus-Optik (Heuristik): Material-Buttons ohne focusScaleOnTv
section "== BUTTONS OHNE focusScaleOnTv (prüfen) =="
capture RAW_BTNS \
  -g 'app/src/main/java/**/*.kt' \
  -g '!**/ui/common/TvButtons.kt' \
  -e '\b(Button|TextButton|IconButton|OutlinedButton)\s*\(' \
  -N
# Filter alle Vorkommen, bei denen die gleiche Zeile bereits focusScaleOnTv enthält
RAW_BTNS_FILTERED="$(printf "%s" "$RAW_BTNS" | rg -v 'focusScaleOnTv\s*\(' || true)"
echo "$RAW_BTNS_FILTERED"
RAW_BTNS_COUNT=$(printf "%s" "$RAW_BTNS_FILTERED" | sed -n '/./p' | wc -l | tr -d ' ')
hr

# 4) Gesamtfazit
TOTAL=$(( RAW_ROWS_COUNT + RAW_CLICK_COUNT + SCREENS_DPAD_COUNT ))
echo "== SUMMARY =="
echo "Horizontal-Container (roh): $RAW_ROWS_COUNT"
echo "Clickable (roh):           $RAW_CLICK_COUNT"
echo "Screens-DPAD:              $SCREENS_DPAD_COUNT"
echo "Buttons ohne Fokusoptik:   $RAW_BTNS_COUNT (advisory)"
echo "Total findings:            $TOTAL"

if [ "$REPORT_MODE" = "ci" ]; then
  {
    echo "Audit: TV Focus/DPAD"
    echo "Generated: $(date -u +%F' '%T'Z')"
    echo
    echo "== HORIZONTALE CONTAINER (prüfen/aufrüsten) =="
    echo "$RAW_ROWS"
    echo
    echo "== CLICKABLE (prüfen: tvClickable bevorzugen) =="
    echo "$RAW_CLICK"
    echo
    echo "== SCREENS: onPreviewKeyEvent / DPAD (prüfen) =="
    echo "$SCREENS_DPAD"
    echo
    echo "== BUTTONS OHNE focusScaleOnTv (prüfen) =="
    echo "$RAW_BTNS_FILTERED"
    echo
    echo "== SUMMARY =="
    echo "Horizontal-Container (roh): $RAW_ROWS_COUNT"
    echo "Clickable (roh):           $RAW_CLICK_COUNT"
    echo "Screens-DPAD:              $SCREENS_DPAD_COUNT"
    echo "Buttons ohne Fokusoptik:   $RAW_BTNS_COUNT (advisory)"
    echo "Total findings:            $TOTAL"
  } > "$REPORT_FILE"
  echo "Report written to: $REPORT_FILE"
  # Fail CI when findings exist
  if [ "$TOTAL" -gt 0 ]; then exit 1; fi
fi
