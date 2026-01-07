# Implement Feature Prompt Template

## Usage
Copy this template and fill in the placeholders when asking Copilot to implement a new feature.

---

## Feature: [FEATURE_NAME]

### Target Module
- Module: `[feature/*, core/*, pipeline/*, etc.]`
- Package: `com.fishit.player.[module].[subpackage]`

### Requirements
1. [Requirement 1]
2. [Requirement 2]
3. [Requirement 3]

### Constraints (Auto-Applied)
- Follow AGENTS.md layer boundaries
- Use path-scoped instructions for target module
- No forbidden imports (check `.github/instructions/*.instructions.md`)

### Acceptance Criteria
- [ ] Compiles without errors
- [ ] Tests pass
- [ ] ktlint check passes
- [ ] No layer boundary violations

### Files to Create/Modify
| File | Action | Purpose |
|------|--------|---------|
| `[path]` | Create/Modify | [Purpose] |

---

## Example Prompt

```
Implement feature [FEATURE_NAME] in module [MODULE].

Requirements:
- [List requirements]

Follow AGENTS.md and the path-scoped instructions for this module.
Create tests using JUnit5 + MockK.
```
