# Xtream Fix Report

## What was broken
- Onboarding rejected valid Xtream URLs and ignored percent-encoded credentials.
- Xtream transport silently accepted HTML/challenge pages and HTTP errors, leading to empty lists instead of explicit failures.
- Auth/connection state propagation from transport to onboarding domain only reflected snapshots, making UI updates unreliable.
- Debug traffic inspection for Xtream HTTP calls was missing.

## What changed
- Updated `OnboardingViewModel.parseXtreamUrl` to URL-decode credentials and added unit coverage for common URL shapes (get.php, player_api.php, encoded values).
- Added script-parity initialization in `DefaultXtreamApiClient` (preflight player_api, then get_server_info with fallback) and hardened `fetchRaw` to detect HTML/challenge responses, preserving the provided scheme/port.
- Introduced explicit Xtream errors for HTML challenges and surfaced HTTP failures instead of returning empty data.
- Switched `XtreamAuthRepositoryAdapter` to continuously collect transport auth/connection flows; added Turbine test for Pending→Authenticated propagation.
- Wired Chucker (debug-only) into the Xtream OkHttp client with username/password query redaction.

## How to verify locally
1. **Unit tests**
   - Run onboarding and Xtream data tests: `./gradlew :feature:onboarding:testDebugUnitTest :infra:data-xtream:testDebugUnitTest`
2. **Manual onboarding parsing**
   - Enter `http://konigtv.com:8080/get.php?username=Christoph10&password=JQ2rKsQ744&type=m3u_plus&output=ts` and confirm logs show `host=konigtv.com, port=8080, useHttps=false`.
3. **Transport requests**
   - Initialize Xtream with real credentials; observe logs for:
     - `runScriptParityAuthCheck` preflight URL
     - `getServerInfo` request and response byte size
     - Errors for any HTML/challenge pages instead of empty results
4. **State flow observation**
   - While requests run, ensure onboarding logs show connection/auth transitions (Pending → Connected/Authenticated or error).
5. **Chucker (debug build)**
   - Launch a debug build and open Chucker; confirm player_api/panel_api calls appear with `username`/`password` query params redacted.

Expected log tags: `OnboardingViewModel`, `XtreamAuthRepoAdapter`, `XtreamApiClient`.
