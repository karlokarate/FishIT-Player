# Refactor Code Prompt Template

## Usage
Use this template when asking Copilot to refactor existing code while maintaining PLATIN quality.

---

## Refactoring: [REFACTOR_NAME]

### Target
- File(s): `[file paths]`
- Current Issue: [What's wrong with current implementation]

### Goal
[Describe the desired outcome]

### Refactoring Type
- [ ] Extract Function/Class
- [ ] Move to Different Layer
- [ ] Remove Duplication
- [ ] Improve Performance
- [ ] Fix Layer Boundary Violation
- [ ] Rename for Glossary Compliance

### Constraints
- Must NOT break existing tests
- Must NOT introduce layer boundary violations
- Must follow AGENTS.md Section 4 (Layer Boundaries)
- Must follow /contracts/GLOSSARY_v2_naming_and_modules.md

### Pre-Check
Before refactoring, verify:
- [ ] Current tests exist and pass
- [ ] No existing layer violations
- [ ] Module README.md read

### Post-Check
After refactoring:
- [ ] All tests still pass
- [ ] No new imports from forbidden layers
- [ ] ktlint passes
- [ ] Naming follows Glossary

---

## Example Prompt

```
Refactor [FILE] to [GOAL].

Current issue: [ISSUE]

Ensure:
1. No layer boundary violations (AGENTS.md Section 4)
2. Tests still pass
3. Naming follows Glossary
```
