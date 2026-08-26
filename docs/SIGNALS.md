# Cluster capability catalogue

Everything that can be put on, or read from, the Pulsar N160 UG instrument cluster — what
is decoded, what this specific bike supports, what it would cost to build, and whether it
is worth building.

**Evidence tags:** `[hardware]` observed on the bike · `[dex]` native Java ·
`[js]` JS bundle · `[inferred]` reasoned, not verified.

Feature flags inside the vendor app are **policy**. The GATT table is **truth**. Anything
marked absent below is absent on this hardware regardless of what the protocol defines.

---

## 1. The whole surface at a glance

| Capability | Char | On this bike | Protocol | Effort | Verdict |
|---|---|---|---|---|---|
| Turn-by-turn navigation | `0110` | **yes** `[hardware]` | **complete, hardware-verified** | — | **Build first** |
| Clear the display | `0110` | **yes** `[hardware]` | complete | trivial | **Build** — required |
| Phone status (battery, signal, volume, DND) | `0210` | **yes** `[hardware]` | complete `[dex]` | low | **Build** — likely required, see §3 |
| Rider button input | `0A10` | **yes** `[hardware]` | complete `[dex]` | low | **Build** — see §4 |
| Incoming / active call display | `0210` | **yes** `[hardware]` | complete `[dex]` | medium | Optional, §5 |
| Missed-call list | `0310` | **yes** `[hardware]` | complete `[dex]` | medium | Optional, §5 |
| SMS / WhatsApp alerts | `0410` | **yes** `[hardware]` | complete `[dex]` | medium | Optional, §5 |
| Music playlist browsing | `0510` | **yes** `[hardware]` | **partial** | high | Defer, §6 |
| Now-playing track info | `0610` | **ABSENT** | complete `[dex]` | — | **Cannot build** |
| Favourite contacts | `0910` | **ABSENT** | undecoded | — | **Cannot build** |
| Recent-calls list | `0B10` | **ABSENT** | undecoded | — | **Cannot build** |
| Custom text / weather | — | — | — | — | **Not a thing**, §7 |
| Firmware update | `f000ffd0` | present | TI OAD | — | **Never touch**, §8 |

## 2. Navigation — `0110` TBT_INFO

The one capability proven end to end on real hardware `[hardware]`.

Carries: manoeuvre icon, distance to turn, total distance remaining, ETA as wall-clock
time, roundabout exit number, GPS-active flag, and up to 31 characters of street text.

Constraints worth designing around:
- Text is reduced to `[0-9a-zA-Z.]`, 31 chars. Hyphens and slashes become spaces.
- Distance switches to kilometres at 999 m with two decimals — sub-metre precision is lost
  above that threshold, and 999 m itself renders as `1.00 km`.
- ETA is an absolute clock time, not a duration, so it must be recomputed as time passes.
- The icon set is closed — see `MANEUVERS.md`. Several bytes are still unverified.

**Build this first.** Everything else is optional.

## 3. Phone status — `0210` GENERAL

Sent on a 1000 ms timer by the vendor `[dex]`. Carries battery (a **2-bit** level, 0–3 —
not a percentage), signal strength 0–7, media volume, headset-connected, DND, call state,
caller name, the phone's model name, and **the acknowledgement block for every rider
command**.

Two reasons this is probably not optional:

1. **It is the only way to acknowledge rider input.** Without it, buttons on the cluster
   can be read but never confirmed (§4).
2. **It carries a heartbeat byte** (index 54) that increments on every transmission. This
   is one of the leading candidates for the 65-second disconnect described in
   `CONNECTION.md` §6.

If the link cannot be kept alive without it, this moves from optional to mandatory. That is
currently **unresolved**.

## 4. Rider input — `0A10` CONTROL

The cluster's return channel, read every 700 ms `[dex]`. Fully decoded — see `PROTOCOL.md`
§5. On this bike it is `[READ]` with no NOTIFY, so it must be polled `[hardware]`.

What the rider can press, and what each would require of our app:

