---
name: bump-deps
description: Resolve current dependency versions from the registry and update gradle/libs.versions.toml. Use when adding a dependency, upgrading Kotlin/AGP/Compose/Hilt, checking whether something is out of date, or whenever a version number needs to be written.
---

# Bump dependencies

**Never write a version number from memory.** Training data goes stale in months, and the
Kotlin toolchain in particular breaks across minor releases — Kotlin 2.3.0 shipped before KSP
and Hilt supported it.

## Resolve

For each artifact, fetch the actual current version:

```bash
curl -s https://repo1.maven.org/maven2/<group-as-path>/<artifact>/maven-metadata.xml
```

For AndroidX use the release-notes page (`developer.android.com/jetpack/androidx/releases/<lib>`),
which distinguishes stable from alpha — Maven metadata does not. Google-hosted artifacts are at
`https://dl.google.com/dl/android/maven2/...`. For plugins, `plugins.gradle.org/plugin/<id>`.

**Take the latest stable.** Alpha or beta only with a stated reason.

## Check the matrix before changing anything

These four move together and a mismatch is a hard build failure:

| | Constraint |
|---|---|
| KSP | **versioned independently since 2.3.0** (e.g. `2.3.11`) — the old `<kotlin>-<ksp>` form is gone. Check its release notes for the Kotlin version it targets |
| Dagger/Hilt | historically lags Kotlin releases — check the Dagger changelog and open issues for the target Kotlin before bumping Kotlin |
| Compose compiler | ships with Kotlin (`org.jetbrains.kotlin.plugin.compose`), so it follows Kotlin automatically |
| Compose BOM | check its release note for the minimum AGP and compileSdk it requires |
| AGP | check the Gradle wrapper version it needs |

**Kotlin is the constraint, not the goal.** If Hilt has not caught up, staying on the current
Kotlin is the correct outcome, not a failure. Say so rather than forcing it.

## Apply

1. Edit `gradle/libs.versions.toml` **only**. Never inline a version in a build file.
2. Bump the `[versions]` entry, not the individual library entries.
3. `./gradlew test` — all of it, not one module.
4. `./gradlew assembleDiagDebug assembleFullDebug`.
5. If anything is version-sensitive or surprising, note the date checked next to the entry:
   `# checked 2026-08-28, latest stable`.

## Report

State the version you moved from and to, where you resolved it, and anything you deliberately
did **not** bump and why. If you could not verify a compatibility claim, say that plainly
instead of assuming it works.
