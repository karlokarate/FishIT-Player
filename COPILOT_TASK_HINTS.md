Available Gradle quality tasks Copilot should use, e.g.:

## Build Tasks
- ./gradlew :app-v2:assembleDebug          # Build debug APK
- ./gradlew :app-v2:assembleRelease        # Build release APK

## Quality & Linting Tasks
- ./gradlew lintDebug                      # Android Lint (TV checks)
- ./gradlew detekt                         # Kotlin code smells
- ./gradlew ktlintCheck                    # Kotlin code style
- ./gradlew ktlintFormat                   # Auto-format Kotlin

## Testing Tasks
- ./gradlew testDebugUnitTest              # Unit tests
- ./gradlew connectedDebugAndroidTest      # Instrumented tests
- ./gradlew :app-v2:compileDebugAndroidTestSources  # Compile tests

## Notes
- For TDLib builds use the existing TDLib workflows/scripts.
- See DEVELOPER_GUIDE.md for detailed usage and best practices.
