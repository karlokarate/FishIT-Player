# NextLib Codecs Module

This module integrates FFmpeg-based software decoders via **NextLib** into the FishIT-Player internal player (SIP).

## Purpose

Extends Media3/ExoPlayer with software decoding support for codecs not natively supported by all Android devices.

## Supported Codecs

### Audio
- **Vorbis** - Common in WebM containers
- **Opus** - Modern, high-efficiency codec
- **FLAC** - Lossless audio
- **ALAC** - Apple Lossless
- **MP3** - MPEG-1/2 Audio Layer III
- **AAC** - Advanced Audio Coding
- **AC3** - Dolby Digital
- **EAC3** - Enhanced AC-3 (Dolby Digital Plus)
- **DTS** - DTS Digital Surround
- **TrueHD** - Dolby TrueHD (MLP)
- **PCM** - Raw PCM audio (μ-law, A-law)
- **AMR** - AMR-NB, AMR-WB

### Video
- **H.264** - AVC, most common codec
- **HEVC** - H.265, newer high-efficiency codec
- **VP8** - WebM video
- **VP9** - WebM video (improved)

> **Note:** AV1 is NOT included. AV1 decoding remains via hardware or Media3's native extension.

## Architecture

```
player/nextlib-codecs/
├── build.gradle.kts
├── README.md
└── src/main/java/com/fishit/player/nextlib/
    ├── NextlibCodecConfigurator.kt    # Abstraction interface
    └── di/
        └── NextlibCodecsModule.kt     # Hilt DI bindings
```

## Usage

The module provides `NextlibCodecConfigurator` which creates a `RenderersFactory` using NextLib's FFmpeg decoders.

```kotlin
// Injected via Hilt
class InternalPlayerSession(
    private val codecConfigurator: NextlibCodecConfigurator,
    // ...
) {
    fun createPlayer(): ExoPlayer {
        val renderersFactory = codecConfigurator.createRenderersFactory(context)
        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .build()
    }
}
```

## License

**NextLib is GPL-3.0 licensed** due to its FFmpeg dependency.

This affects binary distribution requirements. Consult legal guidance before public release.

## Dependencies

| Dependency | Version | Notes |
|------------|---------|-------|
| NextLib media3ext | 1.8.0-0.9.0 | Must match Media3 version (1.8.x) |
| Media3 ExoPlayer | 1.8.0 | Player core |

## Layer Boundaries

This module:
- ✅ MAY depend on: `infra:logging`
- ❌ MUST NOT depend on: `pipeline/**`, `infra/transport-*`, `infra/data-*`
- ❌ MUST NOT be depended on by: pipelines, transport, or data layers

Only `player:internal` should depend on this module.
