# Task Chain Agent Workflow

Automated GitHub Actions workflow for processing sub-issues sequentially through the Copilot Coding Agent with review cycles.

## Overview

This workflow automatically:
1. Finds the next open task from a parent issue's sub-issues
2. Triggers the Copilot Coding Agent to work on it
3. Manages labels and status throughout the process
4. Handles review cycles and fixes
5. Automatically proceeds to the next task after completion
6. Closes the parent issue when all tasks are done

## Required Labels

The workflow uses the following labels (create them in your repository):

| Label | Color | Description |
|-------|-------|-------------|
| `ready-for-agent` | `#0E8A16` (green) | Task is ready for the agent to start |
| `in-progress` | `#FBCA04` (yellow) | Task is currently being worked on |
| `needs-review` | `#D93F0B` (orange) | Task is waiting for review |
| `platinum-done` | `#7057FF` (purple) | Task is perfectly completed |
| `task-chain` | `#0075CA` (blue) | Marks PRs as part of the task chain |

### Creating Labels via GitHub CLI

```bash
gh label create "ready-for-agent" --color "0E8A16" --description "Task is ready for the agent to start"
gh label create "in-progress" --color "FBCA04" --description "Task is currently being worked on"
gh label create "needs-review" --color "D93F0B" --description "Task is waiting for review"
gh label create "platinum-done" --color "7057FF" --description "Task is perfectly completed"
gh label create "task-chain" --color "0075CA" --description "Marks PRs as part of the task chain"
```

## Usage

### Setting Up a Task Chain

1. Create a parent issue (e.g., #573) that lists all sub-issues
2. Reference sub-issues in the parent issue body using:
   - `#575` (short reference)
   - `GH-575` (alternative format)
   - Full URLs: `https://github.com/owner/repo/issues/575`

3. Mark the first task as `ready-for-agent`
4. Start the workflow

### Triggering the Workflow

#### Manual Start
```bash
# Start with default parent issue (#573)
gh workflow run task-chain-agent.yml

# Start with custom parent issue
gh workflow run task-chain-agent.yml -f parent_issue=123

# Force a specific issue
gh workflow run task-chain-agent.yml -f force_issue=575
```

#### Via Issue Comments

On any task issue, post these commands:

- `/agent-next` - Find and start the next available task
- `/agent-approve` - Mark current task as complete and move to next
- `/agent-continue` - Same as approve
- `/agent-fix` - Request fixes based on review comments

#### Automatic Triggers

- **After PR Merge**: When a PR with the `task-chain` label is merged, the workflow automatically finds and starts the next task

## Workflow Behavior

### Task Status Flow

```
[Open] → [ready-for-agent] → [in-progress] → [needs-review] → [platinum-done] → [Closed]
                                      ↑              ↓
                                      └──[/agent-fix]┘
```

### Finding Next Task

The workflow examines all sub-issues and:
1. ✅ **Skips** issues that are `closed` or have `platinum-done` label
2. 🎯 **Selects** the first open issue without `platinum-done`
3. 🏷️ **Applies** `in-progress` and `task-chain` labels
4. 🤖 **Triggers** Copilot Coding Agent

### Review Cycle

1. Agent creates PR → workflow adds `needs-review` to issue
2. Reviewer checks PR:
   - ✅ If good → comment `/agent-approve` → closes issue, starts next
   - 🔧 If changes needed → comment `/agent-fix` → agent makes fixes
3. Repeat until approved

### Completion

When all sub-issues are completed:
- Parent issue gets a success comment
- Parent issue is automatically closed
- Workflow stops

## Example Parent Issue Format

```markdown
# Speed up Xtream Sync & Enrichment

This is an automated task chain. Sub-tasks will be processed sequentially.

## Tasks

1. #575 - Wire-up Enhanced Sync
2. #576 - Hot Path Entlastung
3. #577 - Canonical-Link Backlog Worker
4. #578 - Info Backfill Parallelisierung
5. #579 - Performance/Regression Metriken

## Status

- [ ] All tasks completed
- [ ] Parent issue closed
```

## Troubleshooting

### Workflow not triggering
- Check that labels are created
- Verify `GITHUB_TOKEN` has proper permissions
- Check workflow run logs in Actions tab

### Agent not starting
- Ensure issue has `ready-for-agent` or trigger with `/agent-next`
- Check that issue is referenced in parent issue body
- Verify issue is not already closed or has `platinum-done`

### Task stuck in progress
- Comment `/agent-next` to force progression
- Check if there are PR merge conflicts
- Review GitHub Actions logs for errors

## Advanced Usage

### Custom Parent Issue

You can use this workflow with any parent issue:

```bash
gh workflow run task-chain-agent.yml -f parent_issue=YOUR_ISSUE_NUMBER
```

### Processing Specific Task

To skip ahead or retry a specific task:

```bash
gh workflow run task-chain-agent.yml -f force_issue=TASK_NUMBER
```

### Monitoring Progress

Check the workflow summary in GitHub Actions for:
- Current task being processed
- Tasks completed
- Overall progress
- Any errors or issues

## Technical Details

### Files

- **`.github/workflows/task-chain-agent.yml`** - Main workflow file
- **`.github/scripts/task-chain-helper.js`** - Node.js helper script for GitHub API interactions

### Permissions Required

The workflow requires these permissions:
```yaml
permissions:
  contents: write
  issues: write
  pull-requests: write
  actions: write
```

### Jobs Overview

1. **dispatch** - Determines what action to take based on trigger
2. **find-next-task** - Finds the next open task from parent issue
3. **process-task** - Starts the agent on a task
4. **approve-task** - Marks task as complete
5. **fix-task** - Requests fixes from agent
6. **check-completion** - Checks if all tasks are done
7. **continue-chain** - Automatically triggers next task

## Contributing

To modify or extend the workflow:

1. Edit `.github/workflows/task-chain-agent.yml` for workflow logic
2. Edit `.github/scripts/task-chain-helper.js` for API interactions
3. Test with a test parent issue before using on real tasks
4. Check GitHub Actions logs for debugging

## References

- Parent Issue: #573 (Speed up Xtream Sync & Enrichment)
- First Task: #575 (Wire-up Enhanced Sync)
- Other Tasks: #576, #577, #578, #579
