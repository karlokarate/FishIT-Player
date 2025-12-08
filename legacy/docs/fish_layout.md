Fish Module Übersicht

- app/src/main/java/com/chris/m3usuite/ui/layout/FishTheme.kt
    - Tokens (FishDimens): Größe/Abstände/Ecken/Scale/Glow/Padding (tileWidth/Height/Corner/Spacing, contentPaddingHorizontal, focusScale, focusBorderWidth, reflectionAlpha, enableGlow, showTitleWhenUnfocused).
    - Theme-Wrapper (FishTheme): injiziert Tokens via LocalFishDimens; steuert alle FishTiles/Rows zentral.
- app/src/main/java/com/chris/m3usuite/ui/layout/FishTile.kt
    - Gemeinsame Wrapper (`LiveFishTile`, `SeriesFishTile`, `VodFishTile`, `TelegramFishTile`) liegen in `ui/layout/FishMediaTiles.kt` und werden von Start/Library/Rows genutzt.
    - Einheitliches Tile: ContentScale.Fit, abgerundete Ecken, Reflection, Fokus‑Scale + Frame, globaler Glow (via FocusKit), optionaler Selection‑Rahmen.
    - Slots: topStartBadge (z. B. „+ / ✓“), bottomEndActions (z. B. Play/Assign), footer (z. B. Plot).
    - Verhalten: Kein per‑Tile Bring‑Into‑View (Row regelt Scroll), onFocusChanged‑Hook (z. B. Logging).
- app/src/main/java/com/chris/m3usuite/ui/layout/FishHeader.kt
    - `FishHeaderHost` platziert eine futuristische „Focus Beacon“-Overlay, gespeist von `FishHeaderData` (Text/Chip/Provider).
    - `FishRow`/`FishRowPaged` akzeptieren optional `header`, das beim Row-Fokus automatisch aktiviert wird.
- app/src/main/java/com/chris/m3usuite/ui/layout/FishRow.kt
    - Reihen (minimaler Layer, optionaler Header nur bei Übergabe):
        - FishRowLight: einfache LazyRow + Fokus (fixes Spacing/Padding aus Tokens).
        - FishRow: Media‑Engine für List<MediaItem> (DPAD, edgeLeftExpandChrome, initialFocusEligible, Fokus‑Persistenz).
        - FishRowPaged: Paged‑Engine für LazyPagingItems<MediaItem>.
    - Delegiert an FocusKit (TvRowLight/Media/Paged); zentrale DPAD‑/Scroll‑Logik; keine Row‑Rahmen/Overlays.
- app/src/main/java/com/chris/m3usuite/ui/layout/FishVodContent.kt
    - VOD‑Inhalte für Tiles: displayTitle (+Jahr), Poster‑Wahl (poster > logo > backdrop), Resume‑Fortschritt (0..1), NEW‑Badge, Auswahl‑Badge (+/✓), Bottom‑End‑Aktionen (Play + Assign), onClick (Assign/Details),             
      onFocusChanged (Logging).
    - Liefert ein fertiges VodTileContent zur direkten Befüllung von FishTile.
- app/src/main/java/com/chris/m3usuite/ui/layout/FishSeriesContent.kt
    - Serien‑Basisinhalte: Title (+Jahr), Poster‑Wahl (poster > backdrop), Auswahl‑Badge, Klick (Assign/Details), (Platzhalter für Staffeln/Episoden).
    - Bereit für Erweiterungen (z. B. „Weiter schauen“, Episoden‑Progress).
- app/src/main/java/com/chris/m3usuite/ui/layout/FishLiveContent.kt
    - Live‑Basisinhalte: Title, Logo‑Wahl (logo > poster > backdrop), (Platzhalter für EPG Now/Next‑Progress), Klick/Actions‑Slot.
    - Bereit für EPG‑Anbindung (Now/Next + Fortschritt).
- app/src/main/java/com/chris/m3usuite/ui/layout/FishMeta.kt
    - Meta‑Helper: displayVodTitle (Titel (+Jahr)), pickVodPoster, PlotFooter (2 Zeilen Ellipsis), rememberVodResumeFraction (Composable IO‑Helper).
    - Wiederverwendbar in allen Content‑Modulen.
- app/src/main/java/com/chris/m3usuite/ui/layout/FishActions.kt
    - Aktionen/Badges: AssignBadge (+/✓, Top‑Left), VodBottomActions (Play), AssignBottomAction (BookmarkAdd, Bottom‑End).
    - Bottom‑Buttons sind nicht fokusierbar (DPAD springt nicht in Overlays).
