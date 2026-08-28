---
paths:
  - "**/build.gradle.kts"
  - "**/settings.gradle.kts"
  - "gradle/libs.versions.toml"
  - "gradle.properties"
  - "build-logic/**"
---

# Build rules

## Versions are resolved, never recalled

<!--
Failure: versions written from training data are routinely months stale or mutually
incompatible. Kotlin/KSP/Dagger in particular have broken across minor releases — Kotlin 2.3.0
shipped before KSP and Hilt supported it.
Why: one lookup versus a broken build and a bisect.
Outcome: use the bump-deps skill, which resolves from the registry and checks the matrix.
-->

**Never write a version number from memory.** Resolve it from Maven metadata or the release
notes, verify the **Kotlin ↔ KSP ↔ Dagger/Hilt ↔ AGP** compatibility matrix, and note the date
checked. Use the `bump-deps` skill.

KSP changed scheme at 2.3.0: it is now versioned independently (`2.3.11`), no longer
`<kotlin>-<ksp>`. Do not reintroduce the old form. `ksp.useKSP2` is deprecated — KSP2 is the
default.

## The catalog is the only place a version lives

Every dependency and plugin goes in `gradle/libs.versions.toml`. **Never inline a coordinate or
a version in a build file.** Remove entries that stop being referenced.

## Module build files stay tiny

All shared configuration lives in `build-logic/convention`. A module build file is 3–15 lines:

```kotlin
plugins { alias(libs.plugins.betterconnect.android.feature) }
android { namespace = "dev.jay.betterconnect.feature.log" }
dependencies { implementation(project(":core:data")) }
```

If you find yourself adding `compileSdk`, a Java target, a test dependency or the Compose
compiler to a module, it belongs in a convention plugin instead. SDK levels are in
`build-logic/convention/src/main/kotlin/ProjectExtensions.kt` (`BuildConfig`), nowhere else.

Existing plugins: `android.application`, `android.library`, `android.compose`, `android.hilt`,
`android.feature`, `android.screenshot`, `jvm.library`, all prefixed `betterconnect.`.

Kover and ktlint are applied to every subproject from the root build file, not per module.

## Layering is enforced by the graph

`feature:* → core:data → core:ble → core:link → core:protocol → core:model`

- **The `[jvm]` modules — `model`, `protocol`, `link`, `domain`, `testing` — must never gain an
  Android dependency.** Applying `betterconnect.android.library` to one of them is a defect.
- Core modules use `api(project(...))` where the dependency is part of their surface; features
  use `implementation`.
- **Feature modules never depend on each other.** The app module composes them.

## Flavour separation

<!--
Failure: diag is a hardware harness that can fire arbitrary bytes at the cluster, including
codes with no meaning. Shipping any of it in the rider-facing build would put a "send raw frame"
path in a production app.
Why: the two builds have different risk profiles and different audiences.
Outcome: feature modules are wired with "diagImplementation"(project(...)) so full does not
link them at all.
-->

`diag` and `full` are product flavours on dimension `mode`, with separate source sets
(`app/src/diag`, `app/src/full`), separate `applicationId`, and their own `MainActivity`.

Diag-only feature modules are wired with `"diagImplementation"(project(...))`, never plain
`implementation`. Shared code goes in a `core:` module, not in `app/src/main`.

Both must build: `./gradlew assembleDiagDebug assembleFullDebug`.
