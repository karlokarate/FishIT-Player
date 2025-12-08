> LEGACY (V1) – historical document  
> Not valid for v2. For current architecture see `V2_PORTAL.md` and `docs/v2/**`.

# Fish* Migration Checklist (Tiles/Rows Centralization)

## Vorbereitung
- FishTheme setzen: Global oder screen‑weise `FishTheme { … }` um alle Tiles/Rows mit Tokens zu versorgen.
- Tokens prüfen/setzen: `tileWidthDp`, `tileHeightDp`, `tileCornerDp`, `tileSpacingDp`, `contentPaddingHorizontalDp`, `focusScale`, `enableGlow`.
- FocusKit nutzen: zentrale DPAD/Fokus‑Schicht; keine lokalen Fokus‑Helfer (`tvClickable`/`focusScaleOnTv`/`onPreviewKeyEvent`) mehr in Screens.

## Altstellen prüfen (laufende Nachpflege)
- Im Home-Modul sind die alten Wrapper (`LiveRow`, `SeriesRow`, `VodRow`, Paged) entfernt. Start/Library/Detail nutzen `FishRow`/`FishRowPaged` + `FishMediaTiles` direkt.
- ✅ `ui/components/rows/HomeRows.kt` nutzt jetzt FocusKit (`LiveAddTile`, Assign-Badges); keine `ui.skin`-Modifer mehr offen.
- ✅ Legacy `ui/cards/*` und `BuildConfig.CARDS_V1` entfernt; FishTile/FishRow ist verbindlich.
- Legacy DPAD/Fokus: nach Entfernung der oben genannten Komponenten keine direkten `onPreviewKeyEvent`/`tvClickable` außerhalb FocusKit mehr zulassen.
- ✅ StartScreen nutzt `FishHeaderHost` + `FishHeaderData` (Text/Chip) für II. Overlay-Header – keine eigenen Header-Items mehr.
- ✅ LibraryScreen erzeugt Header (Suche/Kuration/Provider/Genres/Jahre/Telegram) ausschließlich über `FishHeaderData` und zeigt keine manuellen Text-Header mehr.

## Inhalte/Slots planen (pro Typ)
- VOD: Titel (+Jahr), Posterwahl, Resume‑Fortschritt, NEW‑Badge, Assign‑Badge (+/✓), Bottom‑End Actions (Play, Assign), Klick (Assign/Details), Fokus‑Logs.
- Series: Titel (+Jahr), Posterwahl, Assign‑Badge, später „Weiter schauen“/Episoden‑Progress.
- Live: Logo‑Wahl, später EPG Now/Next‑Progress, ggf. Quick‑Actions.

## Engine wählen (pro Row)
- Light: kleine Chip/Overlay‑Leisten ohne DPAD‑Sonderfälle.
- Media: Start/Library Medien‑Rows (VOD/Series/Live), `edgeLeftExpandChrome` optional.
- Paged: Suchergebnisse/Endlose Listen mit `LazyPagingItems`.

## Portierung: VOD‑Row (konkrete Schritte)
1) ✅ CARDS_V1‑Branch entfernt – Rows nutzen ausschließlich FishTile.
2) Row ersetzen:
   - `FishRow(items, stateKey, edgeLeftExpandChrome=true, initialFocusEligible=…) { media -> ... }`
   - Oder `FishRowPaged(pagingItems, stateKey, edgeLeftExpandChrome=…) { idx, media -> ... }`
3) Tile inhaltlich füttern:
   - `val c = buildVodTileContent(media, newIds, allowAssign=…, onOpenDetails={ onOpen(media) }, onPlayDirect={ onPlay(media) }, onAssignToKid={ onAssign(media) })`
   - `FishTile(title=c.title, poster=c.poster, showNew=c.showNew, selected=c.selected, resumeFraction=c.resumeFraction, topStartBadge=c.topStartBadge, bottomEndActions=c.bottomEndActions, footer=c.footer, onFocusChanged=c.onFocusChanged, onClick=c.onClick)`

## Portierung: Telegram‑Row (Start‑Suche)
- ✅ erledigt: `StartScreen` nutzt `TelegramRow` + `FishTile` (Badge im `topStartBadge`).
- ✅ StartScreen-Hauptsektionen (Serien/VOD/Live) laufen jetzt vollständig über FishRow/FishTile; AccentCard/BoxWithConstraints wurden entfernt.
- Folgeaktionen: Badge-Styling zentral in `FishActions`/`FishLiveContent` dokumentieren.

