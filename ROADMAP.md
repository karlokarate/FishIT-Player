#
# FishIT Player — Roadmap (Q4 2025)

Hinweis
- Der vollständige Verlauf steht in `CHANGELOG.md`. Diese Roadmap listet nur kurzfristige und mittelfristige, umsetzbare Punkte.
- Maintenance 2025‑10‑31: Release-Builds schlagen nicht mehr auf fehlendem `Locale`-Import
  im Telegram-Dienst fehl; Kotlin 2.0 Release kann wieder kompiliert werden.
- Maintenance 2025‑10‑28: Telegram-Logs lassen sich nun per Settings-Schalter live als Snackbar einblenden; ideal für mehrstufige TDLib-Diagnosen ohne Logcat.
- Maintenance 2025‑10‑29: Telegram-Login akzeptiert wieder lokale Nummern ohne "+" und meldet fehlende TDLib-Starts sofort. Der TDLib-Loglevel lässt sich auf Touch-Geräten über einen Slider anpassen.
- Maintenance 2025‑10‑27: Telegram‑Login normalisiert lokale Telefonnummern via Gerätestandort (E.164), sodass WAIT_FOR_NUMBER nach Eingabe ohne führendes "+" nicht mehr hängen bleibt.
- Maintenance 2025‑09‑27: Manifest‑Icon auf `@mipmap/ic_launcher` (+ `roundIcon`) vereinheitlicht; kein Roadmap‑Impact.
- Maintenance 2025‑09‑28: Build‑Blocking Lücken geschlossen (Nav‑Extension, TV‑Focus‑Compat, TvRowScroll, safePainter, Adults‑Filter, XtreamImportCoordinator). Kein neues Feature; Roadmap unverändert.
- Maintenance 2025‑10‑08: Telegram TDLib‑Streaming liest API‑ID/HASH zur Laufzeit aus Settings (Fallback, wenn BuildConfig leer). Keine Roadmap‑Auswirkung.
- Maintenance 2025‑10‑10: TDLib‑Auth konformisiert – Service queued Phone/Code/Password bis TDLib die jeweiligen States anfordert; 400 „Initialization parameters are needed“ triggert einmaliges Re‑Senden der TdlibParameters + DB‑Key; 406 „UPDATE_APP_TO_LOGIN“ bleibt im Code‑Flow und bietet QR nur noch per Button an.
- Maintenance 2025‑10‑10: Settings → Telegram auf FocusKit/FishForm umgestellt. Keine auto‑fokussierten Textfelder mehr auf TV; API‑Eingaben werden erst bei Bestätigung gespeichert (keine Live‑Writes während Tippen). Cache‑Limit per DPAD‑Slider.
- Maintenance 2025‑10‑15: TV Mini-Player Overlay wieder funktionsfähig (MiniPlayerHost/MiniPlayerState + PlaybackSession
  Navigator). Release-Builds binden `objectbox-kotlin` ein, damit TelegramSeriesIndexer (`query`/`put`) ohne Debug-Abhängigkeiten
  kompiliert.
- Maintenance 2025‑10‑17: Telegram Sync liefert nun globale Banner mit Fortschritt/Zähler, Settings melden echte Ergebnisse und
  die Library zeigt die Live/VOD/Serien-Schalter im Header wieder an. Login-Dialog besitzt eine "Per Code anmelden"-Fallback.
- Maintenance 2025‑10‑18: Release-Build brach auf Kotlin 2.0 wegen Telegram-Indexer/Service Typ-Mismatches. IDs bleiben jetzt
  als `Int`-Sets im Indexer und der TDLib-Schreibpfad liefert wieder konkrete Outcomes, sodass `:app:compileReleaseKotlin`
  erfolgreich durchläuft.
- Maintenance 2025‑10‑19: InternalPlayer nutzt die Media3-FFmpeg-Extension und priorisiert hochwertige Audio-/Video-Codecs
  (höchste verfügbare Bitrate und bevorzugte Formate werden automatisch gewählt).
- Maintenance 2025‑10‑21: Media3 1.8.0 ist wieder verfügbar. Interner Player und
  Trailer-Preview teilen sich eine RenderersFactory, die die FFmpeg-Extension
  bevorzugt und Decoder-Fallback aktiviert lässt.

