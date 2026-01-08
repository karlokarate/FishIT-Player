# OBX Layering PLATIN Refactor - Execution Roadmap

**Version:** 1.0  
**Status:** Planning  
**Parent Issue:** #621  
**Created:** 2026-01-08

## Executive Summary

This roadmap provides a comprehensive, phase-by-phase execution plan for refactoring the ObjectBox persistence layer from 23 legacy `Obx*` entities to 16 unified `NX_*` entities, implementing a proper SSOT (Single Source of Truth) work graph architecture.

### Key Metrics
- **From:** 23 legacy entities with scattered responsibilities
- **To:** 16 NX_ entities in unified work graph
- **Duration:** 28-39 development days
- **Phases:** 7 phases (0-6) with incremental rollout
- **Risk Mitigation:** Mode toggles + kill-switch rollback capability

### Core Transformations
1. **Unified Work Graph**: `NX_Work` as central UI SSOT (replaces fragmented VOD/Series/Live entities)
2. **Multi-Account Ready**: `accountKey` in all `sourceKey` formats
3. **Playback Variants**: Separate `NX_WorkVariant` per quality/encoding/language
4. **Ingest Ledger**: Explicit Accept/Reject/Skip tracking (no silent drops)
5. **Profile System**: Unified `NX_ProfileRule` (replaces 4 separate Kid entities)
6. **Cloud-Ready**: `NX_WorkUserState` + `NX_CloudOutboxEvent` for Firebase sync

## See Full Documentation

For complete task breakdown with acceptance criteria, see sections below:
- Phase 0: Contracts & Guardrails (2-3 days)
- Phase 1: Schema + Repositories (5-7 days)
- Phase 2: Ingest Path (4-5 days)
- Phase 3: Migration Worker (5-7 days)
- Phase 4: Dual-Read UI (7-10 days)
- Phase 5: Stop-Write Legacy (2-3 days)
- Phase 6: Cleanup (3-4 days)

---

**Note:** This is a summary document. Full detailed tasks are tracked in the GitHub issue.
For implementation details, see:
- Parent Issue: #621
- Contract: `/contracts/NX_SSOT_CONTRACT.md` (to be created in Phase 0)
- Architecture: `AGENTS.md` Section 4
