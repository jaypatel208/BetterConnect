# Cluster link — connection contract

How a phone establishes and keeps a BLE link to the Bajaj instrument cluster.

**Evidence tags** — every claim is tagged:
`[hardware]` observed on the bike · `[dex]` native Java in `classes3.dex` ·
`[js]` the React Native bundle · `[inferred]` reasoned, not verified.

This document describes **what the cluster requires**. Where the official app's technique
is worth knowing it is quoted as evidence, and where it is bad practice that is said
plainly — we copy constraints, never technique. See `IMPLEMENTATION.md` for how to build
this well.

---

## 1. Link model

The cluster is a **BLE GATT peripheral** on a **Texas Instruments CC26xx** `[hardware]`
(vendor UUIDs carry `0451`, TI's identifier). The phone is central.

This is not Bluetooth Classic and not A2DP/HFP, which is why the cluster never appears as a
connected device in Android's Bluetooth settings and why it "only works through their app":
a BLE GATT connection is owned by the app process, established with `connectGatt`, and dies
with the app. Nothing to pair in the usual sense.

Your bike may *also* hold a separate Classic link for call audio and media. That is
independent of everything in this document.

**The link is bidirectional** `[dex]` `[hardware]`. An earlier draft of this document
claimed it was write-only. That was wrong, and it was wrong because it was written from the
JS layer, which only drives navigation. The cluster talks back on `CONTROL` — see §5.

### No bonding, no authentication

- `createBond` is never called by the app `[dex]` `[js]`.
- No challenge/response, no pairing token, no key exchange gates writes `[dex]`.
- Writes succeeded on first connection with no bond `[hardware]`.

## 2. Discovery

**No service-UUID scan filter.** The app scans unfiltered and matches on the **advertised
device name**, case-insensitively by substring `[js]`:

```
name.lowercase() contains "pulsar" | "freedom" | "dominar"
```

Native additionally derives the vehicle type from the GATT device name on connect
(`VehicleType.fromDeviceName`) `[dex]`.

Do not filter the scan by service UUID: there is no evidence the cluster advertises it, and
a filtered scan that finds nothing is indistinguishable from a cluster that is switched off.

Cache the MAC after the first successful connection and reconnect to it directly.

## 3. Connect sequence — mandatory ordering

This is the contract. Deviating from it is what produces the 65-second drop in §6.

```
connectGatt(autoConnect = false, TRANSPORT_LE)
  │
  ├─ onConnectionStateChange(STATE_CONNECTED)                     [dex]
  │     requestConnectionPriority(BALANCED)      ← API 35+; never HIGH, see §7
  │     wait 300 ms                              ← "BALANCED renegotiation window"
  │     discoverServices()
  │     … and at connect + 1200 ms: start the CONTROL read pump (§5)
  │
  ├─ onServicesDiscovered                                          [dex]
  │     bind characteristic handles
  │     requestMtu(256)
  │     clear read and write queues
  │     arm a 2500 ms fallback that force-enables writes if onMtuChanged never fires
  │
  └─ onMtuChanged                                                  [dex]
        isMTUIncreased = true        ← WRITES ARE BLOCKED UNTIL THIS POINT
        push initial status packet
```

**Writes are MTU-gated** `[dex]`. The vendor's `prepareCharAndWrite` refuses outright:

```
if (!GlobalVar.isMTUIncreased) { log "skipping write because MTU not increased"; return; }
```

Requesting 256 is not arbitrary caution — a 48-byte navigation frame needs an ATT MTU of at
least 51, and the larger packets need more. Observed negotiated MTU on this cluster is
**247** `[hardware]`, so there is ample headroom once the exchange completes.

## 4. Write type — chosen per characteristic, not fixed

The vendor selects the write type from the characteristic's own advertised properties
`[dex]`:

```java
return (isPlaylistChar || (properties & PROPERTY_WRITE) != 0
                       || (properties & PROPERTY_WRITE_NO_RESPONSE) == 0)
       ? WRITE_TYPE_DEFAULT      // Write Request, acknowledged at ATT level
       : WRITE_TYPE_NO_RESPONSE; // Write Command, fire and forget
```

| Characteristic advertises | Write type used |
|---|---|
| `WRITE` only | **Write Request** (`WRITE_TYPE_DEFAULT`) |
| `WRITE` and `WRITE_NO_RESPONSE` | **Write Request** |
| `WRITE_NO_RESPONSE` only | Write Command |

**On this cluster every writable characteristic advertises `WRITE` and not
`WRITE_NO_RESPONSE`** `[hardware]`. Therefore the correct write type for all of them is
**Write Request**.

This matters. A Write Command sent to a characteristic that only supports Write Request has
undefined handling — some land, some are discarded, and there is no error surfaced to the
application. That is consistent with the "signal fading away" seen in the field
`[hardware]`.

Do not hardcode a write type. Read the properties and choose, exactly as above.

## 5. The return channel — `CONTROL`

`0A10676e-6972-6565-6e69-676e4543544f`, 20 bytes, cluster → phone. This is how the rider's
cluster button presses reach the phone. Payload layout is in `PROTOCOL.md`.

The vendor tries notifications first, then falls back to polling `[dex]`:

- `setCharacteristicNotification(CONTROL_CHAR, true)` plus a CCCD descriptor write.
  It logs `CONTROL CCCD descriptor missing` when the descriptor is absent.
- **A `scheduleAtFixedRate` read of `CONTROL` every 700 ms**, started 1200 ms after connect
  and running for the whole session.

**On this cluster `CONTROL` is `[READ]` with no `NOTIFY` property** `[hardware]`, so
notifications are unavailable and **the 700 ms polled read is the only option**.

The same 700 ms tick doubles as a write-stall watchdog: if no write has completed in 3000 ms
and the queue is non-empty, it re-issues the head of the queue `[dex]`.

## 6. The 65-second disconnect

Reproduced across two independent sessions `[hardware]`:

```
session 1    65.689 s   65.305 s   65.423 s      all GATT status 8
session 2    65.572 s   65.348 s                 all GATT status 8
```

Five occurrences inside a 0.4 s spread. This is a timer, not radio conditions.

### Is it the cluster, or is it our client?

A reasonable suspicion, since the official app holds the link indefinitely on the same
hardware. The evidence available so far points at the cluster, on three grounds:

1. **The status code distinguishes the two cases.** When our own client tears down a link it
   produces **status 0** — visible in the log at the moment the user pressed Connect and the
   old GATT was closed. Every 65 s drop is **status 8**, `GATT_CONN_TIMEOUT`, which the
   Android stack reports when the *link* dies, not when the application closes it.
2. **No 65 s timer exists in our client.** Its only timers are a 350 ms write heartbeat and
   a 3 s reconnect backoff.
3. **Write volume is irrelevant to it.** In session 2 the first window carried a single
   frame in 65 s (one-shot mode, `sent` went 1 → 2 across 40 s); a later window carried a
   frame every 350 ms. Both dropped at the same 65 s. A client-side fault driven by
   traffic would not behave identically at 0.02 and 3.5 writes per second.

Two constraints bound the diagnosis further: **BLE's maximum supervision timeout is 32 s**,
so this cannot be a link-layer timeout configured by either side; and one drop occurred
mid-write, so it is not a write-inactivity timeout.

This is evidence, not proof. It is **unresolved**.

### A real defect in our diagnostic client, found while investigating

`ClusterService` — the foreground service of type `connectedDevice` — **is written but never
started.** Nothing calls `ClusterService.start()`. The client therefore holds its GATT link
from a plain application scope with no foreground service and no wake lock.

That is a genuine bug and must be fixed. It is unlikely to be *this* bug: the drops occurred
with the screen on and the app in the foreground, where Doze does not apply. But it should
be eliminated before the cause is declared cluster-side.

### What the official app does that ours does not

The ranked candidate list, unchanged by the new data:

1. **It never reads `CONTROL`.** The 700 ms read pump is the only continuous traffic the
   official app generates, and it runs regardless of navigation state. A TI peripheral that
   never sees its controller poll may reasonably treat the client as dead.
2. **It never sends `GENERAL`**, so the cluster never sees the incrementing heartbeat byte.
3. **It writes with the wrong write type.** Every writable characteristic here advertises
   `WRITE` only, so Write Request is correct; our client sends Write Commands (§4).
   Worth noting: the writes still land — icons change — so the peripheral tolerates them.
4. **No foreground service** (above), and no explicit connection priority.

Fix all four, confirm the link holds, then bisect. Until then, no single cause is
established.

## 7. `CONNECTION_PRIORITY_HIGH` causes status 8

The vendor's own code carries this `[dex]`:

```
onServicesDiscovered: keeping CONNECTION_PRIORITY_BALANCED
                      (API 35+ — avoid HIGH renegotiation / status=8 loop)
```

They request `BALANCED` on connect for API 35+, wait 300 ms, and deliberately do **not**
escalate to `HIGH` after discovery. On API < 35 they do request `HIGH` (priority `1`).

Use `BALANCED`. The 350 ms navigation cadence does not need a faster connection interval.

## 8. One central at a time

A BLE peripheral typically accepts one connection, and on Android the GATT link is held per
process. While the official Bajaj app is connected, another app cannot connect, and vice
versa `[inferred]`, consistent with field behaviour.

- Force-stop the official app before connecting.
- Expect the first attempt after switching apps to fail while the previous link tears down.
- Never run two clients with auto-connect enabled; the symptom is indistinguishable from
  random disconnects.

## 9. Reconnection

The vendor schedules an auto-reconnect on every unexpected drop, tagged with the GATT status
(`scheduleAutoReconnect("gatt_drop_status_8")`), tracks `reconnectAttempt`, and cancels any
pending attempt on a successful connect `[dex]`. On disconnect it clears `isMTUIncreased`,
empties all queues, resets the CONTROL bootstrap state, and releases its wake lock.

A reconnect must repeat §3 in full. MTU and notification state do not survive a drop.

## 10. Staying alive in the background

<!--
Failure: this section originally said "any real client needs" a CPU wake lock, copied from
the vendor's own technique (`[dex]`: a wake lock held for "keeps Timer + BLE writes alive
during Doze"). Copying the vendor's constraint (staying alive) by copying their technique
(a wake lock) is exactly the mistake CLAUDE.md warns against.
Why: Google's excessive-wake-locks Play vital went to enforcement 2026-03-01, measured while
an app is running a foreground service - i.e. for the whole time this app is connected. A
one-hour ride would trip it.
Outcome: no wake lock. It also turns out to be unnecessary - see below.
-->

**Do not hold a wake lock.** The vendor's own technique - a CPU wake lock justified by
`keeps Timer + BLE writes alive during Doze` `[dex]` - is now a Play Store violation to
copy: the *excessive wake locks* vital went to enforcement on 2026-03-01, and it is measured
while the app is running a foreground service, which for this app is the entire time it is
connected.

It is also unnecessary. A foreground service of type `connectedDevice` (with `location` added
once the guidance loop runs in it) keeps the app in the *active* standby bucket, and the
Android 15 six-hour foreground-service timeout applies only to `dataSync`/`mediaProcessing` -
`connectedDevice` and `location` have none through API 37. A moving bike does not enter Doze.
What actually matters:

- `START_REDELIVER_INTENT`, not `START_STICKY` - a sticky restart redelivers a null Intent,
  which for a link-holding service means restarting with nothing to reconnect to.
- No `ACCESS_BACKGROUND_LOCATION` - a `location` foreground service started from a visible
  Activity keeps while-in-use location with the screen off; declaring the background
  permission only invites a Play review for access the service never needed.
- Route/ride state persisted, so an OEM background-kill (Samsung/Xiaomi/OnePlus/Huawei all
  score worst-case on this, and there is no known developer-side fix) is recoverable rather
  than fatal.

## 11. Permissions

```
BLUETOOTH_SCAN      (API 31+, neverForLocation if you don't derive location from scans)
BLUETOOTH_CONNECT   (API 31+)
ACCESS_FINE_LOCATION (API 30 and below — required for BLE scanning)
FOREGROUND_SERVICE
FOREGROUND_SERVICE_CONNECTED_DEVICE
POST_NOTIFICATIONS  (API 33+)
```

The vendor re-checks `BLUETOOTH_CONNECT` before every GATT operation on API 31+ `[dex]`;
a revoked permission mid-session otherwise throws from the callback thread.

## 12. Open questions

- Which of the four candidates in §6 actually causes the 65 s drop. **Open.**
- Whether the cluster tolerates a slower CONTROL poll than 700 ms, and what the real
  deadline is. **Open.**
- Whether `CONTROL` exposes `NOTIFY` on any other cluster firmware, making polling
  unnecessary. **Open.**
