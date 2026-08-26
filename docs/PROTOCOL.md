# Bajaj cluster — BLE wire protocol

Complete packet specification for the Bajaj instrument cluster BLE service, reverse
engineered from `com.bajajconnect.rideapp` v1.11.1 and verified against a Pulsar N160 UG.

**Evidence tags** — every claim carries one:
`[hardware]` observed on the bike · `[dex]` native Java in `classes3.dex` ·
`[js]` the React Native bundle · `[inferred]` reasoned, not verified.
An untagged assertion is a defect in this document.

> **Where the logic lives.** The app is React Native, but the JS bundle only builds the
> navigation frame. **Every other packet, the connection lifecycle and the return channel
> are native Java in `classes3.dex`** `[dex]`. An earlier revision of this document was
> written from the JS alone and was wrong in several load-bearing ways — most importantly
> it claimed the link was write-only. It is not.

See `CONNECTION.md` for the link contract, `SIGNALS.md` for what is worth building, and
`IMPLEMENTATION.md` for how to build it well.

---

## 1. GATT map

Vendor UUIDs are little-endian ASCII for `OTCEngineering` with a 16-bit selector prefix.
The cluster is a **Texas Instruments CC26xx** (`0451` = TI) `[hardware]`.

Service `0010676e-6972-6565-6e69-676e4543544f` `[dex]` `[hardware]`

| UUID prefix | Native name | Dir | Size | On this bike `[hardware]` |
|---|---|---|---|---|
| `0110` | `TBT_INFO` | → cluster | 48 B | **yes** `[WRITE]` |
| `0210` | `GENERAL` | → cluster | 89 B (v2) / 55 B (v1) | **yes** `[WRITE]` |
| `0310` | `MISSED_CALL` | → cluster | 63 B (v2) / 74 B (v1) | **yes** `[WRITE]` |
| `0410` | `ALERTS_INFO` | → cluster | 44 B (v2) / 40 B (v1) | **yes** `[WRITE]` |
| `0510` | `PLAYLIST_INFO` | → cluster | — | **yes** `[WRITE]` |
| `0610` | `MEDIA_INFO` | → cluster | 111 B | **absent** |
| `0910` | `FAV_CONTACTS` | → cluster | — | **absent** |
| `0A10` | `CONTROL` | **← cluster** | 20 B | **yes** `[READ]`, no NOTIFY |
| `0B10` | `RECENT_CALLS` | → cluster | — | **absent** |

Three characteristics the protocol defines are **not present on this cluster**. Feature
flags in the app are policy; **the GATT table is truth**. Enumerate at runtime and degrade.

Other services on this bike `[hardware]`: GAP `1800`, Device Information `180A`
(model/serial/firmware/hardware/software/manufacturer, all readable and worth reading),
a second vendor service `0020676e-…` (`1020` `[WRITE]`, `1120` `[READ]`) unknown to the app,
and `f000ffd0-0451-…`, a **TI firmware-update service — do not write to it.**

## 2. Two protocol generations

`NewBtProtocolSelector` picks per vehicle `[dex]`:

```java
useNewGeneralProtocol() = VehicleFeature.BLE_GENERAL_PACKET_V2.isEnabledFor(modelCode)
```

`BLE_GENERAL_PACKET_V2` is registered for cohorts `CLUSTER_V1` and `BLE_ONLY_GENERAL_V2`
`[dex]`, which between them cover every SKU the app knows — so **v2 is the normal case**.
Alerts and missed-call selection follow the same flag.

| | v1 (`CallFrame`) | v2 (`New*PacketV2`) |
|---|---|---|
| Status | `phoneStatusNew()` 55 B, heartbeat @53, **no checksum** | `NewGeneralPacketV2` 89 B, heartbeat @54, checksum @88 |
| Missed call | 74 B | 63 B |
| Alerts | 40 B | 44 B |

The 48/89/74/40/20 sizes in the JS constants table are the **v1** sizes `[js]` and are
misleading for v2 hardware.

## 3. Checksums

Where present, always the same: **8-bit additive sum of every preceding byte**, stored last.

```
checksum = (Σ buf[0 .. n-1]) & 0xFF   →  buf[n]
```

TBT `n = 47`; GENERAL v2 `n = 88` `[dex]`. **`MISSED_CALL` and `ALERTS` carry no checksum
at all** `[dex]` — verified by reading both builders end to end.

