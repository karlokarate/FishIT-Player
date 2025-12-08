> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# UiState Layer (v1)

Goal
- Provide a unified state model for screens to render one of Loading, Empty, Error (with retry), or Success.

API
- `UiState<T>`: Loading | Success<T> | Empty | Error(message, cause, retry?)
- `LoadingState()`, `EmptyState(text)`, `ErrorState(text, onRetry)` — simple status views
- `collectAsUiState(flow, emptyWhen)` — lifecycle-aware collector helper

Flags
- `BuildConfig.UI_STATE_V1` (default ON). Screens can opt-in and keep legacy loaders as fallback when OFF.

Migration pattern
- Hold a `var uiState by remember { mutableStateOf<UiState<Unit>>(Loading) }` at top-level.
- When loading begins → `Loading`. On success → `Success(Unit)`. On no data → `Empty`. On error → `Error(msg) { retry }`.
- Render early-return gate:
  - When `UI_STATE_V1`: `when(uiState) { Loading -> LoadingState(); Empty -> EmptyState(); Error -> ErrorState(..); Success -> content() }`
  - Else: legacy progress handling.

Status views
- Minimal aesthetics that work for TV and phone. Screens remain responsible for background/hero visuals.

Adoption (v1)
- Detail screens (VOD/Series/Live) migrated to UiState gating.
- Start/Library pending: they have multiple data sources; will adopt collector per flow and map combined emptiness.

Fish* note
- UiState complements Fish*: when state is Success the screen composes rows via FishRow + FishTile; Loading/Empty/Error render the status views.