| Rider action | Needs | Note |
|---|---|---|
| Volume up/down | `MODIFY_AUDIO_SETTINGS` | Picks voice-call vs music stream by call state |
| Accept call | `ANSWER_PHONE_CALLS` | `TelecomManager.acceptRingingCall()` |
| Reject call | `ANSWER_PHONE_CALLS` | Also auto-SMSes if a reply is configured |
| Reject with SMS | `ANSWER_PHONE_CALLS`, `SEND_SMS` | Only while ringing |
| Play / pause / next / prev | media session access | Via `MediaControllerCompat` |
| Dial favourite / recent / missed | `READ_CONTACTS`, `READ_CALL_LOG`, `CALL_PHONE` | Index 0–4 |
| "Take me home" | saved home location | Starts navigation |
| Request missed calls / alerts | — | Drains one queued record per press |

**Recommendation: read and log it from day one, act on almost none of it initially.**
Reading costs one poll and is probably needed to hold the link. Acting on presses drags in
call, contact and SMS permissions that navigation does not otherwise need.

The obvious early exception is **"take me home"**, which is a navigation feature and needs
no new permissions.

## 5. Calls and messages — `0310`, `0410`, and the call fields of `0210`

All three are decoded and all three are supported by this cluster.

**But note what the vendor app actually does with them, because it is unusually careful:**
active-call state feeds the GENERAL packet continuously; missed calls and alerts sit in
queues that the *cluster* drains one record at a time by incrementing a request byte. It is
a pull model, not a push model. Any reimplementation has to hold those queues.

Cost: `READ_CALL_LOG`, `READ_CONTACTS`, `READ_PHONE_STATE`, `RECEIVE_SMS`, and notification
listener access for WhatsApp. That is a large permission surface, and notification-listener
access has Play Store policy implications if the app is ever distributed.

**Recommendation: defer.** Revisit once navigation is solid. Worth knowing: your cluster
may already show calls over the separate Bluetooth Classic link, independently of any of
this `[inferred]`.

## 6. Music — `0510` PLAYLIST_INFO

Paged song list, 5 per page, with the rider selecting a slot that comes back on `CONTROL`
as `(page-1)*5 + slot` `[dex]`.

Two problems. The **frame layout is not fully decoded** — it is assembled across two
methods rather than one builder. And `0610` MEDIA_INFO, which carries the now-playing
title/artist/album, is **absent on this cluster**, so the rider could browse and select but
would never see what is playing.

**Recommendation: defer.** Poor value until `0610` is confirmed on some cluster.

## 7. Custom text and weather — not supported

There is no general-purpose text characteristic. Every string field is bound to a semantic
slot: street name in `TBT_INFO`, caller name in `GENERAL`, message body in `ALERTS_INFO`.

Arbitrary text could be smuggled into the navigation street field or the alerts body, but
the cluster will frame it as navigation or as a message. There is no clean way to render
"22°C, rain later" as its own thing. **Not worth pursuing** `[inferred]`.

## 8. Firmware update — do not touch

`f000ffd0-0451-4000-b000-000000000000` is a Texas Instruments service, and `f000ffd1` is the
only characteristic on the whole device advertising `WRITE_NO_RESPONSE` `[hardware]`. This
is the TI over-the-air download family.

**Writing to it can brick the instrument cluster.** No feature justifies the risk. The
second vendor service `0020676e-…` is also undocumented and unknown to the app — leave it
alone until someone has a reason and a spare cluster.

## 9. Recommended build order

1. **Navigation** — `0110`, plus the all-zero clear frame. Proven, self-contained.
2. **Link stability** — whatever `CONNECTION.md` §6 turns out to require. Probably the
   700 ms `CONTROL` read and a minimal `GENERAL` heartbeat. Non-negotiable: a link that
   drops every 65 seconds is not shippable.
3. **Device Information read** — `2A24`–`2A29`. Free, and identifies which cluster and
   firmware we are talking to. Needed before supporting a second bike.
4. **"Take me home"** — the one rider button that is purely a navigation feature.
5. Everything else — only with a specific reason.

## 10. Open questions

- Is `GENERAL` required to hold the link, or only `CONTROL` reads? **Open** — this decides
  whether §3 is mandatory or optional.
- `PLAYLIST_INFO` frame layout. **Open.**
- Whether any Pulsar cluster exposes `0610`/`0910`/`0B10`, or whether they are for other
  models entirely. **Open.**
- What the second vendor service `0020676e-…` does. **Open.**
