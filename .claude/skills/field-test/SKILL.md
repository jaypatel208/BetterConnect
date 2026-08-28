---
name: field-test
description: Run an on-bike field test session against the Pulsar cluster and record the results. Use when the user is going to the bike, is at the bike, wants to test on hardware, is reporting what the cluster displayed, or asks to write up a field session into docs/FIELD-TESTS.md.
---

# Field test session

Bike time is the scarce resource and every observation gets recorded as `[hardware]` — the
highest-trust tag in the docs. A wrong entry is worse than a blank one, because it will be
trusted.

## Before the session

1. `./gradlew test` — **green is the gate.** If it is red, the session is cancelled, not
   "probably fine".
2. `./gradlew installDiagDebug`.
3. **Force-stop the official Bajaj Ride Connect app.** A BLE peripheral accepts one central at a
   time; two apps with auto-connect look exactly like random disconnects.
4. Decide the questions this session answers, in writing, before leaving. Pull the open ones
   from the `docs/DEVELOPMENT-NOTES.md` tracker (D1–D5 and the loose ends at the end of it).
   A session with no written question comes back with no usable data.
5. Arrange to film the cluster if the plan includes a sweep. Twenty-six steps is more than
   anyone recalls accurately afterwards.

## At the bike

1. **Link** → scan → a `PULSAR…` candidate appears → connect.
2. **Inspect** → check the verdict: MTU ≥ 51, `TBT_INFO` present *and writable*. If the
   characteristic is missing, stop — that finding is the result of the session.
3. **Signals** → send `I` → expect a left arrow and `500m`. If that renders, the link is good.
4. Run the planned sequence.

## The discriminator — this is the part that makes the data valid

Every sweep step sends **different text** (`SYMBOL A`, `SYMBOL B`, …). Then:

| Observed | Means |
|---|---|
| Text changed, arrow changed | The code renders. Record what it draws. |
| **Text changed, arrow did not** | The byte is **inert**. The frame landed; the cluster has no glyph. |
| Neither changed | The frame never landed. The result is **void** — do not record it. |

Without the changing text there is no way to tell an inert code from a dropped frame, and the
seven inert codes (`M N S T U W Y`) would have been recorded as "unknown".

Reading a glyph: a second **un-arrowed** branch means fork/keep. A single angled arrow means a
slight turn. The vendor's own `PrimaryTurns` labels for `C`/`D`/`Z`/`X` are inverted relative to
what the hardware draws — trust the hardware.

## Writing it up

Export from the **Log** tab → Share. That gives every frame as hex plus its decode.

Then write into `docs/FIELD-TESTS.md`:

- Tag every claim `[hardware]`. **An untagged assertion is a defect in the document.**
- Record timings to the millisecond where they matter — the 65-second disconnect was only
  identifiable as a timer because five occurrences fell inside a 0.4 s spread.
- **"No change" is a real result. "I could not tell" is a real result.** Record either honestly.
  Never fill a gap with a plausible guess.
- If a session closes a tracker item, update `docs/DEVELOPMENT-NOTES.md` and remove the question
  from wherever else it is listed. Stale open questions are how the docs rotted the first time.
- If a finding invalidates a rule in `.claude/rules/protocol.md`, update that rule and its
  Failure/Why/Outcome comment in the same change.