## Fokus/DPAD & Scroll
- Kein per‑Tile Bring‑Into‑View: FishTile hat `autoBringIntoView=false`.
- Edge‑Left→Chrome: `FishRow` mit `edgeLeftExpandChrome=true` (Media/Paged).
- Initialfokus: `initialFocusEligible=true` nur für die erste gewünschte Row.

## Assign/Actions
- Top‑Left Badge (+/✓): `FishActions.AssignBadge` (via FishVodContent eingebunden).
- Bottom‑End:
  - Play: `FishActions.VodBottomActions(onPlay)` (nicht fokusierbar).
  - Assign: `FishActions.AssignBottomAction(onAssignToKid)` (nicht fokusierbar, wenn Assign nicht aktiv).

## Fokus‑Logs
- `onFocusChanged` aus Content an FishTile durchreichen → `FishLogging.logVodFocus`.

## Design/Optik (fix über Tokens)
- ContentScale: Fit (FishTile default), keine lokalen Abweichungen.
- Ecken: `tileCornerDp`.
- Spacing/Padding: `tileSpacingDp`, `contentPaddingHorizontalDp`.
- Größe: `tileWidthDp`/`tileHeightDp` (Row füllt dadurch vertikal).
- Fokus‑Scale: `focusScale`; globaler Glow via Token `enableGlow` (FocusKit wrapper).

## Bereinigung pro portierter Row
- Weiterhin im Blick behalten: neue lokale Skin-/DPAD-Hacks vermeiden; FishKit ist verbindlich.
- Statischer Rahmen/Overlays vermeiden; Buttons in Overlays bleiben `focusProperties { canFocus=false }`.
- Kontrolliere nach jeder Umstellung den Telegram‑Badge‑Slot (Top‑Start) und die Resume‑Leiste (FishResumeTile).

## Tests/Abnahme (je Row)
- Visuell: Tiles füllen Row; Abstände fix; Fokus (Scale+Frame+Glow); NEW/Assign‑Badges; Resume‑Linie; Titel/Plot im Fokus; Bilder sichtbar (Headers korrekt).
- DPAD: LEFT/RIGHT je Tile; LEFT am ersten Tile → Chrome (wenn konfiguriert); Bottom‑Buttons nicht fokusierbar.
- Klick: Assign aktiv → toggle; sonst → Details; Play‑Button startet (Launcher).
- Logging: Fokus‑Logs sichtbar (GlobalDebug).
- Performance: Low‑Spec TV ggf. Glow per Token abschalten.

## Aufräumen (Repo-weit, nach Abschluss)
- ✅ `BuildConfig.CARDS_V1`, `ui/cards/*` und `PosterCard*` Referenzen entfernt (2025-10-07).
- ✅ Reorder-Flows (`LiveTileCard`/`ReorderableLiveRow`) laufen jetzt über `FishRow` + FocusRowEngine (`itemModifier`) und nutzen FishTile/FocusKit; alte Skin-Abhängigkeiten entfernt.
- Restliche Screens (Player, Settings, Detail-Masken, Form-Kit) auf Fokus-Primitives der `FocusKit`-Fassade umstellen, bevor die alten Skin-Extensions entfernt werden.

## FishForms – TV Formular-Zentralisierung
- [ ] DPAD-Audit fortschreiben (`rg onPreviewKeyEvent`, `rg focusScaleOnTv`) und Findings im Fokus-Audit dokumentieren. *(Scan aktualisiert am 2025-10-08; HomeRows DPAD-Handler bereits auf FocusKit umgestellt.)*
- [x] Neue FishForm-Komponenten (`FishFormSwitch/Select/Slider/TextField/ButtonRow/Section`) definieren, basierend auf FocusKit + FishTheme Tokens. *(FishForm.kt erstellt, DPAD via FocusKit zentralisiert.)*
- [x] PlaylistSetupScreen, CreateProfileSheet, Settings-Formulare auf FishForms migrieren (TV/DPAD Smoke-Test nach jedem Schritt). *(TV-Pfade für CreateProfileSheet, PlaylistSetupScreen und Settings auf FishForms umgestellt.)*
- [x] Legacy `ui/forms/*` deprecaten und nach vollständiger Migration entfernen (inkl. Flags wie `TV_FORMS_V1`). *(Wrappers gelöscht; Screens nutzen FishForms direkt.)*
- [ ] Dokumentation & Tooling aktualisieren (`docs/tv_forms.md`, `docs/fish_layout.md`, `tools/audit_tv_focus.sh`) und die neuen Komponenten in CI-Audits aufnehmen.

## Doku/Changelog
- Nach jeder portierten Row: CHANGELOG-Eintrag und `docs/fish_layout.md` (Mapping/Status) aktualisieren.
