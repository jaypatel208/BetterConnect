# Manoeuvre icons and navigation mapping

Byte 1 of `TBT_INFO` selects the icon. It is an **ASCII letter**; the cluster's navigation
area is a fixed icon set, so one byte picks from a closed list.

**Evidence tags:** `[hardware]` observed on the bike · `[dex]` native Java ·
`[js]` JS bundle · `[inferred]` reasoned, not verified.

---

## 1. The central problem: three vocabularies, none confirmed

There are **three** different sources for what a byte means, and they **contradict each
other**:

1. **`PrimaryTurns`** — the native enum, used by the Google Navigation path `[dex]`.
2. **The Mappls sprite map** — `maneuverIDMap` plus the PNG filenames the app draws in its
   own UI `[js]`. This was the only source the first revision of this document had.
3. **What the cluster actually draws** — **largely unknown** `[hardware]`.

Where 1 and 2 disagree, at least one is wrong about the hardware. Four codes are outright
contradictory:

| Byte | `PrimaryTurns` `[dex]` | Mappls sprite `[js]` | Verdict |
|---|---|---|---|
| `C` | `TURN_SLIGHT_LEFT` | keep-left variant | **conflict** |
| `D` | `TURN_SLIGHT_RIGHT` | keep-right variant | **conflict** |
| `Z` | `KEEP_LEFT` | slight left | **conflict — inverse of C** |
| `X` | `KEEP_RIGHT` | slight right | **conflict — inverse of D** |
| `B` | `WRONG_WAY` | enter the rotary | **conflict, severe** |
| `O` / `P` | `U_TURN_LEFT` / `U_TURN_RIGHT` | u-turn right-hand / u-turn | **conflict, handedness** |

`C`/`D` and `Z`/`X` appear to be **swapped** between the two vocabularies. Sending the wrong
one of a swapped pair produces a plausible-looking but wrong arrow — the exact failure the
stock navigation is criticised for.

**Nothing here is settled until the sweep in §5 is run.**

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

## 3. Blinking

Lowercase (`+0x20`) is the blinking form. Threshold: **under 100 m** to the manoeuvre
(under 40 m for Mappls manoeuvre id 8) `[dex]`.

Two important asymmetries `[dex]`:

- **`PrimaryTurns` has no lowercase variants at all.** Anything routed through it is always
  uppercase, so on the Google path **only roundabouts blink** — those are routed through
  the Mappls `maneuverIDMap`, which does have blink pairs.
- **Arrival breaks the `+0x20` rule.** Steady is `G` (0x47), blinking is `H` (0x48), not
  `g`. `G` therefore means both "straight ahead" and "arrived", separated only by the blink
  form.

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

## 5. `A`–`Z` worksheet — the artefact that settles this

**Only the `Observed on cluster` column can close a row.** Everything in the Source column
is a claim about what the *app* believes, which is a different claim from what the
*cluster draws*.

Run the sweep, film the cluster, fill in the middle column.

| Byte | Char | Believed meaning | Source | Observed on cluster | Status |
|---|---|---|---|---|---|
| 0x41 | `A` | — | unused in both | *(to fill)* | **open** |
| 0x42 | `B` | `WRONG_WAY` / enter rotary | `[dex]` vs `[js]` **conflict** | *(to fill)* | **open** |
| 0x43 | `C` | slight left / keep left | **conflict** | *(to fill)* | **open** |
| 0x44 | `D` | slight right / keep right | **conflict** | *(to fill)* | **open** |
| 0x45 | `E` | sharp left | `[dex]` `[js]` agree | *(to fill)* | unconfirmed |
| 0x46 | `F` | sharp right | `[dex]` `[js]` agree | *(to fill)* | unconfirmed |
| 0x47 | `G` | straight / arrived steady | `[dex]` `[js]` agree | *(to fill)* | unconfirmed |
| 0x48 | `H` | arrived (blink form) | `[dex]` `[js]` agree | *(to fill)* | unconfirmed |
| 0x49 | `I` | turn left | `[dex]` `[js]` agree | **renders** `[hardware]` | **confirmed** |
| 0x4A | `J` | turn right | `[dex]` `[js]` agree | *(to fill)* | unconfirmed |
| 0x4B | `K` | ramp left | `[dex]` `[js]` agree | *(to fill)* | unconfirmed |
| 0x4C | `L` | ramp right | `[dex]` `[js]` agree | *(to fill)* | unconfirmed |
| 0x4D | `M` | — | unused in both | *(to fill)* | **open** |
| 0x4E | `N` | roundabout (right / family A) | `[dex]` `[js]` | **renders** `[hardware]` | **confirmed present** |
| 0x4F | `O` | u-turn (handedness disputed) | **conflict** | *(to fill)* | **open** |
| 0x50 | `P` | u-turn (handedness disputed) | **conflict** | *(to fill)* | **open** |
| 0x51 | `Q` | fork left | `[dex]` `[js]` agree | *(to fill)* | unconfirmed |
| 0x52 | `R` | fork right | `[dex]` `[js]` agree | *(to fill)* | unconfirmed |
| 0x53 | `S` | — | unused in both | **no effect** `[hardware]` | **confirmed inert** |
| 0x54 | `T` | `FERRY_TRAIN` | `[dex]` | **no effect** `[hardware]` | **conflict — see §2** |
| 0x55 | `U` | roundabout (left / family B) | `[dex]` `[js]` | *(to fill)* | **open — `N` vs `U`** |
| 0x56 | `V` | `MERGE` | `[dex]` | *(to fill)* | **open** |
| 0x57 | `W` | — | unused in both | *(to fill)* | **open** |
| 0x58 | `X` | keep right / slight right | **conflict** | *(to fill)* | **open** |
| 0x59 | `Y` | `FERRY` | `[dex]` | *(to fill)* | **open** |
| 0x5A | `Z` | keep left / slight left | **conflict** | *(to fill)* | **open** |

Also record the **lowercase** form of any letter that renders, to confirm blinking.

### Hardware behaviour already established

- **An unknown icon byte leaves the previous icon on screen** `[hardware]`. The cluster does
  not blank. So a wrong code is invisible in testing unless you change something else at
  the same time — vary the distance or text between sweep steps.
- `S` and `T` produced no change `[hardware]`.

## 6. Building a mapping we can trust

Until the worksheet is filled in:

- **Use only the confirmed and agreed rows.** `I`, `J`, `E`, `F`, `G`, `H`, `K`, `L`, `Q`,
  `R` have both sources agreeing.
- **Avoid the conflicted pairs** (`C`/`Z`, `D`/`X`, `O`/`P`, `B`) in production mapping
  until observed. Prefer degrading a slight-left to `I` (a confirmed left) over guessing
  between `C` and `Z`.
- **Never default to `B`.** If a manoeuvre cannot be mapped, hold the previous icon or send
  `G`. Telling a rider "wrong way" because of an unmapped enum is a safety issue, not a
  cosmetic one.
- **Use `K`/`L` for ramps.** They exist and the vendor does not use them.

## 7. Open questions

- Every `*(to fill)*` row above.
- The six direct conflicts in §1.
- Whether `T`/`Y` (ferry) render at all on a motorcycle cluster.
- Whether the cluster distinguishes `N` from `U`, or draws the same roundabout for both.
- Whether lowercase forms blink for all icons or only some.
