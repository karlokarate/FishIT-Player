# Xtream Fix Report

## Broken behaviors
- Xtream onboarding parser did not decode percent-encoded credentials and missed some URL forms, causing rejected logins.
- Transport initialization occasionally stopped at HTML challenge pages or empty responses, treating them as empty data instead of connection errors.
- Xtream auth/connection state exposure was snapshot-based, so UI could miss transitions during requests.
- Debugging HTTP traffic lacked an interceptor; sensitive query params risked appearing in logs.

## Changes implemented
- Added UTF-8 decoding for Xtream URLs (get.php, player_api.php, userinfo format) and unit coverage for standard, player_api, and percent-encoded cases.
- Hardened transport initialization: server info flow now mirrors script parity (plain player_api.php then get_server_info with live-categories fallback), preserves provided scheme/port, and surfaces HTML challenge/HTTP failures as explicit errors with redacted logging.
- Updated Xtream state adapter to continuously collect transport flows and added Turbine coverage to verify Pending → Connected/Auth transitions.
- Integrated Chucker (debug only) plus legacy User-Agent/Cookie jar defaults to inspect Xtream HTTP traffic without leaking credentials in app logs.

## How to verify locally
1. **Unit tests**: `./gradlew feature:onboarding:test infra:data-xtream:test infra:transport-xtream:test --console=plain` (Android SDK required). 
2. **Onboarding**: Enter `http://konigtv.com:8080/get.php?username=Christoph10&password=JQ2rKsQ744&type=m3u_plus&output=ts`; logs with tag `OnboardingViewModel` should show parsed host/port/scheme/user without the password.
3. **Transport auth**: In debug build, attempt Xtream login with real credentials. Watch logs tagged `XtreamApiClient` for sequential requests `player_api.php` → `get_server_info` (or fallback) with response byte counts; HTML responses should surface as `UnexpectedHtml` errors.
4. **Chucker**: Open the Chucker UI in debug builds to inspect Xtream requests (player_api.php/panel_api.php/actions) with headers; password/username remain redacted in app logs.
