# Manoeuvre icons and navigation mapping

Byte 1 of `TBT_INFO` selects the icon. It is an **ASCII letter**; the cluster's navigation
area is a fixed icon set, so one byte picks from a closed list.

**Evidence tags:** `[hardware]` observed on the bike · `[dex]` native Java ·
`[js]` JS bundle · `[inferred]` reasoned, not verified.

---

## 1. Three vocabularies — and which one the hardware backs

There are three sources for what an icon byte means:

1. **`PrimaryTurns`** — the native enum used by the Google path `[dex]`.
2. **The Mappls `maneuverIDMap`** plus its sprite filenames `[dex]` `[js]`.
3. **What the cluster actually draws** `[hardware]` — the arbiter.

They disagreed on six codes. **Field observation has now settled most of them, and the two
vocabularies split the result** — neither is wholly right:

| Byte | `PrimaryTurns` | Mappls sprite | **Observed** `[hardware]` | Winner |
|---|---|---|---|---|
| `C` | slight left | keep left at fork | left arrow **plus a second, un-arrowed line to the right**, both off a central stem | **Mappls** — the second line is the not-taken branch, i.e. a fork/keep |
| `Z` | keep left | slight left | same as `C` **without** the right-hand line | **Mappls** — a plain slight turn |
| `D` | slight right | keep right at fork | exact mirror of `C` | **Mappls** |
| `X` | keep right | slight right | exact mirror of `Z` | **Mappls** |
| `O` | u-turn left | u-turn (right-hand) | u-turn | **`PrimaryTurns`** |
| `P` | u-turn right | u-turn | u-turn | **`PrimaryTurns`** |
| `B` | `WRONG_WAY` | enter the rotary | partial arc | **Mappls** |

**Rule that falls out:** where a code draws a *second, un-arrowed branch*, it is a
fork/keep-lane icon (`C`, `D`). Where it draws a single arrow at an angle, it is a slight
turn (`Z`, `X`).

The `PrimaryTurns` labels for `C`/`D`/`Z`/`X` are **inverted** relative to what the hardware
draws. Anyone mapping Google manoeuvres via the vendor's table would put slight turns on the
fork icon and vice versa.

`O` vs `P` handedness (which is left, which is right) still needs one confirming look.

## 2. `PrimaryTurns` — the native vocabulary

Written verbatim to byte 1 `[dex]`.

| Byte | Char | `PrimaryTurns` constant |
|---|---|---|
| 0x42 | `B` | `WRONG_WAY` |
| 0x43 | `C` | `TURN_SLIGHT_LEFT` |
| 0x44 | `D` | `TURN_SLIGHT_RIGHT` |
| 0x45 | `E` | `TURN_SHARP_LEFT` |
| 0x46 | `F` | `TURN_SHARP_RIGHT` |
| 0x47 | `G` | `STRAIGHT` |
| 0x48 | `H` | `DESTINATION_REACHED` |
| 0x49 | `I` | `TURN_LEFT` |
| 0x4A | `J` | `TURN_RIGHT` |
| 0x4B | `K` | `RAMP_LEFT` |
| 0x4C | `L` | `RAMP_RIGHT` |
| 0x4E | `N` | `ROUNDABOUT_RIGHT` |
| 0x4F | `O` | `U_TURN_LEFT` |
| 0x50 | `P` | `U_TURN_RIGHT` |
| 0x51 | `Q` | `FORK_LEFT` |
| 0x52 | `R` | `FORK_RIGHT` |
| 0x54 | `T` | `FERRY_TRAIN` |
| 0x55 | `U` | `ROUNDABOUT_LEFT` |
| 0x56 | `V` | `MERGE` |
| 0x58 | `X` | `KEEP_RIGHT` |
| 0x59 | `Y` | `FERRY` |
| 0x5A | `Z` | `KEEP_LEFT` |

**Not used by `PrimaryTurns`:** `A` (65), `M` (77), `S` (83), `W` (87).

