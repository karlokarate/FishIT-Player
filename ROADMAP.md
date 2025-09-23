#
# FishIT Player — Roadmap (Q4 2025)

Hinweis
- Der vollständige Verlauf steht in `CHANGELOG.md`. Diese Roadmap listet nur kurzfristige und mittelfristige, umsetzbare Punkte.

---

## Kurzfristig (2–4 Wochen)

- Fonts (UI): Korrupte/fehlende TTFs ersetzen (AdventPro, Cinzel, Fredoka, Inter, Merriweather, MountainsOfChristmas, Orbitron, Oswald, Playfair Display, Teko, Baloo2). Ziel: stabile dekorative Familien ohne Fallbacks.
- Media3 Pufferung: `DefaultLoadControl` pro Typ prüfen und moderate Puffer für VOD/Live definieren (kein aggressives Prebuffering; TV‑Stabilität bevorzugen).
- Coil3 Netzwerk: Explizite OkHttp‑Factory prüfen/integrieren, falls stabil verfügbar (sonst bei per‑Request NetworkHeaders bleiben). `respectCacheHeaders(true)` evaluieren.
- EPG Konsistenz: Room vollständig aus Flows entfernen (UI/Prefs‑Reste wie `roomEnabled` aufräumen); EPG Now/Next ausschließlich ObjectBox + XMLTV Fallback.
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
