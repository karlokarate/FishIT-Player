# TV Form Kit (v1)

Scope
- DPAD-first form rows for Settings/Setup screens: Switch, Slider, TextField, Select, Buttons.
- Consistent TV focus visuals (scale + halo), clear enabled/disabled/error states, and inline helper/error hints.

Package
- `app/src/main/java/com/chris/m3usuite/ui/forms/`
  - `TvFormSection.kt` – section header + description, wraps a Column with `focusGroup()`
  - `TvSwitchRow.kt` – label + switch; whole row clickable; LEFT/RIGHT toggle
  - `TvSliderRow.kt` – label + numeric + progress track; LEFT/RIGHT increments by `step`
  - `TvTextFieldRow.kt` – read-only row; click opens edit dialog (prevents TV keyboard traps)
  - `TvSelectRow.kt` – label + current value; LEFT/RIGHT cycles options
  - `TvButtonRow.kt` – primary/secondary buttons (busy optional)
  - `Validation.kt` – `ValidationState`, `Validator<T>`, `ValidationHint`

DPAD behavior
- Up/Down moves between rows naturally (focus group).
- LEFT/RIGHT changes values (Switch/Slider/Select), not focus.
- TextField opens a modal edit dialog on Enter/Click; values validate before submit.

Validation
- `ValidationState.Ok | Error(message)` and `ValidationHint(...)` show helper/error below each row.
- Submit buttons should be disabled when any hard errors are present.

Usage example (PlaylistSetupScreen)
- Gated by `BuildConfig.TV_FORMS_V1`.
- Mode select via `TvSelectRow(M3U|Xtream)`.
- M3U: `TvTextFieldRow` for M3U/EPG.
- Xtream: `TvTextFieldRow` (host/port/user/pass), `TvSwitchRow` (HTTPS), `TvSelectRow` (output).
- Submit: `TvButtonRow` with busy indicator.

Guidelines
- Vertical spacing: 6–8dp between rows, 8–12dp between sections.
- Labels use `bodyLarge`, helper/error use `bodySmall`.
- Keep row content single-line; overflow with ellipsis.
- Prefer short helper messages; error messages should be actionable.

Feature flag
- `BuildConfig.TV_FORMS_V1` (default ON). Screens can opt-in and keep legacy controls as fallback.

