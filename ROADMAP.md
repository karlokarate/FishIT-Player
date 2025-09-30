#
# FishIT Player — Roadmap (Q4 2025)

Hinweis
- Der vollständige Verlauf steht in `CHANGELOG.md`. Diese Roadmap listet nur kurzfristige und mittelfristige, umsetzbare Punkte.
- Maintenance 2025‑09‑27: Manifest‑Icon auf `@mipmap/ic_launcher` (+ `roundIcon`) vereinheitlicht; kein Roadmap‑Impact.
- Maintenance 2025‑09‑28: Build‑Blocking Lücken geschlossen (Nav‑Extension, TV‑Focus‑Compat, TvRowScroll, safePainter, Adults‑Filter, XtreamImportCoordinator). Kein neues Feature; Roadmap unverändert.
- Maintenance 2025‑09‑28 (TV Focus): Start-Home Serien/VOD/Live Reihen repariert – initialer Fokus greift wieder, Tiles skalieren sofort und DPAD‑LEFT expandiert HomeChrome nur am linken Rand.
- Maintenance 2025‑09‑29 (TV Low-Spec): Laufzeitprofil für TV hinzugefügt (reduzierte Fokus‑Effekte, kleinere Paging‑Fenster, OkHttp Drosselung, Coil ohne Crossfade). Während der Wiedergabe werden Xtream‑Seeding‑Worker pausiert und danach wieder aktiviert (wenn zuvor aktiv).

---

## Kurzfristig (2–4 Wochen)

PRIO‑1: TV Fokus/DPAD Vereinheitlichung
- Alles Horizontale → `TvFocusRow` (inkl. Chips/Carousels).
- Alles Interaktive → `tvClickable`/`tvFocusableItem` (No‑Op auf Phone).
- Zentrale Registry für Scroll+Fokus je Route/Row (`ScrollStateRegistry`).
- Chrome: einheitliche Auto‑Collapse/Expand‑Trigger im `HomeChromeScaffold`.
- Kein `onPreviewKeyEvent` (außer echte Sonderfälle).
- Audit‑Skript erzwingt die Regeln (`tools/audit_tv_focus.sh`).

Status: umgesetzt und in CI verankert (Audit Schritt). Buttons/Actions erhalten auf TV eine visuelle Fokus‑Hervorhebung (`TvButtons` oder `focusScaleOnTv`).

- TV Fokus QA: Nach Compose-Updates automatisierte Regression (Screenshot/UI-Test) für TvFocusRow + Tiles aufsetzen, damit Scale/Halo-Verhalten gesichert bleibt.
- Fonts (UI): Korrupte/fehlende TTFs ersetzen (AdventPro, Cinzel, Fredoka, Inter, Merriweather, MountainsOfChristmas, Orbitron, Oswald, Playfair Display, Teko, Baloo2). Ziel: stabile dekorative Familien ohne Fallbacks.
- Media3 Pufferung: `DefaultLoadControl` pro Typ prüfen und moderate Puffer für VOD/Live definieren (kein aggressives Prebuffering; TV‑Stabilität bevorzugen).
- Coil3 Netzwerk: Explizite OkHttp‑Factory prüfen/integrieren, falls stabil verfügbar (sonst bei per‑Request NetworkHeaders bleiben). `respectCacheHeaders(true)` evaluieren.
- EPG Konsistenz: Room vollständig aus Flows entfernen (UI/Prefs‑Reste wie `roomEnabled` aufräumen); EPG Now/Next ausschließlich ObjectBox + XMLTV Fallback.
- Seeding‑Whitelist (Regions): Settings‑Multi‑Select fertigstellen/validieren (Default DE/US/UK/VOD); Quick‑Seed nur für erlaubte Prefixe ausführen.
- CI/Build: Job für `assembleRelease` + Split‑APKs (arm64‑v8a, armeabi‑v7a) erzeugen; Artefakte im CI hinterlegen. Keystore verbleibt lokal (Unsigned‑Artefakte).
- Git WSL Push: Repo‑Docs um `core.sshCommand`/SSH‑Config (Deploy‑Key) ergänzen, damit Push aus WSL/AS stabil funktioniert.

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
