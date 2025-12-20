# app-v2

## Telegram API credentials

The TDLib session requires Telegram API credentials to be provided at build time. The Gradle configuration reads them from the environment:

- `TG_API_ID` — numeric API ID from [my.telegram.org](https://my.telegram.org/apps)
- `TG_API_HASH` — API hash from the same Telegram app configuration

### Local builds

Export the variables before invoking Gradle so `BuildConfig` contains valid values:

```bash
export TG_API_ID=<api_id>
export TG_API_HASH=<api_hash>
./gradlew :app-v2:assembleDebug
```

If either value is missing or empty in debug builds, app startup will fail fast in `TelegramAuthModule` to surface the misconfiguration. Release builds will log a warning instead.
