---
paths:
  - "core/protocol/**"
  - "core/link/**"
  - "core/ble/**"
  - "core/data/**"
---

# Cluster link rules

These are hardware findings, not preferences. Every one of them cost either a trip to the bike
or a session reading the vendor's dex. Source of truth is `docs/`; tracker ids in the comments
refer to `docs/DEVELOPMENT-NOTES.md`.

## Never

<!--
Failure: f000ffd0-0451-4000-b000-000000000000 is the TI CC26xx OTA firmware service, and
f000ffd1 is the only characteristic on the entire device advertising WRITE_NO_RESPONSE — which
makes it the one a naive "find something writable" loop is most likely to pick.
Why: an unintended write into an OTA image handler can leave the cluster unbootable. There is
no recovery path we control.
Outcome: never written. No cluster damaged across all field sessions.
-->
- **Never write to `f000ffd0-0451-4000-b000-000000000000`** (TI OTA firmware). It can brick the
  cluster. Leave the second vendor service `0020676e-…` (`1020`/`1120`) alone as well — the
  vendor app never touches it and we do not know what it does.

<!--
Failure: symbol sweep on hardware. 19 of 26 icon codes render; M N S T U W Y draw nothing — and
critically the cluster does NOT blank on an unknown code, it leaves the previous arrow up.
Why: a stale arrow that looks current is worse than no arrow. The rider acts on it at speed.
Outcome: docs/MANEUVERS.md §5. Safe set is I J E F Q R K L G H C D Z X.
-->
- **Never send an inert icon code — `M N S T U W Y`.** An unrecognised icon byte leaves the
  *previous* icon on screen. Sending a dud is worse than sending nothing.

<!--
Failure: B is WRONG_WAY in the vendor's enum, which makes it a tempting default. On this
cluster B draws a partial rotary arc.
Why: an unmapped manoeuvre would silently render as a roundabout. Safety issue.
Outcome: fallback is "hold the previous icon", or G (straight) if there is no previous.
-->
- **Never default to `B` for an unmapped manoeuvre.** Hold the previous icon, or send `G`.

<!--
Failure: the vendor's own source comment reads "keeping CONNECTION_PRIORITY_BALANCED
(API 35+ — avoid HIGH renegotiation / status=8 loop)".
Why: they hit it and worked around it. Constraint, not technique — so we copy it.
Outcome: not yet independently reproduced; we follow the vendor. Below API 35 they do use HIGH.
-->
- **Never request `CONNECTION_PRIORITY_HIGH`** on API 35+. Use `BALANCED`, then wait 300 ms
  before `discoverServices()`.

<!--
Failure: on this cluster every writable characteristic advertises WRITE only, and none advertise
WRITE_NO_RESPONSE. Diag build defect A2 hardcoded WRITE_TYPE_NO_RESPONSE.
Why: a Write Command sent to a Write-Request-only characteristic has undefined handling and the
stack surfaces no error — the frame just vanishes.
Outcome: A2 open. Derive the type; do not assume.
-->
- **Never hardcode the write type.** Derive it from `characteristic.properties`.

<!--
Failure: characteristic.setValue(payload) + gatt.writeCharacteristic(characteristic) is
stateful — two queued writes to the same characteristic overwrite each other's payload before
either is sent. Deprecated since API 33.
Why: our write cadence is sub-second on a shared characteristic; this races by design.
Outcome: use the three-arg gatt.writeCharacteristic(char, payload, writeType) on 33+, and
serialise the legacy branch through a mutex below 33.
-->
- **Never use the stateful write API.**
- **Never write before `onMtuChanged`.** Writes are MTU-gated. A 48-byte TBT frame needs ATT
  MTU ≥ 51; observed negotiated MTU is 247.
- **Never send deltas.** Always re-send the *cached* frame on tick. The cluster has no retry and
  no acknowledgement — repetition is the only recovery mechanism there is.

<!--
Failure: there is no evidence the cluster advertises its service UUID.
Why: a filtered scan that finds nothing is indistinguishable from a cluster that is switched off.
Outcome: unfiltered scan, case-insensitive substring match on "pulsar" | "freedom" | "dominar".
-->
- **Never filter the BLE scan by service UUID.**
- **Never run two clients against the cluster.** A BLE peripheral takes one central at a time and
  the symptom is indistinguishable from random disconnects. Force-stop the official Bajaj app.
- **Never maintain two encoders.** The vendor's JS/native split is exactly why `< 999` and
  `< 1000`, and 31 and 32, both exist in their code.

