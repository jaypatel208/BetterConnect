package dev.jay.betterconnect.core.model

/**
 * Everything the `GENERAL` (`0210`) packet reports about the phone, excluding the
 * acknowledgement block (see [ControlAcks]) and the heartbeat byte (owned by the sender).
 *
 * All defaults are neutral/absent - this phase wires the heartbeat and the acknowledgement
 * block (needed to hold the link and unblock D4's text-render sweep), not the call/SMS/
 * battery features themselves, which are future work per `docs/DEVELOPMENT-NOTES.md` D6.
 */
data class GeneralState(
    /** Tenths, 0-10. */
    val volume: Int = 0,
    val headsetConnected: Boolean = false,
    val doNotDisturb: Boolean = false,
    /** A 2-bit bar count 0-4 (v1) / clamped 0-3 (v2) - not a percentage. PROTOCOL.md §6. */
    val batteryBars: Int = 0,
    /** 0 none - 1 incoming - 2 outgoing - 3 active - 4 ended. */
    val callState: Int = 0,
    /** Clamped 0-7. */
    val signalBars: Int = 0,
    val callerPresent: Boolean = false,
    val callerName: String = "",
    val callProgressCount: Int = 0,
    val missedCallCount: Int = 0,
    val smsCount: Int = 0,
    /** `Build.MODEL` in the real implementation. Absent entirely in the v1 frame. */
    val phoneName: String = "",
)

/**
 * The acknowledgement block, `GENERAL` bytes 4-17. Owned by the `CONTROL` read pump, which
 * updates one field the instant it sees the corresponding request byte change - never on a
 * timer, so an ack is never stale by more than one `CONTROL` poll. PROTOCOL.md §5.
 *
 * Two styles, matched exactly to the fields that use them:
 * - **Mirror** - the ack becomes the request byte's new value.
 * - **Free-running counter** - the ack increments (mod 256) on every accepted press, and
 *   the counter is driven only by an actual level change, never by re-observing the same
 *   press twice - unlike the vendor's `launchMediaPlayerAck`, which the docs record as
 *   incrementing forever because it compares against a bootstrap-only mirror (C6).
 */
data class ControlAcks(
    val callAccept: Int = 0,
    val callReject: Int = 0,
    val callRejectWithSms: Int = 0,
    val skipToNext: Int = 0,
    val skipToPrev: Int = 0,
    val missedCallGet: Int = 0,
    val alertGet: Int = 0,
    val resumeSong: Int = 0,
    val pauseSong: Int = 0,
    val stopSong: Int = 0,
    val launchMediaPlayer: Int = 0,
)

/** Which packet generation to build. See `docs/PROTOCOL.md` §2 and tracker D2. */
enum class GeneralVersion {
    /**
     * 55 bytes, heartbeat at byte 53, no phone name, no checksum. The cohort this cluster
     * maps to (`docs/PROTOCOL.md` §2) - **unconfirmed on hardware** `[inferred]`. Exact
     * byte offsets past the acknowledgement block are derived from the v2 table by
     * truncation, not independently confirmed; see `GeneralEncoder` KDoc.
     */
    V1,

    /** 89 bytes, heartbeat at byte 54, checksum at byte 88. Fully documented, PROTOCOL.md §6. */
    V2,
}
