# Telegram Structured Bundles Contract

**Version:** 1.0  
**Datum:** 2025-12-17  
**Status:** Binding – authoritative spec for structured Telegram message handling  
**Scope:** Erkennung, Gruppierung und Verarbeitung von strukturierten Telegram-Nachrichten-Clustern

> **⚠️ Dieser Contract ist verbindlich.** Alle Implementierungen in `pipeline/telegram` müssen diesen Regeln folgen. Verletzungen sind Bugs und müssen sofort behoben werden.

---

## 1. Begriffsdefinitionen

### 1.1 Structured Bundle

Ein **Structured Bundle** ist eine Gruppe von 2-3 Telegram-Nachrichten, die:
- Denselben `date` (Unix-Timestamp in Sekunden) haben
- Zusammen ein einzelnes Media-Item beschreiben
- Strukturierte Metadaten in TEXT-Nachrichten enthalten

### 1.2 Bundle-Typen

| Typ | Zusammensetzung | Beschreibung |
|-----|-----------------|--------------|
| `FULL_3ER` | PHOTO + TEXT + VIDEO | Vollständiges Bundle mit Poster, Metadaten und Video |
| `COMPACT_2ER` | TEXT + VIDEO oder PHOTO + VIDEO | Kompaktes Bundle ohne alle drei Komponenten |
| `SINGLE` | Einzelne Nachricht | Kein Bundle, normale Verarbeitung |

### 1.3 Structured Metadata Fields

Felder, die in TEXT-Nachrichten strukturierter Chats vorkommen können:

| Feld | Typ | Beispiel | Verwendung |
|------|-----|----------|------------|
| `tmdbUrl` | String | `"https://www.themoviedb.org/movie/12345-name"` | TMDB-ID-Extraktion |
| `tmdbRating` | Double | `7.5` | Rating-Anzeige |
| `year` | Int | `2020` | Release-Jahr |
| `originalTitle` | String | `"The Movie"` | Original-Titel |
| `genres` | List<String> | `["Action", "Drama"]` | Genre-Tags |
| `fsk` | Int | `12` | Altersfreigabe (Kids-Filter) |
| `director` | String | `"John Doe"` | Regie |
| `lengthMinutes` | Int | `120` | Laufzeit |
| `productionCountry` | String | `"US"` | Produktionsland |

---

## 2. Contract Rules

### 2.1 Bundle-Erkennung (MANDATORY)

**R1: Timestamp-Gruppierung**
> Nachrichten mit identischem `date` (Unix-Timestamp) MÜSSEN als potentielles Bundle gruppiert werden.

**R2: Bundle-Klassifikation**
> Ein Bundle MUSS nach Content-Typen klassifiziert werden:
> - `FULL_3ER`: Hat VIDEO + TEXT + PHOTO
> - `COMPACT_2ER`: Hat VIDEO + (TEXT oder PHOTO)
> - `SINGLE`: Nur ein Nachrichten-Typ

**R3: Reihenfolge-Invariante**
> Innerhalb eines Bundles gilt die Message-ID-Reihenfolge:
> - PHOTO hat typischerweise die niedrigste `messageId`
> - TEXT hat die mittlere `messageId`
> - VIDEO hat die höchste `messageId`
> - Diese Reihenfolge ist zur Identifikation nutzbar, aber NICHT für Sortierung maßgeblich.

### 2.2 Metadaten-Extraktion (MANDATORY)

**R4: Pass-Through-Prinzip**
> Strukturierte Felder MÜSSEN RAW extrahiert und UNVERÄNDERT weitergereicht werden.
> - Keine Titel-Bereinigung
> - Keine Normalisierung
> - Keine Validierung (außer Type-Parsing)

**R5: TMDB-ID-Extraktion**
> Die TMDB-ID MUSS aus der `tmdbUrl` extrahiert werden via Regex:
> ```
> /movie/(\d+)
> ```
> Beispiel: `"https://www.themoviedb.org/movie/12345-name"` → `"12345"`

**R6: FSK-Verwendung**
> Das `fsk`-Feld MUSS als `ageRating` in `RawMediaMetadata` weitergereicht werden.
> Dies ermöglicht den Kids-Filter ohne TMDB-Lookup.

### 2.3 Mapping-Regeln (MANDATORY)

**R7: Bundle → Single TelegramMediaItem**
> Ein Structured Bundle MUSS auf genau EIN `TelegramMediaItem` gemappt werden.
> Die VIDEO-Nachricht liefert die primären Playback-Daten.

