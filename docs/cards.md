# Cards Library (v1)

Components
- PosterCard(title?, imageUrl?, onClick)
- ChannelCard(name, logoUrl?, nowNext?, onClick)
- SeasonCard(name, posterUrl?, onClick)
- EpisodeRow(title, subtitle?, imageUrl?, onClick)

Guidelines
- Ratios: Poster 2:3, Channel ~16:10, Episode 16:9
- Focus: `focusScaleOnTv` + `tvClickable`; decorative images use `contentDescription=null` when appropriate
- Test tags: Card-Poster, Card-Channel, Card-Season, Card-Episode

Integration
- `HomeRows` delegates to Cards when `BuildConfig.CARDS_V1` is ON.
- Series episode list renders `EpisodeRow` under the flag.

Flags
- `BuildConfig.CARDS_V1` (default ON). Legacy tile implementations remain as fallback.
