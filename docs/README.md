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

The diagnostic Android build that proved this protocol against real hardware lives in the
repository root — see [`../README.md`](../README.md).

## Status

**Navigation is proven.** Frames built to `PROTOCOL.md` §4 render correctly on the cluster
`[hardware]`. The captured frame in that section decodes exactly as the official app's own
log reported it, checksum included.

**The link is not yet stable.** A client that writes navigation frames but never reads
`CONTROL` is dropped by the cluster after ~65 seconds. Four candidate causes are ranked in
`CONNECTION.md` §6. Unresolved.

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

One bike session closes most of the icon questions: read the Device Information service
(`2A24`–`2A29`) to pin model and firmware, then sweep `A`–`Z` filming the cluster. Vary the
distance or text between steps — an unknown icon byte leaves the **previous** icon on
screen `[hardware]`, so a dud is invisible otherwise.
