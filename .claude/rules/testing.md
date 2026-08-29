---
paths:
  - "**/src/test/**"
  - "**/src/androidTest/**"
  - "**/*Test.kt"
  - "core/testing/**"
---

# Testing rules

<!--
Failure: the cluster never acknowledges a write, so a wrong frame and a dropped frame are
indistinguishable from the phone. The only observable output is the cluster's own display.
Why: bike time is the scarce resource. Every failure mode reachable on a laptop is a trip saved.
Outcome: 115 tests; the encoder was proven byte-identical to the vendor's before the first ride.
-->

## Non-negotiable

**The test ships in the same change as the behaviour.** Not afterwards.

## Style — match it exactly

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class XViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `tapping a letter sends that exact byte as the icon`() = runTest {
        val h = TestHarness(backgroundScope)
        val vm = XViewModel(h.controller)
        runCurrent()

        vm.onAction(XAction.SendLetter('K'))

        assertEquals('K', h.transport.lastDecoded!!.symbolChar)
    }
}
```

- **Backticked lowercase sentences** describing the behaviour, not
  `testSendLetter_returnsK`. The sentence is the spec.
- **A per-test `Fixture` / `TestHarness`** wiring the *real* collaborators with one fake at the
  boundary. No `@Before`, no field-level SUT — build it inside each `@Test`, for isolation.
- `runTest { }` as the expression body. Virtual time via `runCurrent` / `advanceTimeBy` /
  `advanceUntilIdle`. Never `Thread.sleep`, never a real delay.
- **Hand anything holding a long-lived collector `backgroundScope`**, not the `TestScope` —
  otherwise `runTest` waits forever for a coroutine that is never meant to finish.

<!--
Failure: GuidanceControllerTest used advanceUntilIdle() after pushing a fix through a fake
push-based Flow (MutableSharedFlow.tryEmit) into a backgroundScope collector. The collector
never ran - confirmed with a bare MutableSharedFlow reproduction, no GuidanceController
involved. runCurrent() in the same spot worked immediately.
Why: advanceUntilIdle() does not reliably resume a backgroundScope-launched collector woken by
an external, non-delay trigger. The delay-driven schedulers elsewhere (WriteScheduler,
ControlPump, GeneralScheduler) never hit this because delay() is what advanceUntilIdle/
advanceTimeBy are built to drive; tryEmit isn't.
Outcome: after emitting into any push-based fake Flow (a SharedFlow-backed fake source, not a
delay loop) collected on backgroundScope, call runCurrent(), not advanceUntilIdle(). If a test
starts failing with the collector simply never having run, this is the first thing to check.
-->
- **After `tryEmit`-ing into a push-based fake `Flow`** (a `SharedFlow`-backed fake location/
  sensor source, not a `delay`-loop scheduler) collected on `backgroundScope`, call
  `runCurrent()`, not `advanceUntilIdle()` — the latter does not reliably wake that collector.
- `MainDispatcherRule` via `@get:Rule` in ViewModel tests.
- JUnit 4 with plain `org.junit.Assert.*`. Prefer the message-first overload when the failure
  would otherwise be cryptic.
- KDoc on any test whose *reason for existing* is not obvious from its name.

## Fakes, never mocks

<!--
Failure: n/a — this is a preserved property, not a fix. There is no MockK or Mockito anywhere in
the dependency graph.
Why: a fake that decodes what it receives can assert on meaning ("the cluster was told to turn
left at 250 m"); a mock can only assert that a method was called with some bytes.
Outcome: holding. Do not add a mocking library.
-->

Test doubles live in `:core:testing`, in its **main** source set, so production demo mode and
the test suite share exactly one implementation. `FakeClusterTransport` decodes every frame and
can inject each real failure: MTU too small, characteristic missing, write rejected, write
failed, mid-session disconnect, stalled callback.

Verification is **state-based** — inspect what the fake recorded and decoded. Not
interaction-based.

## Assert on decoded output

Never on raw bytes, never on the UI state that produced them. `assertEquals('E',
decoded.symbolChar)`, not a hex comparison. A packing mistake must be visible as a wrong
*instruction*, not as a diff of two byte arrays.

## Two hygiene rules paid for in flaky tests

<!--
Failure: A12 — a log test asserted on frame bytes without pinning the clock. It passed in the
morning and failed in the afternoon, because the ETA field encodes a 12-hour time.
Outcome: fixed. TbtEncoder(clock = TestClocks.TEN_AM) in every test that touches frame bytes.
-->
- **Pin the clock** in any test that asserts on frame content: `TbtEncoder(clock = TestClocks.TEN_AM)`.

<!--
Failure: A11 — the distance-boundary sweep stepped by 2 from an even start, so it skipped 999,
the exact value it existed to demonstrate.
Outcome: fixed. Boundary tests assert the boundary value explicitly, not via a stride.
-->
- **Boundary tests must hit the boundary**, by name, not by hoping a stride lands on it.

## Coverage

Kover reports on every module. Coverage is a signal for finding untested branches, not a number
to farm — a test written to move the percentage and asserting nothing is worse than the gap it
filled.

## Screenshot tests

Roborazzi on the JVM (via Robolectric) for design-system components and screens: light, dark,
`fontScale = 2.0`. Baselines are committed and a diff fails CI.