**R8: Tie-Breaker (bei mehreren VIDEOs)**
> Falls ein Bundle mehrere VIDEO-Nachrichten enthält:
> 1. Wähle das Video mit der größten `sizeBytes`
> 2. Bei Gleichstand: längste `duration`
> 3. Bei Gleichstand: niedrigste `messageId` (deterministic)

**R9: Poster-Auswahl**
> Falls ein Bundle eine PHOTO-Nachricht enthält:
> - Wähle die Größe mit `height > 1000` (Poster-Qualität)
> - Falls keine solche existiert: größte verfügbare Höhe

### 2.4 Contract Compliance

**R10: MEDIA_NORMALIZATION_CONTRACT**
> Alle Regeln aus `docs/v2/MEDIA_NORMALIZATION_CONTRACT.md` gelten weiterhin:
> - Pipeline darf NICHT normalisieren
> - Pipeline darf KEINE TMDB-Lookups durchführen
> - `globalId` MUSS leer bleiben

**R11: Layer-Boundaries**
> Structured Bundles werden vollständig in `pipeline/telegram` verarbeitet:
> - Transport liefert `TgMessage` (keine Bundle-Logik)
> - Pipeline gruppiert, extrahiert, mappt
> - Data erhält `RawMediaMetadata` (keine Bundle-Interna)

---

## 3. Datenmodell-Erweiterungen

### 3.1 TelegramMediaItem (REQUIRED)

Die folgenden Felder MÜSSEN zu `TelegramMediaItem` hinzugefügt werden:

```kotlin
// Structured Bundle Fields
val structuredTmdbId: String? = null
val structuredRating: Double? = null
val structuredYear: Int? = null
val structuredFsk: Int? = null
val structuredGenres: List<String>? = null
val structuredDirector: String? = null
val structuredOriginalTitle: String? = null
val structuredLengthMinutes: Int? = null
val structuredProductionCountry: String? = null
val bundleType: TelegramBundleType = TelegramBundleType.SINGLE
val textMessageId: Long? = null
val photoMessageId: Long? = null
```

### 3.2 RawMediaMetadata (REQUIRED)

Die folgenden Felder MÜSSEN zu `RawMediaMetadata` hinzugefügt werden:

```kotlin
val ageRating: Int? = null  // FSK/MPAA für Kids-Filter
val rating: Double? = null  // TMDB-Rating etc.
```

### 3.3 toRawMediaMetadata() Mapping (REQUIRED)

```kotlin
fun TelegramMediaItem.toRawMediaMetadata(): RawMediaMetadata {
    // Structured Bundle fields take precedence
    val effectiveYear = structuredYear ?: year
    val effectiveDuration = structuredLengthMinutes ?: durationSecs?.let { it / 60 }
    
    return RawMediaMetadata(
        originalTitle = structuredOriginalTitle ?: extractRawTitle(),
        year = effectiveYear,
        durationMinutes = effectiveDuration,
        ageRating = structuredFsk,
        rating = structuredRating,
        externalIds = ExternalIds(
            tmdbId = structuredTmdbId,  // Pass-through from TEXT
        ),
        // ... other fields
    )
}
```

---

## 4. Komponenten-Spezifikation

### 4.1 TelegramMessageBundler

**Package:** `com.fishit.player.pipeline.telegram.grouper`

**Responsibility:** Gruppiert TgMessage-Listen nach Timestamp

**Contract:**
- MUSS alle Nachrichten mit gleichem `date` gruppieren
- MUSS `TelegramMessageBundle` mit korrektem `bundleType` zurückgeben
- MUSS Content-Typen korrekt identifizieren (VIDEO/TEXT/PHOTO)
- DARF KEINE Normalisierung durchführen

### 4.2 TelegramStructuredMetadataExtractor

**Package:** `com.fishit.player.pipeline.telegram.grouper`

**Responsibility:** Extrahiert strukturierte Felder aus TEXT-Nachrichten

**Contract:**
- MUSS alle definierten Structured Fields erkennen (Section 1.3)
- MUSS TMDB-URL zu ID parsen (Rule R5)
- MUSS fehlende Felder als `null` zurückgeben
- DARF KEINE Werte erfinden oder ableiten

### 4.3 TelegramBundleToMediaItemMapper

**Package:** `com.fishit.player.pipeline.telegram.mapper`

**Responsibility:** Konvertiert Bundles zu TelegramMediaItem

