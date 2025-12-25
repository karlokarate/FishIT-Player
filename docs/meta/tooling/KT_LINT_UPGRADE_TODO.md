# Ktlint plugin upgrade TODO

## Status

Attempted to upgrade `org.jlleitschuh.gradle.ktlint` from 12.1.2 to 14.0.1.
The newer plugin enforces stricter formatting rules (import ordering, annotation
layout, trailing commas, and indentation) across `app-v2` and other modules,
resulting in a repo-wide reformat requirement.

## Follow-up

When scheduling a formatting sweep, rerun the upgrade and apply
`./gradlew ktlintFormat` (or an equivalent batch reformat) to align the codebase.
No Gradle/Kotlin baseline blockers were observed during the attempt, but verify
compatibility with the current Gradle/AGP/Kotlin versions before re-enabling
the bump.