## 4. `TBT_INFO` — navigation, 48 bytes

Phone → cluster. **Hardware verified** `[hardware]`: frames built to this spec render
correctly on the cluster.

| Byte | Field | Notes |
|---|---|---|
| 0 | flags | `bit0`=1 always · `bit4`=turn distance in metres · `bit7`=ETA is PM |
| 1 | icon | ASCII letter; lowercase (+0x20) blinks. See `MANEUVERS.md` |
| 2–3 | turn distance, fraction | uint16 **little-endian**, hundredths 0–99 (km mode) |
| 4–5 | turn distance, whole | uint16 little-endian |
| 6 | ETA minutes | 0–59 |
| 7 | ETA hour + roundabout exit | low nibble = hour, **12-hour** · high nibble = exit 1–7 |
| 8–9 | total remaining, fraction | uint16 LE |
| 10–11 | total remaining, whole | uint16 LE |
| 12 | flags2 | `bit0`=total in metres · **bits 3–2 = GPS status**, see below |
| 13 | `takeMeHomeAck` | **not reserved** — echoes the cluster's Take-Me-Home request `[dex]` |
| 14 | text length | `min(len, 31)` `[js]` / `min(len, 32)` `[dex]` — see below |
| 15–46 | text | ASCII, not NUL-terminated. 32 bytes available `[dex]` |
| 47 | checksum | additive sum of 0..46 |

**Byte 12 is not a simple flag.** GPS occupies **two bits**, `gpsStatus << 2` `[dex]`:
`0` not active · `1` active · `2` searching. So "bit2 = GPS active" is only true for the
active case; *searching* sets bit 3, not bit 2.

**Byte 13 is the Take-Me-Home acknowledgement**, mirroring the rider's request byte from
`CONTROL` `[dex]`. An earlier revision called it reserved. Wrong.

### Distance encoding

Whole and fractional parts are **two separate little-endian uint16 fields**, not one scaled
integer. This is the most commonly mis-guessed part of the format.

- below the threshold → metre mode: whole = `d`, fraction = **always 0**, unit bit set.
- at or above → km mode: `d/1000`; whole = km, fraction = hundredths.

**The two implementations disagree on the threshold.** Native uses `< 1000` `[dex]`; the JS
navigation path uses `< 999` `[js]`, so 999 m renders as `1.00 km` there. Both write the
same characteristic. Which the cluster sees depends on which code path is driving.
**Unresolved** — pick `< 1000`, it is the correct one and the native default.

```
  850 m -> bytes[2..5] = 00 00 52 03   bit4=1  ->   850 m
 2500 m -> bytes[2..5] = 32 00 02 00   bit4=0  ->  2.50 km
12345 m -> bytes[2..5] = 23 00 0c 00   bit4=0  -> 12.35 km
```

### Text

`replaceAll("[^0-9a-zA-Z.]", " ").trim()` in both implementations `[dex]` `[js]`. This
destroys hyphens: `Sarkhej-Gandhinagar Hwy` ships as `Sarkhej Gandhinagar Hwy`. Dots
survive.

Length differs: JS clips to **31** `[js]`; native truncates to 31 and **appends a `.`**,
giving **32** bytes across 15..46 `[dex]`. Whether the cluster renders 32 is **untested**.

### Ending navigation

Two different frames exist, and they are not equivalent:

- **Native** `[dex]` — `buildNavHelperFrame(0, 0, …)`: byte0 `0x10` (**bit0 cleared** = nav
  inactive), icon 0, distances 0, byte12 `0x01 | gpsStatus<<2`, byte13 `takeMeHomeAck`,
  byte47 checksum. **Not an all-zero buffer.**
- **Legacy Mappls** `[dex]` — a literal `new byte[48]`. No caller for it was found.

Our diagnostic build sends the all-zero frame and the log records it, but whether the
cluster visually cleared was **not confirmed** `[hardware]`. Prefer the native form: clearing
bit 0 is an explicit "navigation inactive" signal, which an all-zero buffer only achieves
by coincidence. **Open.**

### Send cadence

Native re-sends the **cached** frame on a fixed schedule even when nothing changed, which
doubles as link keep-alive `[dex]`:

