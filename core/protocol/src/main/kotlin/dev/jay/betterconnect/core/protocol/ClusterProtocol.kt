package dev.jay.betterconnect.core.protocol

import java.util.UUID

/**
 * Wire constants for the Bajaj cluster BLE service.
 *
 * The 128-bit UUIDs are little-endian ASCII for "OTCEngineering" with a 16-bit
 * selector in front. See PROTOCOL.md §2.
 */
object ClusterProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("0010676e-6972-6565-6e69-676e4543544f")
    val TBT_INFO_UUID: UUID = UUID.fromString("0110676e-6972-6565-6e69-676e4543544f")
    val GENERAL_UUID: UUID = UUID.fromString("0210676e-6972-6565-6e69-676e4543544f")
    val MISS_CALL_UUID: UUID = UUID.fromString("0310676e-6972-6565-6e69-676e4543544f")
    val ALERT_UUID: UUID = UUID.fromString("0410676e-6972-6565-6e69-676e4543544f")
    val ACTION_UUID: UUID = UUID.fromString("0A10676e-6972-6565-6e69-676e4543544f")

    /** TBT_INFO payload size. Fixed; the cluster does not accept short frames. */
    const val TBT_SIZE: Int = 48

    /** Index of the additive checksum; it covers bytes 0 until this index. */
    const val CHECKSUM_INDEX: Int = 47

    /**
     * The link has no return channel, so state is re-asserted continuously rather than
     * sent as deltas. This cadence is the transport, not an optimisation. PROTOCOL.md §1.
     */
    const val HEARTBEAT_MS: Long = 350L

    /** A 48-byte ATT write needs MTU >= 51. The official app never requests this. */
    const val MIN_MTU: Int = TBT_SIZE + 3
    const val REQUESTED_MTU: Int = 64

    /** Maximum street text bytes, at offset [TEXT_OFFSET]. */
    const val MAX_TEXT_LEN: Int = 31
    const val TEXT_OFFSET: Int = 15
}
