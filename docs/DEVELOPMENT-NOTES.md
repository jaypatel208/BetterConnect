# Development notes — bugs found and traps to avoid

A running list so the real app does not repeat mistakes already made or already found in the
vendor's code. Two sections: **defects in our own diagnostic build**, and **traps** that will
bite any implementation.

Tick items off as they are handled in the production app.

---

## A. Defects in our diagnostic build

Found by field testing or by reading the native code afterwards. None of these are in the
production app yet — the point is that they must not be.

| # | Defect | Impact | Fix |
|---|---|---|---|
| A1 | **`ClusterService` is written but never started.** Nothing calls `ClusterService.start()`, so the GATT link is held from a plain app scope with **no foreground service and no wake lock**. | Sub-second timers will not survive Doze or background. Not the cause of the 65 s drop (screen was on) but must be eliminated before blaming the cluster. | Start it on connect, stop on disconnect |
| A2 | **Write type hardcoded to `WRITE_TYPE_NO_RESPONSE`.** Every writable characteristic on this cluster advertises `WRITE` only, so Write **Request** is correct. | Writes do land — icons change — so the peripheral tolerates it. Still wrong, and a candidate for the 65 s drop. | Derive from `characteristic.properties`, per `CONNECTION.md` §4 |
| A3 | **No `CONTROL` read pump.** The vendor reads `0A10` every 700 ms; we never read anything. | Leading candidate for the 65 s disconnect. Also means rider button presses are invisible. | 700 ms scheduled read |
| A4 | **No `GENERAL` packet.** The cluster never sees the incrementing heartbeat byte. | Second candidate for the 65 s drop. Also no acks are possible. | Minimal `GENERAL` on a 1 s timer |
| A5 | **Connection priority never set.** | Vendor explicitly sets `BALANCED` and blames `HIGH` for a `status=8` loop. | `requestConnectionPriority(BALANCED)` |
| A6 | **Distance threshold is `< 999`; native uses `< 1000`.** | 999 m renders as `1.00 km` instead of `999 m`. One-metre window, cosmetic. | Use `< 1000` |
| A7 | **Byte 13 sent as 0.** It is `takeMeHomeAck`, not reserved. | Take-Me-Home requests can never be acknowledged. | Mirror the `CONTROL` request byte |
| A8 | **GPS sent as a boolean** setting bit 2. It is a **2-bit field** (bits 2–3): off / active / searching. | Cannot express "searching". And clearing it blanks the whole display — see B2. | Expose the 3-state enum |
| A9 | **End-of-navigation frame is 48 zero bytes.** Native instead sends a normal frame with **byte 0 bit 0 cleared**. | Unconfirmed whether the all-zero form actually clears this cluster. | Send the native form |
| A10 | **Text capped at 31.** Native truncates to 31 then appends `.`, giving 32. | Moot on this cluster — text does not render at all. | Low priority |
| A11 | *(fixed)* Distance-boundary sweep stepped by 2 from an even start, skipping 999 — the exact value it existed to demonstrate. | Caught by a unit test. | done |
| A12 | *(fixed)* A log test asserted on frame bytes without pinning the clock, so it passed in the morning and failed in the afternoon. | Caught by a unit test. | done |

## B. Hardware traps

Established on the bike. These are properties of the cluster, not of any implementation.

- [ ] **B1 — An unknown icon byte leaves the previous icon on screen.** The cluster does not
  blank. Sending a code it does not support is *worse* than sending nothing: the rider keeps
  seeing a stale instruction that looks current. **Never send an inert code.** The inert set
  on this cluster is `M N S T U W Y`.
- [ ] **B2 — The GPS bits gate the entire navigation display.** Clearing them removed the
  whole nav area, not an indicator. Always set GPS active while navigating.
- [ ] **B3 — Text renders and long strings scroll** marquee-style `[hardware]`. *(An earlier
  note here claimed the opposite; that was wrong.)* **But our client's text still does not
  appear**, and the frame has been proven byte-for-byte identical to the vendor's for the
  same inputs — so it is not an encoding bug. Leading hypothesis: the cluster does not enable
  its text region until it sees a complete client (`GENERAL` + `CONTROL` reads). Re-test
  after A3/A4.
