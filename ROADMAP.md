#
# FishIT Player — Roadmap (Q4 2025)

Hinweis
- Der vollständige Verlauf steht in `CHANGELOG.md`. Diese Roadmap listet nur kurzfristige und mittelfristige, umsetzbare Punkte.
- Maintenance 2025‑09‑27: Manifest‑Icon auf `@mipmap/ic_launcher` (+ `roundIcon`) vereinheitlicht; kein Roadmap‑Impact.
- Maintenance 2025‑09‑28: Build‑Blocking Lücken geschlossen (Nav‑Extension, TV‑Focus‑Compat, TvRowScroll, safePainter, Adults‑Filter, XtreamImportCoordinator). Kein neues Feature; Roadmap unverändert.
 
Prio 1 — Globale Zentralisierung Fokus/TV‑Darstellung
- Ziel: Einheitliche, zentral gesteuerte Fokusdarstellung und -navigation (DPAD) in allen UIs; keine verstreuten Implementierungen mehr.
- Leitfaden: `tools/Zentralisierung.txt` (kanonisch). Fokus-/TV‑Änderungen erfolgen ausschließlich dort bzw. in den dort benannten Modulen.
- Aufgaben:
  - Primitives konsolidieren: `TvFocusRow`, `tvFocusableItem`, `tvClickable`, `tvFocusFrame`, `focusGroup`.
  - Screens migrieren, lokale Workarounds entfernen (manuelles DPAD, ad‑hoc bringIntoView).
  - Globale Stilquelle (Halo/Scale/Farben), neutrale Clickables ohne Doppel‑Effekte.
  - CI‑Audit (`tools/audit_tv_focus.sh`) schärfen und durchsetzen.
  - Beispiele/Docs im Leitfaden; Code‑Lagen vereinheitlichen (keine Duplikate in Unterordnern).
  - Modul‑Migration: auth/ProfileGate → FocusKit (DONE); weitere Module folgen.
- Maintenance 2025-10-03: Detailseiten neu auf stabilem Scaffold (HomeChromeScaffold → Box → Card + LazyColumn). VOD: OBX-first + Xtream-On-Demand, Plot/Facts als Karten, ein Scroll-Container, sichere Play/Resume-Actions. Bruchfeste Klammerstruktur; Build-Fehler behoben. StartScreen: Klammerfehler korrigiert (Compose-Kontextfehler beseitigt).
 - Maintenance 2025-10-03: DetailMask eingeführt (`ui.detail.DetailPage`/`DetailBackdrop`/`DetailSections`) – einheitlicher Aufbau für alle Details (Hero/Gradients/AccentCard/Header/Sections). VOD nutzt bereits die Maske; Series/Live Migration als nächstes.
- Feature 2025-10-03: Globaler Zuweisen‑Modus auf Start. Mehrfachauswahl über Reihen/Suche, Profil‑Picker für Bulk‑Freigabe (ObjectBox‑Batch), klare Selektion und permisssions‑Gating. VOD‑Details zeigen wieder Freigabe/Entfernen in der `MediaActionBar`.
- Maintenance 2025-10-04 (Start): Vertikale Gewichtung der Start-Abschnitte (Serien/Filme/Live) implementiert; Landscape nutzt 40/40/20, Portrait gleichmäßig. Keine Überlappung mehr ganz oben; Zeilenhöhen werden in Landscape automatisch an die Kartenhöhe angepasst.
 - UX 2025-10-04 (Start Assign): Start-Button zum Zuordnen entfernt. Stattdessen hat jedes Tile einen globalen, klickbaren Badge (+/✓) für die Markierung; markierte Tiles zeigen einen sichtbaren Rahmen. Ein schwebender Plus-Button erscheint bei mindestens einer Markierung und öffnet den Profil-Picker zur Zuordnung.
 - Maintenance 2025-10-04 (TV Forms): Profil-Erstellen-Dialog auf TV-Form-Kit umgestellt (TvTextFieldRow/TvSwitchRow/TvButtonRow). DPAD/OK funktionieren stabil; Fokus-Falle durch Textfeld-IME entfällt.
 - Maintenance 2025-10-04 (Whitelist Refresh): Nach Bulk-Zuordnung in Start werden gefilterte Listen sofort neu geladen (und wie bisher bei Profilwechsel). Sichtbarkeit für Kids-Profile ist damit konsistent.
  
