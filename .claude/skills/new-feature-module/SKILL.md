---
name: new-feature-module
description: Scaffold a new :feature module following the project's UDF conventions. Use when adding a new screen or feature area to the app.
---

# New feature module

## 1. Gradle

`settings.gradle.kts` — add `include(":feature:<name>")`.

`feature/<name>/build.gradle.kts`:

```kotlin
plugins { alias(libs.plugins.betterconnect.android.feature) }

android { namespace = "dev.jay.betterconnect.feature.<name>" }

dependencies { implementation(project(":core:data")) }
```

That is the whole file. The `betterconnect.android.feature` convention plugin already brings in
library + compose + hilt, `:core:model`, `:core:domain`, `:core:designsystem`, lifecycle,
`hilt-navigation-compose`, `kotlinx-collections-immutable`, and
`testImplementation(project(":core:testing"))`.

If the feature is diag-only, wire it in `app/build.gradle.kts` as
`"diagImplementation"(project(":feature:<name>"))`.

## 2. Source layout

`feature/<name>/src/main/kotlin/dev/jay/betterconnect/feature/<name>/` — note `kotlin`, not
`java`.

Two files:

- `<X>ViewModel.kt` — holds `<X>UiState`, `sealed interface <X>Action`, `@HiltViewModel class
  <X>ViewModel`, and any local config type. Do not split these into subpackages.
- `<X>Screen.kt` — `<X>Route` (stateful, `hiltViewModel()`) and `<X>Screen` (stateless), plus
  `private fun` sub-composables.

Follow the shapes in `.claude/rules/compose-ui.md` exactly: data-class UiState with derived
`val … get()`, one `MutableStateFlow` per piece of local state,
`combine(...).stateIn(viewModelScope, WhileSubscribed(5_000), <X>UiState())`, one `onAction`
with an exhaustive `when`.

## 3. Navigation

Declare a `@Serializable` `NavKey` for each destination in this module, and expose an
`EntryProviderBuilder.<x>Entry(onNavigateTo…: (…) -> Unit)` extension. The app module composes
it. **Never depend on another feature module** — navigation callbacks go out through parameters.

## 4. Tests, in the same change

`feature/<name>/src/test/kotlin/.../<X>ViewModelTest.kt` with a `TestHarness` wiring the real
controller/scheduler/encoder and only the transport faked, plus `MainDispatcherRule`. And a
Compose UI test for `<X>Screen`.

## 5. Verify

```bash
./gradlew :feature:<name>:test
./gradlew assembleDiagDebug assembleFullDebug
```
