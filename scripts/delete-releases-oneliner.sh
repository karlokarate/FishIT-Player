#!/usr/bin/env bash
#
# Quick one-liner to delete all releases
# Usage: ./delete-releases-oneliner.sh
#

set -euo pipefail

REPO="karlokarate/FishIT-Player"

gh release list --repo "${REPO}" --json tagName --jq '.[].tagName' | \
  xargs -I {} gh release delete {} --repo "${REPO}" --yes