Note `Y` is `FERRY` — the first revision of this document listed it as "unresolved" because
the Mappls sprite could not be found. `[dex]` answers it, `[hardware]` has not.

### The `T` contradiction

`T` = `FERRY_TRAIN` in the native enum `[dex]`, yet **`T` produced no visible change on the
cluster** `[hardware]`. Two readings, both plausible:

- the cluster has no ferry-train icon and ignores the code, or
- it renders something that was not noticed during the sweep.

This is precisely why `[dex]` cannot close a row. A vocabulary the *app* uses is not proof
of what the *cluster* draws.

## 3. Blinking — NOT supported on this cluster

The protocol defines lowercase (`+0x20`) as the blinking form, with a 100 m threshold
`[dex]`. **This cluster ignores it** `[hardware]`:

- `I` and `i` produce the identical result. Same for `J` / `j`.
- `H` shows the arrival icon in **either** case.

So the blink distinction exists in the wire format and in the vendor's code, but this
display does not act on it. Send uppercase always; there is no reason to compute the blink
form for this hardware.

One consequence worth noting: `G` (straight) and `H` (arrived) are **separate icons** here,
not one icon in two blink states. The `+0x20` arrival exception in the encoder is therefore
irrelevant on this cluster.

## 3b. Which vocabulary drives THIS bike: Mappls

The official app shows **Mappls** on this bike `[hardware]`, so the Google mapping in §4 is
**not** what its cluster sees. The Mappls path uses `NavigationHelper.maneuverIDMap`
`[dex]` — the same map this document was originally derived from, now cross-verified
against the native code.

Both paths build the **identical 48-byte frame**; only the manoeuvre→icon choice differs.

Two consequences that matter:

- **`B` = "enter the rotary"** (Mappls manoeuvre 72 → 66 `[dex]`). You observed `A` and `B`
  drawing a partial arc `[hardware]`. That is a direct confirmation: **`B` is the rotary
  icon**, and the native enum's claim that `B` means `WRONG_WAY` does not describe this
  cluster.
- **Mappls manoeuvres 58–71 are roundabouts** and map to `N` / `U` `[dex]` — both of which
  are **inert on this cluster** `[hardware]`. So the official app cannot draw those
  roundabouts here either. Only the rotary (72 → `B`) renders.

Exit numbers `[dex]`: manoeuvres 58–64 → exits 1–7, and 65–71 → exits 1–7, both ascending.
(An earlier revision of this document had the 58–64 block reversed. Corrected.)

## 4. Google Navigation SDK → icon

The vendor's own mapping `[dex]`. Reproduced because it is the most complete
Google-to-cluster table available, **not** because it is correct — several entries are
visibly lossy and are flagged.

| Google `Maneuver` | Byte | Note |
|---|---|---|
| `DEPART`, `STRAIGHT`, `NAME_CHANGE` | `G` | |
| `DESTINATION`, `_LEFT`, `_RIGHT` | `H` | downgraded to `G` while >80 m remain |
| `TURN_LEFT` / `TURN_RIGHT` | `I` / `J` | |
| `TURN_KEEP_LEFT` / `TURN_KEEP_RIGHT` | `Z` / `X` | |
| `TURN_SLIGHT_LEFT` / `_RIGHT` | `C` / `D` | |
| `TURN_SHARP_LEFT` / `_RIGHT` | `E` / `F` | |
| `TURN_U_TURN_COUNTERCLOCKWISE` | `O` | |
| `TURN_U_TURN_CLOCKWISE` | `P` | |
| `MERGE_*` (all three) | `V` | |
| `FORK_LEFT` / `FORK_RIGHT` | `Q` / `R` | |
| `ON_RAMP_LEFT`, `OFF_RAMP_LEFT` | `I` | **lossy** — a ramp shown as a plain left turn, though `K` `RAMP_LEFT` exists and is unused |
| `ON_RAMP_RIGHT`, `OFF_RAMP_RIGHT` | `J` | **lossy** — same, `L` unused |
| `ON_RAMP_SLIGHT_*` / `OFF_RAMP_SLIGHT_*` | `C` / `D` | |
| `ON_RAMP_SHARP_*` / `OFF_RAMP_SHARP_*` | `E` / `F` | |
| `ON_RAMP_UNSPECIFIED`, `OFF_RAMP_UNSPECIFIED` | `G` | **lossy** |
| `FERRY_BOAT` / `FERRY_TRAIN` | `Y` / `T` | |
| `ROUNDABOUT_*` clockwise band | `U` (+ exit) | via Mappls ids 65–71 |
| `ROUNDABOUT_*` counter-clockwise band | `N` (+ exit) | via Mappls ids 58–64 |
| **everything else** — incl. ramp keep/u-turn variants | `B` | **`WRONG_WAY` as the default** |

