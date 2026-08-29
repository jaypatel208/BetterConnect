package dev.jay.betterconnect.core.model

/**
 * Semantic navigation state, the input to the TBT encoder.
 *
 * This is deliberately the *intent*, not the wire representation. The encoder lossily
 * projects it onto 48 bytes (kilometre distances keep two decimals, text is stripped
 * to a 31-character ASCII subset), which is why round-trip tests compare against
 * [DecodedFrame] wire values rather than against this type.
 */
data class NavState(
    val symbol: Symbol,
    /** Metres to the next manoeuvre. Drives both the distance field and the blink rule. */
    val distanceToTurnM: Int,
    /** Metres remaining on the whole route. */
    val distanceLeftM: Int,
    /** Seconds until arrival. Encoded as a wall-clock time, not a duration. */
    val etaSeconds: Long,
    /** Instruction or street text. Sanitised and clipped by the encoder. */
    val text: String,
    /** 1..7 for roundabouts, 0 otherwise. Occupies the high nibble of byte 7. */
    val roundaboutExit: Int = 0,
    /**
     * GPS gates the whole navigation display on this cluster `[hardware]` - clearing it
     * removes the nav area entirely, not just an indicator. A lost fix must send
     * [GpsStatus.SEARCHING], never [GpsStatus.OFF], while navigation is still active.
     */
    val gpsStatus: GpsStatus = GpsStatus.ACTIVE,
    /** Mirrors the `CONTROL` `takeMeHome` request byte. Byte 13 is not reserved (A7). */
    val takeMeHomeAck: Int = 0,
    /**
     * Sends this exact byte as the icon code, bypassing [symbol].
     *
     * The diagnostic sweep has to emit codes with no semantic name - most of A-Z is
     * undocumented, and identifying what the cluster draws for each one is the whole
     * point of the exercise. Null in all normal navigation use.
     */
    val symbolOverride: Int? = null,
) {
    /** The original encoder blinks the icon once the turn is within 100 m. */
    val blinking: Boolean get() = distanceToTurnM <= BLINK_THRESHOLD_M

    val symbolCode: Int get() = symbolOverride ?: symbol.code(blinking)

    companion object {
        const val BLINK_THRESHOLD_M: Int = 100
    }
}

/** Byte 12 bits 3-2 of the TBT frame. A 2-bit field, not a boolean - PROTOCOL.md §4. */
enum class GpsStatus(val code: Int) {
    OFF(0),
    ACTIVE(1),
    SEARCHING(2),
    ;

    companion object {
        fun fromCode(code: Int): GpsStatus = entries.firstOrNull { it.code == (code and 0x03) } ?: OFF
    }
}

/** Connection lifecycle for the cluster link. Mirrors CONNECTION.md §4. */
sealed interface ConnectionState {
    data object Idle : ConnectionState
    data class Connecting(val address: String) : ConnectionState
    data class Discovering(val address: String, val mtu: Int) : ConnectionState
    data class Ready(val address: String, val mtu: Int) : ConnectionState

    /** Reached the device, but it does not speak this protocol. Terminal without user action. */
    data class Unsupported(val address: String, val reason: UnsupportedReason) : ConnectionState
    data class Disconnected(val address: String?, val status: Int) : ConnectionState
}

enum class UnsupportedReason(val message: String) {
    SERVICE_MISSING("Cluster service not present - this device does not speak the TBT protocol"),
    CHARACTERISTIC_MISSING("TBT_INFO characteristic missing - cluster is gated to GENERAL-only"),
    NOT_WRITABLE("TBT_INFO present but not writable"),
    MTU_TOO_SMALL("Negotiated MTU is too small for a 48-byte write"),
}

/** A device seen during a scan. */
data class DeviceInfo(
    val address: String,
    val name: String?,
    val rssi: Int,
    val bonded: Boolean,
    val connectable: Boolean,
    val serviceUuids: List<String> = emptyList(),
    val manufacturerData: String? = null,
) {
    /**
     * The official app runs an unfiltered scan and matches on advertised name only.
     * Reproduced here so the same devices surface. See CONNECTION.md §3.
     */
    val isCandidate: Boolean
        get() = name?.lowercase()?.let { n ->
            CANDIDATE_TOKENS.any { it in n }
        } == true

    companion object {
        val CANDIDATE_TOKENS = listOf("pulsar", "freedom", "dominar")

        /** A bonded device retrieved from the adapter, never seen in an active scan. */
        const val RSSI_UNKNOWN = -127
    }
}

/** Flattened GATT table for the Inspect screen. */
data class GattDump(val address: String, val mtu: Int, val services: List<GattService>) {
    val hasTbtCharacteristic: Boolean
        get() = services.any { s -> s.characteristics.any { it.isTbtInfo && it.writable } }
}

data class GattService(val uuid: String, val characteristics: List<GattCharacteristic>)

data class GattCharacteristic(val uuid: String, val properties: List<String>, val isTbtInfo: Boolean) {
    val writable: Boolean get() = "WRITE" in properties || "WRITE_NO_RESPONSE" in properties
}