## Always

<!--
Failure: byte 12 bits 3-2 are a 2-bit GPS status field (gpsStatus shl 2), not a boolean.
Clearing them removed the entire navigation area from the display on hardware. Diag defect A8
sends it as a boolean.
Why: "searching" (value 2) sets bit 3, not bit 2 — a boolean encoding produces the wrong state.
Outcome: confirmed [hardware]. 0 = not active, 1 = active, 2 = searching.
-->
- **Always set the GPS field on every frame while navigating** — byte 12, `gpsStatus shl 2`.
- **Always send uppercase icons.** Blinking (lowercase, +0x20) is in the protocol but this
  cluster ignores it — `i` renders identically to `I`. Do not compute blink forms.
- **The metre/km threshold is `< 1000`** (native), not `< 999` (JS). Diag defect A6.
- **Byte 13 is `takeMeHomeAck`** — mirror the `CONTROL` request byte. It is not reserved. A7.
- **End navigation with the native frame**: byte 0 = `0x10` (bit 0 cleared), icon 0, distances 0,
  byte 12 = `0x01 or (gpsStatus shl 2)`, byte 13 = ack, byte 47 = checksum. **Not** 48 zero
  bytes. A9. And always clear on stop, or the last instruction stays frozen indefinitely.
- **Endianness is not uniform.** TBT distances are little-endian uint16 pairs;
  `MISSED_CALL`/`ALERTS` timestamps are **big-endian** uint32.
- **`GENERAL` battery is a 2-bit level 0–3**, not a percentage, despite being named
  `batteryPercentage`. Note v2 clamps to 0–3 and silently loses the top bar; v1 keeps all five.
  Do not copy the v2 clamp.
- **Re-check `BLUETOOTH_CONNECT` before every GATT operation** on API 31+. A revoked permission
  otherwise throws from the callback thread.
- **Feature flags are policy; the GATT table is truth.** Enumerate at runtime and degrade —
  `MEDIA_INFO`, `FAV_CONTACTS` and `RECENT_CALLS` are absent on this hardware.

## Cadences and connect ordering

```
connectGatt(autoConnect = false, TRANSPORT_LE)
  onConnectionStateChange(CONNECTED) -> requestConnectionPriority(BALANCED)
                                     -> wait 300 ms  (renegotiation window)
                                     -> discoverServices()
  onServicesDiscovered              -> bind handles; requestMtu(256); clear queues
                                     -> arm a 2500 ms fallback that force-enables writes
                                        if onMtuChanged never fires
  onMtuChanged                      -> writes unblocked; push initial status packet
  connect + 1200 ms                 -> start the CONTROL read pump
```

| Job | Period |
|---|---|
| `CONTROL` (`0A10`) read — the return channel, READ only, **no NOTIFY** | 700 ms |
| `TBT_INFO` (`0110`) re-send | 800 ms |
| `GENERAL` (`0210`) heartbeat | 1000 ms |

**The 350 ms figure is a JS-layer artefact and appears nowhere in native code — do not use it.**

A reconnect repeats the **full** sequence. MTU and notification state do not survive a drop:
clear the MTU flag, empty the queues, reset the `CONTROL` bootstrap, release the wake lock.

## `CONTROL` decoding

No sequence number — the cluster holds a *level* per byte and changes it on press; the phone
mirrors and fires on inequality. Two gates are mandatory:

1. **Whole-frame dedup** — if all 20 bytes match the previous read, skip the entire handler.
2. **Bootstrap adoption** — the first frame after connect adopts sticky values without acting.
   Dial requires passing through `dialSource == 0` before re-arming.

<!--
Failure: the vendor's bootstrap zeroes the three call-request bytes instead of adopting them,
which fires a phantom action on every reconnect.
Why: this is a technique to not copy, unlike the constraint above it.
Outcome: documented in docs/DEVELOPMENT-NOTES.md §C.
-->

Acks go in `GENERAL` bytes 4–17, **never** by writing to `CONTROL`. Two styles: mirror
(`callAccept`, `callReject`, `callRejectWithSms`, `skipToNext`, `skipToPrev`, `missedCallGet`,
`alertGet`) and free-running counter mod 256 (`resumeSong`, `pauseSong`, `stopSong`,
`launchMediaPlayer`). `missedCallGet`/`alertGet` are a drain-by-polling queue: each request pops
exactly one record, writes it, then sets the ack.

## Threading

`BluetoothGattCallback` fires on a binder thread. **Treat it purely as a translator.**