- app/src/main/java/com/chris/m3usuite/ui/layout/FishLogging.kt
    - Fokus‑Logs: OBX‑Titelauflösung + GlobalDebug (z. B. für VOD); wird über onFocusChanged aus Content aufgerufen.
- app/src/main/java/com/chris/m3usuite/ui/layout/FishResumeTile.kt
    - Generische Resume‑Karte (VOD/Serien‑Folgen): 2‑zeiliger Titel, optionales Subtitle (z. B. Fortschritt), Play/Clear‑Actions, Fokus‑Scale + Glow via FocusKit.                                                               


- VodTileCard (Tile)
    - app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:1089
    - Ersetzt durch: FishTile + FishVodContent.buildVodTileContent
- TelegramTileCard (Tile)
    - app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:1855
    - Ersetzt durch: FishTile + „Tagged“-Badge (Badge‑Slot). Für Content (TG) später: FishTelegramContent (analog).

Hinweis: Es existieren .orig‑Dateien mit älteren Versionen; maßgeblich ist die „ohne .orig“.

B. Rows (alte Engines + Render) → neu: FishRow (Light/Media/Paged)

- Legacy `LiveRow`/`SeriesRow`/`VodRow` (inkl. Paged) in `ui/components/rows/HomeRows.kt` wurden entfernt. Alle Call-Sites nutzen jetzt `FishRow`/`FishRowPaged` mit den gemeinsamen Tiles in `ui/layout/FishMediaTiles.kt`.

Engine-Aufrufer heute (`MediaRowCore`/`MediaRowCorePaged` in `ui/focus/FocusRowEngine.kt`) → neu: FocusKit via FishRow

- MediaRowCore
    - app/src/main/java/com/chris/m3usuite/ui/focus/FocusRowEngine.kt
- MediaRowCorePaged
    - app/src/main/java/com/chris/m3usuite/ui/focus/FocusRowEngine.kt
- Ersetzen durch: FishRow/FishRowPaged (delegieren intern an FocusKit.TvRowMedia/Paged).

Raw LazyRow (vermeiden; über FishRow ersetzen)

- ✅ StartScreen Telegram-Suche nutzt `FishRow` + `FishTile` (Badge „T“). Kein Raw LazyRow mehr offen.

C. Legacy Cards / CARDS_V1

- ✅ `ui/cards/*` and `BuildConfig.CARDS_V1` removed (2025-10-07). FishTile/FishRow now cover all rows and Telegram badges via FishTile slots.
- ✅ StartScreen nutzt denselben Fish*-Stack wie die Library (Serien/VOD/Live + Telegram). AccentCard/BoxWithConstraints wurden entfernt; Layout-Anpassungen laufen künftig über Tokens.

D. Assign‑Selektion/Badges/Actions → neu: FishActions/FishVodContent                                                                                                                                                             
Bisherige Stellen mit Selektion:

- HomeRows Tiles: app/src/main/java/com/chris/m3usuite/ui/components/rows/HomeRows.kt:295, 816, 1112 (LocalAssignSelection)
- StartScreen: globales `LocalAssignSelection` wird einmal am LazyColumn-Einstieg gesetzt.
- Neu:
    - FishVodContent nutzt LocalAssignSelection zentral (Top‑Badge +/✓, Klick toggelt in Assign‑Mode).
    - FishActions.AssignBottomAction bietet Bottom‑Assign Action (nicht fokusierbar).

E. Resume (VOD/Series) → neu: FishResumeTile

- Vorhandene Resume‑UI:
    - (Removed) Legacy ResumeCarousel (ResumeVodRow + ResumeCard) wurde durch FishRow/FishResumeTile ersetzt und ist nicht mehr im Code.
- Neu:
    - FishResumeTile (UI) universell; späte Portierung von Resume‑Carousels.
    - Resume‑Daten: weiter via ResumeRepository (bereits in FishMeta.rememberVodResumeFraction).

F. Fokus/DPAD/Glow

- Bisher: focusScaleOnTv + tvFocusGlow + tvClickable je Tile/Row (viele verstreute Aufrufe).
- Neu:
    - FishTile kapselt Fokus‑Visuals (Scale + Frame + optional Glow via Token).
    - FishRow kapselt DPAD/Scroll/edgeLeft→Chrome/Initial Focus (über FocusKit).
    - Glow über FocusKit‑Wrapper global (deaktivierbar via Token).

G. Sonstiges

- Poster/Headers: AppAsyncImage + RequestHeadersProvider (zentrale Headers) bleiben – FishTile nutzt sie.
- Obx Logging: FishLogging.logVodFocus (aufgerufen per onFocusChanged im buildVodTileContent).  