| Path | Interval |
|---|---|
| Google Navigation SDK | **800 ms** fixed rate |
| Mappls, 2-arg callback | ≥ 800 ms |
| Mappls, legacy 1-arg callback | ≥ 1000 ms |

**The 350 ms figure appears nowhere in the native code** — it is a JS-layer artefact. 800 ms
is the real cadence.

### Verified frames

Golden frame — left turn, 500 m, 12.30 km remaining, ETA 10:45 AM, `MG ROAD`:

```
11 49 00 00 F4 01 2D 0A 1E 00 0C 00 04 00 07 4D
47 20 52 4F 41 44 00 00 00 00 00 00 00 00 00 00
00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 95
```

Captured from the bike `[hardware]` — roundabout family N, 500 m, 8.40 km, 2:41 PM,
`TEST ROAD`:

```
91 4E 00 00 F4 01 29 02 28 00 08 00 04 00 09 54
45 53 54 20 52 4F 41 44 00 00 00 00 00 00 00 00
00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 C2
```

Note `0x91` = PM bit set + metre bit set + constant bit.

## 5. `CONTROL` — the return channel, 20 bytes

**Cluster → phone** `[dex]`. This is how rider button presses reach the app. Read on a
700 ms fixed-rate poll; notifications are attempted first but this cluster exposes no
NOTIFY, so polling is the only route `[hardware]`.

Only bytes 0–17 are ever parsed `[dex]`.

| Byte | Bits | Field | Meaning |
|---|---|---|---|
| 0 | 7–6 | `dialSource` | 0 none · 1 favourite · 2 recent · 3 missed |
| 0 | 5–4 | — | **UNDETERMINED** — never read |
| 0 | 3–0 | `volumeToSet` | volume in tenths, clamped 0–10 |
| 1 | | `callAccept` | value *change* = press |
| 2 | | `callReject` | change = press |
| 3 | | `callRejectWithSms` | change = press |
| 4 | 5–0 | `pagePlaylist` | playlist page, 1-based, 5 songs per page |
| 4 | 7–6 | `newPlaylistReq` | toggle/counter requesting a fresh list |
| 5 | | `takeMeHome` | change = press |
| 6 | | `resumeSong` | change = press |
| 7 | | `pauseSong` | change = press |
| 8 | | `skipToNext` | change = press |
| 9 | | `skipToPrev` | change = press |
| 10 | | `stopSong` | change = press |
| 11 | | `missedCallGet` | request the next missed-call record |
| 12 | | `alertGet` | request the next alert record |
| 13 | | `launchMediaPlayer` | change = press |
| 14 | | `selectPlaylistSong` | trigger byte |
| 15 | | `selectedPlaylistSong` | slot, clamped 0–4 |
| 16 | | `dialIndex` | clamped 0–4 |
| 17 | | `dialTxn` | transaction id; change = fresh dial confirmation |
| 18–19 | | — | **UNDETERMINED** — never read |

### Edge detection

There is no sequence number. The cluster holds a **level** in each command byte and changes
it on each press; the phone keeps a mirror and fires on inequality. Two gates matter:

- **Whole-frame dedup** — if all 20 bytes equal the previous frame, the entire handler is
  skipped. At 700 ms polling only changed frames do anything.
- **Bootstrap** — on the first frame after connect the phone *adopts* the cluster's sticky
  values without acting, so a retained value does not fire a phantom action on reconnect.
  Dial specifically requires passing through `dialSource == 0` before re-arming.

### Acknowledgement

The phone never writes to `CONTROL`. It acknowledges inside the **`GENERAL`** packet,
bytes 4–17. Two distinct styles `[dex]`:

- **Mirror** — ack byte is set to the request byte's value: `callAccept`, `callReject`,
  `callRejectWithSms`, `skipToNext`, `skipToPrev`, `missedCallGet`, `alertGet`.
- **Free-running counter** — ack increments on each accepted press: `resumeSong`,
  `pauseSong`, `stopSong`, `launchMediaPlayer`. Wraps modulo 256.

Acks are never cleared; mirrors track the last value and counters simply advance.

`missedCallGet` / `alertGet` are a **drain-by-polling queue**: each request pops exactly one
record, writes it to its characteristic, and only then sets the ack.

## 6. `GENERAL` — status and acknowledgement, 89 bytes (v2)

