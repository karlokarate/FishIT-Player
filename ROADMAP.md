Ziele (was am Ende steht)

- Startseite hat genau drei horizontale Rows – Serien, Filme, TV – unter einem persistenten, halbtransparenten Header; Inhalte scrollen darunter.
- Sortierung: Serien & Filme nach Release‑Jahr absteigend (neueste zuerst), robuste Fallbacks (Jahr in Klammern aus dem Titel extrahieren).
- TV‑Row zeigt nur deutsche Sender (per Country/Language/Group/Name‑Heuristik).
- Keine überdeckten Inhalte: Top/Bottom‑Bar belegen Platz via Insets; kein verschachteltes Scaffold mehr.
- TV‑Focus/Remote tauglich und Icon‑Buttons aus dem PNG‑Pack überall konsistent.

Phase 1 – Globaler Chrome (pinned Header + kompakte Bottom‑Bar)

- HomeChromeScaffold (bereitgestellt) ist einzige Chroming‑Instanz.
- TopBar: halbtransparent + Blur; BottomBar: kompakt.
- Content erhält PaddingValues(top=status+TopBarHeight, bottom=navigation+BottomBarHeight) → nichts wird verdeckt.
- Aufräumen: Alle untergeordneten Screens: keine eigenen Scaffolds, keine statusBarsPadding()/navigationBarsPadding() mehr.

Akzeptanzkriterien

- Beim Scrollen bleiben Header‑Icons sichtbar; Rows beginnen unterhalb des Headers.
- Kein „Mischmasch“: Startseite zeigt nur die 3 definierten Rows.

Phase 2 – Daten-Selektion, Sortierung & Filter

Lege einen kleinen, wiederverwendbaren Selector‑Helper an (neutral gegenüber deinen Modellen):

// ContentSelectors.kt (neu)
package com.chris.m3usuite.domain.selectors

private val yearRegex = Regex("""\((\d{4})\)""")

fun extractYearFrom(text: String?): Int? =
    text?.let { yearRegex.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }

fun <T> sortByYearDesc(
    items: List<T>,
    yearOf: (T) -> Int?,
    titleOf: (T) -> String
): List<T> = items.sortedWith(
    compareByDescending<T> { yearOf(it) ?: extractYearFrom(titleOf(it)) ?: Int.MIN_VALUE }
        .thenBy { titleOf(it) } // stabiler Fallback
)

fun <T> filterGermanTv(
    items: List<T>,
    countryOf: (T) -> String?,    // "DE", "Germany", ...
    languageOf: (T) -> String?,   // "de", "German", ...
    groupOf: (T) -> String?,      // z.B. "DE", "Germany"
    nameOf: (T) -> String         // Kanalname als Fallback
): List<T> {
    fun isDe(s: String?) = s?.contains("de", true) == true || s?.contains("germ", true) == true
    return items.filter { item ->
        isDe(countryOf(item)) || isDe(languageOf(item)) || isDe(groupOf(item)) ||
        // Fallback: häufige Präfixe/Kennungen im Namen
        nameOf(item).contains(" DE ", true) ||
        nameOf(item).startsWith("DE-", true) ||
        nameOf(item).contains("Germany", true)
    }
}


Einbindung:

Serien/Filme: sortByYearDesc(list, { it.year }, { it.name }) – wenn kein year im Modell: nur extractYearFrom(it.name).

TV: filterGermanTv(channels, { it.country }, { it.language }, { it.group }, { it.name }).

Akzeptanzkriterien

- Fehlt das Jahr im Modell, wird es stabil aus „(YYYY)“ im Titel geparst.
- TV‑Row enthält nur DE‑Sender (sichtbar am UI).

Phase 3 – Startseite exakt 3 Rows (Serien → Filme → TV)

StartScreen (bereitgestellt) wird präzisiert:

- maxRowsStart = 3.
- Reihenfolge fix: Serien, Filme, TV.
- Serien/Filme: sortByYearDesc; TV: filterGermanTv.
- Row‑Header: „Serien“, „Filme“, kein Header bei TV (wie gewünscht möglich).

Beispiel‑Wiring im ViewModel (skizziert):

// HomeViewModel.kt (skizze)
val startSeries = repository.seriesFlow
    .map { sortByYearDesc(it, { s -> s.year }, { s -> s.name }) }

val startMovies = repository.vodFlow
    .map { sortByYearDesc(it, { v -> v.year }, { v -> v.name }) }

val startTv = repository.liveFlow
    .map { filterGermanTv(it, { c -> c.country }, { c -> c.language }, { c -> c.group }, { c -> c.name }) }


StartScreen-Aufruf:

StartScreen(
  series = uiState.series,   // sortierte Liste
  movies = uiState.movies,   // sortierte Liste
  tv = uiState.tv,           // nur DE
  onSearch = { /* ... */ },
  onProfiles = { /* ... */ },
  onSettings = { /* ... */ },
  onRefresh = { /* ... */ },
  bottomBar = { /* Icons: Alle / VOD / Serien / Live */ },
  poster = { item -> PosterCard(item) } // eure bestehende Card
)


Hinweis: Wir hatten vorher resume entfernt; jetzt exakt 3 Rows. Wenn du später „Weiter schauen“ zurückwillst: einfach vor Serien einfügen und maxRowsStart = 4 setzen.

Akzeptanzkriterien

- Die Startseite zeigt nur 3 Rows.
- Serien und Filme eindeutig „neuste zuerst“.
- TV enthält nur DE.

Phase 4 – Listen‑Screens (viele vertikale Sections)

Live/Serien/VOD‑Screens nutzen SectionsScreen (bereitgestellt) → beliebig viele horizontale Rows untereinander.

Gleicher Header/BottomBar wie Startseite (über HomeChromeScaffold).

Kein zweites Scaffold, keine zusätzlichen Insets.

Akzeptanzkriterien

- Jede Liste scrollt weich unter dem persistierenden Header.
- Keine Überlappung durch Top/Bottom‑Bar.

Phase 5 – Icons, Fokus & Interaktion (bereits integriert, Feinschliff)

- PNG‑Pack Option B in drawable-* (fertig).
- AppIconButton nutzt PNGs + TV‑Focus‑Scale & Overlay.
- Header: Search, Profile, Settings, Refresh als Icons.
- Card‑Overlays (Play/Info) weiterhin als Icons (32/28 dp).

Akzeptanzkriterien

- Buttons sind sofort erkennbar (Icon + contentDescription).
- Fire TV‑Remote: Fokus‑Vergrößerung sichtbar, kein „Focus‑Loss“ beim Scroll.

Phase 6 – Performance/UX‑Polish

- Image‑Prefetch für die nächsten 1‑2 Rows (Coil: ImageRequest prefetch).
- Shimmer/Skeleton für Row‑Items, bis Daten da sind.
- Snap‑Scrolling für LazyRow (Netflix‑Gefühl): snapFlingBehavior.
- Remembered scroll state pro Row (Zurückspringen an gleiche Position).
- Stabiler key (nutze serverseitige ID, nicht Index).

Akzeptanzkriterien

- Reibungsloses Scrollen ohne Jank.
- Zurücknavigieren erhält Row‑Positionen.

Phase 7 – Checks & Debug

- Insets‑Audit: Suche projektweit nach Scaffold(, statusBarsPadding(, navigationBarsPadding(.
- Es darf nur noch das Root‑Chrome paddings setzen.
- TV‑Filter: Stichproben (5–10 Kanäle) – alle deutsch.
- Sortierung: Stichprobe Serien/Filme — Jahre strikt absteigend; wenn Jahr fehlt, prüfe Regex‑Fallback.
- A11y: contentDescriptions auf Header‑Icons und Poster‑Overlays.

Optional: Codex‑Startpaket (3 präzise Blöcke)

A) Selector‑Helpers

Create file app/src/main/java/com/chris/m3usuite/domain/selectors/ContentSelectors.kt
[Inhalt = extractYearFrom, sortByYearDesc, filterGermanTv (siehe oben)]


B) StartScreen final (3 Rows)

Edit app/src/main/java/com/chris/m3usuite/ui/home/StartScreen.kt
- Entferne "resume"
- API: fun StartScreen(series: List<Any>, movies: List<Any>, tv: List<Any>, ...)
- Reihenfolge: item { SectionRow("Serien", series, ...) }
               item { SectionRow("Filme", movies, ...) }
               item { SectionRow(null, tv, ...) } // TV ohne Header-Text
- maxRowsStart fix auf 3 (oder entferne param)


C) ViewModel‑Selektion

Edit HomeViewModel.kt
- Wandle Flows/Livedata so, dass:
  seriesUi = sortByYearDesc(seriesRaw, { it.year }, { it.name })
  moviesUi = sortByYearDesc(vodRaw,    { it.year }, { it.name })
  tvUi     = filterGermanTv(liveRaw,   { it.country }, { it.language }, { it.group }, { it.name })
- Übergib seriesUi/moviesUi/tvUi an StartScreen

Ergebnisbild (erwartet)

- Oben: schmaler, leicht transparenter Header (Icons: Suche, Profil, Settings, Refresh).
- Darunter: Serien (neu → alt) • Filme (neu → alt) • TV (DE), jeweils horizontal scrollbar.
- Unten: kompakte Bottom‑Bar (Icons), die nichts überdeckt.
- TV‑Remote: klare Fokus‑Indikation.

