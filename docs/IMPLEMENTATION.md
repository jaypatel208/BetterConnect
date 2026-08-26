# Implementation guide

How to build a cluster client well on modern Android. This is a reference, not a plan.

`PROTOCOL.md` is the wire contract, `CONNECTION.md` the link contract, `SIGNALS.md` the
scope. This document is about **technique**, and its guiding rule is:

> Copy the vendor's **constraints**. Never copy their **technique**.

Where the vendor worked around a limitation, the limitation is real and binding — reproduce
it. The workaround usually is not, and several of theirs are outright defects. Those are
called out so nobody reimplements them by accident.

---

## 1. What the vendor's code actually is

Stated plainly, because it is the reference implementation and it will be read:

- A **React Native bridge** where the JS layer builds navigation frames and native Java does
  everything else. Two implementations of the same protocol that already disagree — the
  distance threshold is `< 999` in one and `< 1000` in the other, text is capped at 31 in
  one and 32 in the other `[js]` `[dex]`.
- **`GlobalVar`**: dozens of public static mutable fields, written from BLE callback threads,
  timer threads and the main thread with no synchronisation.
- **`characteristic.setValue(payload)` then `gatt.writeCharacteristic(characteristic)`** —
  deprecated since API 33, and *stateful*: the payload lives on the shared characteristic
  object, so two queued writes to the same characteristic overwrite each other's data
  before either is sent.
- **A hand-rolled `Queue<PendingWrite>`** drained by callbacks, with a **3-second stall
  watchdog** on a separate 700 ms timer that re-issues the queue head if nothing completed.
  The watchdog exists because the queue has no timeouts of its own.
- **`isMTUIncreased`**, a global boolean gate, plus a `Handler.postDelayed(2500)` that
  force-sets it if the callback never fires.
- Three unsynchronised timers: 700 ms reads, 800 ms navigation, 1000 ms status.

None of that is necessary. All of it is replaceable by a coroutine actor and a state
machine, which is what the rest of this document describes.

## 2. GATT layer

### One suspending operation at a time

BLE permits exactly one outstanding GATT operation per connection. The vendor enforces this
with a queue plus a watchdog. A coroutine `Mutex` plus `withTimeout` does the same job with
no watchdog, because **the timeout is the watchdog**:

```kotlin
private val gattMutex = Mutex()

private suspend fun <T> gattOp(
    timeout: Duration = 5.seconds,
    block: (CompletableDeferred<T>) -> Boolean,
): T = gattMutex.withLock {
    withTimeout(timeout) {
        val result = CompletableDeferred<T>()
        check(block(result)) { "GATT operation rejected by the stack" }
        result.await()
    }
}
```

Each callback completes the pending `CompletableDeferred`. A stalled operation throws
`TimeoutCancellationException` and releases the mutex — no separate recovery timer, no
queue to leak.

### Never use the stateful write API

```kotlin
// API 33+: payload is a parameter, not shared mutable state on the characteristic
gatt.writeCharacteristic(characteristic, payload, writeType)

// Below 33, the deprecated path is unavoidable - keep it isolated and serialised
@Suppress("DEPRECATION")
characteristic.also { it.writeType = writeType; it.value = payload }
    .let(gatt::writeCharacteristic)
```

Serialising through the mutex is what makes the legacy branch safe. The vendor's queue does
not serialise the `setValue` itself, only the write call.

### Choose the write type from the characteristic

Do not hardcode it. `CONNECTION.md` §4 has the rule; on this cluster every writable
characteristic is `WRITE`-only, so all of them need **Write Request**.

```kotlin
fun writeTypeFor(c: BluetoothGattCharacteristic): Int = when {
    c.properties and PROPERTY_WRITE != 0 -> WRITE_TYPE_DEFAULT
    c.properties and PROPERTY_WRITE_NO_RESPONSE != 0 -> WRITE_TYPE_NO_RESPONSE
    else -> WRITE_TYPE_DEFAULT
}
```

### Keep the connection logic pure

Android's `BluetoothGattCallback` fires on a binder thread. Treat it as a **translator**:
callbacks become events, a pure state machine decides, the adapter executes commands.

```kotlin
sealed interface LinkEvent { /* Connected, MtuNegotiated, ServicesResolved, ... */ }
sealed interface LinkCommand { /* RequestMtu, DiscoverServices, StartReadPump, ... */ }

fun reduce(state: ConnectionState, event: LinkEvent): Pair<ConnectionState, List<LinkCommand>>
```

Everything in `CONNECTION.md` §3 — the ordering, the 300 ms BALANCED window, the MTU gate,
the 2500 ms fallback, reconnect backoff — becomes testable on virtual time with no device
and no Robolectric. This pattern is already proven in the diagnostic build.

### State is immutable and single-owner

One `StateFlow<ClusterState>` replacing `GlobalVar`. Callback threads send events; only the
state machine mutates. This removes an entire category of race that the vendor's code has
by construction.

## 3. Cadence

Three periodic jobs, all cancellable with the connection scope:

| Job | Interval | Why |
|---|---|---|
| `CONTROL` read | 700 ms | return channel; also the leading keep-alive candidate |
| `TBT_INFO` write | 800 ms | re-send cached frame; native cadence `[dex]` |
| `GENERAL` write | 1000 ms | status + acks + heartbeat byte |

Re-send the **cached** frame on tick even when nothing changed. The cluster has no retry and
no acknowledgement, so repetition is the only recovery mechanism. Never send deltas.

