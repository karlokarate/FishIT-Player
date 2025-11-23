#!/usr/bin/env bash
#
# Quick one-liner to delete all releases
# Usage: bash delete-releases-oneliner.sh
#
gh release list --repo karlokarate/FishIT-Player --json tagName --jq '.[].tagName' | \
  xargs -I {} gh release delete {} --repo karlokarate/FishIT-Player --yes
