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
| A1 | *(fixed 2026-08-29)* **`ClusterService` is written but never started.** Nothing calls `ClusterService.start()`, so the GATT link is held from a plain app scope with **no foreground service and no wake lock**. | Sub-second timers will not survive Doze or background. Not the cause of the 65 s drop (screen was on) but must be eliminated before blaming the cluster. | `full`'s onboarding flow now calls `ClusterService.start()` once required permissions are granted. **Doing this for the first time surfaced a second, previously-latent bug** `[hardware]`: `onCreate()`'s `collectLatest` called `stopSelf()` on `ConnectionState.Idle` — the bootstrap state before `connect()` is ever called, not a finished session — which raced `onStartCommand()`'s `startForeground()` and crashed with `ForegroundServiceDidNotStartInTimeException` on every real launch. Fixed: only stop on `Disconnected`. |
| A2 | *(fixed 2026-08-29, unconfirmed on hardware)* **Write type hardcoded to `WRITE_TYPE_NO_RESPONSE`.** Every writable characteristic on this cluster advertises `WRITE` only, so Write **Request** is correct. | Writes do land — icons change — so the peripheral tolerates it. Still wrong, and a candidate for the 65 s drop. | `BleClusterTransport` now derives the write type from `characteristic.properties` per `CONNECTION.md` §4. |
| A3 | *(fixed 2026-08-29, unconfirmed on hardware)* **No `CONTROL` read pump.** The vendor reads `0A10` every 700 ms; we never read anything. | Leading candidate for the 65 s disconnect. Also means rider button presses are invisible. | `core:link`'s new `ControlPump` polls every 700 ms with whole-frame dedup and bootstrap adoption, producing the `GENERAL` ack block. Toggleable from the debug menu (tap the version string ×7 on Home) for the B4 bisect. |
| A4 | *(fixed 2026-08-29, unconfirmed on hardware)* **No `GENERAL` packet.** The cluster never sees the incrementing heartbeat byte. | Second candidate for the 65 s drop. Also no acks are possible. | `core:link`'s new `GeneralScheduler` sends `GENERAL` every 1000 ms, v1 or v2 behind a live flag (tracker D2). Toggleable from the debug menu for B4. |
| A5 | *(fixed 2026-08-29, unconfirmed on hardware)* **Connection priority never set, and MTU was requested before service discovery instead of after.** | Vendor explicitly sets `BALANCED` and blames `HIGH` for a `status=8` loop; the discovery/MTU ordering also did not match the documented sequence. | `ClusterLink.reduce` now requests `RequestConnectionPriority` (always `BALANCED`) and discovers services first, requesting MTU only once the TBT characteristic is confirmed present and writable. **Deliberately does not copy** the vendor's 2500 ms force-write-on-timeout fallback — see `core/link/ClusterLink.kt` KDoc. |
| A6 | **Distance threshold is `< 999`; native uses `< 1000`.** | 999 m renders as `1.00 km` instead of `999 m`. One-metre window, cosmetic. | Use `< 1000` |
| A7 | *(fixed 2026-08-29, unconfirmed on hardware)* **Byte 13 sent as 0.** It is `takeMeHomeAck`, not reserved. | Take-Me-Home requests can never be acknowledged. | `NavState.takeMeHomeAck` now mirrors the `CONTROL` request byte through the encoder. |
| A8 | *(fixed 2026-08-29, unconfirmed on hardware)* **GPS sent as a boolean** setting bit 2. It is a **2-bit field** (bits 2–3): off / active / searching. | Cannot express "searching". And clearing it blanks the whole display — see B2. | `NavState.gpsStatus: GpsStatus` (OFF/ACTIVE/SEARCHING) replaces the boolean; the encoder writes `gpsStatus.code shl 2`. |
| A9 | *(fixed 2026-08-29, unconfirmed on hardware)* **End-of-navigation frame is 48 zero bytes.** Native instead sends a normal frame with **byte 0 bit 0 cleared**. | Unconfirmed whether the all-zero form actually clears this cluster. | `TbtEncoder.endNavigationFrame()` now builds the native form: byte 0 = `0x10`, byte 12 = `0x01 \| (gpsStatus shl 2)`, byte 13 = ack, checksummed. |
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

- [ ] **D1** — Which of A2–A5 actually causes the 65 s disconnect. **All four now fixed in code
  (2026-08-29), unconfirmed on hardware.** `ClusterController` exposes independent
  `setControlPumpEnabled`/`setGeneralSchedulerEnabled` toggles, now reachable from the hidden
  debug menu (tap the version string ×7 on Home, `feature:debug`) so the next ride can bisect
  which fix actually mattered rather than only confirming all four together.
- [ ] **D2** — Whether this cluster expects **v1 (legacy)** or **v2** packet sizes for
  `GENERAL` / `MISSED_CALL` / `ALERTS`. The vendor picks by SKU cohort, and this bike appears
  to be an **unrecognised SKU** (see `PROTOCOL.md` §2), which selects **v1**. Only affects
  those three packets — TBT is identical either way. `GeneralScheduler`/`GeneralEncoder` now
  implement both behind `ClusterController.setGeneralVersion`, defaulting to v1, exposed as a
  v1/v2 chip in the debug menu, so flipping to v2 on the bike is a tap, not a rebuild. **The v1
  byte layout past the shared acknowledgement block is `[inferred]`, not confirmed** — see
  `GeneralEncoder`'s KDoc. Not yet built: **B3**'s one-shot Device Information (`2A24`–`2A29`)
  read, which would settle this from the model/firmware strings directly rather than inferring
  it from SKU cohort — left for a later session, it needs its own read path.
- [ ] **D3** — Whether the cluster latches a frame indefinitely while connected. The one-shot
  observation may simply have been the 65 s disconnect clearing the display.
- [ ] **D4** — Why our text does not render when the frame is provably correct. See B3. The
  four-step toggle sweep (Deliverable 2b) is now runnable from the debug menu without a
  rebuild — still **unconfirmed on hardware**, next ride's first job.
- [ ] **D5** — `O` vs `P` u-turn handedness.
- [x] **D6** — **Resolved 2026-08-28**: how to acquire `MISSED_CALL`/`ALERTS_INFO` permission
  support without the raw dangerous permissions Google Play's 2026 policy blocks for a non-
  default-handler app. Decision: `CallScreeningService` + `READ_PHONE_STATE` for call state,
  `NotificationListenerService` for message/text alerts, `READ_CONTACTS` for caller-name
  resolution — none of these are `READ_SMS`/`READ_CALL_LOG`. Acquired in the `full` onboarding
  flow (enhanced set, non-blocking) ahead of the features that consume them. See
  `.claude/rules/protocol.md` Permissions section and `docs/IMPLEMENTATION.md` §6.