The 350 ms figure from the JS layer is not the real cadence — 800 ms is `[dex]`.

Run all of this in a **foreground service of type `connectedDevice`** with a wake lock.
Sub-second timers do not survive Doze.

## 4. Navigation

### Source: Routes API plus your own guidance loop

Fetch the route from Google's Routes API, then track position along it yourself: snap to the
polyline, advance the active step, compute exact distance to the next manoeuvre, detect
off-route and re-fetch.

This is more work than scraping the Maps notification, and it is the only option that
actually fixes the wrong arrows, because it yields:

- **exact distances in metres** — notification text is pre-rounded, which makes the 100 m
  blink threshold meaningless;
- **a real manoeuvre enum** — no locale-dependent string matching;
- **roundabout exit numbers**, which the notification does not carry at all.

Text matching is precisely the technique that produces the stock app's bad arrows. Do not
reintroduce it at the source layer.

### Mapping manoeuvres

`MANEUVERS.md` §4 has the vendor's Google mapping. Use it as a starting point, not as
truth — and specifically do not copy these three defects `[dex]`:

- **Ramps flattened to plain turns.** `K` and `L` exist for ramps and the vendor leaves them
  unused.
- **`WRONG_WAY` as the fallback.** Any unmapped manoeuvre tells the rider they are going the
  wrong way. Hold the previous icon or send `G` instead. This is a safety issue.
- **Silent downgrade of unknown codes.** If a manoeuvre cannot be classified, log it. An
  unmapped manoeuvre is a bug to fix, not an arrow to guess.

Six icon codes are actively disputed between sources (`MANEUVERS.md` §1). Until the sweep
resolves them, prefer a confirmed neighbour over a plausible guess.

### Ending navigation

Send the native end frame — byte 0 with **bit 0 cleared** — rather than an all-zero buffer.
It is an explicit "navigation inactive" signal. See `PROTOCOL.md` §4.

## 5. The return channel

Read `CONTROL` every 700 ms and decode it even if you act on nothing. It is likely required
to hold the link, and it costs one poll.

Reproduce two behaviours exactly, because both prevent phantom actions `[dex]`:

- **Whole-frame dedup** — if all 20 bytes match the previous read, do nothing.
- **Bootstrap adoption** — on the first frame after connect, adopt the cluster's values
  without acting. Dial additionally requires passing through `dialSource == 0` before
  re-arming, so a retained value cannot auto-dial on reconnect.

Do **not** reproduce the vendor's `launchMediaPlayer` handling: it compares against a mirror
that is only ever set during bootstrap, so the ack increments on every distinct frame
forever, and no media player is ever launched `[dex]`. Their bootstrap also forces the three
call-request bytes to zero rather than adopting them, so a cluster reconnecting with a
non-zero call byte fires that action immediately.

Acknowledge in `GENERAL` bytes 4–17, respecting the two ack styles (mirror vs counter) in
`PROTOCOL.md` §5.

## 6. Permissions

```
BLUETOOTH_SCAN            API 31+, neverForLocation
BLUETOOTH_CONNECT         API 31+
ACCESS_FINE_LOCATION      API 30 and below, for scanning
ACCESS_FINE_LOCATION      always, if you do your own guidance loop
FOREGROUND_SERVICE
FOREGROUND_SERVICE_CONNECTED_DEVICE
POST_NOTIFICATIONS        API 33+
```

Navigation needs no call, contact, SMS or notification-listener permission. Keep it that
way for as long as possible — `SIGNALS.md` §5 covers what adding them would cost.

Re-check `BLUETOOTH_CONNECT` before GATT operations on API 31+; a revoked permission
otherwise throws from a callback thread.

## 7. Testing

The link has no acknowledgement, so a wrong frame and a dropped frame are indistinguishable
on the bike. Bike time is the scarce resource, so make every failure mode reachable on a
laptop. Three things do most of the work:

1. **Write a decoder alongside every encoder.** Turns "is the packet right?" into an
   exhaustive round-trip sweep instead of a question only hardware can answer.
2. **Keep the state machine pure.** Connection ordering, MTU gating, write serialisation and
   reconnect backoff all test on virtual time.
3. **Render the decoded outgoing bytes**, not the state that produced them. A packing
   mistake becomes visible on a desk.

Plus a fake transport that decodes what it receives and can inject each real failure: MTU
too small, characteristic missing, write rejected, mid-session disconnect, stalled callback.

This is proven — it is how the diagnostic build was validated before the first bike session,
and the protocol worked first time.

## 8. Things that will bite

- **One central at a time.** Force-stop the official app. Two clients with auto-connect look
  exactly like random disconnects.
- **The 65-second drop** is unresolved (`CONNECTION.md` §6). Satisfy all four candidates
  before concluding anything.
- **Feature flags are policy; the GATT table is truth.** Three characteristics the protocol
  defines are absent on this cluster. Enumerate at runtime and degrade.
- **Endianness is not consistent.** TBT distances are little-endian; `MISSED_CALL` and
  `ALERTS` timestamps are big-endian.
- **Battery in `GENERAL` is a 2-bit level (0–3)**, not a percentage, despite the vendor's
  variable being called `batteryPercentage`.
- **Clear the display on stop**, or the last instruction stays frozen indefinitely.
- **Never write to `f000ffd0`** — TI firmware update. It can brick the cluster.
