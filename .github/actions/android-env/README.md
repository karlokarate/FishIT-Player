# Android Environment Setup Action

Wiederverwendbare Composite Action zum Einrichten der Android-Entwicklungsumgebung mit optimiertem Caching.

## Features

- ✅ **Android SDK Caching**: Reduziert Setup-Zeit von ~2 Minuten auf ~10 Sekunden bei Cache-Hit
- ✅ **JDK Setup**: Java 21 (Standard) mit Gradle-Caching
- ✅ **Android SDK**: Automatische Installation von platform-tools, platforms und build-tools
- ✅ **NDK Setup**: Android NDK r27c (Standard) mit lokaler Cache-Unterstützung
- ✅ **Conditional Installation**: SDK-Pakete werden nur bei Cache-Miss installiert

## Usage

```yaml
steps:
  - uses: actions/checkout@v5

  - name: Setup Android Environment
    uses: ./.github/actions/android-env
    with:
      java-version: '21'    # Optional, Standard: '21'
      ndk-version: 'r27c'   # Optional, Standard: 'r27c'
```

## Inputs

| Input | Beschreibung | Required | Standard |
|-------|-------------|----------|----------|
| `java-version` | Java-Version für JDK Setup | Nein | `'21'` |
| `ndk-version` | Android NDK Version | Nein | `'r27c'` |

## Outputs

Keine direkten Outputs, aber folgende Umgebungsvariablen werden gesetzt:
- `ANDROID_NDK_HOME`: Pfad zum NDK
- `ANDROID_NDK_ROOT`: Pfad zum NDK (alias)

## Cache-Strategie

### Android SDK Cache
- **Path**: `/usr/local/lib/android/sdk/{platforms,build-tools,platform-tools}`
- **Key**: `android-sdk-${{ runner.os }}-24-34-35.0.0` (entspricht den installierten Plattformen 24, 34 und build-tools 35.0.0)
- **Restore Keys**: `android-sdk-${{ runner.os }}-`

### Gradle Cache
- Automatisch durch `actions/setup-java@v4` mit `cache: gradle`

### NDK Cache
- Automatisch durch `nttld/setup-ndk@afb4c9964b521afb97c864b7d40b11e6911bd410` (v1, gepinnt auf Commit-SHA) mit `local-cache: true`

## Performance-Verbesserungen

Bei wiederholten Workflow-Runs:
- **SDK Installation**: ~2 Minuten → ~10 Sekunden (Cache-Hit)
- **Gesamte Setup-Zeit**: ~50-70% schneller
- **Gradle Dependencies**: Zusätzliche Zeitersparnis durch Gradle-Caching

## Verwendung in anderen Workflows

Diese Action kann in allen Workflows verwendet werden, die eine Android-Umgebung benötigen:

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5
      
      - name: Setup Android Environment
        uses: ./.github/actions/android-env
      
      - name: Build
        run: ./gradlew assembleDebug
```

## Wartung

Wenn SDK-Versionen aktualisiert werden:
1. Update `key` in `.github/actions/android-env/action.yml` (z.B. von `24-34-35.0.0` zu `24-35-36.0.0`)
2. Update `sdkmanager --install` Pakete in derselben Datei
3. Test durchführen mit `workflow_dispatch`

**Hinweis:** Der Cache-Key wird nur in `.github/actions/android-env/action.yml` definiert und richtet sich nach den dort konfigurierten SDK-Versionen.
