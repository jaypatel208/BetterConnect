# Better Connect

An Android app that drives a **Bajaj Pulsar N160 UG instrument cluster over BLE GATT**, giving
the rider turn-by-turn navigation on the dashboard. The protocol was reverse-engineered from the
vendor app `com.bajajconnect.rideapp` v1.11.1 and verified against real hardware.

Two product flavours:

| Flavour | Application id | State |
|---|---|---|
| `diag` | `dev.jay.betterconnect.diag` | Done. Scan, connect, inspect GATT, fire symbols, run sequences, frame log. |
| `full` | `dev.jay.betterconnect` | **Being built.** The navigation app. |

## Commands

```bash
./gradlew test                 # the gate — must be green before any hardware session
./gradlew ktlintCheck          # style; ktlintFormat to fix
./gradlew koverHtmlReport      # coverage
./gradlew assembleDiagDebug
./gradlew assembleFullDebug
```

Needs `JAVA_HOME=<android-studio>/jbr`. Configuration cache is on.

## `docs/` is the authority

<!--
Failure: rev 1 of the protocol docs was written from the vendor's JS bundle alone and recorded
inference as fact — it claimed the link had no return channel, when CONTROL (0A10) is polled
every 700 ms. That single false "fact" shaped the whole first architecture.
Why: an evidence tag forces the author to say how they know, so an inference can never be
mistaken for a hardware observation later.
Outcome: held since rev 2. Every correction since has been traceable to its source.
-->

Every factual claim about the hardware carries an evidence tag: `[hardware] [dex] [js]
[inferred]`. **An untagged assertion is a defect in the document.** Only `[hardware]` closes a
question. Start at `docs/README.md`.

Open questions and known defects live in `docs/DEVELOPMENT-NOTES.md` as a numbered tracker
(A1–A12, B1–B7, C1–C11, D1–D5). Cite the id when you touch related code.

## Architecture

Single-activity, Compose-first, unidirectional data flow. Multi-module, NiA-style, with all
build config in `build-logic/convention` — module build files stay 3–15 lines.

```
core/model    [jvm]   core/protocol [jvm]   core/link [jvm]   core/domain [jvm]   core/testing [jvm]
core/ble      [android]   core/data [android]   core/designsystem [android]
feature/connect   feature/signals   feature/log
app
```

Dependency direction, never reversed:
`feature:* → core:data → core:ble → core:link → core:protocol → core:model`

<!--
Failure: the vendor app kept all state in `GlobalVar`, dozens of public static mutable fields
written from BLE callback threads, timer threads and the main thread with no synchronisation.
Why: keeping the logic in Android-free JVM modules means the parts that can be wrong are
exhaustively testable in milliseconds, and bike time is the scarce resource.
Outcome: 115 tests, all on a laptop. The frame format was proven byte-identical to the vendor's
before the first ride.
-->

**The four `[jvm]` core modules must never gain an Android SDK dependency.** All the logic that
can be wrong lives there.

### The UDF contract

Every screen follows this shape. Deviating from it is what breaks consistency across sessions.

- `XUiState` — a **data class with defaults**. Anything derivable is a computed `val … get()`
  on the state class, never a separate mapper.
- `XAction` — a `sealed interface` of `data class`/`data object`, handled by one
  `fun onAction(action: XAction)` with an exhaustive `when`. Use plain methods only when a
  screen has fewer than four interactions.
- `uiState: StateFlow<XUiState>` built with
  `combine(...).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), XUiState())`.
  One `MutableStateFlow` per piece of local UI state — never a single `_uiState`.
- `XRoute(viewModel: XViewModel = hiltViewModel())` collects with `collectAsStateWithLifecycle()`
  and delegates to a stateless `XScreen(state, onAction, modifier: Modifier = Modifier)`.

DI is Hilt with KSP. Navigation in the `full` flavour is **Navigation 3** (`androidx.navigation3`).

## Testing is part of the change, not a follow-up

<!--
Failure: the link has no acknowledgement, so a wrong frame and a dropped frame are
indistinguishable from the phone. The only observable output is the cluster's own display.
Why: every failure mode that is reachable on a laptop is one that never costs a trip to the bike.
Outcome: the encoder was proven correct on a desk; the first ride tested only physical questions.
-->

- **Write the test in the same change as the behaviour.** Not afterwards, not "later".
- **Fakes, never mocks.** There is no MockK or Mockito in the graph and none should be added.
  Test doubles live in `:core:testing` (main source set, so demo mode shares them).
- **Write a decoder alongside every encoder** — it turns "is the packet right?" into an
  exhaustive round-trip sweep.
- **Assert on decoded protocol output**, not raw bytes and not the UI state that produced them.
- `./gradlew test` green is the gate for going to the bike.

## Dependency versions

<!--
Failure: version numbers recalled from training data are routinely months stale or mutually
incompatible (Kotlin/KSP/Dagger in particular have repeatedly broken across minor releases).
Why: resolving the version costs one lookup; a wrong pin costs a broken build and a bisect.
Outcome: the whole toolchain is on versions that were confirmed to exist and interoperate.
-->

**Never write a dependency version from memory.** Resolve it from the registry or release notes,
confirm the Kotlin ↔ KSP ↔ Dagger/Hilt ↔ AGP compatibility matrix, and record the date you
checked. Use the `bump-deps` skill.

Versions go in `gradle/libs.versions.toml` only — never inline in a build file.

## The rule the reverse-engineering taught us

> **Copy the vendor's constraints. Never copy their technique.**

Where the vendor worked around a limitation, the limitation is real. Their workaround usually
isn't — `GlobalVar`, the hand-rolled write queue and the 3-second stall watchdog are all
replaced here by a coroutine actor over a pure state machine.

## Where the rest lives

Detailed rules load automatically when you open matching files, in `.claude/rules/`:
`protocol.md` (BLE and byte-level — read it before touching the transport), `compose-ui.md`,
`design-system.md`, `testing.md`, `gradle.md`.

Procedures are skills: `field-test` (the on-bike session protocol), `bump-deps`,
`new-feature-module`.

The visual language is specified in `docs/DESIGN-SYSTEM.md`.
