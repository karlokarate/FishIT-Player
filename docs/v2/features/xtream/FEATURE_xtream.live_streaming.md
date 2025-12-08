# Feature: xtream.live_streaming

## Metadata
- **ID:** `xtream.live_streaming`
- **Scope:** PIPELINE
- **Owner:** `pipeline:xtream`

## Dependencies
- `core:model` - Canonical media models
- `infra:logging` - Unified logging

## Guarantees
- Provides live TV channel streaming via Xtream Codes API
- Supports EPG (Electronic Program Guide) integration
- Handles channel listing and categorization
- Produces RawMediaMetadata for live streams

## Failure Modes
- Network connectivity issues → retry with exponential backoff
- Invalid Xtream credentials → clear auth error
- Stream URL unavailable → fallback to alternative sources if available

## Logging & Telemetry
- Log tag: `XtreamLiveStreaming`
- Events: channel_list_fetched, stream_started, stream_error

## Test Requirements
- Unit tests for API client and data mapping
- Integration tests with mock Xtream server