Completed (moved to Changelog)
- Detail screens: full-screen hero background (TV/portrait/landscape) and removal of FishBackground overlay.
- Details centralization: unify VOD/Series/Live via `ui/detail` (DetailHeader+MetaChips+HeaderExtras+DetailFacts), legacy paths removed; on-demand metadata load only; DPAD‑LEFT→Chrome disabled in details; BACK re-enabled.
- Maintenance 2025‑09‑29 (TV Low-Spec): Laufzeitprofil für TV hinzugefügt (reduzierte Fokus‑Effekte, kleinere Paging‑Fenster, OkHttp Drosselung, Coil ohne Crossfade). Während der Wiedergabe werden Xtream‑Seeding‑Worker pausiert und danach wieder aktiviert (wenn zuvor aktiv).
- Maintenance 2025‑09‑30: LiveDetailScreen Buildfix – Klammerfehler behoben, EPG/Kid-Dialogs in den Screenkörper verlagert. Kein Roadmap‑Impact.
 - Maintenance 2025‑09‑30: Start/Library Playback-Migration – Verbleibende Direktaufrufe von PlayerChooser auf PlaybackLauncher (Flag `PLAYBACK_LAUNCHER_V1`) umgestellt; VOD „Ähnliche Inhalte“ und Live „Mehr aus Kategorie“ öffnen nun Details des gewählten Elements (Lambdas an Screens ergänzt und in MainActivity verdrahtet).
- Maintenance 2025‑10‑01: Telegram – Settings zeigen nun Chat‑Namen für ausgewählte Film/Serien‑Sync‑Quellen; Backfill‑Worker `TelegramSyncWorker` liest jüngste Nachrichten aus selektierten Chats und indiziert Minimal‑Metadaten (ObxTelegramMessage).
- Maintenance 2025‑10‑01: Telegram – Zusätzliche Rows in Library (VOD/Series) je ausgewähltem Chat, mit on‑demand Thumbnails und blauem „T“-Badge. Globale Suche auf Start bindet Telegram als zusätzliche Row ein. Player nutzt für tg:// geringe RAM‑Buffer, IO (TDLib) cached.
- Maintenance 2025‑10‑02: Telegram – Service stellt Chat-IPC (`listChats`, `resolveChatTitles`, `pullChatHistory`) bereit; Settings-Dialog + Sync-Worker hängen sich an denselben TDLib-Kontext, sodass die Auth-Session erhalten bleibt und kein zweiter Reflection-Client nötig ist. Login-Dialog erkennt die lokale Telegram-App, setzt `PhoneNumberAuthenticationSettings` (`is_current_phone_number`, Tokens) und öffnet den QR-Link automatisch für Single-Device-Anmeldungen.

---

PRIO‑1: Kids/Gast Whitelist – Actions + Multi‑Select (Q4 2025)
- Fix filtering reliability for kid/guest profiles (effective allow = item allows ∪ category allows − item blocks; guests treated like kids).
- Detail actions: Re-enable "Für Kinder freigeben" / "Freigabe entfernen" in MediaActionBar on Live/VOD/Series details (gated by canEditWhitelist). Open profile picker and call KidContentRepository allow/disallow.
- Multi‑Select (phase 1 = Allow only):
  - Add global "Zuweisen"-Modus on Start/Library/Search (TV/DPAD-first). When active, tiles become selectable across rows and global search.
  - Route-scoped selection state (encoded media IDs) with a visible count and CTA "Profil wählen".
  - Bulk-apply allow via KidContentRepository. Show snackbar/Toast with the profile name and count.
- TV usability: Selection overlay uses tvFocusableItem without breaking DPAD traversal; toggling selection via DPAD CENTER.
- Performance: Bulk ObjectBox writes in a single transaction per type to avoid UI jank.
- Race‑Safety: Decode encoded media IDs just-in-time to avoid drift during Xtream delta updates; skip missing/invalid rows gracefully.

Status: Backend helpers + selection scaffold landed; detail actions and UI wiring follow next.
## Kurzfristig (2–4 Wochen)

PRIO‑1: TV Fokus/DPAD Vereinheitlichung
- Alles Horizontale → `TvFocusRow` (inkl. Chips/Carousels).
- Alles Interaktive → `tvClickable`/`tvFocusableItem` (No‑Op auf Phone).
- Zentrale Registry für Scroll+Fokus je Route/Row (`ScrollStateRegistry`).
- Chrome: einheitliche Auto‑Collapse/Expand‑Trigger im `HomeChromeScaffold`.
- Kein `onPreviewKeyEvent` (außer echte Sonderfälle).
- Audit‑Skript erzwingt die Regeln (`tools/audit_tv_focus.sh`).

Status: umgesetzt und in CI verankert (Audit Schritt). Buttons/Actions erhalten auf TV eine visuelle Fokus‑Hervorhebung (`TvButtons` oder `focusScaleOnTv`).

- PRIO‑1: TV‑Form‑Kit (Settings/Setup)
  - v1 mit DPAD‑optimierten Rows (Switch/Slider/TextField/Select/Button) unter `ui.forms` bereitgestellt.
  - Validierungs‑Hints, konsistenter Fokus (scale + halo), Dialog‑Eingabe für Textfelder auf TV.
  - Proof‑of‑Concept Migration: `PlaylistSetupScreen` (gated via `BuildConfig.TV_FORMS_V1`, default ON).
  - Nächste Schritte: `SettingsScreen` Kernoptionen (Player‑Wahl, Cache‑Größe) und `XtreamPortalCheckScreen` (Status/Retry, Eingaben) migrieren.

