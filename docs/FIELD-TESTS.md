# Field test checklist

Every open question in these documents, turned into something observable on the bike.

Fill in the **Observed** columns and hand this back. Anything you record here becomes
`[hardware]` evidence, which is the only tag that closes a question.

> **The single most important gotcha.** An unknown icon byte leaves the **previous** icon on
> screen — the cluster does not blank `[hardware]`. So a dud code looks identical to a code
> that did not arrive.
>
> **The discriminator: watch the street text.** Every sweep step sends different text
> (`SYMBOL A`, `SYMBOL B`, …). If the **text changes but the arrow does not**, that byte is
> inert. If neither changes, the frame never landed and the result is void.

Before starting: **force-stop Bajaj Ride Connect** (Settings → Apps → Force stop).
Connect on the Link tab, confirm Inspect shows the green verdict.

---

# Session 3 — what is still unknown

Sessions 1 and 2 answered the icon set, distances, blinking and the shape conflicts.
**Four things left**, and two of them need app changes first.

## 1. `O` vs `P` handedness — 30 seconds

Both draw a u-turn. Which one is the **left** u-turn?

| Send | Observed |
|---|---|
| `O` | |
| `P` | |

## 2. Is `A`/`B` really the rotary?

The Mappls map says manoeuvre 72 → `B` → "enter the rotary" `[dex]`, and you saw an arc.
One confirmation:

| Test | Observed |
|---|---|
| Navigate a **real roundabout** in the official app — what does the cluster draw? | |
| Does it match the arc `A`/`B` draws from ours? | |
| Does an exit number appear anywhere? | |

## 3. How much text fits before it scrolls

Captions must be readable at a glance, so they must **not** scroll. Send progressively
longer strings and note where scrolling starts:

| Send | Scrolls? |
|---|---|
| `TURN LEFT` (9) | |
| `SLIGHT LEFT` (11) | |
| `ROUNDABOUT` (10) | |
| `KEEP RIGHT NOW` (14) | |
| `ABCDEFGHIJKLMNOPQRST` (20) | |

The answer sets the caption length budget in `IMPLEMENTATION.md` §4.

## 4. Shapes of the remaining codes

Only for completeness — none are disputed:

| Code | Observed shape |
|---|---|
| `E` / `F` | |
| `G` | |
| `K` / `L` | |
| `Q` / `R` | |

## 5. Needs app changes first — do not attempt yet

- **Why our text does not render.** The frame is proven identical to the vendor's, so it is
  not an encoding bug. Re-test once `GENERAL` and the `CONTROL` read pump exist.
- **The 65 s disconnect.** Same fix list.

---

# Session B — needs app changes first

I cannot answer these with the current build. Each needs code I have not written yet.
Listed so you know they are tracked, not forgotten.

| # | Question | What the app needs |
|---|---|---|
| B1 | **What causes the 65 s disconnect** | The write-type fix (Write Request), a 700 ms `CONTROL` read, a `GENERAL` heartbeat, connection priority. Then bisect. |
| B2 | **What the cluster reports on `CONTROL`** — bytes 18–19 and byte 0 bits 5–4 are never read by the vendor either | Read `0A10` and log the raw 20 bytes, then press every cluster button |
| B3 | **Which cluster and firmware this is** | Read Device Information `2A24`–`2A29` (model, serial, firmware, hardware, software, manufacturer) |
| B4 | **Is `GENERAL` required to hold the link, or only `CONTROL` reads?** | Send one, then the other, independently |
| B5 | **Does the cluster tolerate a slower `CONTROL` poll than 700 ms?** | Configurable poll interval |
| B6 | **Text beyond 31 chars, and non-ASCII** | Bypass the sanitiser; native allows 32 |
| B7 | **GPS "searching" state (byte 12 bit 3)** | Expose the 2-bit field, not a boolean |
| B8 | **What the second vendor service `0020676e-…` does** | Read `1120`, which the vendor app never touches |
| B9 | **Do `MISSED_CALL` / `ALERTS` really have no checksum?** | Send a deliberately corrupt frame and see if it renders |

**B2 and B3 are cheap and high value** — a read pump and six string reads. If you want one
more app change before the next session, ask for those two.

Not answerable on this bike at all: `MEDIA_INFO` (`0610`), `FAV_CONTACTS` (`0910`) and
`RECENT_CALLS` (`0B10`) are **absent from your cluster's GATT table**, so their layouts
cannot be confirmed here regardless of app changes.

---

# Recording

Filming is strongly recommended for A1 — 26 steps is too many to note by hand, and the
video timestamps let us line frames up against the Log tab afterwards.

Also useful: after each session, **Log tab → Share** and send the export. It contains every
frame as hex plus its decode, so if an observation is ambiguous I can check exactly what
was sent.

A plain answer of "no change" is a real result. So is "I could not tell". Please do not
guess — a wrong entry here is worse than a blank one, because it will be recorded as
`[hardware]` and trusted.
