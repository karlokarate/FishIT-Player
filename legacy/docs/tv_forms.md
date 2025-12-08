# TV Form Kit → FishForms

Scope
- DPAD-first form rows for Settings/Setup screens: Switch, Slider, TextField, Select, Buttons.
- Consistent TV focus visuals (scale + halo), clear enabled/disabled/error states, and inline helper/error hints.

Package
- Canonical: `app/src/main/java/com/chris/m3usuite/ui/layout/FishForm.kt`
  - `FishFormSection` – section header + description, uses FishTheme paddings + FocusKit.focusGroup
  - `FishFormSwitch` – label + switch; DPAD links via `FocusKit.onDpadAdjustLeftRight`
  - `FishFormSelect` – label + value; LEFT/RIGHT cycles options; shareable Option label lambda
  - `FishFormSlider` – numeric slider with Fish tokens for track size/colors
  - `FishFormTextField` – read-only row, opens modal dialog for input; supports password/email/uri keyboards
  - `FishFormButtonRow` – primary/secondary buttons, busy indicator, Fish spacing
  - `TvKeyboard` enum (Default/Uri/Number/Password/Email)
- Legacy wrappers (`app/src/main/java/com/chris/m3usuite/ui/forms/*`) now delegate to FishForms for backwards compatibility; scheduled for removal once all screens migrate.

DPAD behavior
- Up/Down moves between rows naturally (FocusKit focus group).
- LEFT/RIGHT changes values (Switch/Slider/Select) via centralized FocusKit helpers.
- TextField opens a modal edit dialog on Enter/Click; values validate before submit.

Validation
- `ValidationState.Ok | Error(message)` and `ValidationHint(...)` show helper/error below each row.
- Submit buttons should be disabled when any hard errors are present.

Usage status
- `CreateProfileSheet`, `PlaylistSetupScreen` und die TV-spezifischen Settings-Sektionen nutzen FishForms (`FishFormSection/Switch/Select/TextField`).
- Phone/Tablet Pfade behalten die klassischen Material OutlinedTextFields; FishForms greifen automatisch nur auf TV (`BuildConfig.TV_FORMS_V1`).

Guidelines
- Vertical spacing: 6–8dp between rows, 8–12dp between sections.
- Labels use `bodyLarge`, helper/error use `bodySmall`.
- Keep row content single-line; overflow with ellipsis.
- Prefer short helper messages; error messages should be actionable.

Feature flag
- `BuildConfig.TV_FORMS_V1` (default ON). While wrappers exist, FishForms respect the same gating; once migration completes the flag will gate the unified FishForms module.

Notes
- Focus visuals + DPAD all come from FocusKit, matching the FishRow/FishTile behavior.
- FishForms share FishTheme paddings, making them safe to reuse across modules (Setup, Settings, Profile, future flows).