- Maintenance 2025‑10‑24: Kotlin 2.0 verbietet benannte Argumente bei Funktions-
  typen. TelegramTdlibDataSource ruft den Fallback jetzt positionsbasiert auf,
  sodass `:app:compileDebugKotlin` wieder durchläuft.
- Maintenance 2025‑10‑30: Interner Player nutzt nun `TdlibRandomAccessSource`
  und `RarEntryRandomAccessSource`. `tg://file/<fileId>` wird direkt über TDLib
  mit 512-KiB-Readahead und Backoff gestreamt, `rar://msg/<msg>/<entry>` extrahiert
  MP3s on-the-fly (LRU-Chunk-Cache + Ringbuffer). TDLib-Updates unterstützen
  mehrere Listener gleichzeitig.
- Maintenance 2025‑11‑01: Telegram-Login in das Modul `feature-tg-auth`
  ausgelagert. Auto-SMS via Google User Consent, strukturierte Fehlermeldungen
  und ein orchestrierter QR-/Code-Flow halten die Settings sauber und
  erleichtern künftige Anpassungen.
- Maintenance 2025‑11‑02: Kotlin-2.0-Buildfix. Telegram-Settings importieren
  wieder die Flow-Operatoren, der Chat-Picker ist als `@Composable`
  gekennzeichnet und der SMS-Consent-Manager nutzt einen klaren
  `SupervisorJob`-Scope.
- Maintenance 2025‑10‑25: Telegram-Laufzeitsteuerung erweitert – IPv6/Online-
  Status, Proxy/Loglevel, Auto-Download-Profile, Streaming-Prefetch, Seek-Boost,
  parallele Downloads und Storage-Optimizer sind direkt in den Settings
  schaltbar und werden beim Start konsistent auf TDLib angewendet.
 - Maintenance 2025‑10‑26: Kotlin 2.0 Inferenz-Probleme im TDLib-Reflection-Pfad
   (Auto-Download + PhoneAuth-Constructor Arrays sind jetzt `Array<Class<*>>`) und
   in den Telegram-Settings korrigiert, damit Release-Builds wieder
   kompilieren.
- Maintenance 2025‑10‑23: Build block durch fehlendes Media3-FFmpeg-AAR gelöst.
  Wir binden Jellyfins `media3-ffmpeg-decoder` 1.8.0+1 ein, halten die restlichen
  Media3-Module auf 1.8.0 und behalten die bisherigen ABI-Splits bei.

Prio 1 — Tiles/Rows Centralization (ON)
- Ziel: UI‑Layout vollständig zentralisieren (Tokens + Tile + Row + Content), damit Screens nur noch `FishRow` + `FishTile` verdrahten.
- Module (Stand): `ui/layout/FishTheme`, `FishTile`, `FishRow(Light/Media/Paged)`, `FishVodContent` (VOD), `FishSeriesContent`/`FishLiveContent` (Basis), `FishMeta`, `FishActions`, `FishLogging`, `FishResumeTile`.
- Maßnahmen:
  - VOD Rows (Start/Library/Suche/Telegram) auf `FishRow` + `FishTile` + `FishVodContent` portieren.
  - Bottom-End Assign-Action in VOD anbinden (bereit in FishActions/FishVodContent).
  - Legacy cards removed (2025-10-07); FishTile/FishRow are now the single tile path.
  - Rows nutzen feste Abstände/Padding aus Tokens, ContentScale=Fit, kein per-Tile Bring-Into-View.
  - ✅ FishHeader overlay (Text/Chip/Provider) aktiv für Start- und Library-Sektionen; alte Inline-Header entfernt (2025-10-08).
  - Optional: Title‑outside‑focus (Token) nur falls Poster‑Parität später gewünscht.

  - TODO 2025-10-16: FocusRowEngine parity (initialFocusEligible, edgeLeftExpandChrome, chrome setter) still pending; keep Start/Library wiring ready.
  - TODO 2025-10-16: FishHeader overlay needs gradient/badge polish and accent exposure.
  - TODO 2025-10-16: FishMediaTiles must reintroduce resume/assign states and Telegram play vs. detail handling.

Prio 2 — FocusKit Migration (ON, blockiert durch Prio 1)
- Here’s a fresh repo-wide audit of focus usages and a precise list of modules to migrate to the new FocusKit facade. Grouped by what needs changing to plan the rollout.

