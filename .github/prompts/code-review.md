# Code Review Prompt Template

## Usage
Use this template when asking Copilot to review code for PLATIN quality compliance.

---

## Review Request

### Target
- File(s): `[file paths or PR link]`
- Type: [ ] New Feature [ ] Bug Fix [ ] Refactor [ ] Other

### Review Focus
- [ ] Layer Boundary Compliance (AGENTS.md Section 4)
- [ ] Naming Convention (GLOSSARY_v2_naming_and_modules.md)
- [ ] Code Quality (ktlint, detekt)
- [ ] Test Coverage
- [ ] Performance
- [ ] Security
- [ ] Documentation

### Checklist (Auto-Applied)
The reviewer should verify:

#### Layer Boundaries
- [ ] No forbidden imports from other layers
- [ ] Pipeline does not import Data/Persistence
- [ ] Transport does not import Pipeline
- [ ] Player does not import Transport directly

#### Naming
- [ ] Classes follow Glossary patterns
- [ ] Packages follow module structure
- [ ] No `*FeatureProvider` in pipeline (use `*CapabilityProvider`)

#### Quality
- [ ] No `TODO` without issue reference
- [ ] No hardcoded strings (use resources)
- [ ] Proper error handling
- [ ] Logging uses UnifiedLog

#### Tests
- [ ] New code has tests
- [ ] Tests follow AAA pattern
- [ ] MockK used for mocking

---

## Example Prompt

```
Review this code for PLATIN quality:

[Paste code or file path]

Check for:
1. Layer boundary violations (AGENTS.md Section 4)
2. Naming compliance (Glossary)
3. Missing tests
4. Potential bugs
5. Performance issues

Provide specific line-by-line feedback.
```
