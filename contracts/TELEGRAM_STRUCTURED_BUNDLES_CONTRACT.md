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
- Die Bundle-Kohäsions-Prüfung bestehen (siehe R1b)
- Zusammen ein einzelnes Media-Item beschreiben
- Strukturierte Metadaten in TEXT-Nachrichten enthalten

### 1.2 Bundle-Typen

| Typ | Zusammensetzung | Beschreibung |
|-----|-----------------|--------------|
| `FULL_3ER` | PHOTO + TEXT + VIDEO | Vollständiges Bundle mit Poster, Metadaten und Video |
| `COMPACT_2ER` | TEXT + VIDEO oder PHOTO + VIDEO | Kompaktes Bundle ohne alle drei Komponenten |
| `SINGLE` | Einzelne Nachricht | Kein Bundle, normale Verarbeitung |

### 1.3 BundleKey

Ein **BundleKey** ist der eindeutige Identifikator eines Bundles:
- **Zusammensetzung:** `(chatId, timestamp, discriminator)`
- **discriminator:** Album-ID falls vorhanden (von Telegram/TDLib), sonst deterministischer Fallback basierend auf messageId-Proximity
- **Zweck:** Ermöglicht deterministische Bundle-Erkennung über Timestamp-Matching hinaus

### 1.4 Structured Metadata Fields

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

### 1.5 Work, PlayableAsset und WorkKey

| Begriff | Definition | Verwendung |
|---------|------------|------------|
| **Work** | Kanonikalisierbares Entity (Movie/Episode), das downstream aufgelöst wird | Ein logisches Werk, das mehrere Playback-Varianten haben kann |
| **PlayableAsset** | Konkrete Video-Datei/Stream-Referenz (Telegram remoteId/fileId etc.) | Eine abspielbare Datei, die zu einem Work gehört |
| **WorkKey** | `tmdb:<type>:<id>` wenn strukturierte TMDB-Daten vorhanden (type aus URL), sonst pipeline-lokaler Key (NICHT globalId; muss als pipeline-lokal und ephemeral markiert sein) | Temporärer Schlüssel zur Gruppierung von Assets vor Normalisierung |

---

## 2. Contract Rules

### 2.1 Bundle-Erkennung (MANDATORY)

**R1: Bundle Candidate Grouping**
> Nachrichten mit identischem `date` (Unix-Timestamp) MÜSSEN als **BundleCandidate** gruppiert werden.

**R1b: Bundle Cohesion Gate (MANDATORY)**
> Ein BundleCandidate DARF nur dann als Structured Bundle behandelt werden, wenn:
> 1. Er mindestens eine VIDEO-Nachricht enthält, UND
> 2. Er eine deterministische Kohäsions-Regel erfüllt:
>    - **Primär:** Falls Telegram/TDLib eine Album/Group-ID bereitstellt, MUSS diese als Discriminator verwendet werden; Timestamp wird sekundär.
>    - **Fallback:** Falls keine Album/Group-ID vorhanden ist, MUSS der Discriminator aus messageId-Proximity und Type-Pattern berechnet werden:
>      - Der Candidate ist kohäsiv, wenn die kleinste und größte messageId im Candidate innerhalb eines festen maximalen Spans liegen (Konstante: <= 3 * 2^20 = 3.145.728, basierend auf beobachtetem Pattern), ODER
>      - wenn er das bekannte Step-Pattern 2^20 innerhalb einer Toleranz erfüllt (dokumentiert als "confidence heuristic", aber dennoch deterministisch).
> 3. Falls die Kohäsions-Prüfung fehlschlägt, MUSS der Candidate in SINGLE-Verarbeitungseinheiten aufgeteilt werden (kein Bundle).

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

**R4: Pass-Through mit Schema Guards**
> Strukturierte Felder MÜSSEN RAW extrahiert und weitergereicht werden, außer bei Type-Parsing und Schema Guards.
> 
> **Schema Guards MÜSSEN** offensichtlich ungültige Werte auf null setzen:
> - `year` gültiger Bereich: 1800..2100, sonst null
> - `tmdbRating` gültiger Bereich: 0.0..10.0, sonst null
> - `fsk` gültiger Bereich: 0..21, sonst null
> - `lengthMinutes` gültiger Bereich: 1..600, sonst null
> 
> Keine abgeleiteten Werte, keine Bereinigung, keine Normalisierung.

