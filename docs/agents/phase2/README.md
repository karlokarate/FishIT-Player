# Phase 2 Agent Workspace

This folder contains progress tracking and workflow documentation for Phase 2 multi-agent development.

## Folder Purpose

Enable **safe parallel development** of Phase 2 tasks with:
- Clear ownership and write scopes
- Progress tracking per agent
- Structured follow-up documentation
- Zero merge conflicts

## File Types

### 1. Planning Documents (Read-Only for Agents)

**`PHASE2_PARALLELIZATION_PLAN.md`**
- Defines 8 Phase 2 tasks (P2-T1 through P2-T8)
- **P2-T1 (Core Persistence) is COMPLETED** - treated as frozen infrastructure
- Documents dependencies and parallelization strategy for remaining tasks
- Provides per-task implementation guidance
- **Required reading before starting any task**

**`AGENT_PROTOCOL_PHASE2.md`**
- Defines mandatory workflow protocol for all agents
- Includes templates for progress and follow-up files
- **Required reading before starting any task**

### 2. Progress Files (One per Agent per Task)

**Naming:** `agent-<agent-id>_P2-<task-id>_progress.md`

**Examples:**
- `agent-alice_P2-T1_progress.md`
- `agent-bob_P2-T2_progress.md`
- `agent-carol_P2-T3_progress.md`

**Purpose:**
- Track agent's progress on assigned task
- Document milestones, blockers, and status
- Enable coordination between agents

**Status Values:**
- `Planned` – Task claimed, not started coding yet
- `In Progress` – Actively working on task
- `Blocked` – Stuck, waiting for help or dependency
- `Completed` – Task done, ready for PR

**Rules:**
- ✅ Agent MAY read any progress file
- ✅ Agent MUST create their own progress file when starting a task
- ✅ Agent MUST update their own progress file regularly
- ❌ Agent MUST NOT modify other agents' progress files

### 3. Follow-Up Files (One per Task)

**Naming:** `FOLLOWUP_P2-<task-id>_by-<agent-id>.md`

**Examples:**
- `FOLLOWUP_P2-T1_by-alice.md`
- `FOLLOWUP_P2-T2_by-bob.md`

**Purpose:**
- Document what was accomplished
- List remaining work for future phases
- Identify risks and dependencies
- Provide test commands for validation

**Rules:**
- ✅ Created at end of task, before opening PR
- ✅ Mandatory for all Phase 2 tasks
- ✅ Serves as handoff documentation

## Quick Start for New Agents

1. **Read Planning Docs**
   ```bash
   cat docs/agents/phase2/PHASE2_PARALLELIZATION_PLAN.md
   cat docs/agents/phase2/AGENT_PROTOCOL_PHASE2.md
   ```

2. **Check Available Tasks**
   ```bash
   ls docs/agents/phase2/agent-*_progress.md
   # Look for tasks not yet claimed or completed
   # NOTE: P2-T1 is already COMPLETED - do not select
   ```

3. **Claim Task (P2-T2 through P2-T8 only)**
   - **DO NOT** create progress files for P2-T1 (already complete)
   - Create progress file: `agent-<your-id>_P2-<task-id>_progress.md`
   - Use template from `AGENT_PROTOCOL_PHASE2.md`
   - Set status to `Planned`

4. **Start Working**
   - Follow protocol in `AGENT_PROTOCOL_PHASE2.md`
   - Update progress file regularly
   - Create follow-up file before PR

## File Organization

```
docs/agents/phase2/
├── README.md                                    # This file
├── PHASE2_PARALLELIZATION_PLAN.md              # Task definitions & dependencies
├── AGENT_PROTOCOL_PHASE2.md                    # Workflow protocol & templates
├── agent-<agent-id>_P2-T1_progress.md          # Progress files (one per agent/task)
├── agent-<agent-id>_P2-T2_progress.md
├── ...
├── FOLLOWUP_P2-T1_by-<agent-id>.md             # Follow-up files (one per task)
├── FOLLOWUP_P2-T2_by-<agent-id>.md
└── ...
```

