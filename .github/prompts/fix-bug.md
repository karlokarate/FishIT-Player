# Fix Bug Prompt Template

## Usage
Use this template for bug fixes to ensure systematic debugging and PLATIN-quality fixes.

---

## Bug: [BUG_TITLE]

### Issue Reference
- GitHub Issue: #[NUMBER] (if applicable)
- Error Message: `[Error message]`
- Stack Trace: (if applicable)

### Reproduction Steps
1. [Step 1]
2. [Step 2]
3. [Step 3]

### Expected Behavior
[What should happen]

### Actual Behavior
[What actually happens]

### Affected Module
- Module: `[module path]`
- File(s): `[file paths]`

### Root Cause Analysis
[Your analysis of why this happens]

### Proposed Fix
[High-level description of fix approach]

### Testing Strategy
- [ ] Unit test for the fix
- [ ] Regression tests for related functionality
- [ ] Manual verification steps

---

## Example Prompt

```
Fix bug: [BUG_TITLE]

Reproduction: [Steps]
Expected: [Expected behavior]
Actual: [Actual behavior]

Root cause: [Analysis]

Create a fix that:
1. Solves the immediate issue
2. Doesn't break existing tests
3. Includes a regression test
4. Follows AGENTS.md and module instructions
```