- Rows → FocusKit.TvRowXXX
  - Use FocusKit.TvRowLight/Media/Paged instead of direct TvFocusRow/FocusRowEngine or raw LazyRow.
  - ✅ SeriesDetailScreen (Season-Chips) nutzt FocusKit.TvRowLight statt direktem TvFocusRow.
  - ✅ SeriesDetailMask Seasons-Row auf FocusKit.TvRowLight portiert.
  - ✅ StartScreen Live-Picker (Provider-Chips) läuft auf FocusKit.TvRowLight; keine Roh-LazyRow mehr offen.
  - ✅ RowCore-Engine in FocusKit integriert (`ui/focus/FocusRowEngine.kt`); altes Modul in `ui/components/rows` entfernt.
  - app/src/main/java/com/chris/m3usuite/player/InternalPlayerScreen.kt:1204
  - ✅ ReorderableLiveRow now delegates to FishRow/FocusRowEngine via item modifiers; only drag/drop logic remains local.

  Abhängigkeit
  - Finalisierung des FocusKit (End‑to‑end Audit, Entfernen alt. Pfade) erfolgt erst nach Abschluss der Tiles/Rows‑Zentralisierung (Prio 1).

  Guidance
  - Use FocusKit.TvRowLight for chip/overlay rows (filters, season chips, small carousels).
  - Use FocusKit.TvRowMedia for non-paged media rows; FocusKit.TvRowPaged for Paging rows.

- Primitives → FocusKit primitives (tvClickable, tvFocusFrame, tvFocusableItem, focusScaleOnTv)
  - ✅ Completed (2025-10-07). All screens use FocusKit; legacy `ui.skin` facade has been removed.
  - Guidance
    - Keep using `FocusKit.run { … }` for modifier scopes to stay on the single facade.
    - Prefer FocusKit.TvButton/TvTextButton/TvOutlinedButton/TvIconButton for shared button visuals.

- Forms (DPAD adjust) → FocusKit DPAD helpers
  - ✅ Replace ad-hoc onPreviewKeyEvent LEFT/RIGHT with FocusKit.onDpadAdjustLeftRight (TvSwitchRow/TvSliderRow/TvSelectRow) – 2025-10-08.
  - FishFormTextField behält die dialogbasierte Eingabe für TV und nutzt FocusKit.tvClickable (siehe `ui/layout/FishForm.kt`).

  Guidance
  - Keep rows clickable via FocusKit.tvClickable; add FocusKit.onDpadAdjustLeftRight { … } to adjust values.
  - This standardizes behavior and reduces key handling drift.

  Nächste Schritte (FocusKit x FishForms)
  1. **DPAD-Audit abschließen** – `rg onPreviewKeyEvent`/`focusScaleOnTv` repo-weit regelmäßig laufen lassen und Findings in `tools/audit_tv_focus_report.txt` protokollieren.
  2. **FishForm-Komponenten entwerfen** – `FishFormSwitch/Select/Slider/TextField` als wiederverwendbare Compose-Bausteine aufsetzen; FocusKit + FishTheme Tokens verwenden und APIs so schneiden, dass Screens/Module (Setup, Settings, Profile, kommende Flows) dieselben Primitives konsumieren können.
  3. **Screens migrieren** – `CreateProfileSheet`, `PlaylistSetupScreen`, Settings-Abschnitte usw. auf die neuen FishForm-Komponenten umstellen und UI-Tests/TV-Manualläufe durchführen.
  4. **Alte Form-Rows entfernen** – `ui/forms/*` deprecaten, Referenzen bereinigen, BUILD-Gates (`TV_FORMS_V1`) vereinheitlichen.
  5. **Dokumentation & Audit-Tooling** – `docs/tv_forms.md`, `docs/fish_layout.md`, `tools/audit_tv_focus.sh` aktualisieren; FishForms in den Fokus-Audit aufnehmen und CI-Checks erweitern.

- Already aligned (no change required)
  - Profile gate and keypad are already built on FocusKit package‑level wrappers:
    - app/src/main/java/com/chris/m3usuite/ui/auth/ProfileGate.kt: uses com.chris.m3usuite.ui.focus.* (focusGroup, tvClickable, tvFocusFrame, focusBringIntoViewOnFocus, TvRow). Optional future polish: switch to FocusKit.run and FocusKit.TvRowLight for perfect symmetry.