**R5: TMDB-ID + Type Extraktion**
> Die Pipeline MUSS TMDB-ID und TMDB-Medientyp aus `tmdbUrl` mit diesen verbindlichen Patterns parsen:
> - `/movie/(\d+)` ⇒ `tmdbType = MOVIE`, `tmdbId = digits`
> - `/tv/(\d+)` ⇒ `tmdbType = TV`, `tmdbId = digits`
> 
> Jedes andere TMDB-URL-Format MUSS zu `tmdbId=null` führen und MUSS `TMDB-URL parse failed (WARN)` loggen.
> 
> **Strukturiertes Feld:**
> - `structuredTmdbType: TelegramTmdbType? = null` mit enum `MOVIE`, `TV`

**R6: FSK-Verwendung**
> Das `fsk`-Feld MUSS als `ageRating` in `RawMediaMetadata` weitergereicht werden.
> Dies ermöglicht den Kids-Filter ohne TMDB-Lookup.

### 2.3 Mapping-Regeln (MANDATORY)

**R7: Bundle → Catalog Items**
> Ein Structured Bundle MUSS produzieren:
> - Genau einen Raw-Metadata-Record (pro BundleKey), UND
> - Einen Playable-Asset-Record pro VIDEO innerhalb des Bundles

**R8: Multi-VIDEO Mapping (MANDATORY, lossless)**
> Falls ein Bundle mehrere VIDEO-Nachrichten enthält, MUSS die Pipeline mehrere playable Assets erstellen, alle verknüpft mit demselben WorkKey, der aus denselben strukturierten Metadaten abgeleitet wird (TMDB-ID falls vorhanden).
> 
> Die Pipeline DARF KEINE Video-Varianten verwerfen.

**R8b: Deterministische Primary Asset Selection (MANDATORY)**
> Ein "primäres" Asset MUSS deterministisch designiert werden für UI-Defaults:
> 1. Größte `sizeBytes`
> 2. dann längste `duration`
> 3. dann niedrigste `messageId`
> 
> Nicht-primäre Assets MÜSSEN als Alternativen beibehalten werden.

**R9: Poster-Auswahl**
> Falls PHOTO existiert, MUSS das ausgewählte Poster die Größe mit maximaler Pixel-Fläche (`width * height`) sein.
> 
> Ties MÜSSEN deterministisch gebrochen werden durch:
> 1. Größere `height`
> 2. Größere `width`
> 3. Niedrigste `messageId`

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
> 
> **Explizite Exportbeschränkung:**
> - `TelegramMediaItem` und alle bundle-internen DTOs MÜSSEN pipeline-intern bleiben und DÜRFEN NICHT über Modul-Grenzen hinweg exportiert werden.
> - Nur `RawMediaMetadata` und die definierte "PlayableAsset"-Exportstruktur dürfen die Pipeline verlassen.

### 2.5 Canonical Linking Rule (Binding)

**Kanonisches Linking:**
> - Falls `externalIds.tmdbId` in `RawMediaMetadata` vorhanden ist, MUSS downstream `canonicalId` als `tmdb:<type>:<id>` berechnet werden.
> - Alle playable Assets (einschließlich Alternativen) MÜSSEN downstream mit derselben `canonicalId` verknüpfbar sein.
> - Die Pipeline DARF NICHT `canonicalId`/`globalId` berechnen; sie reicht nur TMDB-ID/Type und strukturierte Raw-Felder durch.

**Single Source of Truth (SSOT):**
> - Ein Werk hat eine kanonische ID (TMDB bevorzugt) und ein einzelnes SSOT für Metadaten (TMDB).
> - Alle abspielbaren Dateien über Pipelines hinweg werden an dieselbe kanonische ID angehängt.
> - Pipeline-Verantwortung: Strukturierte IDs und Typen durchreichen, NICHT kanonische Identity berechnen.

---

## 3. Datenmodell-Erweiterungen

### 3.1 TelegramMediaItem (REQUIRED)

Die folgenden Felder MÜSSEN zu `TelegramMediaItem` hinzugefügt werden:

```kotlin
// Structured Bundle Fields
val structuredTmdbId: String? = null
val structuredTmdbType: TelegramTmdbType? = null  // NEU: MOVIE oder TV
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

**Enum für TMDB-Typ:**
```kotlin
enum class TelegramTmdbType {
    MOVIE,
    TV
}
```

### 3.2 RawMediaMetadata (REQUIRED)

Die folgenden Felder MÜSSEN zu `RawMediaMetadata` hinzugefügt werden:

```kotlin
val ageRating: Int? = null  // FSK/MPAA für Kids-Filter
val rating: Double? = null  // TMDB-Rating etc.
```

### 3.3 toRawMediaMetadata() Mapping (REQUIRED)

**Wichtig:** Multi-Video-Bundles erfordern Mehrfach-Asset-Emission (siehe R7, R8).

```kotlin
fun TelegramMediaItem.toRawMediaMetadata(): RawMediaMetadata {
    // Structured Bundle fields take precedence
    val effectiveYear = structuredYear ?: year
    val effectiveDuration = structuredLengthMinutes ?: durationSecs?.let { it / 60 }
    
    // Schema Guards (R4)
    val validatedYear = effectiveYear?.takeIf { it in 1800..2100 }
    val validatedRating = structuredRating?.takeIf { it in 0.0..10.0 }
    val validatedFsk = structuredFsk?.takeIf { it in 0..21 }
    val validatedLength = effectiveDuration?.takeIf { it in 1..600 }
    
    return RawMediaMetadata(
        originalTitle = structuredOriginalTitle ?: extractRawTitle(),
        year = validatedYear,
        durationMinutes = validatedLength,
        ageRating = validatedFsk,
        rating = validatedRating,
        externalIds = ExternalIds(
            tmdbId = structuredTmdbId,  // Pass-through from TEXT
        ),
        // ... other fields
    )
}
```

**Multi-Asset-Hinweis:**
> Bei Bundles mit mehreren VIDEOs muss die Mapping-Logik mehrere Catalog Items emittieren, die denselben `RawMediaMetadata`-Kern teilen, aber unterschiedliche Playback-Referenzen (remoteId, fileId) haben.

---

## 4. Komponenten-Spezifikation

### 4.1 TelegramMessageBundler

**Package:** `com.fishit.player.pipeline.telegram.grouper`

**Responsibility:** Gruppiert TgMessage-Listen nach Timestamp und prüft Kohäsion

**Contract:**
- MUSS alle Nachrichten mit gleichem `date` als BundleCandidate gruppieren
- MUSS Bundle Cohesion Gate anwenden (R1b)
- MUSS `TelegramMessageBundle` mit korrektem `bundleType` zurückgeben
- MUSS Content-Typen korrekt identifizieren (VIDEO/TEXT/PHOTO)
- MUSS kohäsions-fehlgeschlagene Candidates in SINGLE-Units splitten
- DARF KEINE Normalisierung durchführen

### 4.2 TelegramStructuredMetadataExtractor

**Package:** `com.fishit.player.pipeline.telegram.grouper`

**Responsibility:** Extrahiert strukturierte Felder aus TEXT-Nachrichten

**Contract:**
- MUSS alle definierten Structured Fields erkennen (Section 1.4)
- MUSS TMDB-URL zu ID + Type parsen (Rule R5) - unterstützt `/movie/` und `/tv/`
- MUSS Schema Guards anwenden (Rule R4) - ungültige Werte auf null setzen
- MUSS fehlende Felder als `null` zurückgeben
- DARF KEINE Werte erfinden oder ableiten

### 4.3 TelegramBundleToMediaItemMapper

**Package:** `com.fishit.player.pipeline.telegram.mapper`

**Responsibility:** Konvertiert Bundles zu TelegramMediaItem(s) - unterstützt Multi-Asset-Emission

**Contract:**
- MUSS Primary Asset Selection Rules anwenden (Rule R8b)
- MUSS Poster-Auswahl-Regeln anwenden (Rule R9) - max pixel area
- MUSS alle Bundle-Felder korrekt setzen
- MUSS `bundleType` korrekt setzen
- MUSS bei Multi-Video-Bundles mehrere Assets emittieren (lossless, Rule R8)
- MUSS nicht-primäre Assets als Alternativen markieren

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
| Structured Metadata extrahiert | DEBUG | `chatId`, `tmdbId`, `tmdbType`, `year`, `fsk` |
| TMDB-URL parse failed | WARN | `chatId`, `messageId`, `tmdbUrl` |
| Bundle rejected (cohesion failed) | DEBUG | `chatId`, `timestamp`, `messageIds`, `reason` |
| Bundle-Statistik pro Chat | INFO | `chatId`, `bundleCount`, `singleCount` |

**Mandatory Per-Chat Metrics:**

Die folgenden Metriken MÜSSEN pro Chat getrackt und geloggt werden:

| Metrik | Typ | Beschreibung |
|--------|-----|--------------|
| `bundleCandidateCount` | Counter | Anzahl Timestamp-Gruppierungen |
| `bundleAcceptedCount` | Counter | Anzahl akzeptierter Bundles (Kohäsion erfolgreich) |
| `bundleRejectedCount` | Counter | Anzahl abgelehnter Bundles (Kohäsion fehlgeschlagen) |
| `bundlesByType` | Map<BundleType, Count> | Verteilung: FULL_3ER, COMPACT_2ER, SINGLE |
| `orphanTextCount` | Counter | TEXT mit structured fields aber ohne akzeptiertes Bundle |
| `orphanPhotoCount` | Counter | PHOTO ohne akzeptiertes Bundle |
| `videoVariantCountTotal` | Counter | Gesamtzahl emittierter Video-Assets |
| `multiVideoBundleCount` | Counter | Anzahl Bundles mit >1 VIDEO |

---

## 6. Test-Anforderungen

### 6.1 Required Unit Tests

| Test-ID | Beschreibung | Fixture |
|---------|--------------|---------|
| TB-001 | Gruppierung nach Timestamp | 3 Nachrichten, gleicher Timestamp |
| TB-002 | Keine Gruppierung bei unterschiedlichen Timestamps | 3 Nachrichten, verschiedene Timestamps |
| TB-003 | FULL_3ER Klassifikation | VIDEO + TEXT + PHOTO |
| TB-004 | COMPACT_2ER Klassifikation | TEXT + VIDEO |
| TB-005 | Cohesion Gate akzeptiert validen Candidate | BundleCandidate mit messageId-Proximity <= 3*2^20 |
| TB-006 | Cohesion Gate lehnt invaliden Candidate ab | BundleCandidate mit zu großem messageId-Span |
| SM-001 | TMDB-URL Parsing (movie) | Standard URL `/movie/12345` |
| SM-002 | TMDB-URL Parsing (tv) | TV URL `/tv/98765` |
| SM-003 | TMDB-URL mit Slug | URL mit `-name` Suffix |
| SM-004 | FSK Extraktion | `"fsk": 12` |
| SM-005 | Fehlende Felder | TEXT ohne tmdbUrl |
| SM-006 | Schema Guard: ungültiges Jahr | `year: 3000` → null |
| SM-007 | Schema Guard: ungültiges Rating | `tmdbRating: 15.0` → null |
| SM-008 | Schema Guard: ungültiger FSK | `fsk: 50` → null |
| SM-009 | Schema Guard: ungültige Länge | `lengthMinutes: 1000` → null |
| MM-001 | Primary Asset: größtes Video | 2 VIDEOs, verschiedene Größen |
| MM-002 | Primary Asset: längste Dauer | 2 VIDEOs, gleiche Größe |
| MM-003 | Multi-Video Bundle emittiert N Assets | Bundle mit 3 VIDEOs → 3 Assets, 1 primär |
| MM-004 | Poster-Auswahl: max pixel area | PHOTO mit 3 Größen, wählt größte Fläche |
| MM-005 | Poster-Auswahl: Tie-Breaker | 2 Größen gleiche Fläche, wählt höhere height |

### 6.2 Required Integration Tests

| Test-ID | Chat | Erwartung |
|---------|------|-----------|
| INT-001 | Mel Brooks 🥳 | ≥8 FULL_3ER Bundles erkannt |
| INT-002 | Filme kompakt | ≥8 COMPACT_2ER Bundles erkannt |
| INT-003 | Unstrukturierter Chat | 0 Bundles, alle SINGLE |
| INT-004 | Cohesion Rejection | BundleCandidate mit gleichem Timestamp aber unrelated messages wird rejected/split |
| INT-005 | Multi-Video Emission | Bundle mit multi-video emittiert ≥2 Assets für ein Work |

---

## 7. Compliance-Checkliste

Vor jedem Merge MUSS geprüft werden:

- [ ] Keine Normalisierung in Pipeline (MEDIA_NORMALIZATION_CONTRACT R10)
- [ ] Keine TMDB-Lookups in Pipeline
- [ ] `globalId` bleibt leer (Pipeline berechnet KEINE kanonische ID)
- [ ] Structured Fields werden RAW extrahiert mit Schema Guards (R4)
- [ ] TMDB-ID + Type via Regex extrahiert, unterstützt /movie/ und /tv/ (R5)
- [ ] Bundle Cohesion Gate implementiert (R1b)
- [ ] Multi-Video Bundles emittieren alle Assets lossless (R8)
- [ ] Primary Asset Selection Rules implementiert (R8b)
- [ ] Poster-Auswahl via max pixel area (R9)
- [ ] TelegramMediaItem bleibt pipeline-intern (R11)
- [ ] Logging gemäß Section 5.3 mit allen Metrics
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