Phone → cluster. Sent on a **1000 ms timer** and on demand at connect, MTU change and call
state change `[dex]`. Rebuilt fresh at transmission time so the heartbeat advances per
actual send, not per enqueue.

| Byte | Field |
|---|---|
| 0 | `(volume & 0x0F) \| (headsetConnected << 4) \| 0xC0` |
| 1 | `(dnd ? 0x80 : 0) \| ((min(battery,3) & 3) << 4) \| (callState & 0x0F)` |
| 2 | `(signal & 7) \| ((birthdayDay & 0x1F) << 3)` |
| 3 | `birthdayMonth & 0x0F` |
| 4–17 | acknowledgement block — see §5 |
| 13 | `missedCallCount` (a count, not an ack) |
| 15 | `smsCount` (a count, not an ack) |
| 18 | caller present flag, 0/1 |
| 19 | caller string length, ≤ 32 |
| 20–51 | caller name, else number, UTF-8, zero padded |
| 52–53 | `callProgressCount`, little-endian uint16 |
| **54** | **heartbeat** — increments on every build |
| 55 | phone name length, ≤ 32 |
| 56–87 | phone name (`Build.MODEL`), UTF-8 |
| 88 | checksum, additive sum of 0..87 |

`callState` `[dex]`: `0` none · `1` incoming · `2` outgoing · `3` active · `4` ended.
Signal is clamped to 0–7; anything outside becomes 0. Battery is clamped to 0–3 — a
**2-bit** level, not a percentage, despite the source variable being named
`batteryPercentage`.

The v1 form is 55 bytes with heartbeat at 53, no phone name and **no checksum** `[dex]`.

## 7. `MISSED_CALL` — 63 bytes (v2)

Phone → cluster, one record per `missedCallGet` request. **No checksum** `[dex]`.

| Byte | Field |
|---|---|
| 0 | reserved id, constant `1` |
| 1 | caller name length, ≤ 32 (left 0 if the name is unknown or equals the number) |
| 2–33 | caller name, UTF-8 |
| 34 | number length, ≤ 18 |
| 35–52 | number, UTF-8 |
| 53–56 | timestamp, **big-endian** uint32 |
| 57–62 | unused |

Note the timestamp is big-endian here while TBT distances are little-endian. Do not assume
one endianness across the protocol.

## 8. `ALERTS_INFO` — 44 bytes (v2)

Phone → cluster, one record per `alertGet` request. **No checksum** `[dex]`.

| Byte | Field |
|---|---|
| 0 | alert type — `1` SMS, `2` WhatsApp `[dex]` |
| 1 | text length, ≤ 32 |
| 2–33 | text, UTF-8 |
| 34–37 | timestamp, big-endian uint32 |
| 38–43 | unused |

## 9. `MEDIA_INFO` — 111 bytes — not on this cluster

Phone → cluster `[dex]`. Documented for completeness; `0610` is **absent on this bike**.

| Byte | Field |
|---|---|
| 0 | constant `1` |
| 1 | title length ≤ 32 · 2–33 title |
| 34 | artist length ≤ 32 · 35–66 artist |
| 67 | album length ≤ 32 · 68–99 album |
| 100–101 | elapsed, uint16 big-endian |
| 102–103 | duration, uint16 big-endian |
| 104 | play status enum |

## 10. `PLAYLIST_INFO` — partially decoded

Phone → cluster. Paged song list, **5 songs per page**, page numbers 1-based `[dex]`.
Selection arrives back on `CONTROL` as `absoluteIndex = (page-1)*5 + slot`.

**The exact frame layout is UNDETERMINED** — it is assembled across `Controls.sendPlaylist`
and `BleService.sendSinglePlaylistPacket` rather than in one builder. Open.

## 11. Open questions

- `CONTROL` bytes 18–19, and byte 0 bits 5–4. Never read by the app. **Open.**
- `PLAYLIST_INFO` frame layout. **Open.**
- `FAV_CONTACTS` (`0910`) and `RECENT_CALLS` (`0B10`) layouts — absent on this cluster, so
  unverifiable here. **Open.**
- The second vendor service `0020676e-…`. Unknown to the app entirely. **Open.**
- Whether the cluster renders text outside `[A-Za-z0-9. ]`. **Open.**
- Whether `MISSED_CALL`/`ALERTS` really have no integrity check, or whether the cluster
  validates length some other way. **Open.**
