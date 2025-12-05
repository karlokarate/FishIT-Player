# FishIT Player v2 Documentation

This folder contains all documentation specific to the FishIT Player v2 architecture redesign.

## Branch Rule: `architecture/v2-bootstrap` and Related Branches

**IMPORTANT:** All v2-related markdown documentation MUST be placed in this `v2-docs/` folder, not in the root `docs/` folder.

### Documentation Organization

- **`v2-docs/`** - All v2 architecture, design, and implementation documentation
  - Vision & Architecture docs
  - Phase implementation guides
  - Analysis reports
  - Task documents
  - Review reports

- **`docs/`** - Legacy v1 documentation (keep for reference, do not modify)

### Why This Rule?

1. **Clear Separation**: v2 is a complete redesign using the Strangler Pattern. All v2 documentation should be easily identifiable and separate from legacy v1 docs.

2. **Branch Consistency**: The `architecture/v2-bootstrap` branch maintains this structure. All v2 work should follow the same convention.

3. **Easy Migration**: When v2 becomes production-ready, we can easily merge/promote these docs to the main documentation structure.

### Documentation Files in v2-docs/

**Vision & Architecture:**
- `APP_VISION_AND_SCOPE.md` - Product vision and scope definition
- `ARCHITECTURE_OVERVIEW_V2.md` - Module architecture and layer structure
- `IMPLEMENTATION_PHASES_V2.md` - Phase-by-phase implementation roadmap
- `AGENTS_V2.md` - Execution guide for AI agents

**Analysis & Reviews:**
- `V1_VS_V2_ANALYSIS_REPORT.md` - v1 code quality assessment and porting strategy (~17k lines mapped)
- `V2_BOOTSTRAP_REVIEW_2025-12-05.md` - Comprehensive Phase 0-1 review
- `PHASE_0_1_VERIFICATION_REPORT_2025-12-05.md` - Technical verification report

**Implementation Tasks:**
- `PHASE_2_TASK_PIPELINE_STUBS.md` - Phase 2 implementation guide (Pipeline Stubs & Core Persistence)

### When Adding New v2 Documentation

1. Create the file in `v2-docs/`
2. Use descriptive names with dates for time-sensitive reports
3. Update this README with a brief description if it's a major document
4. Cross-reference from other docs as needed

### Enforcement

This rule is enforced for:
- Branch: `architecture/v2-bootstrap`
- Related v2 development branches

All AI agents (Copilot, ChatGPT, etc.) working on v2 MUST follow this convention.

---

**Last Updated:** 2025-12-05  
**Maintained By:** v2 Development Team