Two defects worth not copying:

- **Ramps are flattened into ordinary turns** even though `K`/`L` exist for exactly this.
- **The fallback is `B` (`WRONG_WAY`)**, so any unmapped manoeuvre tells the rider they are
  going the wrong way. That is worse than showing nothing.

### Roundabout exits

Exit number goes in the **high nibble of byte 7**, 1–7. Taken from
`step.getRoundaboutTurnNumber()`, else parsed from the instruction text
(`take the Nth exit`), else a per-severity default, else 0 `[dex]`.

Sticky state carries the entry icon and exit number through the `ROUNDABOUT_EXIT_*`
manoeuvre so the exit leg keeps showing the same roundabout.

## 5. `A`–`Z` sweep — RESULTS

Run on a Pulsar N160 UG, full alphabet, constant 500 m / `TEST ROAD` `[hardware]`.

**19 of 26 codes render. Seven do nothing.**

| Byte | Renders? | Observed | Notes |
|---|---|---|---|
| `A` | **yes** | partial arc, "like a loader at 0–80%, not a full circle" | identical to `B`. **Not in the native enum at all** |
| `B` | **yes** | same partial arc as `A` | |
| `C` | yes | left arrow **plus an un-arrowed line to the right**, both off a central stem | **fork / keep left** |
| `D` | yes | exact mirror of `C` | **fork / keep right** |
| `E` | yes | | |
| `F` | yes | | |
| `G` | yes | | |
| `H` | yes | arrival / "destination reached" icon, in either case | |
| `I` | yes | turn left | `i` identical - no blink |
| `J` | yes | | |
| `K` | yes | | |
| `L` | yes | | |
| `M` | **no effect** | | unused in the enum too — genuinely unallocated |
| `N` | **NO EFFECT** | | **roundabout family A — this cluster has no such icon** |
| `O` | yes | u-turn | handedness to confirm |
| `P` | yes | u-turn (other hand) | handedness to confirm |
| `Q` | yes | | |
| `R` | yes | | |
| `S` | **no effect** | | unused in the enum too |
| `T` | **NO EFFECT** | | `FERRY_TRAIN` in the enum — not on this cluster |
| `U` | **NO EFFECT** | | **roundabout family B — this cluster has no such icon** |
| `V` | yes | straight arrow with a shaped/flared tail, "like the arrow of a bow" | `MERGE` |
| `W` | **no effect** | | unused in the enum too |
| `X` | **yes** | exact mirror of `Z` | **slight right** |
| `Y` | **NO EFFECT** | | `FERRY` in the enum — not on this cluster |
| `Z` | yes | like `C` but **without** the right-hand line | **slight left** |

### What this cluster's icon set actually is

```
PrimaryTurns  minus {N, U, T, Y}  plus {A}
```

Three groups, and the split is coherent:

- **`M`, `S`, `W`** — unused by the app *and* inert on hardware. Genuinely unallocated.
- **`T`, `Y`** — ferry and ferry-train. The app defines them; a motorcycle cluster in India
  reasonably has no ferry icon.