**Contract:**
- MUSS Tie-Breaker-Regeln anwenden (Rule R8)
- MUSS Poster-Auswahl-Regeln anwenden (Rule R9)
- MUSS alle Bundle-Felder korrekt setzen
- MUSS `bundleType` korrekt setzen

---

## 5. Verhaltensregeln

### 5.1 Fallback für Unstrukturierte Chats

Wenn ein Chat keine strukturierten Bundles enthält:
- Nachrichten werden als `SINGLE` behandelt
- Bestehende Parsing-Logik wird angewendet
- Keine Regression für existierende Funktionalität

### 5.2 Fehlerbehandlung

| Fehler | Verhalten |
|--------|-----------|
| TEXT ohne strukturierte Felder | Behandle als normalen TEXT |
| Bundle ohne VIDEO | Emittiere KEIN Item (nur VIDEO ist playable) |
| TMDB-URL unparsebar | `structuredTmdbId = null` |
| Ungültige Feldwerte | Feld auf `null` setzen, kein Fehler werfen |

### 5.3 Logging (MANDATORY)

Folgende Events MÜSSEN geloggt werden:

| Event | Log-Level | Inhalt |
|-------|-----------|--------|
| Bundle erkannt | DEBUG | `chatId`, `timestamp`, `bundleType`, `messageIds` |
| Structured Metadata extrahiert | DEBUG | `chatId`, `tmdbId`, `year`, `fsk` |
| TMDB-URL parse failed | WARN | `chatId`, `messageId`, `tmdbUrl` |
| Bundle-Statistik pro Chat | INFO | `chatId`, `bundleCount`, `singleCount` |

---

## 6. Test-Anforderungen

### 6.1 Required Unit Tests

| Test-ID | Beschreibung | Fixture |
|---------|--------------|---------|
| TB-001 | Gruppierung nach Timestamp | 3 Nachrichten, gleicher Timestamp |
| TB-002 | Keine Gruppierung bei unterschiedlichen Timestamps | 3 Nachrichten, verschiedene Timestamps |
| TB-003 | FULL_3ER Klassifikation | VIDEO + TEXT + PHOTO |
| TB-004 | COMPACT_2ER Klassifikation | TEXT + VIDEO |
| SM-001 | TMDB-URL Parsing | Standard URL |
| SM-002 | TMDB-URL mit Slug | URL mit `-name` Suffix |
| SM-003 | FSK Extraktion | `"fsk": 12` |
| SM-004 | Fehlende Felder | TEXT ohne tmdbUrl |
| MM-001 | Tie-Breaker: größtes Video | 2 VIDEOs, verschiedene Größen |
| MM-002 | Tie-Breaker: längste Dauer | 2 VIDEOs, gleiche Größe |
| MM-003 | Poster-Auswahl: beste Qualität | PHOTO mit 3 Größen |

### 6.2 Required Integration Tests

| Test-ID | Chat | Erwartung |
|---------|------|-----------|
| INT-001 | Mel Brooks 🥳 | ≥8 FULL_3ER Bundles erkannt |
| INT-002 | Filme kompakt | ≥8 COMPACT_2ER Bundles erkannt |
| INT-003 | Unstrukturierter Chat | 0 Bundles, alle SINGLE |

---

## 7. Compliance-Checkliste

Vor jedem Merge MUSS geprüft werden:

- [ ] Keine Normalisierung in Pipeline (MEDIA_NORMALIZATION_CONTRACT R10)
- [ ] Keine TMDB-Lookups in Pipeline
- [ ] `globalId` bleibt leer
- [ ] Structured Fields werden RAW extrahiert (R4)
- [ ] TMDB-ID via Regex extrahiert (R5)
- [ ] Tie-Breaker-Regeln implementiert (R8)
- [ ] Logging gemäß Section 5.3
- [ ] Alle Required Unit Tests passieren
- [ ] Alle Required Integration Tests passieren

---

## 8. Versionierung

| Version | Datum | Änderungen |
|---------|-------|------------|
| 1.0 | 2025-12-17 | Initial Release |

---

## 9. Referenzen

- [TELEGRAM_STRUCTURED_BUNDLES_MASTERPLAN.md](docs/v2/TELEGRAM_STRUCTURED_BUNDLES_MASTERPLAN.md)
- [MEDIA_NORMALIZATION_CONTRACT.md](docs/v2/MEDIA_NORMALIZATION_CONTRACT.md)
- [TELEGRAM_PARSER_CONTRACT.md](contracts/TELEGRAM_PARSER_CONTRACT.md)
- [AGENTS.md](AGENTS.md) – Sections 4, 11, 15
