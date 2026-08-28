---
paths:
  - "feature/**"
  - "app/**"
  - "core/designsystem/**"
---

# Compose and UDF rules

## The screen shape

Every screen is exactly this, in one file named `XScreen.kt` plus one named `XViewModel.kt`:

```kotlin
// XViewModel.kt — state, actions and the view model live together
data class XUiState(
    val connection: ConnectionState = ConnectionState.Idle,
    val items: ImmutableList<Item> = persistentListOf(),
) {
    val canSend: Boolean get() = connection is ConnectionState.Ready
    val visible: List<Item> get() = items.filter { it.isCandidate }
}

sealed interface XAction {
    data class Select(val id: String) : XAction
    data object Clear : XAction
}

@HiltViewModel
class XViewModel @Inject constructor(private val controller: ClusterController) : ViewModel() {
    private val selected = MutableStateFlow<String?>(null)

    val uiState: StateFlow<XUiState> = combine(controller.state, selected) { s, sel -> ... }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), XUiState())

    fun onAction(action: XAction) = when (action) { ... }
}
```

```kotlin
// XScreen.kt
@Composable
fun XRoute(viewModel: XViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    XScreen(state, viewModel::onAction)
}

@Composable
fun XScreen(state: XUiState, onAction: (XAction) -> Unit, modifier: Modifier = Modifier) { ... }
```

Rules that fall out of it:

- **Derived state is a computed `val … get()` on the UiState.** No mapper layer, no second state
  class, no `derivedStateOf` in the composable for something the state can compute.
- **UiState is a data class, never sealed.** Domain state machines (`ConnectionState`) are sealed;
  the UI state that contains them is not — a sealed UiState forces every consumer to re-branch on
  loading/error and loses the fields that stay valid across those transitions.
- **One `MutableStateFlow` per piece of local UI state.** Never a single `_uiState` that gets
  `copy`-ed; that reintroduces the read-modify-write race `combine` exists to avoid.
- **When more than five flows need combining**, nest a `combine` producing a private local
  `Quad`/`Pair` type declared in the same file.
- `XRoute` is the only composable allowed to touch Hilt. `XScreen` takes state and a callback and
  nothing else, so it is previewable and testable.
- Sub-composables are `private fun` in the same file, suffixed by what they are: `Card`, `Row`,
  `Tile`, `Grid`, `Heading`.
- Collections in state are `ImmutableList`/`persistentListOf` from `kotlinx-collections-immutable`.
- Use plain methods instead of a sealed action only when a screen has **fewer than four**
  interactions (`LogViewModel`, `InspectViewModel` are the existing precedents).

## Never import Material directly from a feature

<!--
Failure: once features import androidx.compose.material3 directly, the design system becomes
advisory. Restyling means finding every call site, and the app drifts back toward stock Material
one screen at a time.
Why: a single choke point is the only thing that makes a token change actually global.
Outcome: convention only for now - the diag screens predate the rule and still import
material3 directly. They get migrated when RideConnectTheme lands, and at that point material3
comes out of AndroidComposeConventionPlugin so the compiler enforces it instead of a reviewer.
-->

Features import from `core:designsystem` only. `RideConnectTheme` and the `Rc*` components are
the public surface; `androidx.compose.material3.*` is not. See `docs/DESIGN-SYSTEM.md`.

No raw `Color(0xFF…)`, no bare `.dp` spacing literals outside the design system, no
`MaterialTheme.colorScheme` in feature code — use `RcColors`, `RcSpacing`, `RcType`, `RcShape`,
`RcMotion`.

## Navigation 3

The `full` flavour uses `androidx.navigation3` (`navigation3-runtime`, `navigation3-ui`).
`diag` keeps its existing string-route `NavHost` until someone has a reason to touch it.

- Destinations are `@Serializable` `NavKey` objects/data classes, declared in the feature module
  that owns them, so the key carries its arguments with types.
- The back stack is state you own (`rememberNavBackStack`) and is hoisted at the app level —
  this is the reason we chose Nav3, so don't hide it behind a wrapper that makes it opaque again.
- Each feature module exposes an `EntryProviderBuilder.xEntry(onNavigateTo…)` extension. The app
  module composes them; **feature modules never depend on each other.**
- Shared-element transitions go through `SharedTransitionScope` on `NavDisplay`.

## Composition and correctness

- `collectAsStateWithLifecycle()`, never `collectAsState()`, for anything backed by a flow that
  costs something upstream.
- Every public composable takes `modifier: Modifier = Modifier` as its first optional parameter.
- Never nest scrollables in the same axis — a `LazyColumn` inside a `Column` with
  `verticalScroll` throws at runtime, not compile time.
- Add `@Preview` for every design-system component and every `XScreen`. There is currently
  exactly one preview in the whole repo; that is a gap, not a convention.
- Every screen gets a Compose UI test. The dependencies are already wired by
  `AndroidComposeConventionPlugin` and are currently entirely unused.