- Notes
  - Internal engines and primitives remain providers:
  - ui/tv/TvFocusRow.kt, ui/focus/FocusRowEngine.kt, ui/skin/TvModifiers.kt stay; FocusKit fronts them in screens.
  - ✅ StartScreen Telegram-Suche nutzt bereits FocusKit.TvRowLight (stateKey `start_tg_search`).

Prio 1 — Globale Zentralisierung Fokus/TV‑Darstellung (OFF)
- Ziel: Einheitliche, zentral gesteuerte Fokusdarstellung und -navigation (DPAD) in allen UIs; keine verstreuten Implementierungen mehr.
- Leitfaden: `tools/Zentralisierung.txt` (kanonisch). Fokus-/TV‑Änderungen erfolgen ausschließlich dort bzw. in den dort benannten Modulen.
- Aufgaben:
  - Primitives konsolidieren: `FocusKit` als einzige öffentliche Oberfläche (Primitives: `tvClickable`, `tvFocusFrame`, `tvFocusableItem`, `focusGroup`, `focusBringIntoViewOnFocus`).
  - Screens migrieren, lokale Workarounds entfernen (manuelles DPAD, ad‑hoc bringIntoView).
  - Globale Stilquelle (Halo/Scale/Farben), neutrale Clickables ohne Doppel‑Effekte.
- CI‑Audit (`tools/audit_tv_focus.sh`) schärfen und durchsetzen.
  - Neu (2025‑10‑06): `FocusKit.TvRowLight/Media/Paged` als Frontdoor – Screens nutzen nur noch `FocusKit`, nicht direkt `TvFocusRow`/`FocusRowEngine`.
  - Beispiele/Docs im Leitfaden; Code‑Lagen vereinheitlichen (keine Duplikate in Unterordnern).
  - Modul‑Migration: auth/ProfileGate → FocusKit (DONE); weitere Module folgen.
- Maintenance 2025-10-03: Detailseiten neu auf stabilem Scaffold (HomeChromeScaffold → Box → Card + LazyColumn). VOD: OBX-first + Xtream-On-Demand, Plot/Facts als Karten, ein Scroll-Container, sichere Play/Resume-Actions. Bruchfeste Klammerstruktur; Build-Fehler behoben. StartScreen: Klammerfehler korrigiert (Compose-Kontextfehler beseitigt).
 - Maintenance 2025-10-03: DetailMask eingeführt (`ui.detail.DetailPage`/`DetailBackdrop`/`DetailSections`) – einheitlicher Aufbau für alle Details (Hero/Gradients/AccentCard/Header/Sections). VOD nutzt bereits die Maske; Series/Live Migration als nächstes.
- Feature 2025-10-03: Globaler Zuweisen‑Modus auf Start. Mehrfachauswahl über Reihen/Suche, Profil‑Picker für Bulk‑Freigabe (ObjectBox‑Batch), klare Selektion und permisssions‑Gating. VOD‑Details zeigen wieder Freigabe/Entfernen in der `MediaActionBar`.
- Maintenance 2025-10-04 (Start): Vertikale Gewichtung der Start-Abschnitte (Serien/Filme/Live) implementiert; Landscape nutzt 40/40/20, Portrait gleichmäßig. Keine Überlappung mehr ganz oben; Zeilenhöhen werden in Landscape automatisch an die Kartenhöhe angepasst.
 - UX 2025-10-04 (Start Assign): Start-Button zum Zuordnen entfernt. Stattdessen hat jedes Tile einen globalen, klickbaren Badge (+/✓) für die Markierung; markierte Tiles zeigen einen sichtbaren Rahmen. Ein schwebender Plus-Button erscheint bei mindestens einer Markierung und öffnet den Profil-Picker zur Zuordnung.
- Maintenance 2025-10-04 (TV Forms): Profil-Erstellen-Dialog auf TV-Form-Kit umgestellt (FishFormTextField/FishFormSwitch/FishFormButtonRow). DPAD/OK funktionieren stabil; Fokus-Falle durch Textfeld-IME entfällt.
 - Maintenance 2025-10-04 (Whitelist Refresh): Nach Bulk-Zuordnung in Start werden gefilterte Listen sofort neu geladen (und wie bisher bei Profilwechsel). Sichtbarkeit für Kids-Profile ist damit konsistent.
  