```
callback -> LinkEvent -> pure reduce(state, event): Pair<ConnectionState, List<LinkCommand>>
                      -> adapter executes commands
```

One outstanding GATT operation per connection, enforced by a `Mutex` + `withTimeout` —
**the timeout is the watchdog.** State is immutable and single-owner: one `StateFlow`, mutated
only by the state machine.

Everything runs inside a foreground service of type `connectedDevice` with a CPU wake lock. A
700 ms read pump and sub-second writes do not survive Doze or OEM battery management. (Diag
defect A1: `ClusterService` exists but nothing calls `start()`.)

## Permissions

<!--
Failure: this rule originally said "navigation needs no call/SMS/contact permission, keep it
that way" and treated MISSED_CALL/ALERTS_INFO as permissions to request later, scoped to that
feature. Two things it got wrong: (1) it didn't check that Google Play's 2026 policy blocks
READ_SMS/READ_CALL_LOG for any app that isn't the default SMS/Phone/Assistant handler - so the
"request them later" plan was never viable as written; (2) the user wants the full legitimate
permission/access surface acquired in the onboarding phase, once, not feature-by-feature.
Why: re-litigating the permission set every time a new frame gets wired up is how an app ends up
with a permission request flow scattered across five different screens and inconsistent Settings
deep-links.
Outcome: required set (below) gates onboarding; enhanced set is requested in the same flow but
does not block. Verified against developer.android.com and Play policy pages, 2026-08-28.
-->

### Required — gates the onboarding flow, the app cannot function without these

```
BLUETOOTH_SCAN                      API 31+, with neverForLocation
BLUETOOTH_CONNECT                   API 31+
ACCESS_FINE_LOCATION                API 30 and below for scanning; always, for our own
                                    guidance loop
FOREGROUND_SERVICE
FOREGROUND_SERVICE_CONNECTED_DEVICE
FOREGROUND_SERVICE_LOCATION         API 34+ - ClusterService runs the guidance loop in the
                                    same foreground service as the BLE link, so it declares
                                    foregroundServiceType="connectedDevice|location"
POST_NOTIFICATIONS                  API 33+
```

### Enhanced — requested in the same onboarding flow, never blocks reaching the app

```
READ_PHONE_STATE                    runtime permission - ring-state for MISSED_CALL (0310)
READ_CONTACTS                       runtime permission - resolve a ringing number to a name
Notification access                 Settings-granted role (NotificationListenerService) -
                                    source for ALERTS_INFO (0410) custom-text/message alerts
Caller ID & spam apps role          Settings-granted role (CallScreeningService) - confirms
                                    a call was missed vs. answered
```

`CallScreeningService`/`NotificationListenerService` have no runtime permission dialog — the user
grants them from Settings, which the app can only deep-link to and re-check on `ON_RESUME`.

**Never requested, permanently:** `CAMERA`, `GET_ACCOUNTS`, `READ_SMS`/`RECEIVE_SMS`,
`READ_CALL_LOG`. The first two map to vendor features (GeoFence anti-theft, music, account sync)
that don't exist in this protocol's scope. The last two are blocked outright by Play's 2026
default-handler policy for an app that isn't the default SMS/Phone/Assistant handler — becoming
that handler is out of scope. `CallScreeningService`/`READ_PHONE_STATE` and
`NotificationListenerService` cover the same protocol frames (`MISSED_CALL`, `ALERTS_INFO`)
without that restriction.

## The unresolved one

The link drops at **65 seconds**, reproducibly: 65.689 / 65.305 / 65.423 / 65.572 / 65.348 s,
all GATT status 8 (`GATT_CONN_TIMEOUT`). Five occurrences inside a 0.4 s spread is a timer, not
radio conditions — and BLE's maximum supervision timeout is 32 s, so it cannot be a link-layer
timeout. Write volume is irrelevant (0.02 and 3.5 writes/s both dropped at 65 s).

Ranked candidates, all still open (tracker D1): (1) we never read `CONTROL`, (2) we never send
the `GENERAL` heartbeat, (3) wrong write type, (4) no foreground service / connection priority.
Fix A1–A5 before theorising further.

**Also blocking**: our text does not render even though the frame is byte-for-byte identical to
the vendor's (`differing byte indices: NONE`). Leading hypothesis is that the cluster does not
enable its text region until it sees a "complete client" — i.e. `GENERAL` plus `CONTROL` reads.
Tracker D4. This blocks the product's main differentiator.