- **`N`, `U`** — **both roundabout families are inert.** This is the significant one, and it
  is a hard constraint on navigation. See below.
- **`A`** — renders, yet the native enum never emits it. The cluster draws something the app
  never asks for.

### Roundabouts: likely `A`/`B`, not `N`/`U`

`N` and `U` — the two codes the vendor's Google path uses for every roundabout — **do
nothing on this cluster** `[hardware]`. Meanwhile `A` and `B` both draw a **partial arc**,
which is what a roundabout pictogram looks like.

The Mappls sprite map calls `B` *"enter the rotary"*; the native enum calls it `WRONG_WAY`.
The hardware behaviour supports the sprite map and contradicts the enum.

**Working hypothesis** `[inferred]`: on this cluster the rotary/roundabout icon is `A`/`B`,
and `N`/`U` belong to a different cluster generation. **Unconfirmed** — needs someone to
compare against the official app entering a real roundabout.

### Corrections to earlier revisions of this document

- An earlier revision recorded **`N` as "renders something"**. That was wrong. It was
  inferred from a captured frame that merely showed `N` being *sent* — which says nothing
  about what was *drawn*. Exactly the error class the evidence tags exist to prevent.
- The `N` vs `U` question is not "which one does it draw" but **"neither"**.

### Still open from this sweep

- `O` vs `P` handedness — which is the left u-turn.
- The exact shapes of `E`, `F`, `G`, `K`, `L`, `Q`, `R`.
- Whether `A` and `B` really draw the rotary, confirmed against a real roundabout.

## 6. Building a mapping we can trust

Constraints now established on hardware `[hardware]`:

- **Roundabouts cannot be drawn with `N` or `U`.** Both are inert. Until `A`/`B` is
  confirmed as the rotary icon, a roundabout has no reliable representation — fall back to
  the closest turn direction (`I`/`J`) rather than sending an inert code, which would leave
  the *previous* arrow on screen and actively mislead.
- **Never send `M`, `S`, `T`, `U`, `W`, `Y` or `N`.** They render nothing, and because the
  cluster holds the previous icon, sending one is worse than sending nothing: the rider
  keeps seeing a stale instruction that looks current.
- **Ferry manoeuvres have no icon.** Map them to `G` (straight) rather than `T`/`Y`.
- **Never default to `B`.** The vendor's Google mapping falls back to `B` = `WRONG_WAY` for
  every unmapped manoeuvre. On this cluster `B` draws an arc that is probably a rotary, so
  the vendor's fallback would show riders a roundabout for anything unmapped. Hold the
  previous icon or send `G`.
- **Use `K`/`L` for ramps.** Both render, and the vendor leaves them unused.

The safe set for a first navigation build — renders on hardware, and both sources agree on
meaning:

```
I  turn left        J  turn right
E  sharp left       F  sharp right
Q  fork left        R  fork right
K  ramp left        L  ramp right
G  straight         H  arrived
```

`C`/`D`/`X`/`Z` are **now resolved by observation** `[hardware]` and safe to use:

```
C  keep left / fork left     D  keep right / fork right
Z  slight left               X  slight right
```

Use the vendor's own table with care here — its `PrimaryTurns` labels for these four are
inverted relative to what the cluster draws.

**Blinking is not supported** `[hardware]`. Always send uppercase.

## 7. Open questions

- The exact shape drawn by each of the 19 rendering codes. Only `A`/`B` and `I` described.
- Whether `A`/`B` really is the roundabout/rotary icon.
- Whether blinking works at all — the lower/upper test used an inert pair and is void.
  Re-test `I` vs `i`.
- Why `X` renders slightly differently from the official app's arrow for that direction.
- Whether `C`/`Z` and `D`/`X` are swapped relative to one of the two source vocabularies.
- Whether the roundabout **exit number** (byte 7 high nibble) renders at all, given that
  both roundabout icons are inert.
