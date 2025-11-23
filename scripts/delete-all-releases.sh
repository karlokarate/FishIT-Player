#!/usr/bin/env bash
#
# Delete All GitHub Releases Script
# 
# This script deletes all releases from the GitHub repository to save space.
# Requires: gh CLI installed and authenticated
#
# Usage: ./scripts/delete-all-releases.sh [--dry-run]
#

set -euo pipefail

REPO="karlokarate/FishIT-Player"
DRY_RUN=false

# Parse arguments
if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=true
  echo "🔍 DRY RUN MODE - No changes will be made"
fi

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
  echo "❌ Error: GitHub CLI (gh) is not installed"
  echo "Install from: https://cli.github.com/"
  exit 1
fi

# Check if authenticated
if ! gh auth status &> /dev/null; then
  echo "❌ Error: Not authenticated with GitHub CLI"
  echo "Run: gh auth login"
  exit 1
fi

echo "📋 Fetching all releases from ${REPO}..."
RELEASES=$(gh release list --repo "${REPO}" --limit 1000 --json tagName --jq '.[].tagName')

if [[ -z "${RELEASES}" ]]; then
  echo "✅ No releases found in the repository"
  exit 0
fi

RELEASE_COUNT=$(echo "${RELEASES}" | wc -l)
echo "Found ${RELEASE_COUNT} release(s) to delete"
echo ""

# List all releases
echo "Releases to be deleted:"
echo "${RELEASES}" | while read -r tag; do
  echo "  - ${tag}"
done
echo ""

if [[ "${DRY_RUN}" == true ]]; then
  echo "🔍 DRY RUN: Would delete ${RELEASE_COUNT} release(s)"
  exit 0
fi

# Confirm deletion
read -p "⚠️  Are you sure you want to delete ALL ${RELEASE_COUNT} release(s)? (yes/no): " CONFIRM
if [[ "${CONFIRM}" != "yes" ]]; then
  echo "Aborted."
  exit 0
fi

echo ""
echo "🗑️  Deleting releases..."
echo ""

DELETED=0
FAILED=0

echo "${RELEASES}" | while read -r tag; do
  echo -n "Deleting release: ${tag}... "
  if gh release delete "${tag}" --repo "${REPO}" --yes 2>/dev/null; then
    echo "✅ Deleted"
    DELETED=$((DELETED + 1))
  else
    echo "❌ Failed"
    FAILED=$((FAILED + 1))
  fi
done

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Deletion complete!"
echo "   Deleted: ${DELETED}"
echo "   Failed: ${FAILED}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
