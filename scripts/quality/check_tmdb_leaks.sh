#!/bin/bash
#
# check_tmdb_leaks.sh
#
# Enforces TMDB library boundaries:
# - tmdb-java may only be used in :infra:transport-tmdb
# - Moviebase TMDB may not be used anywhere
# - No tmdb-java types may leak outside infra
#
# Exit code:
# - 0: No violations found
# - 1: Violations found

set -e

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

echo "=== TMDB Library Boundary Check ==="
echo ""

VIOLATIONS_FOUND=0

# Check 1: tmdb-java dependency must only be in :infra:transport-tmdb
echo "Checking tmdb-java dependency usage..."
TMDB_JAVA_DEPS=$(grep -r "uwetrottmann.tmdb2:tmdb-java" \
  --include="*.gradle*" \
  --include="*.kts" \
  --exclude-dir=".gradle" \
  --exclude-dir="build" \
  . | grep -v "infra/transport-tmdb" || true)

if [ -n "$TMDB_JAVA_DEPS" ]; then
  echo "❌ VIOLATION: tmdb-java dependency found outside :infra:transport-tmdb"
  echo "$TMDB_JAVA_DEPS"
  echo ""
  VIOLATIONS_FOUND=1
else
  echo "✅ tmdb-java dependency is only in :infra:transport-tmdb"
fi

# Check 2: Moviebase TMDB must not be used anywhere
echo ""
echo "Checking for Moviebase TMDB usage..."
MOVIEBASE_DEPS=$(grep -r "app.moviebase:tmdb" \
  --include="*.gradle*" \
  --include="*.kts" \
  --exclude-dir=".gradle" \
  --exclude-dir="build" \
  . || true)

if [ -n "$MOVIEBASE_DEPS" ]; then
  echo "❌ VIOLATION: Moviebase TMDB dependency found"
  echo "$MOVIEBASE_DEPS"
  echo ""
  VIOLATIONS_FOUND=1
else
  echo "✅ No Moviebase TMDB dependencies found"
fi

# Check 3: tmdb-java imports must only be in :infra:transport-tmdb/internal
echo ""
echo "Checking tmdb-java import leakage..."
TMDB_JAVA_IMPORTS=$(find . \
  -type f \
  -name "*.kt" \
  -not -path "*/infra/transport-tmdb/src/main/java/com/fishit/player/infra/transport/tmdb/internal/*" \
  -not -path "*/infra/transport-tmdb/src/main/java/com/fishit/player/infra/transport/tmdb/di/*" \
  -not -path "*/.gradle/*" \
  -not -path "*/build/*" \
  -not -path "*/legacy/*" \
  -exec grep -l "import com.uwetrottmann.tmdb2" {} \; || true)

if [ -n "$TMDB_JAVA_IMPORTS" ]; then
  echo "❌ VIOLATION: tmdb-java imports found outside :infra:transport-tmdb/internal"
  echo "$TMDB_JAVA_IMPORTS"
  echo ""
  VIOLATIONS_FOUND=1
else
  echo "✅ No tmdb-java imports outside :infra:transport-tmdb/internal"
fi

# Check 4: Moviebase imports must not exist
echo ""
echo "Checking for Moviebase imports..."
MOVIEBASE_IMPORTS=$(find . \
  -type f \
  -name "*.kt" \
  -not -path "*/.gradle/*" \
  -not -path "*/build/*" \
  -not -path "*/legacy/*" \
  -exec grep -l "import app.moviebase" {} \; || true)

if [ -n "$MOVIEBASE_IMPORTS" ]; then
  echo "❌ VIOLATION: Moviebase imports found"
  echo "$MOVIEBASE_IMPORTS"
  echo ""
  VIOLATIONS_FOUND=1
else
  echo "✅ No Moviebase imports found"
fi

echo ""
echo "=== Check Complete ==="
if [ $VIOLATIONS_FOUND -eq 0 ]; then
  echo "✅ All checks passed - no TMDB library boundary violations"
  exit 0
else
  echo "❌ Violations found - see errors above"
  exit 1
fi
