# Bajaj cluster — reverse engineering

Protocol documentation for the Bajaj instrument cluster BLE interface, reverse engineered
from `com.bajajconnect.rideapp` v1.11.1 and verified against a **Pulsar N160 UG (2026)**.

| Document | What it covers |
|---|---|
| [`PROTOCOL.md`](PROTOCOL.md) | The wire contract. Every characteristic, byte layouts, checksums, verified frames. |
| [`CONNECTION.md`](CONNECTION.md) | The link contract. Connect ordering, MTU gating, keep-alive, failure modes. |
| [`MANEUVERS.md`](MANEUVERS.md) | Icon vocabulary, Google Maps mapping, and the `A`–`Z` worksheet still to be filled in. |
| [`SIGNALS.md`](SIGNALS.md) | Capability catalogue — what this cluster can do and what is worth building. |
| [`IMPLEMENTATION.md`](IMPLEMENTATION.md) | Engineering guide for building a client well on modern Android. |
| [`FIELD-TESTS.md`](FIELD-TESTS.md) | **Every open question as a bike test.** Fill in the Observed columns. |
| [`DEVELOPMENT-NOTES.md`](DEVELOPMENT-NOTES.md) | **Bugs found and traps to avoid.** Read before writing the production app. |

The diagnostic Android build that proved this protocol against real hardware lives in the
repository root — see [`../README.md`](../README.md).

## Status

**Navigation is proven.** Frames built to `PROTOCOL.md` §4 render correctly on the cluster
`[hardware]`. The captured frame in that section decodes exactly as the official app's own
log reported it, checksum included.

**The link is not yet stable.** The cluster drops a client that never reads `CONTROL` after
~65 seconds — five occurrences across two sessions, all GATT status 8, inside a 0.4 s
spread. Four candidates are ranked in `CONNECTION.md` §6. Unresolved.

**This bike runs the Mappls path, not Google** `[hardware]` — which implies an unrecognised
SKU, and therefore the **v1 packet generation** for `GENERAL`/`MISSED_CALL`/`ALERTS`.
`TBT_INFO` is identical in both. See `PROTOCOL.md` §2.

**Three field findings shape the product** `[hardware]`: the cluster shows **no street
text**, has **no roundabout icon** (both codes inert), and the **GPS bits gate the entire
navigation display**. Kilometre-mode distances may be mis-rendered and are the top thing to
re-test — see `PROTOCOL.md` §4.

## Reproducing the analysis

The vendor APK is **not** in this repository — it is a 115 MB third-party binary, over
GitHub's 100 MB per-file limit, and it is not ours to redistribute. Everything here was
derived from `com.bajajconnect.rideapp` **v1.11.1**.

To regenerate from a copy of that XAPK:

```bash
unzip -q com.bajajconnect.rideapp_1.11.1.xapk -d xapk        # base APK + splits
unzip -p xapk/com.bajajconnect.rideapp.apk classes3.dex > c3.dex

# The native BLE stack - where everything except navigation lives
jadx -d out --no-res --no-debug-info c3.dex
#   out/sources/com/bajajconnect/ble/BleService.java          connection lifecycle
#   out/sources/com/bajajconnect/ble/Controls.java            CONTROL decoder
#   out/sources/com/bajajconnect/ble/protocol/New*PacketV2.java
#   out/sources/com/bajajconnect/utils/{TbtNavFrame,CallFrame}.java
#   out/sources/com/bajajconnect/enums/PrimaryTurns.java      icon vocabulary

# The JS bundle - navigation frame only, Hermes bytecode
unzip -p xapk/com.bajajconnect.rideapp.apk assets/index.android.bundle > bundle.hbc
pip install hermes-dec && hbc-decompiler bundle.hbc bundle.js
```

Read the native side first. The JS layer drives navigation and nothing else.

## Reading the evidence tags

Every factual claim carries one:

| Tag | Meaning |
|---|---|
| `[hardware]` | Observed on the bike. The only tag that settles a question. |
| `[dex]` | Native Java in `classes3.dex` — the real BLE stack. |
| `[js]` | The React Native bundle — navigation only. |
| `[inferred]` | Reasoned, not verified. |

An untagged assertion is a defect in the document.

This matters because of how the first revision went wrong. It was written entirely from the
JS bundle, which turns out to drive navigation and nothing else — the connection lifecycle,
the return channel and every other packet are native. Inference from a partial source got
recorded as fact and survived until the hardware contradicted it. Notably, the earlier
claim that the link had **no return channel** was false: `0A10` is read by the official app
every 700 ms.

## Open questions

Tracked per document; the ones that matter most:

- Which of the four candidates causes the 65 s disconnect. `CONNECTION.md` §6
- The `A`–`Z` icon worksheet. Six codes are actively **disputed** between the native enum
  and the sprite map. `MANEUVERS.md` §1, §5
- Whether `GENERAL` is required to hold the link, or only `CONTROL` reads. `SIGNALS.md` §3
- `PLAYLIST_INFO` frame layout. `PROTOCOL.md` §10
- The second vendor service `0020676e-…`, unknown to the app entirely.

[`FIELD-TESTS.md`](FIELD-TESTS.md) turns all of these into a checklist. Eight of them are
testable with the diagnostic build as it stands; nine need app changes first and are listed
separately there.