## Workspace Rules

### Shared Workspace Guidelines

1. **No Shared Log Files**
   - Each agent maintains their own progress file
   - No central log file that multiple agents write to

2. **Read All, Write Own**
   - ✅ Agents may read all files in this folder
   - ✅ Agents may only write to their own progress file
   - ❌ Agents must never modify:
     - Planning documents (PHASE2_PARALLELIZATION_PLAN.md, AGENT_PROTOCOL_PHASE2.md)
     - Other agents' progress files
     - Other agents' follow-up files

3. **Naming Consistency**
   - Use same `<agent-id>` across all your files
   - Use lowercase letters and hyphens only
   - Follow exact naming conventions from protocol

### Module Write Scope Rules

Agents must adhere to their task's **Primary Write Scope**:

| Task   | Status | Write Scope                    |
|--------|--------|--------------------------------|
| P2-T1  | ✅ COMPLETED | `:core:persistence/` (frozen) |
| P2-T2  | Available | `:pipeline:xtream/`            |
| P2-T3  | Available | `:pipeline:telegram/`          |
| P2-T4  | Available | `:pipeline:io/`                |
| P2-T5  | Available | `:pipeline:audiobook/`         |
| P2-T6  | Available | `:playback:domain/`            |
| P2-T7  | Available | Test directories only          |
| P2-T8  | Available | None (validation only)         |

**⚠️ IMPORTANT:**
- **P2-T1 is COMPLETED** and `:core:persistence/` is frozen infrastructure
- Agents MUST NOT write to `:core:persistence/` except through explicit maintenance tasks
- All remaining tasks treat P2-T1 as a read-only dependency

**Enforcement:**
- Agents MUST NOT write to modules outside their scope
- Violations cause merge conflicts and broken builds
- See `PHASE2_PARALLELIZATION_PLAN.md` for detailed scopes

## Conflict Prevention

### Why This System Works

1. **Module-Level Isolation**
   - Wave 0: P2-T1 is complete and serves as shared foundation
   - Wave 1: Each remaining task (P2-T2 to P2-T5) owns a different module
   - No two agents write to the same module simultaneously
   - Zero file-level conflicts

2. **Progress File Locking**
   - Progress file signals task ownership
   - Other agents see status before starting same task
   - Prevents duplicate work

3. **Sequential Waves**
   - Wave 0: P2-T1 is COMPLETED (frozen infrastructure)
   - Wave 1: P2-T2 to P2-T5 run in parallel
   - Wave 2: P2-T6 waits for Wave 1 completion
   - Wave 3: P2-T7, P2-T8 wait for Wave 2 completion
   - Dependencies enforced by protocol

### If Conflicts Occur Anyway

1. Document in progress file (set status to `Blocked`)
2. Follow conflict resolution process in `AGENT_PROTOCOL_PHASE2.md` Section 7
3. Coordinate with affected agent(s)
4. Resolve conflicts carefully
5. Update both progress files

## Success Criteria

Phase 2 agent workflow is successful when:

✅ All 8 tasks have status `Completed`  
✅ All progress files exist and are up-to-date  
✅ All follow-up files created  
✅ All PRs opened with proper links  
✅ All PRs merged without conflicts  
✅ No agent violated write scope rules  
✅ No duplicate or wasted work

## Support

If you have questions about:
- **Task definitions:** See `PHASE2_PARALLELIZATION_PLAN.md`
- **Workflow protocol:** See `AGENT_PROTOCOL_PHASE2.md`
- **Architecture:** See `v2-docs/ARCHITECTURE_OVERVIEW_V2.md`
- **Implementation phases:** See `v2-docs/IMPLEMENTATION_PHASES_V2.md`

---

**Folder Created:** 2025-12-05  
**Protocol Version:** 1.0  
**Maintained By:** v2 Architecture Team