Completed (moved to Changelog)
- Detail screens: full-screen hero background (TV/portrait/landscape) and removal of FishBackground overlay.
- Details centralization: unify VOD/Series/Live via `ui/detail` (DetailHeader+MetaChips+HeaderExtras+DetailFacts), legacy paths removed; on-demand metadata load only; DPAD‑LEFT→Chrome disabled in details; BACK re-enabled.
- Maintenance 2025‑09‑29 (TV Low-Spec): Laufzeitprofil für TV hinzugefügt (reduzierte Fokus‑Effekte, kleinere Paging‑Fenster, OkHttp Drosselung, Coil ohne Crossfade). Während der Wiedergabe werden Xtream‑Seeding‑Worker pausiert und danach wieder aktiviert (wenn zuvor aktiv).
- Maintenance 2025‑09‑30: LiveDetailScreen Buildfix – Klammerfehler behoben, EPG/Kid-Dialogs in den Screenkörper verlagert. Kein Roadmap‑Impact.
 - Maintenance 2025‑09‑30: Start/Library Playback-Migration – Verbleibende Direktaufrufe von PlayerChooser auf PlaybackLauncher (Flag `PLAYBACK_LAUNCHER_V1`) umgestellt; VOD „Ähnliche Inhalte“ und Live „Mehr aus Kategorie“ öffnen nun Details des gewählten Elements (Lambdas an Screens ergänzt und in MainActivity verdrahtet).
- Maintenance 2025‑10‑01: Telegram – Settings zeigen nun Chat‑Namen für ausgewählte Film/Serien‑Sync‑Quellen; Backfill‑Worker `TelegramSyncWorker` indiziert Nachrichten aus selektierten Chats (ObxTelegramMessage).
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
  - FishForms (`ui/layout/FishForm.kt`) stellt DPAD‑optimierte Rows (Switch/Select/Slider/TextField/Button) auf Basis FocusKit + FishTheme Tokens bereit; Legacy `ui/forms` wurde entfernt.
  - Validierungs‑Hints, konsistenter Fokus (Scale + Halo), Dialog‑Eingabe für Textfelder auf TV.
  - Migrationen: `CreateProfileSheet`, `PlaylistSetupScreen` abgeschlossen (`BuildConfig.TV_FORMS_V1` gate bleibt aktiv); Settings-Module folgen.

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

- TDLib Phase‑2 (laufend):
  - ✅ Telegram Serien Aggregation: Indexer baut `ObxSeries` + `ObxEpisode` aus `ObxTelegramMessage` (SxxEyy), ProviderKey `telegram`; Library zeigt Row „Telegram Serien“. Playback via tg:// in Detail‑Screen.
    - ✅ Serienposter = Chat‑Foto (größte Größe), wird via TDLib geladen und als erstes Bild in `imagesJson` abgelegt.
    - ✅ Paging (fetchAll): robuste Mehrseiten‑Historie (fromId = oldestId‑1), verhindert „nur letzte Seite“ Fälle.
    - ✅ Heuristik erweitert: „Staffel X Folge Y“ zusätzlich zu SxxEyy/xxxyyy Formen.
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
   - ✅ Legacy `ui.cards` + `BuildConfig.CARDS_V1` removed (2025-10-07); FishTile/FishRow cover all usages.
 - PRIO‑1: PlaybackLauncher (Unification)
   - `playback/` mit `PlayRequest`/`PlayerResult` und `rememberPlaybackLauncher`; Flag `BuildConfig.PLAYBACK_LAUNCHER_V1` (default ON).
   - V1 Migration: VOD/Serie/Live Detail nutzen PlaybackLauncher (intern/extern via PlayerChooser); ehemalige ResumeCarousel-Ansichten durch FishRow/FishResumeTile ersetzt.
 - Maintenance 2025-10-06: Audit an `tools/Zentralisierung.txt` angepasst – prüft jetzt verbotene TvLazyRow‑Nutzung, per‑Item Bring‑Into‑View (nur zentral erlaubt), doppelte Fokus‑Indikatoren (Heuristik) sowie SSOT‑Verstöße (eigene Fokus‑Primitives außerhalb der Zentral‑Module). Diff‑Ordner (`a/**`,`b/**`) und `.git` werden ignoriert; `FocusKit`/`PackageScope` sind als zentrale Fassaden zugelassen.