- TV Fokus QA: Nach Compose-Updates automatisierte Regression (Screenshot/UI-Test) für TvFocusRow + Tiles aufsetzen, damit Scale/Halo-Verhalten gesichert bleibt.
- Fonts (UI): Korrupte/fehlende TTFs ersetzen (AdventPro, Cinzel, Fredoka, Inter, Merriweather, MountainsOfChristmas, Orbitron, Oswald, Playfair Display, Teko, Baloo2). Ziel: stabile dekorative Familien ohne Fallbacks.
- Media3 Pufferung: `DefaultLoadControl` pro Typ prüfen und moderate Puffer für VOD/Live definieren (kein aggressives Prebuffering; TV‑Stabilität bevorzugen).
- Coil3 Netzwerk: Explizite OkHttp‑Factory prüfen/integrieren, falls stabil verfügbar (sonst bei per‑Request NetworkHeaders bleiben). `respectCacheHeaders(true)` evaluieren.
- EPG Konsistenz: Room vollständig aus Flows entfernen (UI/Prefs‑Reste wie `roomEnabled` aufräumen); EPG Now/Next ausschließlich ObjectBox + XMLTV Fallback.
- Seeding‑Whitelist (Regions): Settings‑Multi‑Select fertigstellen/validieren (Default DE/US/UK/VOD); Quick‑Seed nur für erlaubte Prefixe ausführen.
- CI/Build: Job für `assembleRelease` + Split‑APKs (arm64‑v8a, armeabi‑v7a) erzeugen; Artefakte im CI hinterlegen. Keystore verbleibt lokal (Unsigned‑Artefakte).
- Git WSL Push: Repo‑Docs um `core.sshCommand`/SSH‑Config (Deploy‑Key) ergänzen, damit Push aus WSL/AS stabil funktioniert.

- PRIO‑1: MediaActionBar (UI Unification)
  - Zentrales Action‑Modell (`ui.actions`) + `MediaActionBar` eingeführt.
  - Migration v1: `VodDetail`, `SeriesDetail`, `LiveDetail` unter Flag `BuildConfig.MEDIA_ACTIONBAR_V1` (default ON).
  - Reihenfolge vereinheitlicht: Resume? → Play → Trailer? → Add/Remove (Live) → OpenEPG? → Share?; Telemetrie‑Hooks vorhanden.
  - Serien: pro‑Episode wird eine MediaActionBar angezeigt (Resume? → Play → Share).

- PRIO‑1: DetailScaffold (Header + MetaChips)
  - `ui.detail/*` eingeführt (Header/Scrim/MetaChips/Scaffold) mit Flag `BuildConfig.DETAIL_SCAFFOLD_V1` (default ON).
  - Migration abgeschlossen: VOD + Serie + Live nutzen `DetailHeader` und zentrale Extras/Facts; legacy Header ausgebaut.

- PRIO‑1: UiState Layer
  - `UiState` + `StatusViews` + `collectAsUiState` eingeführt; Flag `BuildConfig.UI_STATE_V1` (default ON).
  - Migration v1: Detail‑Screens (VOD/Serie/Live) nutzen das Status‑Gate (Loading/Empty/Error/Success). Start nutzt kombinierten Paging‑Collector im Suchmodus; Library‑Suche reaktiviert (Paging + UiState).

## Mittelfristig (4–8 Wochen)

- TDLib Phase‑2 (offen):
  - E‑Mail‑Flows: `AuthorizationStateWaitEmailAddress`/`AuthorizationStateWaitEmailCode`.
  - Storage‑Cleanup: `getStorageStatistics`‑basiert (LRU/selten genutzt) zusätzlich zum GB‑Limit.
  - Logging‑Kontrolle: `setLogStream` (Datei optional), `setVerbosityLevel(1)` in Prod konfigurierbar.
  - CI für `libtdjni.so` (arm64) aufsetzen (optional Artefakte).
- Bilder: Optional SVG/Video‑Frame Decoder via Coil‑Components hinzufügen, falls gebraucht.
- Player UX: Subtitle/Audio‑Auswahl verfeinern; Fehler‑Dialoge (Netzwerk/401/Timeout) verbessern.
- Import/Export: Settings‑Export/Import UI in Settings finalisieren; Drive‑Shim bei Bedarf durch echte Implementierung ersetzen.

---

Abgeschlossen → siehe `CHANGELOG.md`.
- PRIO‑1: Cards‑Bibliothek
   - `ui.cards` mit `PosterCard`/`ChannelCard`/`SeasonCard`/`EpisodeRow`, Flag `BuildConfig.CARDS_V1` (default ON).
   - Einsatz: Start/Library Rows via `HomeRows` Delegation; Serien‑Episoden nutzen `EpisodeRow`.
 - PRIO‑1: PlaybackLauncher (Unification)
   - `playback/` mit `PlayRequest`/`PlayerResult` und `rememberPlaybackLauncher`; Flag `BuildConfig.PLAYBACK_LAUNCHER_V1` (default ON).
   - V1 Migration: VOD/Serie/Live Detail + ResumeCarousel rufen Playback über Launcher (intern/extern via PlayerChooser, Resume-Lesen vor Start).
