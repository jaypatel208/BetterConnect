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

# Session 2 — the short list

Session 1 answered most of the icon set. **Six things left**, in priority order. Everything
else is either answered or needs app changes (Session B below).

## 1. Kilometre distances — blocking

The one result that could make navigation unusable. Note that **our encoding is
byte-for-byte identical to the vendor's** `[dex]`, so if the cluster is wrong for us it is
wrong for the official app too. That makes a direct comparison the strongest test:

**Step 1 — reference.** Run the **official Bajaj app**, navigate to somewhere ~2.5 km away.
Note exactly what the cluster shows, digit for digit, including any decimal point.

**Step 2 — ours.** Force-stop it, connect with ours, Signals tab, send these:

| Send | Expected | Observed |
|---|---|---|
| 2500 m | `2.50 km` | |
| 1200 m | `1.20 km` | |
| 5750 m | `5.75 km` | |
| 999 m | `999 m` or `1.00 km`? | |
| 1011 m | `1.01 km` | |

These values are asymmetric on purpose — 1011 encodes as whole=1/fraction=1 and cannot
distinguish between the candidate readings. 2500 (whole=2, fraction=50) can.

## 2. Blinking — the previous test was void

Session 1 compared `A` vs `a`, but `A`'s blink behaviour is itself unknown, so the result
proves nothing. Use a code that definitely renders:

| Test | Observed |
|---|---|
| `I` then `i` — does `i` blink? | |
| `J` then `j` | |
| `G` then `H` — different icons, or the same one blinking? | |

## 3. Is `A`/`B` really the roundabout?

The Mappls code says `B` = "enter the rotary" `[dex]` and you saw an arc `[hardware]`. Worth
one confirmation:

| Test | Observed |
|---|---|
| Navigate a **real roundabout** in the official app — what does the cluster draw? | |
| Does it match the arc `A`/`B` draws from our app? | |
| Does the exit number appear anywhere? | |

## 4. Shapes of the 19 rendering codes

Session 1 established *which* codes render but described only `A`, `B` and `I`. The disputed
pairs are what matter — send each and describe the arrow in a few words:

| Code | Observed shape |
|---|---|
| `C` | |
| `Z` | |
| `D` | |
| `X` | |
| `O` | |
| `P` | |
| `V` | |

`C` vs `Z` and `D` vs `X` are the important ones: my two sources disagree about which is the
slight turn and which is the lane-keep. And `X` reportedly renders slightly differently from
the official app's arrow for the same direction — worth a closer look.

## 5. Does the display latch?

Session 1 suggested it holds "a while" then drops — but that may simply have been the 65 s
disconnect clearing it. Time it against the Log tab:

| Test | Observed |
|---|---|
| One-shot, send `I`, note the clock. Does it vanish? | |
| If yes, at roughly what elapsed time? | |
| Did the Log show a disconnect at the same moment? | |

## 6. Clearing

| Test | Observed |
|---|---|
| Send `I`, press **Clear**. Does the nav area actually clear? | |
| Or does the arrow stay? | |

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
