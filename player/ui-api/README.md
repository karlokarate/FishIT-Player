# player:ui-api

**Status:** RESERVED (stub module)  
**Purpose:** UI-facing interfaces/contracts only (no implementation, no screens)

## Overview

This module is reserved for future player UI API extraction. It will contain stable interface definitions that enable clean separation between player UI implementation and its consumers.

## Responsibilities

- Define stable UI API interfaces
- Player screen contract definitions
- UI event/state models
- Navigation integration contracts

## Contract Rules

1. **Interfaces only, NO implementation code**
2. **NO Compose dependencies** (no @Composable, no UI code)
3. **NO Hilt dependencies** (contracts should be DI-agnostic)
4. Once created, player:ui will depend on player:ui-api
5. This enables future modularization of player UI

## TODO

- [ ] Define player UI contracts when ready for split
- [ ] Create screen factory interfaces
- [ ] Define UI event/state models
- [ ] Create navigation integration contracts

## References

- `AGENTS.md` - Module manifest and rules
- `player/ui` - Current player UI implementation
- `playback/domain` - Playback domain contracts
