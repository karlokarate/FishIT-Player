#!/bin/bash
# Script to create required labels for task-chain-agent workflow

set -e

echo "Creating labels for Task Chain Agent workflow..."
echo ""

# Colors
GREEN="0E8A16"
YELLOW="FBCA04"
ORANGE="D93F0B"
PURPLE="7057FF"
BLUE="0075CA"

# Create labels
echo "Creating label: ready-for-agent"
gh label create "ready-for-agent" \
  --color "$GREEN" \
  --description "Task is ready for the agent to start" \
  --force 2>/dev/null || echo "  ✓ Label already exists"

echo "Creating label: in-progress"
gh label create "in-progress" \
  --color "$YELLOW" \
  --description "Task is currently being worked on" \
  --force 2>/dev/null || echo "  ✓ Label already exists"

echo "Creating label: needs-review"
gh label create "needs-review" \
  --color "$ORANGE" \
  --description "Task is waiting for review" \
  --force 2>/dev/null || echo "  ✓ Label already exists"

echo "Creating label: platinum-done"
gh label create "platinum-done" \
  --color "$PURPLE" \
  --description "Task is perfectly completed" \
  --force 2>/dev/null || echo "  ✓ Label already exists"

echo "Creating label: task-chain"
gh label create "task-chain" \
  --color "$BLUE" \
  --description "Marks PRs as part of the task chain" \
  --force 2>/dev/null || echo "  ✓ Label already exists"

echo ""
echo "✅ All labels created successfully!"
echo ""
echo "You can now:"
echo "1. Add 'ready-for-agent' label to issue #575"
echo "2. Run: gh workflow run task-chain-agent.yml"
