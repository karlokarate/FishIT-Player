# Task Chain Agent - Quick Start Guide

## Prerequisites

1. The task-chain-agent workflow is now installed
2. Required labels need to be created

## Step 1: Create Labels

Run one of these commands:

### Option A: Using GitHub Actions
```bash
gh workflow run setup-task-chain-labels.yml
```

### Option B: Using the script
```bash
.github/scripts/setup-labels.sh
```

### Option C: Manually
Create these labels in your repository settings:
- `ready-for-agent` (color: 0E8A16, green)
- `in-progress` (color: FBCA04, yellow)
- `needs-review` (color: D93F0B, orange)
- `platinum-done` (color: 7057FF, purple)
- `task-chain` (color: 0075CA, blue)

## Step 2: Start with Issue #575

Add the `ready-for-agent` label to issue #575:

```bash
gh issue edit 575 --add-label "ready-for-agent"
```

Or manually via GitHub UI:
1. Go to https://github.com/karlokarate/FishIT-Player/issues/575
2. Click "Labels" on the right
3. Select "ready-for-agent"

## Step 3: Run the Workflow

Trigger the workflow to start processing:

```bash
gh workflow run task-chain-agent.yml
```

Or via GitHub UI:
1. Go to Actions tab
2. Select "Task Chain Agent" workflow
3. Click "Run workflow"
4. Keep default parent_issue as 573
5. Click "Run workflow"

## What Happens Next

1. ✅ Workflow finds issue #575 (first task)
2. 🏷️ Adds `in-progress` and `task-chain` labels
3. 🤖 Triggers Copilot Coding Agent via comment
4. 📝 Agent analyzes and works on the issue
5. 🔄 Continues through review cycles as needed

## Monitoring Progress

Check workflow runs:
```bash
gh run list --workflow=task-chain-agent.yml
```

View latest run:
```bash
gh run view --web
```

## Commands During Processing

On any task issue, comment:

- `/agent-next` - Find and start next task
- `/agent-approve` - Mark complete, move to next
- `/agent-fix` - Request fixes from agent

## Troubleshooting

### Workflow not starting
- Check Actions tab for errors
- Verify labels were created
- Ensure issue #575 has `ready-for-agent` label

### Task stuck
Comment `/agent-next` on the issue to force progression

### View logs
```bash
gh run view <run-id> --log
```

## References

- Full documentation: `.github/docs/README-task-chain.md`
- Parent issue: #573
- Task issues: #575, #576, #577, #578, #579
