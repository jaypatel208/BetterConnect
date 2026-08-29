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

## 5. The 65 s disconnect and the text-render sweep — ready to test (2026-08-29)

**Both of the items previously listed here needed app changes that now exist.** The write-type
fix, the 700 ms `CONTROL` read pump, the `GENERAL` heartbeat and `BALANCED` connection priority
are all in code (`DEVELOPMENT-NOTES.md` A2–A5), and each is independently toggleable from the
hidden debug menu (tap the version string ×7 on Home) — so this session can bisect, not just
re-observe.

**Run this as one sitting, stopwatch running:**

1. Connect, leave `CONTROL` and `GENERAL` both on (the default) — does the link survive past
   65 s? If yes, note which of the four combined fixes made the difference is still open; go to
   step 4's toggle sweep to narrow it down while you have the bike.
2. **The four-step text-render sweep (Deliverable 2b / D4)**, same session, same toggles:
   TBT only → expect blank text (the known baseline) → TBT + `CONTROL` → TBT + `GENERAL` →
   all three. Photograph the cluster at each step. If the caption never appears in any
   combination, that closes D4 in the other direction — icons-only is the product, and that is
   worth knowing before more work goes on top of it.
3. Whichever combination holds the link, flip `CONTROL` and `GENERAL` off one at a time
   (debug menu switches) and re-time from a fresh connect, to answer **B4** below directly.
4. Flip the `GENERAL` version chip (v1 → v2) and reconnect — **D2** — only if v1 does not hold
   the link on its own; this changes packet size, not the read/heartbeat cadence, so test it
   after, not instead of, the toggle sweep above.

---

# Session B — needs app changes first

I cannot answer these with the current build. Each needs code I have not written yet.
Listed so you know they are tracked, not forgotten.

| # | Question | What the app needs |
|---|---|---|
| B1 | *(app change landed 2026-08-29 — see §5 above)* **What causes the 65 s disconnect** | ~~The write-type fix (Write Request), a 700 ms `CONTROL` read, a `GENERAL` heartbeat, connection priority.~~ Now bisect on the bike via the debug menu. |
| B2 | **What the cluster reports on `CONTROL`** — bytes 18–19 and byte 0 bits 5–4 are never read by the vendor either | The read pump exists and decodes every field it knows about, but nothing yet dumps the two undetermined bytes/bits to the ride log for a button-press-by-button-press comparison — still needs a small logging addition |
| B3 | **Which cluster and firmware this is** | Read Device Information `2A24`–`2A29` (model, serial, firmware, hardware, software, manufacturer) — **not yet built**, scoped out of this phase; a one-shot read, worth adding before this app ever supports a second bike |
| B4 | *(app change landed 2026-08-29 — see §5 above)* **Is `GENERAL` required to hold the link, or only `CONTROL` reads?** | Send one, then the other, independently — the debug menu's two switches do exactly this now |
| B5 | **Does the cluster tolerate a slower `CONTROL` poll than 700 ms?** | Configurable poll interval — **not yet built**; `ControlPump`'s period is still a constructor constant, not a runtime toggle |
| B6 | **Text beyond 31 chars, and non-ASCII** | Bypass the sanitiser; native allows 32 |
| B7 | **GPS "searching" state (byte 12 bit 3)** | *(app change landed 2026-08-29)* `NavState.gpsStatus` is now a 2-bit field (OFF/ACTIVE/SEARCHING), not a boolean — worth re-confirming on the bike that SEARCHING renders as expected |
| B8 | **What the second vendor service `0020676e-…` does** | Read `1120`, which the vendor app never touches |
| B9 | **Do `MISSED_CALL` / `ALERTS` really have no checksum?** | Send a deliberately corrupt frame and see if it renders |

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
