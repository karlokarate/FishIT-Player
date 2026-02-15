# Phase 2 Multi-Agent Workflow Setup Task

You are a GitHub Copilot Agent working in the `architecture/v2-bootstrap` branch of the FishIT-Player project.

Your mission is to design, create and enforce a complete multi-agent workflow system for Phase 2 of the v2 architecture, combining:

1. A full parallelization strategy with strict module-scoped write access.
2. A shared workspace folder where each agent maintains its own progress files.
3. A global initial task required for all agents before making any code changes.
4. A mandatory follow-up task rule: each agent must create a follow-up task file at the end of their main task and after running all tests, right before drafting a PR.

The system must be deterministic, conflict-free, easy for future agents to follow, and fully aligned with the v2 documentation.

---

## Step 1 — Analyze Phase 2 dependencies and determine parallelization

1. Read and interpret:
   - `docs/APP_VISION_AND_SCOPE.md`
   - `docs/ARCHITECTURE_OVERVIEW_V2.md`
   - `docs/IMPLEMENTATION_PHASES_V2.md`
   - any other existing v2 docs.

2. Inspect the module tree under `architecture/v2-bootstrap`:
   - `:core:persistence`
   - `:playback:domain`
   - `:pipeline:xtream`
   - `:pipeline:telegram`
   - `:pipeline:io`
   - `:pipeline:audiobook`

3. Build a dependency map and determine:
   - which Phase 2 tasks must be sequential,
   - which can run in parallel,
   - which modules belong to each task’s write scope,
   - which tasks depend on others.

Use these task IDs:
- P2-T1: Core Persistence
- P2-T2: Xtream Pipeline
- P2-T3: Telegram Pipeline
- PP2-T4: IO Pipeline
- P2-T5: Audiobook Pipeline
- P2-T6: Playback Domain Impl
- P2-T7: Integration Testing
- P2-T8: Build & Quality Validation

Document findings in `PHASE2_PARALLELIZATION_PLAN.md`.

---

## Step 2 — Create shared agent workspace folder

Create:

`docs/agents/phase2/`

Rules:
- No shared log files.
- Each agent writes only their own files.
- Agents may read all files but never modify files belonging to other agents.

Naming for progress files:

`agent-<agent-id>_P2-<task-id>_progress.md`

Naming for follow-up files:

`FOLLOWUP_P2-<task-id>_by-<agent-id>.md`

---

## Step 3 — Create Phase 2 parallelization plan file

Create/update:

`docs/agents/phase2/PHASE2_PARALLELIZATION_PLAN.md`

Must include:
- A summary of Phase 2 context.
- All task IDs, write scopes, dependencies.
- A section “Parallelization Strategy for Phase 2”.
- A section “Agent Workflow Summary for Phase 2”.
- A section “Per-task Guidance” with actionable steps for each task.

---

## Step 4 — Create Phase 2 agent protocol file

Create/update:

`docs/agents/phase2/AGENT_PROTOCOL_PHASE2.md`

Define:

### 1. Global Initial Task
All agents must:
- Read all v2 docs.
- Read parallelization plan + protocol.
- Pick a task.
- Create a progress file using template.
- Perform read-only inspection before coding.

### 2. Mandatory Follow-up Task Rule
Before drafting a PR:
- Agent must create follow-up task file.
- It must include: summary, remaining work, dependencies, risks, next steps, test commands.

### 3. Templates

Progress file:

```
# Phase 2 Agent Progress – <agent-id> / <task-id>

- Agent ID: <agent-id>
- Task ID: <P2-Tx>
- Date (UTC): YYYY-MM-DD
- Current Status: Planned | In Progress | Blocked | Completed
- Primary Write Scope:
  - <module path>
- Read-only Dependencies:
  - <module paths>
- Last Changes:
  - ...
- Next Planned Steps:
  - ...
- Blocking Issues:
  - ...
```

Follow-up file:

```
# Follow-up Task – Phase 2 / <task-id>

- Created by Agent: <agent-id>
- Date (UTC): YYYY-MM-DD

## Context Summary
...

## Remaining Work
...

## Dependencies and Risks
...

## Suggested Next Steps
...

## Test Commands
...
```

---

## Step 5 — Ensure consistency

- Do not change v1 code.
- Do not modify core v2 architecture except cross-referencing.
- Ensure consistent naming conventions.
- Ensure file layout follows project standards.

---

## Deliverables

This task must produce:

1. `docs/agents/phase2/PHASE2_PARALLELIZATION_PLAN.md`
2. `docs/agents/phase2/AGENT_PROTOCOL_PHASE2.md`
3. A functional folder: `docs/agents/phase2/`
4. All templates embedded in protocol

The resulting system must allow future agents to onboard identically, safely work in parallel in isolated scopes, track progress independently, avoid merge conflicts, and leave a structured follow-up task for the next agent.
