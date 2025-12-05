# Phase 2 Agent Protocol

**Date:** 2025-12-05  
**Branch:** `architecture/v2-bootstrap`  
**Phase:** Phase 2 – Pipeline Stubs & Core Persistence Implementation

---

## Purpose

This document defines the **mandatory workflow protocol** that all AI agents (GitHub Copilot, ChatGPT, etc.) MUST follow when implementing Phase 2 tasks for FishIT Player v2.

**Goals:**
- Ensure deterministic, conflict-free parallel development
- Maintain clear communication and progress tracking
- Enable safe onboarding of new agents mid-phase
- Create structured handoff documentation for follow-up work

**Enforcement:** This protocol is **mandatory** for all Phase 2 work. Non-compliance may result in merge conflicts, duplicate work, or architectural violations.

---

## Table of Contents

1. [Global Initial Task](#1-global-initial-task)
2. [Mandatory Follow-Up Task Rule](#2-mandatory-follow-up-task-rule)
3. [Progress File Template](#3-progress-file-template)
4. [Follow-Up File Template](#4-follow-up-file-template)
5. [Workflow Step-by-Step](#5-workflow-step-by-step)
6. [Communication Guidelines](#6-communication-guidelines)
7. [Conflict Resolution](#7-conflict-resolution)

---

## 1. Global Initial Task

Every agent working on Phase 2 MUST complete this initial task sequence **before making any code changes**.

### 1.1 Read All v2 Documentation

**Required Reading (in order):**

1. **`v2-docs/APP_VISION_AND_SCOPE.md`**
   - Understand product vision, target users, feature scope
   - Understand design principles (offline-first, TV-first, etc.)

2. **`v2-docs/ARCHITECTURE_OVERVIEW_V2.md`**
   - Understand module structure and layer responsibilities
   - Understand dependency rules (which modules can depend on which)
   - Review ObjectBox entity definitions and repository patterns

3. **`v2-docs/IMPLEMENTATION_PHASES_V2.md`**
   - Focus on **Phase 2 section** (lines 163-239)
   - Understand Phase 2 goals, allowed modules, checklist items
   - Review v1 component reuse guidance

4. **`docs/agents/phase2/PHASE2_PARALLELIZATION_PLAN.md`**
   - Understand all 8 Phase 2 tasks (P2-T1 through P2-T8)
   - Review task dependencies and parallelization strategy
   - Review per-task guidance for chosen task

5. **`docs/agents/phase2/AGENT_PROTOCOL_PHASE2.md`**
   - This document – understand protocol rules

**Time Estimate:** 30-45 minutes

### 1.2 Pick a Task

**Task Selection Rules:**

1. **Check Task Availability:**
   - List all files in `docs/agents/phase2/`
   - Look for existing progress files: `agent-*_P2-TX_progress.md`
   - If a progress file exists with status `In Progress` or `Blocked`, task is **taken**
   - If a progress file exists with status `Completed`, task is **done**

2. **Check Task Dependencies:**
   - Review `Blocking Dependencies` in task definition (in parallelization plan)
   - If dependencies not satisfied, task **cannot start**
   - Example: P2-T6 cannot start until P2-T1 is completed

3. **Claim Task:**
   - Create your progress file immediately (see template below)
   - Set status to `Planned` initially
   - This prevents other agents from starting the same task

**Task Priority (if multiple available):**
- Wave 1 tasks (P2-T1 to P2-T5) can all start immediately – choose any
- Wave 2 task (P2-T6) must wait for Wave 1 completion
- Wave 3 tasks (P2-T7, P2-T8) must wait for Wave 2 completion

### 1.3 Create Progress File

**File Naming Convention:**
```
agent-<agent-id>_P2-<task-id>_progress.md
```

**Examples:**
- `agent-alice_P2-T1_progress.md` (Agent Alice working on P2-T1)
- `agent-bob_P2-T2_progress.md` (Agent Bob working on P2-T2)

**Agent ID Rules:**
- Use a short, unique identifier (e.g., first name, nickname, or generated ID)
- Use lowercase letters only
- Use hyphens for multi-word IDs (e.g., `agent-copilot-1`)
- MUST be consistent across all your files in this phase

**Location:**
```
docs/agents/phase2/agent-<agent-id>_P2-<task-id>_progress.md
```

**Template:** See [Section 3: Progress File Template](#3-progress-file-template)

### 1.4 Perform Read-Only Inspection

Before writing any code, inspect the workspace to understand context:

**For All Tasks:**
1. **Review Module Structure:**
   - Explore assigned write scope module(s)
   - Check existing files (likely package-info.kt only in Phase 2)
   - Note any dependencies in `build.gradle.kts`

2. **Review v1 Reference Code (if applicable):**
   - For P2-T1: Review `app/src/main/java/com/chris/m3usuite/data/obx/`
   - For P2-T2: Review `app/src/main/java/com/chris/m3usuite/core/xtream/`
   - For P2-T3: Review `app/src/main/java/com/chris/m3usuite/telegram/`
   - See `v2-docs/V1_VS_V2_ANALYSIS_REPORT.md` for mapping guidance

3. **Review Dependencies:**
   - Check read-only dependency modules
   - Note any interfaces or models already defined in `:core:model/`

4. **Document Findings:**
   - Update progress file with observations
   - Note any questions or concerns
   - Update status to `In Progress` when ready to code

**Time Estimate:** 15-30 minutes

---

## 2. Mandatory Follow-Up Task Rule

Every agent MUST create a follow-up task file **before opening a pull request**.

### 2.1 When to Create Follow-Up File

Create follow-up file when:

✅ All task deliverables are complete (see task checklist in parallelization plan)  
✅ All module-specific tests pass  
✅ All code follows ktlint formatting  
✅ Progress file has status set to `Completed`  
✅ Ready to open PR

### 2.2 Follow-Up File Requirements

**File Naming Convention:**
```
FOLLOWUP_P2-<task-id>_by-<agent-id>.md
```

**Examples:**
- `FOLLOWUP_P2-T1_by-alice.md`
- `FOLLOWUP_P2-T2_by-bob.md`

**Location:**
```
docs/agents/phase2/FOLLOWUP_P2-<task-id>_by-<agent-id>.md
```

**Template:** See [Section 4: Follow-Up File Template](#4-follow-up-file-template)

### 2.3 Follow-Up File Content

The follow-up file MUST include:

1. **Context Summary**
   - What was accomplished
   - Major decisions made
   - Deviations from plan (if any)

2. **Remaining Work**
   - What was intentionally left for later (e.g., Phase 3)
   - What was started but not finished (should be none)
   - Known limitations or TODOs

3. **Dependencies and Risks**
   - What other tasks depend on this work
   - Known risks or technical debt introduced
   - Compatibility concerns

4. **Suggested Next Steps**
   - What should be done in Phase 3 (if applicable)
   - What follow-up refactoring might be needed
   - What documentation should be updated

5. **Test Commands**
   - Exact commands to validate this task's deliverables
   - Expected output for each command
   - Instructions for manual testing (if applicable)

---

## 3. Progress File Template

Copy this template when creating your progress file:

```markdown
# Phase 2 Agent Progress – <agent-id> / <task-id>

- **Agent ID:** <agent-id>
- **Task ID:** <P2-TX>
- **Task Name:** <short task name>
- **Date Started (UTC):** YYYY-MM-DD
- **Date Completed (UTC):** YYYY-MM-DD (or "In Progress")
- **Current Status:** Planned | In Progress | Blocked | Completed

---

## Primary Write Scope

- `<module path>`

## Read-Only Dependencies

- `<module path 1>`
- `<module path 2>`

---

## Progress Log

### YYYY-MM-DD HH:MM UTC – Initial Setup

**Status:** Planned

**Actions:**
- Read all v2 documentation
- Reviewed task definition in parallelization plan
- Claimed task by creating this progress file

**Next Steps:**
- Perform read-only inspection of write scope module
- Review v1 reference code (if applicable)
- Begin implementation

---

### YYYY-MM-DD HH:MM UTC – <Milestone Name>

**Status:** In Progress

**Actions:**
- <Describe what you did>
- <List files created/modified>

**Tests Run:**
- <Command and result>

**Next Steps:**
- <What you plan to do next>

**Blocking Issues:**
- None | <Describe any blockers>

---

### YYYY-MM-DD HH:MM UTC – Task Complete

**Status:** Completed

**Summary:**
- <High-level summary of deliverables>
- <Links to key files>

**Final Test Results:**
- ✅ Module builds: `./gradlew :<module>:assembleDebug`
- ✅ Unit tests pass: `./gradlew :<module>:testDebugUnitTest`
- ✅ Code formatted: `./gradlew :<module>:ktlintCheck`
- ✅ No detekt issues: `./gradlew :<module>:detekt`

**Follow-Up:**
- Created follow-up file: `FOLLOWUP_P2-<task-id>_by-<agent-id>.md`
- Ready for PR

---

## Notes & Observations

- <Any important notes for future reference>
- <Lessons learned>
- <Suggestions for protocol improvements>
```

---

## 4. Follow-Up File Template

Copy this template when creating your follow-up file:

```markdown
# Follow-up Task – Phase 2 / <task-id>

- **Created by Agent:** <agent-id>
- **Task ID:** <P2-TX>
- **Task Name:** <short task name>
- **Date (UTC):** YYYY-MM-DD
- **Status:** Completed

---

## Context Summary

### What Was Accomplished

<Provide a high-level summary of what this task delivered>

**Key Deliverables:**
- <Deliverable 1>
- <Deliverable 2>
- <Deliverable 3>

**Files Created:**
- `<file path>`
- `<file path>`

**Files Modified:**
- `<file path>`
- `<file path>`

### Major Decisions Made

<Describe any significant architectural or implementation decisions>

**Decision 1: <Title>**
- **Context:** <Why this decision was needed>
- **Options Considered:** <Alternative approaches>
- **Choice:** <What was chosen>
- **Rationale:** <Why this was chosen>

### Deviations from Plan

<List any deviations from the original task definition or parallelization plan>

- **Deviation 1:** <What changed>
  - **Reason:** <Why it changed>
  - **Impact:** <How it affects other tasks or phases>

---

## Remaining Work

### Intentionally Deferred to Later Phases

<List work that was intentionally left for Phase 3, 4, or later>

- **Item 1:** <Description>
  - **Target Phase:** Phase X
  - **Reason for Deferral:** <Why not done now>

### Known Limitations

<List any limitations or incomplete functionality in delivered code>

- **Limitation 1:** <Description>
  - **Impact:** <Who/what is affected>
  - **Mitigation:** <How it's handled or worked around>

### TODOs and Technical Debt

<List any TODO comments left in code or technical debt introduced>

- **TODO 1:** `<file path:line>` – <Description>
  - **Priority:** Low | Medium | High
  - **Estimated Effort:** <Time estimate>

---

## Dependencies and Risks

### Downstream Dependencies

<List tasks or phases that depend on this work>

- **Task P2-TX:** <How it depends on this task>
- **Phase X:** <How Phase X will build on this>

### Upstream Dependencies

<List tasks this work depended on>

- **Task P2-TX:** <What was used from this task>

### Known Risks

<Identify any risks introduced or discovered>

- **Risk 1:** <Description>
  - **Likelihood:** Low | Medium | High
  - **Impact:** Low | Medium | High
  - **Mitigation:** <How to address>

### Compatibility Concerns

<Note any backward compatibility or integration concerns>

- **Concern 1:** <Description>
  - **Affected Components:** <What might break>
  - **Recommendation:** <How to handle>

---

## Suggested Next Steps

### Phase 3 Implementation

<For pipeline stubs (P2-T2 to P2-T5): Suggest how to implement the full pipeline in Phase 3>

- **Step 1:** <Description>
- **Step 2:** <Description>

### Potential Refactoring

<Suggest any refactoring that might be beneficial in the future>

- **Refactor 1:** <Description>
  - **Benefit:** <Why it would help>
  - **Effort:** <Estimated complexity>

### Documentation Updates

<List any documentation that should be updated as a result of this work>

- **Doc 1:** `<file path>` – <What needs updating>

---

## Test Commands

### Build & Compile

```bash
# Build the module
./gradlew :<module>:assembleDebug

# Expected output:
BUILD SUCCESSFUL in Xs
```

### Unit Tests

```bash
# Run unit tests
./gradlew :<module>:testDebugUnitTest

# Expected output:
BUILD SUCCESSFUL in Xs
X tests completed, 0 failed
```

### Code Quality

```bash
# Check code formatting
./gradlew :<module>:ktlintCheck

# Expected output:
BUILD SUCCESSFUL in Xs

# Static analysis
./gradlew :<module>:detekt

# Expected output:
BUILD SUCCESSFUL in Xs
```

### Integration Tests (if applicable)

```bash
# Run integration tests
./gradlew :<module>:connectedAndroidTest

# Expected output:
BUILD SUCCESSFUL in Xs
X tests completed, 0 failed
```

### Manual Testing (if applicable)

<Provide step-by-step instructions for manual testing>

1. **Step 1:** <Description>
   - **Expected Result:** <What should happen>

2. **Step 2:** <Description>
   - **Expected Result:** <What should happen>

---

## Additional Notes

<Any other information that might be helpful for future agents or reviewers>

---

**Document Version:** 1.0  
**Last Updated:** YYYY-MM-DD  
**Maintained By:** <agent-id>
```

---

## 5. Workflow Step-by-Step

### Complete Workflow from Start to PR

```
┌─────────────────────────────────────┐
│  1. ONBOARDING                      │
│  ────────────────────────────────── │
│  □ Read all v2 docs                 │
│  □ Read parallelization plan        │
│  □ Read agent protocol (this doc)  │
│  Time: 30-45 min                    │
└─────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────┐
│  2. TASK SELECTION                  │
│  ────────────────────────────────── │
│  □ Check task availability          │
│  □ Verify dependencies satisfied    │
│  □ Choose task from available wave  │
│  Time: 5 min                        │
└─────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────┐
│  3. CLAIM TASK                      │
│  ────────────────────────────────── │
│  □ Create progress file             │
│  □ Set status to "Planned"          │
│  □ Document initial observations    │
│  Time: 10 min                       │
└─────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────┐
│  4. READ-ONLY INSPECTION            │
│  ────────────────────────────────── │
│  □ Explore write scope modules      │
│  □ Review v1 reference code         │
│  □ Review dependency modules        │
│  □ Update progress file             │
│  □ Set status to "In Progress"      │
│  Time: 15-30 min                    │
└─────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────┐
│  5. ITERATIVE DEVELOPMENT           │
│  ────────────────────────────────── │
│  Loop until task complete:          │
│  □ Write code                       │
│  □ Run tests frequently             │
│  □ Update progress file regularly   │
│  □ Fix any failures immediately     │
│  Time: Variable (1-3 days)          │
└─────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────┐
│  6. PRE-PR VALIDATION               │
│  ────────────────────────────────── │
│  □ All tests pass                   │
│  □ All code formatted (ktlint)      │
│  □ No critical issues (detekt)      │
│  □ All deliverables complete        │
│  □ Progress file updated            │
│  □ Set status to "Completed"        │
│  Time: 30 min                       │
└─────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────┐
│  7. CREATE FOLLOW-UP FILE           │
│  ────────────────────────────────── │
│  □ Use follow-up template           │
│  □ Document context & decisions     │
│  □ List remaining work              │
│  □ Identify risks & dependencies    │
│  □ Suggest next steps               │
│  □ Provide test commands            │
│  Time: 30-45 min                    │
└─────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────┐
│  8. OPEN PULL REQUEST               │
│  ────────────────────────────────── │
│  □ Title: [Phase 2][P2-TX] <desc>  │
│  □ Link to progress file            │
│  □ Link to follow-up file           │
│  □ Request review                   │
│  Time: 15 min                       │
└─────────────────────────────────────┘
```

**Total Time Estimate (excluding coding):** 2.5-3.5 hours

---

## 6. Communication Guidelines

### Progress File Updates

**When to Update:**
- At the start of each work session
- After completing a milestone (e.g., "Models defined", "Tests written")
- When encountering blockers
- When task status changes
- At the end of each work session

**What to Include:**
- Timestamp (UTC)
- Current status (Planned | In Progress | Blocked | Completed)
- Actions taken since last update
- Files created/modified
- Test results
- Next planned steps
- Blocking issues (if any)

**Format:**
- Use clear, concise language
- Use bullet points for lists
- Include exact commands run and their results
- Link to files or commits when relevant

### Communicating Blockers

If you encounter a blocker:

1. **Document in Progress File:**
   - Update status to `Blocked`
   - Describe the blocker in detail
   - Include error messages, stack traces, or screenshots
   - Suggest potential solutions if known

2. **Check Other Agents' Progress:**
   - Review other agents' progress files
   - See if anyone has encountered similar issue
   - Reach out if helpful (outside this protocol)

3. **Escalate if Needed:**
   - If blocker cannot be resolved independently, escalate to team
   - Document escalation in progress file
   - Wait for guidance before proceeding

### Reading Other Agents' Progress

You MAY read other agents' progress files to:
- Check status of dependencies
- Learn from their approach or solutions
- Avoid duplicate work
- Coordinate integration points

You MUST NOT:
- Modify other agents' progress files
- Start a task another agent is working on (status = `In Progress`)
- Change another agent's code without coordination

---

## 7. Conflict Resolution

### Preventing Conflicts

**Module-Level Locking:**
- Progress file with status `In Progress` = module is locked
- Other agents must not write to that module
- Coordination via progress files prevents conflicts

**File-Level Best Practices:**
- Create new files rather than modifying shared files when possible
- Keep changes focused on write scope
- Commit and push frequently (at least daily)

### Resolving Conflicts (if they occur)

**Step 1: Identify Conflict**
- Agent encounters merge conflict when pulling or pushing
- Document conflict in progress file
- Set status to `Blocked`

**Step 2: Analyze Conflict**
- Determine which files conflict
- Determine which tasks caused conflict (should be visible from git history)
- Assess impact and priority

**Step 3: Coordinate Resolution**
- If conflict is trivial (formatting, imports), resolve immediately
- If conflict is structural (logic changes), coordinate with other agent(s)
- Document resolution approach in progress file

**Step 4: Resolve**
- One agent is designated as resolver (usually the one who detected conflict)
- Resolver merges changes from both sides
- Resolver tests merged code
- Resolver updates both affected progress files

**Step 5: Validate**
- Run all tests
- Run quality checks
- Verify both tasks' deliverables still work
- Update status back to `In Progress` or `Completed`

---

## Appendix A: Quick Reference Commands

### Task Status Check
```bash
# List all progress files
ls -la docs/agents/phase2/agent-*_progress.md

# Check status of specific task
grep "Current Status" docs/agents/phase2/agent-*_P2-T1_progress.md
```

### Module-Specific Testing
```bash
# Build module
./gradlew :<module>:assembleDebug

# Run unit tests
./gradlew :<module>:testDebugUnitTest

# Format code
./gradlew :<module>:ktlintFormat

# Check formatting
./gradlew :<module>:ktlintCheck

# Run static analysis
./gradlew :<module>:detekt
```

### Full Project Validation
```bash
# Build all
./gradlew clean assembleDebug

# Test all
./gradlew testDebugUnitTest

# Quality checks
./gradlew ktlintCheck detekt lintDebug
```

---

## Appendix B: Example Workflow

### Example: Agent Alice working on P2-T1 (Core Persistence)

**Day 1 – Morning:**
```
1. Alice reads all v2 docs (45 min)
2. Alice reviews task P2-T1 in parallelization plan (15 min)
3. Alice creates: docs/agents/phase2/agent-alice_P2-T1_progress.md
4. Alice sets status to "Planned"
5. Alice reviews v1 ObjectBox code in app/src/main/java/.../data/obx/
6. Alice updates progress file with observations
7. Alice sets status to "In Progress"
```

**Day 1 – Afternoon:**
```
8. Alice creates package structure in :core:persistence/
9. Alice ports ObxCategory, ObxLive, ObxVod entities
10. Alice writes unit tests for entities
11. Alice runs: ./gradlew :core:persistence:testDebugUnitTest
12. Alice updates progress file: "Ported 3 entities, tests passing"
```

**Day 2 – Full Day:**
```
13. Alice ports remaining entities
14. Alice implements ProfileRepositoryImpl
15. Alice implements ResumeRepositoryImpl
16. Alice writes unit tests for repositories
17. Alice runs tests, fixes failures iteratively
18. Alice updates progress file at lunch and end of day
```

**Day 3 – Morning:**
```
19. Alice implements DataStore wrappers
20. Alice writes unit tests for DataStore logic
21. Alice runs full module test suite: all pass
22. Alice runs ktlintCheck: 10 formatting errors
23. Alice runs ktlintFormat: auto-fixed
24. Alice runs detekt: 0 issues
25. Alice sets progress file status to "Completed"
```

**Day 3 – Afternoon:**
```
26. Alice creates: FOLLOWUP_P2-T1_by-alice.md
27. Alice documents: context, decisions, remaining work, next steps, test commands
28. Alice opens PR: [Phase 2][P2-T1] Core Persistence Implementation
29. Alice links progress file and follow-up file in PR description
30. Alice requests review from team
```

**Total Time:** 2.5 days (typical for P2-T1)

---

## Appendix C: Common Mistakes to Avoid

### ❌ Mistake 1: Skipping Documentation Reading
**Problem:** Agent starts coding without reading architecture docs  
**Impact:** Code doesn't follow architecture, creates rework  
**Solution:** Always complete Global Initial Task (#1) fully

### ❌ Mistake 2: Not Creating Progress File Immediately
**Problem:** Two agents start same task simultaneously  
**Impact:** Duplicate work, wasted effort  
**Solution:** Create progress file as first action after choosing task

### ❌ Mistake 3: Writing to Other Modules
**Problem:** Agent modifies modules outside write scope  
**Impact:** Merge conflicts, broken builds  
**Solution:** Strictly adhere to Primary Write Scope

### ❌ Mistake 4: Not Updating Progress File Regularly
**Problem:** Team can't see agent's status or blockers  
**Impact:** Poor coordination, missed dependencies  
**Solution:** Update progress file at each milestone and daily

### ❌ Mistake 5: Skipping Follow-Up File
**Problem:** No documentation for next phase or follow-up work  
**Impact:** Future agents lack context, redo work  
**Solution:** Always create follow-up file before PR

### ❌ Mistake 6: Not Running Tests Frequently
**Problem:** Bugs accumulate, hard to locate source  
**Impact:** Long debugging sessions, delays  
**Solution:** Run tests after each logical change

### ❌ Mistake 7: Ignoring Ktlint Errors
**Problem:** Code formatting inconsistencies  
**Impact:** PR review noise, CI failures  
**Solution:** Run ktlintFormat regularly, check before PR

### ❌ Mistake 8: Starting Task with Unmet Dependencies
**Problem:** Agent starts P2-T6 before P2-T1 is complete  
**Impact:** Compilation errors, blocked work  
**Solution:** Always verify dependencies in parallelization plan first

---

## Appendix D: Protocol Compliance Checklist

Use this checklist to verify you're following the protocol correctly:

### Before Starting Task
- [ ] Read all required documentation (#1.1)
- [ ] Checked task availability (#1.2)
- [ ] Verified dependencies are satisfied (#1.2)
- [ ] Created progress file (#1.3)
- [ ] Set status to "Planned" (#1.3)
- [ ] Performed read-only inspection (#1.4)
- [ ] Set status to "In Progress" (#1.4)

### During Development
- [ ] Only writing to modules in Primary Write Scope
- [ ] Updating progress file regularly (at least daily)
- [ ] Running tests frequently (after each logical change)
- [ ] Committing and pushing frequently (at least daily)
- [ ] Running ktlintCheck before each commit
- [ ] Documenting any blockers in progress file

### Before Opening PR
- [ ] All task deliverables completed
- [ ] All module tests pass
- [ ] All code formatted (ktlintCheck passes)
- [ ] No critical issues (detekt passes)
- [ ] Progress file updated with final status
- [ ] Status set to "Completed"
- [ ] Follow-up file created (#2)
- [ ] Follow-up file includes all required sections (#2.3)

### After PR Opened
- [ ] PR title follows format: `[Phase 2][P2-TX] <description>`
- [ ] PR body links to progress file
- [ ] PR body links to follow-up file
- [ ] Requested review from team

---

**Document Version:** 1.0  
**Last Updated:** 2025-12-05  
**Maintained By:** v2 Architecture Team
