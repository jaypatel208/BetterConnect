# Better Connect

Talks to the Pulsar N160 UG instrument cluster over BLE.

**The protocol is documented in [`docs/`](docs/)** — start with
[`docs/README.md`](docs/README.md).

Two flavours:

| Flavour | Application id | What it is |
|---|---|---|
| `diag` | `dev.jay.betterconnect.diag` | The diagnostic harness. Scan, connect, inspect the GATT table, fire individual symbols, run scripted sequences, read the frame log. |
| `full` | `dev.jay.betterconnect` | Placeholder. The navigation build starts once `diag` has confirmed the link on the bike. |

Both install side by side, and alongside the official Bajaj app.

## Why it is shaped this way

The cluster link has **no return channel**: it never notifies, never acknowledges, and
cannot be read. A wrong frame and a dropped frame look identical from the phone, and the
only observable output is the cluster's own display. Bike time is therefore expensive and
low-bandwidth.

So the goal is not "build a test app and go debug on the bike". It is **make every failure
mode reachable in a test on a laptop**, and leave the bike only the questions that are
genuinely physical. Three decisions carry that:

1. **A decoder exists alongside the encoder.** The protocol only needs encoding, but the
   inverse turns "is the packet right?" into an exhaustive round-trip sweep
   (`core/protocol`) instead of a question only the bike can answer.
2. **The GATT state machine is pure Kotlin.** `core/link` reduces events to states with no
   Android dependency, so MTU negotiation, write serialisation, the drop-don't-queue rule
   and reconnect backoff are all tested on virtual time.
3. **The virtual cluster renders the decoded outgoing bytes** — not the UI state that
   produced them. A packing mistake is visible on a desk.

Plus `FakeClusterTransport`, which decodes every frame it receives and can inject each
real failure mode: MTU too small, characteristic missing, write failure, mid-session
disconnect, stalled callback. **Demo mode** in the app swaps it in at runtime, so every
screen can be driven with no hardware present — the same fake the test suite uses.

## Modules

```
build-logic/convention   Gradle convention plugins

core/model      [jvm]    NavState, Symbol, ConnectionState, DeviceInfo, GattDump
core/protocol   [jvm]    TbtEncoder, TbtDecoder, distance codec, checksum
core/link       [jvm]    ClusterLink state machine, WriteScheduler, transport interfaces
core/domain     [jvm]    sequence scripts + runner, DiagLog
core/testing    [jvm]    fakes, fixtures, MainDispatcherRule
core/ble        [android] BleScanner, BleClusterTransport - the only android.bluetooth code
core/data       [android] ClusterController, SwitchableTransport, DI
core/designsystem [android] theme, components, VirtualCluster

feature/connect  Link + Inspect
feature/signals  Signals + Sequence
feature/log      Log

app              NavHost, DI wiring, flavours, foreground service
```

The four `[jvm]` core modules have no Android SDK, so their tests run in milliseconds and
can be exhaustive. All the logic that can be wrong lives there.

## Build and test

```bash
./gradlew test                 # 115 tests across every layer
./gradlew assembleDiagDebug    # the diagnostic APK
./gradlew assembleFullDebug
```

`./gradlew test` green is the gate for going to the bike.

Requires JDK 25 (`JAVA_HOME=<android-studio>/jbr`). `minSdk` is 26; both permission paths
are implemented — `ACCESS_FINE_LOCATION` below API 31, `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`
at 31 and above.

## On the bike

Force-stop the official Bajaj app first — a BLE peripheral accepts one central at a time,
and two apps with auto-connect look exactly like random disconnects.

1. **Link** → scan → a `PULSAR…` candidate appears → connect.
2. **Inspect** → MTU ≥ 51 and *TBT_INFO present and writable*. If the characteristic is
   missing, stop; that finding is the result.
3. **Signals** → press `I` → a left arrow and `500m` on the cluster. The project is
   de-risked at this point.
4. Toggle one-shot vs heartbeat → does the display latch, or decay without repetition?
5. Sweep `A`–`Z` and record what each draws. This produces the definitive icon table for
   *this* cluster and settles the two questions the APK could not answer: `N` vs `U`
   roundabouts, and what symbol `Y` is.

Findings from step 5 belong back in `docs/MANEUVERS.md` §5.