- [ ] **B4 — Neither roundabout code renders.** `N` and `U` are both inert. Only `B` (rotary)
  draws an arc. See `MANEUVERS.md` §5.
- [ ] **B5 — One central at a time.** Force-stop the official app first. Two clients with
  auto-connect look exactly like random disconnects.
- [ ] **B6 — Feature flags are policy; the GATT table is truth.** Three characteristics the
  protocol defines are absent on this cluster.
- [ ] **B7 — Never write to `f000ffd0`.** TI firmware update. It can brick the cluster.

## C. Traps in the vendor's implementation — do not copy

Reading their code is useful. Copying it is not.

- [ ] **C1 — `characteristic.setValue()` then `writeCharacteristic(characteristic)`.**
  Deprecated since API 33, and *stateful*: the payload lives on the shared characteristic
  object, so two queued writes to the same characteristic overwrite each other before either
  is sent. Use `writeCharacteristic(char, value, writeType)`.
- [ ] **C2 — `GlobalVar`**: dozens of public static mutable fields written from BLE callback
  threads, timer threads and the main thread with no synchronisation.
- [ ] **C3 — A hand-rolled write queue plus a 3-second stall watchdog.** The watchdog exists
  because the queue has no timeouts. A coroutine `Mutex` + `withTimeout` needs neither.
- [ ] **C4 — `WRONG_WAY` as the mapping fallback.** Any unmapped manoeuvre tells the rider
  they are going the wrong way. On this cluster that code (`B`) draws the rotary arc, so
  the vendor's fallback would show a roundabout for anything unmapped. Hold the previous
  icon or send `G`.
- [ ] **C5 — Ramps flattened to plain turns.** `K`/`L` exist for ramps, both render on this
  cluster, and the vendor leaves them unused.
- [ ] **C6 — `launchMediaPlayerAck` increments forever.** It compares against a mirror that
  is only ever set during bootstrap, so once byte 13 differs the ack advances on every
  distinct frame. And no media player is ever launched.
- [ ] **C7 — Bootstrap zeroes the three call-request bytes** instead of adopting them, so a
  cluster reconnecting with a non-zero call byte fires that action immediately.
- [ ] **C8 — Two implementations of the same protocol that disagree.** JS uses `< 999` and a
  31-char text cap; native uses `< 1000` and 32. Do not maintain two encoders.
- [ ] **C9 — Endianness is not consistent across the protocol.** TBT distances are
  little-endian; `MISSED_CALL` and `ALERTS` timestamps are big-endian.
- [ ] **C10 — `batteryPercentage` is not a percentage.** It is a **bar count 0–4**, bucketed
  at 20/40/60/80 %. And **v2 clamps it to 0–3, silently losing the top bar** — a phone above
  80 % reports the same level as one at 60–80 %. v1 preserves all five. Do not copy the v2
  clamp.
- [ ] **C11 — Blinking is defined in the protocol but ignored by this cluster** `[hardware]`.
  `i` renders identically to `I`. Do not spend effort computing blink forms for this hardware.

## D. Open engineering questions

- [ ] **D1** — Which of A1–A5 actually causes the 65 s disconnect. Fix all, confirm, bisect.
- [ ] **D2** — Whether this cluster expects **v1 (legacy)** or **v2** packet sizes for
  `GENERAL` / `MISSED_CALL` / `ALERTS`. The vendor picks by SKU cohort, and this bike appears
  to be an **unrecognised SKU** (see `PROTOCOL.md` §2), which selects **v1**. Only affects
  those three packets — TBT is identical either way.
- [ ] **D3** — Whether the cluster latches a frame indefinitely while connected. The one-shot
  observation may simply have been the 65 s disconnect clearing the display.
- [ ] **D4** — Why our text does not render when the frame is provably correct. See B3.
- [ ] **D5** — `O` vs `P` u-turn handedness.
